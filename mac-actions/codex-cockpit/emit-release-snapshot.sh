#!/usr/bin/env bash
set -euo pipefail

repo_path="${1:-$(pwd)}"
output_path="${2:-/tmp/codecks-release-cockpit-snapshot.json}"
generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

branch="$(git -C "${repo_path}" rev-parse --abbrev-ref HEAD 2>/dev/null || printf 'unknown')"
dirty_count="$(git -C "${repo_path}" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
latest_tag="$(git -C "${repo_path}" describe --tags --abbrev=0 2>/dev/null || printf '')"
remote_url="$(git -C "${repo_path}" config --get remote.origin.url 2>/dev/null || printf '')"
release_url=""

if command -v gh >/dev/null 2>&1 && [ -n "${latest_tag}" ]; then
  release_url="$(gh release view "${latest_tag}" --json url --jq .url 2>/dev/null || printf '')"
fi

REPO_PATH="${repo_path}" \
OUTPUT_PATH="${output_path}" \
GENERATED_AT="${generated_at}" \
BRANCH="${branch}" \
DIRTY_COUNT="${dirty_count}" \
LATEST_TAG="${latest_tag}" \
REMOTE_URL="${remote_url}" \
RELEASE_URL="${release_url}" \
python3 - <<'PY'
import json
import os

dirty_count = int(os.environ["DIRTY_COUNT"] or "0")
latest_tag = os.environ["LATEST_TAG"]
release_url = os.environ["RELEASE_URL"]

tasks = [
    {
        "id": "release-worktree",
        "title": "Release worktree",
        "repoPath": os.environ["REPO_PATH"],
        "branch": os.environ["BRANCH"],
        "state": "NeedsAttention" if dirty_count else "Succeeded",
        "elapsedLabel": f"{dirty_count} dirty" if dirty_count else "clean",
        "updatedLabel": "now",
        "needsAttention": dirty_count > 0,
        "hasUnread": False,
        "effortMode": "Fast",
        "safeSummary": "Working tree has local changes." if dirty_count else "Working tree is clean."
    },
    {
        "id": "release-tag",
        "title": "Latest tag",
        "repoPath": os.environ["REPO_PATH"],
        "branch": os.environ["BRANCH"],
        "state": "Released" if latest_tag else "Queued",
        "elapsedLabel": latest_tag or "none",
        "updatedLabel": "git",
        "needsAttention": not bool(latest_tag),
        "hasUnread": False,
        "effortMode": "Fast",
        "safeSummary": f"Latest local tag is {latest_tag}." if latest_tag else "No local tag found."
    },
    {
        "id": "github-release",
        "title": "GitHub release",
        "repoPath": os.environ["REMOTE_URL"] or os.environ["REPO_PATH"],
        "branch": os.environ["BRANCH"],
        "state": "Released" if release_url else "Offline",
        "elapsedLabel": "found" if release_url else "not checked",
        "updatedLabel": "gh",
        "needsAttention": False,
        "hasUnread": False,
        "effortMode": "Fast",
        "safeSummary": f"GitHub release URL: {release_url}" if release_url else "GitHub release unavailable or gh is not authenticated."
    }
]

snapshot = {
    "version": 1,
    "generatedAt": os.environ["GENERATED_AT"],
    "source": "release-status",
    "privacy": {
        "promptContentIncluded": False,
        "sourceContentIncluded": False,
        "notes": "Git/GitHub release status only. No prompt or source content."
    },
    "tasks": tasks
}

with open(os.environ["OUTPUT_PATH"], "w", encoding="utf-8") as handle:
    json.dump(snapshot, handle, indent=2)
    handle.write("\n")

print(os.environ["OUTPUT_PATH"])
PY

