#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AAB_PATH="${1:-$ROOT_DIR/app/build/outputs/bundle/playRelease/app-play-release.aab}"
OUTPUT_DIR="${2:-$ROOT_DIR/build/commercial-proof/artifacts}"
RECEIPT_PATH="${3:-$OUTPUT_DIR/artifact-build-receipt.json}"

mkdir -p "$OUTPUT_DIR"

not_run() {
  local reason="$1"
  python3 - "$RECEIPT_PATH" "$reason" <<'PY'
import json
from pathlib import Path
import sys
path = Path(sys.argv[1])
path.parent.mkdir(parents=True, exist_ok=True)
result = {
    "schema": "codecks.commercial-proof.v1",
    "kind": "bundletool_build",
    "overall": "NOT_RUN",
    "checks": [{"id": "bundletool.build", "status": "NOT_RUN", "evidence": sys.argv[2], "violations": []}],
}
path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
print(path)
PY
}

if [[ ! -f "$AAB_PATH" ]]; then
  not_run "Play release AAB missing: $AAB_PATH"
  exit 0
fi
if ! command -v java >/dev/null || ! command -v keytool >/dev/null || ! command -v unzip >/dev/null; then
  not_run "java, keytool, or unzip unavailable"
  exit 0
fi

BUNDLETOOL_EVIDENCE=""
if command -v bundletool >/dev/null; then
  BUNDLETOOL=(bundletool)
  BUNDLETOOL_EVIDENCE="$(command -v bundletool)"
elif [[ -n "${BUNDLETOOL_JAR:-}" && -f "$BUNDLETOOL_JAR" ]] && \
    unzip -p "$BUNDLETOOL_JAR" META-INF/MANIFEST.MF 2>/dev/null | grep -q '^Main-Class:'; then
  BUNDLETOOL=(java -jar "$BUNDLETOOL_JAR")
  BUNDLETOOL_EVIDENCE="$BUNDLETOOL_JAR"
else
  not_run "bundletool unavailable"
  exit 0
fi

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/codecks-proof.XXXXXX")"
trap 'rm -rf "$TEMP_DIR"' EXIT
KEYSTORE="$TEMP_DIR/ephemeral-proof.jks"
keytool -genkeypair -noprompt \
  -keystore "$KEYSTORE" -storepass proof-only-pass -keypass proof-only-pass \
  -alias proof -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Codecks Local Proof" >/dev/null 2>&1

UNIVERSAL_APKS="$OUTPUT_DIR/play-release-universal.apks"
SPLIT_APKS="$OUTPUT_DIR/play-release-splits.apks"
COMMON=(--bundle="$AAB_PATH" --ks="$KEYSTORE" --ks-pass=pass:proof-only-pass --ks-key-alias=proof --key-pass=pass:proof-only-pass --overwrite)
"${BUNDLETOOL[@]}" build-apks "${COMMON[@]}" --mode=universal --output="$UNIVERSAL_APKS"
"${BUNDLETOOL[@]}" build-apks "${COMMON[@]}" --output="$SPLIT_APKS"

python3 "$ROOT_DIR/tools/commercial_proof_harness.py" \
  --receipt "$OUTPUT_DIR/universal-scan.json" artifact "$UNIVERSAL_APKS"
python3 "$ROOT_DIR/tools/commercial_proof_harness.py" \
  --receipt "$OUTPUT_DIR/splits-scan.json" artifact "$SPLIT_APKS"

python3 - "$OUTPUT_DIR/universal-scan.json" "$OUTPUT_DIR/splits-scan.json" "$RECEIPT_PATH" "$BUNDLETOOL_EVIDENCE" <<'PY'
import json
from pathlib import Path
import sys
children = [json.loads(Path(value).read_text()) for value in sys.argv[1:3]]
statuses = {child["overall"] for child in children}
overall = "FAIL" if "FAIL" in statuses else "NOT_RUN" if "NOT_RUN" in statuses else "PASS"
result = {
    "schema": "codecks.commercial-proof.v1",
    "kind": "bundletool_build",
    "overall": overall,
    "bundletool": sys.argv[4],
    "checks": [{
        "id": "bundletool.universal_and_splits",
        "status": overall,
        "evidence": "ephemeral proof signer; never a production signer",
        "violations": [],
    }],
    "child_receipts": children,
}
path = Path(sys.argv[3])
path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
print(path)
raise SystemExit(1 if overall == "FAIL" else 0)
PY
