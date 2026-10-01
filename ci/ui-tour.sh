#!/usr/bin/env bash
# Runs the UI tour on a booted emulator, then collects screenshots and logs.
# Used by .github/workflows/ui-tour.yml. Never fails the step itself; the result is in tour-result.txt.
set -u
PKG=io.github.themovementsignal.training

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c || true

adb shell am instrument -w -r \
  -e class "$PKG.UiTourTest" \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee instrument.txt

mkdir -p out/screens
adb exec-out run-as "$PKG" tar -c files/screens > out/screens.tar 2>/dev/null || true
tar -xf out/screens.tar -C out 2>/dev/null && mv out/files/screens/* out/screens/ 2>/dev/null || true
adb exec-out run-as "$PKG" cat files/errors.log > out/errors.log 2>/dev/null || true
adb logcat -d > out/logcat.txt || true
cp instrument.txt out/instrument.txt

if grep -q "^OK (" instrument.txt; then echo PASS > out/tour-result.txt; else echo FAIL > out/tour-result.txt; fi

echo "===== Crashes / app errors ====="
adb logcat -d -b crash || true
grep -E "AndroidRuntime|UiTour|Training" out/logcat.txt | tail -200 || true
echo "===== App error log ====="
cat out/errors.log 2>/dev/null || true
echo "===== Result: $(cat out/tour-result.txt) ====="
ls -la out/screens || true
