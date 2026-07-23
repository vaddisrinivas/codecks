#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARSER="$SCRIPT_DIR/signed_release_emulator_smoke.sh"

assert_component() {
  local expected="$1"
  local fixture="$2"
  local actual
  actual="$(printf '%s\n' "$fixture" | "$PARSER" --parse-focused-component)"
  if [ "$actual" != "$expected" ]; then
    echo "Expected '$expected', got '$actual' for: $fixture" >&2
    exit 1
  fi
}

assert_component \
  "app.codecks/io.codecks.MainActivity" \
  "mCurrentFocus=Window{42a1 u0 app.codecks/io.codecks.MainActivity}"
assert_component \
  "com.android.settings/.Settings" \
  "mResumedActivity: ActivityRecord{a2b3 u0 com.android.settings/.Settings t12}"
assert_component \
  "com.example/com.example.MainActivity" \
  "topResumedActivity=ActivityRecord{a2b3 u0 com.example/com.example.MainActivity t7}"

empty_result="$(printf '%s\n' 'mCurrentFocus=null' | "$PARSER" --parse-focused-component)"
test -z "$empty_result"

echo "signed_release_emulator_smoke parser fixtures passed"
