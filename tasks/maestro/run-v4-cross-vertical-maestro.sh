#!/usr/bin/env bash
set -euo pipefail

FLOW_FILE="tasks/maestro/v4-cross-vertical.yaml"
APP_PACKAGE="app.codecks.debug"
SCREENSHOT_DIR="tasks/test-evidence/maestro-v4"
mkdir -p "$SCREENSHOT_DIR"

if ! command -v maestro >/dev/null 2>&1; then
  echo "maestro not installed"
  exit 0
fi

DEVICE_ID=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
if [[ -z "$DEVICE_ID" ]]; then
  echo "no emulator/device connected"
  exit 0
fi

if ! adb -s "$DEVICE_ID" shell pidof "$APP_PACKAGE" >/dev/null 2>&1; then
  echo "app package not installed yet: $APP_PACKAGE"
  exit 0
fi

for SIZE in "1280x720" "1920x1080"; do
  echo "Running flow for wm size $SIZE"
  adb -s "$DEVICE_ID" shell wm size "$SIZE"
  adb -s "$DEVICE_ID" shell wm density 320
  maestro test "$FLOW_FILE" --format junit -o "$SCREENSHOT_DIR/v4-${SIZE}.xml"
  adb -s "$DEVICE_ID" shell screencap -p "/sdcard/v4-${SIZE}.png"
  adb -s "$DEVICE_ID" pull "/sdcard/v4-${SIZE}.png" "$SCREENSHOT_DIR/v4-${SIZE}.png"
  adb -s "$DEVICE_ID" shell rm "/sdcard/v4-${SIZE}.png"
  adb -s "$DEVICE_ID" shell wm size reset
  sleep 2
  if adb -s "$DEVICE_ID" shell getprop sys.boot_completed | tr -d '\r' | grep -q "1"; then
    :
  fi
 done

maestro test "$FLOW_FILE" --format junit -o "$SCREENSHOT_DIR/v4-identity.xml"
