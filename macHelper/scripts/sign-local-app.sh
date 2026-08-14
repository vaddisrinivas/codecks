#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
helper_root="$(cd "$script_dir/.." && pwd)"
app_path="${1:-$helper_root/.build/local-app/Codecks Mac Helper.app}"

if [[ -z "${CODECKS_MAC_DEVELOPER_ID:-}" || -z "${CODECKS_MAC_TEAM_ID:-}" ]]; then
  echo "SIGNING_NOT_RUN: missing CODECKS_MAC_DEVELOPER_ID or CODECKS_MAC_TEAM_ID"
  exit 0
fi
if [[ ! -d "$app_path" ]]; then
  echo "SIGNING_NOT_RUN: app bundle missing; run build-local-app.sh first" >&2
  exit 1
fi

bundle_id="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app_path/Contents/Info.plist")"
expected_app_id="${CODECKS_MAC_TEAM_ID}.${bundle_id}"
entitlements="$(mktemp "${TMPDIR:-/tmp}/codecks-entitlements.XXXXXX")"
observed="$(mktemp "${TMPDIR:-/tmp}/codecks-observed-entitlements.XXXXXX")"
cleanup() { rm -f "$entitlements" "$observed"; }
trap cleanup EXIT

plutil -create xml1 "$entitlements"
/usr/libexec/PlistBuddy -c "Add :com.apple.application-identifier string $expected_app_id" "$entitlements"
/usr/libexec/PlistBuddy -c "Add :com.apple.developer.team-identifier string $CODECKS_MAC_TEAM_ID" "$entitlements"
plutil -lint "$entitlements" >/dev/null

cli_path="$app_path/Contents/MacOS/codecks-mac-helper"
codesign --force --options runtime --timestamp --sign "$CODECKS_MAC_DEVELOPER_ID" --entitlements "$entitlements" "$cli_path"
codesign --force --options runtime --timestamp --sign "$CODECKS_MAC_DEVELOPER_ID" --entitlements "$entitlements" "$app_path"
codesign --verify --deep --strict --verbose=2 "$app_path"
codesign -d --entitlements :- "$app_path" >"$observed" 2>/dev/null

[[ "$(/usr/libexec/PlistBuddy -c 'Print :com.apple.application-identifier' "$observed")" == "$expected_app_id" ]]
[[ "$(/usr/libexec/PlistBuddy -c 'Print :com.apple.developer.team-identifier' "$observed")" == "$CODECKS_MAC_TEAM_ID" ]]
[[ "$($cli_path _keychain-probe)" == "KEYCHAIN_PROBE_PASS" ]]
echo "SIGNING_PASS: identity entitlements and signed Keychain probe verified"
