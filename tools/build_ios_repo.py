#!/usr/bin/env python3
"""Build an IPA source and rootful jailbreak APT repository from published releases."""
import argparse
import bz2
import datetime
import email.utils
import gzip
import hashlib
import html
import json
import os
import plistlib
import re
import shutil
import struct
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

BUNDLE = 'com.brian.wiiucontroller.ios'
APP = 'WiiUController.app'
REPO = 'https://github.com/TheStonedGamer/wiiu-controller'
DEFAULT_URL = 'https://thestonedgamer.github.io/wiiu-controller/'
DESCRIPTION = 'Use your iPhone as a Wii Remote, Pro or Classic Controller for Wii U. Includes gyro pointing, touch controls and editable layouts. Requires the Aroma receiver.'

def download(asset, cache):
    name = asset['name']
    if Path(name).name != name:
        raise ValueError('Invalid asset filename')
    path = cache / name
    if not path.exists():
        with urllib.request.urlopen(asset['browser_download_url'], timeout=60) as response:
            path.write_bytes(response.read())
    if path.stat().st_size != asset['size']:
        raise ValueError(f'Wrong asset size: {name}')
    return path

def extract_ipa(ipa, target):
    prefix = f'Payload/{APP}/'
    with zipfile.ZipFile(ipa) as archive:
        if archive.testzip() is not None:
            raise ValueError('Invalid IPA CRC')
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            if not entry.filename.startswith(prefix):
                raise ValueError('Unexpected IPA contents')
            relative = Path(entry.filename[len(prefix):])
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError('Invalid IPA path')
            path = target / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(entry))
            path.chmod(0o644)
    info = plistlib.loads((target / 'Info.plist').read_bytes())
    if info['CFBundleIdentifier'] != BUNDLE:
        raise ValueError('Unexpected application identity')
    executable = target / info['CFBundleExecutable']
    if struct.unpack_from('<II', executable.read_bytes()) != (0xFEEDFACF, 0x0100000C):
        raise ValueError('Expected arm64 Mach-O')
    executable.chmod(0o755)
    if not (target / '_CodeSignature/CodeResources').exists():
        raise ValueError('Missing resource signature')
    return info

def package(app, info, output, base):
    version = info['CFBundleShortVersionString'] + '-1'
    name = f'{BUNDLE}_{version}_iphoneos-arm.deb'
    fields = {
        'Package': BUNDLE, 'Name': 'Wii U Controller', 'Version': version,
        'Architecture': 'iphoneos-arm', 'Maintainer': 'TheStonedGamer <283505048+TheStonedGamer@users.noreply.github.com>',
        'Author': 'TheStonedGamer', 'Section': 'Utilities',
        'Depends': f'firmware (>= {info["MinimumOSVersion"]}), uikittools',
        'Description': 'Phone controller for Wii U (rootful jailbreak, arm64)',
        'Homepage': REPO, 'Depiction': base, 'Icon': base + 'icon.png',
        'Installed-Size': str((sum(p.stat().st_size for p in app.rglob('*') if p.is_file()) + 1023)//1024),
    }
    with tempfile.TemporaryDirectory(prefix='controller-deb-') as temporary:
        root = Path(temporary)
        root.chmod(0o755)
        shutil.copytree(app, root / 'Applications' / APP)
        control = root / 'DEBIAN'; control.mkdir()
        (control / 'control').write_text(''.join(f'{key}: {value}\n' for key, value in fields.items()))
        scripts = {
            'preinst': '''#!/bin/sh
set -e
for app in /var/containers/Bundle/Application/*/WiiUController.app; do
  if [ -d "$app" ]; then
    echo 'An IPA installation already exists. Export your layouts, then remove that app before installing the repo package.' >&2
    exit 1
  fi
done
exit 0
''',
            'postinst': '''#!/bin/sh
if command -v uicache >/dev/null 2>&1; then uicache -p /Applications/WiiUController.app || true; fi
exit 0
''',
            'postrm': '''#!/bin/sh
case "$1" in
  remove|purge) if command -v uicache >/dev/null 2>&1; then uicache -u /Applications/WiiUController.app || true; fi ;;
esac
exit 0
''',
        }
        for script, body in scripts.items():
            path = control / script; path.write_text(body); path.chmod(0o755)
        target = output / 'pool' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['dpkg-deb', '--build', '--root-owner-group', '-Zgzip', str(root), str(target)], check=True)
    data = target.read_bytes()
    fields.update({'Filename': 'pool/' + name, 'Size': str(len(data)),
                   'MD5sum': hashlib.md5(data).hexdigest(), 'SHA256': hashlib.sha256(data).hexdigest(),
                   'SHA512': hashlib.sha512(data).hexdigest()})
    return fields

