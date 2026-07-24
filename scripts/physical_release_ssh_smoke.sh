#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:?Usage: physical_release_ssh_smoke.sh <signed-release-apk> <release-test-apk>}"
TEST_APK_PATH="${2:?Usage: physical_release_ssh_smoke.sh <signed-release-apk> <release-test-apk>}"
ADB_SERIAL="${ADB_SERIAL:?Set ADB_SERIAL to the explicitly approved physical phone serial.}"
PACKAGE_NAME="app.codecks"
TEST_PACKAGE_NAME="app.codecks.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="io.codecks.release.ReleaseSshSmokeInstrumentedTest#signedReleaseRunsBundledFinderAction"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$(dirname "$APK_PATH")"
INSTRUMENTATION_OUTPUT="$OUTPUT_DIR/physical-ssh-instrumentation.txt"
LOGCAT_OUTPUT="$OUTPUT_DIR/physical-ssh-logcat.txt"
TEMP_DIR="$(mktemp -d)"

cleanup() {
  local exit_code=$?
  adb -s "$ADB_SERIAL" shell am start -n "$PACKAGE_NAME/io.codecks.MainActivity" >/dev/null 2>&1 || true
  rm -rf "$TEMP_DIR"
  exit "$exit_code"
}
trap cleanup EXIT

if [[ "$ADB_SERIAL" == emulator-* ]]; then
  echo "Physical SSH smoke rejects emulator serials." >&2
  exit 1
fi

if [ ! -f "$APK_PATH" ] || [ ! -f "$TEST_APK_PATH" ]; then
  echo "Both signed release and release-test APKs are required." >&2
  exit 1
fi

"$ROOT_DIR/scripts/verify_release_no_shrink.sh" "$APK_PATH"

if ! adb devices | awk -v serial="$ADB_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit !found }'; then
  echo "Approved physical phone is not connected: $ADB_SERIAL" >&2
  exit 1
fi

if ! unzip -Z1 "$TEST_APK_PATH" | grep -E '^classes([0-9]+)?\.dex$' >/dev/null; then
  echo "Release-test APK has no classes.dex." >&2
  exit 1
fi

APKSIGNER_BIN="${APKSIGNER:-}"
if [ -z "$APKSIGNER_BIN" ]; then
  APKSIGNER_BIN="$(command -v apksigner || true)"
fi
if [ -z "$APKSIGNER_BIN" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  APKSIGNER_BIN="$(find "$ANDROID_SDK_ROOT/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
fi
if [ -z "$APKSIGNER_BIN" ] && [ -n "${ANDROID_HOME:-}" ]; then
  APKSIGNER_BIN="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
fi
if [ -z "$APKSIGNER_BIN" ] && [ -f "$ROOT_DIR/local.properties" ]; then
  LOCAL_SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$ROOT_DIR/local.properties" | tail -n 1)"
  if [ -n "$LOCAL_SDK_DIR" ]; then
    APKSIGNER_BIN="$(find "$LOCAL_SDK_DIR/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
  fi
fi
if [ -z "$APKSIGNER_BIN" ] || [ ! -x "$APKSIGNER_BIN" ]; then
  echo "apksigner not found; certificate match cannot be verified." >&2
  exit 1
fi

INSTALLED_APK_PATH="$(
  adb -s "$ADB_SERIAL" shell pm path --user 0 "$PACKAGE_NAME" \
    | sed -n 's/^package://p' \
    | tr -d '\r' \
    | head -n 1
)"
if [ -z "$INSTALLED_APK_PATH" ]; then
  echo "Protected production package is not installed; refusing non-update test." >&2
  exit 1
fi

adb -s "$ADB_SERIAL" pull "$INSTALLED_APK_PATH" "$TEMP_DIR/installed.apk" >/dev/null

certificate_digest() {
  "$APKSIGNER_BIN" verify --print-certs "$1" \
    | awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print $2; exit }'
}

INSTALLED_CERT="$(certificate_digest "$TEMP_DIR/installed.apk")"
CANDIDATE_CERT="$(certificate_digest "$APK_PATH")"
if [ -z "$INSTALLED_CERT" ] || [ "$INSTALLED_CERT" != "$CANDIDATE_CERT" ]; then
  echo "Signing certificate mismatch; production app was not changed." >&2
  exit 1
fi

APK_SHA_BEFORE="$(shasum -a 256 "$APK_PATH" | awk '{print $1}')"

adb -s "$ADB_SERIAL" logcat -c
adb -s "$ADB_SERIAL" install -r --no-streaming "$APK_PATH"
adb -s "$ADB_SERIAL" install -r --no-streaming "$TEST_APK_PATH"

set +e
adb -s "$ADB_SERIAL" shell am instrument \
  -w \
  -e requirePhysicalMac true \
  -e class "$TEST_CLASS" \
  "$TEST_PACKAGE_NAME/$RUNNER" | tee "$INSTRUMENTATION_OUTPUT"
TEST_STATUS=${PIPESTATUS[0]}
set -e

adb -s "$ADB_SERIAL" logcat -d -v threadtime > "$LOGCAT_OUTPUT"

if [ "$TEST_STATUS" -ne 0 ] || ! grep -q "OK (1 test)" "$INSTRUMENTATION_OUTPUT"; then
  echo "Physical signed-release SSH test failed." >&2
  tail -n 160 "$INSTRUMENTATION_OUTPUT" >&2 || true
  tail -n 240 "$LOGCAT_OUTPUT" >&2 || true
  exit 1
fi

if grep -Eiq 'ClassNotFoundException|NoClassDefFoundError|IllegalAccessException|JSchException' "$LOGCAT_OUTPUT"; then
  echo "SSH/JSch runtime failure found in device logs." >&2
  tail -n 240 "$LOGCAT_OUTPUT" >&2
  exit 1
fi

APK_SHA_AFTER="$(shasum -a 256 "$APK_PATH" | awk '{print $1}')"
if [ "$APK_SHA_BEFORE" != "$APK_SHA_AFTER" ]; then
  echo "Release APK changed during physical validation." >&2
  exit 1
fi

echo "Physical signed-release SSH smoke passed without uninstalling or clearing app.codecks."
