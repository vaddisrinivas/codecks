#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -n "${SCAN_PATHS:-}" ]]; then
  read -r -a scan_paths <<<"${SCAN_PATHS}"
else
  scan_paths=(
    "protocol"
    "mac-actions"
  )
fi

json_paths=(
  "protocol"
  "mac-actions"
)

forbidden_pattern='(firebase|firestore|remote[ _-]?config|hosted[ _-]?backend|cloud[ _-]?database|silent[ _-]?telemetry|telemetry|analytics|billing|subscription|subscribe|payment|payments|stripe|revenuecat|login|sign[ _-]?in|sign[ _-]?up|oauth|sentry|crashlytics|amplitude|segment|mixpanel|supabase|mongodb|postgres|aws|gcp|azure)'

json_files=()
for path in "${json_paths[@]}"; do
  if [[ -d "$repo_root/$path" ]]; then
    while IFS= read -r -d '' file; do
      json_files+=("$file")
    done < <(find "$repo_root/$path" -type f -name '*.json' -print0)
  fi
done

if ((${#json_files[@]} > 0)); then
  if command -v node >/dev/null 2>&1; then
    node - "${json_files[@]}" <<'NODE'
const fs = require("fs");
let ok = true;
for (const file of process.argv.slice(2)) {
  try {
    JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    ok = false;
    console.error(`Invalid JSON: ${file}`);
    console.error(error.message);
  }
}
process.exit(ok ? 0 : 1);
NODE
  elif command -v jq >/dev/null 2>&1; then
    for file in "${json_files[@]}"; do
      jq empty "$file" >/dev/null
    done
  else
    echo "ERROR: need node or jq for JSON syntax validation" >&2
    exit 1
  fi
fi

scan_files=()
for path in "${scan_paths[@]}"; do
  if [[ -e "$repo_root/$path" ]]; then
    while IFS= read -r -d '' file; do
      scan_files+=("$file")
    done < <(find "$repo_root/$path" -type f \
      ! -name '.DS_Store' \
      ! -path '*/build/*' \
      ! -path '*/.gradle/*' \
      ! -path '*/node_modules/*' \
      -print0)
  fi
done

if ((${#scan_files[@]} > 0)); then
  matches="$(grep -EIn "$forbidden_pattern" "${scan_files[@]}" || true)"
  if [[ -n "$matches" ]]; then
    echo "ERROR: forbidden localRelease terms found:" >&2
    echo "$matches" >&2
    exit 1
  fi
fi

echo "localRelease boundary check passed"
