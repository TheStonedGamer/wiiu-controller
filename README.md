# Wii U Controller

[![Build and test](https://github.com/TheStonedGamer/wiiu-controller/actions/workflows/build.yml/badge.svg)](https://github.com/TheStonedGamer/wiiu-controller/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/TheStonedGamer/wiiu-controller)](https://github.com/TheStonedGamer/wiiu-controller/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Turn your Android phone, iPhone, or Nintendo 3DS into a wireless Wii U controller.**

Use touchscreen buttons and sticks, customize their layout, or aim the Wii
pointer by moving your phone. One Aroma plugin receives input from all clients,
with independent controller slots for multiplayer.

[Downloads](#download-v051) · [Setup](#setup) · [Gyro pointing](#gyro-pointing) · [Build](#build-from-source) · [Troubleshooting](#troubleshooting)

<!-- downloads:start -->
## Download v0.5.1

| Platform | Download | Requirements |
| --- | --- | --- |
| Android | [APK](https://github.com/TheStonedGamer/wiiu-controller/releases/download/v0.5.1/WiiU-Controller-android-v0.5.1.apk) | Android 6.0+ |
| iPhone | [Jailbreak IPA](https://github.com/TheStonedGamer/wiiu-controller/releases/download/v0.5.1/WiiU-Controller-ios-v0.5.1-jailbreak.ipa) | arm64, iOS 12+, active jailbreak and AppSync Unified |
| Nintendo 3DS / 2DS | [CIA](https://github.com/TheStonedGamer/wiiu-controller/releases/download/v0.5.1/WiiU-Controller-3ds-v0.5.1.cia) · [3DSX](https://github.com/TheStonedGamer/wiiu-controller/releases/download/v0.5.1/WiiU-Controller-3ds-v0.5.1.3dsx) | Homebrew-enabled console |
| Wii U | [Aroma plugin](https://github.com/TheStonedGamer/wiiu-controller/releases/download/v0.5.1/WiiU-Controller-aroma-v0.5.1.wps) | Aroma environment |

Download **the Wii U plugin and the client for your device**. Checksums are
included on the [release page](https://github.com/TheStonedGamer/wiiu-controller/releases/tag/v0.5.1).
<!-- downloads:end -->

## What it supports

| Feature | Android | iOS | 3DS / 2DS |
| --- | --- | --- | --- |
| Wii U Pro Controller | Yes | Yes | Yes |
| Wii Classic Controller | Yes | Yes | Yes |
| Wii Remote | Yes | Yes | — |
| Gyro pointer and touch aiming | Yes | Yes | — |
| MotionPlus | Experimental | Experimental | — |
| Player selection | 1–4 | 1–4 | Player 2 |
| Move and resize controls | Yes | Yes | Fixed touch controls |
| Layout import / export | Yes | Yes | — |
| Portrait / landscape Remote | Yes | Yes | — |

Phones support simultaneous buttons and both sticks, independent layouts for
each controller type, appearance options, stick deadzones, sensitivity and Y
inversion. Gyro features require an actual gyroscope; touch controls still work
without one. Haptics depend on the device.

Physical controllers take priority on their slots. Each virtual slot accepts
one sender at a time; the 3DS and a phone cannot both use player 2.

## Setup

### 1. Install the Wii U receiver

1. Download the Aroma plugin above.
2. Rename it to **`ThreeDSController.wps`**.
3. Replace the existing file on the Wii U SD card at:

   ```text
   wiiu/environments/aroma/plugins/ThreeDSController.wps
   ```

4. Keep only one copy of this receiver plugin, reinsert the SD card and restart
   the Wii U into Aroma.

### 2. Install a client

**Android:** install the APK. If Android asks, allow installation from the app
you used to download or open the file. Official release APKs retain the same
signing key, so they can update an existing official installation.

**iOS:** install the IPA through Filza with an active jailbreak and AppSync
Unified. The package is ad-hoc signed for jailbroken devices; it is not signed
or provisioned for stock iOS, the App Store or TestFlight. The iPhone 5s is a
tested installation target.

**3DS / 2DS:** install the CIA using FBI, or place the 3DSX under `sd:/3ds/` and
launch it through the Homebrew Launcher.

### iOS homebrew sources

For updates through **Zebra, Sileo or Cydia**, add:

```text
https://thestonedgamer.github.io/wiiu-controller/
```

For an installer supporting an **AltStore Classic IPA source**, use:

```text
https://thestonedgamer.github.io/wiiu-controller/apps.json
```

The jailbreak package supports rootful arm64/iOS 12+; export layouts and remove
an existing IPA copy before switching to it. The IPA feed requires a compatible
installer and signing setup. On iOS 12, use the jailbreak source or Filza.
Both feeds update automatically from published releases. See
[iOS repository instructions](docs/IOS_REPOSITORY.md) for installation and limits.

### 3. Connect and join a game

1. Put the client and Wii U on the same local network.
2. On a phone, open **SETUP**, choose an unused player slot and controller type,
   then find the Wii U or enter its IPv4 address. The iOS player and type
   selectors are separate entries in SETUP.
3. On 3DS, edit the address with the D-pad, press **X** to choose Pro or Classic,
   then **A** to connect. It always uses slot 2.
4. Open a Wii U game that supports the selected controller type and join.

Game-specific player numbers can differ from controller slot numbers. After
changing controller type, some games require you to join again. This project
does not emulate the Wii U GamePad.

## Gyro pointing

In **Wii Remote** or **Wii Remote + MotionPlus** mode:

1. Enable **AIM** if it is off.
2. Point the **top edge of the phone** toward the center of your TV.
3. Tap **CENTER**, then turn the phone left/right or up/down to aim.

The pointer pad previews your aim. Touching it overrides gyro input until you
release it. Gyro aiming works in ordinary Remote mode; MotionPlus is optional.
No sensor bar, IR blaster or camera is required.

Under **SETUP → Motion & orientation**, adjust:

| Setting | Effect |
| --- | --- |
| Aim sensitivity | 25–300%; higher values move the pointer farther for the same rotation |
| Aim smoothing | 0–200 ms; higher values reduce jitter and add response delay |
| Recenter pointer | Sets your current grip as the center without resetting MotionPlus |
| Reset MotionPlus orientation | Resets the separate relative motion orientation |
| Remote orientation | Automatic, vertical or horizontal |

At 100% sensitivity, roughly 35° horizontally or 25° vertically reaches an
edge. Gyro aiming tracks rotation, not the phone's physical position relative
to the TV: sliding it sideways without turning it does not move the pointer.
Recenter when you change grip or notice drift. Rotating the display resets
calibration. Sensor readings older than 200 ms invalidate the gyro pointer.

## Customize your controls

Open **SETUP → Edit button layout** to drag controls or resize them using their
bottom-right corners. **SAVE** keeps changes; **CANCEL** discards them. The
editor locks orientation and reserves the toolbar so its buttons stay reachable.

Pro, Classic, portrait Remote and landscape Remote layouts are saved separately.
Export/import JSON files to share layouts between Android and iOS. Settings
also offer grid snapping, stick sensitivity/deadzone, right Y inversion,
haptics, label visibility, control opacity, accent colors and packet statistics.

Remote mode includes a touch pointer, synthetic **SHAKE**, and **TILT** to
forward accelerometer input. MotionPlus adds real gyroscope data; it does not
provide Nunchuk support.

### 3DS controls

| Input | Function |
| --- | --- |
| A/B/X/Y and D-pad | Corresponding controller buttons |
| START / SELECT | Plus / Minus |
| Circle Pad | Left stick |
| New 3DS C-stick | Right stick |
| Touch right-stick area | Right stick; releasing returns it to neutral |
| Touch shoulder swap | Swap L/R with ZL/ZR |
| Touch L3 / R3 | Stick clicks in Pro mode |
| START + SELECT together | Exit |

Original 3DS/2DS models use the touch stick and shoulder swap for missing
physical controls. The touchscreen accepts one contact at a time. HOME remains
reserved for the 3DS system.

## Compatibility and testing

- The original 3DS Pro path has been used on a Wii U.
- Android 0.5.1 passed installed-app checks on a Galaxy A14: Pro/Classic
  multitouch, neutral releases, portrait controls, gyro pointer output,
  recentering, touch override, stale-sample handling and layout persistence.
  Touch and pointer-orientation checks use synthetic inputs; real gyroscope
  packet delivery was checked separately.
- iOS 0.5.1 installed over USB on a jailbroken iPhone 5s. Its touch, rotation and
  sensor behavior still needs live device testing.
- All four targets build in GitHub Actions. Host tests cover protocol encoding,
  reconnection, discovery, ownership, timeout release, motion and pointer math.

**MotionPlus and game compatibility remain experimental.** The receiver hooks
KPAD reads and basic controller/MotionPlus detection and control. Games using
raw WPAD reads, additional connection/extension callbacks or special calibration
commands may need more receiver support. Gyro pointing still needs live Wii U
feel and compatibility testing.

Nunchuk, GamePad emulation, rumble, speaker feedback and vWii are not implemented.
Aroma runs in Wii U mode; it does not extend this plugin into vWii.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Waiting / reconnecting | Confirm the receiver is installed and Aroma restarted. Check the IP, shared network and router client isolation. |
| Slot occupied | Disconnect the physical controller or virtual sender using that slot, or choose another player. |
| Connected but no input in a game | Check which controller types the game supports. Rejoin after switching type. Raw WPAD games may need additional hooks. |
| Pointer drifts or feels wrong | Point at the TV center and tap CENTER. Check sensitivity, smoothing and display orientation. |
| MotionPlus unavailable | The phone needs a real gyroscope. An accelerometer or IR transmitter cannot replace it. |
| iOS installation rejected | Confirm the jailbreak and AppSync Unified are active and use the jailbreak IPA. |
| Android update rejects its signature | Use an official release. Fork/PR APKs use another development key and cannot update the official app. |

Input expires on the receiver after 250 ms without a fresh accepted packet.
Clients reconnect after lost replies and send neutral input when stopping or
changing configuration. UDP traffic uses port **26760**, without pairing or
authentication; use your own local network.

## Build from source

Requirements: Docker Engine and the Compose plugin. Builds download their SDKs;
Android Studio and Xcode are not required on the host.

```sh
git clone https://github.com/TheStonedGamer/wiiu-controller.git
cd wiiu-controller
./build.sh                 # Android, iOS, 3DS and Aroma
./build.sh android ios     # or select platforms
./build.sh 3ds wiiu
./tests/check.sh
```

Use `sudo` if your Linux account cannot access Docker. Build products are written
to `dist/`. Android signing keys stay under the ignored `android/signing/`
directory. See [building and signing](docs/BUILDING.md) for output names,
toolchains, iOS requirements, key setup and optional ADB device tests.

### Automatic releases

Every push and pull request builds all platforms and runs the checks. After a
successful `main` build, Actions creates the version tag and publishes the five
platform packages, `SHA256SUMS.txt`, and `release.json`.

To publish a new version:

```sh
python3 tools/version.py --patch
# Update docs/RELEASE_NOTES.md, then commit and push to main.
```

The version tool synchronizes platform display versions and increments Android,
iOS and CIA installer build numbers. Published versions are preserved;
ordinary commits with the same VERSION run CI without replacing the release.
Android release signing uses an encrypted Actions secret. PR builds cannot
access that key or publish releases.

## Development

| Directory | Contents |
| --- | --- |
| `android/` | Native Java/Canvas client |
| `ios/` | Objective-C/UIKit client |
| `3ds/` | libctru client and touch controls |
| `wiiu/` | Aroma WUPS receiver |
| `docker/` | Platform build environments |
| `tests/` | Host and Android device checks |
| `tools/` | Version and release tooling |
| `docs/` | Protocol, build notes and roadmap |

[Protocol](docs/PROTOCOL.md) · [Android notes](android/README.md) ·
[iOS notes](ios/README.md) · [Roadmap](docs/ROADMAP.md)

Bug reports are welcome in [Issues](https://github.com/TheStonedGamer/wiiu-controller/issues).
Include the app/plugin version, client device, selected controller mode and
player slot, Wii U game, and steps to reproduce. Game compatibility reports
should distinguish connecting successfully from receiving input in the game.

## License

MIT. See [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
Independent homebrew software; not affiliated with Nintendo, Samsung or Apple.
