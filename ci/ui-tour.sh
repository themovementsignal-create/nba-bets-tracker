#!/usr/bin/env bash
# Runs the UI tour on a booted emulator, then collects screenshots and logs.
# Used by .github/workflows/ui-tour.yml. Never fails the step itself; the result is in tour-result.txt.
set -u
PKG=com.muir.bear

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c || true
# The emulator's own launcher sometimes stalls on CI runners; hide "isn't responding" pop-ups so
# they don't cover screenshots or steal input from the tour.
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true

run_tests() {
  adb shell am instrument -w -r \
    -e class "$PKG.MigrationTest,$PKG.WakeAlarmTest,$PKG.UiTourTest" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee instrument.txt
}
run_tests
# Known Compose test-framework race on slow CI emulators (issuetracker.google.com/issues/325299275):
# retry once for that exact error only, from a clean app state, and note it. Any other failure stands.
RETRIED=""
if ! grep -q "^OK (" instrument.txt && grep -q "performMeasureAndLayout called during measure layout" instrument.txt; then
  cp instrument.txt instrument-attempt1.txt
  adb shell pm clear "$PKG" >/dev/null 2>&1 || true
  RETRIED="yes"
  run_tests
fi

mkdir -p out/screens
adb exec-out run-as "$PKG" tar -c files/screens > out/screens.tar 2>/dev/null || true
tar -xf out/screens.tar -C out 2>/dev/null && mv out/files/screens/* out/screens/ 2>/dev/null || true
adb exec-out run-as "$PKG" cat files/errors.log > out/errors.log 2>/dev/null || true
adb logcat -d > out/logcat.txt || true
cp instrument.txt out/instrument.txt
if [ -n "$RETRIED" ]; then
  cp instrument-attempt1.txt out/instrument-attempt1.txt
  echo "Retried once after the Compose test-clock race (see instrument-attempt1.txt)." > out/RETRIED.txt
fi

if grep -q "^OK (" instrument.txt; then echo PASS > out/tour-result.txt; else echo FAIL > out/tour-result.txt; fi

echo "===== Crashes / app errors ====="
adb logcat -d -b crash || true
grep -E "AndroidRuntime|UiTour|Training" out/logcat.txt | tail -200 || true
echo "===== App error log ====="
cat out/errors.log 2>/dev/null || true
echo "===== Result: $(cat out/tour-result.txt) ====="
ls -la out/screens || true
