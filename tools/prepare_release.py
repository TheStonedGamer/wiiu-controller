#!/usr/bin/env python3
"""Create versioned release assets from successful platform build artifacts."""
import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path
from version import check, version

PACKAGES = {
    'ThreeDSController.cia': 'WiiU-Controller-3ds-v{version}.cia',
    'ThreeDSController.3dsx': 'WiiU-Controller-3ds-v{version}.3dsx',
    'ThreeDSController.wps': 'WiiU-Controller-aroma-v{version}.wps',
    'WiiU-Controller.apk': 'WiiU-Controller-android-v{version}.apk',
    'WiiU-Controller-iPhone5s-JAILBREAK.ipa': 'WiiU-Controller-ios-v{version}-jailbreak.ipa',
}

def prepare(source, output):
    check(); release_version = version()
    output.mkdir(parents=True,exist_ok=True)
    files = {}
    # Fail before copying if any platform was omitted or uploaded twice.
    for original, name in PACKAGES.items():
        matches = list(source.rglob(original))
        if len(matches) != 1 or matches[0].stat().st_size == 0:
            raise ValueError(f'Expected one nonempty {original}, found {len(matches)}')
        files[name.format(version=release_version)] = matches[0]
    hashes = {}
    for name, path in sorted(files.items()):
        target = output / name
        shutil.copyfile(path,target)
        hashes[name] = hashlib.sha256(target.read_bytes()).hexdigest()
    (output / 'SHA256SUMS.txt').write_text(''.join(f'{digest}  {name}\n' for name,digest in hashes.items()))
    (output / 'release.json').write_text(json.dumps({'version':release_version,'sha256':hashes},indent=2)+'\n')
    print(f'Prepared {len(files)} packages for v{release_version}')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path)
    parser.add_argument('output',type=Path)
    args = parser.parse_args()
    prepare(args.source,args.output)
