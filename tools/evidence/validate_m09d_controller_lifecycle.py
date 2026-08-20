#!/usr/bin/env python3
"""Validate the final C3 M09D controller-lifecycle receipt."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

from collect_m09d_controller_lifecycle import (
    ARTIFACT_MANIFEST, C1_PATHS, C2_PATHS, C3_PATHS, MAX_ARTIFACT_MANIFEST_BYTES, MAX_LOG_BYTES, MAX_RECEIPT_BYTES,
    MAX_XML_BYTES, RECEIPT, ROOT, SANITIZED_JUNIT_XML, SANITIZED_LOG, changed_paths, collect_receipt, git,
    load_bounded_json,
    require_clean_status, require_focused_result_absent, require_worktree_matches_commit, validate_private_bytes, validate_topology,
)
from strict_json_schema import validate_json_schema

SCHEMA = ROOT / "tools/evidence/schemas/codecks-m09d-controller-lifecycle-v1.schema.json"


def require_recorded_postrun_scope(data: dict) -> None:
    claims = data.get("claims") if isinstance(data, dict) else None
    if not isinstance(claims, dict) or claims.get("writableHomeMaterialRetention") != "NOT_RETAINED" or claims.get("postRunLiveRevalidation") != "NOT_APPLICABLE":
        raise ValueError("receipt writable-home retention/revalidation scope substituted")
    evidence_source = data.get("evidenceSource")
    if not isinstance(evidence_source, dict) or set(evidence_source) != {"sourceParent", "c1Paths", "files"}:
        raise ValueError("receipt collector/schema/validator source binding missing")


def validate_committed_evidence_privacy(artifact_commit: str, receipt_commit: str) -> None:
    """Scan only committed C2/C3 evidence; ignored local build outputs are outside this proof."""
    limits = {
        SANITIZED_JUNIT_XML.as_posix(): MAX_XML_BYTES,
        SANITIZED_LOG.as_posix(): MAX_LOG_BYTES,
        ARTIFACT_MANIFEST.as_posix(): MAX_ARTIFACT_MANIFEST_BYTES,
        RECEIPT.as_posix(): MAX_RECEIPT_BYTES,
    }
    for commit, paths in ((artifact_commit, C2_PATHS), (receipt_commit, C3_PATHS)):
        for path in sorted(paths):
            payload = subprocess.run(
                ["git", "show", f"{commit}:{path}"], cwd=ROOT, check=True, capture_output=True,
            ).stdout
            if len(payload) > limits[path]:
                raise ValueError("committed evidence exceeds privacy scan bound")
            validate_private_bytes(payload)


def validate_data(data: dict, receipt_commit: str | None = None) -> None:
    require_focused_result_absent()
    test = data.get("test") if isinstance(data, dict) else None
    expected_counts = {"tests": 10, "failures": 0, "errors": 0, "skipped": 0}
    if not isinstance(test, dict) or any(type(test.get(key)) is not int or test[key] != value for key, value in expected_counts.items()):
        raise ValueError("receipt test counts must be exact integers")
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_json_schema(data, schema)
    require_recorded_postrun_scope(data)
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
    validate_committed_evidence_privacy(artifact, receipt)
    if data != collect_receipt(artifact):
        raise ValueError("receipt does not exactly match current C1/C2 bytes and topology")
    require_focused_result_absent()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt", nargs="?", type=Path, default=ROOT / RECEIPT)
    args = parser.parse_args()
    validate_data(load_bounded_json(args.receipt, MAX_RECEIPT_BYTES))
    print("M09D_CONTROLLER_LIFECYCLE_PASS")


if __name__ == "__main__":
    main()
