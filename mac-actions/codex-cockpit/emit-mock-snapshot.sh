#!/usr/bin/env bash
set -euo pipefail

output_path="${1:-/tmp/codecks-codex-cockpit-snapshot.json}"
generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

cat >"${output_path}" <<JSON
{
  "version": 1,
  "generatedAt": "${generated_at}",
  "source": "mock",
  "privacy": {
    "promptContentIncluded": false,
    "sourceContentIncluded": false,
    "notes": "Status-only mock snapshot for Codecks Codex Cockpit bridge development."
  },
  "tasks": [
    {
      "id": "task-cockpit-ui",
      "title": "Build Codex cockpit",
      "repoPath": "~/Projects/codecks",
      "branch": "codex/all-waves-trackpad-hid",
      "state": "Running",
      "elapsedLabel": "18m",
      "updatedLabel": "now",
      "needsAttention": false,
      "hasUnread": false,
      "effortMode": "Deep",
      "safeSummary": "Mock dashboard, fancy buttons, and themes are being wired first."
    },
    {
      "id": "task-release",
      "title": "Release v0.1.4",
      "repoPath": "~/Projects/codecks",
      "branch": "main",
      "state": "Released",
      "elapsedLabel": "done",
      "updatedLabel": "today",
      "needsAttention": false,
      "hasUnread": true,
      "effortMode": "Standard",
      "safeSummary": "APK, tag, and GitHub release were published from local work."
    },
    {
      "id": "task-appfunctions",
      "title": "Park AppFunctions",
      "repoPath": "~/Projects/codecks/android",
      "branch": "main",
      "state": "Blocked",
      "elapsedLabel": "parked",
      "updatedLabel": "today",
      "needsAttention": true,
      "hasUnread": false,
      "effortMode": "Unknown",
      "safeSummary": "Integration is preserved but paused until official dependency coordinates are confirmed."
    }
  ]
}
JSON

printf '%s\n' "${output_path}"

