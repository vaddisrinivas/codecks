#!/usr/bin/env bash
set -euo pipefail

plist_path="${HOME:?}/Library/LaunchAgents/app.codecks.mac-helper.plist"

launchctl bootout "gui/$UID" "$plist_path" >/dev/null 2>&1 || true
rm -f "$plist_path"

echo "Codecks Mac helper LaunchAgent removed."
echo "Helper binary/config preserved under ~/Library/Application Support/CodecksMacHelper."
