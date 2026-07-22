#!/usr/bin/env python3
"""Validate every bundled Mac action without triggering user-visible controls."""

from __future__ import annotations

import json
import platform
import shlex
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/codecks_actions.json"


def run(command: list[str], *, stdin: str | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, input=stdin, text=True, capture_output=True, check=False)


def main() -> int:
    actions = json.loads(CATALOG.read_text())
    failures: list[str] = []
    ids = [action["id"] for action in actions]
    if len(ids) != len(set(ids)):
        failures.append("catalog contains duplicate action ids")

    for action in actions:
        if action.get("kind") != "ssh":
            continue
        command = action.get("command", "").strip()
        if not command:
            failures.append(f"{action['id']}: missing command")
            continue
        for field in ("command", "test_command"):
            value = action.get(field)
            if not value:
                continue
            syntax = run(["zsh", "-n"], stdin=value)
            if syntax.returncode:
                failures.append(f"{action['id']}.{field}: {syntax.stderr.strip()}")

        if command.startswith("osascript ") and platform.system() == "Darwin":
            args = shlex.split(command)
            scripts = [args[index + 1] for index, arg in enumerate(args[:-1]) if arg == "-e"]
            compile_args = [part for script in scripts for part in ("-e", script)]
            with tempfile.TemporaryDirectory(prefix="codecks-actions-") as output_dir:
                compiled = run(["osacompile", "-o", str(Path(output_dir) / "action.scpt"), *compile_args])
            if compiled.returncode:
                failures.append(f"{action['id']}: AppleScript does not compile: {compiled.stderr.strip()}")

    for executable in ("zsh", "open", "osascript", "screencapture", "pmset", "caffeinate"):
        if platform.system() == "Darwin" and shutil.which(executable) is None:
            failures.append(f"required macOS tool missing: {executable}")

    if failures:
        print("Bundled Mac action verification failed:")
        print("\n".join(f"- {failure}" for failure in failures))
        return 1
    ssh_count = sum(action.get("kind") == "ssh" for action in actions)
    print(f"Verified {ssh_count} bundled Mac actions: JSON, shell syntax, AppleScript, and required tools OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
