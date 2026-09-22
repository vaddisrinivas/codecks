#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
signer="$script_dir/sign-local-app.sh"

bash -n "$signer"
output="$(env -u CODECKS_MAC_DEVELOPER_ID -u CODECKS_MAC_TEAM_ID "$signer" "$script_dir/not-present.app")"
[[ "$output" == "SIGNING_NOT_RUN: missing CODECKS_MAC_DEVELOPER_ID or CODECKS_MAC_TEAM_ID" ]]

echo "SIGNING_CONTRACT_PASS"
