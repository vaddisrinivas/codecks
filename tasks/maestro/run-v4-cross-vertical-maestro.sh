#!/usr/bin/env bash
set -euo pipefail

COMPACT_FLOW_FILE="tasks/maestro/v4-cross-vertical.yaml"
EXPANDED_FLOW_FILE="tasks/maestro/v4-cross-vertical-expanded.yaml"
APP_PACKAGE="app.codecks.debug"
SCREENSHOT_DIR="${SCREENSHOT_DIR:-/tmp/codecks-maestro-v4}"
EMULATOR_ONLY="${EMULATOR_ONLY:-true}"
mkdir -p "$SCREENSHOT_DIR"

if ! command -v maestro >/dev/null 2>&1; then
  echo "maestro not installed" >&2
  exit 1
fi

if [[ "$EMULATOR_ONLY" != "true" ]]; then
  echo "EMULATOR_ONLY=true is required" >&2
  exit 1
fi

DEVICE_ID="${ADB_SERIAL:-$(adb devices | awk '$1 ~ /^emulator-/ && $2=="device" {print $1; exit}')}"
if [[ -z "$DEVICE_ID" ]]; then
  echo "no emulator connected" >&2
  exit 1
fi

if [[ "$DEVICE_ID" != emulator-* ]]; then
  echo "refusing non-emulator ADB target: $DEVICE_ID" >&2
  exit 1
fi

if ! adb -s "$DEVICE_ID" shell pm path "$APP_PACKAGE" >/dev/null 2>&1; then
  echo "app package not installed: $APP_PACKAGE" >&2
  exit 1
fi

reset_display() {
  adb -s "$DEVICE_ID" shell wm size reset >/dev/null 2>&1 || true
  adb -s "$DEVICE_ID" shell wm density reset >/dev/null 2>&1 || true
}
trap reset_display EXIT

for SIZE in "1280x720" "1920x1080"; do
  echo "Running flow for wm size $SIZE"
  adb -s "$DEVICE_ID" shell wm size "$SIZE"
  adb -s "$DEVICE_ID" shell wm density 320
  if [[ "$SIZE" == "1920x1080" ]]; then
    FLOW_FILE="$EXPANDED_FLOW_FILE"
  else
    FLOW_FILE="$COMPACT_FLOW_FILE"
  fi
  maestro test "$FLOW_FILE" --device "$DEVICE_ID" --format junit --output "$SCREENSHOT_DIR/v4-${SIZE}.xml"
  adb -s "$DEVICE_ID" shell screencap -p "/sdcard/v4-${SIZE}.png"
  adb -s "$DEVICE_ID" pull "/sdcard/v4-${SIZE}.png" "$SCREENSHOT_DIR/v4-${SIZE}.png"
  adb -s "$DEVICE_ID" shell rm "/sdcard/v4-${SIZE}.png"
  reset_display
  sleep 2
  if adb -s "$DEVICE_ID" shell getprop sys.boot_completed | tr -d '\r' | grep -q "1"; then
    :
  fi
 done

maestro test "$COMPACT_FLOW_FILE" --device "$DEVICE_ID" --format junit --output "$SCREENSHOT_DIR/v4-identity.xml"
