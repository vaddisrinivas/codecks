#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

export ANDROID_HOME="${ANDROID_HOME:-/Users/srinivasvaddi/Library/Android/sdk}"

./gradlew --no-daemon --max-workers=2 \
  :app:testOssReleaseUnitTest \
  --tests io.codecks.domain.automation.M18AutomationAssuranceTest \
  --console=plain

python3 tools/evidence/collect_m18_automation_proof.py \
  --result app/build/test-results/testOssReleaseUnitTest/TEST-io.codecks.domain.automation.M18AutomationAssuranceTest.xml \
  --sanitized-result tasks/test-evidence/m18-automation-proof-cpu.xml \
  --output tasks/test-evidence/autonomous-maturity-m18-automation-proof.json

python3 tools/evidence/validate_m18_automation_proof.py
python3 -m unittest tools/evidence/test_m18_automation_proof.py
