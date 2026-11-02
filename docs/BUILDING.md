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

## Publishing a release

The public monorepo uses the root VERSION file as its release version.
`python3 tools/version.py` checks all platform display versions.
`python3 tools/version.py 0.5.2` bumps the version and Android/iOS/CIA installer
build numbers together. Update `docs/RELEASE_NOTES.md`, commit and push to main.

After every platform builds successfully, Actions downloads only that run's
artifacts, creates versioned package names plus checksums, creates the version
tag at the tested commit and publishes a GitHub release. Release write access
is limited to the publishing job. Pull requests only build/test; they cannot
publish or read the Android signing secret.

Published versions are immutable: another main push with the same version does
not overwrite the release. Failed draft uploads can be retried for the same
commit. A conflicting existing tag/draft fails rather than replacing another
commit's packages. SDKs, signing keys and credentials remain excluded from Git.

## Windows PC receiver

Install Visual Studio 2022 or newer with Desktop development with C++, CMake 3.24+,
Git, and the .NET 10 SDK, then run `./windows/build.ps1` in PowerShell.
The Windows workflow builds the pinned libvirtualhid native SDK bridge and a
self-contained x64 ZIP. See [Windows instructions](../windows/README.md) for
driver/license requirements, profiles, and the separate Windows tag release.
The Docker build and mobile/console release pipeline target the original four
platforms.
