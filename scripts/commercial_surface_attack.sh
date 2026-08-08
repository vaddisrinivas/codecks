#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${1:-}"
OUTPUT_DIR="${2:-$ROOT_DIR/build/commercial-proof/device-attack}"
MODE="${3:---inventory-only}"
RECEIPT="$OUTPUT_DIR/receipt.json"
PACKAGE="app.codecks"
mkdir -p "$OUTPUT_DIR/results"

emit_not_run() {
  python3 - "$RECEIPT" "$1" <<'PY'
import json
from pathlib import Path
import sys
result = {"schema":"codecks.commercial-proof.v1","kind":"device_attack","overall":"NOT_RUN","checks":[{"id":"device.attack","status":"NOT_RUN","evidence":sys.argv[2],"violations":[]}]}
Path(sys.argv[1]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
PY
}

if ! command -v adb >/dev/null || [[ -z "$SERIAL" ]]; then
  emit_not_run "adb unavailable or emulator serial omitted"
  exit 0
fi
if [[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != "1" ]]; then
  emit_not_run "refused: target is not an Android emulator"
  exit 0
fi
if ! adb -s "$SERIAL" shell pm path "$PACKAGE" >"$OUTPUT_DIR/package-path.txt" 2>&1; then
  emit_not_run "$PACKAGE is not installed on emulator $SERIAL"
  exit 0
fi

adb -s "$SERIAL" shell dumpsys package "$PACKAGE" >"$OUTPUT_DIR/package-inventory.txt"
if [[ "$MODE" != "--execute" ]]; then
  emit_not_run "inventory collected; direct attacks require explicit --execute"
  exit 0
fi

find "$OUTPUT_DIR/results" -type f -delete

run_probe() {
  local id="$1"
  shift
  set +e
  adb -s "$SERIAL" shell "$@" >"$OUTPUT_DIR/results/$id.txt" 2>&1
  echo "$?" >"$OUTPUT_DIR/results/$id.exit"
  set -e
}

run_probe forbidden_account_deeplink am start -W -a android.intent.action.VIEW -d codecks://account -p "$PACKAGE"
run_probe forbidden_sync_deeplink am start -W -a android.intent.action.VIEW -d codecks://sync -p "$PACKAGE"
run_probe forbidden_purchase_deeplink am start -W -a android.intent.action.VIEW -d codecks://purchase -p "$PACKAGE"
run_probe forbidden_ads_deeplink am start -W -a android.intent.action.VIEW -d codecks://ads -p "$PACKAGE"
run_probe nonexported_activity am start -W -n "$PACKAGE/.ui.mouse.lockscreen.LockscreenTrackpadActivity"
run_probe nonexported_service am startservice -n "$PACKAGE/.HidSessionService"
run_probe exported_receiver am broadcast -a io.codecks.COMMERCIAL_PROBE -n "$PACKAGE/.widget.TrackpadWidgetProvider"
run_probe allowed_trackpad am start -W -a android.intent.action.VIEW -d codecks://trackpad -p "$PACKAGE"
run_probe allowed_ai am start -W -a android.intent.action.VIEW -d codecks://ai -p "$PACKAGE"
run_probe allowed_helper_pair am start -W -a android.intent.action.VIEW -d codecks://helper-pair -p "$PACKAGE"

python3 - "$OUTPUT_DIR" "$RECEIPT" "$SERIAL" <<'PY'
import json
from pathlib import Path
import sys
root = Path(sys.argv[1])
checks = []
expected = {
    "forbidden_account_deeplink", "forbidden_sync_deeplink", "forbidden_purchase_deeplink",
    "forbidden_ads_deeplink", "nonexported_activity", "nonexported_service",
    "exported_receiver", "allowed_trackpad", "allowed_ai", "allowed_helper_pair",
}
observed = set()
for output in sorted((root / "results").glob("*.txt")):
    name = output.stem
    if name not in expected:
        continue
    observed.add(name)
    text = output.read_text(errors="ignore")
    try:
        code = int(output.with_suffix(".exit").read_text())
    except (OSError, ValueError):
        checks.append({"id":f"route.{name}","status":"FAIL","evidence":str(output),"violations":["missing or malformed exit receipt"]})
        continue
    forbidden = name.startswith("forbidden_") or name.startswith("nonexported_")
    rejected = code != 0 or any(marker in text.lower() for marker in ("unable to resolve", "permission denial", "not exported", "error"))
    status = "PASS" if (rejected if forbidden else code == 0) else "FAIL"
    checks.append({"id":f"route.{name}","status":status,"evidence":str(output),"violations":[] if status == "PASS" else [text[-500:]]})
for name in sorted(expected - observed):
    checks.append({"id":f"route.{name}","status":"FAIL","evidence":"probe output missing","violations":[name]})
statuses = {item["status"] for item in checks}
result = {"schema":"codecks.commercial-proof.v1","kind":"device_attack","overall":"FAIL" if "FAIL" in statuses or len(checks) != len(expected) else "PASS","emulator":sys.argv[3],"checks":checks,"warning":"PASS proves route rejection/resolution only, not absence of commercial runtime work"}
Path(sys.argv[2]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
print(Path(sys.argv[2]))
raise SystemExit(1 if result["overall"] == "FAIL" else 0)
PY
