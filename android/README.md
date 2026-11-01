# Android client

Native Java/Canvas app for Android 6.0+, package `com.brian.wiiuremote`.
Build with `./build.sh android` from the repository root; install
`dist/WiiU-Controller.apk` directly or with `adb install -r`.

Select players 1–4 and Wii Remote, Pro, Classic or experimental MotionPlus in
SETUP. Discovery and controller sockets use Wi-Fi/Ethernet rather than mobile
data. Physical controllers and other senders can reserve the selected slot.

SETUP offers layout editing, input/appearance options, layout JSON
import/export and motion/orientation settings. Portrait and landscape Remote
layouts are saved separately. Pro and Classic stay in landscape. MotionPlus
requires a hardware gyroscope; an accelerometer alone only provides tilt.
Recenter resets relative gyro orientation. Sensor axes and Remote D-pad
buttons follow the display's rotation. Gyro samples older than 200 ms are
marked invalid; normal input expires on the Wii U after 250 ms without packets.

Version 0.5.0 passed Galaxy A14 checks for five-contact Pro and Classic input,
neutral releases, portrait Remote controls, valid live gyroscope packets, and
editor persistence. Contacts were synthetic View events; gyro data came from
the phone sensor. Wii U game compatibility still needs live console checks. Neither Nunchuk nor vWii is implemented.

See [building](../docs/BUILDING.md), [protocol](../docs/PROTOCOL.md) and the root
[README](../README.md) for the feature list and compatibility limits.
