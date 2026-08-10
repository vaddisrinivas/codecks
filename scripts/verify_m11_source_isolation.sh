#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

test_source="app/src/androidTestPlayInternal/java/io/codecks/internalquality/M11DexProxyInstrumentedTest.kt"
common_source="app/src/androidTest/java/io/codecks/internalquality/M11DexProxyInstrumentedTest.kt"

test -f "$test_source" || { echo "M11 internal test source missing" >&2; exit 1; }
test ! -e "$common_source" || { echo "M11 test leaked into common androidTest" >&2; exit 1; }

./gradlew --no-daemon --max-workers=1 \
  :app:compileOssReleaseAndroidTestKotlin \
  :app:compilePlayReleaseAndroidTestKotlin \
  :app:compilePlayInternalReleaseAndroidTestKotlin \
  --console=plain

classes="app/build/intermediates/built_in_kotlinc"
internal_test="$classes/playInternalReleaseAndroidTest/compilePlayInternalReleaseAndroidTestKotlin/classes/io/codecks/internalquality/M11DexProxyInstrumentedTest.class"
internal_activity="$classes/playInternalRelease/compilePlayInternalReleaseKotlin/classes/io/codecks/internalquality/M11DexProxyActivity.class"
test -f "$internal_test" || { echo "M11 test missing from playInternal AndroidTest" >&2; exit 1; }
test -f "$internal_activity" || { echo "M11 probe activity missing from playInternal" >&2; exit 1; }

for public_variant in ossRelease playRelease ossReleaseAndroidTest playReleaseAndroidTest; do
  if find "$classes/$public_variant" -type f \( -name 'M11DexProxyActivity*.class' -o -name 'M11DexProxyInstrumentedTest*.class' \) -print -quit 2>/dev/null | grep -q .; then
    echo "M11 internal class leaked into $public_variant" >&2
    exit 1
  fi
done

echo "PASS: M11 probe/test compile only in playInternal; OSS and public Play outputs are clean"
