#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

result="app/build/outputs/androidTest-results/managedDevice/release/flavors/playInternal/pixel6Api35/TEST-pixel6Api35-_app-playInternal.xml"
durable_result="tasks/test-evidence/m11/runtime/TEST-pixel6Api35-playInternalRelease.xml"
receipt="tasks/test-evidence/autonomous-maturity-m11-dex-proxy.json"

./gradlew --no-daemon --max-workers=1 \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  -Pandroid.testInstrumentationRunnerArguments.class=io.codecks.internalquality.M11DexProxyInstrumentedTest \
  :app:pixel6Api35PlayInternalReleaseAndroidTest --console=plain

test -f "$result" || { echo "M11 managed result missing: $result" >&2; exit 1; }
mkdir -p "$(dirname "$durable_result")"
cp "$result" "$durable_result"
python3 tools/evidence/collect_m11_dex_proxy.py --result "$durable_result" --output "$receipt"
python3 tools/evidence/validate_m11_dex_proxy.py
python3 -m unittest tools/evidence/test_m11_dex_proxy.py
