#!/usr/bin/env bash
set -euo pipefail

parse_focused_component() {
  awk '
    {
      for (field_index = 1; field_index <= NF; field_index++) {
        token = $field_index
        gsub(/^[^[:alnum:]_]+/, "", token)
        gsub(/[^[:alnum:]_.$\/]+$/, "", token)
        if (token ~ /^[[:alnum:]_.]+\/[[:alnum:]_.$]+$/) {
          print token
          exit
        }
      }
    }
  '
}

if [ "${1:-}" = "--parse-focused-component" ]; then
  parse_focused_component
  exit 0
fi

APK_PATH="${1:?Usage: signed_release_emulator_smoke.sh <signed-release-apk> <test-apk>}"
TEST_APK_PATH="${2:?Usage: signed_release_emulator_smoke.sh <signed-release-apk> <test-apk>}"
OUTPUT_DIR="$(dirname "$APK_PATH")"
ADB_SERIAL="${ADB_SERIAL:-}"
EMULATOR_ONLY="${EMULATOR_ONLY:-true}"

if [ ! -f "$APK_PATH" ]; then
  echo "Missing release APK at: $APK_PATH" >&2
  exit 1
fi

if [ ! -f "$TEST_APK_PATH" ]; then
  echo "Missing test APK at: $TEST_APK_PATH" >&2
  exit 1
fi

if ! unzip -Z1 "$TEST_APK_PATH" | grep -E '^classes([0-9]+)?\.dex$' >/dev/null; then
  echo "Invalid test APK: no classes.dex found in $TEST_APK_PATH" >&2
  echo "Release smoke needs a dexed instrumentation APK." >&2
  exit 1
fi

if [ "$EMULATOR_ONLY" != "true" ]; then
  echo "Release emulator smoke requires EMULATOR_ONLY=true." >&2
  exit 1
fi

if [ -z "$ADB_SERIAL" ]; then
  ADB_SERIAL="$(adb devices | awk '/^emulator-[0-9][0-9]*[[:space:]]device$/{print $1; exit}')"
fi

if [ -z "$ADB_SERIAL" ]; then
  echo "No connected emulator in state 'device' found." >&2
  echo "Connect an Android emulator and rerun, or keep test off." >&2
  exit 1
fi

if [[ "$ADB_SERIAL" != emulator-* ]]; then
  echo "ADB_SERIAL=$ADB_SERIAL is not an emulator. Phone testing is disabled for this script." >&2
  exit 1
fi

ADB=(adb -s "$ADB_SERIAL")

PACKAGE_NAME="app.codecks"
TEST_PACKAGE_NAME="app.codecks.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.codecks.ui.app.MainActivityStartupInstrumentedTest#coldStartReachesResumedActivityAndProcessSurvives"

STATE_FILE="$OUTPUT_DIR/package-state.txt"
INSTRUMENTATION_OUTPUT="$OUTPUT_DIR/emulator-instrumentation.txt"
LOGCAT_OUTPUT="$OUTPUT_DIR/emulator-logcat.txt"

adb_shell() {
  "${ADB[@]}" shell "$@"
}

print_tail() {
  local file="$1"
  local lines="${2:-120}"
  if [ -f "$file" ]; then
    echo "----- tail: $file -----"
    tail -n "$lines" "$file"
    echo "----------------------"
  fi
}

fail_with_context() {
  local message="$1"
  echo "Release emulator smoke failed: $message" >&2
  print_tail "$INSTRUMENTATION_OUTPUT"
  print_tail "$LOGCAT_OUTPUT"
  exit 1
}

restore_foreground() {
  local launcher_component="$1"
  adb_shell am start -n "$launcher_component" >/dev/null 2>&1 || true
}

current_foreground_component() {
  local component
  component="$(
    adb_shell dumpsys window windows 2>/dev/null \
      | awk '/mCurrentFocus|mFocusedApp/' \
      | parse_focused_component
  )" || component=""
  if [ -n "$component" ]; then
    printf '%s\n' "$component"
    return
  fi
  adb_shell dumpsys activity activities 2>/dev/null \
    | awk '/topResumedActivity|mResumedActivity/' \
    | parse_focused_component
}

collect_package_state() {
  {
    echo "timestamp=$(date -u +%FT%TZ)"
    if adb_shell pm list packages --user 0 "$PACKAGE_NAME" | grep -q "package:$PACKAGE_NAME"; then
      echo "installed_before=present"
      echo "apk_path_before=$(adb_shell pm path --user 0 "$PACKAGE_NAME" 2>/dev/null | sed -n '1p' | tr -d '\r')"
      echo "version_before=$(adb_shell dumpsys package --user 0 "$PACKAGE_NAME" 2>/dev/null | awk '/versionName=/{print $0; exit}' | sed 's/.*versionName=//')"
    else
      echo "installed_before=absent"
    fi
    echo "focused_component_before=$(current_foreground_component)"
    echo "focused_component_after="
  } > "$STATE_FILE"
}

cleanup() {
  local exit_code=$?
  restore_foreground "${FOCUSED_COMPONENT:-$PACKAGE_NAME/.MainActivity}"
  exit "$exit_code"
}

trap cleanup EXIT

"${ADB[@]}" wait-for-device
"${ADB[@]}" get-state

collect_package_state

FOCUSED_COMPONENT="$(sed -n 's/^focused_component_before=//p' "$STATE_FILE")"
if [ -z "$FOCUSED_COMPONENT" ]; then
  FOCUSED_COMPONENT="$PACKAGE_NAME/.MainActivity"
fi

if awk -F= '$1=="installed_before" {print $2}' "$STATE_FILE" | grep -q present; then
  ORIGINAL_APK_PATH="$(sed -n 's/^apk_path_before=package://p' "$STATE_FILE")"
  if [ -n "${ORIGINAL_APK_PATH:-}" ]; then
    "${ADB[@]}" pull "$ORIGINAL_APK_PATH" "$OUTPUT_DIR/original-release.apk" || true
  fi
fi

"${ADB[@]}" logcat -c

"${ADB[@]}" install -r "$APK_PATH"
"${ADB[@]}" install -r "$TEST_APK_PATH"

set +e
adb_shell am instrument \
  -w \
  -e class "$TEST_CLASS" \
  "$TEST_PACKAGE_NAME/$RUNNER" | tee "$INSTRUMENTATION_OUTPUT"
TEST_STATUS=${PIPESTATUS[0]}
set -e

"${ADB[@]}" logcat -d -v threadtime > "$LOGCAT_OUTPUT"
FOCUSED_AFTER="$(current_foreground_component)"
awk -v focused_after="$FOCUSED_AFTER" '{
  if ($0 ~ /^focused_component_after=/) {
    print "focused_component_after=" focused_after
  } else {
    print
  }
}' "$STATE_FILE" > "$STATE_FILE.tmp"
mv "$STATE_FILE.tmp" "$STATE_FILE"

if [[ "$TEST_STATUS" -ne 0 ]]; then
  fail_with_context "instrumentation process failed with exit code $TEST_STATUS"
fi

if ! grep -q "OK (" "$INSTRUMENTATION_OUTPUT"; then
  fail_with_context "instrumentation output did not report a passing test run"
fi

echo "Signed-release emulator smoke passed" | tee -a "$INSTRUMENTATION_OUTPUT"
