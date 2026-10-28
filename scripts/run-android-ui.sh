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
# Only this host bridge may drive the disposable emulator's simulated modem.
# The native integration test communicates requests through bounded JSON files;
# it receives actual Android Telecom calls instead of injecting fixture state.
rm -f app/build/emulator-call-driver.ready.json
python3 -u scripts/emulator-call-driver.py app/build/emulator-call-driver.jsonl > app/build/emulator-call-driver.log 2>&1 &
call_driver_pid=$!
stop_call_driver() {
  kill "$call_driver_pid" 2>/dev/null || true
  wait "$call_driver_pid" 2>/dev/null || true
}
trap stop_call_driver EXIT
for call_driver_attempt in {1..50}; do
  if [ -f app/build/emulator-call-driver.ready.json ]; then break; fi
  if ! kill -0 "$call_driver_pid" 2>/dev/null; then
    cat app/build/emulator-call-driver.log >&2
    exit 1
  fi
  sleep .2
done
test -f app/build/emulator-call-driver.ready.json
test_status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || test_status=$?
mkdir -p app/build/ui-snapshots
# Keep failing renders for visual debugging, even if instrumentation failed.
adb pull /sdcard/Download/DA-CALLER-snapshots/ app/build/ui-snapshots/ || true
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
python3 scripts/verify-ui-matrix.py app/build/ui-snapshots
python3 scripts/verify-personalization-matrix.py app/build/ui-snapshots
python3 scripts/verify-call-integration-matrix.py app/build/ui-snapshots
python3 scripts/verify-island-matrix.py app/build/ui-snapshots
python3 scripts/verify-external-island-matrix.py app/build/ui-snapshots
