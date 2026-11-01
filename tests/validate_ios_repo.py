#!/usr/bin/env python3
"""Verify live-feed metadata, package hashes, compression and packaged app identity."""
import bz2
import gzip
import hashlib
import json
import io
import tarfile
import plistlib
import subprocess
import sys
import tempfile
from pathlib import Path

root = Path(sys.argv[1])
source = json.loads((root/'apps.json').read_text())
assert source['apps'][0]['bundleIdentifier'] == 'com.brian.wiiucontroller.ios'
versions = source['apps'][0]['versions']
legacy = json.loads((root/'sidestore.json').read_text())
assert legacy['apps'][0]['versions'][0]['downloadURL'] == versions[0]['downloadURL']
assert set(legacy) <= {'name','identifier','sourceURL','apps','news'}
assert versions and all(v['size'] > 0 and v['downloadURL'].endswith('.ipa') for v in versions)
assert 'keychain-access-groups' in source['apps'][0]['appPermissions']['entitlements']
assert 'NSMotionUsageDescription' in source['apps'][0]['appPermissions']['privacy']
data = (root/'Packages').read_bytes()
assert gzip.decompress((root/'Packages.gz').read_bytes()) == data
assert bz2.decompress((root/'Packages.bz2').read_bytes()) == data
paragraphs = [dict(line.split(': ',1) for line in block.splitlines()) for block in data.decode().strip().split('\n\n')]
assert len(paragraphs) == len(versions)
for fields in paragraphs:
    package = root/fields['Filename']; package_data = package.read_bytes()
    assert len(package_data) == int(fields['Size'])
    assert hashlib.sha256(package_data).hexdigest() == fields['SHA256']
    assert fields['Architecture'] == 'iphoneos-arm'
    with tarfile.open(fileobj=io.BytesIO(subprocess.check_output(['dpkg-deb','--fsys-tarfile',str(package)]))) as archive:
        assert archive.getmember('.').mode == 0o755
        assert all(member.uid == 0 and member.gid == 0 for member in archive.getmembers())
    assert subprocess.check_output(['dpkg-deb','-f',str(package),'Version'],text=True).strip() == fields['Version']
    with tempfile.TemporaryDirectory(prefix='controller-deb-check-') as tmp:
        subprocess.run(['dpkg-deb','-R',str(package),tmp],check=True,stdout=subprocess.DEVNULL)
        app = Path(tmp)/'Applications/WiiUController.app'
        info = plistlib.loads((app/'Info.plist').read_bytes())
        assert info['CFBundleIdentifier'] == fields['Package']
        assert info['CFBundleShortVersionString']+'-1' == fields['Version']
        assert (app/'WiiUController').stat().st_mode & 0o111
        assert (app/'_CodeSignature/CodeResources').exists()
        for name in ['preinst','postinst','postrm']:
            subprocess.run(['sh','-n',str(Path(tmp)/'DEBIAN'/name)],check=True)
release = (root/'Release').read_text().split('SHA256:\n')[1].split('SHA512:')[0]
for line in release.strip().splitlines():
    digest,size,name = line.split()
    content = (root/name).read_bytes()
    assert int(size) == len(content) and hashlib.sha256(content).hexdigest() == digest
assert (root/'icon.png').read_bytes().startswith(b'\x89PNG')
print('IPA feed, DEB app identity, executable permissions, scripts, package hashes and APT indexes verified')
