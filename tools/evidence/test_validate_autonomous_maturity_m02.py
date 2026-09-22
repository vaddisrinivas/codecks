#!/usr/bin/env python3

from __future__ import annotations

import unittest
import sys
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from validate_autonomous_maturity_m02 import validate


class M02EvidenceTest(unittest.TestCase):
    def test_current_receipt_is_closed(self) -> None:
        validate()

    def test_missing_rollback_path_is_rejected(self) -> None:
        with patch("validate_autonomous_maturity_m02.is_base_checkout", return_value=True):
            with patch("validate_autonomous_maturity_m02.indexed_changes", return_value=["missing.kt"]):
                with self.assertRaisesRegex(ValueError, "rollback manifest"):
                    validate()


if __name__ == "__main__":
    unittest.main()
