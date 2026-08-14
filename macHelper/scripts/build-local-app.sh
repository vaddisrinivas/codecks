#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
helper_root="$(cd "$script_dir/.." && pwd)"
output_root="${1:-$helper_root/.build/local-app}"
app_path="$output_root/Codecks Mac Helper.app"
mkdir -p "$output_root"
staging_root="$(mktemp -d "$output_root/.codecks-app.XXXXXX")"
staging_app="$staging_root/Codecks Mac Helper.app"
contents="$staging_app/Contents"
backup_path="$output_root/.Codecks Mac Helper.app.previous"
cleanup() { rm -rf "$staging_root" "$backup_path"; }
trap cleanup EXIT

swift build --package-path "$helper_root" -c release --product "Codecks Mac Helper"
swift build --package-path "$helper_root" -c release --product "codecks-mac-helper"
mkdir -p "$contents/MacOS" "$contents/Resources"
cp "$helper_root/.build/release/Codecks Mac Helper" "$contents/MacOS/Codecks Mac Helper"
cp "$helper_root/.build/release/codecks-mac-helper" "$contents/MacOS/codecks-mac-helper"
cp "$helper_root/Resources/Info.plist" "$contents/Info.plist"
chmod 755 "$contents/MacOS/Codecks Mac Helper"
chmod 755 "$contents/MacOS/codecks-mac-helper"

rm -rf "$backup_path"
if [[ -e "$app_path" ]]; then
  mv "$app_path" "$backup_path"
fi
if ! mv "$staging_app" "$app_path"; then
  if [[ -e "$backup_path" ]]; then mv "$backup_path" "$app_path"; fi
  exit 1
fi
rm -rf "$backup_path"

echo "Unsigned local app built: $app_path"
echo "Signing and notarization: NOT_RUN"
