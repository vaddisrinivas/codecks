#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from m12_current_mac_lib import collect_current_mac, write_receipt


def main() -> int:
    parser = argparse.ArgumentParser(description="Run non-destructive M12 current-Mac evidence matrix")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    repo_root = Path(__file__).resolve().parent.parent
    try:
        receipt = collect_current_mac(repo_root, Path.home())
        write_receipt(receipt, args.output.resolve(), repo_root=repo_root)
    except Exception as error:
        print(f"M12 FAIL code=harness_error type={type(error).__name__}", file=sys.stderr)
        return 2
    summary = receipt["summary"]
    print(f"M12 {summary['result']} pass={summary['pass']} fail={summary['fail']} not_run={summary['notRun']}")
    return 0 if summary["fail"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
