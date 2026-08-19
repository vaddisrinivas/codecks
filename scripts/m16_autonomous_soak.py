#!/usr/bin/env python3
"""Fail-closed host controller for the M16 AUTONOMOUS_PROXY harness.

This script never creates evidence events or acknowledgements; those originate in
the five app processes on each of four reviewed API-35 emulators.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import secrets

PACKAGE = "app.codecks.internal"
PROTECTED_PACKAGE = "app.codecks"
AVDS = tuple(f"m16Soak0{i}Api35" for i in range(1, 5))
SERVICES = tuple(f"io.codecks.internalquality.m16.M16ProfileService0{i}" for i in range(1, 6))
LOCK = Path("/opt/codex-auth/locks/codecks-m16-soak.lock")
MIN_PREPROVISION_GIB = 64
MIN_POSTPROVISION_GIB = 50
RUNTIME_STOP_GIB = 40
PROJECTED_FOOTPRINT_GIB = 24
HOST_LEDGER_CAP = 32 * 1024 * 1024
ARTIFACT_CAP = 32 * 1024 * 1024
FAILURE_ARTIFACT_CAP = 4 * 1024 * 1024
BURNIN_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000
BURNIN_RECEIPT_NAME = "burnin2h-receipt.json"
BURNIN_STATE_NAME = "burnin2h-state.json"
SERIAL = re.compile(r"emulator-[0-9]{4,5}")
ADB_SERVER_PORT = 5039
DEFAULT_ADB_SERVER_PORT = 5037
EMULATOR_PORTS = (5580, 5582, 5584, 5586)
M16_SERIALS = frozenset(f"emulator-{port}" for port in EMULATOR_PORTS)
AUTHORIZED_DEFAULT_AVDS = frozenset(("Utopia_GL_1", "Utopia_GL_2"))
AUTHORIZED_DEFAULT_SERIALS = {"Utopia_GL_1": "emulator-5554", "Utopia_GL_2": "emulator-5556"}
SYSTEM_IMAGE = "system-images;android-35;default;arm64-v8a"
PRIVACY = re.compile(rb"(?i)(/Users/[^\s]+|/home/[^\s]+|Bearer\s+[^\s]+|password=[^\s]+|token=[^\s]+|BEGIN [A-Z ]*PRIVATE KEY)")
CATEGORIES = frozenset(("deck", "trackpad", "keyboard", "clipboard", "rules", "ssh_failure", "ssh_recovery", "lifecycle"))
EVENT_TYPES = frozenset(("admitted", "ack", "window_complete", "window_ineligible", "admitted_incomplete",
                         "profile_complete", "lifecycle_process_death_scheduled", "resumed"))
COMMON_EVENT_KEYS = frozenset(("type", "elapsedRealtimeMillis", "wallTimeMillis", "profileId", "processName",
                               "pid", "originNonce", "previousHash", "eventHash"))
EVENT_KEYS = {
    "admitted": frozenset(("bootId", "windowIndex", "eligibleTarget", "profileRoot", "seed")),
    "ack": frozenset(("windowIndex", "sequence", "ackId", "category", "operationLatencyMillis", "totalPssKb",
                       "batteryPercentProxy", "applicationExitReasons", "crashOrAnr")),
    "window_complete": frozenset(("windowIndex", "sequence", "elapsedMillis", "activeMillis", "categories", "eligible")),
    "window_ineligible": frozenset(("windowIndex", "reasonCode")),
    "admitted_incomplete": frozenset(("windowIndex", "sequence", "reasonCode")),
    "profile_complete": frozenset(("attemptedWindows", "eligibleWindows", "acknowledgedOperations")),
    "lifecycle_process_death_scheduled": frozenset(("attemptedWindows", "eligibleWindows")),
    "resumed": frozenset(("windowIndex", "sequence", "priorPid", "newPid")),
}
ISOLATION_CLASS = "io.codecks.internalquality.m16.M16ProfileIsolationInstrumentedTest"
ISOLATION_METHODS = frozenset(("exactTwentyImmutableIdentitiesAreDisjoint", "profileContextsCannotReadEachOthersStores",
                               "manifestCarriesFiveExactNamedProcesses", "fiveLiveServicesOwnFivePidsLocksAndRealRepositoryStores"))
HOST_COMMON_KEYS = frozenset(("schema", "previousHash", "hostWallMillis", "hostMonotonicNanos", "type"))
HOST_EVENT_KEYS = {"controller_start": frozenset(("mode", "profiles")), "twenty_workers_admitted": frozenset(("workers",)),
                   "monitor": frozenset(("workers", "complete", "qemuRssKiB", "freeGiB", "health")),
                   "scheduled_restart_gap": frozenset(("profileId",)),
                   "unexpected_worker_missing": frozenset(("profileId", "failurePacket", "oldPid", "lastAckId")),
                   "worker_restarted": frozenset(("profileId", "oldPid", "newPid", "freshAckId", "repoProbePath", "repoProbeSha256")),
                   "worker_crash_or_anr": frozenset(("profileId", "failurePacket")), "controller_stop": frozenset()}


class SafetyStop(RuntimeError):
    pass


def adb_command(port: int, *args: str) -> list[str]:
    if port not in {ADB_SERVER_PORT, DEFAULT_ADB_SERVER_PORT}:
        raise SafetyStop("adb_server_port")
    return ["adb", "-P", str(port), *args]


def listener_pids(port: int) -> set[int]:
    result=subprocess.run(["lsof","-nP",f"-iTCP:{port}","-sTCP:LISTEN","-t"],text=True,capture_output=True,check=False)
    return {int(item) for item in result.stdout.split() if item.isdigit()}


def process_command(pid: int) -> str:
    return subprocess.run(["ps","-p",str(pid),"-o","command="],text=True,capture_output=True,check=False).stdout.strip()


def fsync_dir(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def acquire_owner(run_dir: Path, path: Path = LOCK) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as error:
        raise SafetyStop("m16_singleton_exists") from error
    try:
        os.write(descriptor, json.dumps({"runDir": str(run_dir), "createdWallMillis": int(time.time() * 1000)}).encode())
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    fsync_dir(path.parent)


def require_owner(run_dir: Path, path: Path = LOCK) -> None:
    try:
        owner = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise SafetyStop("m16_owner_missing_or_invalid") from error
    if owner != {"runDir": str(run_dir), "createdWallMillis": owner.get("createdWallMillis")}:
        raise SafetyStop("m16_owner_mismatch")
    if not isinstance(owner["createdWallMillis"], int):
        raise SafetyStop("m16_owner_invalid")


def release_owner(run_dir: Path, path: Path = LOCK) -> None:
    require_owner(run_dir, path)
    path.unlink()
    fsync_dir(path.parent)


def disk_free_gib(path: Path) -> float:
    return shutil.disk_usage(path).free / 1024**3


def adb(serial: str, *args: str, timeout: int = 20) -> str:
    if serial not in M16_SERIALS:
        raise SafetyStop("non_m16_serial")
    result = subprocess.run(
        adb_command(ADB_SERVER_PORT, "-s", serial, *args), text=True, capture_output=True, timeout=timeout, check=False,
    )
    if result.returncode:
        raise SafetyStop(f"adb_failed:{args[0] if args else 'unknown'}")
    return result.stdout.strip()


def adb_result(serial: str, *args: str, timeout: int = 20) -> subprocess.CompletedProcess[str]:
    if serial not in M16_SERIALS:
        raise SafetyStop("non_m16_serial")
    return subprocess.run(adb_command(ADB_SERVER_PORT, "-s", serial, *args), text=True, capture_output=True, timeout=timeout, check=False)


def adb_bytes(serial: str, *args: str, timeout: int = 60) -> bytes:
    if serial not in M16_SERIALS:
        raise SafetyStop("non_m16_serial")
    result = subprocess.run(adb_command(ADB_SERVER_PORT, "-s", serial, *args), capture_output=True, timeout=timeout, check=False)
    if result.returncode:
        raise SafetyStop(f"adb_binary_failed:{args[0] if args else 'unknown'}")
    if len(result.stdout) > ARTIFACT_CAP:
        raise SafetyStop("pulled_artifact_cap")
    return result.stdout


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    if not path.is_file():
        raise SafetyStop(f"artifact_missing:{path.name}")
    return sha256_bytes(path.read_bytes())


def canonical_sha256(value: object) -> str:
    return sha256_bytes(json.dumps(value, sort_keys=True, separators=(",", ":")).encode())


def burnin_not_required() -> dict[str, object]:
    return {
        "status": "NOT_REQUIRED", "receiptPath": "", "receiptSha256": "0" * 64,
        "statePath": "", "stateSha256": "0" * 64, "finishedWallMillis": 0,
        "validatedWallMillis": 0, "maxBurninAgeHours": 24,
        "bindingSha256": "0" * 64, "topologySha256": "0" * 64,
        "burninRunDirLexical": "", "burninRunDirCanonical": "",
        "soakRunDirLexical": "", "soakRunDirCanonical": "",
        "pathIdentitySha256": "0" * 64,
        "externalValidator": "NOT_REQUIRED",
    }


def safe_absolute_directory(value: str, code: str, must_exist: bool) -> Path:
    candidate = Path(value)
    if not candidate.is_absolute():
        raise SafetyStop(f"{code}_not_absolute")
    lexical = Path(os.path.abspath(candidate))
    for existing in (lexical, *lexical.parents):
        if existing.exists() and existing.is_symlink():
            raise SafetyStop(f"{code}_symlink")
    if must_exist and (not lexical.is_dir() or lexical.resolve() != lexical):
        raise SafetyStop(f"{code}_missing_or_noncanonical")
    if not must_exist and lexical.exists() and (not lexical.is_dir() or lexical.resolve() != lexical):
        raise SafetyStop(f"{code}_noncanonical")
    return lexical


def resolve_phase_directories(run_dir_value: str, burnin_run_dir_value: str | None,
                              mode: str, require_run: bool) -> tuple[Path, Path | None]:
    run_dir = safe_absolute_directory(run_dir_value, "run_dir", require_run)
    if mode == "burnin2h":
        if burnin_run_dir_value:
            raise SafetyStop("burnin_mode_rejects_burnin_run_dir")
        return run_dir, None
    if mode != "soak168h" or not burnin_run_dir_value:
        raise SafetyStop("soak_requires_burnin_run_dir")
    burnin_dir = safe_absolute_directory(burnin_run_dir_value, "burnin_run_dir", True)
    if (run_dir == burnin_dir or run_dir.is_relative_to(burnin_dir)
            or burnin_dir.is_relative_to(run_dir)):
        raise SafetyStop("phase_directory_relationship")
    return run_dir, burnin_dir


def phase_path_identity(run_dir: Path, burnin_dir: Path) -> dict[str, str]:
    value = {
        "burninRunDirLexical": str(burnin_dir), "burninRunDirCanonical": str(burnin_dir.resolve()),
        "soakRunDirLexical": str(run_dir), "soakRunDirCanonical": str(run_dir.resolve()),
    }
    return {**value, "pathIdentitySha256": canonical_sha256(value)}


def phase_manifest_value(run_dir: Path, burnin_dir: Path) -> dict[str, object]:
    return {"schema": "codecks.m16.phase-directory.v1", "mode": "soak168h",
            **phase_path_identity(run_dir, burnin_dir)}


def require_phase_manifest(run_dir: Path, burnin_dir: Path) -> None:
    path = run_dir / "phase.json"
    if path.is_symlink() or not path.is_file() or path.resolve().parent != run_dir:
        raise SafetyStop("soak_phase_manifest_missing")
    try:
        value = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise SafetyStop("soak_phase_manifest_invalid") from error
    if value != phase_manifest_value(run_dir, burnin_dir):
        raise SafetyStop("soak_phase_manifest_mismatch")


def require_fresh_soak_evidence(run_dir: Path) -> None:
    forbidden = ("host-ledger.jsonl", "host-ledger.head", "receipt.json", BURNIN_RECEIPT_NAME,
                 BURNIN_STATE_NAME, "profiles", "failures", "restart-probes")
    if any((run_dir / name).exists() for name in forbidden):
        raise SafetyStop("soak_evidence_directory_not_fresh")


def require_prelaunch_soak_directory(run_dir: Path) -> None:
    if {item.name for item in run_dir.iterdir()} != {"phase.json", "provision.json", "avd-home"}:
        raise SafetyStop("soak_run_directory_not_new")


def apk_signer(path: Path) -> str:
    build_tools = Path(os.environ.get("ANDROID_HOME", "")) / "build-tools"
    candidates = [item for item in build_tools.glob("*/apksigner") if item.is_file()]
    if not candidates:
        raise SafetyStop("apksigner_missing")
    tool = max(candidates, key=lambda item: tuple(int(x) if x.isdigit() else 0 for x in item.parent.name.split(".")))
    result = subprocess.run([str(tool), "verify", "--print-certs", str(path)], text=True, capture_output=True, check=False)
    digest = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})", result.stdout)
    if result.returncode or not digest:
        raise SafetyStop("apk_signature_invalid")
    return digest.group(1).lower()


def verify_isolation_xml(path: Path) -> dict[str, object]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        raise SafetyStop("isolation_xml_invalid") from error
    suites = [root] if root.tag == "testsuite" else list(root.findall(".//testsuite"))
    cases = [case for suite in suites for case in suite.findall("testcase") if case.get("classname") == ISOLATION_CLASS]
    names = [case.get("name") for case in cases]
    if set(names) != ISOLATION_METHODS or len(names) != len(ISOLATION_METHODS):
        raise SafetyStop("isolation_xml_exact_methods")
    if any(case.find("failure") is not None or case.find("error") is not None or case.find("skipped") is not None for case in cases):
        raise SafetyStop("isolation_xml_failure")
    total_failures = sum(int(suite.get("failures", "0")) + int(suite.get("errors", "0")) for suite in suites)
    if total_failures or any(int(suite.get("tests", "0")) < len(ISOLATION_METHODS) for suite in suites if any(c in cases for c in suite.findall("testcase"))):
        raise SafetyStop("isolation_xml_counts")
    output = "\n".join((node.text or "") for node in root.findall(".//system-out"))
    match = re.search(r"M16_BINDING package=(\S+) flavor=(\S+) project=(\S+) api=(\d+) fingerprintSha256=([0-9a-f]{64})", output)
    if not match or match.groups()[:4] != (PACKAGE, "playInternal", ":app", "35"):
        raise SafetyStop("isolation_xml_binding")
    return {"class": ISOLATION_CLASS, "methods": sorted(ISOLATION_METHODS), "package": PACKAGE,
            "flavor": "playInternal", "project": ":app", "api": 35, "fingerprintSha256": match.group(5)}


def git_output(repo: Path, *args: str) -> str:
    result = subprocess.run(["git", *args], cwd=repo, text=True, capture_output=True, check=False)
    if result.returncode:
        raise SafetyStop("git_binding_failed")
    return result.stdout.strip()


def global_adb_guard(devices: dict[str, str], default_audit: dict[str, object]) -> None:
    result = subprocess.run(adb_command(ADB_SERVER_PORT, "devices"), text=True, capture_output=True, check=False)
    if result.returncode:
        raise SafetyStop("adb_devices_failed")
    rows = [line.split()[:2] for line in result.stdout.splitlines()[1:] if len(line.split()) >= 2]
    expected_m16 = set(devices.values())
    if expected_m16 != M16_SERIALS:
        raise SafetyStop("m16_serial_mapping")
    entries=default_audit.get("authorizedAvds", [])
    if not isinstance(entries,list) or any(not isinstance(item,dict) or item.get("serial")!=AUTHORIZED_DEFAULT_SERIALS.get(str(item.get("avd"))) for item in entries):
        raise SafetyStop("default_adb_audit_binding")
    allowed_extras = {str(item["serial"]) for item in entries}
    actual={row[0] for row in rows}
    if not expected_m16.issubset(actual) or not (actual-expected_m16).issubset(allowed_extras) or any(row[1] != "device" for row in rows):
        raise SafetyStop("isolated_adb_unexpected_device")


def audit_default_adb() -> dict[str, object]:
    listeners=listener_pids(DEFAULT_ADB_SERVER_PORT)
    if not listeners: return {"status":"absent","sanitizedNonM16EmulatorCount":0,"authorizedAvds":[],"serverPid":0,"serverCmdlineSha256":"0"*64}
    if len(listeners)!=1: raise SafetyStop("default_adb_listener_ambiguous")
    server_pid=next(iter(listeners)); server_command=process_command(server_pid)
    if "adb" not in server_command: raise SafetyStop("default_adb_listener_unknown")
    result = subprocess.run(adb_command(DEFAULT_ADB_SERVER_PORT, "devices"), text=True, capture_output=True, check=False)
    if result.returncode: raise SafetyStop("default_adb_audit_failed")
    rows=[line.split()[:2] for line in result.stdout.splitlines()[1:] if len(line.split())>=2]
    if any(state!="device" or not SERIAL.fullmatch(serial) for serial,state in rows): raise SafetyStop("default_adb_unknown_or_offline")
    captured=[]
    for serial,_ in rows:
        probe=subprocess.run(adb_command(DEFAULT_ADB_SERVER_PORT,"-s",serial,"shell","getprop","ro.kernel.qemu"),text=True,capture_output=True,check=False)
        if probe.returncode or probe.stdout.strip()!="1": raise SafetyStop("default_adb_non_qemu")
        name_result=subprocess.run(adb_command(DEFAULT_ADB_SERVER_PORT,"-s",serial,"emu","avd","name"),text=True,capture_output=True,check=False)
        name=name_result.stdout.splitlines()[0].strip() if name_result.returncode==0 and name_result.stdout else ""
        if name not in AUTHORIZED_DEFAULT_AVDS: raise SafetyStop("default_adb_unauthorized_avd")
        if serial != AUTHORIZED_DEFAULT_SERIALS[name]: raise SafetyStop("default_adb_avd_serial_mismatch")
        matches=[]
        for line in subprocess.run(["ps","ax","-o","pid=,command="],text=True,capture_output=True,check=True).stdout.splitlines():
            if re.search(rf"(?:-avd\s+){re.escape(name)}(?:\s|$)",line): matches.append(line.strip().split(maxsplit=1))
        if len(matches)!=1: raise SafetyStop("default_avd_host_binding")
        host_pid=int(matches[0][0]); command=matches[0][1]
        if f"-port {serial.removeprefix('emulator-')}" not in command: raise SafetyStop("default_avd_serial_binding")
        captured.append({"avd":name,"serial":serial,"hostPid":host_pid,"cmdlineSha256":sha256_bytes(command.encode())})
    if listener_pids(DEFAULT_ADB_SERVER_PORT)!={server_pid}: raise SafetyStop("default_adb_raced")
    return {"status":"present","sanitizedNonM16EmulatorCount":len(rows),"authorizedAvds":sorted(captured,key=lambda item:item["avd"]),
            "serverPid":server_pid,"serverCmdlineSha256":sha256_bytes(server_command.encode())}


def adb_server_binding() -> dict[str, object]:
    pids=listener_pids(ADB_SERVER_PORT)
    if len(pids)!=1: raise SafetyStop("isolated_adb_server_binding")
    pid=pids.pop(); command=process_command(pid)
    if "adb" not in command or str(ADB_SERVER_PORT) not in command: raise SafetyStop("isolated_adb_server_cmdline")
    return {"port":ADB_SERVER_PORT,"endpoint":"tcp:127.0.0.1:5039","serverPid":pid,"cmdlineSha256":sha256_bytes(command.encode())}


def avd_home_for(run_dir: Path) -> Path:
    lexical = Path(os.path.abspath(run_dir)) / "avd-home"
    for existing in (lexical, *lexical.parents):
        if existing.exists() and existing.is_symlink():
            raise SafetyStop("managed_avd_symlink_parent")
    canonical_parent = lexical.parent.resolve()
    if canonical_parent != lexical.parent or lexical.exists() and lexical.resolve() != lexical:
        raise SafetyStop("managed_avd_symlink_escape")
    return lexical


def managed_avd_config(avd_home: Path, avd: str) -> Path:
    lexical_root = Path(os.path.abspath(avd_home))
    for existing in (lexical_root, *lexical_root.parents):
        if existing.exists() and existing.is_symlink():
            raise SafetyStop("managed_avd_symlink_parent")
    root = lexical_root.resolve()
    if avd not in AVDS or root.name != "avd-home" or root != lexical_root:
        raise SafetyStop("managed_avd_home_mismatch")
    ini = root / f"{avd}.ini"
    config_path = root / f"{avd}.avd" / "config.ini"
    if any(path.is_symlink() for path in (ini, config_path.parent, config_path)) or not ini.is_file() or not config_path.is_file():
        raise SafetyStop("managed_avd_config_mismatch")
    ini_values = dict(line.split("=", 1) for line in ini.read_text().splitlines() if "=" in line)
    if Path(ini_values.get("path", "")).resolve() != config_path.parent.resolve() or ini_values.get("target") != "android-35":
        raise SafetyStop("managed_avd_metadata_mismatch")
    config = config_path.read_text()
    if "system-images/android-35/default/arm64-v8a/" not in config.replace("\\", "/"):
        raise SafetyStop("managed_avd_not_aosp_api35_arm64")
    return config_path


def provision_binding(run_dir: Path) -> dict[str, object]:
    path = run_dir / "provision.json"
    try:
        binding = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise SafetyStop("m16_provision_binding_missing") from error
    if set(binding) != {"schema", "avdHome", "systemImage", "avds"} or binding["schema"] != "codecks.m16.avd-provision.v1":
        raise SafetyStop("m16_provision_binding_shape")
    avd_home = avd_home_for(run_dir)
    if binding["avdHome"] != str(avd_home) or binding["systemImage"] != SYSTEM_IMAGE:
        raise SafetyStop("m16_provision_binding_root")
    entries = binding["avds"]
    if not isinstance(entries, list) or any(not isinstance(item,dict) for item in entries) or [item.get("name") for item in entries] != list(AVDS):
        raise SafetyStop("m16_provision_exact_four")
    directories = set()
    for item, port in zip(entries, EMULATOR_PORTS):
        if set(item) != {"name", "port", "configPath", "configSha256"} or item["port"] != port:
            raise SafetyStop("m16_provision_entry_shape")
        config = managed_avd_config(avd_home, str(item["name"]))
        if item["configPath"] != str(config) or item["configSha256"] != sha256_file(config):
            raise SafetyStop("m16_provision_config_binding")
        directories.add(config.parent.resolve())
    if len(directories) != 4:
        raise SafetyStop("m16_provision_shared_store")
    return binding


def receipt_provision_binding(run_dir: Path, state: dict[str, object]) -> dict[str, object]:
    provision = provision_binding(run_dir)
    manifest = run_dir / "provision.json"
    avd_home_lexical = Path(str(provision["avdHome"]))
    avd_home_canonical = avd_home_lexical.resolve()
    token = str(state.get("runToken", ""))
    if not re.fullmatch(r"[0-9a-f]{32}", token):
        raise SafetyStop("receipt_provision_token")
    entries=[]
    for item in provision["avds"]:
        entries.append({**item,"runIdentity":token})
    return {"avdHomeLexical":str(avd_home_lexical),"avdHomeCanonical":str(avd_home_canonical),
            "manifestPath":"provision.json","manifestSha256":sha256_file(manifest),
            "systemImage":SYSTEM_IMAGE,"avds":entries}


def identity_log_binding(run_dir: Path, avd: str, run_token: str, candidate: Path | None = None) -> dict[str, object]:
    if avd not in AVDS or not re.fullmatch(r"[0-9a-f]{32}",run_token): raise SafetyStop("identity_log_arguments")
    lexical_run=Path(os.path.abspath(run_dir)); expected=lexical_run/f"emulator-{avd}-{run_token}.log"
    actual=Path(os.path.abspath(candidate or expected))
    if actual!=expected or not actual.is_file() or actual.is_symlink(): raise SafetyStop("identity_log_path")
    for existing in (actual.parent,*actual.parents):
        if existing.exists() and existing.is_symlink(): raise SafetyStop("identity_log_symlink_parent")
    canonical=actual.resolve()
    if canonical!=actual or not canonical.is_relative_to(lexical_run.resolve()): raise SafetyStop("identity_log_containment")
    metadata=actual.stat()
    if metadata.st_uid!=os.getuid() or not stat.S_ISREG(metadata.st_mode): raise SafetyStop("identity_log_owner")
    return {"identityLogPath":str(actual),"identityLogCanonical":str(canonical),"identityLogOwnerUid":metadata.st_uid}


def qemu_host_binding(avd_home: Path, avd: str, run_token: str | None = None, expected_pid: int | None = None) -> dict[str, object]:
    config_path = managed_avd_config(avd_home, avd)
    output = subprocess.run(["ps", "ax", "-o", "pid=,rss=,command="], text=True, capture_output=True, check=True).stdout
    matches = []
    for line in output.splitlines():
        if ("qemu-system" in line or "/emulator" in line) and re.search(rf"(?:-avd\s+|/){re.escape(avd)}(?:\s|$)", line):
            parts = line.strip().split(maxsplit=2)
            if len(parts) == 3:
                matches.append((int(parts[0]), int(parts[1]), parts[2]))
    if len(matches) != 1:
        raise SafetyStop("qemu_host_process_mismatch")
    pid, rss_kib, command = matches[0]
    if expected_pid is not None and pid != expected_pid: raise SafetyStop("qemu_pid_binding")
    identity={}
    if run_token is not None:
        identity=identity_log_binding(avd_home.parent,avd,run_token)
        if f"-logcat-output {identity['identityLogPath']}" not in command: raise SafetyStop("qemu_run_identity_binding")
    expected_port = EMULATOR_PORTS[AVDS.index(avd)]
    if f"-port {expected_port}" not in command: raise SafetyStop("qemu_port_binding")
    if "-wipe-data" in command: raise SafetyStop("qemu_wipe_forbidden")
    if "-no-window" not in command and "qemu-system" not in command and "/emulator" not in command:
        raise SafetyStop("qemu_cmdline_invalid")
    return {"pid": pid, "rssKiB": rss_kib, "cmdlineSha256": sha256_bytes(command.encode()),
            "configPath": str(config_path), "configSha256": sha256_file(config_path),**identity}


def verify_device(avd: str, serial: str, target_sha256: str | None = None, test_sha256: str | None = None,
                  run_token: str | None = None, expected_pid: int | None = None, avd_home: Path | None = None) -> dict[str, object]:
    if avd not in AVDS:
        raise SafetyStop("unexpected_avd_id")
    if adb(serial, "shell", "getprop", "ro.kernel.qemu") != "1":
        raise SafetyStop("device_not_qemu")
    if adb(serial, "emu", "avd", "name").splitlines()[0].strip() != avd:
        raise SafetyStop("avd_name_mismatch")
    sdk = adb(serial, "shell", "getprop", "ro.build.version.sdk")
    if sdk != "35":
        raise SafetyStop("api_not_35")
    package_path = adb(serial, "shell", "pm", "path", PACKAGE)
    if not package_path.startswith("package:"):
        raise SafetyStop("internal_package_missing")
    fingerprint = adb(serial, "shell", "getprop", "ro.build.fingerprint")
    if not any(marker in fingerprint.lower() for marker in ("aosp", "generic", "sdk_gphone")) or not fingerprint.endswith("dev-keys"):
        raise SafetyStop("not_aosp_fingerprint")
    protected = adb_result(serial, "shell", "pm", "path", PROTECTED_PACKAGE)
    if protected.returncode == 0 and protected.stdout.strip().startswith("package:"):
        raise SafetyStop("protected_package_present")
    if "disabled" not in adb(serial, "shell", "cmd", "wifi", "status").lower():
        raise SafetyStop("wifi_not_disabled")
    if adb(serial, "shell", "settings", "get", "global", "mobile_data") not in {"0", "null"}:
        raise SafetyStop("mobile_data_not_disabled")
    routes = adb(serial, "shell", "ip", "route", "show", "default")
    connectivity = adb(serial, "shell", "dumpsys", "connectivity")
    if routes.strip() or re.search(r"(?m)^\s*Active default network:", connectivity) and "none" not in connectivity.lower():
        raise SafetyStop("default_network_present")
    proc_routes = adb(serial, "shell", "cat", "/proc/net/route")
    if any(line.split()[1] == "00000000" for line in proc_routes.splitlines()[1:] if len(line.split()) > 1):
        raise SafetyStop("kernel_default_route_present")
    app_path = package_path.splitlines()[0].removeprefix("package:")
    installed_sha = adb(serial, "shell", "sha256sum", app_path).split()[0]
    if target_sha256 is not None and installed_sha != target_sha256:
        raise SafetyStop("installed_target_apk_mismatch")
    test_path_result = adb_result(serial, "shell", "pm", "path", f"{PACKAGE}.test")
    if test_sha256 is not None:
        if test_path_result.returncode:
            raise SafetyStop("test_package_missing")
        installed_test_sha = adb(serial, "shell", "sha256sum", test_path_result.stdout.strip().splitlines()[0].removeprefix("package:")).split()[0]
        if installed_test_sha != test_sha256:
            raise SafetyStop("installed_test_apk_mismatch")
    package_dump = adb(serial, "shell", "dumpsys", "package", PACKAGE)
    uid_match = re.search(r"userId=(\d+)", package_dump)
    data_match = re.search(r"dataDir=([^\s]+)", package_dump)
    if not uid_match or not data_match or data_match.group(1) != f"/data/user/0/{PACKAGE}":
        raise SafetyStop("package_uid_datadir_invalid")
    device_wall = int(adb(serial, "shell", "date", "+%s")) * 1000
    device_uptime = int(float(adb(serial, "shell", "cat", "/proc/uptime").split()[0]) * 1000)
    clock_delta = abs(int(time.time() * 1000) - device_wall)
    if clock_delta > 10_000:
        raise SafetyStop("device_wall_clock_jump")
    return {
        "avd": avd, "serial": serial, "api": 35, "fingerprintSha256": sha256_bytes(fingerprint.encode()),
        "uid": int(uid_match.group(1)), "dataDir": data_match.group(1), "targetApkSha256": installed_sha,
        "qemu": qemu_host_binding(avd_home or Path("/invalid"),avd,run_token,expected_pid), "observedWallMillis": device_wall, "observedUptimeMillis": device_uptime,
    }


def component(service: str) -> str:
    return f"{PACKAGE}/{service}"


def service_command(serial: str, avd: str, service: str, action: str, duration_hours: int) -> None:
    if PACKAGE == PROTECTED_PACKAGE or not service in SERVICES:
        raise SafetyStop("protected_or_unknown_component")
    adb(
        serial, "shell", "am", "start-foreground-service", "-n", component(service),
        "-a", f"app.codecks.internal.m16.{action}", "--es", "avd_id", avd,
        "--ei", "duration_hours", str(duration_hours),
    )


def parse_devices(values: list[str]) -> dict[str, str]:
    devices: dict[str, str] = {}
    for value in values:
        avd, separator, serial = value.partition("=")
        if not separator or avd in devices or avd not in AVDS or not SERIAL.fullmatch(serial):
            raise SafetyStop("invalid_device_mapping")
        if serial != f"emulator-{EMULATOR_PORTS[AVDS.index(avd)]}":
            raise SafetyStop("m16_avd_serial_substitution")
        devices[avd] = serial
    if tuple(sorted(devices)) != AVDS or len(set(devices.values())) != 4:
        raise SafetyStop("exactly_four_distinct_avds_required")
    return devices


def append_host_event(run_dir: Path, event: dict[str, object]) -> None:
    ledger = run_dir / "host-ledger.jsonl"
    head = run_dir / "host-ledger.head"
    previous = head.read_text().strip() if head.exists() else "0" * 64
    if not re.fullmatch(r"[0-9a-f]{64}", previous):
        raise SafetyStop("host_ledger_head_invalid")
    closed = {"schema": "codecks.m16.host-event.v1", "previousHash": previous,
              "hostWallMillis": int(time.time() * 1000), "hostMonotonicNanos": time.monotonic_ns(), **event}
    event_hash = sha256_bytes(json.dumps(closed, sort_keys=True, separators=(",", ":")).encode())
    closed["eventHash"] = event_hash
    encoded = (json.dumps(closed, sort_keys=True, separators=(",", ":")) + "\n").encode()
    if ledger.exists() and ledger.stat().st_size + len(encoded) > HOST_LEDGER_CAP:
        raise SafetyStop("host_ledger_cap")
    with ledger.open("ab", buffering=0) as output:
        output.write(encoded)
        os.fsync(output.fileno())
    atomic_bytes(head, event_hash.encode(), 64)


def verify_host_ledger(data: bytes, started_wall: int, finished_wall: int) -> dict[str, int]:
    previous = "0" * 64
    monitors = 0
    unexpected = 0
    crash_events = 0
    failure_packets: set[str] = set()
    pending_restarts: dict[str, tuple[int, str]] = {}
    recoveries: list[dict[str, object]] = []
    prior_wall = started_wall
    prior_mono = None
    first_mono = None
    last_mono = None
    for raw in data.splitlines():
        event = json.loads(raw)
        recorded = event.pop("eventHash", None)
        if event.get("schema") != "codecks.m16.host-event.v1" or event.get("previousHash") != previous:
            raise SafetyStop("host_ledger_chain")
        if event.get("type") not in HOST_EVENT_KEYS or set(event) != HOST_COMMON_KEYS | HOST_EVENT_KEYS[event["type"]] or sha256_bytes(json.dumps(event, sort_keys=True, separators=(",", ":")).encode()) != recorded:
            raise SafetyStop("host_ledger_event")
        if event["type"] == "monitor" and (not isinstance(event.get("health"), dict) or
                set(event["health"]) != {"swapUsedMiB", "memoryFreePercent", "availableGiB", "load1", "thermal"}):
            raise SafetyStop("host_monitor_not_closed")
        wall, mono = event.get("hostWallMillis"), event.get("hostMonotonicNanos")
        if not isinstance(wall, int) or not isinstance(mono, int) or wall < prior_wall:
            raise SafetyStop("host_clock_rollback")
        if prior_mono is not None:
            mono_delta = (mono - prior_mono) / 1_000_000
            wall_delta = wall - prior_wall
            if mono_delta < 0 or abs(wall_delta - mono_delta) > 5_000:
                raise SafetyStop("host_wall_monotonic_jump")
        first_mono = mono if first_mono is None else first_mono
        last_mono = mono
        prior_wall, prior_mono, previous = wall, mono, recorded
        if event["type"] == "monitor": monitors += 1
        if event["type"] == "unexpected_worker_missing":
            unexpected += 1
            profile = event["profileId"]
            if not re.fullmatch(r"avd0[1-4]-p0[1-5]", profile) or profile in pending_restarts or not isinstance(event["oldPid"], int) or event["oldPid"] <= 0 or not isinstance(event["lastAckId"], str):
                raise SafetyStop("host_unexpected_missing_binding")
            pending_restarts[profile] = (event["oldPid"], event["lastAckId"])
        if event["type"] == "worker_restarted":
            pending = pending_restarts.pop(event["profileId"], None)
            if (pending is None or event["oldPid"] != pending[0] or event["newPid"] == event["oldPid"]
                    or event["freshAckId"] == pending[1] or not re.fullmatch(r"[0-9a-f]{64}", event.get("repoProbeSha256", ""))):
                raise SafetyStop("host_restart_not_fresh")
            if not re.fullmatch(rf"restart-probes/{re.escape(event['profileId'])}-[0-9]+\.json", event.get("repoProbePath", "")):
                raise SafetyStop("host_restart_probe_path")
            recoveries.append({"profileId": event["profileId"], "priorPid": event["oldPid"], "newPid": event["newPid"],
                               "freshAckId": event["freshAckId"], "repoProbePath": event["repoProbePath"],
                               "repoProbeSha256": event["repoProbeSha256"]})
        if event["type"] == "worker_crash_or_anr": crash_events += 1
        if event["type"] in {"unexpected_worker_missing", "worker_crash_or_anr"}:
            packet = event["failurePacket"]
            if not isinstance(packet, str) or not re.fullmatch(r"failures/[0-9]+-avd0[1-4]-p0[1-5]", packet) or packet in failure_packets:
                raise SafetyStop("host_failure_packet_bijection")
            failure_packets.add(packet)
    if not data or pending_restarts or prior_wall > finished_wall or monitors < 1 or first_mono is None or last_mono is None:
        raise SafetyStop("host_ledger_coverage")
    return {"monitorEvents": monitors, "unexpectedWorkerDeaths": unexpected,
            "crashOrAnrEvents": crash_events, "failurePacketCount": len(failure_packets),
            "failurePackets": sorted(failure_packets),
            "recoveries": recoveries,
            "monitoredMillis": int((last_mono - first_mono) / 1_000_000), "headHash": previous}


def atomic_state(run_dir: Path, state: dict[str, object]) -> None:
    candidate = run_dir / "state.next"
    target = run_dir / "state.json"
    with candidate.open("w", encoding="utf-8") as output:
        json.dump(state, output, sort_keys=True, separators=(",", ":"))
        output.flush()
        os.fsync(output.fileno())
    os.replace(candidate, target)
    fsync_dir(run_dir)


def atomic_bytes(path: Path, value: bytes, cap: int = ARTIFACT_CAP) -> None:
    if len(value) > cap:
        raise SafetyStop("artifact_cap")
    path.parent.mkdir(parents=True, exist_ok=True)
    candidate = path.with_suffix(path.suffix + ".next")
    descriptor = os.open(candidate, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        os.write(descriptor, value)
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    os.replace(candidate, path)
    fsync_dir(path.parent)


def sanitize_text(value: bytes) -> bytes:
    return PRIVACY.sub(b"[REDACTED]", value)[:FAILURE_ARTIFACT_CAP]


def verify_worker_ledger(data: bytes, checkpoint_data: bytes, profile_id: str, process_name: str, nonce: str) -> dict[str, object]:
    if not data or len(data) > ARTIFACT_CAP:
        raise SafetyStop("ledger_size")
    checkpoint = json.loads(checkpoint_data)
    previous_hash = "0" * 64
    hashes: set[str] = set()
    ack_ids: set[str] = set()
    admitted: set[int] = set()
    eligible: set[int] = set()
    categories: dict[int, set[str]] = {}
    ops: dict[int, int] = {}
    crash_or_anr: set[int] = set()
    failures = 0
    last_elapsed = -1
    last_wall = -1
    profile_complete = None
    window_start: dict[int, int] = {}
    ack_elapsed: dict[int, list[int]] = {}
    ack_sequences: dict[int, list[int]] = {}
    scheduled_restarts = 0
    pending_resume: dict[int, tuple[int, int]] = {}
    recoveries: list[dict[str, object]] = []
    for raw in data.splitlines():
        event = json.loads(raw)
        if not isinstance(event, dict) or event.get("type") not in EVENT_TYPES:
            raise SafetyStop("ledger_event_type")
        if set(event) != COMMON_EVENT_KEYS | EVENT_KEYS[event["type"]]:
            raise SafetyStop("ledger_event_not_closed")
        if event.get("profileId") != profile_id or event.get("processName") != process_name or event.get("originNonce") != nonce:
            raise SafetyStop("ledger_identity")
        if not isinstance(event.get("pid"), int) or event["pid"] <= 0:
            raise SafetyStop("ledger_pid")
        if PRIVACY.search(raw):
            raise SafetyStop("ledger_privacy")
        recorded = event.pop("eventHash", None)
        if event.get("previousHash") != previous_hash:
            raise SafetyStop("ledger_chain_previous")
        canonical = json.dumps(event, ensure_ascii=False, separators=(",", ":"))
        if not isinstance(recorded, str) or sha256_bytes(canonical.encode()) != recorded:
            raise SafetyStop("ledger_chain_hash")
        previous_hash = recorded
        hashes.add(recorded)
        elapsed, wall = int(event["elapsedRealtimeMillis"]), int(event["wallTimeMillis"])
        if elapsed < last_elapsed or wall + 5_000 < last_wall:
            raise SafetyStop("ledger_clock_rollback")
        last_elapsed, last_wall = elapsed, wall
        window = int(event.get("windowIndex", 0))
        if event["type"] == "admitted":
            if window in admitted:
                raise SafetyStop("duplicate_admission")
            expected_seed = hashlib.sha256(f"codecks-m16-seed-v1:{profile_id}".encode()).hexdigest()[:16]
            if event.get("seed") != expected_seed or event.get("profileRoot") != f"m16/profiles/{profile_id}" or event.get("eligibleTarget") not in {2, 168}:
                raise SafetyStop("admission_binding")
            admitted.add(window)
            window_start[window] = elapsed
        elif event["type"] == "ack":
            ack = event.get("ackId")
            if not isinstance(ack, str) or ack in ack_ids:
                raise SafetyStop("duplicate_ack")
            ack_ids.add(ack)
            sequence = event.get("sequence")
            category = event.get("category")
            if not isinstance(sequence, int) or sequence <= 0 or category not in CATEGORIES:
                raise SafetyStop("ack_sequence_or_category")
            expected_ack = hashlib.sha256(f"{nonce}:{window}:{sequence}".encode()).hexdigest()
            if ack != expected_ack:
                raise SafetyStop("ack_identity")
            resumed = pending_resume.pop(window, None)
            if resumed:
                if event["pid"] != resumed[1]: raise SafetyStop("resume_ack_pid")
                recoveries.append({"profileId": profile_id, "priorPid": resumed[0], "newPid": resumed[1], "freshAckId": ack})
            reasons = event.get("applicationExitReasons")
            if not isinstance(reasons, dict) or set(reasons) != {"crash", "nativeCrash", "anr", "self", "other"} or any(not isinstance(value, int) or value < 0 for value in reasons.values()):
                raise SafetyStop("exit_reason_shape")
            if not isinstance(event.get("operationLatencyMillis"), int) or not 0 <= event["operationLatencyMillis"] <= 10_000:
                raise SafetyStop("operation_latency")
            ack_sequences.setdefault(window, []).append(sequence)
            ack_elapsed.setdefault(window, []).append(elapsed)
            ops[window] = ops.get(window, 0) + 1
            categories.setdefault(window, set()).add(category)
            reasons = event.get("applicationExitReasons", {})
            if any(int(reasons.get(key, 0)) > 0 for key in ("crash", "nativeCrash", "anr")):
                crash_or_anr.add(window)
        elif event["type"] == "window_complete":
            claimed = bool(event["eligible"])
            times = ack_elapsed.get(window, [])
            active = sum(delta for first, second in zip(times, times[1:]) if 20_000 <= (delta := second - first) <= 45_000)
            sequences = ack_sequences.get(window, [])
            if sequences != list(range(1, len(sequences) + 1)):
                raise SafetyStop("ack_sequence_gap")
            span = elapsed - window_start.get(window, elapsed)
            if int(event.get("elapsedMillis", -1)) != span or not 3_600_000 <= span <= 3_660_000:
                raise SafetyStop("window_not_exact_hour")
            if int(event.get("activeMillis", -1)) != active or set(event.get("categories", [])) != categories.get(window, set()):
                raise SafetyStop("window_claim_mismatch")
            computed = active >= 50 * 60 * 1000 and len(sequences) >= 100 and len(categories.get(window, set())) >= 5
            if claimed != computed:
                raise SafetyStop("window_eligibility_mismatch")
            if computed:
                eligible.add(window)
        elif event["type"] in {"admitted_incomplete", "window_ineligible"}:
            failures += 1
        elif event["type"] == "profile_complete":
            profile_complete = event
        elif event["type"] == "lifecycle_process_death_scheduled":
            scheduled_restarts += 1
        elif event["type"] == "resumed":
            prior_pid, new_pid = event.get("priorPid"), event.get("newPid")
            if (window not in admitted or window in pending_resume or event.get("sequence") != len(ack_sequences.get(window, []))
                    or not isinstance(prior_pid, int) or not isinstance(new_pid, int) or prior_pid <= 0 or new_pid <= 0
                    or prior_pid == new_pid or event["pid"] != new_pid):
                raise SafetyStop("resume_binding")
            pending_resume[window] = (prior_pid, new_pid)
    if checkpoint.get("ledgerHeadHash") not in hashes or pending_resume:
        raise SafetyStop("checkpoint_not_prefix")
    if profile_complete is None:
        raise SafetyStop("profile_not_complete")
    if int(profile_complete["eligibleWindows"]) != len(eligible) or int(profile_complete["attemptedWindows"]) != len(admitted):
        raise SafetyStop("profile_totals_mismatch")
    if failures != len(admitted) - len(eligible):
        raise SafetyStop("incomplete_not_classified")
    return {
        "profileId": profile_id, "ledgerSha256": sha256_bytes(data), "checkpointSha256": sha256_bytes(checkpoint_data),
        "attemptedSessions": len(admitted), "eligibleSessions": len(eligible), "acknowledgedOperations": len(ack_ids),
        "uniqueAckOperations": len(ack_ids), "crashOrAnrSessions": len(crash_or_anr), "classifiedFailures": failures,
        "ledgerHeadHash": previous_hash,
        "scheduledRestarts": scheduled_restarts,
        "recoveries": recoveries,
    }


def pull_profile_artifacts(run_dir: Path, devices: dict[str, str]) -> list[dict[str, object]]:
    results = []
    for avd, serial in devices.items():
        avd_number = int(avd.removeprefix("m16Soak").removesuffix("Api35"))
        for slot in range(1, 6):
            profile = f"avd{avd_number:02d}-p{slot:02d}"
            process = f"{PACKAGE}:m16p{slot:02d}"
            nonce = hashlib.sha256(f"codecks-m16-nonce-v1:{profile}".encode()).hexdigest()[:32]
            base_uri = f"content://{PACKAGE}.m16evidence/profile/{profile}"
            ledger = adb_bytes(serial, "exec-out", "content", "read", "--uri", f"{base_uri}/ledger.jsonl")
            checkpoint = adb_bytes(serial, "exec-out", "content", "read", "--uri", f"{base_uri}/checkpoint.json")
            directory = run_dir / "profiles" / profile
            atomic_bytes(directory / "ledger.jsonl", ledger)
            atomic_bytes(directory / "checkpoint.json", checkpoint)
            summary = verify_worker_ledger(ledger, checkpoint, profile, process, nonce)
            summary.update({"avd": avd, "process": process, "ledgerPath": str((directory / "ledger.jsonl").relative_to(run_dir)),
                            "checkpointPath": str((directory / "checkpoint.json").relative_to(run_dir))})
            results.append(summary)
    return results


def verify_repo_probes(devices: dict[str, str]) -> dict[str, bytes]:
    hashes = {}
    for avd, serial in devices.items():
        avd_number = int(avd.removeprefix("m16Soak").removesuffix("Api35"))
        speeds = set()
        for slot in range(1, 6):
            profile = f"avd{avd_number:02d}-p{slot:02d}"
            nonce = hashlib.sha256(f"codecks-m16-nonce-v1:{profile}".encode()).hexdigest()[:32]
            data = adb_bytes(serial, "exec-out", "content", "read", "--uri",
                             f"content://{PACKAGE}.m16evidence/profile/{profile}/repo-probe.json")
            probe = json.loads(data)
            if set(probe) != {"profileId", "pointerSpeed", "originNonce"} or probe["profileId"] != profile or probe["originNonce"] != nonce:
                raise SafetyStop("repo_probe_identity")
            hashes[profile] = data
            speeds.add(probe["pointerSpeed"])
        if len(speeds) != 5:
            raise SafetyStop("repo_probe_cross_store_contamination")
    return hashes


def capture_failure(run_dir: Path, avd: str, serial: str, worker: dict[str, object]) -> str:
    profile = str(worker.get("profileId", "unknown"))
    if not re.fullmatch(r"avd0[1-4]-p0[1-5]", profile):
        raise SafetyStop("failure_profile_invalid")
    directory = run_dir / "failures" / f"{int(time.time() * 1000)}-{profile}"
    if len(list((run_dir / "failures").glob("*"))) >= 64 if (run_dir / "failures").exists() else False:
        raise SafetyStop("failure_packet_cap")
    pid = str(worker.get("pid", ""))
    logcat = adb_bytes(serial, "logcat", "-d", "--pid", pid, "-t", "400")
    exits = adb_bytes(serial, "shell", "dumpsys", "activity", "exit-info", PACKAGE)
    tombstones = adb_bytes(serial, "shell", "dumpsys", "dropbox", "--print", "SYSTEM_TOMBSTONE")
    for name, value in (("logcat.txt", logcat), ("exit-info.txt", exits), ("tombstones.txt", tombstones)):
        atomic_bytes(directory / name, sanitize_text(value), FAILURE_ARTIFACT_CAP)
    failure_code = str(worker.get("reasonCode", "worker_failed"))
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,47}", failure_code):
        failure_code = "worker_failed"
    window_index = min(336, max(1, int(worker.get("attemptedWindows", 0)) + 1))
    activity = f"{PACKAGE}/io.codecks.internalquality.m16.M16FailureEvidenceActivity"
    adb(serial, "shell", "am", "start", "-W", "-n", activity, "--es", "profile_id", profile,
        "--es", "failure_code", failure_code, "--ei", "window_index", str(window_index))
    foreground = adb(serial, "shell", "dumpsys", "activity", "activities")
    if not re.search(r"mResumedActivity.*io\.codecks\.internalquality\.m16\.M16FailureEvidenceActivity", foreground):
        atomic_bytes(directory / "screenshot-capture-failed.json", b'{"reasonCode":"safe_surface_not_foreground"}')
        raise SafetyStop("safe_screenshot_surface_not_foreground")
    screenshot = adb_bytes(serial, "exec-out", "screencap", "-p")
    atomic_bytes(directory / "screenshot-allowlist.png", screenshot, FAILURE_ARTIFACT_CAP)
    repro = {
        "schema": "codecks.m16.failure-host.v1", "avd": avd, "profileId": profile,
        "processName": worker.get("processName"), "reasonCode": failure_code,
        "lastAckId": worker.get("lastAckId"), "syntheticNoNetworkDevice": True,
        "reproduction": ["install exact bound PlayInternal APKs", "start exact profile seed", "replay ledger through last acknowledged operation"],
    }
    atomic_bytes(directory / "state-repro.json", json.dumps(repro, sort_keys=True, separators=(",", ":")).encode(), FAILURE_ARTIFACT_CAP)
    return str(directory.relative_to(run_dir))


def profile_catalog_from_receipt(receipt: dict[str, object]) -> list[dict[str, object]]:
    profiles = receipt.get("profiles")
    if not isinstance(profiles, list):
        raise SafetyStop("burnin_profile_catalog")
    return [{"profileId": item.get("profileId"), "avd": item.get("avd"), "process": item.get("process")}
            for item in profiles if isinstance(item, dict)]


def expected_profile_catalog() -> list[dict[str, object]]:
    return [{"profileId": f"avd{avd:02d}-p{slot:02d}", "avd": AVDS[avd - 1],
             "process": f"{PACKAGE}:m16p{slot:02d}"}
            for avd in range(1, 5) for slot in range(1, 6)]


def phase_neutral_avd_configs(provision: dict[str, object]) -> list[dict[str, object]]:
    entries = provision.get("avds")
    lexical_value = provision.get("avdHomeLexical")
    canonical_value = provision.get("avdHomeCanonical")
    if not isinstance(entries, list) or not isinstance(lexical_value, str) or not isinstance(canonical_value, str):
        raise SafetyStop("topology_provision_shape")
    lexical = Path(lexical_value); canonical = Path(canonical_value)
    if (not lexical.is_absolute() or lexical.is_symlink() or lexical.resolve() != canonical
            or lexical != canonical or canonical.name != "avd-home"):
        raise SafetyStop("topology_avd_home_binding")
    normalized=[]
    for item in entries:
        if not isinstance(item,dict): raise SafetyStop("topology_config_shape")
        name=item.get("name"); path=Path(str(item.get("configPath","")))
        expected=Path(f"{name}.avd")/"config.ini"
        if (name not in AVDS or not path.is_absolute() or path.is_symlink() or not path.is_file()
                or path.resolve()!=path or not path.is_relative_to(canonical)
                or path.relative_to(canonical)!=expected or sha256_file(path)!=item.get("configSha256")):
            raise SafetyStop("topology_config_binding")
        normalized.append({"name":name,"port":item.get("port"),"configRelativePath":expected.as_posix(),
                           "configSha256":item.get("configSha256")})
    return normalized


def burnin_topology(receipt: dict[str, object]) -> dict[str, object]:
    runtime = receipt.get("runtime")
    devices = receipt.get("devices")
    if not isinstance(runtime, dict) or not isinstance(devices, list):
        raise SafetyStop("burnin_topology_shape")
    provision = runtime.get("avdProvision")
    if not isinstance(provision, dict) or not isinstance(provision.get("avds"), list):
        raise SafetyStop("burnin_topology_provision")
    return {
        "devices": [{key: item.get(key) for key in ("avd", "serial", "api", "fingerprintSha256", "uid", "dataDir")}
                    for item in devices if isinstance(item, dict)],
        "avdConfigs": phase_neutral_avd_configs(provision),
        "profiles": profile_catalog_from_receipt(receipt),
    }


def current_topology(device_bindings: list[dict[str, object]], provision: dict[str, object]) -> dict[str, object]:
    entries = provision.get("avds")
    if not isinstance(entries, list):
        raise SafetyStop("current_topology_provision")
    return {
        "devices": [{key: item.get(key) for key in ("avd", "serial", "api", "fingerprintSha256", "uid", "dataDir")}
                    for item in device_bindings],
        "avdConfigs": phase_neutral_avd_configs(provision),
        "profiles": expected_profile_catalog(),
    }


def verify_burnin_state_snapshot(state: dict[str, object], receipt: dict[str, object], receipt_sha: str) -> None:
    required = {"schema", "mode", "durationHours", "devices", "startedWallMillis", "startedMonotonicNanos",
                "status", "profiles", "baselineHealth", "binding", "deviceBindings", "isolatedAdb", "runToken",
                "emulatorPids", "defaultAdbAudit", "avdHome", "burninAdmission", "receiptSha256",
                "completedWallMillis", "cleanupStatus"}
    if set(state) not in (required, required | {"resumedWallMillis"}):
        raise SafetyStop("burnin_state_not_closed")
    runtime = receipt.get("runtime")
    wall = receipt.get("wall")
    devices = receipt.get("devices")
    if not isinstance(runtime, dict) or not isinstance(wall, dict) or not isinstance(devices, list):
        raise SafetyStop("burnin_state_receipt_shape")
    expected_devices = {str(item.get("avd")): str(item.get("serial")) for item in devices if isinstance(item, dict)}
    if (state.get("schema") != "codecks.m16.host-state.v1" or state.get("mode") != "burnin2h"
            or state.get("durationHours") != 2 or state.get("status") != "complete"
            or state.get("cleanupStatus") != "complete" or state.get("profiles") != 20
            or state.get("completedWallMillis") != wall.get("finishedWallMillis")
            or state.get("receiptSha256") != receipt_sha or state.get("binding") != receipt.get("binding")
            or state.get("deviceBindings") != devices or state.get("devices") != expected_devices
            or state.get("runToken") != runtime.get("runIdentity") or state.get("isolatedAdb") != runtime.get("isolatedAdb")
            or state.get("emulatorPids") != runtime.get("emulatorPids")
            or state.get("defaultAdbAudit") != runtime.get("defaultAdbAudit")
            or state.get("avdHome") != runtime.get("avdProvision", {}).get("avdHomeLexical")
            or state.get("burninAdmission") != burnin_not_required()
            or not isinstance(state.get("startedWallMillis"), int) or not isinstance(state.get("startedMonotonicNanos"), int)
            or not isinstance(state.get("baselineHealth"), dict)
            or set(state["baselineHealth"]) != {"swapUsedMiB", "memoryFreePercent", "availableGiB", "load1", "thermal"}):
        raise SafetyStop("burnin_state_mismatch")


def verify_burnin_admission(burnin_run_dir: Path, soak_run_dir: Path, binding: dict[str, object],
                            device_bindings: list[dict[str, object]],
                            provision: dict[str, object], now_millis: int | None = None) -> dict[str, object]:
    if (burnin_run_dir == soak_run_dir or burnin_run_dir.is_relative_to(soak_run_dir)
            or soak_run_dir.is_relative_to(burnin_run_dir)):
        raise SafetyStop("phase_directory_relationship")
    path_identity = phase_path_identity(soak_run_dir, burnin_run_dir)
    receipt_path = burnin_run_dir / BURNIN_RECEIPT_NAME
    state_path = burnin_run_dir / BURNIN_STATE_NAME
    if (receipt_path.is_symlink() or state_path.is_symlink() or not receipt_path.is_file() or not state_path.is_file()
            or receipt_path.resolve().parent != burnin_run_dir or state_path.resolve().parent != burnin_run_dir):
        raise SafetyStop("burnin_artifacts_missing_or_unsafe")
    try:
        receipt = json.loads(receipt_path.read_text())
        state = json.loads(state_path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise SafetyStop("burnin_artifacts_invalid") from error
    repo = Path(__file__).resolve().parents[1]
    validator = repo / "tools/evidence/validate_m16_autonomous_soak.py"
    result = subprocess.run([sys.executable, str(validator), str(receipt_path)], cwd=repo,
                            text=True, capture_output=True, check=False)
    validator_pass = "PASS M16 AUTONOMOUS_PROXY receipt"
    if result.returncode != 0 or result.stdout.strip() != validator_pass:
        raise SafetyStop("burnin_external_validator_failed")
    receipt_sha = sha256_file(receipt_path)
    finished = receipt.get("wall", {}).get("finishedWallMillis") if isinstance(receipt.get("wall"), dict) else None
    now = int(time.time() * 1000) if now_millis is None else now_millis
    if (receipt.get("status") != "PASS" or receipt.get("sourceCommit") != binding.get("sourceCommit")
            or receipt.get("binding") != binding or not isinstance(finished, int)
            or finished > now or now - finished > BURNIN_MAX_AGE_MILLIS):
        raise SafetyStop("burnin_receipt_stale_or_mismatched")
    profiles = receipt.get("profiles")
    if (not isinstance(profiles, list) or len(profiles) != 20
            or any(not isinstance(item, dict) or item.get("eligibleSessions") != 2 for item in profiles)
            or profile_catalog_from_receipt(receipt) != expected_profile_catalog()):
        raise SafetyStop("burnin_profile_catalog_mismatch")
    verify_burnin_state_snapshot(state, receipt, receipt_sha)
    burnin_topology_value = burnin_topology(receipt)
    current_topology_value = current_topology(device_bindings, provision)
    topology_sha = canonical_sha256(burnin_topology_value)
    if burnin_topology_value != current_topology_value:
        raise SafetyStop("burnin_topology_mismatch")
    return {
        "status": "PASS", "receiptPath": BURNIN_RECEIPT_NAME, "receiptSha256": receipt_sha,
        "statePath": BURNIN_STATE_NAME, "stateSha256": sha256_file(state_path),
        "finishedWallMillis": finished, "validatedWallMillis": now,
        "maxBurninAgeHours": 24, "bindingSha256": canonical_sha256(binding),
        "topologySha256": topology_sha, "externalValidator": validator_pass,
        **path_identity,
    }


def write_receipt(run_dir: Path, state: dict[str, object], profiles: list[dict[str, object]]) -> Path:
    binding = state["binding"]
    target = int(state["durationHours"]) * 20
    finished_wall = int(time.time() * 1000)
    host_proof = verify_host_ledger((run_dir / "host-ledger.jsonl").read_bytes(), int(state["startedWallMillis"]), finished_wall)
    if host_proof["monitoredMillis"] < int(state["durationHours"]) * 3_600_000:
        raise SafetyStop("host_monitor_duration")
    crash_sessions = sum(int(item["crashOrAnrSessions"]) for item in profiles)
    if int(host_proof["crashOrAnrEvents"]) != crash_sessions or int(host_proof["failurePacketCount"]) != crash_sessions + int(host_proof["unexpectedWorkerDeaths"]):
        raise SafetyStop("failure_event_packet_count")
    classified = (sum(int(item["classifiedFailures"]) for item in profiles)
                  + int(host_proof["unexpectedWorkerDeaths"]) + crash_sessions)
    summary = {
        "admittedSessions": sum(int(item["attemptedSessions"]) for item in profiles),
        "eligibleSessions": sum(int(item["eligibleSessions"]) for item in profiles),
        "acknowledgedOperations": sum(int(item["acknowledgedOperations"]) for item in profiles),
        "crashOrAnrSessions": crash_sessions,
        "p0": (sum(int(item["eligibleSessions"]) != int(state["durationHours"]) for item in profiles)
               + int(sum(int(item["eligibleSessions"]) for item in profiles) != target)),
        "p1": crash_sessions + int(host_proof["unexpectedWorkerDeaths"]),
        "classifiedFailures": classified,
    }
    dependencies = json.loads((Path(__file__).resolve().parents[1] / "tasks/test-evidence/m16-dependency-manifest.json").read_text())["dependencies"]
    failure_artifacts = []
    for artifact in sorted((run_dir / "failures").glob("**/*")) if (run_dir / "failures").exists() else []:
        if artifact.is_file():
            if artifact.stat().st_size > FAILURE_ARTIFACT_CAP or PRIVACY.search(artifact.read_bytes()):
                raise SafetyStop("failure_artifact_invalid")
            failure_artifacts.append({"path": str(artifact.relative_to(run_dir)), "sha256": sha256_file(artifact)})
    packet_dirs = {str(path.parent.relative_to(run_dir)) for path in (run_dir / "failures").glob("*/*") if path.is_file()}
    if packet_dirs != set(host_proof["failurePackets"]):
        raise SafetyStop("failure_packet_orphan_or_missing")
    passed = summary["p0"] == 0 and summary["p1"] == 0 and summary["crashOrAnrSessions"] == 0
    receipt = {
        "schema": "codecks.autonomous-maturity.m16-soak.v1", "milestone": "M16", "status": "PASS" if passed else "FAIL",
        "evidence": "AUTONOMOUS_PROXY", "package": PACKAGE, "sourceCommit": binding["sourceCommit"],
        "binding": binding, "devices": state["deviceBindings"], "profiles": profiles,
        "runtime": {"isolatedAdb": state["isolatedAdb"], "runIdentity": state["runToken"],
                    "emulatorPids": state["emulatorPids"], "defaultAdbAudit": state["defaultAdbAudit"],
                    "avdProvision":receipt_provision_binding(run_dir,state)},
        "dependencies": dependencies, "summary": summary, "failureArtifacts": failure_artifacts,
        "burninAdmission": state.get("burninAdmission", burnin_not_required()),
        "wall": {"startedWallMillis": state["startedWallMillis"], "finishedWallMillis": finished_wall,
                 "hostLedgerSha256": sha256_file(run_dir / "host-ledger.jsonl"), **host_proof},
        "limitations": ["AUTONOMOUS_PROXY is not human, Samsung, physical-phone, or production evidence."],
    }
    path = run_dir / "receipt.json"
    atomic_bytes(path, json.dumps(receipt, sort_keys=True, separators=(",", ":")).encode())
    if not passed:
        raise SafetyStop("receipt_quality_gate_failed")
    return path


def self_validate_receipt(path: Path) -> None:
    repo = Path(__file__).resolve().parents[1]
    validator = repo / "tools/evidence/validate_m16_autonomous_soak.py"
    result = subprocess.run([sys.executable, str(validator), str(path)], cwd=repo, text=True, capture_output=True, check=False)
    if result.returncode != 0 or result.stdout.strip() != "PASS M16 AUTONOMOUS_PROXY receipt":
        raise SafetyStop("producer_self_validation_failed")


def safe_stop_services(state: dict[str, object]) -> None:
    devices = state.get("devices", {})
    for avd, serial in devices.items():
        for service in SERVICES:
            try:
                service_command(str(serial), str(avd), service, "STOP", int(state["durationHours"]))
            except (SafetyStop, subprocess.SubprocessError):
                continue
        try:
            adb(str(serial), "shell", "am", "force-stop", PACKAGE)
        except (SafetyStop, subprocess.SubprocessError):
            continue


def verify_services_stopped(state: dict[str, object]) -> None:
    deadline=time.monotonic()+30
    devices=state.get("devices",{})
    while time.monotonic()<deadline:
        live=[]
        for serial in devices.values():
            result=adb_result(str(serial),"shell","pidof",PACKAGE,*[f"{PACKAGE}:m16p{i:02d}" for i in range(1,6)])
            if result.stdout.strip(): live.append(result.stdout.strip())
        if not live: return
        time.sleep(1)
    raise SafetyStop("worker_cleanup_timeout")


def stop_owned_emulators(run_dir: Path, state: dict[str, object]) -> None:
    token=str(state.get("runToken","")); pids=state.get("emulatorPids",{}); devices=state.get("devices",{})
    if not re.fullmatch(r"[0-9a-f]{32}",token) or set(pids)!=set(AVDS) or set(devices)!=set(AVDS): raise SafetyStop("owned_emulator_binding")
    require_owner(run_dir)
    server_binding=state.get("isolatedAdb")
    if server_binding!=adb_server_binding(): raise SafetyStop("refuse_unowned_adb_server_stop")
    for avd in AVDS:
        pid=int(pids[avd]); command=process_command(pid)
        if command and (avd not in command or token not in command): raise SafetyStop("refuse_unowned_emulator_stop")
        if command:
            qemu_host_binding(Path(str(state.get("avdHome",""))),avd,token,pid)
            adb_result(str(devices[avd]),"emu","kill")
    deadline=time.monotonic()+30
    while time.monotonic()<deadline and any(subprocess.run(["ps","-p",str(pid)],capture_output=True).returncode==0 for pid in pids.values()): time.sleep(1)
    if any(subprocess.run(["ps","-p",str(pid)],capture_output=True).returncode==0 for pid in pids.values()): raise SafetyStop("owned_emulator_stop_timeout")
    if server_binding!=adb_server_binding(): raise SafetyStop("isolated_adb_changed_before_stop")
    stop_owned_adb_server(server_binding)


def continuous_monitor(run_dir: Path, state: dict[str, object]) -> None:
    devices = parse_devices([f"{key}={value}" for key, value in state["devices"].items()])
    previous_ack: dict[str, tuple[str, float]] = {}
    previous_workers: dict[str, dict[str, object]] = {}
    scheduled_gap_since: dict[str, float] = {}
    pending_restarts: dict[str, tuple[int, str, float]] = {}
    device_clocks: dict[str, tuple[int, int]] = {}
    deadline = time.monotonic() + int(state["durationHours"]) * 2 * 3600 + 600
    try:
        while True:
            current_state = json.loads((run_dir / "state.json").read_text())
            if current_state.get("status") == "stopped":
                return
            if time.monotonic() > deadline:
                raise SafetyStop("monitor_wall_cap")
            require_capacity("runtime", run_dir)
            global_adb_guard(devices, audit_default_adb())
            health = host_health()
            baseline = state["baselineHealth"]
            if state["mode"] == "burnin2h" and float(health["swapUsedMiB"]) > float(baseline["swapUsedMiB"]):
                raise SafetyStop("burnin_swap_growth")
            qemu_rss = 0
            for binding in state["deviceBindings"]:
                verified = verify_device(binding["avd"], binding["serial"], state["binding"]["targetApkSha256"], state["binding"]["testApkSha256"],
                                         str(state["runToken"]),int(state["emulatorPids"][binding["avd"]]),Path(str(state["avdHome"])))
                qemu_rss += int(verified["qemu"]["rssKiB"])
                prior_clock = device_clocks.get(str(binding["avd"]))
                current_clock = (int(verified["observedWallMillis"]), int(verified["observedUptimeMillis"]))
                if prior_clock and (current_clock[1] < prior_clock[1] or abs((current_clock[0] - prior_clock[0]) - (current_clock[1] - prior_clock[1])) > 5_000):
                    raise SafetyStop("device_wall_monotonic_jump")
                device_clocks[str(binding["avd"])] = current_clock
            if qemu_rss > 24 * 1024 * 1024:
                raise SafetyStop("qemu_rss_cap")
            statuses = collect_worker_statuses(devices, tolerate_missing=True)
            now = time.monotonic()
            for worker in statuses:
                profile = str(worker["profileId"])
                if worker["state"] == "scheduled_gap":
                    since = scheduled_gap_since.setdefault(profile, now)
                    if now - since > 90:
                        raise SafetyStop("scheduled_restart_timeout")
                    append_host_event(run_dir, {"type": "scheduled_restart_gap", "profileId": profile})
                    continue
                scheduled_gap_since.pop(profile, None)
                if worker["state"] == "missing":
                    pending = pending_restarts.get(profile)
                    if pending:
                        if now - pending[2] > 90: raise SafetyStop("unexpected_restart_timeout")
                        continue
                    prior = previous_workers.get(profile, worker)
                    packet = capture_failure(run_dir, str(worker["avd"]), devices[str(worker["avd"])], prior)
                    append_host_event(run_dir, {"type": "unexpected_worker_missing", "profileId": profile, "failurePacket": packet,
                                               "oldPid": int(prior["pid"]), "lastAckId": str(prior.get("lastAckId", ""))})
                    slot = int(profile[-2:])
                    service_command(devices[str(worker["avd"])], str(worker["avd"]), SERVICES[slot - 1], "RESUME", int(state["durationHours"]))
                    pending_restarts[profile] = (int(prior["pid"]), str(prior.get("lastAckId", "")), now)
                    continue
                if worker["state"] == "failed":
                    serial = devices[str(worker["avd"])]
                    capture_failure(run_dir, str(worker["avd"]), serial, worker)
                    raise SafetyStop("worker_failed")
                pending = pending_restarts.get(profile)
                if pending:
                    if worker.get("state") != "running" or int(worker.get("pid", 0)) == pending[0] or not worker.get("lastAckId") or worker.get("lastAckId") == pending[1]:
                        if now - pending[2] > 90: raise SafetyStop("unexpected_restart_not_fresh")
                        continue
                    probes = verify_repo_probes({str(worker["avd"]): devices[str(worker["avd"])]})
                    probe_path = run_dir / "restart-probes" / f"{profile}-{int(worker['pid'])}.json"
                    atomic_bytes(probe_path, probes[profile], FAILURE_ARTIFACT_CAP)
                    append_host_event(run_dir, {"type": "worker_restarted", "profileId": profile,
                                               "oldPid": pending[0], "newPid": int(worker["pid"]), "freshAckId": worker["lastAckId"],
                                               "repoProbePath": str(probe_path.relative_to(run_dir)),
                                               "repoProbeSha256": sha256_bytes(probes[profile])})
                    pending_restarts.pop(profile)
                ack = worker.get("lastAckId")
                if worker["state"] == "running" and isinstance(ack, str):
                    old = previous_ack.get(str(worker["profileId"]))
                    if old and old[0] == ack and now - old[1] > 90:
                        raise SafetyStop("ack_not_advancing")
                    if not old or old[0] != ack:
                        previous_ack[str(worker["profileId"])] = (ack, now)
                previous_workers[profile] = worker
            append_host_event(run_dir, {"type": "monitor", "workers": 20, "complete": sum(item["state"] == "complete" for item in statuses),
                                        "qemuRssKiB": qemu_rss, "freeGiB": round(disk_free_gib(run_dir), 3), "health": health})
            if all(item["state"] == "complete" for item in statuses):
                profiles = pull_profile_artifacts(run_dir, devices)
                status_by_profile = {str(item["profileId"]): item for item in statuses}
                for profile_summary in profiles:
                    for _ in range(int(profile_summary["crashOrAnrSessions"])):
                        worker = status_by_profile[str(profile_summary["profileId"])]
                        packet = capture_failure(run_dir, str(worker["avd"]), devices[str(worker["avd"])], worker)
                        append_host_event(run_dir, {"type": "worker_crash_or_anr",
                                                   "profileId": profile_summary["profileId"], "failurePacket": packet})
                receipt = write_receipt(run_dir, state, profiles)
                self_validate_receipt(receipt)
                state["receiptSha256"] = sha256_file(receipt)
                state["completedWallMillis"] = json.loads(receipt.read_text())["wall"]["finishedWallMillis"]
                safe_stop_services(state)
                verify_services_stopped(state)
                stop_owned_emulators(run_dir,state)
                state["status"] = "complete"
                state["cleanupStatus"] = "complete"
                atomic_state(run_dir, state)
                if state["mode"] == "burnin2h":
                    atomic_bytes(run_dir / BURNIN_RECEIPT_NAME, receipt.read_bytes())
                    atomic_bytes(run_dir / BURNIN_STATE_NAME, (run_dir / "state.json").read_bytes())
                release_owner(run_dir)
                return
            time.sleep(15)
    except BaseException:
        safe_stop_services(state)
        try: verify_services_stopped(state)
        except SafetyStop: state["cleanupStatus"]="incomplete"
        try: stop_owned_emulators(run_dir,state)
        except SafetyStop: state["cleanupStatus"]="incomplete"
        state["status"] = "failed"
        atomic_state(run_dir, state)
        raise


def require_capacity(stage: str, root: Path) -> None:
    free = disk_free_gib(root)
    minimum = {"pre": MIN_PREPROVISION_GIB, "post": MIN_POSTPROVISION_GIB, "runtime": RUNTIME_STOP_GIB}.get(stage)
    if minimum is None:
        raise SafetyStop("unknown_capacity_stage")
    if stage == "pre" and free < PROJECTED_FOOTPRINT_GIB + 40:
        raise SafetyStop("preprovision_reserve")
    if free < minimum:
        raise SafetyStop(f"disk_{stage}_below_{minimum}gib")


def host_health() -> dict[str, object]:
    swap = subprocess.run(["sysctl", "-n", "vm.swapusage"], text=True, capture_output=True, check=False)
    thermal = subprocess.run(["pmset", "-g", "therm"], text=True, capture_output=True, check=False)
    pressure = subprocess.run(["memory_pressure", "-Q"], text=True, capture_output=True, check=False)
    if swap.returncode or thermal.returncode or pressure.returncode:
        raise SafetyStop("host_health_probe_failed")
    match = re.search(r"used = ([0-9.]+)M", swap.stdout)
    free_match = re.search(r"System-wide memory free percentage:\s*([0-9]+)%", pressure.stdout)
    if not match or not free_match:
        raise SafetyStop("host_health_unparseable")
    thermal_text = thermal.stdout.lower()
    if "cpu_speed_limit=100" not in thermal_text.replace(" ", "") and "no thermal warning level" not in thermal_text:
        raise SafetyStop("thermal_not_nominal")
    free_percent = int(free_match.group(1))
    if free_percent < 20:
        raise SafetyStop("memory_pressure")
    vm = subprocess.run(["vm_stat"], text=True, capture_output=True, check=False)
    load = os.getloadavg()
    page_size = int(re.search(r"page size of (\d+) bytes", vm.stdout).group(1)) if vm.returncode == 0 and re.search(r"page size of (\d+) bytes", vm.stdout) else 0
    pages = sum(int(value.replace(".", "")) for value in re.findall(r"Pages (?:free|inactive|speculative):\s+(\d+\.)", vm.stdout))
    available_gib = pages * page_size / 1024**3
    if available_gib < 8:
        raise SafetyStop("available_ram_below_8gib")
    if load[0] > 9.5:
        raise SafetyStop("host_load_cap")
    return {"swapUsedMiB": float(match.group(1)), "memoryFreePercent": free_percent, "availableGiB": round(available_gib, 3), "load1": load[0], "thermal": "nominal"}


def source_and_artifact_binding(args: argparse.Namespace) -> dict[str, object]:
    repo = Path(__file__).resolve().parents[1]
    if git_output(repo, "status", "--porcelain"):
        raise SafetyStop("source_worktree_dirty")
    commit = git_output(repo, "rev-parse", "HEAD")
    if args.source_commit and args.source_commit != commit:
        raise SafetyStop("source_commit_argument_mismatch")
    if not args.target_apk or not args.test_apk or not args.xml_result:
        raise SafetyStop("start_artifact_arguments_required")
    target, test, xml = map(lambda value: Path(value).resolve(), (args.target_apk, args.test_apk, args.xml_result))
    try:
        target_relative, test_relative, xml_relative = (str(path.relative_to(repo)) for path in (target, test, xml))
    except ValueError as error:
        raise SafetyStop("bound_artifacts_must_be_repo_relative") from error
    isolation = verify_isolation_xml(xml)
    return {
        "sourceCommit": commit, "targetApkPath": target_relative, "targetApkSha256": sha256_file(target),
        "testApkPath": test_relative, "testApkSha256": sha256_file(test), "xmlResultPath": xml_relative,
        "xmlResultSha256": sha256_file(xml), "targetSignerSha256": apk_signer(target), "testSignerSha256": apk_signer(test),
        "isolation": isolation,
    }


def stop_owned_adb_server(binding: dict[str, object]) -> None:
    if binding!=adb_server_binding(): raise SafetyStop("isolated_adb_server_stale")
    pid=int(binding["serverPid"])
    result=subprocess.run(adb_command(ADB_SERVER_PORT,"kill-server"),check=False,capture_output=True)
    if result.returncode: raise SafetyStop("isolated_adb_kill_failed")
    deadline=time.monotonic()+10
    while time.monotonic()<deadline and (pid in listener_pids(ADB_SERVER_PORT) or process_command(pid)): time.sleep(.25)
    if listener_pids(ADB_SERVER_PORT) or process_command(pid): raise SafetyStop("isolated_adb_kill_unproven")


def launch(args: argparse.Namespace) -> None:
    run_dir,burnin_dir=resolve_phase_directories(args.run_dir,args.burnin_run_dir,args.mode,True)
    if burnin_dir is not None:
        require_phase_manifest(run_dir,burnin_dir)
        require_prelaunch_soak_directory(run_dir)
        require_fresh_soak_evidence(run_dir)
    require_capacity("pre",run_dir); audit=audit_default_adb()
    provision=provision_binding(run_dir); avd_home=Path(str(provision["avdHome"]))
    if listener_pids(ADB_SERVER_PORT): raise SafetyStop("isolated_adb_port_in_use")
    acquire_owner(run_dir)
    token=secrets.token_hex(16); pids={}; devices={}; server_binding=None
    try:
        subprocess.run(adb_command(ADB_SERVER_PORT,"start-server"),check=True,capture_output=True)
        server_binding=adb_server_binding()
        isolated=subprocess.run(adb_command(ADB_SERVER_PORT,"devices"),text=True,capture_output=True,check=True).stdout
        isolated_rows=[line.split()[:2] for line in isolated.splitlines()[1:] if len(line.split())>=2]
        allowed_extras={str(item["serial"]) for item in audit.get("authorizedAvds",[])}
        actual_extras={row[0] for row in isolated_rows}
        if not actual_extras.issubset(allowed_extras) or any(row[1]!="device" for row in isolated_rows):
            raise SafetyStop("isolated_adb_prelaunch_inventory")
        emulator=Path(os.environ.get("ANDROID_HOME",str(Path.home()/"Library/Android/sdk")))/"emulator/emulator"
        if not emulator.is_file(): raise SafetyStop("emulator_binary_missing")
        for avd,port in zip(AVDS,EMULATOR_PORTS):
            managed_avd_config(avd_home,avd)
            identity_log=run_dir/f"emulator-{avd}-{token}.log"
            identity_descriptor=os.open(identity_log,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600); os.close(identity_descriptor)
            identity_log_binding(run_dir,avd,token,identity_log)
            console_log=run_dir/f"emulator-{avd}-{token}.console.log"
            descriptor=os.open(console_log,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
            process=subprocess.Popen([str(emulator),"-avd",avd,"-port",str(port),"-no-window","-no-snapshot",
                "-no-boot-anim","-no-audio","-gpu","swiftshader_indirect","-no-metrics","-logcat-output",str(identity_log)],
                stdout=descriptor,stderr=subprocess.STDOUT,start_new_session=True,
                env={**os.environ,"ANDROID_ADB_SERVER_PORT":str(ADB_SERVER_PORT),"ANDROID_AVD_HOME":str(avd_home)})
            os.close(descriptor); pids[avd]=process.pid; devices[avd]=f"emulator-{port}"
        deadline=time.monotonic()+300
        while time.monotonic()<deadline:
            rows=subprocess.run(adb_command(ADB_SERVER_PORT,"devices"),text=True,capture_output=True,check=False).stdout
            try:
                global_adb_guard(devices,audit)
                break
            except SafetyStop:
                pass
            time.sleep(2)
        else: raise SafetyStop("isolated_four_boot_timeout")
        for avd,serial in devices.items():
            qemu_host_binding(avd_home,avd,token,pids[avd])
        state={"schema":"codecks.m16.launch-state.v1","status":"launched","isolatedAdb":server_binding,
               "runToken":token,"devices":devices,"emulatorPids":pids,"defaultAdbAudit":audit,"avdHome":str(avd_home),
               "provision":provision,"launchedWallMillis":int(time.time()*1000)}
        atomic_state(run_dir,state)
    except BaseException as error:
        cleanup=[]
        for avd,pid in pids.items():
            command=process_command(pid)
            if command and avd in command and token in command:
                try: os.kill(pid,15)
                except ProcessLookupError: pass
            elif command: cleanup.append(f"unowned_emulator:{avd}")
        if server_binding is not None:
            try: stop_owned_adb_server(server_binding)
            except SafetyStop as stop_error: cleanup.append(str(stop_error))
        atomic_state(run_dir,{"schema":"codecks.m16.launch-state.v1","status":"failed","runToken":token,
                              "emulatorPids":pids,"devices":devices,"cleanupFailures":cleanup,"reasonCode":str(error)})
        release_owner(run_dir)
        if cleanup: raise SafetyStop("launch_cleanup_incomplete") from error
        raise


def start(args: argparse.Namespace, resume: bool = False) -> None:
    run_dir,burnin_dir=resolve_phase_directories(args.run_dir,args.burnin_run_dir,args.mode,True)
    if burnin_dir is not None:
        require_phase_manifest(run_dir,burnin_dir)
        if not resume:
            require_fresh_soak_evidence(run_dir)
    require_owner(run_dir)
    require_capacity("pre", run_dir)
    devices = parse_devices(args.device)
    launch_state=json.loads((run_dir/"state.json").read_text())
    live_default_audit=audit_default_adb()
    if live_default_audit!=launch_state["defaultAdbAudit"]: raise SafetyStop("default_adb_changed_before_start")
    global_adb_guard(devices,live_default_audit)
    binding = source_and_artifact_binding(args)
    require_capacity("post", run_dir)
    health = host_health()
    duration_hours = 2 if args.mode == "burnin2h" else 168
    if launch_state.get("isolatedAdb")!=adb_server_binding() or launch_state.get("devices")!=devices:
        raise SafetyStop("isolated_launch_binding")
    run_token=str(launch_state.get("runToken","")); emulator_pids=launch_state.get("emulatorPids",{})
    if not re.fullmatch(r"[0-9a-f]{32}",run_token): raise SafetyStop("run_token_invalid")
    avd_home=Path(str(launch_state.get("avdHome","")))
    provision_binding(run_dir)
    device_bindings = [verify_device(avd, serial, str(binding["targetApkSha256"]), str(binding["testApkSha256"]),run_token,
                                     int(emulator_pids[avd]),avd_home) for avd, serial in devices.items()]
    if binding["isolation"]["fingerprintSha256"] not in {item["fingerprintSha256"] for item in device_bindings}:
        raise SafetyStop("isolation_device_not_in_soak_set")
    current_provision = receipt_provision_binding(run_dir, launch_state)
    burnin_admission = (verify_burnin_admission(burnin_dir, run_dir, binding, device_bindings, current_provision)
                         if burnin_dir is not None else burnin_not_required())
    if resume:
        state = json.loads((run_dir / "state.json").read_text())
        stored_admission = state.get("burninAdmission")
        comparable_stored = {**stored_admission, "validatedWallMillis": 0} if isinstance(stored_admission, dict) else stored_admission
        comparable_current = {**burnin_admission, "validatedWallMillis": 0}
        if (state.get("devices") != devices or state.get("binding") != binding
                or int(state.get("durationHours", 0)) != duration_hours
                or comparable_stored != comparable_current):
            raise SafetyStop("resume_binding_mismatch")
        burnin_admission = stored_admission
        state["status"] = "running"
        state["resumedWallMillis"] = int(time.time() * 1000)
    else:
        state = {
            "schema": "codecks.m16.host-state.v1", "mode": args.mode,
            "durationHours": duration_hours, "devices": devices,
            "startedWallMillis": int(time.time() * 1000), "startedMonotonicNanos": time.monotonic_ns(),
            "status": "running", "profiles": 20,
            "baselineHealth": health,
            "binding": binding, "deviceBindings": device_bindings,
            "isolatedAdb":launch_state["isolatedAdb"],"runToken":run_token,"emulatorPids":emulator_pids,
            "defaultAdbAudit":launch_state["defaultAdbAudit"],"avdHome":str(avd_home),
            "burninAdmission": burnin_admission,
        }
    atomic_state(run_dir, state)
    append_host_event(run_dir, {"type": "controller_start", "mode": args.mode, "profiles": 20})
    action = "RESUME" if resume else "START"
    for avd, serial in devices.items():
        for service in SERVICES:
            service_command(serial, avd, service, action, duration_hours)
    deadline = time.monotonic() + 90
    while True:
        try:
            statuses = collect_worker_statuses(devices)
            if any(worker.get("state") != "running" or not worker.get("lastAckId") for worker in statuses):
                raise SafetyStop("workers_not_heartbeat_ready")
            verify_repo_probes(devices)
            break
        except SafetyStop:
            if time.monotonic() >= deadline:
                raise SafetyStop("twenty_worker_admission_timeout")
            time.sleep(2)
    append_host_event(run_dir, {"type": "twenty_workers_admitted", "workers": len(statuses)})
    continuous_monitor(run_dir, state)


def stop(args: argparse.Namespace) -> None:
    run_dir = Path(args.run_dir).resolve()
    require_owner(run_dir)
    state = json.loads((run_dir / "state.json").read_text())
    devices = parse_devices([f"{key}={value}" for key, value in state["devices"].items()])
    global_adb_guard(devices,audit_default_adb())
    safe_stop_services(state)
    verify_services_stopped(state)
    stop_owned_emulators(run_dir,state)
    state["status"] = "stopped"
    state["stoppedWallMillis"] = int(time.time() * 1000)
    atomic_state(run_dir, state)
    append_host_event(run_dir, {"type": "controller_stop"})
    release_owner(run_dir)


def collect_worker_statuses(devices: dict[str, str], tolerate_missing: bool = False) -> list[dict[str, object]]:
    statuses: list[dict[str, object]] = []
    for avd, serial in devices.items():
        uptime_millis = int(float(adb(serial, "shell", "cat", "/proc/uptime").split()[0]) * 1000)
        avd_number = int(avd.removeprefix("m16Soak").removesuffix("Api35"))
        for service in SERVICES:
            slot = SERVICES.index(service) + 1
            expected_profile = f"avd{avd_number:02d}-p{slot:02d}"
            output = adb(serial, "shell", "dumpsys", "activity", "service", component(service))
            json_lines = [line.strip() for line in output.splitlines() if line.strip().startswith("{")]
            if len(json_lines) != 1:
                if not tolerate_missing:
                    raise SafetyStop("missing_worker_status")
                scheduled = False
                try:
                    checkpoint = json.loads(adb_bytes(serial, "exec-out", "content", "read", "--uri",
                        f"content://{PACKAGE}.m16evidence/profile/{expected_profile}/checkpoint.json"))
                    scheduled = checkpoint.get("scheduledRestart") is True
                except (SafetyStop, json.JSONDecodeError):
                    pass
                statuses.append({"avd": avd, "profileId": expected_profile,
                                 "state": "scheduled_gap" if scheduled else "missing"})
                continue
            worker = json.loads(json_lines[0])
            expected_process = f"{PACKAGE}:m16p{SERVICES.index(service) + 1:02d}"
            expected_nonce = hashlib.sha256(f"codecks-m16-nonce-v1:{expected_profile}".encode()).hexdigest()[:32]
            if worker.get("processName") != expected_process or worker.get("pid", 0) <= 0:
                raise SafetyStop("worker_origin_mismatch")
            if worker.get("profileId") != expected_profile or worker.get("originNonce") != expected_nonce:
                raise SafetyStop("worker_identity_nonce_mismatch")
            if worker.get("state") not in {"starting", "admitted", "running", "complete", "failed"}:
                raise SafetyStop("worker_not_admitted")
            pid = int(worker["pid"])
            pidof = adb(serial, "shell", "pidof", expected_process).split()
            if str(pid) not in pidof:
                raise SafetyStop("worker_pid_not_live")
            cmdline = adb(serial, "shell", "cat", f"/proc/{pid}/cmdline").rstrip("\x00")
            if cmdline != expected_process:
                raise SafetyStop("worker_cmdline_mismatch")
            if worker.get("state") == "running":
                heartbeat = int(worker.get("heartbeatElapsed", 0))
                if heartbeat <= 0 or not 0 <= uptime_millis - heartbeat <= 90_000:
                    raise SafetyStop("worker_heartbeat_stale")
            statuses.append({"avd": avd, **worker})
    if len({(item["avd"], item.get("profileId")) for item in statuses}) != 20:
        raise SafetyStop("duplicate_profile_identity")
    live = [item for item in statuses if item["state"] not in {"scheduled_gap", "missing"}]
    if len({(item["avd"], item.get("pid")) for item in live}) != len(live):
        raise SafetyStop("duplicate_worker_pid")
    return statuses


def status(args: argparse.Namespace) -> None:
    run_dir = Path(args.run_dir).resolve()
    require_owner(run_dir)
    state = json.loads((run_dir / "state.json").read_text())
    if disk_free_gib(run_dir) < RUNTIME_STOP_GIB:
        raise SafetyStop("runtime_disk_below_40gib")
    health = host_health()
    baseline = state.get("baselineHealth", {})
    if state.get("mode") == "burnin2h" and health["swapUsedMiB"] > float(baseline.get("swapUsedMiB", 0)):
        raise SafetyStop("burnin_swap_growth")
    devices = parse_devices([f"{key}={value}" for key, value in state["devices"].items()])
    global_adb_guard(devices,audit_default_adb())
    for binding in state["deviceBindings"]:
        verify_device(binding["avd"], binding["serial"], state["binding"]["targetApkSha256"], state["binding"]["testApkSha256"],
                      str(state["runToken"]),int(state["emulatorPids"][binding["avd"]]),Path(str(state["avdHome"])))
    statuses = collect_worker_statuses(devices)
    print(json.dumps({"evidence": "AUTONOMOUS_PROXY", "workers": statuses}, sort_keys=True))


def provision(args: argparse.Namespace) -> None:
    """Create four persistent, run-owned AVD stores without launching them."""
    run_dir,burnin_dir=resolve_phase_directories(args.run_dir,args.burnin_run_dir,args.mode,False)
    existing_items=list(run_dir.iterdir()) if run_dir.exists() else []
    if burnin_dir is not None and existing_items:
        require_phase_manifest(run_dir,burnin_dir)
        names={item.name for item in existing_items}
        allowed={"phase.json"} if "provision.json" not in names else {"phase.json","provision.json","avd-home"}
        if names!=allowed: raise SafetyStop("soak_run_directory_not_new")
    run_dir.mkdir(parents=True,exist_ok=True)
    if burnin_dir is not None and not existing_items:
        atomic_bytes(run_dir/"phase.json",json.dumps(phase_manifest_value(run_dir,burnin_dir),sort_keys=True,separators=(",", ":")).encode())
    require_capacity("pre", run_dir)
    audit_default_adb()
    if listener_pids(ADB_SERVER_PORT): raise SafetyStop("isolated_adb_port_in_use")
    if (run_dir/"provision.json").exists():
        provision_binding(run_dir)
        require_capacity("post",run_dir)
        return
    avd_home=avd_home_for(run_dir)
    if avd_home.exists() and any(avd_home.iterdir()): raise SafetyStop("m16_partial_provision_present")
    avd_home.mkdir(mode=0o700,parents=True,exist_ok=True)
    sdk=Path(os.environ.get("ANDROID_HOME",str(Path.home()/"Library/Android/sdk"))).resolve()
    avdmanager=sdk/"cmdline-tools/latest/bin/avdmanager"
    system_image=sdk/"system-images/android-35/default/arm64-v8a"
    if not avdmanager.is_file(): raise SafetyStop("avdmanager_missing")
    if not system_image.is_dir(): raise SafetyStop("m16_system_image_missing")
    environment={**os.environ,"ANDROID_HOME":str(sdk),"ANDROID_SDK_ROOT":str(sdk),"ANDROID_AVD_HOME":str(avd_home)}
    for avd in AVDS:
        result=subprocess.run([str(avdmanager),"create","avd","--name",avd,"--package",SYSTEM_IMAGE,
                               "--device","pixel_6"],input="no\n",text=True,capture_output=True,check=False,env=environment)
        if result.returncode: raise SafetyStop(f"m16_avd_create_failed:{avd}")
    entries=[]
    for avd,port in zip(AVDS,EMULATOR_PORTS):
        config=managed_avd_config(avd_home,avd)
        entries.append({"name":avd,"port":port,"configPath":str(config),"configSha256":sha256_file(config)})
    manifest={"schema":"codecks.m16.avd-provision.v1","avdHome":str(avd_home),"systemImage":SYSTEM_IMAGE,"avds":entries}
    atomic_bytes(run_dir/"provision.json",json.dumps(manifest,sort_keys=True,separators=(",", ":")).encode())
    provision_binding(run_dir)
    require_capacity("post", run_dir)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    result.add_argument("command", choices=("provision", "launch", "start", "status", "stop", "resume"))
    result.add_argument("--run-dir", required=True)
    result.add_argument("--burnin-run-dir")
    result.add_argument("--device", action="append", default=[])
    result.add_argument("--mode", choices=("burnin2h", "soak168h"), default="burnin2h")
    result.add_argument("--source-commit")
    result.add_argument("--target-apk")
    result.add_argument("--test-apk")
    result.add_argument("--xml-result")
    return result


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.command == "provision":
            provision(args)
        elif args.command == "launch":
            launch(args)
        elif args.command == "start":
            start(args)
        elif args.command == "resume":
            start(args, resume=True)
        elif args.command == "stop":
            stop(args)
        else:
            status(args)
        return 0
    except (SafetyStop, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"M16_STOP:{error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
