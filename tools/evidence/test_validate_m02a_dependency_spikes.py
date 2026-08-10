import json
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RECEIPT = ROOT / "tasks/test-evidence/m02a/dependency-spike-results.json"


class M02AReceiptTest(unittest.TestCase):
    def test_receipt_is_machine_validated(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "tools/evidence/validate_m02a_dependency_spikes.py"), str(RECEIPT)],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("5 candidates", result.stdout)

    def test_all_candidates_have_fail_closed_decisions(self):
        candidates = json.loads(RECEIPT.read_text())["candidates"]
        self.assertEqual(5, len(candidates))
        self.assertEqual({"REJECT"}, {candidate["decision"] for candidate in candidates})


if __name__ == "__main__":
    unittest.main()
