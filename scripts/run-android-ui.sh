#!/usr/bin/env bash
# Run only against the disposable emulator supplied by the CI action.
set -euo pipefail
adb shell wm size 480x854
adb shell wm density 240
adb shell rm -rf /sdcard/Download/DA-CALLER-snapshots
test_status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || test_status=$?
mkdir -p app/build/ui-snapshots
# Keep failing renders for visual debugging, even if instrumentation failed.
adb pull /sdcard/Download/DA-CALLER-snapshots/ app/build/ui-snapshots/ || true
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
python3 scripts/verify-ui-matrix.py app/build/ui-snapshots
