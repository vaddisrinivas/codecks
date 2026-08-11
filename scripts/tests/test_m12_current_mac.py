from __future__ import annotations

import copy
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from m12_current_mac_lib import (  # noqa: E402
    LANE_IDS,
    SCHEMA_VERSION,
    CommandResult,
    LANE_POLICY,
    expected_bindings,
    is_host_key_mismatch,
    is_auth_denial,
    load_and_validate_schema,
    lane,
    run_bounded,
    run_discarded,
    safe_source_commit,
    validate_privacy,
    validate_receipt,
    validate_schema_instance,
    validate_source_commit,
    write_receipt,
)


def valid_receipt() -> dict:
    lanes = []
    for lane_id in LANE_IDS:
        evidence, codes = LANE_POLICY[lane_id]
        status = "PASS" if "PASS" in codes else "NOT_RUN"
        lanes.append(lane(lane_id, status, evidence, codes[status]))
    pass_count = sum(item["status"] == "PASS" for item in lanes)
    not_run_count = sum(item["status"] == "NOT_RUN" for item in lanes)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAtUtc": "2026-08-10T00:00:00Z",
        "sourceCommit": "a" * 40,
        "bindings": expected_bindings(SCRIPTS.parent),
        "environment": {
            "platform": "macOS",
            "architecture": "arm64",
            "macosVersion": "15.6",
            "evidenceScope": "CURRENT_MAC_ONLY",
        },
        "safety": {
            "sleepStateChanged": False,
            "serviceRestarted": False,
            "accountChanged": False,
            "authorizationChanged": False,
            "keyMaterialChanged": False,
            "hidStateChanged": False,
            "clipboardContentRecorded": False,
        },
        "lanes": lanes,
        "summary": {"result": "PASS_WITH_NOT_RUN", "pass": pass_count, "fail": 0, "notRun": not_run_count},
    }


