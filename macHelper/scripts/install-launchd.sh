#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
helper_root="$(cd "$script_dir/.." && pwd)"
support_dir="${HOME:?}/Library/Application Support/CodecksMacHelper"
log_dir="${HOME:?}/Library/Logs/CodecksMacHelper"
agents_dir="${HOME:?}/Library/LaunchAgents"
binary_path="$support_dir/codecks-mac-helper"
config_path="$support_dir/helper.json"
plist_path="$agents_dir/app.codecks.mac-helper.plist"
template_path="$helper_root/launchd/app.codecks.mac-helper.plist.template"

mkdir -p "$support_dir" "$log_dir" "$agents_dir"

swift build --package-path "$helper_root" -c release
cp "$helper_root/.build/release/codecks-mac-helper" "$binary_path"
chmod 755 "$binary_path"

if [[ ! -f "$config_path" ]]; then
  secret_hex="$(python3 - <<'PY'
import secrets
print(secrets.token_hex(32))
PY
)"
  fingerprint="$(python3 - <<'PY'
import secrets
print(secrets.token_hex(32))
PY
)"
  mac_id="$(scutil --get ComputerName 2>/dev/null || hostname)"
  issued_at_millis="$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)"
  umask 077
  python3 - "$config_path" "$mac_id" "$fingerprint" "$secret_hex" "$issued_at_millis" <<'PY'
import json
import sys

path, mac_id, fingerprint, secret_hex, issued = sys.argv[1:]
with open(path, "w", encoding="utf-8") as handle:
    json.dump(
        {
            "port": 47321,
            "macId": mac_id,
            "helperId": "codecks-mac-helper",
            "publicKeyFingerprint": fingerprint,
            "issuedAtMillis": int(issued),
            "sharedSecretHex": secret_hex,
        },
        handle,
        indent=2,
    )
    handle.write("\n")
PY
  chmod 600 "$config_path"
fi

python3 - "$template_path" "$plist_path" "$binary_path" "$config_path" "$log_dir" <<'PY'
import sys
from xml.sax.saxutils import escape

template, out, binary, config, log_dir = sys.argv[1:]
text = open(template, encoding="utf-8").read()
for key, value in {
    "__HELPER_BINARY__": binary,
    "__HELPER_CONFIG__": config,
    "__LOG_DIR__": log_dir,
}.items():
    text = text.replace(key, escape(value))
open(out, "w", encoding="utf-8").write(text)
PY

launchctl bootout "gui/$UID" "$plist_path" >/dev/null 2>&1 || true
launchctl bootstrap "gui/$UID" "$plist_path"
launchctl enable "gui/$UID/app.codecks.mac-helper"
launchctl kickstart -k "gui/$UID/app.codecks.mac-helper"

echo "Codecks Mac helper installed: $plist_path"
echo "Config: $config_path"
