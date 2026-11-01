# Wii U Controller

Use an Android phone, iPhone or Nintendo 3DS as a Wi-Fi controller for Wii U.
One Aroma plugin receives input for up to four controller slots.

| Client | Controller types | Players | Package |
| --- | --- | --- | --- |
| Android 6.0+ | Wii Remote, Pro, Classic, experimental MotionPlus | 1–4 | APK |
| iOS 12+ arm64, jailbroken with AppSync Unified | Wii Remote, Pro, Classic, experimental MotionPlus | 1–4 | IPA |
| Nintendo 3DS / 2DS homebrew | Pro, Classic | 2 | CIA / 3DSX |
| Wii U with Aroma | Receiver for all clients | Four independent slots | WPS |

Version 0.5.1 adds gyro pointing with calibration, smoothing and touch override,
building on Classic Controller, phone rotation and experimental MotionPlus support. Physical controllers take priority over virtual input on the
same slot. Two devices cannot own the same slot simultaneously.

## Install

1. Put `ThreeDSController.wps` in `wiiu/environments/aroma/plugins/` on the Wii U
   SD card, then restart Aroma. Update the plugin when updating to 0.5.0.
2. Install the APK on Android, or install the jailbreak IPA using Filza and
   AppSync Unified on iOS. The IPA is locally ad-hoc signed, with application
   identifier entitlements; it has no Apple provisioning profile.
3. Connect the phone and Wii U to the same local network. In **SETUP**, choose
   the controller type and player, then find the Wii U or enter its IPv4 address.
4. Join a game that supports the selected controller type. A game can assign
   player numbers differently from the controller slot selected here.

For 3DS, install the CIA with FBI or launch the 3DSX with the Homebrew Launcher.
Use the D-pad to edit the Wii U address, **X** to select Pro or Classic, and **A**
to connect. The 3DS uses slot 2; leave that slot free. **START + SELECT** exits.
The touchscreen provides a right stick, stick clicks in Pro mode, and a
shoulder swap for accessing ZL/ZR on original 3DS models. New 3DS models can
also use the C-stick and physical ZL/ZR.

## Phone controls

- Multi-touch buttons, floating sticks, pointer area and shake button.
- **MODE** changes controller type. Rejoin the game if required.
- **SETUP → Edit button layout** moves/resizes controls. Save, cancel and reset
  are available; the toolbar stays accessible.
- Separate layouts for Pro, Classic, horizontal Remote and vertical Remote.
- Layout JSON import/export works across Android and iOS.
- Stick sensitivity/deadzone, right Y inversion, haptics, grid snapping,
  control opacity, labels, accent colors and packet diagnostics.
- **Motion & orientation** offers automatic rotation, vertical/horizontal Wii
  Remote orientation and motion recentering. Pro and Classic use landscape.
- **TILT** forwards accelerometer input. MotionPlus additionally uses a real
  hardware gyroscope, with relative orientation and recentering. Devices
  without a gyroscope retain ordinary Remote, Pro and Classic support.

Motion axes and the Remote D-pad follow display rotation, including reverse
landscape. Layout editing locks the current orientation. Rotate the phone to
change the Remote layout when automatic rotation is selected.

## Point at the TV

Choose **Wii Remote** or **Wii Remote + MotionPlus** on a phone with a gyroscope.
Gyro aiming starts enabled on supported phones. Point the **top edge** of the
phone toward the center of your TV and tap **CENTER**, then turn the phone to
move the pointer. This uses relative rotation, so sideways translation alone
does not move the cursor. Recenter after drift or changing your grip.

- **AIM ON/OFF** switches between gyro and touch-only pointing.
- Touching the pointer pad overrides gyro input until you release it.
- **SETUP → Motion & orientation** adjusts aim sensitivity (25–300%) and
  smoothing (0–200 ms). At 100%, approximately 35° horizontally or 25° vertically
  reaches an edge of the pointer range.
- **CENTER** recalibrates only the pointer, preserving MotionPlus orientation.
  A separate setting resets the MotionPlus orientation if needed.
- Portrait/landscape changes reset both coordinate frames. Sensor data older
  than 200 ms hides the gyro pointer rather than leaving it stuck onscreen.

The pointer pad previews the transmitted aim position. Gyro aiming works with
ordinary Remote packets; MotionPlus mode is optional. No camera or sensor bar
is required. Live Wii U game feel and iOS sensor behavior still need testing.

## Build

Install Docker Engine and the Compose plugin. From the repository root:

```sh
./build.sh                 # all four platforms
./build.sh android ios     # selected platforms
./build.sh 3ds wiiu
./tests/check.sh
```

Run with `sudo` if your user cannot access Docker. On Linux, the script passes
your UID/GID into containers. No Android Studio, Xcode or console SDK install
on the host is required. The Docker builds download their SDKs and toolchains.

| Output | Purpose |
| --- | --- |
| `dist/WiiU-Controller.apk` | Android installer |
| `dist/WiiU-Controller-iPhone5s-JAILBREAK.ipa` | Jailbroken arm64 iOS 12+ installer |
| `dist/ThreeDSController.cia` | 3DS installer |
| `dist/ThreeDSController.3dsx` | 3DS Homebrew Launcher executable |
| `dist/ThreeDSController.wps` | Aroma receiver plugin |

GitHub Actions builds each target separately on pushes and pull requests, runs
the host tests, validates the IPA identity/entitlements, and uploads packages
as workflow artifacts. Download them from the **Actions** tab after a run.

Android builds reuse `android/signing/controller.jks` locally. Trusted Actions
builds restore the same key from the encrypted `ANDROID_KEYSTORE_BASE64`
repository secret; the key file is excluded from Git. Forks and pull requests
without that secret generate a different development key, so those APKs cannot
update an installation signed with the original key. See
[building and signing](docs/BUILDING.md).

## Repository

```text
android/     Native Java application
3ds/         libctru application and touchscreen controls
ios/         Native Objective-C/UIKit application
wiiu/        Aroma WUPS receiver
docker/     Container toolchains
tests/      Protocol, input, motion and receiver tests
docs/       Protocol and development notes
```

## Compatibility and testing

The original 3DS-to-Wii-U Pro path has been used on hardware. The Android 0.5.0 build passed installed-app Pro/Classic multitouch, portrait
Remote, live gyro packet and layout-editor checks on a Galaxy A14. The iOS app installed successfully on a jailbroken iPhone 5s.

The new modes have host test and cross-build coverage. Android portrait
controls and real gyroscope packets were checked on a phone; live Wii U game
compatibility and iOS rotation/sensors still need hardware testing. MotionPlus is experimental: the receiver supplies processed
KPAD gyro data and basic MotionPlus detection/control hooks, but does not
implement the complete Wii Remote hardware stack. Games using raw WPAD input,
connection/extension callbacks, calibration commands or special accessory
behavior may need more hooks. Nunchuk, rumble, speaker feedback, GamePad and
vWii support are not implemented. Gyro orientation is relative and can drift;
use recenter and the game's calibration when available.

Input expires after 250 ms without accepted packets and produces a neutral
release. Clients reconnect when acknowledgements stop and release input on
backgrounding or configuration changes. Transport is local-network UDP,
without pairing/authentication; network client isolation can block discovery.

See [protocol](docs/PROTOCOL.md), [Android](android/README.md),
[iOS](ios/README.md), [roadmap](docs/ROADMAP.md) and
[third-party notices](THIRD_PARTY_NOTICES.md).

MIT license. Independent homebrew project; Nintendo trademarks belong to their
respective owners.
