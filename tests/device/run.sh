#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.."
docker compose run --rm build-android bash -ec 'rm -rf build/device-classes; mkdir -p build/device-classes build/device-dex; javac --release 8 -cp "$ANDROID_HOME/platforms/android-35/android.jar" -d build/device-classes /project/tests/device/*.java; jar cf build/device.jar -C build/device-classes .; "$ANDROID_HOME/build-tools/35.0.0/d8" --lib "$ANDROID_HOME/platforms/android-35/android.jar" --min-api 23 --output build/device-dex build/device.jar; "$ANDROID_HOME/build-tools/35.0.0/aapt2" link -o build/instrument-base.apk -I "$ANDROID_HOME/platforms/android-35/android.jar" --manifest /project/tests/device/AndroidManifest.xml; cp build/instrument-base.apk build/instrument.apk; (cd build/device-dex && zip -q ../instrument.apk classes.dex); "$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks signing/controller.jks --ks-pass pass:android --key-pass pass:android build/instrument.apk'
adb install --no-incremental -r android/build/instrument.apk
device_result_file=$(mktemp /tmp/wiiu-device-test.XXXXXX)
trap 'rm -f "$device_result_file"' EXIT
adb shell am instrument -w -e restoreHost "${WIIU_TEST_RESTORE_HOST:-}" com.brian.wiiuremote.test/.ControllerInstrumentation | tee "$device_result_file"
rg -q 'INSTRUMENTATION_CODE: -1' "$device_result_file"
