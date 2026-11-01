#!/usr/bin/env python3
"""Keep all app display versions in sync; increment installer build numbers on a bump."""
import argparse
import plistlib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def version():
    value = (ROOT / 'VERSION').read_text().strip()
    if not re.fullmatch(r'0|[1-9][0-9]*', value.split('.')[0]) or not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', value):
        raise ValueError('VERSION must be MAJOR.MINOR.PATCH')
    return value

def check():
    value = version()
    android = (ROOT / 'android/AndroidManifest.xml').read_text()
    info = plistlib.loads((ROOT / 'ios/Info.plist').read_bytes())
    assert f'android:versionName="{value}"' in android, 'Android version differs from VERSION'
    assert info['CFBundleShortVersionString'] == value, 'iOS version differs from VERSION'
    assert f'WUPS_PLUGIN_VERSION("{value}")' in (ROOT / 'wiiu/src/main.cpp').read_text(), 'Aroma version differs from VERSION'
    assert f'3DS Wii U Controller {value}' in (ROOT / '3ds/source/main.c').read_text(), '3DS version differs from VERSION'
    print(value)

def bump(value):
    if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', value):
        raise ValueError('Use MAJOR.MINOR.PATCH')
    old = version()
    if tuple(map(int,value.split('.'))) <= tuple(map(int,old.split('.'))):
        raise ValueError('New version must be greater than current version')
    p = ROOT / 'android/AndroidManifest.xml'
    text = re.sub(r'android:versionName="[^"]+"', f'android:versionName="{value}"', p.read_text())
    text = re.sub(r'android:versionCode="([0-9]+)"', lambda m: f'android:versionCode="{int(m[1])+1}"', text)
    p.write_text(text)
    p = ROOT / 'ios/Info.plist'
    info = plistlib.loads(p.read_bytes()); info['CFBundleShortVersionString'] = value
    info['CFBundleVersion'] = str(int(info['CFBundleVersion'])+1)
    p.write_bytes(plistlib.dumps(info,sort_keys=False))
    p = ROOT / 'wiiu/src/main.cpp'
    p.write_text(re.sub(r'WUPS_PLUGIN_VERSION\("[^"]+"\)',f'WUPS_PLUGIN_VERSION("{value}")',p.read_text()))
    p = ROOT / '3ds/source/main.c'
    text = re.sub(r'3DS Wii U Controller [0-9.]+',f'3DS Wii U Controller {value}',p.read_text())
    text = re.sub(r'stage\("[0-9.]+: entered main"\)',f'stage("{value}: entered main")',text)
    p.write_text(text)
    p = ROOT / '3ds/app.rsf'
    text = re.sub(r'RemasterVersion: ([0-9]+)',lambda m:f'RemasterVersion: {int(m[1])+1}',p.read_text())
    p.write_text(text)
    p = ROOT / 'compose.yaml'
    p.write_text(re.sub(r'-ver ([0-9]+)',lambda m:f'-ver {int(m[1])+1}',p.read_text()))
    readme = ROOT / 'README.md'
    if readme.exists():
        text = readme.read_text()
        start, end = text.index('<!-- downloads:start -->'), text.index('<!-- downloads:end -->')
        text = text[:start] + text[start:end].replace(old,value) + text[end:]
        text = re.sub(r'\[Downloads\]\(#download-v[0-9]+\)', '[Downloads](#download-v'+value.replace('.','')+')', text)
        readme.write_text(text)
    (ROOT / 'VERSION').write_text(value+'\n')
    check()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('new_version',nargs='?',help='Bump versions and installer build numbers; omit to check')
    parser.add_argument('--patch',action='store_true',help='Increment the patch version')
    args = parser.parse_args()
    if args.patch and args.new_version: parser.error('Use --patch or an explicit version, not both')
    if args.patch:
        parts = list(map(int,version().split('.'))); parts[2] += 1
        bump('.'.join(map(str,parts)))
    elif args.new_version: bump(args.new_version)
    else: check()
