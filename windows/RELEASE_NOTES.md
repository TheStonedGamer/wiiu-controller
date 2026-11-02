Windows PC receiver for the existing Android, iPhone and Nintendo 3DS controller apps.

- Up to four receiver slots over Wi-Fi using the existing client protocol.
- LizardByte Virtual HID Driver backend with Xbox 360, Xbox Series, DualShock 4,
  DualSense, Switch Pro and generic HID profiles.
- Button and stick mapping, face button position/letter selection, and neutral
  input after a 250 ms disconnect timeout.
- Portable x64 package containing the .NET runtime and native SDK bridge.

Requires Windows 11 21H2+ x64 and a separate Virtual HID Driver installation
with an activated paid machine license. Windows 10 2004+ is best effort upstream.
Install the matching driver release:
https://github.com/LizardByte/libvirtualhid/releases/tag/v2026.914.1218.10
License information: https://app.lizardbyte.dev/libvirtualhid/virtual-hid-driver/

Extract the entire ZIP, run WiiU-Controller-Windows.exe, choose a profile,
start the receiver, and point the client at the displayed PC address.
Select the resulting gamepad in your emulator. The 3DS uses receiver slot 2.

Initial release: live Windows and emulator testing is still pending.
Motion, IR pointing, touchpad and rumble forwarding are not implemented.
See windows/README.md in the repository for full setup and build instructions.
