#!/usr/bin/env python3

from __future__ import annotations

import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from collect_m09d_theme_studio import (
    C1B_CHANGED_PATHS, C1_CHANGED_PATHS, C1_REVIEWED_COMMIT, C2_ARTIFACT_PATHS,
    DIRTY_REVIEW_MODIFIED_PATHS, DIRTY_REVIEW_PATHS, GIT_TIMEOUT_SECONDS,
    GRADLE_TIMEOUT_SECONDS, REBUILD_PROJECTED_BYTES, REBUILD_RESERVE_BYTES,
    RECEIPT, ROOT, atomic_copy_bytes, execute_detached_rebuild, sha_bytes,
    validate_commit_relationship_values, validate_dirty_review_entries, verify_rebuilt_apk,
)
from strict_json_schema import validate_json_schema
from validate_m09d_theme_studio import validate_data


class M09DSourceContractTest(unittest.TestCase):
    def test_dirty_review_scope_rejects_extra_and_status_substitution(self) -> None:
        entries = [
            f"{' M' if path in DIRTY_REVIEW_MODIFIED_PATHS else '??'} {path}"
            for path in sorted(DIRTY_REVIEW_PATHS)
        ]
        validate_dirty_review_entries(entries)
        with self.assertRaises(ValueError):
            validate_dirty_review_entries(entries + ["?? unexpected.txt"])
        changed = list(entries)
        changed[0] = "?? " + changed[0][3:]
        with self.assertRaises(ValueError):
            validate_dirty_review_entries(changed)

    def test_commit_topology_rejects_ancestor_path_and_parent_substitution(self) -> None:
        values = {
            "source_commit": "1" * 40,
            "artifact_commit": "2" * 40,
            "receipt_commit": "3" * 40,
            "base_is_ancestor": True,
            "source_parents": (C1_REVIEWED_COMMIT,),
            "source_commit_paths": C1B_CHANGED_PATHS,
            "source_paths": C1_CHANGED_PATHS,
            "artifact_parent": "1" * 40,
            "artifact_paths": C2_ARTIFACT_PATHS,
            "receipt_parent": "2" * 40,
            "receipt_paths": frozenset({RECEIPT.as_posix()}),
        }
        validate_commit_relationship_values(**values)
        for key, replacement in (
            ("base_is_ancestor", False),
            ("source_parents", ("4" * 40,)),
            ("source_parents", (C1_REVIEWED_COMMIT, "4" * 40)),
            ("source_commit_paths", frozenset({"substituted"})),
            ("source_paths", frozenset({"substituted"})),
            ("artifact_parent", "4" * 40),
            ("artifact_paths", frozenset({"substituted"})),
            ("receipt_parent", "4" * 40),
            ("receipt_paths", frozenset({"substituted"})),
        ):
            mutated = dict(values)
            mutated[key] = replacement
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_commit_relationship_values(**mutated)

    def test_rebuilt_apk_rejects_byte_hash_package_and_signer_substitution(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            committed = Path(directory) / "committed.apk"
            rebuilt = Path(directory) / "rebuilt.apk"
            committed.write_bytes(b"exact apk bytes")
            rebuilt.write_bytes(committed.read_bytes())
            identity = {
                "applicationId": "app.codecks.internal",
                "sha256": sha_bytes(committed.read_bytes()),
                "signerSha256": "a" * 64,
                "versionCode": "37",
                "versionName": "0.1.37-play-internal",
            }
            verify_rebuilt_apk("target", committed, rebuilt, identity, lambda _: dict(identity))
            rebuilt.write_bytes(b"substituted")
            with self.assertRaises(ValueError):
                verify_rebuilt_apk("target", committed, rebuilt, identity, lambda _: dict(identity))
            rebuilt.write_bytes(committed.read_bytes())
            for key, replacement in (
                ("sha256", "0" * 64),
                ("applicationId", "app.codecks"),
                ("signerSha256", "b" * 64),
                ("versionCode", "38"),
                ("versionName", "substituted"),
            ):
                actual = dict(identity)
                actual[key] = replacement
                with self.subTest(key=key), self.assertRaises(ValueError):
                    verify_rebuilt_apk("target", committed, rebuilt, identity, lambda _, value=actual: value)

    def test_final_validator_cannot_skip_live_rebuild(self) -> None:
        data = {
            "sourceBinding": {"sourceCommit": "1" * 40},
            "targetApk": {"path": "target"},
            "testApk": {"path": "test"},
        }
        with (
            patch("validate_m09d_theme_studio.validate_json_schema"),
            patch("validate_m09d_theme_studio.collect_receipt", return_value=data),
            patch("validate_m09d_theme_studio.verify_detector_live"),
            patch("validate_m09d_theme_studio.verify_reproducible_apk_build") as rebuild,
        ):
            validate_data(data, Path("detect.mjs"))
        rebuild.assert_called_once_with("1" * 40, data["targetApk"], data["testApk"])

    def test_detached_rebuild_rejects_insufficient_temp_capacity_before_subprocess(self) -> None:
        calls = []
        with self.assertRaisesRegex(ValueError, "projected build bytes plus reserve"):
            execute_detached_rebuild(
                "1" * 40, {}, {}, runner=lambda *args, **kwargs: calls.append((args, kwargs)),
                disk_usage=lambda _: SimpleNamespace(
                    free=REBUILD_PROJECTED_BYTES + REBUILD_RESERVE_BYTES - 1,
                ),
            )
        self.assertEqual([], calls)

    def test_partial_add_timeout_is_cleaned_with_bounded_commands(self) -> None:
        calls = []

        def runner(command, **kwargs):
            calls.append((command, kwargs))
            if command[:3] == ["git", "worktree", "add"]:
                Path(command[-2]).mkdir(parents=True)
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            if command[:3] == ["git", "worktree", "remove"]:
                shutil.rmtree(command[-1])
            return subprocess.CompletedProcess(command, 0, "", "")

        with tempfile.TemporaryDirectory() as directory:
            admin = Path(directory) / "admin"
            admin.mkdir()
            with self.assertRaises(subprocess.TimeoutExpired):
                execute_detached_rebuild(
                    "1" * 40, {}, {}, runner=runner, admin_root=admin,
                    disk_usage=lambda _: SimpleNamespace(
                        free=REBUILD_PROJECTED_BYTES + REBUILD_RESERVE_BYTES,
                    ),
                )
        self.assertEqual(GIT_TIMEOUT_SECONDS, calls[0][1]["timeout"])
        self.assertTrue(any(call[0][:3] == ["git", "worktree", "remove"] for call in calls))
        self.assertFalse(any(call[0][:3] == ["git", "worktree", "prune"] for call in calls))

    def test_build_timeout_and_cleanup_failure_report_both(self) -> None:
        calls = []

        def runner(command, **kwargs):
            calls.append((command, kwargs))
            if command[:3] == ["git", "worktree", "add"]:
                Path(command[-2]).mkdir(parents=True)
                return subprocess.CompletedProcess(command, 0)
            if command[0] == "./gradlew":
                raise subprocess.TimeoutExpired(command, kwargs["timeout"])
            if command[:3] == ["git", "worktree", "remove"]:
                return subprocess.CompletedProcess(command, 1, "", "busy")
            return subprocess.CompletedProcess(command, 0, "", "")

        with tempfile.TemporaryDirectory() as directory:
            admin = Path(directory) / "admin"
            admin.mkdir()
            owned_temp = Path(directory) / "owned-temp"

            def make_temp(**_):
                owned_temp.mkdir()
                return str(owned_temp)

            def fail_temp_cleanup(_):
                raise OSError("temp cleanup failed")

            try:
                with self.assertRaisesRegex(RuntimeError, "detached rebuild failed.*cleanup failed") as raised:
                    execute_detached_rebuild(
                        "1" * 40, {}, {}, runner=runner, admin_root=admin,
                        make_temp=make_temp, remove_tree=fail_temp_cleanup,
                        disk_usage=lambda _: SimpleNamespace(
                            free=REBUILD_PROJECTED_BYTES + REBUILD_RESERVE_BYTES,
                        ),
                    )
                self.assertIn("exact detached worktree removal failed", str(raised.exception))
                self.assertIn("temp cleanup failed", str(raised.exception))
            finally:
                if owned_temp.exists():
                    shutil.rmtree(owned_temp)
        self.assertIsInstance(raised.exception.__cause__, subprocess.TimeoutExpired)
        build = next(call for call in calls if call[0][0] == "./gradlew")
        self.assertEqual(GRADLE_TIMEOUT_SECONDS, build[1]["timeout"])

    def test_cleanup_failure_alone_fails(self) -> None:
        def runner(command, **kwargs):
            if command[:3] == ["git", "worktree", "add"]:
                Path(command[-2]).mkdir(parents=True)
                return subprocess.CompletedProcess(command, 0)
            if command[:3] == ["git", "worktree", "remove"]:
                return subprocess.CompletedProcess(command, 1, "", "busy")
            return subprocess.CompletedProcess(command, 0, "", "")

        with tempfile.TemporaryDirectory() as directory:
            admin = Path(directory) / "admin"
            admin.mkdir()
            with patch("collect_m09d_theme_studio.verify_rebuilt_apk"), self.assertRaisesRegex(
                RuntimeError, "exact detached worktree removal failed",
            ):
                execute_detached_rebuild(
                    "1" * 40, {}, {}, runner=runner, admin_root=admin,
                    disk_usage=lambda _: SimpleNamespace(
                        free=REBUILD_PROJECTED_BYTES + REBUILD_RESERVE_BYTES,
                    ),
                )

    def test_unrelated_admin_entry_is_unchanged_and_global_prune_is_never_run(self) -> None:
        commands = []
        with tempfile.TemporaryDirectory() as directory:
            admin = Path(directory) / "admin"
            unrelated = admin / "unrelated"
            unrelated.mkdir(parents=True)
            marker = unrelated / "marker"
            marker.write_text("unchanged", encoding="utf-8")

            def runner(command, **kwargs):
                commands.append(command)
                if command[:3] == ["git", "worktree", "add"]:
                    checkout = Path(command[-2])
                    checkout.mkdir(parents=True)
                    owned = admin / "owned"
                    owned.mkdir()
                    (owned / "gitdir").write_text(str(checkout / ".git"), encoding="utf-8")
                    return subprocess.CompletedProcess(command, 0)
                if command[:3] == ["git", "worktree", "remove"]:
                    shutil.rmtree(command[-1])
                    shutil.rmtree(admin / "owned")
                    return subprocess.CompletedProcess(command, 0, "", "")
                return subprocess.CompletedProcess(command, 0)

            with patch("collect_m09d_theme_studio.verify_rebuilt_apk"):
                execute_detached_rebuild(
                    "1" * 40, {}, {}, runner=runner, admin_root=admin,
                    disk_usage=lambda _: SimpleNamespace(
                        free=REBUILD_PROJECTED_BYTES + REBUILD_RESERVE_BYTES,
                    ),
                )
            self.assertEqual("unchanged", marker.read_text(encoding="utf-8"))
            self.assertFalse(any(command[:3] == ["git", "worktree", "prune"] for command in commands))

    def test_temp_cleanup_failure_alone_fails_after_exact_worktree_remove(self) -> None:
        def runner(command, **kwargs):
            if command[:3] == ["git", "worktree", "add"]:
                Path(command[-2]).mkdir(parents=True)
                return subprocess.CompletedProcess(command, 0)
            if command[:3] == ["git", "worktree", "remove"]:
                shutil.rmtree(command[-1])
                return subprocess.CompletedProcess(command, 0, "", "")
            return subprocess.CompletedProcess(command, 0)

        with tempfile.TemporaryDirectory() as directory:
            admin = Path(directory) / "admin"
            admin.mkdir()
            owned_temp = Path(directory) / "owned-temp"

            def make_temp(**_):
                owned_temp.mkdir()
                return str(owned_temp)

            try:
                with (
                    patch("collect_m09d_theme_studio.verify_rebuilt_apk"),
                    self.assertRaisesRegex(RuntimeError, "temp cleanup failed"),
                ):
                    execute_detached_rebuild(
                        "1" * 40, {}, {}, runner=runner, admin_root=admin,
                        make_temp=make_temp,
                        remove_tree=lambda _: (_ for _ in ()).throw(OSError("temp cleanup failed")),
                        disk_usage=lambda _: SimpleNamespace(
                            free=REBUILD_PROJECTED_BYTES + REBUILD_RESERVE_BYTES,
                        ),
                    )
            finally:
                if owned_temp.exists():
                    shutil.rmtree(owned_temp)


class M09DThemeStudioReceiptTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.receipt = json.loads((ROOT / RECEIPT).read_text(encoding="utf-8"))
        cls.detector = Path(os.environ["IMPECCABLE_DETECTOR_PATH"])

    def test_current_receipt_passes(self) -> None:
        validate_data(copy.deepcopy(self.receipt), self.detector)

    def assert_rejected(self, mutate) -> None:
        value = copy.deepcopy(self.receipt)
        mutate(value)
        with self.assertRaises(Exception):
            validate_data(value, self.detector)

    def test_mutations_fail_closed(self) -> None:
        mutations = (
            lambda value: value["sourceBinding"].update(sourceDiffSha256="0" * 64),
            lambda value: value["sourceBinding"]["sources"][0].update(sha256="0" * 64),
            lambda value: value["targetApk"].update(sha256="0" * 64),
            lambda value: value["testApk"].update(sha256="0" * 64),
            lambda value: value["targetApk"].update(signerSha256="0" * 64),
            lambda value: value["tooling"]["apksigner"].update(sha256="0" * 64),
            lambda value: value["reproducibleBuild"].update(validation="OPTIONAL"),
            lambda value: value["reproducibleBuild"].update(sourceMode="CURRENT_HEAD"),
            lambda value: value["reproducibleBuild"]["command"].__setitem__(2, ":app:assembleOssRelease"),
            lambda value: value["reproducibleBuild"]["command"].remove("--no-build-cache"),
            lambda value: value["reproducibleBuild"].update(targetOutput=value["reproducibleBuild"]["testOutput"]),
            lambda value: value["commitChain"].update(artifactCommit="0" * 40),
            lambda value: value["commitChain"]["artifacts"][0].update(sha256="0" * 64),
            lambda value: value["runs"][0]["result"].update(sha256="0" * 64),
            lambda value: value["runs"][1]["deviceInfo"].update(sha256="0" * 64),
            lambda value: value["runs"][0]["textproto"].update(sha256="0" * 64),
            lambda value: value["runs"][1].update(device="pixel6Api35"),
            lambda value: value["runs"][0]["topology"].update(model="Pixel Tablet"),
            lambda value: value["runs"][0]["topology"].update(formFactor="tablet"),
            lambda value: value["runs"][1]["topology"].update(smallestScreenWidthRule="<600dp"),
            lambda value: value["runs"][1]["topology"].update(resultPathRoot=value["runs"][0]["topology"]["resultPathRoot"]),
            lambda value: value["detectorGate"].update(sha256="0" * 64),
            lambda value: value["detectorGate"].update(result=[{"finding": "regression"}]),
            lambda value: value["detectorGate"]["targets"][0].update(sha256="0" * 64),
            lambda value: value["runs"][0]["result"].update(tests=12),
            lambda value: value["runs"][0]["result"]["methods"].pop(),
            lambda value: value["excludedAttempts"][0].update(status="PASS"),
            lambda value: value["excludedAttempts"][0].update(eligibleForPass=True),
            lambda value: value["excludedAttempts"][1]["artifact"].update(sha256="0" * 64),
            lambda value: value.update(extra="not closed"),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                self.assert_rejected(mutate)

    def test_atomic_writer_replaces_complete_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_bytes(b"old")
            atomic_copy_bytes(b"new\n", path)
            self.assertEqual(b"new\n", path.read_bytes())
            self.assertFalse(any(item.name.startswith(".receipt.json.") for item in path.parent.iterdir()))

    def test_phone_and_tablet_artifact_bytes_cannot_be_swapped(self) -> None:
        phone = ROOT / "tasks/test-evidence/m09d-theme-studio/runtime/phone/device-info.pb"
        tablet = ROOT / "tasks/test-evidence/m09d-theme-studio/runtime/tablet/device-info.pb"
        phone_raw, tablet_raw = phone.read_bytes(), tablet.read_bytes()
        try:
            atomic_copy_bytes(tablet_raw, phone)
            atomic_copy_bytes(phone_raw, tablet)
            with self.assertRaises(Exception):
                validate_data(copy.deepcopy(self.receipt), self.detector)
        finally:
            atomic_copy_bytes(phone_raw, phone)
            atomic_copy_bytes(tablet_raw, tablet)

    def test_textproto_device_path_substitution_is_rejected(self) -> None:
        path = ROOT / "tasks/test-evidence/m09d-theme-studio/runtime/phone/test-result.sanitized.textproto"
        original = path.read_bytes()
        substituted = original.replace(b"pixel6Api35", b"m10TabletApi35")
        self.assertNotEqual(original, substituted)
        try:
            atomic_copy_bytes(substituted, path)
            with self.assertRaises(Exception):
                validate_data(copy.deepcopy(self.receipt), self.detector)
        finally:
            atomic_copy_bytes(original, path)

    def test_schema_drift_is_executed_and_rejected(self) -> None:
        schema_path = ROOT / "tools/evidence/schemas/codecks-m09d-theme-studio-v1.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        schema["properties"]["status"]["const"] = "FAIL"
        with self.assertRaises(ValueError):
            validate_json_schema(copy.deepcopy(self.receipt), schema)
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        schema["$defs"]["source"]["unimplementedKeyword"] = True
        with self.assertRaises(ValueError):
            validate_json_schema(copy.deepcopy(self.receipt), schema)


if __name__ == "__main__":
    unittest.main()
