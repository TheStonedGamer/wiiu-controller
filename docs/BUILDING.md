# Building and signing

## Docker builds

`./build.sh` builds Android, iOS, 3DS and Wii U. Pass platform names to limit
the build. Compose services are `build-android`, `build-ios`, `build-3ds` and
`build-wiiu`. Source is bind-mounted at `/project`; output goes to `dist/`.
Use a Linux Docker daemon or Docker Desktop's Linux containers.

The 3DS SDK image uses a digest, makerom uses a source commit, and Wii U SDKs
use dated image tags. Android uses SDK 35/build-tools 35.0.0 and a checked tools
download. The iOS build uses Ubuntu clang/lld, a checked iPhoneOS12.4 SDK archive
from Theos, and a pinned ldid source commit. Review upstream SDK/tool terms
before distributing build environments.

To rebuild without Docker, consult the individual build scripts and Makefiles.
A Linux iOS build can use `IOS_SDK_PATH=/path/to/iPhoneOS12.4.sdk ./ios/build.sh`,
with clang/lld, Python/Pillow and `ldid` on PATH (`LDID` overrides that path).
The default SDK cache is under `$XDG_CACHE_HOME` or `$HOME/.cache`.

## Android key

The local build generates a development key once under `android/signing/`.
Keep that key for compatible updates. The development password is `android`;
this project is not configured for store distribution. Never commit the key.

To configure a clone's trusted GitHub Actions builds, set an encrypted
`ANDROID_KEYSTORE_BASE64` Actions secret containing the base64 representation
of `android/signing/controller.jks`. The workflow only restores this secret on
trusted pushes/manual runs, never on pull requests. Without it, each fresh
build environment creates its own key. Test APKs signed with another key
require uninstalling the existing application first.

## iOS package

The IPA supports arm64/iOS 12+. `ldid` ad-hoc signs the complete app bundle,
including its resources and the application identifier, placeholder team ID
and matching keychain group from `ios/Entitlements.plist`.

This is intended for a jailbroken device with AppSync Unified. It is not an
Apple development, enterprise, App Store or TestFlight signature and has no
provisioning profile. Stock devices require a separate valid signing workflow.

## Checks

`./tests/check.sh` runs Java protocol/client/discovery/input/geometry/motion
tests inside the Android container, then compiles the actual receiver hooks
against host stubs with address/undefined-behavior sanitizers. It also checks
the iOS input queue and motion integration on the host.

Build the Android image first (`docker compose build build-android`). The host
needs a C++ compiler. `tests/device/run.sh` additionally requires ADB, an
unlocked authorized Android device, and the matching signed APK installed.
It dispatches synthetic contacts through the installed application's View;
it does not validate the physical touchscreen or real sensor behavior.

After an iOS build, validate its package:

```sh
docker compose run --rm build-ios ldid -e build/Payload/WiiUController.app/WiiUController > /tmp/controller-entitlements.plist
python3 tests/validate_ipa.py dist/WiiU-Controller-iPhone5s-JAILBREAK.ipa /tmp/controller-entitlements.plist
```

The CIA uses homebrew unique ID `0xF3D01`; forks should allocate their own
identity when distributing alongside this app.
