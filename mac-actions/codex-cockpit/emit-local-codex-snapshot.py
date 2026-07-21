#!/usr/bin/env python3
"""Emit a privacy-safe Codecks Codex Cockpit snapshot from local Codex metadata.

This reader intentionally uses only metadata-shaped files:
- ~/.codex/session_index.jsonl for task id/title/update time
- ~/.codex/process_manager/chat_processes.json for active process hints

It does not read session JSONL bodies, prompts, tool outputs, source files, or
conversation content.
"""

from __future__ import annotations

import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def parse_iso(value: str) -> datetime | None:
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except Exception:
        return None


def process_alive(pid: Any) -> bool:
    try:
        os.kill(int(pid), 0)
        return True
    except Exception:
        return False


def read_session_index(path: Path, limit: int) -> list[dict[str, Any]]:
    if not path.exists():
        return []

    latest_by_id: dict[str, dict[str, Any]] = {}
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue
            task_id = str(item.get("id") or "").strip()
            if not task_id:
                continue
            existing = latest_by_id.get(task_id)
            if existing is None or str(item.get("updated_at") or "") >= str(existing.get("updated_at") or ""):
                latest_by_id[task_id] = item

    return sorted(
        latest_by_id.values(),
        key=lambda item: str(item.get("updated_at") or ""),
        reverse=True,
    )[:limit]


def read_processes(path: Path) -> dict[str, dict[str, Any]]:
    if not path.exists():
        return {}
    try:
        raw = json.loads(path.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        return {}
    if not isinstance(raw, list):
        return {}

    by_conversation: dict[str, dict[str, Any]] = {}
    for item in raw:
        if not isinstance(item, dict):
            continue
        conversation_id = str(item.get("conversationId") or "").strip()
        if not conversation_id:
            continue
        existing = by_conversation.get(conversation_id)
        if existing is None or int(item.get("updatedAtMs") or 0) >= int(existing.get("updatedAtMs") or 0):
            by_conversation[conversation_id] = item
    return by_conversation


def elapsed_label(updated_at: datetime | None, now: datetime) -> str:
    if updated_at is None:
        return "unknown"
    seconds = max(0, int((now - updated_at).total_seconds()))
    if seconds < 60:
        return f"{seconds}s ago"
    minutes = seconds // 60
    if minutes < 60:
        return f"{minutes}m ago"
    hours = minutes // 60
    if hours < 48:
        return f"{hours}h ago"
    return f"{hours // 24}d ago"


def state_for(index_item: dict[str, Any], process_item: dict[str, Any] | None, now: datetime) -> str:
    if process_item and process_alive(process_item.get("osPid")):
        return "Running"
    updated = parse_iso(str(index_item.get("updated_at") or ""))
    if updated and (now - updated).total_seconds() < 15 * 60:
        return "NeedsAttention"
    return "Succeeded"


def build_snapshot(codex_home: Path, limit: int) -> dict[str, Any]:
    now = datetime.now(timezone.utc)
    index_items = read_session_index(codex_home / "session_index.jsonl", limit)
    processes = read_processes(codex_home / "process_manager" / "chat_processes.json")

    tasks = []
    for item in index_items:
        task_id = str(item.get("id") or "")
        title = str(item.get("thread_name") or "Codex task")
        process = processes.get(task_id)
        updated_raw = str(item.get("updated_at") or "")
        updated = parse_iso(updated_raw)
        cwd = str((process or {}).get("cwd") or "")
        state = state_for(item, process, now)
        tasks.append(
            {
                "id": task_id,
                "title": title[:96],
                "repoPath": cwd or "local-codex",
                "branch": "metadata",
                "state": state,
                "elapsedLabel": "active" if state == "Running" else elapsed_label(updated, now),
                "updatedLabel": updated_raw or "unknown",
                "needsAttention": state == "NeedsAttention",
                "hasUnread": False,
                "effortMode": "Unknown",
                "safeSummary": f"Local Codex metadata only. State: {state}.",
            }
        )

    if not tasks:
        tasks.append(
            {
                "id": "codex-local-metadata",
                "title": "Local Codex metadata",
                "repoPath": str(codex_home),
                "branch": "metadata",
                "state": "Offline",
                "elapsedLabel": "no tasks",
                "updatedLabel": "now",
                "needsAttention": False,
                "hasUnread": False,
                "effortMode": "Unknown",
                "safeSummary": "No session_index.jsonl tasks were available.",
            }
        )

    return {
        "version": 1,
        "generatedAt": now.isoformat().replace("+00:00", "Z"),
        "source": "local-codex-metadata",
        "privacy": {
            "promptContentIncluded": False,
            "sourceContentIncluded": False,
            "notes": "Uses session/process metadata only. Does not read prompts, source, tool outputs, or session bodies.",
        },
        "tasks": tasks,
    }


def main(argv: list[str]) -> int:
    output_path = Path(argv[1]) if len(argv) > 1 else Path("/tmp/codecks-local-codex-snapshot.json")
    codex_home = Path(argv[2]).expanduser() if len(argv) > 2 else Path.home() / ".codex"
    limit = int(argv[3]) if len(argv) > 3 else 12
    snapshot = build_snapshot(codex_home=codex_home, limit=limit)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(snapshot, indent=2) + "\n", encoding="utf-8")
    print(output_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

