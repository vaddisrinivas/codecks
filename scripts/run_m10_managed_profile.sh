#!/usr/bin/env bash
set -euo pipefail

API="${1:-}"
SHAPE="${2:-}"
case "$API" in 31|32|33|34|35|36) ;;
  *) echo "Usage: $0 <31|32|33|34|35|36> <compact|standard|tablet>" >&2; exit 2 ;;
esac
case "$SHAPE" in
  compact) PROFILE="Compact" ;;
  standard) PROFILE="Standard" ;;
  tablet) PROFILE="Tablet" ;;
  *) echo "Usage: $0 <31|32|33|34|35|36> <compact|standard|tablet>" >&2; exit 2 ;;
esac

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
IMAGE_ROOT="$ANDROID_SDK_ROOT/system-images/android-$API/default"
TASK=":app:m10${PROFILE}Api${API}OssDebugAndroidTest"

if [[ ! -d "$ANDROID_SDK_ROOT" ]]; then
  echo "NOT_RUN M10 api=$API shape=$SHAPE reason=android_sdk_unavailable"
  exit 3
fi
if [[ ! -d "$IMAGE_ROOT" && "${M10_ALLOW_IMAGE_DOWNLOAD:-false}" != "true" ]]; then
  echo "NOT_RUN M10 api=$API shape=$SHAPE reason=exact_aosp_image_unavailable expected=$IMAGE_ROOT"
  exit 3
fi
echo "AUTONOMOUS_PROXY M10 api=$API shape=$SHAPE task=$TASK image_source=aosp"
cd "$REPO_ROOT"
ANDROID_HOME="$ANDROID_SDK_ROOT" ./gradlew --no-daemon \
  -PcodecksInstrumentedTestBuildType=debug \
  -Pandroid.testInstrumentationRunnerArguments.class=io.codecks.maturity.M10ManagedMatrixInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.m10Shape="$SHAPE" \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  "$TASK"

PACKAGE_XML_LIST="$(find "$IMAGE_ROOT" -mindepth 2 -maxdepth 2 -name package.xml -print 2>/dev/null || true)"
PACKAGE_XML_COUNT="$(printf '%s\n' "$PACKAGE_XML_LIST" | awk 'NF { count += 1 } END { print count + 0 }')"
RECEIPT="$REPO_ROOT/app/build/outputs/m10-managed-api-${API}-${SHAPE}.json"
if [[ "$PACKAGE_XML_COUNT" != 1 ]]; then
  echo "M10 failed: expected exactly one installed AOSP package receipt under $IMAGE_ROOT" >&2
  exit 5
fi
PACKAGE_XML="$PACKAGE_XML_LIST"
DEVICE_NAME="m10${PROFILE}Api${API}"
RESULT_DIR="$REPO_ROOT/app/build/outputs/androidTest-results/managedDevice/debug/flavors/oss/$DEVICE_NAME"
TARGET_APK="$REPO_ROOT/app/build/outputs/apk/oss/debug/app-oss-debug.apk"
TEST_APK="$REPO_ROOT/app/build/outputs/apk/androidTest/oss/debug/app-oss-debug-androidTest.apk"
python3 "$REPO_ROOT/tools/evidence/m10_managed_receipt.py" \
  --root "$REPO_ROOT" \
  --sdk "$ANDROID_SDK_ROOT" \
  --api "$API" \
  --shape "$SHAPE" \
  --task "$TASK" \
  --result-dir "$RESULT_DIR" \
  --target-apk "$TARGET_APK" \
  --test-apk "$TEST_APK" \
  --image-package "$PACKAGE_XML" \
  --output "$RECEIPT"
python3 "$REPO_ROOT/tools/evidence/m10_managed_receipt.py" \
  --root "$REPO_ROOT" --sdk "$ANDROID_SDK_ROOT" --verify "$RECEIPT"
