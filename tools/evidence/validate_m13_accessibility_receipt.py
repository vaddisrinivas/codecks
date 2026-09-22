#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

from generate_m13_accessibility_receipt import BOUND_PATHS, MANAGED_CLASS, MANAGED_REQUIRED
from managed_execution_binding import validate_binding
from validate_autonomous_maturity_evidence import validate_schema_node

ROOT = Path(__file__).resolve().parents[2]
RECEIPT = ROOT / "tasks/test-evidence/autonomous-maturity-m13-accessibility.json"
SCHEMA = ROOT / "tools/evidence/schemas/autonomous-maturity-m13-accessibility-v1.schema.json"


def validate(path: Path = RECEIPT) -> None:
    data = json.loads(path.read_text())
    schema = json.loads(SCHEMA.read_text())
    validate_schema_node(data, schema, schema, "m13")
    validate_binding(
        ROOT,
        data["managed_execution"],
        source_paths=BOUND_PATHS,
        class_name=MANAGED_CLASS,
        methods={identity.rsplit(".", 1)[1] for identity in MANAGED_REQUIRED},
    )


if __name__ == "__main__":
    validate()
    print("PASS: M13 exact 13-case source/APK/XML/device receipt")
