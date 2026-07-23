#!/usr/bin/env bash
set -euo pipefail

echo "Deprecated: use scripts/signed_release_emulator_smoke.sh." >&2
exec "$(dirname "$0")/signed_release_emulator_smoke.sh" "$@"
