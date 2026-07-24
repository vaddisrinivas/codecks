#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$ROOT_DIR/app/build.gradle.kts"
APK_PATH="${1:-}"

require_line() {
  local line="$1"
  if ! grep -Fqx "$line" "$BUILD_FILE"; then
    echo "Release no-shrink invariant missing: $line" >&2
    exit 1
  fi
}

reject_line() {
  local line="$1"
  if grep -Fq "$line" "$BUILD_FILE"; then
    echo "Release shrinking is forbidden: $line" >&2
    exit 1
  fi
}

require_line "            isMinifyEnabled = false"
require_line "            isShrinkResources = false"
reject_line "isMinifyEnabled = true"
reject_line "isShrinkResources = true"

if [ -n "$APK_PATH" ]; then
  if [ ! -f "$APK_PATH" ]; then
    echo "Release APK not found: $APK_PATH" >&2
    exit 1
  fi
  MAPPING_PATH="$ROOT_DIR/app/build/outputs/mapping/release/mapping.txt"
  if [ -e "$MAPPING_PATH" ] && [ "$MAPPING_PATH" -nt "$APK_PATH" ]; then
    echo "A current R8 mapping exists for release; refusing a potentially minified APK." >&2
    exit 1
  fi
  if ! unzip -Z1 "$APK_PATH" | grep -E '^classes([0-9]+)?\.dex$' >/dev/null; then
    echo "Release APK has no classes.dex: $APK_PATH" >&2
    exit 1
  fi
fi

echo "Release no-shrink invariant verified."
