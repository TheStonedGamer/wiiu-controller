# Controller protocol

UDP port **26760**, big-endian multibyte values. Input samples are typically
sent at 60 Hz. Discovery sends `WUD?` and receives `WUD!` without claiming a slot.

## Common fields

| Offset | Size | Meaning |
| --- | --- | --- |
| 0 | 4 | `ANWC` for phones, `3DWC` for 3DS |
| 4 | 1 | Protocol version |
| 5 | 1 | Phone: channel 0–3; 3DS: 0 Pro / 1 Classic (always channel 1) |
| 6 | 2 | Wrapping sequence number |
| 8 | 4 | Button mask |
| 12 | 2 | Left X for Pro/Classic, pointer X for Remote |
| 14 | 2 | Left Y for Pro/Classic, pointer Y for Remote |
| 16 | 2 | Right X for Pro/Classic, Remote accelerometer X |
| 18 | 2 | Right Y for Pro/Classic, Remote accelerometer Y |

The 3DS packet is 20 bytes, version 1. Existing packets with byte 5 zero
continue to select Pro mode. The receiver routes them to slot 2.

## Phone version 1

32 bytes. Version 1 accepts modes 0, 1 and 2.

| Offset | Size | Meaning |
| --- | --- | --- |
| 20 | 2 | Remote accelerometer Z |
| 22 | 1 | Bit 0: pointer valid; other bits zero |
| 23 | 1 | Mode: 0 Remote, 1 Pro, 2 Classic |
| 24 | 8 | Reserved, zero |

Sticks and pointer use signed 16-bit values with full scale ±156. Stick Y is
positive up; pointer Y is positive down. Remote acceleration is signed milli-g,
clamped by phone clients to ±4000. The neutral Remote acceleration is (0,0,1000).
Pro uses the WPAD Pro mask (0x3FEFF); Classic uses the shared low button bits
(0xFEFF) without stick clicks. Classic L/R buttons also produce fully pressed
analog triggers (1.0); releasing returns them to 0.0. Remote uses core buttons
(0x9F1F). No Nunchuk extension is currently transmitted.

## Phone version 2: experimental MotionPlus

64 bytes. Version 2 currently accepts only mode **3**, Wii Remote + MotionPlus.
Offsets 0–31 retain the Remote meanings above, with version byte 2 and mode 3.

| Offset | Size | Meaning |
| --- | --- | --- |
| 32 | 6 | Gyroscope X/Y/Z: signed int16, 1000 units per revolution/second |
| 38 | 8 | Relative orientation quaternion W/X/Y/Z: signed int16, scale 32767 |
| 46 | 1 | Bit 0: motion sample valid; other bits zero |
| 47 | 17 | Reserved, zero |

Quaternion direction vectors use a right-handed display basis: X right,
Y toward the top edge, Z out of the display. Gyro sensors provide radians/s;
clients rotate the X/Y axes into the current display orientation, divide the
rate by 2π, and integrate relative orientation with a normalized quaternion.
The update ignores time gaps longer than 100 ms. Recenter resets quaternion to
identity; a display rotation also recenters to avoid mixing coordinate bases.

If gyro data is absent or older than 200 ms, bit 46 is clear. The receiver
supplies zero rate and identity direction vectors instead of stale motion.
Valid quaternions must have squared norm within 0.8–1.2 before normalization.
Malformed lengths, modes, flags, reserved bytes and quaternions are rejected.

The receiver exposes `WPAD_EXT_MPLUS` / `WPAD_FMT_MPLUS`, processed KPAD angular
rate (turns/s), Euler angles (turns) and direction vectors. It hooks
`KPADSetControlMplsCallback`, `KPADEnableMpls`, `KPADDisableMpls`,
`KPADGetMplsStatus`, `WPADIsMplsAttached` and `WPADIsMplsIntegrated` in addition
to the ordinary KPAD reads/probe. Callbacks run outside the receiver mutex.
These hooks cover a subset of the hardware API; raw reads and all accessory
calibration/extension behavior are not implemented.

## Client gyro pointing

Version 0.5.1 clients can populate the ordinary pointer fields (12, 14, 22)
from relative gyro orientation in either Remote mode. This needs no additional
packet format or receiver hook. Touch input overrides the gyro coordinates.

A separate calibration quaternion is stored when CENTER is tapped. The client
rotates the top-edge ray (0,1,0) by the relative quaternion, projects its yaw and
pitch into ±35°/±25° full-scale ranges, applies user sensitivity, clamps to ±1,
and optionally filters with an exponential time constant. Quaternion rotations
about the pointing ray do not move the pointer. A ray pointing behind the
calibrated forward hemisphere, or an old sensor sample, clears pointer validity.
The calibration quaternion does not alter MotionPlus orientation.

## Ownership, acknowledgements and release

Accepted phone packets receive `ANWA`; an occupied slot receives `ANWB`.
The 3DS equivalents are `3DWA` and `3DWB`. Old/out-of-order packets receive no
acknowledgement. Sequence numbers wrap with signed 16-bit comparison.

Each slot reserves its current sender IP and protocol family for 250 ms after
the last accepted sample. Ownership does not include the sender UDP port;
multiple app instances behind the same IP are not independently identified.
Physical controllers take priority. On timeout, the receiver emits one neutral
sample per KPAD consumer before falling through to physical input.

Phone clients send neutral packets in the old mode and pause for 350 ms before
changing type or slot. ACK loss triggers socket recreation; backgrounding and
editing release input. There is no encryption, pairing or authentication.
