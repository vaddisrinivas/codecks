#!/usr/bin/env python3
"""Run and durably bind the single final Theme Studio Impeccable detector gate."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess

from collect_m09d_theme_studio import DETECTOR_ARTIFACT, ROOT, atomic_copy_bytes, safe_repo_path, sha

TARGETS = [
    "app/src/main/java/io/codecks/ui/settings/SettingsControlSections.kt",
    "app/src/main/java/io/codecks/ui/theme/ThemeStudioPanel.kt",
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--detector", type=Path, required=True)
    args = parser.parse_args()
    detector = args.detector.resolve(strict=True)
    if detector.is_symlink() or not detector.is_file() or detector.name != "detect.mjs":
        raise SystemExit("unsafe or unexpected detector")
    command = ["node", str(detector), "--json", *TARGETS]
    run = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=True)
    result = json.loads(run.stdout)
    if result != []:
        raise SystemExit(f"Impeccable detector findings: {json.dumps(result, sort_keys=True)}")
    artifact = {
        "schema": "codecks.m09d.impeccable-detector.v1",
        "status": "PASS",
        "command": ["node", "$IMPECCABLE_DETECTOR", "--json", *TARGETS],
        "detector": {"name": detector.name, "sha256": hashlib.sha256(detector.read_bytes()).hexdigest()},
        "targets": [{"path": path, "sha256": sha(safe_repo_path(path))} for path in TARGETS],
        "result": result,
    }
    atomic_copy_bytes(
        json.dumps(artifact, sort_keys=True, separators=(",", ":")).encode() + b"\n",
        safe_repo_path(DETECTOR_ARTIFACT),
    )
    print("M09D_IMPECCABLE_DETECTOR_PASS")


if __name__ == "__main__":
    main()
