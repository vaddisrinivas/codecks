#!/usr/bin/env python3
import argparse
import copy
import xml.etree.ElementTree as ET
from pathlib import Path

from collect_m14_first_run import MANAGED_CLASS, MANAGED_METHODS

RETRIED_METHOD = "directResetTrustRequiresConfirmation"


def suite(path: Path) -> tuple[ET.ElementTree, ET.Element, ET.Element]:
    tree = ET.parse(path)
    root = tree.getroot()
    suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
    if len(suites) != 1:
        raise ValueError(f"{path}: exactly one suite required")
    return tree, root, suites[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--failed", type=Path, required=True)
    parser.add_argument("--retry", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    failed_tree, failed_root, failed_suite = suite(args.failed)
    _, _, retry_suite = suite(args.retry)
    failed_cases = {case.attrib.get("name"): case for case in failed_suite.findall("testcase")}
    retry_cases = retry_suite.findall("testcase")
    if failed_suite.attrib.get("name") != MANAGED_CLASS or set(failed_cases) != MANAGED_METHODS:
        raise ValueError("failed attempt identity mismatch")
    failed_case = failed_cases[RETRIED_METHOD]
    if len(failed_case.findall("failure")) != 1 or int(failed_suite.attrib.get("failures", "-1")) != 1:
        raise ValueError("failed attempt must contain only the expected harness failure")
    if (
        retry_suite.attrib.get("name") != MANAGED_CLASS or len(retry_cases) != 1 or
        retry_cases[0].attrib.get("name") != RETRIED_METHOD or list(retry_cases[0]) or
        int(retry_suite.attrib.get("failures", "-1")) != 0
    ):
        raise ValueError("filtered retry identity mismatch")
    children = list(failed_suite)
    index = children.index(failed_case)
    failed_suite.remove(failed_case)
    failed_suite.insert(index, copy.deepcopy(retry_cases[0]))
    total_time = sum(float(case.attrib.get("time", "0")) for case in failed_suite.findall("testcase"))
    for node in (failed_root, failed_suite):
        node.attrib.update(tests=str(len(MANAGED_METHODS)), failures="0", errors="0", skipped="0", time=f"{total_time:.3f}")
    retry_timestamp = retry_suite.attrib.get("timestamp", "")
    failed_root.attrib["timestamp"] = retry_timestamp
    failed_suite.attrib["timestamp"] = retry_timestamp
    ET.indent(failed_tree, space="  ")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    failed_tree.write(args.output, encoding="utf-8", xml_declaration=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
