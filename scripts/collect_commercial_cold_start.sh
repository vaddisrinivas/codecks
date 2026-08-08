#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${1:-}"
OUTPUT_DIR="${2:-$ROOT_DIR/build/commercial-proof/cold-start}"
MODE="${3:---prepare-only}"
RECEIPT="$OUTPUT_DIR/receipt.json"
PACKAGE="app.codecks"
mkdir -p "$OUTPUT_DIR"

not_run() {
  python3 - "$RECEIPT" "$1" <<'PY'
import json
from pathlib import Path
import sys
result={"schema":"codecks.commercial-proof.v1","kind":"cold_start_collection","overall":"NOT_RUN","checks":[{"id":"cold_start.collection","status":"NOT_RUN","evidence":sys.argv[2],"violations":[]}],"warning":"No trace-based product claim is allowed from NOT_RUN evidence."}
Path(sys.argv[1]).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
PY
}

if ! command -v adb >/dev/null || [[ -z "$SERIAL" ]]; then
  not_run "adb unavailable or emulator serial omitted"
  exit 0
fi
if [[ "$(adb -s "$SERIAL" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != "1" ]]; then
  not_run "refused: cold-start harness is emulator-only"
  exit 0
fi
if ! adb -s "$SERIAL" shell pm path "$PACKAGE" >"$OUTPUT_DIR/package-path.txt" 2>&1; then
  not_run "$PACKAGE is not installed on emulator $SERIAL"
  exit 0
fi
if [[ "$MODE" != "--run" ]]; then
  not_run "prerequisites present; collection requires explicit --run"
  exit 0
fi

for name in start.txt logcat.txt activity.txt package.txt jobscheduler.txt alarm.txt services.txt \
    work.txt work.exit binder.txt binder.exit network.txt network.exit cold-start.perfetto-trace; do
  [[ ! -e "$OUTPUT_DIR/$name" ]] || rm "$OUTPUT_DIR/$name"
done

adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am force-stop "$PACKAGE"
adb -s "$SERIAL" shell am start -W -n "$PACKAGE/.MainActivity" >"$OUTPUT_DIR/start.txt" 2>&1
sleep 3
adb -s "$SERIAL" logcat -d -v threadtime >"$OUTPUT_DIR/logcat.txt"
adb -s "$SERIAL" shell dumpsys activity activities >"$OUTPUT_DIR/activity.txt"
adb -s "$SERIAL" shell dumpsys package "$PACKAGE" >"$OUTPUT_DIR/package.txt"
adb -s "$SERIAL" shell dumpsys jobscheduler >"$OUTPUT_DIR/jobscheduler.txt"
adb -s "$SERIAL" shell dumpsys alarm >"$OUTPUT_DIR/alarm.txt"
adb -s "$SERIAL" shell dumpsys activity services >"$OUTPUT_DIR/services.txt"
collect_optional() {
  local name="$1"
  shift
  set +e
  adb -s "$SERIAL" shell "$@" >"$OUTPUT_DIR/$name.txt" 2>&1
  local code="$?"
  set -e
  printf '%s\n' "$code" >"$OUTPUT_DIR/$name.exit"
}

collect_optional work dumpsys activity service androidx.work.impl.background.systemjob.SystemJobService
collect_optional binder dumpsys binder_calls_stats
collect_optional network dumpsys netstats detail

PERFETTO_STATUS="NOT_RUN"
if adb -s "$SERIAL" shell 'command -v perfetto' >/dev/null 2>&1; then
  TRACE_DEVICE="/data/misc/perfetto-traces/codecks-commercial-proof.perfetto-trace"
  if adb -s "$SERIAL" shell perfetto -o "$TRACE_DEVICE" -t 3s sched freq idle am wm binder_driver >/dev/null 2>&1 && \
      adb -s "$SERIAL" pull "$TRACE_DEVICE" "$OUTPUT_DIR/cold-start.perfetto-trace" >/dev/null 2>&1; then
    PERFETTO_STATUS="PASS"
  fi
fi

python3 - "$OUTPUT_DIR" "$RECEIPT" "$SERIAL" "$PERFETTO_STATUS" <<'PY'
import hashlib
import json
from pathlib import Path
import sys
root=Path(sys.argv[1])
required=("start.txt","logcat.txt","activity.txt","package.txt","jobscheduler.txt","alarm.txt","services.txt")
checks=[]
for name in required:
    path=root/name
    ok=path.is_file() and (name == "logcat.txt" or path.stat().st_size > 0)
    digest=hashlib.sha256(path.read_bytes()).hexdigest() if ok else None
    checks.append({"id":f"collect.{name}","status":"PASS" if ok else "FAIL","evidence":str(path),"sha256":digest,"violations":[]})
for name in ("work", "binder", "network"):
    path=root/f"{name}.txt"
    exit_path=root/f"{name}.exit"
    try:
        code=int(exit_path.read_text())
    except (OSError, ValueError):
        code=-1
    ok=code == 0 and path.is_file() and path.stat().st_size > 0
    digest=hashlib.sha256(path.read_bytes()).hexdigest() if ok else None
    checks.append({"id":f"collect.{name}","status":"PASS" if ok else "NOT_RUN","evidence":str(path),"sha256":digest,"violations":[]})
start=(root/"start.txt").read_text(errors="ignore") if (root/"start.txt").is_file() else ""
launch_ok="Status: ok" in start and "app.codecks" in start
checks.append({"id":"cold_start.launch_truth","status":"PASS" if launch_ok else "FAIL","evidence":str(root/"start.txt"),"violations":[] if launch_ok else [start[-500:]]})
checks.append({"id":"collect.perfetto","status":sys.argv[4],"evidence":str(root/"cold-start.perfetto-trace"),"violations":[]})
statuses={item["status"] for item in checks}
overall="FAIL" if "FAIL" in statuses else "NOT_RUN" if "NOT_RUN" in statuses else "PASS"
result={"schema":"codecks.commercial-proof.v1","kind":"cold_start_collection","overall":overall,"emulator":sys.argv[3],"checks":checks,"warning":"Collection success is not semantic proof; traces require review before any production-dark claim."}
Path(sys.argv[2]).write_text(json.dumps(result, indent=2, sort_keys=True)+"\n")
print(Path(sys.argv[2]))
raise SystemExit(1 if overall == "FAIL" else 0)
PY
