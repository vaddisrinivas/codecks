#!/usr/bin/env bash
set -euo pipefail

package_name="${1:-app.codecks.debug}"
out_dir="${2:-build/adb-smoke/hid-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$out_dir"

adb_cmd=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb_cmd=(adb -s "$ANDROID_SERIAL")
fi

dump_ui() {
  local name="$1"
  "${adb_cmd[@]}" shell uiautomator dump "/sdcard/${name}.xml" >/dev/null
  "${adb_cmd[@]}" shell cat "/sdcard/${name}.xml" > "${out_dir}/${name}.xml"
}

screen() {
  local name="$1"
  "${adb_cmd[@]}" exec-out screencap -p > "${out_dir}/${name}.png"
}

tap_text() {
  local xml="$1"
  local text="$2"
  python3 - "$xml" "$text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    text = node.attrib.get("text") or node.attrib.get("content-desc") or ""
    if text == wanted:
        bounds = node.attrib.get("bounds", "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
            sys.exit(0)
print("")
PY
}

tap_bounds_for_text() {
  local xml="$1"
  local text="$2"
  local point
  point="$(tap_text "$xml" "$text")"
  if [[ -z "$point" ]]; then
    echo "Missing UI text/content-desc: $text" >&2
    return 1
  fi
  "${adb_cmd[@]}" shell input tap $point
}

"${adb_cmd[@]}" logcat -c || true
"${adb_cmd[@]}" shell monkey -p "$package_name" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 1
dump_ui "01-launch"
screen "01-launch"

tap_bounds_for_text "${out_dir}/01-launch.xml" "Trackpad" || true
sleep 1
dump_ui "02-trackpad"
screen "02-trackpad"

tap_bounds_for_text "${out_dir}/02-trackpad.xml" "Choose target" || \
tap_bounds_for_text "${out_dir}/02-trackpad.xml" "Trackpad target" || \
tap_bounds_for_text "${out_dir}/02-trackpad.xml" "Choose" || true
sleep 2
dump_ui "03-picker"
screen "03-picker"

"${adb_cmd[@]}" logcat -d -t 1200 \
  | grep -Ei 'codecks|hid|bluetooth|register|profile|pair|connect|failed|denied|exception|crash' \
  > "${out_dir}/hid-logcat.txt" || true

python3 - "$out_dir" <<'PY'
import pathlib
import sys

out = pathlib.Path(sys.argv[1])
for name in ["01-launch", "02-trackpad", "03-picker"]:
    text = (out / f"{name}.xml").read_text(errors="ignore")
    print(f"\n== {name} ==")
    for needle in [
        "Trackpad",
        "Trackpad targets",
        "Choose target",
        "No compatible targets found yet.",
        "Pair your Mac first",
        "Add device",
        "Reconnect",
        "Use",
        "Settings",
    ]:
        if needle in text:
            print(f"found: {needle}")

log = (out / "hid-logcat.txt").read_text(errors="ignore")
print("\n== hid-logcat hints ==")
for needle in [
    "HID registration failed",
    "failed because another app is registered",
    "HID registered",
    "Connect request failed",
    "CONNECTION_FAILED_ESTABLISHMENT",
    "Bluetooth permission missing",
]:
    if needle.lower() in log.lower():
        print(f"found: {needle}")
print(f"\nartifacts: {out}")
PY
