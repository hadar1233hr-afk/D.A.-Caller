#!/usr/bin/env bash
# Run only against the disposable emulator supplied by the CI action.
set -euo pipefail
adb shell wm size 480x854
adb shell wm density 240
# Match the reference/target device's native Android three-button navigation.
# API33 Google APIs images ship this SystemUI overlay; do not fake an app bar.
adb shell cmd overlay enable-exclusive --user 0 --category com.android.internal.systemui.navbar.threebutton
sleep 2
mkdir -p app/build
{
  adb shell wm size
  adb shell wm density
  adb shell getprop qemu.hw.mainkeys
  adb shell cmd overlay list --user 0
  adb shell settings get secure navigation_mode
} > app/build/ui-device-config.txt
adb shell rm -rf /sdcard/Download/DA-CALLER-snapshots
test_status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || test_status=$?
mkdir -p app/build/ui-snapshots
# Keep failing renders for visual debugging, even if instrumentation failed.
adb pull /sdcard/Download/DA-CALLER-snapshots/ app/build/ui-snapshots/ || true
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
python3 scripts/verify-ui-matrix.py app/build/ui-snapshots
python3 scripts/verify-personalization-matrix.py app/build/ui-snapshots
