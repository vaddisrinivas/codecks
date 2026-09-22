#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
DEFAULT_ANDROID_SDK="${HOME:?}/Library/Android/sdk"
: "${ANDROID_HOME:=$DEFAULT_ANDROID_SDK}"
export ANDROID_HOME

signing_dir="$(mktemp -d)"
trap 'rm -r "$signing_dir"' EXIT
keytool -genkeypair -noprompt -keystore "$signing_dir/m14-test.p12" -storetype PKCS12 \
  -storepass m14-test-only -keypass m14-test-only -alias m14-test \
  -keyalg RSA -keysize 2048 -validity 2 -dname "CN=Codecks M14 Test" >/dev/null 2>&1
export CODECKS_RELEASE_STORE_FILE="$signing_dir/m14-test.p12"
export CODECKS_RELEASE_KEY_ALIAS="m14-test"
export CODECKS_RELEASE_STORE_PASSWORD="m14-test-only"
export CODECKS_RELEASE_KEY_PASSWORD="m14-test-only"

generated="app/build/outputs/androidTest-results/managedDevice/release/flavors/playInternal/pixel6Api35/TEST-pixel6Api35-_app-playInternal.xml"
durable="tasks/test-evidence/m14/runtime/TEST-pixel6Api35-M14FirstRunRepairInstrumentedTest.xml"

./gradlew --no-daemon --max-workers=1 \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  -Pandroid.testInstrumentationRunnerArguments.class=io.codecks.ui.settings.M14FirstRunRepairInstrumentedTest \
  :app:pixel6Api35PlayInternalReleaseAndroidTest --console=plain

test -f "$generated" || { echo "M14 managed result missing: $generated" >&2; exit 1; }
mkdir -p "$(dirname "$durable")"
cp "$generated" "$durable"
cp "$(dirname "$generated")/device-info.pb" "$(dirname "$durable")/device-info.pb"
cp "$(dirname "$generated")/test-result.textproto" "$(dirname "$durable")/test-result.textproto"
cp app/build/outputs/apk/playInternal/release/app-playInternal-release.apk "$(dirname "$durable")/app-playInternal-release.apk"
cp app/build/outputs/apk/androidTest/playInternal/release/app-playInternal-release-androidTest.apk "$(dirname "$durable")/app-playInternal-release-androidTest.apk"
