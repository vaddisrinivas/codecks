from __future__ import annotations

import copy
from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).parent
sys.path.insert(0, str(TOOLS))

import collect_m21_public_release_live as collector

SPEC = importlib.util.spec_from_file_location("m21_m23", TOOLS / "validate_m21_m23_local_readiness.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class M21M23ReadinessTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt = json.loads(MODULE.LIVE_PUBLIC.read_text())
        cls.state = json.loads(MODULE.STATE.read_text())
        cls.artifact = json.loads(MODULE.BASELINE.read_text())["release_artifact"]

    def test_current_package_live_recollects_and_passes(self):
        result = MODULE.validate(env={})
        self.assertEqual("LOCAL_SUPPORT_PACKAGE_READY", result["m21"])
        self.assertEqual("BLOCKED_SIGNING_AND_EXACT_ARTIFACT", result["m23"])
        self.assertEqual("STATIC_PRODUCTION_DARK_PASS", result["commercial"])
        self.assertEqual("AMBIENT_UNSET_CANONICAL_NOT_RUN", result["signingInputs"])

    def test_signing_presence_fails_before_live_collection_without_exposing_value(self):
        with patch.object(MODULE, "collect", side_effect=AssertionError("network must not run")):
            with self.assertRaisesRegex(ValueError, "signing inputs unexpectedly present"):
                MODULE.validate(env={"CODECKS_RELEASE_KEY_ALIAS": "do-not-print"})

    def test_candidate_and_public_provenance_mutations_fail(self):
        state = copy.deepcopy(self.state)
        state["candidate"]["artifact_admitted"] = True
        with self.assertRaisesRegex(ValueError, "candidate was promoted"):
            MODULE.validate_state_contract(state)
        state = copy.deepcopy(self.state)
        state["public_release"]["tag"] = "v0.1.36"
        with self.assertRaisesRegex(ValueError, "provenance drift"):
            MODULE.validate_state_contract(state)

    def test_document_and_external_boundaries_remain(self):
        guide = MODULE.GITHUB_GUIDE.read_text().replace("Never uninstall", "Avoid uninstalling")
        with self.assertRaisesRegex(ValueError, "GitHub guide missing"):
            MODULE.validate_document_contract(guide, MODULE.PLAY_DRAFT.read_text(), MODULE.M23_DRAFT.read_text(), self.artifact)
        self.assertIn("- [ ] M21 execute clean install, in-place update, broken-network UI", MODULE.TODO.read_text())

    def test_timestamp_shape_duration_and_future_fail(self):
        for edit, message in (
            (lambda x: x.update(startedAtUtc="2026-08-18 18:00:00Z"), "exact UTC seconds"),
            (lambda x: x.update(completedAtUtc="2026-08-18T19:00:00Z"), "duration invalid"),
        ):
            changed = copy.deepcopy(self.receipt)
            edit(changed)
            with self.assertRaisesRegex(ValueError, message):
                MODULE.validate_public_receipt(changed, self.state["public_release"], self.artifact)
        changed = copy.deepcopy(self.receipt)
        fixed = datetime(2026, 8, 18, 18, 0, tzinfo=timezone.utc)
        future = fixed + timedelta(days=1)
        changed["startedAtUtc"] = future.strftime("%Y-%m-%dT%H:%M:%SZ")
        changed["completedAtUtc"] = future.strftime("%Y-%m-%dT%H:%M:%SZ")
        with self.assertRaisesRegex(ValueError, "in the future"):
            MODULE.validate_public_receipt(changed, self.state["public_release"], self.artifact, now=fixed)

    def test_request_provenance_mutations_fail(self):
        for edit, message in (
            (lambda x: x["requests"]["apk"].update(requestUrl="https://example.invalid/app.apk"), "request provenance changed"),
            (lambda x: x["requests"]["apk"].update(status=404), "request provenance changed"),
            (lambda x: x["requests"]["releaseMetadata"]["headers"].update(date="Wed, 19 Aug 2099 00:00:00 GMT"), "response date invalid"),
        ):
            changed = copy.deepcopy(self.receipt)
            edit(changed)
            with self.assertRaisesRegex(ValueError, message):
                MODULE.validate_public_receipt(changed, self.state["public_release"], self.artifact)

    def test_consistent_tag_release_and_artifact_fabrications_fail(self):
        changed = copy.deepcopy(self.receipt)
        changed["tagLookup"]["tagObjectSha"] = "0" * 40
        changed["tagLookup"]["rawOutput"] = changed["tagLookup"]["rawOutput"].replace(MODULE.EXPECTED_TAG_OBJECT, "0" * 40)
        with self.assertRaisesRegex(ValueError, "tag provenance changed"):
            MODULE.validate_public_receipt(changed, self.state["public_release"], self.artifact)

        changed = copy.deepcopy(self.receipt)
        changed["artifact"]["apkSha256"] = "0" * 64
        for asset in changed["release"]["assets"]:
            if asset["name"] == collector.APK_NAME:
                asset["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(ValueError, "canonical release identity changed|public artifact identity changed"):
            MODULE.validate_public_receipt(changed, self.state["public_release"], self.artifact)

    def test_tool_and_source_binding_mutations_fail(self):
        changed = copy.deepcopy(self.receipt)
        changed["tooling"]["apksigner"]["sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Build Tools binding changed"):
            MODULE.validate_public_receipt(changed, self.state["public_release"], self.artifact)
        changed = copy.deepcopy(self.receipt)
        changed["sourceBindings"][collector.SOURCE_PATHS[0]] = "0" * 64
        with self.assertRaisesRegex(ValueError, "source binding changed"):
            MODULE.validate_public_receipt(changed, self.state["public_release"], self.artifact)

    def test_mutable_download_count_is_excluded_from_identity(self):
        raw = {
            "tag_name": collector.TAG, "html_url": collector.RELEASE_URL, "target_commitish": "main",
            "draft": False, "prerelease": False, "published_at": "2026-08-08T20:05:08Z",
            "assets": [
                {"name": collector.APK_NAME, "browser_download_url": f"{collector.DOWNLOAD_ROOT}/{collector.APK_NAME}", "size": 1, "digest": "sha256:x", "content_type": "apk", "state": "uploaded", "download_count": 1},
                {"name": collector.CHECKSUM_NAME, "browser_download_url": f"{collector.DOWNLOAD_ROOT}/{collector.CHECKSUM_NAME}", "size": 2, "digest": "sha256:y", "content_type": "text", "state": "uploaded", "download_count": 2},
            ],
        }
        first = collector.canonical_release(raw)
        raw["assets"][0]["download_count"] = 999
        self.assertEqual(first, collector.canonical_release(raw))
        self.assertNotIn("download_count", json.dumps(first))

    def test_atomic_failure_preserves_previous_receipt(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            output = root / "receipt.json"
            output.write_bytes(b"previous\n")
            with patch.object(collector.os, "replace", side_effect=OSError("interrupted")):
                with self.assertRaisesRegex(OSError, "interrupted"):
                    collector.atomic_write(output, b"new\n", root)
            self.assertEqual(b"previous\n", output.read_bytes())
            self.assertEqual([output], list(root.iterdir()))

    def test_atomic_write_replaces_complete_receipt(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            output = root / "receipt.json"
            collector.atomic_write(output, b'{"complete":true}\n', root)
            self.assertEqual({"complete": True}, json.loads(output.read_text()))


if __name__ == "__main__":
    unittest.main()
