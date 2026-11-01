#!/usr/bin/env python3
"""Check the packaged app identity and the entitlements read from its executable."""
import plistlib
import struct
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    assert archive.testzip() is None, 'Archive CRC failure'
    prefix = 'Payload/WiiUController.app/'
    info = plistlib.loads(archive.read(prefix + 'Info.plist'))
    executable = archive.read(prefix + info['CFBundleExecutable'])
    magic, cpu = struct.unpack_from('<II', executable)
    assert (magic, cpu) == (0xFEEDFACF, 0x0100000C), 'Expected arm64 Mach-O'
    assert info['MinimumOSVersion'] == '12.0'
    assert info['CFBundlePackageType'] == 'APPL'
    assert info['UISupportedInterfaceOrientations']
    assert archive.read(prefix + '_CodeSignature/CodeResources')
    assert not any(name.endswith('embedded.mobileprovision') for name in archive.namelist())
with open(sys.argv[2], 'rb') as stream:
    entitlements = plistlib.load(stream)
identifier = entitlements['application-identifier']
assert identifier.split('.', 1)[1] == info['CFBundleIdentifier'], 'App identifier mismatch'
assert identifier.startswith(entitlements['com.apple.developer.team-identifier'] + '.')
assert identifier in entitlements['keychain-access-groups']
print('IPA CRC, arm64 executable, iOS target, resource signature and identity entitlements verified')
