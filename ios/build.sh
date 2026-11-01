#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
ios_sdk_path="${IOS_SDK_PATH:-${XDG_CACHE_HOME:-$HOME/.cache}/wiiu-controller/ios-sdk/iPhoneOS12.4.sdk}"
test -d "$ios_sdk_path" || { echo 'Set IOS_SDK_PATH to an iPhoneOS SDK directory'; exit 1; }
mkdir -p build/Payload/WiiUController.app ../dist
clang -target arm64-apple-ios12.0 -isysroot "$ios_sdk_path" -fobjc-arc -fblocks -fuse-ld=lld -O2 -Wall -Wextra -Wno-unused-parameter -framework UIKit -framework Foundation -framework QuartzCore -framework CoreGraphics -framework CoreMotion main.m -o build/Payload/WiiUController.app/WiiUController
cp Info.plist build/Payload/WiiUController.app/Info.plist
python3 package.py
