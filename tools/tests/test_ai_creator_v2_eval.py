import importlib.util
import tempfile
import unittest
from pathlib import Path


TOOL_PATH = Path(__file__).resolve().parents[1] / "ai_creator_v2_eval.py"
SPEC = importlib.util.spec_from_file_location("ai_creator_v2_eval", TOOL_PATH)
assert SPEC is not None and SPEC.loader is not None
eval_tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(eval_tool)


class AiCreatorV2EvalToolTest(unittest.TestCase):
    def test_checked_in_coverage_is_versioned_exhaustive_and_unique(self) -> None:
        version, assignments = eval_tool.read_coverage(eval_tool.COVERAGE, 120)

        self.assertEqual(2, version)
        self.assertEqual(eval_tool.REQUIRED_CATEGORIES, set(assignments))
        self.assertEqual(list(range(1, 121)), sorted(case for values in assignments.values() for case in values))

    def test_overlap_is_rejected_fail_closed(self) -> None:
        valid = eval_tool.COVERAGE.read_text(encoding="utf-8")
        corrupt = valid.replace("artifact_codec_roundtrip\t9-16", "artifact_codec_roundtrip\t8-16")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "coverage.tsv"
            path.write_text(corrupt, encoding="utf-8")
            with self.assertRaises(SystemExit):
                eval_tool.read_coverage(path, 120)

    def test_metrics_distinguish_not_run_from_fail(self) -> None:
        self.assertEqual("NOT_RUN", eval_tool.metric(0, 1, 0.95, False)["status"])
        self.assertEqual("FAIL", eval_tool.metric(0, 1, 0.95, True)["status"])
        self.assertEqual("FAIL", eval_tool.zero_metric(1, 19, True)["status"])

    def test_bypass_corpus_has_exact_immutable_ids_and_digest(self) -> None:
        rows = eval_tool.read_bypass_corpus(eval_tool.BYPASS_CORPUS)

        self.assertEqual(19, len(rows))
        self.assertEqual(eval_tool.EXPECTED_BYPASS_IDS, [case_id for case_id, _ in rows])
        self.assertEqual(eval_tool.EXPECTED_BYPASS_SHA256, eval_tool.canonical_hash(rows))

    def test_contract_never_promotes_static_manifest_to_execution(self) -> None:
        rows = eval_tool.read_corpus(eval_tool.CORPUS)
        counts = eval_tool.validate_counts(rows)
        version, coverage = eval_tool.read_coverage(eval_tool.COVERAGE, len(rows))
        coverage_hash = eval_tool.canonical_hash(
            [(category, f"{values[0]}-{values[-1]}") for category, values in coverage.items()],
        )
        contract = eval_tool.report_contract(
            counts,
            eval_tool.canonical_hash(rows),
            eval_tool.read_bypass_corpus(eval_tool.BYPASS_CORPUS),
            version,
            coverage,
            coverage_hash,
            False,
        )

        self.assertEqual("NOT_RUN", contract["overallStatus"])
        self.assertTrue(all(metric["status"] == "NOT_RUN" for metric in contract["combinedMetrics"].values()))
        self.assertTrue(all(provider["status"] == "NOT_RUN" for provider in contract["liveProviders"]))

    def test_failed_combined_metric_forces_failed_verdict(self) -> None:
        self.assertEqual("FAIL", eval_tool.metric(94, 100, 0.95, True)["status"])
        self.assertEqual("PASS", eval_tool.metric(95, 100, 0.95, True)["status"])


if __name__ == "__main__":
    unittest.main()
