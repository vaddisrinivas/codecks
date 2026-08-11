import json
import tempfile
import unittest
from pathlib import Path

from tools.evidence.collect_m14_first_run import UNIT_CLASS, UNIT_METHODS, exact_suite, receipt_digest
from tools.evidence.validate_m14_first_run import validate


class M14ReceiptValidatorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[2]
        cls.source = cls.root / "tasks/test-evidence/autonomous-maturity-m14-first-run.json"

    def mutate(self, transform, resign=True):
        data = json.loads(self.source.read_text())
        transform(data)
        if resign:
            data["receiptDigest"] = receipt_digest(data)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"
            path.write_text(json.dumps(data))
            return validate(path, self.root)

    def test_durable_receipt_passes(self):
        self.assertEqual([], validate(self.source, self.root))

    def test_event_result_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["journeys"][0].update(success=False)))

    def test_silent_dead_end_tamper_fails(self):
        def change(data):
            events = data["journeys"][0]["events"]
            events[:] = [event for event in events if event["kind"] != "RepairCallback"]
        self.assertTrue(self.mutate(change))

    def test_repeated_profile_fails(self):
        self.assertTrue(self.mutate(lambda data: data["profiles"].__setitem__(1, data["profiles"][0])))

    def test_wrong_return_destination_fails(self):
        self.assertTrue(self.mutate(lambda data: data["journeys"][0]["events"][-1].update(destination="settings")))

    def test_source_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["sourceDigests"].update(
            {next(iter(data["bindings"]["sourceDigests"])): "0" * 64}
        )))

    def test_validator_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["sourceDigests"].update(
            {"tools/evidence/validate_m14_first_run.py": "0" * 64}
        )))

    def test_corpus_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"].update(corpusDigest="0" * 64)))

    def test_command_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["commands"].append("unrecorded command")))

    def test_environment_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["environment"].update(network="USED")))

    def test_test_result_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["unit"].update(resultDigest="0" * 64)))

    def test_compose_result_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["managed"].update(resultDigest="0" * 64)))

    def test_managed_attempt_binding_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["managed"]["attempts"][0].update(
            digest="0" * 64
        )))

    def test_artifact_identity_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"]["managed"]["targetArtifact"].update(
            applicationId="app.codecks"
        )))

    def test_base_commit_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data["bindings"].update(baseCommit="0" * 40)))

    def test_receipt_digest_tamper_fails(self):
        self.assertTrue(self.mutate(lambda data: data.update(successes=0), resign=False))

    def test_human_claim_fails(self):
        self.assertTrue(self.mutate(lambda data: data.update(moderatedHumanPairing={"status": "PASS"})))

    def test_duplicate_suite_rejected(self):
        xml = "<testsuites tests='3' failures='0' errors='0' skipped='0'>" + \
            "<testsuite name='x'/><testsuite name='x'/></testsuites>"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.xml"
            path.write_text(xml)
            with self.assertRaises(ValueError):
                exact_suite(path, UNIT_CLASS, UNIT_METHODS)

    def test_foreign_case_rejected(self):
        cases = "".join(
            f"<testcase classname='{UNIT_CLASS}' name='{name}'/>" for name in sorted(UNIT_METHODS - {next(iter(UNIT_METHODS))})
        ) + f"<testcase classname='foreign.Suite' name='foreign'/>"
        xml = f"<testsuite name='{UNIT_CLASS}' tests='3' failures='0' errors='0' skipped='0'>{cases}</testsuite>"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.xml"
            path.write_text(xml)
            with self.assertRaises(ValueError):
                exact_suite(path, UNIT_CLASS, UNIT_METHODS)


if __name__ == "__main__":
    unittest.main()
