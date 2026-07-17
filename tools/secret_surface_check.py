#!/usr/bin/env python3
"""Fail on common secret or private-workstation leaks in public source."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_FILE_SUFFIXES = (
    ".env",
    ".jks",
    ".keystore",
    ".p12",
    ".pem",
)
FORBIDDEN_FILE_NAMES = {
    "local.properties",
}
SOURCE_PREFIXES = (
    "app/src/main/",
)
QUERY_SECRET = re.compile(r"""[?&](api[_-]?key|key|token|access[_-]?token|password|secret)=""", re.IGNORECASE)
ANDROID_SECRET_LOG = re.compile(r"""Log\.[a-z]+\([^)]*(password|token|secret|api[_-]?key|authorization)""", re.IGNORECASE | re.DOTALL)
JS_SECRET_LOG = re.compile(r"""console\.(log|info|warn|error)\([^)]*(password|token|secret|api[_-]?key|authorization)""", re.IGNORECASE | re.DOTALL)
RAW_SECRET_ASSIGNMENT = re.compile(
    r"""(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|password|secret)\s*[:=]\s*["'][^"'<>{}\s]{12,}["']"""
)
PRIVATE_IDENTITY = re.compile(r"(?i)\b(srinivas|vaddi)\b|R3CW10MSVRT|emulator-5554")
PRIVATE_HOME = re.compile(r"/Users/(?!example(?:/|\b)|me(?:/|\b)|user(?:/|\b))[^/\s]+")
PRIVATE_WORKSPACE = re.compile(r"Documents/Codex/20\d\d-")
TEXT_SUFFIXES = {
    ".gradle",
    ".java",
    ".json",
    ".kts",
    ".kt",
    ".md",
    ".properties",
    ".py",
    ".txt",
    ".xml",
    ".yml",
    ".yaml",
}


def tracked_files() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    return [line for line in result.stdout.splitlines() if line]


def is_production_source(path: str) -> bool:
    return path.startswith(SOURCE_PREFIXES) and "/test/" not in path and "/androidTest/" not in path


def main() -> int:
    failures: list[str] = []
    for relative in tracked_files():
        path = ROOT / relative
        name = path.name
        if name in FORBIDDEN_FILE_NAMES or name.endswith(FORBIDDEN_FILE_SUFFIXES):
            failures.append(f"tracked secret-like file is forbidden: {relative}")
            continue
        if relative != "tools/secret_surface_check.py" and (
            path.suffix.lower() in TEXT_SUFFIXES or name in {"gradlew", ".gitignore"}
        ):
            text = path.read_text(encoding="utf-8", errors="ignore")
            if PRIVATE_IDENTITY.search(text):
                failures.append(f"private identity appears in public source: {relative}")
            if PRIVATE_HOME.search(text):
                failures.append(f"private home path appears in public source: {relative}")
            if PRIVATE_WORKSPACE.search(text):
                failures.append(f"private workspace path appears in public source: {relative}")
        if not is_production_source(relative) or path.suffix not in {".kt", ".java", ".ts", ".js"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if QUERY_SECRET.search(text):
            failures.append(f"secret-like value appears in URL query string: {relative}")
        if relative.startswith("app/src/main/") and ANDROID_SECRET_LOG.search(text):
            failures.append(f"secret-like value appears in Android log call: {relative}")
        if RAW_SECRET_ASSIGNMENT.search(text):
            failures.append(f"hard-coded secret-like assignment in production source: {relative}")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print("secret surface OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
