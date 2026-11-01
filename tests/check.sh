#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
export LOCAL_UID="${SUDO_UID:-$(id -u)}"
export LOCAL_GID="${SUDO_GID:-$(id -g)}"
docker compose run --rm build-android bash -ec 'mkdir -p build/test-classes; javac --release 8 -d build/test-classes src/com/brian/wiiuremote/GyroPointer.java src/com/brian/wiiuremote/PhoneMotion.java src/com/brian/wiiuremote/ControllerPacket.java src/com/brian/wiiuremote/ControllerClient.java src/com/brian/wiiuremote/InputBuffer.java src/com/brian/wiiuremote/LayoutGeometry.java src/com/brian/wiiuremote/ControllerDiscovery.java /project/tests/android/android/os/SystemClock.java /project/tests/android/GyroPointerTest.java /project/tests/android/PhoneMotionTest.java /project/tests/android/ControllerPacketTest.java /project/tests/android/ControllerClientTest.java /project/tests/android/InputBufferTest.java /project/tests/android/ControllerRecoveryTest.java /project/tests/android/LayoutGeometryTest.java /project/tests/android/ControllerDiscoveryTest.java; java -cp build/test-classes com.brian.wiiuremote.GyroPointerTest; java -cp build/test-classes com.brian.wiiuremote.PhoneMotionTest; java -cp build/test-classes com.brian.wiiuremote.ControllerPacketTest; java -cp build/test-classes com.brian.wiiuremote.InputBufferTest; java -cp build/test-classes com.brian.wiiuremote.LayoutGeometryTest; java -cp build/test-classes com.brian.wiiuremote.ControllerClientTest; java -cp build/test-classes com.brian.wiiuremote.ControllerRecoveryTest; java -cp build/test-classes com.brian.wiiuremote.ControllerDiscoveryTest'
receiver_test_binary=$(mktemp /tmp/controller-receiver-check.XXXXXX)
trap 'rm -f "$receiver_test_binary"' EXIT
c++ -std=c++17 -Wall -Wextra -fsanitize=address,undefined -Itests/stubs tests/receiver_test.cpp -o "$receiver_test_binary"
"$receiver_test_binary"

ios_test_binary=$(mktemp /tmp/controller-ios-check.XXXXXX)
trap 'rm -f "$receiver_test_binary" "$ios_test_binary"' EXIT
c++ -std=c++17 -Wall -Wextra -fsanitize=address,undefined tests/ios_input_test.cpp -o "$ios_test_binary"
"$ios_test_binary"
