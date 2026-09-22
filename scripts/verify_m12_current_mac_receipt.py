#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from m12_current_mac_lib import validate_receipt


def main() -> int:
    parser = argparse.ArgumentParser(description="Fail-closed M12 receipt verifier")
    parser.add_argument("receipt", type=Path)
    args = parser.parse_args()
    try:
        raw = args.receipt.read_bytes()
        if len(raw) > 128 * 1024:
            raise ValueError("receipt exceeds 128 KiB")
        receipt = json.loads(raw.decode("utf-8"))
        if not isinstance(receipt, dict):
            raise ValueError("receipt must be an object")
        repo_root = Path(__file__).resolve().parent.parent
        validate_receipt(receipt, repo_root=repo_root)
    except Exception as error:
        print(f"M12 receipt FAIL type={type(error).__name__}", file=sys.stderr)
        return 1
    summary = receipt["summary"]
    print(f"M12 receipt PASS result={summary['result']} pass={summary['pass']} fail={summary['fail']} not_run={summary['notRun']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
