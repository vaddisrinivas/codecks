#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

DEFAULT_ANDROID_SDK="${HOME:?}/Library/Android/sdk"
: "${ANDROID_HOME:=$DEFAULT_ANDROID_SDK}"
export ANDROID_HOME

raw="build/ga-evidence/M14/first_run_repair_benchmark.raw.json"
junit_generated="app/build/test-results/testOssReleaseUnitTest/TEST-io.codecks.ui.connection.FirstRunRepairBenchmarkTest.xml"
junit_durable="tasks/test-evidence/m14/runtime/TEST-FirstRunRepairBenchmarkTest.xml"
compose_junit="tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.xml"
target_apk="tasks/test-evidence/m14/runtime/app-playInternal-release.apk"
test_apk="tasks/test-evidence/m14/runtime/app-playInternal-release-androidTest.apk"
receipt="tasks/test-evidence/autonomous-maturity-m14-first-run.json"

./gradlew --no-daemon --max-workers=2 \
  :app:testOssReleaseUnitTest \
  --tests io.codecks.ui.connection.FirstRunRepairBenchmarkTest \
  :app:compilePlayInternalReleaseAndroidTestKotlin \
  --console=plain

test -f "$raw" || { echo "M14 raw receipt missing: $raw" >&2; exit 1; }
test -f "$junit_generated" || { echo "M14 JUnit result missing: $junit_generated" >&2; exit 1; }
test -f "$compose_junit" || { echo "M14 managed Compose result missing: $compose_junit" >&2; exit 1; }
test -f "$target_apk" || { echo "M14 target artifact missing: $target_apk" >&2; exit 1; }
test -f "$test_apk" || { echo "M14 test artifact missing: $test_apk" >&2; exit 1; }
mkdir -p "$(dirname "$junit_durable")"
cp "$junit_generated" "$junit_durable"
python3 tools/evidence/collect_m14_first_run.py \
  --raw "$raw" --junit "$junit_durable" --compose-junit "$compose_junit" \
  --target-apk "$target_apk" --test-apk "$test_apk" --output "$receipt"
python3 tools/evidence/validate_m14_first_run.py "$receipt"
python3 -m unittest tools/evidence/test_m14_first_run.py
