# Roadmap

- Test Classic and MotionPlus in named Wii U games, recording each game's
  input API path and calibration requirements.
- Add controller connection/extension notifications and raw WPAD read support
  where game tests demonstrate a need.
- Add Nunchuk controls and MotionPlus + Nunchuk passthrough.
- Improve gyro drift compensation and user calibration options after collecting
  stationary and moving sensor traces from several phones.
- Add physical gamepad input, per-button remapping and named layout profiles.
- Add receiver-to-client rumble feedback and optional pairing.
- Improve 3DS address persistence, discovery and player selection.
- Add iOS device automation and broader phone/tablet layout coverage.

vWii requires a separate integration; an Aroma plugin alone does not provide it.

Windows receiver: verify the Virtual HID Driver backend on a Windows PC and in emulators;
add configurable mappings and consider an emulator motion/pointer interface.
