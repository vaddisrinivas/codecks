#!/usr/bin/env python3
"""Validate the final C3 M09D controller-lifecycle receipt."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from collect_m09d_controller_lifecycle import (
    C1_PATHS, C2_PATHS, C3_PATHS, RECEIPT, ROOT, changed_paths, collect_receipt, git,
    require_clean_status, require_worktree_matches_commit, validate_topology,
)
from strict_json_schema import validate_json_schema

SCHEMA = ROOT / "tools/evidence/schemas/codecks-m09d-controller-lifecycle-v1.schema.json"


def validate_data(data: dict, receipt_commit: str | None = None) -> None:
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_json_schema(data, schema)
    receipt = receipt_commit or git("rev-parse", "HEAD")
    artifact = git("rev-parse", f"{receipt}^")
    source = git("rev-parse", f"{artifact}^")
    validate_topology(
        source, git("rev-parse", f"{source}^"), changed_paths(source),
        artifact_commit=artifact, artifact_parent=git("rev-parse", f"{artifact}^"), artifact_paths=changed_paths(artifact),
        receipt_parent=git("rev-parse", f"{receipt}^"), receipt_paths=changed_paths(receipt),
    )
    require_clean_status()
    require_worktree_matches_commit(source, C1_PATHS)
    require_worktree_matches_commit(artifact, C2_PATHS)
    require_worktree_matches_commit(receipt, C3_PATHS)
    if data != collect_receipt(artifact):
        raise ValueError("receipt does not exactly match current C1/C2 bytes and topology")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt", nargs="?", type=Path, default=ROOT / RECEIPT)
    args = parser.parse_args()
    validate_data(json.loads(args.receipt.read_text(encoding="utf-8")))
    print("M09D_CONTROLLER_LIFECYCLE_PASS")


if __name__ == "__main__":
    main()
