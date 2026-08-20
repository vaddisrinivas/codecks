#!/usr/bin/env python3
"""Validate the closed M09D Theme Studio receipt against current bytes."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from collect_m09d_theme_studio import RECEIPT, ROOT, collect_receipt, verify_detector_live
from strict_json_schema import validate_json_schema

SCHEMA = ROOT / "tools/evidence/schemas/codecks-m09d-theme-studio-v1.schema.json"


def validate_data(data: dict, detector: Path) -> None:
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    validate_json_schema(data, schema)
    expected = collect_receipt()
    if data != expected:
        raise ValueError("M09D receipt does not exactly match current source and artifact bytes")
    verify_detector_live(detector)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt", nargs="?", type=Path, default=ROOT / RECEIPT)
    parser.add_argument("--detector", type=Path, required=True)
    args = parser.parse_args()
    validate_data(json.loads(args.receipt.read_text(encoding="utf-8")), args.detector)
    print("M09D_THEME_STUDIO_PASS")


if __name__ == "__main__":
    main()
