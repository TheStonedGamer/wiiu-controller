# iOS client

Native Objective-C/UIKit controller, arm64, minimum iOS 12.0. The iPhone 5s is
supported. Build with `./build.sh ios`; output is
`dist/WiiU-Controller-iPhone5s-JAILBREAK.ipa`.

The IPA is ad-hoc signed with ldid and includes application-identifier
entitlements and a bundle resource signature. Install with Filza on an active
jailbreak with AppSync Unified. It has no Apple certificate/provisioning and
cannot be installed directly on stock iOS. The prior entitlement-fixed build
installed successfully on an iPhone 5s; current input and sensors still need
hardware testing.

Features include players 1–4, Remote/Pro/Classic/experimental MotionPlus,
multitouch, floating sticks, pointer/shake, editable layouts, JSON
import/export, appearance options, haptics where supported, stick settings,
quick-tap buffering and network discovery/reconnection.

Remote mode supports portrait and both landscape orientations, with separate
portrait layouts, orientation locks, and sensor/D-pad remapping. MotionPlus
uses CoreMotion's real gyroscope and relative orientation integration;
recenter is under Motion & orientation. Sensor readings older than 200 ms are
not forwarded as valid motion. Gyro orientation can drift. Basic KPAD
MotionPlus support does not guarantee game compatibility.

The layout export sheet can save/share a JSON file; import uses the iOS
document picker. Files use the same schema as the Android client.

See [building/signing](../docs/BUILDING.md) and [protocol](../docs/PROTOCOL.md).

## Gyro pointing (0.5.1)

In either Remote mode, point the phone's top edge at the TV center and tap
CENTER. AIM ON/OFF toggles gyro pointing, and the pointer pad provides a touch
override and aim preview. Motion & orientation adjusts aim sensitivity and
smoothing. CENTER preserves MotionPlus's independent orientation. Rotating the
screen resets calibration; stale gyro samples invalidate the pointer.
