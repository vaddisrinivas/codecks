#!/usr/bin/env python3
import argparse
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from tools.evidence.collect_m14_first_run import (
    COMMANDS, MANAGED_CLASS, MANAGED_METHODS, SOURCE_FILES, UNIT_CLASS, UNIT_METHODS,
    canonical, exact_suite, execution_environment, receipt_digest, sanitized_companion, sha256_bytes,
)
from tools.evidence.managed_execution_binding import validate_binding

SCENARIOS = {
    "hid_pairing", "ssh_setup", "helper_pairing", "permission_denial", "wrong_host",
    "sleeping_mac", "host_key_mismatch", "lost_key", "bluetooth_off", "network_loss",
}
UNSUPPORTED = {"developer-tools", "hidden-settings", "undocumented-ssh"}


def behavior_digest(events: list[dict]) -> str:
    def kotlin(value):
        if value is None:
            return "null"
        if isinstance(value, bool):
            return str(value).lower()
        return str(value)
    rows = []
    for event in events:
        rows.append("|".join(kotlin(event.get(field)) for field in (
            "kind", "supportCode", "repair", "ready", "destination", "helpRoute"
        )))
    return hashlib.sha256("\n".join(rows).encode()).hexdigest()


def validate(path: Path, root: Path | None = None) -> list[str]:
    root = root or Path(__file__).resolve().parents[2]
    try:
        data = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        return [f"unreadable receipt: {error}"]
    errors: list[str] = []
    expected_top = {
        "schemaVersion", "milestone", "profile", "total", "successes", "successRatePercent",
        "silentDeadEnds", "gate", "moderatedHumanPairing", "profiles", "journeys", "bindings", "receiptDigest",
    }
    if set(data) != expected_top:
        errors.append("missing or unknown top-level receipt field")
    profiles = data.get("profiles")
    journeys = data.get("journeys")
    if data.get("schemaVersion") != 2 or data.get("milestone") != "M14":
        errors.append("wrong schema or milestone")
    if data.get("profile") != "deterministic_clean_profile_state_machine":
        errors.append("wrong execution profile")
    if not isinstance(profiles, list) or len(profiles) != 100:
        errors.append("exactly 100 profiles required")
        profiles = profiles if isinstance(profiles, list) else []
    if not isinstance(journeys, list) or len(journeys) != 100:
        errors.append("exactly 100 journeys required")
        journeys = journeys if isinstance(journeys, list) else []
    profile_by_id = {row.get("caseId"): row for row in profiles if isinstance(row, dict)}
    if len(profile_by_id) != 100:
        errors.append("profile IDs must be unique")
    profile_counts = Counter(row.get("scenario") for row in profiles if isinstance(row, dict))
    if profile_counts != Counter({scenario: 10 for scenario in SCENARIOS}):
        errors.append(f"scenario profile counts invalid: {profile_counts}")
    variants = defaultdict(set)
    material = defaultdict(set)
    for row in profiles:
        if not isinstance(row, dict):
            continue
        if set(row) != {
            "caseId", "scenario", "variant", "transientFailures", "helpRequests", "processRestarts",
            "permissionMode", "responseDelayTicks",
        }:
            errors.append(f"non-canonical profile fields: {row.get('caseId')}")
        variants[row.get("scenario")].add(row.get("variant"))
        material[row.get("scenario")].add((row.get("transientFailures"), row.get("helpRequests"), row.get("processRestarts")))
    if any(variants[name] != set(range(10)) or len(material[name]) != 10 for name in SCENARIOS):
        errors.append("profiles are repeated rather than materially distinct")

    path_digests = set()
    for row in journeys:
        if not isinstance(row, dict):
            errors.append("journey row must be object")
            continue
        case_id = row.get("caseId")
        if set(row) != {
            "caseId", "scenario", "success", "durationMs", "retries", "silentDeadEnds",
            "comprehensionProxy", "intendedFeature", "returnedFeature", "returnedToIntendedFeature",
            "helpOffered", "helpUsed", "unsupportedEscapeHatchUsed", "pathDigest", "events",
        }:
            errors.append(f"non-canonical journey fields: {case_id}")
        profile = profile_by_id.get(case_id)
        events = row.get("events")
        if profile is None or row.get("scenario") != profile.get("scenario"):
            errors.append(f"journey/profile mismatch: {case_id}")
        if not isinstance(events, list) or not events:
            errors.append(f"missing events: {case_id}")
            continue
        if any(not isinstance(event, dict) or set(event) != {
            "kind", "atMs", "stateId", "title", "detail", "supportCode", "repair", "ready",
            "destination", "helpRoute",
        } for event in events):
            errors.append(f"non-canonical event fields: {case_id}")
        shown = [event for event in events if event.get("kind") == "StateShown" and not event.get("ready")]
        repaired = {event.get("stateId") for event in events if event.get("kind") == "RepairCallback"}
        dead_ends = sum(1 for event in shown if event.get("stateId") not in repaired)
        comprehension = all(
            event.get("title") and event.get("detail") and event.get("supportCode") and event.get("repair")
            for event in shown
        )
        terminal = next((event for event in reversed(events) if event.get("kind") == "StateShown"), {})
        returned = terminal.get("ready") is True and terminal.get("destination") == row.get("intendedFeature")
        retries = sum(1 for event in events if event.get("kind") == "RepairCallback" and event.get("repair") == "RetryNow")
        help_used = sum(1 for event in events if event.get("kind") == "HelpCallback")
        help_offered = all(event.get("helpRoute") for event in shown)
        unsupported = any(event.get("destination") in UNSUPPORTED for event in events)
        duration = max((event.get("atMs", -1) for event in events), default=-1)
        digest = behavior_digest(events)
        derived = {
            "success": returned and dead_ends == 0,
            "silentDeadEnds": dead_ends,
            "comprehensionProxy": comprehension,
            "returnedFeature": terminal.get("destination"),
            "returnedToIntendedFeature": returned and dead_ends == 0,
            "retries": retries,
            "helpOffered": help_offered,
            "helpUsed": help_used,
            "unsupportedEscapeHatchUsed": unsupported,
            "durationMs": duration,
            "pathDigest": digest,
        }
        for field, expected in derived.items():
            if row.get(field) != expected:
                errors.append(f"self-reported {field} disagrees with events: {case_id}")
        path_digests.add(digest)
    if len(path_digests) != 100:
        errors.append("executed behavior paths are not all distinct")
    successes = sum(1 for row in journeys if isinstance(row, dict) and row.get("success") is True)
    dead_ends = sum(row.get("silentDeadEnds", 0) for row in journeys if isinstance(row, dict))
    if data.get("total") != 100 or data.get("successes") != successes or data.get("successRatePercent") != successes:
        errors.append("summary counters disagree with journeys")
    if successes < 95 or dead_ends != 0 or data.get("silentDeadEnds") != dead_ends:
        errors.append("M14 scripted gate failed")
    if data.get("moderatedHumanPairing") != {"status": "NOT_RUN", "reason": "EXTERNAL_EVIDENCE_REMAINS"}:
        errors.append("human pairing must remain external NOT_RUN")

    bindings = data.get("bindings", {})
    expected_binding_keys = {
        "baseCommit", "sourceDigests", "sourceTreeDigest", "corpusDigest", "canonicalInputDigest",
        "unit", "managed", "managedExecution", "managedCompanionPrivacy", "commands", "commandDigest", "environment", "environmentDigest",
    }
    if set(bindings) != expected_binding_keys:
        errors.append("missing or unknown execution binding")
    source_digests = bindings.get("sourceDigests", {})
    actual_sources = {}
    for relative in SOURCE_FILES:
        source = root / relative
        if not source.is_file():
            errors.append(f"bound source missing: {relative}")
            continue
        actual_sources[relative] = sha256_bytes(source.read_bytes())
    if source_digests != actual_sources or bindings.get("sourceTreeDigest") != sha256_bytes(canonical(actual_sources)):
        errors.append("source binding mismatch")
    if bindings.get("corpusDigest") != sha256_bytes(canonical(profiles)):
        errors.append("corpus binding mismatch")
    canonical_input = {"profiles": profiles, "journeys": journeys}
    if bindings.get("canonicalInputDigest") != sha256_bytes(canonical(canonical_input)):
        errors.append("canonical input binding mismatch")
    commands = bindings.get("commands")
    if commands != COMMANDS or bindings.get("commandDigest") != sha256_bytes(canonical(commands)):
        errors.append("command binding mismatch")
    environment = bindings.get("environment")
    if environment != execution_environment() or bindings.get("environmentDigest") != sha256_bytes(canonical(environment)):
        errors.append("environment binding mismatch")
    unit = bindings.get("unit", {})
    if set(unit) != {"suiteClass", "methods", "passed", "resultPath", "resultDigest"}:
        errors.append("unit binding fields invalid")
    unit_path = root / str(unit.get("resultPath", ""))
    try:
        exact_suite(unit_path, UNIT_CLASS, UNIT_METHODS)
    except (OSError, ET.ParseError, ValueError) as error:
        errors.append(f"unit suite identity mismatch: {error}")
    if unit.get("resultDigest") != (sha256_bytes(unit_path.read_bytes()) if unit_path.is_file() else None):
        errors.append("unit result binding mismatch")
    if unit.get("suiteClass") != UNIT_CLASS or set(unit.get("methods", [])) != UNIT_METHODS or unit.get("passed") != 3:
        errors.append("unit execution identity mismatch")

    managed = bindings.get("managed", {})
    if set(managed) != {
        "suiteClass", "methods", "passed", "device", "flavor", "project", "resultPath", "resultDigest",
        "targetArtifact", "testArtifact",
    }:
        errors.append("managed binding fields invalid")
    managed_path = root / str(managed.get("resultPath", ""))
    managed_properties = {"device": "pixel6Api35", "flavor": "playInternal", "project": ":app"}
    try:
        exact_suite(managed_path, MANAGED_CLASS, MANAGED_METHODS, managed_properties)
    except (OSError, ET.ParseError, ValueError) as error:
        errors.append(f"managed suite identity mismatch: {error}")
    if managed.get("resultDigest") != (sha256_bytes(managed_path.read_bytes()) if managed_path.is_file() else None):
        errors.append("managed result binding mismatch")
    if (
        managed.get("suiteClass") != MANAGED_CLASS or set(managed.get("methods", [])) != MANAGED_METHODS or
        managed.get("passed") != len(MANAGED_METHODS) or managed.get("device") != "pixel6Api35" or
        managed.get("flavor") != "playInternalRelease" or managed.get("project") != ":app"
    ):
        errors.append("managed execution identity mismatch")
    try:
        validate_binding(root, bindings.get("managedExecution", {}), source_paths=tuple(SOURCE_FILES),
                         class_name=MANAGED_CLASS, methods=MANAGED_METHODS)
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        errors.append(f"stable managed binding mismatch: {error}")
    companion = bindings.get("managedCompanionPrivacy", {})
    expected_companion = sanitized_companion(MANAGED_CLASS, len(MANAGED_METHODS))
    companion_path = root / str(companion.get("path", ""))
    if (
        set(companion) != {"kind", "sanitized", "path", "sha256"} or
        companion.get("kind") != "SANITIZED_METADATA_ONLY" or companion.get("sanitized") is not True or
        not companion_path.is_file() or companion_path.read_bytes() != expected_companion or
        companion.get("sha256") != sha256_bytes(expected_companion)
    ):
        errors.append("managed companion privacy binding mismatch")
    for name, expected_id in (("targetArtifact", "app.codecks.internal"), ("testArtifact", "app.codecks.internal.test")):
        artifact = managed.get(name, {})
        if set(artifact) != {"path", "applicationId", "digest"}:
            errors.append(f"{name} binding fields invalid")
        artifact_path = root / str(artifact.get("path", ""))
        if artifact.get("applicationId") != expected_id:
            errors.append(f"{name} application ID mismatch")
        if not artifact_path.is_file() or artifact.get("digest") != (
            sha256_bytes(artifact_path.read_bytes()) if artifact_path.is_file() else None
        ):
            errors.append(f"{name} digest mismatch")
    base = bindings.get("baseCommit")
    if not isinstance(base, str) or len(base) != 40:
        errors.append("invalid base commit binding")
    else:
        ancestry = subprocess.run(
            ["git", "merge-base", "--is-ancestor", base, "HEAD"],
            cwd=root,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ).returncode
        if ancestry != 0:
            errors.append("base commit is not an ancestor of HEAD")
    if data.get("receiptDigest") != receipt_digest(data):
        errors.append("receipt digest mismatch")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    errors = validate(args.path)
    if errors:
        print("\n".join(errors))
        return 1
    print("M14 receipt PASS: 100 distinct event-driven journeys; bindings valid; human pairing NOT_RUN")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
