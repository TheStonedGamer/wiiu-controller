# Third-party components

The application sources are licensed under MIT. Build tools, SDKs, system
frameworks and templates retain their upstream terms:

- [devkitPro](https://github.com/devkitPro): devkitARM, libctru, devkitPPC and wut.
- [Wii U Plugin System](https://github.com/wiiu-env/WiiUPluginSystem): WUPS SDK and plugin template.
- [Project CTR](https://github.com/3DSGuy/Project_CTR): makerom.
- [Procursus ldid](https://github.com/ProcursusTeam/ldid): local iOS ad-hoc signing tool.
- [Theos SDK collection](https://github.com/theos/sdks): iOS SDK download for the Linux build.
- [Android SDK](https://developer.android.com/studio): platform tools and build tools.
- [Pillow](https://python-pillow.org): package artwork generation.

SDKs, external tool source, signing keys and container images are not vendored
in this repository. The 3DS package currently uses devkitPro's default icon.

## Windows receiver

The Windows native bridge statically links the MIT-licensed libvirtualhid SDK
by LizardByte, pinned to commit 53e1a949fc0784af716b782ddfa6c647cafd1f05
(release v2026.914.1218.10), and its MIT-licensed lizardbyte-common dependency.
The package includes both copyright/license notices under `licenses/`.
Sources: https://github.com/LizardByte/libvirtualhid and
https://github.com/LizardByte/lizardbyte-common.

The separately installed Virtual HID Driver, broker and machine license are
provided by LizardByte under its own terms. Driver and broker binaries are not
bundled with the receiver. See the upstream license map:
https://github.com/LizardByte/libvirtualhid/blob/v2026.914.1218.10/LICENSES/license-map.md
