#!/usr/bin/env bash
# Runs the instrumented smoke tests on a booted emulator (see .github/workflows/android.yml).
# Usage: scripts/emulator-tests.sh <apk-dir> <out-dir> <run-toolchain-test: 0|1>
# Writes logs, per-test results, screenshots and summary.md to <out-dir>. Exit code 0 = all passed.
set -u
APKS=$1
OUT=$2
TOOLCHAIN=${3:-1}
PKG=tech.zakomi.zakdroid.debug
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p "$OUT"
status=0

adb logcat -c || true
adb logcat -v threadtime > "$OUT/logcat.txt" 2>&1 &
LOGCAT_PID=$!

adb install -r "$APKS/app-full-x86_64-debug.apk" || { echo "Installing the app failed"; exit 1; }
adb install -r "$APKS/app-full-debug-androidTest.apk" || { echo "Installing the test APK failed"; exit 1; }

# Runs one test class; prints "class.method STATUS" lines to results.txt.
run_class() {
  local class=$1 log="$OUT/$1.txt"
  echo "::group::$class"
  adb shell am instrument -w -r -e class "tech.zakomi.zakdroid.smoke.$class" "$RUNNER" | tee "$log"
  echo "::endgroup::"
  # Status codes of `am instrument -r`: 1 started, 0 ok, -1 error, -2 failure, -3 ignored, -4 assumption failed.
  awk -v cls="$class" '
    /^INSTRUMENTATION_STATUS: test=/ { sub(/^INSTRUMENTATION_STATUS: test=/, ""); test = $0 }
    /^INSTRUMENTATION_STATUS_CODE: / {
      code = $2
      if (code == 0) r = "PASSED"; else if (code == -2 || code == -1) r = "FAILED"; else if (code == -3 || code == -4) r = "SKIPPED"; else next
      print cls "." test " " r
    }' "$log" >> "$OUT/results.txt"
  if ! grep -q "^OK (" "$log" || grep -q "Process crashed" "$log"; then
    echo "$class: not all tests passed"
    grep -q "Process crashed" "$log" && echo "$class.(process) CRASHED" >> "$OUT/results.txt"
    status=1
  fi
}

: > "$OUT/results.txt"
run_class SmokeTest
if [ "$TOOLCHAIN" = "1" ]; then
  run_class ToolchainTest
else
  echo "ToolchainTest.installToolchainUseTerminalAndRunPrograms NOT-RUN (pull request)" >> "$OUT/results.txt"
fi

# Crash and ANR detection beyond the test results.
adb exec-out run-as "$PKG" cat files/crashes/last-crash.txt > "$OUT/app-crash.txt" 2>/dev/null || true
if [ -s "$OUT/app-crash.txt" ] && ! grep -q "No such file" "$OUT/app-crash.txt"; then
  echo "The app recorded a crash (files/crashes/last-crash.txt)"; status=1
else
  rm -f "$OUT/app-crash.txt"
fi
adb exec-out run-as "$PKG" cat files/runtime/install.log > "$OUT/install.log" 2>/dev/null || true
adb pull "/sdcard/Android/data/$PKG/files/test-screenshots" "$OUT/screenshots" > /dev/null 2>&1 || true
adb exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null || true

kill "$LOGCAT_PID" 2>/dev/null || true
if grep -E "FATAL EXCEPTION|ANR in $PKG" -A2 "$OUT/logcat.txt" | grep -q "$PKG"; then
  echo "logcat shows a crash or ANR in $PKG"; status=1
  grep -E "FATAL EXCEPTION|ANR in" -A20 "$OUT/logcat.txt" > "$OUT/crashes-from-logcat.txt" || true
fi

{
  echo "## Emulator smoke tests"
  echo
  echo "| Test | Result |"
  echo "|---|---|"
  awk '{ print "| `" $1 "` | " $2 " |" }' "$OUT/results.txt"
  echo
  [ "$status" = 0 ] && echo "All tests passed; no crash or ANR in logcat." || echo "**Failed.** Logs, screenshots and the installer log are in the \`emulator-results\` artifact."
} > "$OUT/summary.md"
cat "$OUT/summary.md"
exit "$status"