def build(releases, output, cache, base):
    output.mkdir(parents=True, exist_ok=True); cache.mkdir(parents=True, exist_ok=True)
    base = base.rstrip('/') + '/'
    releases = [r for r in releases if not r.get('draft') and not r.get('prerelease') and re.fullmatch(r'v\d+\.\d+\.\d+', r['tag_name'])]
    releases.sort(key=lambda r: tuple(map(int, r['tag_name'][1:].split('.'))), reverse=True)
    versions, packages = [], []
    privacy = {}
    for release in releases:
        version = release['tag_name'][1:]
        assets = {a['name']: a for a in release['assets']}
        ipa_name = f'WiiU-Controller-ios-v{version}-jailbreak.ipa'
        if ipa_name not in assets:
            continue
        if 'release.json' not in assets:
            raise ValueError('Release is missing its checksum manifest')
        ipa = download(assets[ipa_name], cache)
        # Cache the manifest per version; every release calls it release.json.
        manifest_asset = dict(assets['release.json']); manifest_asset['name'] = f'manifest-{version}.json'
        manifest = json.loads(download(manifest_asset, cache).read_text())
        if manifest['version'] != version or manifest['sha256'][ipa_name] != hashlib.sha256(ipa.read_bytes()).hexdigest():
            raise ValueError('IPA checksum does not match release manifest')
        with tempfile.TemporaryDirectory(prefix='controller-ipa-') as temporary:
            app = Path(temporary) / APP
            info = extract_ipa(ipa, app)
            if info['CFBundleShortVersionString'] != version:
                raise ValueError('IPA and release versions differ')
            if not versions:
                shutil.copyfile(app / 'AppIcon60x60@2x.png', output / 'icon.png')
                privacy = {k: v for k, v in info.items() if k.startswith('NS') and k.endswith('UsageDescription')}
            packages.append(package(app, info, output, base))
            versions.append({'version': version, 'buildVersion': info['CFBundleVersion'],
                             'date': release['published_at'], 'downloadURL': assets[ipa_name]['browser_download_url'],
                             'size': ipa.stat().st_size, 'minOSVersion': info['MinimumOSVersion'],
                             'localizedDescription': 'Gyro pointing and customizable Wii U controls. See GitHub release notes for details.'})
    if not versions:
        raise ValueError('No published IPA releases found')
    app = {'name': 'Wii U Controller', 'bundleIdentifier': BUNDLE, 'developerName': 'TheStonedGamer',
           'localizedDescription': DESCRIPTION + ' Jailbreak IPA; a compatible installer/signing setup is required.',
           'iconURL': base+'icon.png', 'tintColor': '5ADDC5', 'versions': versions,
           'appPermissions': {'entitlements': ['keychain-access-groups'], 'privacy': privacy},
           'versionDate': versions[0]['date'], 'versionDescription': versions[0]['localizedDescription'],
           **{k: versions[0][k] for k in ['version', 'date', 'downloadURL', 'size']}}
    source = {'name': 'Wii U Controller', 'identifier': 'com.brian.wiiucontroller.source',
              'subtitle': 'iPhone controls for Wii U', 'description': DESCRIPTION,
              'iconURL': base+'icon.png', 'website': REPO, 'tintColor': '5ADDC5', 'apps': [app], 'news': []}
    (output / 'apps.json').write_text(json.dumps(source, indent=2)+'\n')
    # Older source clients use a stricter schema without modern appPermissions.
    legacy_app = {k: v for k,v in app.items() if k not in ['appPermissions','date']}
    legacy_app['versions'] = [{k: v for k,v in entry.items() if k != 'buildVersion'} for entry in versions]
    legacy_app['permissions'] = [
        {'type': 'network', 'usageDescription': privacy.get('NSLocalNetworkUsageDescription','Connect to Wii U on Wi-Fi.')},
        {'type': 'motion', 'usageDescription': privacy.get('NSMotionUsageDescription','Use phone motion for controller input.')},
    ]
    legacy = {'name': source['name'], 'identifier': source['identifier'], 'sourceURL': base+'sidestore.json', 'apps': [legacy_app], 'news': []}
    (output / 'sidestore.json').write_text(json.dumps(legacy,indent=2)+'\n')
    package_data = '\n'.join(''.join(f'{k}: {v}\n' for k,v in p.items()) for p in packages).encode()+b'\n'
    (output / 'Packages').write_bytes(package_data)
    (output / 'Packages.gz').write_bytes(gzip.compress(package_data,mtime=0))
    (output / 'Packages.bz2').write_bytes(bz2.compress(package_data))
    release = ('Origin: Wii U Controller\nLabel: Wii U Controller\nSuite: stable\nVersion: 1.0\n'
               'Codename: wiiu-controller\nArchitectures: iphoneos-arm\nComponents: main\n'
               'Description: Rootful jailbreak apps for Wii U controllers\n'
               f'Date: {email.utils.format_datetime(datetime.datetime.now(datetime.timezone.utc),usegmt=True)}\n')
    for algorithm in ['MD5Sum','SHA256','SHA512']:
        release += algorithm + ':\n'
        hash_name = 'md5' if algorithm=='MD5Sum' else algorithm.lower()
        for name in ['Packages','Packages.gz','Packages.bz2']:
            data = (output/name).read_bytes()
            release += f' {hashlib.new(hash_name,data).hexdigest()} {len(data)} {name}\n'
    (output / 'Release').write_text(release)
    (output / '.nojekyll').touch()
    (output / 'CydiaIcon.png').write_bytes((output/'icon.png').read_bytes())
    (output / 'index.html').write_text(render(base,versions[0]))
    (output / 'repo.json').write_text(json.dumps({'version':versions[0]['version'],'versions':len(versions),'apt':base,'ipaSource':base+'apps.json'},indent=2)+'\n')
    print(f'Built IPA feed and {len(packages)} rootful jailbreak packages at {base}')

