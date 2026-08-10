#!/usr/bin/env python3
"""Adversarial mutation tests for the autonomous-maturity evidence validator."""

from __future__ import annotations

import copy
import hashlib
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE_TOOLS = ROOT / "tools/evidence"
sys.path.insert(0, str(EVIDENCE_TOOLS))

from validate_autonomous_maturity_evidence import (  # noqa: E402
    attest_git_and_critical_files,
    attest_public_artifact,
    reject_sensitive_values,
    validate_evidence,
)


BASELINE_PATH = ROOT / "tasks/test-evidence/autonomous-maturity-m00-baseline.json"
INVENTORY_PATH = ROOT / "tasks/test-evidence/autonomous-maturity-source-inventory.json"


class EvidenceMutationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.baseline = json.loads(BASELINE_PATH.read_text())
        self.inventory = json.loads(INVENTORY_PATH.read_text())

    @staticmethod
    def inventory_bytes(inventory: dict) -> bytes:
        return (json.dumps(inventory, indent=2, sort_keys=False) + "\n").encode()

    def sync_inventory_reference(self, baseline: dict, inventory: dict) -> bytes:
        raw = self.inventory_bytes(inventory)
        baseline["source_inventory"]["sha256"] = hashlib.sha256(raw).hexdigest()
        baseline["source_inventory"]["summary"] = copy.deepcopy(inventory["summary"])
        return raw

    def assert_rejected(
        self,
        baseline: dict,
        inventory: dict,
        expected: str,
        *,
        sync_inventory: bool = False,
    ) -> None:
        raw = (
            self.sync_inventory_reference(baseline, inventory)
            if sync_inventory
            else INVENTORY_PATH.read_bytes()
        )
        with self.assertRaisesRegex(ValueError, expected):
            validate_evidence(baseline, inventory, raw)

    def test_empty_required_sections_are_rejected(self) -> None:
        for section in ("source", "release_artifact", "android", "toolchain", "source_inventory"):
            with self.subTest(section=section):
                baseline = copy.deepcopy(self.baseline)
                baseline[section] = {}
                self.assert_rejected(baseline, self.inventory, f"baseline.{section}: missing keys")

    def test_empty_fresh_checks_is_rejected(self) -> None:
        self.baseline["fresh_checks"] = []
        self.assert_rejected(self.baseline, self.inventory, "too few items")

    def test_unknown_baseline_key_is_rejected(self) -> None:
        self.baseline["android"]["invented"] = True
        self.assert_rejected(self.baseline, self.inventory, "baseline.android: unknown key invented")

    def test_unknown_inventory_key_is_rejected(self) -> None:
        self.inventory["files"][0]["invented"] = True
        self.assert_rejected(
            self.baseline, self.inventory, "inventory.files\\[0\\]: unknown key invented", sync_inventory=True
        )

    def test_missing_inventory_method_is_rejected(self) -> None:
        del self.inventory["method"]
        self.assert_rejected(self.baseline, self.inventory, "missing keys \\['method'\\]", sync_inventory=True)

    def test_forged_inventory_method_is_rejected(self) -> None:
        self.inventory["method"]["scope"] = "trust supplied labels"
        self.assert_rejected(self.baseline, self.inventory, "does not match generator method", sync_inventory=True)

    def test_forged_category_is_rejected(self) -> None:
        self.inventory["files"][0]["category"] = "public_production"
        self.assert_rejected(self.baseline, self.inventory, "inventory category mismatch", sync_inventory=True)

    def test_summary_tampering_is_rejected(self) -> None:
        self.inventory["summary"]["by_category"]["tests"] += 1
        self.assert_rejected(self.baseline, self.inventory, "inventory summary mismatch", sync_inventory=True)

    def test_line_count_tampering_is_rejected(self) -> None:
        self.inventory["files"][0]["physical_lines"] += 1
        self.assert_rejected(self.baseline, self.inventory, "inventory line mismatch", sync_inventory=True)

    def test_digest_tampering_is_rejected(self) -> None:
        self.inventory["files"][0]["sha256"] = "0" * 64
        self.assert_rejected(self.baseline, self.inventory, "inventory sha256 mismatch", sync_inventory=True)

    def test_receipt_pass_with_failed_check_is_rejected(self) -> None:
        self.baseline["fresh_checks"][0]["status"] = "RECORDED_FAILURE"
        self.baseline["fresh_checks"][0]["exit_code"] = 1
        self.assert_rejected(self.baseline, self.inventory, "recorded check is a failure")

    def test_not_run_used_as_executed_status_is_rejected(self) -> None:
        self.baseline["fresh_checks"][0]["status"] = "NOT_RUN"
        self.assert_rejected(self.baseline, self.inventory, "value outside enum")

    def test_pass_used_for_not_run_lane_is_rejected(self) -> None:
        self.baseline["not_run"][0]["status"] = "PASS"
        self.assert_rejected(self.baseline, self.inventory, "expected constant 'NOT_RUN'")

    def test_missing_required_gate_is_rejected(self) -> None:
        self.baseline["fresh_checks"].pop()
        self.assert_rejected(self.baseline, self.inventory, "required gate IDs mismatch")

    def test_fabricated_gate_argv_is_rejected(self) -> None:
        self.baseline["fresh_checks"][0]["argv"] = ["true"]
        self.assert_rejected(self.baseline, self.inventory, "argv does not match allowlisted gate spec")

    def test_full_ga_maturity_claim_is_rejected(self) -> None:
        self.baseline["maturity_assessment"]["status"] = "FULL_GA"
        self.assert_rejected(self.baseline, self.inventory, "value outside enum")

    def test_completed_milestones_are_derived_and_closed(self) -> None:
        self.baseline["maturity_assessment"]["completed_milestones"].append("M24")
        self.assert_rejected(self.baseline, self.inventory, "derived M00/M01 only")

    def test_empty_or_forged_pr_manifest_is_rejected(self) -> None:
        for ancestry in ([], self.baseline["source"]["dependency_pr_ancestry"][:-1]):
            with self.subTest(entries=len(ancestry)):
                baseline = copy.deepcopy(self.baseline)
                baseline["source"]["dependency_pr_ancestry"] = ancestry
                self.assert_rejected(baseline, self.inventory, "exactly derive PRs 18-24")

    def test_candidate_signer_continuity_cannot_be_claimed(self) -> None:
        self.baseline["release_artifact"]["candidate_signer_continuity"] = "PASS"
        self.assert_rejected(self.baseline, self.inventory, "expected constant 'NOT_RUN'")

    def test_unverified_signer_subject_is_rejected_as_unknown(self) -> None:
        self.baseline["release_artifact"]["signer_subject"] = "CN=Forged"
        self.assert_rejected(self.baseline, self.inventory, "unknown key signer_subject")

    def test_nested_secret_keys_and_local_paths_are_rejected(self) -> None:
        samples = [
            ("api_key", "redacted"),
            ("nested", "/private/tmp/evidence"),
            ("nested", "/opt/local/evidence"),
            ("nested", "/Volumes/build/evidence"),
        ]
        for key, value in samples:
            with self.subTest(key=key, value=value):
                baseline = copy.deepcopy(self.baseline)
                baseline["fresh_checks"][0]["environment"][key] = value
                self.assert_rejected(baseline, self.inventory, "forbidden")
        with self.assertRaisesRegex(ValueError, "secret-like key is forbidden"):
            reject_sensitive_values({"safe": {"token": "redacted"}})

    def test_combined_forgery_is_rejected(self) -> None:
        self.baseline["maturity_assessment"]["completed_milestones"] = ["M00", "M01", "M24"]
        self.baseline["source"]["dependency_pr_ancestry"] = []
        self.baseline["release_artifact"]["candidate_signer_continuity"] = "PASS"
        self.baseline["fresh_checks"][0]["environment"]["password"] = "forged"
        self.assert_rejected(self.baseline, self.inventory, "expected constant|derived M00/M01 only")

    def test_critical_file_digest_tampering_is_rejected(self) -> None:
        self.baseline["critical_files"][0]["sha256"] = "0" * 64
        self.assert_rejected(self.baseline, self.inventory, "critical_files digest mismatch")

    def test_secret_and_cross_platform_private_paths_are_rejected(self) -> None:
        samples = [
            "Bearer abcdefghijklmnop",
            "api_key=abcdefghijklmnop",
            "/Users/example/private/file",
            "C:\\Users\\example\\private.txt",
            "\\\\server\\share\\private.txt",
        ]
        for sample in samples:
            with self.subTest(sample=sample):
                baseline = copy.deepcopy(self.baseline)
                baseline["fresh_checks"][0]["environment"]["injected"] = sample
                self.assert_rejected(baseline, self.inventory, "forbidden")

    def test_dirty_worktree_is_rejected_by_live_attestation(self) -> None:
        with patch("validate_autonomous_maturity_evidence.checked_output", return_value=" M changed"):
            with self.assertRaisesRegex(ValueError, "clean worktree"):
                attest_git_and_critical_files(self.baseline)

    def test_changed_head_parent_is_rejected_by_live_attestation(self) -> None:
        def output(argv: tuple[str, ...], **_: object) -> str:
            if argv == ("git", "status", "--porcelain=v1"):
                return ""
            if argv == ("git", "rev-parse", "HEAD"):
                return "f" * 40 + "\n"
            if argv == ("git", "rev-parse", "HEAD^"):
                return "0" * 40 + "\n"
            raise AssertionError(argv)

        with patch("validate_autonomous_maturity_evidence.checked_output", side_effect=output):
            with self.assertRaisesRegex(ValueError, "HEAD parent"):
                attest_git_and_critical_files(self.baseline)

    def test_fake_release_tag_is_rejected_by_live_attestation(self) -> None:
        source = self.baseline["source"]

        def output(argv: tuple[str, ...], **_: object) -> str:
            if argv == ("git", "status", "--porcelain=v1"):
                return ""
            if argv == ("git", "rev-parse", "HEAD"):
                return "f" * 40 + "\n"
            if argv == ("git", "rev-parse", "HEAD^"):
                return source["evidence_parent_sha"] + "\n"
            if argv[:4] == ("git", "diff-tree", "--no-commit-id", "--name-only"):
                return "\n".join(self.baseline["commit_binding"]["allowed_commit_paths"]) + "\n"
            if argv[:3] == ("git", "ls-tree", "HEAD"):
                return f"100644 blob {'a' * 40}\t{argv[-1]}\n"
            if argv[:2] == ("git", "hash-object"):
                return "a" * 40 + "\n"
            if argv == ("git", "rev-parse", source["release_tag"]):
                return "0" * 40 + "\n"
            expected = {
                source["implementation_baseline_ref"]: source["implementation_baseline_sha"],
                "origin/main": source["origin_main_sha"],
            }
            if argv[:2] == ("git", "rev-parse") and argv[2] in expected:
                return expected[argv[2]] + "\n"
            raise AssertionError(argv)

        with patch("validate_autonomous_maturity_evidence.checked_output", side_effect=output):
            with self.assertRaisesRegex(ValueError, "release tag object mismatch"):
                attest_git_and_critical_files(self.baseline)

    def test_fake_public_artifact_metadata_is_rejected(self) -> None:
        metadata = {
            "tagName": self.baseline["source"]["release_tag"],
            "url": self.baseline["release_artifact"]["source"],
            "isDraft": False,
            "isPrerelease": False,
            "publishedAt": self.baseline["release_artifact"]["published_at"],
            "assets": [],
        }
        with patch("validate_autonomous_maturity_evidence.checked_output", return_value=json.dumps(metadata)):
            with self.assertRaisesRegex(ValueError, "public release assets missing"):
                attest_public_artifact(self.baseline)

    def test_fake_public_artifact_signer_is_rejected(self) -> None:
        baseline = copy.deepcopy(self.baseline)
        release = baseline["release_artifact"]
        apk_bytes = b"APK"
        release["apk_size_bytes"] = len(apk_bytes)
        release["apk_sha256"] = hashlib.sha256(apk_bytes).hexdigest()
        checksum_bytes = f"{release['apk_sha256']}  {release['apk_name']}\n".encode()
        release["checksum_file_sha256"] = hashlib.sha256(checksum_bytes).hexdigest()
        metadata = {
            "tagName": baseline["source"]["release_tag"],
            "url": release["source"], "isDraft": False, "isPrerelease": False,
            "publishedAt": release["published_at"],
            "assets": [
                {"name": release["apk_name"], "size": len(apk_bytes), "digest": f"sha256:{release['apk_sha256']}", "url": "https://example.test/apk"},
                {"name": "SHA256SUMS.txt", "digest": f"sha256:{release['checksum_file_sha256']}", "url": "https://example.test/sums"},
            ],
        }

        def download(_: str, __: str, destination: Path, apk_name: str) -> None:
            (destination / apk_name).write_bytes(apk_bytes)
            (destination / "SHA256SUMS.txt").write_bytes(checksum_bytes)

        def output(argv: tuple[str, ...], **_: object) -> str:
            if argv[:3] == ("gh", "release", "view"):
                return json.dumps(metadata)
            if "apksigner" in argv[0]:
                return "Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: " + "0" * 64
            raise AssertionError(argv)

        with patch("validate_autonomous_maturity_evidence.checked_output", side_effect=output), \
             patch("validate_autonomous_maturity_evidence.download_public_assets", side_effect=download), \
             patch("validate_autonomous_maturity_evidence.sdk_tool", return_value=Path("apksigner")):
            with self.assertRaisesRegex(ValueError, "public APK signer mismatch"):
                attest_public_artifact(baseline)


if __name__ == "__main__":
    unittest.main()
