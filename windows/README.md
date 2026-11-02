# Windows PC receiver

Use the existing Android, iOS, or Nintendo 3DS client as a wireless PC gamepad.
The receiver creates controllers through LizardByte's **Virtual HID Driver**
and `libvirtualhid`, with a selectable Xbox 360, Xbox Series, DualShock 4,
DualSense, Switch Pro, or generic HID profile.

## Download and setup

[Download the Windows x64 release](https://github.com/TheStonedGamer/wiiu-controller/releases/tag/windows-v0.5.1).

1. Install the production **Virtual HID Driver** MSI from the matching
   [official release v2026.914.1218.10](https://github.com/LizardByte/libvirtualhid/releases/tag/v2026.914.1218.10).
   Reboot if requested. Windows 11 21H2+ x64 is the supported driver target;
   Windows 10 2004+ is best effort upstream. Windows ARM64 is not supported.
2. Activate the driver's **paid machine license** using
   `C:\Program Files\libvirtualhid\tools\windows\virtualhid_control.exe`.
   See [license options](https://app.lizardbyte.dev/libvirtualhid/virtual-hid-driver/).
   The driver and license are separate; neither is bundled with this app.
3. Extract the entire receiver ZIP and run `WiiU-Controller-Windows.exe`.
   The build includes .NET and the native SDK bridge; no separate .NET runtime
   or Visual C++ runtime installation is needed.
4. Choose a controller profile and click **Start receiver**. Start with
   **Xbox 360 (XInput)** for emulators expecting an Xbox controller. Other
   profiles need an emulator input backend that recognizes their HID devices,
   such as SDL or DirectInput. Allow the app through Windows Firewall on your
   private network; it receives UDP on port **26760**.
5. Put your phone/3DS and PC on the same local network. In the existing client,
   enter a PC IPv4 address shown by the receiver. Phone discovery also works,
   though the client UI still describes discovered receivers as Wii U.
   Use **Pro** or **Classic** mode for complete buttons and sticks.
6. Open **Windows game controllers** to inspect input, then select the created
   controller in your emulator's input settings and bind the controls.

Phones use slots 1–4; the current 3DS client always uses slot 2. One sender owns
each slot. Devices are created when a slot first receives input, stay attached
with neutral input after a 250 ms connection timeout, and are removed when the
receiver stops. Stop/restart to change profiles or clear assignments. OS player
numbers can differ from receiver slots, particularly with other pads attached.
The Xbox 360 profile is subject to Windows' four-controller XInput limit.

If creation fails, the app shows the driver's error and stops, releasing other
pads. Check the broker service and license in `virtualhid_control.exe`. After
restarting the broker, stop/restart the receiver as well. The SDK is pinned to
the matching driver release to keep its control protocol compatible.

## Mapping

| Client input | Logical output |
| --- | --- |
| Sticks / D-pad | Corresponding sticks / D-pad |
| B / A / Y / X | South / east / west / north by position (default) |
| L / R | Left / right bumper |
| ZL / ZR | Left / right trigger (digital 0 or 100%) |
| Plus / Minus / Home | Start / Back / Guide |
| L3 / R3 (Pro) | Stick clicks |

Uncheck **Match face buttons by position** before starting to map A/B/X/Y by
Xbox letter instead. Profile-specific labels vary. Classic has no stick clicks.
L/R remain bumpers and ZL/ZR remain triggers in both Pro and Classic modes.

Remote and MotionPlus modes provide a basic button pad: A/B → south/east,
1/2 → west/north, plus D-pad and Plus/Minus/Home. Acceleration, IR pointer,
gyro and touchpad data are not forwarded yet, even on profiles supporting those
features. This is not native Wii Remote emulation for Dolphin. Rumble feedback
is not transmitted to clients.

Use a trusted local network; the existing protocol has no authentication.
Closing the window stops the receiver and removes its devices.

## Build and publish

Install Visual Studio 2022 or newer with Desktop development with C++, CMake 3.24+,
Git, and the .NET 10 SDK. Run from PowerShell at the repository root:

```powershell
./windows/build.ps1
```

The build fetches the pinned `libvirtualhid` SDK and its pinned common submodule,
builds a native bridge with a static C++ runtime, and packages it with the
self-contained .NET application. Driver/broker sources are not built or bundled.
Output: `dist/WiiU-Controller-Windows-x64.zip` and `Windows-SHA256SUMS.txt`.

The Windows workflow uploads build artifacts on pushes and pull requests.
Pushing `windows-v<VERSION>` publishes a separate Windows release, without
replacing existing mobile/console release assets or changing the default
latest release. Application metadata reads the root `VERSION` file.

Windows installation, licensing, driver behavior and actual emulator input need
live device validation. A successful package build alone does not verify them.
