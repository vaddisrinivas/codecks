#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

: "${ANDROID_HOME:=/Users/srinivasvaddi/Library/Android/sdk}"
export ANDROID_HOME

raw="build/ga-evidence/M14/first_run_repair_benchmark.raw.json"
junit_generated="app/build/test-results/testOssReleaseUnitTest/TEST-io.codecks.ui.connection.FirstRunRepairBenchmarkTest.xml"
junit_durable="tasks/test-evidence/m14/runtime/TEST-FirstRunRepairBenchmarkTest.xml"
compose_junit="tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.xml"
managed_failed="tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.failed-attempt.xml"
managed_retry="tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.retry.xml"
target_apk="app/build/outputs/apk/playInternal/release/app-playInternal-release.apk"
test_apk="app/build/outputs/apk/androidTest/playInternal/release/app-playInternal-release-androidTest.apk"
receipt="tasks/test-evidence/autonomous-maturity-m14-first-run.json"

./gradlew --no-daemon --max-workers=2 \
  :app:testOssReleaseUnitTest \
  --tests io.codecks.ui.connection.FirstRunRepairBenchmarkTest \
  :app:compilePlayInternalReleaseAndroidTestKotlin \
  --console=plain

test -f "$raw" || { echo "M14 raw receipt missing: $raw" >&2; exit 1; }
test -f "$junit_generated" || { echo "M14 JUnit result missing: $junit_generated" >&2; exit 1; }
test -f "$compose_junit" || { echo "M14 managed Compose result missing: $compose_junit" >&2; exit 1; }
test -f "$managed_failed" || { echo "M14 failed-attempt result missing: $managed_failed" >&2; exit 1; }
test -f "$managed_retry" || { echo "M14 filtered-retry result missing: $managed_retry" >&2; exit 1; }
test -f "$target_apk" || { echo "M14 target artifact missing: $target_apk" >&2; exit 1; }
test -f "$test_apk" || { echo "M14 test artifact missing: $test_apk" >&2; exit 1; }
mkdir -p "$(dirname "$junit_durable")"
cp "$junit_generated" "$junit_durable"
python3 tools/evidence/collect_m14_first_run.py \
  --raw "$raw" --junit "$junit_durable" --compose-junit "$compose_junit" \
  --managed-failed-attempt "$managed_failed" --managed-retry "$managed_retry" \
  --target-apk "$target_apk" --test-apk "$test_apk" --output "$receipt"
python3 tools/evidence/validate_m14_first_run.py "$receipt"
python3 -m unittest tools/evidence/test_m14_first_run.py
