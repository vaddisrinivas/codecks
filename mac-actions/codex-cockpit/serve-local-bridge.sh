#!/usr/bin/env bash
set -euo pipefail

repo_path="${1:-$(pwd)}"
port="${2:-8765}"
bridge_dir="${TMPDIR:-/tmp}/codecks-codex-cockpit-bridge"

mkdir -p "${bridge_dir}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${script_dir}/emit-mock-snapshot.sh" "${bridge_dir}/codecks-codex-cockpit-snapshot.json" >/dev/null
"${script_dir}/emit-release-snapshot.sh" "${repo_path}" "${bridge_dir}/codecks-release-cockpit-snapshot.json" >/dev/null
"${script_dir}/emit-local-codex-snapshot.py" "${bridge_dir}/codecks-local-codex-snapshot.json" >/dev/null

cat >"${bridge_dir}/index.json" <<JSON
{
  "snapshots": [
    "/codecks-codex-cockpit-snapshot.json",
    "/codecks-release-cockpit-snapshot.json",
    "/codecks-local-codex-snapshot.json"
  ],
  "privacy": {
    "promptContentIncluded": false,
    "sourceContentIncluded": false
  }
}
JSON

cat <<MSG
Serving Codecks Codex Cockpit bridge from:
  ${bridge_dir}

Android emulator URL:
  http://10.0.2.2:${port}/codecks-codex-cockpit-snapshot.json

Release snapshot URL:
  http://10.0.2.2:${port}/codecks-release-cockpit-snapshot.json

Local Codex metadata URL:
  http://10.0.2.2:${port}/codecks-local-codex-snapshot.json

For a physical phone, use adb reverse:
  adb reverse tcp:${port} tcp:${port}
  http://127.0.0.1:${port}/codecks-codex-cockpit-snapshot.json

MSG

cd "${bridge_dir}"
python3 -m http.server "${port}" --bind 127.0.0.1
