#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$ROOT_DIR/app/build.gradle.kts"
APK_PATH="${1:-}"

require_line() {
  local line="$1"
  local match
  match="$(grep -Fnx "$line" "$BUILD_FILE" || true)"
  if [ -z "$match" ]; then
    echo "Release no-shrink invariant missing: $line" >&2
    exit 1
  fi
  echo "verified $BUILD_FILE:${match%%:*}: $line"
}

reject_line() {
  local line="$1"
  local match
  match="$(grep -Fn "$line" "$BUILD_FILE" || true)"
  if [ -n "$match" ]; then
    echo "Release shrinking is forbidden at $BUILD_FILE:${match%%:*}: $line" >&2
    exit 1
  fi
}

require_line "            isMinifyEnabled = false"
require_line "            isShrinkResources = false"
reject_line "isMinifyEnabled = true"
reject_line "isShrinkResources = true"

for flavor in 'create("oss")' 'create("play")' 'create("playInternal")'; do
  if ! grep -Fq "$flavor" "$BUILD_FILE"; then
    echo "Release distribution flavor missing: $flavor" >&2
    exit 1
  fi
done

if [ -n "$APK_PATH" ]; then
  if [ ! -f "$APK_PATH" ]; then
    echo "Release APK not found: $APK_PATH" >&2
    exit 1
  fi
  if find "$ROOT_DIR/app/build/outputs/mapping" -name mapping.txt -newer "$APK_PATH" -print -quit 2>/dev/null \
      | grep -q .; then
    echo "A current R8 mapping exists; refusing a potentially minified artifact." >&2
    exit 1
  fi
  if ! unzip -Z1 "$APK_PATH" | grep -E '(^|/)classes([0-9]+)?\.dex$' >/dev/null; then
    echo "Release artifact has no classes.dex: $APK_PATH" >&2
    exit 1
  fi
fi

echo "Release no-shrink invariant verified."