def render(base, latest):
    url, feed, ipa = map(html.escape,[base,base+'apps.json',latest['downloadURL']])
    return f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Wii U Controller — iOS repository</title><style>body{{font:17px/1.6 system-ui,sans-serif;background:#101827;color:#edf4ff;max-width:760px;margin:50px auto;padding:0 24px}}a{{color:#5addc5}}code{{overflow-wrap:anywhere}}.button{{display:inline-block;padding:10px 18px;border:1px solid #5addc5;border-radius:8px;margin:4px 8px 4px 0}}h2{{margin-top:36px}}</style>
<img src="icon.png" width="96" height="96" alt="Wii U Controller icon"><h1>Wii U Controller</h1>
<p>Turn an iPhone into a Wii Remote, Pro or Classic Controller. Latest release: <strong>{html.escape(latest['version'])}</strong>.</p>
<h2>Jailbreak source</h2><p>Add this URL in Zebra, Sileo or Cydia:</p><p><code>{url}</code></p>
<p><a class="button" href="zbra://sources/add/{url}">Add to Zebra</a><a class="button" href="sileo://source/{url}">Add to Sileo</a></p>
<p>The Debian package supports <strong>rootful jailbreaks, arm64 and iOS 12+</strong>. It is not a rootless package.
If you already installed the IPA, export your layouts and remove that installation before switching to the repo package. The installer prevents duplicate copies.</p>
<h2>IPA source</h2><p>Add this feed in a compatible IPA source client:</p><p><code>{feed}</code></p>
<p><a class="button" href="apps.json">View IPA feed</a><a class="button" href="{ipa}">Download IPA</a></p>
<p>An <a href="sidestore.json">older SideStore-format feed</a> is also available at <code>{url}sidestore.json</code>.</p>
<p>The main feed follows the AltStore Classic source format. Your source client must support your iOS version.
The IPA is ad-hoc signed for a jailbreak with AppSync Unified; stock iOS requires an appropriate signing route.
For a jailbroken iOS 12 device, use the jailbreak source or Filza.</p>
<h2>Connect to Wii U</h2><p>Install the Aroma receiver from GitHub Releases, restart the console, then connect the app on the same local network.
Gyro pointing uses the phone's top edge; aim at the TV center and tap CENTER.</p>
<p><a href="{REPO}">Setup and source code</a> · <a href="{REPO}/releases">All releases</a> · <a href="{REPO}/issues">Report an issue</a></p>
<p>Feeds update automatically from published releases. The APT index is unsigned and served over HTTPS.</p></html>'''

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('releases',type=Path)
    parser.add_argument('output',type=Path)
    parser.add_argument('--cache',type=Path,required=True)
    parser.add_argument('--url',default=DEFAULT_URL)
    args = parser.parse_args()
    build(json.loads(args.releases.read_text()),args.output,args.cache,args.url)
