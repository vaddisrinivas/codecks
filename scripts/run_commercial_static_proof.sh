#!/usr/bin/env bash
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
export PYTHONHASHSEED=0
export LC_ALL=C

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${1:-$ROOT_DIR/build/commercial-proof/static}"
mkdir -p "$OUTPUT_DIR"

python3 -m unittest discover -s "$ROOT_DIR/tools/tests" -p 'test_commercial_proof*.py'
python3 "$ROOT_DIR/tools/commercial_proof_harness.py" \
  --receipt "$OUTPUT_DIR/repo-static.json" repo-static --root "$ROOT_DIR"
