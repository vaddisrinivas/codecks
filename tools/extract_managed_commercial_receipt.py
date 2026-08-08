#!/usr/bin/env python3
"""Reconstruct and verify a chunked managed-emulator commercial proof receipt."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import re


SCHEMA = "codecks.commercial-managed-proof.v1"
META = re.compile(r"CODECKS_COMMERCIAL_PROOF_RECEIPT_META=chunks:(\d+);sha256:([0-9a-f]{64})")
CHUNK = re.compile(r"CODECKS_COMMERCIAL_PROOF_RECEIPT_CHUNK=(\d+)/(\d+):([A-Za-z0-9+/=]+)")


def extract(path: Path) -> dict:
    text = path.read_text(errors="strict")
    metadata = META.findall(text)
    if len(metadata) != 1:
        raise ValueError(f"expected exactly one receipt metadata record, found {len(metadata)}")
    expected_count, expected_digest = int(metadata[0][0]), metadata[0][1]
    if expected_count < 1 or expected_count > 100:
        raise ValueError(f"receipt chunk count outside bounds: {expected_count}")

    chunks: dict[int, str] = {}
    for index_text, count_text, payload in CHUNK.findall(text):
        index, count = int(index_text), int(count_text)
        if count != expected_count or index < 1 or index > expected_count:
            raise ValueError(f"inconsistent receipt chunk header: {index}/{count}")
        if index in chunks:
            raise ValueError(f"duplicate receipt chunk: {index}")
        chunks[index] = payload
    missing = [index for index in range(1, expected_count + 1) if index not in chunks]
    if missing:
        raise ValueError(f"missing receipt chunks: {missing}")

    raw = base64.b64decode("".join(chunks[index] for index in range(1, expected_count + 1)), validate=True)
    if len(raw) > 1_000_000:
        raise ValueError("decoded receipt exceeds 1 MiB")
    actual_digest = hashlib.sha256(raw).hexdigest()
    if actual_digest != expected_digest:
        raise ValueError(f"receipt digest mismatch: expected {expected_digest}, got {actual_digest}")
    result = json.loads(raw)
    if not isinstance(result, dict) or result.get("schema") != SCHEMA:
        raise ValueError("unexpected managed proof receipt schema")
    if result.get("target_package") != "app.codecks":
        raise ValueError("managed proof receipt targets the wrong package")
    if result.get("overall") not in {"PASS", "FAIL", "NOT_RUN"}:
        raise ValueError("invalid managed proof overall status")
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("logcat", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = extract(args.logcat)
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered)
    print(rendered, end="")
    raise SystemExit(1 if result["overall"] == "FAIL" else 0)


if __name__ == "__main__":
    main()
