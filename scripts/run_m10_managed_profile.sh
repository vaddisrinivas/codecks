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
RECEIPT="$REPO_ROOT/app/build/outputs/m10-image-api-${API}-${SHAPE}.txt"
mkdir -p "$(dirname "$RECEIPT")"
if [[ "$PACKAGE_XML_COUNT" == 1 ]]; then
  PACKAGE_XML="$PACKAGE_XML_LIST"
  if command -v sha256sum >/dev/null 2>&1; then
    PACKAGE_DIGEST="$(sha256sum "$PACKAGE_XML" | awk '{print $1}')"
  else
    PACKAGE_DIGEST="$(shasum -a 256 "$PACKAGE_XML" | awk '{print $1}')"
  fi
  {
    echo "status=PASS"
    echo "evidence=AUTONOMOUS_PROXY"
    echo "api=$API"
    echo "shape=$SHAPE"
    echo "system_image_source=aosp"
    echo "package_path=${PACKAGE_XML#"$ANDROID_SDK_ROOT/"}"
    echo "package_xml_sha256=$PACKAGE_DIGEST"
  } > "$RECEIPT"
else
  echo "status=FAIL reason=installed_image_receipt_count_${PACKAGE_XML_COUNT} api=$API shape=$SHAPE" > "$RECEIPT"
  echo "M10 failed: expected exactly one installed AOSP package receipt under $IMAGE_ROOT" >&2
  exit 5
fi
echo "M10 image receipt: $RECEIPT"
