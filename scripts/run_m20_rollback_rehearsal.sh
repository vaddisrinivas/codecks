#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${ANDROID_HOME:-}" && -d /Users/srinivasvaddi/Library/Android/sdk ]]; then
  export ANDROID_HOME=/Users/srinivasvaddi/Library/Android/sdk
fi
export PYTHONDONTWRITEBYTECODE=1

./gradlew --no-daemon --max-workers=2 \
  :app:testOssReleaseUnitTest \
  --tests io.codecks.data.persistence.M20RollbackIncidentRehearsalTest \
  --console=plain

python3 tools/evidence/collect_m20_rollback_rehearsal.py \
  --raw-result app/build/test-results/testOssReleaseUnitTest/TEST-io.codecks.data.persistence.M20RollbackIncidentRehearsalTest.xml \
  --sanitized-result tasks/test-evidence/m20/runtime/TEST-M20RollbackIncidentRehearsalTest.xml \
  --output tasks/test-evidence/autonomous-maturity-m20-rollback-rehearsal.json

python3 tools/evidence/validate_m20_rollback_rehearsal.py
python3 -m unittest tools/evidence/test_m20_rollback_rehearsal.py