class M12CurrentMacTest(unittest.TestCase):
    def test_json_schema_binds_exact_lane_order(self) -> None:
        schema_path = SCRIPTS.parent / "tasks" / "schemas" / "m12-current-mac-receipt.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        bound_ids = [item["properties"]["id"]["const"] for item in schema["properties"]["lanes"]["prefixItems"]]
        self.assertEqual(list(LANE_IDS), bound_ids)
        load_and_validate_schema(SCRIPTS.parent)

    def test_schema_loader_fails_closed_without_dependency(self) -> None:
        source = SCRIPTS.parent / "tasks" / "schemas" / "m12-current-mac-receipt.schema.json"
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = root / "tasks" / "schemas" / "m12-current-mac-receipt.schema.json"
            target.parent.mkdir(parents=True)
            target.write_text(source.read_text(encoding="utf-8").replace('"additionalProperties": false', '"additionalProperties": true', 1), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_and_validate_schema(root)

    def test_recursive_schema_rejects_security_field_mutations(self) -> None:
        schema = load_and_validate_schema(SCRIPTS.parent)
        mutations = (
            lambda r: r["environment"].__setitem__("platform", "Darwin"),
            lambda r: r["safety"].__setitem__("serviceRestarted", True),
            lambda r: r["lanes"][0].__setitem__("durationMs", 120001),
            lambda r: r["bindings"].__setitem__("unexpected", "0" * 64),
            lambda r: r.__setitem__("sourceCommit", "not-a-commit"),
            lambda r: r["lanes"][0].__setitem__("status", "UNKNOWN"),
            lambda r: r["lanes"][0].__setitem__("private", "value"),
            lambda r: r["lanes"].pop(),
        )
        for mutate in mutations:
            receipt = valid_receipt()
            mutate(receipt)
            with self.assertRaises(ValueError):
                validate_schema_instance(schema, receipt)

    def test_exact_matrix_validates(self) -> None:
        validate_receipt(valid_receipt())

    def test_missing_duplicate_reordered_and_unknown_lanes_fail_closed(self) -> None:
        for mutate in (
            lambda value: value["lanes"].pop(),
            lambda value: value["lanes"].__setitem__(1, value["lanes"][0]),
            lambda value: value["lanes"].reverse(),
            lambda value: value["lanes"][0].__setitem__("id", "unknown.lane"),
        ):
            receipt = valid_receipt()
            mutate(receipt)
            with self.assertRaises(ValueError):
                validate_receipt(receipt)

    def test_summary_and_safety_are_fail_closed(self) -> None:
        for path, replacement in (
            (("summary", "pass"), 24),
            (("summary", "result"), "PASS"),
            (("safety", "serviceRestarted"), True),
            (("safety", "keyMaterialChanged"), None),
            (("summary", "fail"), False),
        ):
            receipt = valid_receipt()
            receipt[path[0]][path[1]] = replacement
            with self.assertRaises(ValueError):
                validate_receipt(receipt)

    def test_secret_clipboard_host_account_and_key_material_are_rejected(self) -> None:
        canaries = (
            ("apiToken", "opaque"),
            ("accountId", "opaque"),
            ("clipboardContent", "private"),
            ("detail", "person@example.com"),
            ("detail", "192.168.1.8"),
            ("detail", "-----BEGIN PRIVATE KEY-----"),
        )
        for key, value in canaries:
            receipt = valid_receipt()
            receipt["lanes"][0][key] = value
            with self.assertRaises(ValueError):
                validate_privacy(receipt)

    def test_privacy_denylist_scans_all_nested_string_values(self) -> None:
        for value in ("/Users/private/path", "/home/private/path", "aa:bb:cc:dd:ee:ff", "SHA256:abcdefghijklmnopqrstu"):
            with self.assertRaises(ValueError):
                validate_privacy({"allowed": [{"nested": value}]})

    def test_timeout_output_bound_and_missing_tool_are_terminal(self) -> None:
        timeout = run_bounded(["/usr/bin/python3", "-c", "import time; time.sleep(1)"], timeout_seconds=0.02, max_output_bytes=32)
        self.assertTrue(timeout.timed_out)
        oversized = run_bounded(["/usr/bin/python3", "-c", "print('x'*5000)"], timeout_seconds=2, max_output_bytes=64)
        self.assertTrue(oversized.truncated)
        self.assertEqual(64, len(oversized.stdout))
        missing = run_bounded(["/usr/bin/env", "codecks-m12-test-tool-missing"], timeout_seconds=1, max_output_bytes=32)
        self.assertEqual(127, missing.return_code)

    def test_discarded_runner_never_retains_output(self) -> None:
        discarded = run_discarded(["/usr/bin/python3", "-c", "import sys; sys.stdout.write('private'); sys.stderr.write('private')"])
        self.assertEqual(0, discarded.return_code)
        self.assertEqual(b"", discarded.stdout)
        self.assertEqual(b"", discarded.stderr)

    def test_host_key_mismatch_requires_terminal_ssh_verification_failure(self) -> None:
        for message in (b"Host key verification failed.", b"REMOTE HOST IDENTIFICATION HAS CHANGED"):
            result = CommandResult(255, False, b"", message, False, 1)
            self.assertTrue(is_host_key_mismatch(result))
        self.assertFalse(is_host_key_mismatch(CommandResult(255, True, b"", b"Host key verification failed.", False, 1)))
        self.assertFalse(is_host_key_mismatch(CommandResult(1, False, b"", b"Host key verification failed.", False, 1)))

    def test_auth_denial_requires_permission_denied_signature(self) -> None:
        self.assertTrue(is_auth_denial(CommandResult(255, False, b"", b"Permission denied (publickey).", False, 1)))
        self.assertFalse(is_auth_denial(CommandResult(255, False, b"", b"Connection refused", False, 1)))
        self.assertFalse(is_auth_denial(CommandResult(255, True, b"", b"Permission denied", False, 1)))

    def test_mutation_external_and_lane_evidence_policy_fail_closed(self) -> None:
        for lane_id, field, value in (
            ("mutation.sleep_wake", "status", "PASS"),
            ("external.intel", "status", "PASS"),
            ("ssh.tcp", "evidence", "INJECTED_FAILURE"),
            ("failure.timeout", "code", "timeout_injection_failed"),
        ):
            receipt = valid_receipt()
            item = next(item for item in receipt["lanes"] if item["id"] == lane_id)
            item[field] = value
            with self.assertRaises(ValueError):
                validate_receipt(receipt)

    def test_rfc3339_calendar_duration_platform_arch_and_bindings_fail_closed(self) -> None:
        mutations = (
            lambda r: r.__setitem__("generatedAtUtc", "2026-02-30T00:00:00Z"),
            lambda r: r["lanes"][0].__setitem__("durationMs", 120001),
            lambda r: r["lanes"][0].__setitem__("durationMs", True),
            lambda r: r["environment"].__setitem__("platform", "Darwin"),
            lambda r: r["environment"].__setitem__("architecture", "amd64"),
            lambda r: r["bindings"].__setitem__("schemaSha256", "0" * 64),
            lambda r: r["bindings"].__setitem__("harnessSha256", "0" * 64),
            lambda r: r["bindings"].__setitem__("evidenceParentSha256", "0" * 64),
            lambda r: r.__setitem__("sourceCommit", "b" * 40),
        )
        for mutate in mutations:
            receipt = valid_receipt()
            receipt["sourceCommit"] = safe_source_commit(SCRIPTS.parent)
            mutate(receipt)
            with self.assertRaises(ValueError):
                validate_receipt(receipt, repo_root=SCRIPTS.parent)

    def test_source_commit_must_exist_and_be_ancestor(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            env = {**os.environ, "GIT_AUTHOR_NAME": "M12 Test", "GIT_AUTHOR_EMAIL": "m12@example.invalid", "GIT_COMMITTER_NAME": "M12 Test", "GIT_COMMITTER_EMAIL": "m12@example.invalid"}

            def git(*args: str) -> str:
                return subprocess.run(
                    ["/usr/bin/git", *args], cwd=root, env=env, check=True,
                    capture_output=True, text=True,
                ).stdout.strip()

            git("init", "-q")
            (root / "marker").write_text("ancestor\n", encoding="utf-8")
            git("add", "marker")
            git("commit", "-q", "-m", "ancestor")
            ancestor = git("rev-parse", "HEAD")
            validate_source_commit(root, ancestor)

            tree = git("write-tree")
            nonancestor = subprocess.run(
                ["/usr/bin/git", "commit-tree", tree, "-m", "nonancestor"],
                cwd=root, env=env, check=True, capture_output=True, text=True,
            ).stdout.strip()
            with self.assertRaisesRegex(ValueError, "not an ancestor"):
                validate_source_commit(root, nonancestor)
            with self.assertRaisesRegex(ValueError, "missing"):
                validate_source_commit(root, "f" * 40)

    def test_invalid_generation_time_fails_closed(self) -> None:
        receipt = valid_receipt()
        receipt["generatedAtUtc"] = "today"
        with self.assertRaises(ValueError):
            validate_receipt(receipt)

    def test_receipt_write_is_atomic_and_round_trips(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / "receipt.json"
            write_receipt(valid_receipt(), destination)
            decoded = json.loads(destination.read_text(encoding="utf-8"))
            validate_receipt(decoded)
            self.assertFalse(destination.with_suffix(".json.tmp").exists())


if __name__ == "__main__":
    unittest.main()
