#!/usr/bin/env python3
"""Safe, redacted current-Mac evidence primitives for M12."""

from __future__ import annotations

import json
import os
import platform
import re
import socket
import struct
import subprocess
import base64
import hashlib
import tempfile
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

SCHEMA_VERSION = "m12.current-mac.v1"
MAX_OUTPUT_BYTES = 64 * 1024
COMMAND_TIMEOUT_SECONDS = 5.0
HELPER_LABEL = "app.codecks.mac-helper"
DEFAULT_HELPER_PORT = 47321

LANE_IDS = (
    "platform.current",
    "ssh.tcp",
    "ssh.read_only_command",
    "ssh.bounded_command",
    "helper.config_health",
    "helper.launchd_health",
    "helper.tcp",
    "clipboard.read_only_bridge",
    "power.read_only_proxy",
    "failure.network_refused",
    "failure.timeout",
    "failure.output_bound",
    "failure.auth_denied",
    "failure.host_key_mismatch",
    "failure.tool_missing",
    "failure.backoff",
    "mutation.sleep_wake",
    "mutation.helper_restart",
    "mutation.account",
    "mutation.host_key",
    "mutation.permissions",
    "external.intel",
    "external.other_macos",
    "external.physical_dex",
    "external.physical_hid",
)

LANE_POLICY: dict[str, tuple[str, dict[str, str]]] = {
    "platform.current": ("LIVE_CURRENT_MAC", {"PASS": "platform_identified", "FAIL": "platform_unavailable"}),
    "ssh.tcp": ("LIVE_CURRENT_MAC", {"PASS": "ssh_tcp_reachable", "NOT_RUN": "ssh_server_unavailable"}),
    "ssh.read_only_command": ("LIVE_CURRENT_MAC", {"PASS": "ssh_read_only_verified", "NOT_RUN": "ssh_auth_or_trust_unavailable"}),
    "ssh.bounded_command": ("LIVE_CURRENT_MAC", {"PASS": "ssh_output_bound_verified", "NOT_RUN": "ssh_auth_or_trust_unavailable"}),
    "helper.config_health": ("LIVE_CURRENT_MAC", {"PASS": "helper_config_valid", "FAIL": "helper_config_invalid", "NOT_RUN": "helper_install_unavailable"}),
    "helper.launchd_health": ("LIVE_CURRENT_MAC", {"PASS": "helper_service_running", "NOT_RUN": "helper_service_unavailable"}),
    "helper.tcp": ("LIVE_CURRENT_MAC", {"PASS": "helper_tcp_reachable", "NOT_RUN": "helper_tcp_unavailable"}),
    "clipboard.read_only_bridge": ("READ_ONLY_PROXY", {"PASS": "clipboard_read_discarded", "NOT_RUN": "clipboard_bridge_unavailable"}),
    "power.read_only_proxy": ("READ_ONLY_PROXY", {"PASS": "power_state_queried", "NOT_RUN": "power_query_unavailable"}),
    "failure.network_refused": ("INJECTED_FAILURE", {"PASS": "network_failure_classified", "FAIL": "network_failure_injection_failed"}),
    "failure.timeout": ("INJECTED_FAILURE", {"PASS": "timeout_classified", "FAIL": "timeout_injection_failed"}),
    "failure.output_bound": ("INJECTED_FAILURE", {"PASS": "output_truncated", "FAIL": "output_bound_failed"}),
    "failure.auth_denied": ("INJECTED_FAILURE", {"PASS": "auth_denial_classified", "FAIL": "auth_denial_injection_failed", "NOT_RUN": "ssh_server_unavailable"}),
    "failure.host_key_mismatch": ("INJECTED_FAILURE", {"PASS": "host_key_mismatch_classified", "FAIL": "host_key_mismatch_injection_failed", "NOT_RUN": "ssh_server_unavailable"}),
    "failure.tool_missing": ("INJECTED_FAILURE", {"PASS": "tool_missing_classified", "FAIL": "tool_missing_injection_failed"}),
    "failure.backoff": ("INJECTED_FAILURE", {"PASS": "backoff_bounded", "FAIL": "backoff_invalid"}),
    "mutation.sleep_wake": ("READ_ONLY_PROXY", {"NOT_RUN": "primary_mac_mutation_not_approved"}),
    "mutation.helper_restart": ("READ_ONLY_PROXY", {"NOT_RUN": "primary_service_mutation_not_approved"}),
    "mutation.account": ("READ_ONLY_PROXY", {"NOT_RUN": "account_mutation_not_approved"}),
    "mutation.host_key": ("READ_ONLY_PROXY", {"NOT_RUN": "host_key_mutation_not_approved"}),
    "mutation.permissions": ("READ_ONLY_PROXY", {"NOT_RUN": "permission_mutation_not_approved"}),
    "external.intel": ("EXTERNAL", {"NOT_RUN": "intel_hardware_unavailable"}),
    "external.other_macos": ("EXTERNAL", {"NOT_RUN": "additional_macos_unavailable"}),
    "external.physical_dex": ("EXTERNAL", {"NOT_RUN": "physical_dex_external"}),
    "external.physical_hid": ("EXTERNAL", {"NOT_RUN": "physical_hid_external"}),
}

EVIDENCE_PARENT = "tasks/AUTONOMOUS_MATURITY_PLAN.md#m12-current-mac-automation-matrix"
HARNESS_SOURCES = (
    "scripts/m12_current_mac_lib.py",
    "scripts/run_m12_current_mac.py",
    "scripts/verify_m12_current_mac_receipt.py",
)
SCHEMA_PATH = "tasks/schemas/m12-current-mac-receipt.schema.json"

PROHIBITED_RECEIPT_KEYS = re.compile(
    r"(?:hostname|username|account|email|secret|token|password|private.?key|public.?key|clipboard.?content|command.?output)",
    re.IGNORECASE,
)
PROHIBITED_RECEIPT_VALUES = (
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
    re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b"),
    re.compile(r"(?:^|/)Users/[^/]+/"),
    re.compile(r"(?:^|/)home/[^/]+/"),
    re.compile(r"\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b"),
    re.compile(r"SHA256:[A-Za-z0-9+/=]{16,}"),
)


@dataclass(frozen=True)
class CommandResult:
    return_code: int | None
    timed_out: bool
    stdout: bytes
    stderr: bytes
    truncated: bool
    duration_ms: int


def run_bounded(
    argv: list[str],
    *,
    timeout_seconds: float = COMMAND_TIMEOUT_SECONDS,
    max_output_bytes: int = MAX_OUTPUT_BYTES,
    cwd: Path | None = None,
) -> CommandResult:
    """Drain both streams concurrently while retaining at most the requested cap."""
    if not argv or any(not isinstance(part, str) or "\x00" in part for part in argv):
        raise ValueError("argv must contain non-empty NUL-free strings")
    if timeout_seconds <= 0 or max_output_bytes <= 0:
        raise ValueError("bounds must be positive")
    started = time.monotonic()
    try:
        process = subprocess.Popen(
            argv,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=os.environ.copy(),
            cwd=cwd,
        )
        buffers = {"stdout": bytearray(), "stderr": bytearray()}
        truncated = {"stdout": False, "stderr": False}

        def drain(name: str, stream: object) -> None:
            while True:
                chunk = stream.read(8192)  # type: ignore[attr-defined]
                if not chunk:
                    break
                remaining = max_output_bytes - len(buffers[name])
                if remaining > 0:
                    buffers[name].extend(chunk[:remaining])
                if len(chunk) > remaining:
                    truncated[name] = True

        threads = [
            threading.Thread(target=drain, args=("stdout", process.stdout), daemon=True),
            threading.Thread(target=drain, args=("stderr", process.stderr), daemon=True),
        ]
        for thread in threads:
            thread.start()
        timed_out = False
        try:
            return_code = process.wait(timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            process.kill()
            return_code = None
            process.wait()
        for thread in threads:
            thread.join()
        process.stdout.close()
        process.stderr.close()
        return CommandResult(
            return_code,
            timed_out,
            bytes(buffers["stdout"]),
            bytes(buffers["stderr"]),
            truncated["stdout"] or truncated["stderr"],
            round((time.monotonic() - started) * 1000),
        )
    except FileNotFoundError:
        return CommandResult(127, False, b"", b"", False, round((time.monotonic() - started) * 1000))


def run_discarded(argv: list[str], *, timeout_seconds: float = COMMAND_TIMEOUT_SECONDS) -> CommandResult:
    """Run a command without ever reading its stdout into the harness."""
    if not argv or any(not isinstance(part, str) or "\x00" in part for part in argv):
        raise ValueError("argv must contain non-empty NUL-free strings")
    if timeout_seconds <= 0:
        raise ValueError("timeout must be positive")
    started = time.monotonic()
    try:
        completed = subprocess.run(
            argv,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=timeout_seconds,
            check=False,
            env=os.environ.copy(),
        )
        return CommandResult(completed.returncode, False, b"", b"", False, round((time.monotonic() - started) * 1000))
    except subprocess.TimeoutExpired:
        return CommandResult(None, True, b"", b"", False, round((time.monotonic() - started) * 1000))
    except FileNotFoundError:
        return CommandResult(127, False, b"", b"", False, round((time.monotonic() - started) * 1000))


def lane(lane_id: str, status: str, evidence: str, code: str, duration_ms: int = 0) -> dict:
    if lane_id not in LANE_IDS:
        raise ValueError(f"unknown lane: {lane_id}")
    required_evidence, status_codes = LANE_POLICY[lane_id]
    if evidence != required_evidence or status not in status_codes or code != status_codes.get(status):
        raise ValueError(f"lane policy mismatch: {lane_id}")
    if isinstance(duration_ms, bool) or not isinstance(duration_ms, int) or not 0 <= duration_ms <= 120_000:
        raise ValueError("invalid duration")
    return {
        "id": lane_id,
        "status": status,
        "evidence": evidence,
        "code": code,
        "durationMs": duration_ms,
    }


def tcp_probe(port: int, timeout_seconds: float = 1.0) -> tuple[bool, int]:
    started = time.monotonic()
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=timeout_seconds):
            return True, round((time.monotonic() - started) * 1000)
    except OSError:
        return False, round((time.monotonic() - started) * 1000)


def helper_port(home: Path) -> tuple[int, bool]:
    config = home / "Library" / "Application Support" / "CodecksMacHelper" / "helper.json"
    try:
        if not config.is_file() or config.stat().st_size > 16 * 1024:
            return DEFAULT_HELPER_PORT, False
        parsed = json.loads(config.read_text(encoding="utf-8"))
        port = parsed.get("port")
        if not isinstance(port, int) or not 1 <= port <= 65535:
            return DEFAULT_HELPER_PORT, False
        return port, True
    except (OSError, UnicodeError, json.JSONDecodeError):
        return DEFAULT_HELPER_PORT, False


def ssh_base(user: str, port: int, *, strict: bool = True) -> list[str]:
    args = [
        "/usr/bin/ssh",
        "-T",
        "-o", "BatchMode=yes",
        "-o", "PasswordAuthentication=no",
        "-o", "KbdInteractiveAuthentication=no",
        "-o", "ConnectTimeout=3",
        "-o", "ConnectionAttempts=1",
        "-o", "ServerAliveInterval=1",
        "-o", "ServerAliveCountMax=1",
        "-o", f"StrictHostKeyChecking={'yes' if strict else 'no'}",
        "-p", str(port),
        f"{user}@127.0.0.1",
    ]
    if not strict:
        args[1:1] = ["-o", "UserKnownHostsFile=/dev/null", "-o", "LogLevel=ERROR"]
    return args


def is_host_key_mismatch(result: CommandResult) -> bool:
    return (
        result.return_code == 255
        and not result.timed_out
        and (
            b"REMOTE HOST IDENTIFICATION HAS CHANGED" in result.stderr
            or b"Host key verification failed" in result.stderr
        )
    )


def is_auth_denial(result: CommandResult) -> bool:
    return (
        result.return_code == 255
        and not result.timed_out
        and (
            b"Permission denied" in result.stderr
            or b"No more authentication methods to try" in result.stderr
        )
    )


def _digest_files(repo_root: Path, relative_paths: Iterable[str]) -> str:
    digest = hashlib.sha256()
    for relative in relative_paths:
        path = repo_root / relative
        if not path.is_file():
            raise ValueError(f"binding source missing: {relative}")
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def expected_bindings(repo_root: Path) -> dict[str, str]:
    return {
        "evidenceParent": EVIDENCE_PARENT,
        "evidenceParentSha256": _digest_files(repo_root, (EVIDENCE_PARENT.split("#", 1)[0],)),
        "harnessSha256": _digest_files(repo_root, HARNESS_SOURCES),
        "schemaSha256": _digest_files(repo_root, (SCHEMA_PATH,)),
    }


SUPPORTED_SCHEMA_KEYWORDS = {
    "$schema", "$id", "title", "type", "additionalProperties", "required",
    "properties", "const", "enum", "pattern", "format", "minimum", "maximum",
    "minItems", "maxItems", "prefixItems", "items",
}


def _assert_supported_schema(schema: object, path: str = "$") -> None:
    if isinstance(schema, bool):
        return
    if not isinstance(schema, dict):
        raise ValueError(f"schema node must be object or boolean: {path}")
    unknown = set(schema) - SUPPORTED_SCHEMA_KEYWORDS
    if unknown:
        raise ValueError(f"unsupported schema keyword: {path}")
    for key in ("$schema", "$id", "title"):
        if key in schema and not isinstance(schema[key], str):
            raise ValueError(f"schema annotation invalid: {path}")
    if "type" in schema and schema["type"] not in {"object", "array", "string", "integer", "boolean"}:
        raise ValueError(f"unsupported schema type: {path}")
    if "additionalProperties" in schema and schema["additionalProperties"] is not False:
        raise ValueError(f"schema additionalProperties must be false: {path}")
    required = schema.get("required", [])
    if not isinstance(required, list) or any(not isinstance(key, str) for key in required) or len(set(required)) != len(required):
        raise ValueError(f"schema required invalid: {path}")
    if "enum" in schema and (not isinstance(schema["enum"], list) or not schema["enum"]):
        raise ValueError(f"schema enum invalid: {path}")
    if "pattern" in schema:
        if not isinstance(schema["pattern"], str):
            raise ValueError(f"schema pattern invalid: {path}")
        try:
            re.compile(schema["pattern"])
        except re.error as error:
            raise ValueError(f"schema pattern invalid: {path}") from error
    if "format" in schema and schema["format"] != "date-time":
        raise ValueError(f"schema format unsupported: {path}")
    for key in ("minimum", "maximum", "minItems", "maxItems"):
        if key in schema and (isinstance(schema[key], bool) or not isinstance(schema[key], int) or schema[key] < 0):
            raise ValueError(f"schema bound invalid: {path}")
    if "minimum" in schema and "maximum" in schema and schema["minimum"] > schema["maximum"]:
        raise ValueError(f"schema numeric bounds invalid: {path}")
    if "minItems" in schema and "maxItems" in schema and schema["minItems"] > schema["maxItems"]:
        raise ValueError(f"schema array bounds invalid: {path}")
    properties = schema.get("properties", {})
    if not isinstance(properties, dict):
        raise ValueError(f"schema properties invalid: {path}")
    for key, child in properties.items():
        if not isinstance(key, str):
            raise ValueError(f"schema property name invalid: {path}")
        _assert_supported_schema(child, f"{path}.properties.{key}")
    prefix_items = schema.get("prefixItems", [])
    if not isinstance(prefix_items, list):
        raise ValueError(f"schema prefixItems invalid: {path}")
    for index, child in enumerate(prefix_items):
        _assert_supported_schema(child, f"{path}.prefixItems[{index}]")
    if "items" in schema:
        _assert_supported_schema(schema["items"], f"{path}.items")


def _json_equal(left: object, right: object) -> bool:
    return type(left) is type(right) and left == right


def validate_schema_instance(schema: object, instance: object, path: str = "$") -> None:
    """Validate the dependency-free, fail-closed JSON Schema subset used by M12."""
    if schema is False:
        raise ValueError(f"schema rejects value: {path}")
    if schema is True:
        return
    if not isinstance(schema, dict):
        raise ValueError(f"invalid schema node: {path}")
    if "const" in schema and not _json_equal(instance, schema["const"]):
        raise ValueError(f"const mismatch: {path}")
    if "enum" in schema:
        choices = schema["enum"]
        if not isinstance(choices, list) or not any(_json_equal(instance, choice) for choice in choices):
            raise ValueError(f"enum mismatch: {path}")

    declared_type = schema.get("type")
    type_matches = {
        "object": lambda value: isinstance(value, dict),
        "array": lambda value: isinstance(value, list),
        "string": lambda value: isinstance(value, str),
        "integer": lambda value: isinstance(value, int) and not isinstance(value, bool),
        "boolean": lambda value: isinstance(value, bool),
    }
    if declared_type is not None:
        matcher = type_matches.get(declared_type)
        if matcher is None or not matcher(instance):
            raise ValueError(f"type mismatch: {path}")

    if isinstance(instance, dict):
        properties = schema.get("properties", {})
        required = schema.get("required", [])
        if not isinstance(properties, dict) or not isinstance(required, list) or any(not isinstance(key, str) for key in required):
            raise ValueError(f"object schema invalid: {path}")
        missing = set(required) - set(instance)
        if missing:
            raise ValueError(f"required property missing: {path}")
        if schema.get("additionalProperties") is False and set(instance) - set(properties):
            raise ValueError(f"additional property rejected: {path}")
        for key, child in properties.items():
            if key in instance:
                validate_schema_instance(child, instance[key], f"{path}.{key}")

    if isinstance(instance, list):
        minimum_items = schema.get("minItems")
        maximum_items = schema.get("maxItems")
        if minimum_items is not None and (not isinstance(minimum_items, int) or len(instance) < minimum_items):
            raise ValueError(f"array below minimum: {path}")
        if maximum_items is not None and (not isinstance(maximum_items, int) or len(instance) > maximum_items):
            raise ValueError(f"array above maximum: {path}")
        # M12 uses `items` as a strict base contract and `prefixItems` as ordered
        # overlays. Applying both is deliberately stricter than either alone.
        if "items" in schema:
            for index, item in enumerate(instance):
                validate_schema_instance(schema["items"], item, f"{path}[{index}]")
        for index, child in enumerate(schema.get("prefixItems", [])):
            if index < len(instance):
                validate_schema_instance(child, instance[index], f"{path}[{index}]")

    if isinstance(instance, str):
        pattern = schema.get("pattern")
        if pattern is not None:
            if not isinstance(pattern, str) or re.search(pattern, instance) is None:
                raise ValueError(f"pattern mismatch: {path}")
        value_format = schema.get("format")
        if value_format is not None:
            if value_format != "date-time":
                raise ValueError(f"unsupported format: {path}")
            try:
                datetime.strptime(instance, "%Y-%m-%dT%H:%M:%SZ")
            except ValueError as error:
                raise ValueError(f"date-time mismatch: {path}") from error

    if isinstance(instance, int) and not isinstance(instance, bool):
        minimum = schema.get("minimum")
        maximum = schema.get("maximum")
        if minimum is not None and (not isinstance(minimum, int) or instance < minimum):
            raise ValueError(f"number below minimum: {path}")
        if maximum is not None and (not isinstance(maximum, int) or instance > maximum):
            raise ValueError(f"number above maximum: {path}")


def load_and_validate_schema(repo_root: Path) -> dict:
    """Dependency-free schema load plus exact contract checks."""
    try:
        schema = json.loads((repo_root / SCHEMA_PATH).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError("schema unavailable") from error
    _assert_supported_schema(schema)
    if not isinstance(schema, dict) or schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        raise ValueError("schema declaration mismatch")
    if schema.get("type") != "object" or schema.get("additionalProperties") is not False:
        raise ValueError("schema root must fail closed")
    properties = schema.get("properties")
    if not isinstance(properties, dict) or set(properties) != {
        "schemaVersion", "generatedAtUtc", "sourceCommit", "bindings", "environment", "safety", "lanes", "summary"
    }:
        raise ValueError("schema properties mismatch")
    if set(schema.get("required", [])) != set(properties):
        raise ValueError("schema required fields mismatch")
    lanes = properties.get("lanes")
    if not isinstance(lanes, dict) or lanes.get("minItems") != len(LANE_IDS) or lanes.get("maxItems") != len(LANE_IDS):
        raise ValueError("schema lane cardinality mismatch")
    try:
        schema_ids = [entry["properties"]["id"]["const"] for entry in lanes["prefixItems"]]
    except (KeyError, TypeError) as error:
        raise ValueError("schema lane binding unavailable") from error
    if schema_ids != list(LANE_IDS):
        raise ValueError("schema lane order mismatch")
    for lane_id, entry in zip(LANE_IDS, lanes["prefixItems"], strict=True):
        props = entry.get("properties", {})
        evidence, status_codes = LANE_POLICY[lane_id]
        schema_statuses = props.get("status", {}).get("enum")
        if schema_statuses is None:
            schema_statuses = [props.get("status", {}).get("const")]
        schema_codes = props.get("code", {}).get("enum")
        if schema_codes is None:
            schema_codes = [props.get("code", {}).get("const")]
        if props.get("evidence", {}).get("const") != evidence or set(schema_statuses) != set(status_codes) or set(schema_codes) != set(status_codes.values()):
            raise ValueError(f"schema lane policy mismatch: {lane_id}")
    item_properties = lanes.get("items", {}).get("properties", {})
    if item_properties.get("status", {}).get("enum") != ["PASS", "FAIL", "NOT_RUN"]:
        raise ValueError("schema status enum mismatch")
    if item_properties.get("evidence", {}).get("enum") != ["LIVE_CURRENT_MAC", "INJECTED_FAILURE", "READ_ONLY_PROXY", "EXTERNAL"]:
        raise ValueError("schema evidence enum mismatch")
    return schema


def safe_source_commit(repo_root: Path) -> str:
    result = run_bounded(["/usr/bin/git", "rev-parse", "HEAD"], timeout_seconds=2, max_output_bytes=128, cwd=repo_root)
    value = result.stdout.decode("ascii", "ignore").strip()
    if result.return_code != 0 or not re.fullmatch(r"[0-9a-f]{40}", value):
        raise RuntimeError("source commit unavailable")
    return value


def validate_source_commit(repo_root: Path, source_commit: str) -> None:
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise ValueError("invalid source commit")
    exists = run_bounded(
        ["/usr/bin/git", "cat-file", "-e", f"{source_commit}^{{commit}}"],
        timeout_seconds=2,
        max_output_bytes=128,
        cwd=repo_root,
    )
    if exists.return_code != 0 or exists.timed_out:
        raise ValueError("source commit is missing")
    ancestor = run_bounded(
        ["/usr/bin/git", "merge-base", "--is-ancestor", source_commit, "HEAD"],
        timeout_seconds=2,
        max_output_bytes=128,
        cwd=repo_root,
    )
    if ancestor.return_code != 0 or ancestor.timed_out:
        raise ValueError("source commit is not an ancestor of HEAD")


def collect_current_mac(repo_root: Path, home: Path) -> dict:
    lanes: list[dict] = []
    architecture = platform.machine().lower()
    version_result = run_bounded(["/usr/bin/sw_vers", "-productVersion"], max_output_bytes=64)
    version = version_result.stdout.decode("ascii", "ignore").strip()
    platform_ok = architecture in {"arm64", "x86_64"} and bool(re.fullmatch(r"\d+(?:\.\d+){1,2}", version))
    lanes.append(lane("platform.current", "PASS" if platform_ok else "FAIL", "LIVE_CURRENT_MAC", "platform_identified" if platform_ok else "platform_unavailable", version_result.duration_ms))

    ssh_port = int(os.environ.get("CODECKS_M12_SSH_PORT", "22"))
    ssh_open, ssh_duration = tcp_probe(ssh_port)
    lanes.append(lane("ssh.tcp", "PASS" if ssh_open else "NOT_RUN", "LIVE_CURRENT_MAC", "ssh_tcp_reachable" if ssh_open else "ssh_server_unavailable", ssh_duration))

    user = os.environ.get("CODECKS_M12_SSH_USER") or os.environ.get("USER") or ""
    ssh_read_ok = False
    if ssh_open and user:
        read = run_bounded(ssh_base(user, ssh_port) + ["printf CODECKS_M12_READ_ONLY"], timeout_seconds=5, max_output_bytes=128)
        ssh_read_ok = read.return_code == 0 and read.stdout == b"CODECKS_M12_READ_ONLY" and not read.truncated
        lanes.append(lane("ssh.read_only_command", "PASS" if ssh_read_ok else "NOT_RUN", "LIVE_CURRENT_MAC", "ssh_read_only_verified" if ssh_read_ok else "ssh_auth_or_trust_unavailable", read.duration_ms))
        lanes.append(lane("ssh.bounded_command", "PASS" if ssh_read_ok else "NOT_RUN", "LIVE_CURRENT_MAC", "ssh_output_bound_verified" if ssh_read_ok else "ssh_auth_or_trust_unavailable", read.duration_ms))
    else:
        lanes.extend([
            lane("ssh.read_only_command", "NOT_RUN", "LIVE_CURRENT_MAC", "ssh_server_unavailable"),
            lane("ssh.bounded_command", "NOT_RUN", "LIVE_CURRENT_MAC", "ssh_server_unavailable"),
        ])

    port, config_ok = helper_port(home)
    helper_binary = home / "Library" / "Application Support" / "CodecksMacHelper" / "codecks-mac-helper"
    if config_ok and helper_binary.is_file() and os.access(helper_binary, os.X_OK):
        config_check = run_bounded([str(helper_binary), "check-config"], timeout_seconds=3, max_output_bytes=256)
        config_pass = config_check.return_code == 0 and not config_check.truncated
        lanes.append(lane("helper.config_health", "PASS" if config_pass else "FAIL", "LIVE_CURRENT_MAC", "helper_config_valid" if config_pass else "helper_config_invalid", config_check.duration_ms))
    else:
        lanes.append(lane("helper.config_health", "NOT_RUN", "LIVE_CURRENT_MAC", "helper_install_unavailable"))

    launch = run_bounded(["/bin/launchctl", "print", f"gui/{os.getuid()}/{HELPER_LABEL}"], timeout_seconds=3, max_output_bytes=8 * 1024)
    launch_ok = launch.return_code == 0 and b"state = running" in launch.stdout
    lanes.append(lane("helper.launchd_health", "PASS" if launch_ok else "NOT_RUN", "LIVE_CURRENT_MAC", "helper_service_running" if launch_ok else "helper_service_unavailable", launch.duration_ms))
    helper_open, helper_duration = tcp_probe(port)
    lanes.append(lane("helper.tcp", "PASS" if helper_open else "NOT_RUN", "LIVE_CURRENT_MAC", "helper_tcp_reachable" if helper_open else "helper_tcp_unavailable", helper_duration))

    # Local clipboard bytes are sent directly to /dev/null and never enter this process.
    # The authenticated SSH branch similarly redirects on the remote Mac.
    clipboard_argv = ssh_base(user, ssh_port) + ["pbpaste >/dev/null"] if ssh_read_ok else ["/usr/bin/pbpaste"]
    clipboard = run_discarded(clipboard_argv, timeout_seconds=3)
    clipboard_ok = clipboard.return_code == 0 and not clipboard.timed_out
    lanes.append(lane("clipboard.read_only_bridge", "PASS" if clipboard_ok else "NOT_RUN", "READ_ONLY_PROXY", "clipboard_read_discarded" if clipboard_ok else "clipboard_bridge_unavailable", clipboard.duration_ms))

    power = run_bounded(["/usr/bin/pmset", "-g", "assertions"], timeout_seconds=3, max_output_bytes=16 * 1024)
    power_ok = power.return_code == 0 and not power.truncated
    lanes.append(lane("power.read_only_proxy", "PASS" if power_ok else "NOT_RUN", "READ_ONLY_PROXY", "power_state_queried" if power_ok else "power_query_unavailable", power.duration_ms))

    with socket.socket() as reserved:
        reserved.bind(("127.0.0.1", 0))
        closed_port = reserved.getsockname()[1]
    refused, refused_duration = tcp_probe(closed_port, 0.25)
    lanes.append(lane("failure.network_refused", "PASS" if not refused else "FAIL", "INJECTED_FAILURE", "network_failure_classified" if not refused else "network_failure_injection_failed", refused_duration))

    timeout = run_bounded(["/usr/bin/python3", "-c", "import time; time.sleep(2)"], timeout_seconds=0.05, max_output_bytes=64)
    lanes.append(lane("failure.timeout", "PASS" if timeout.timed_out else "FAIL", "INJECTED_FAILURE", "timeout_classified" if timeout.timed_out else "timeout_injection_failed", timeout.duration_ms))
    oversized = run_bounded(["/usr/bin/python3", "-c", "import sys; sys.stdout.write('x'*70000)"], timeout_seconds=3, max_output_bytes=1024)
    lanes.append(lane("failure.output_bound", "PASS" if oversized.truncated and len(oversized.stdout) == 1024 else "FAIL", "INJECTED_FAILURE", "output_truncated" if oversized.truncated else "output_bound_failed", oversized.duration_ms))

    if ssh_open and user:
        auth = run_bounded(ssh_base(user, ssh_port, strict=False)[:-1] + ["-o", "PreferredAuthentications=none", f"{user}@127.0.0.1", "true"], timeout_seconds=5, max_output_bytes=512)
        auth_ok = is_auth_denial(auth)
        lanes.append(lane("failure.auth_denied", "PASS" if auth_ok else "FAIL", "INJECTED_FAILURE", "auth_denial_classified" if auth_ok else "auth_denial_injection_failed", auth.duration_ms))
        with tempfile.TemporaryDirectory(prefix="codecks-m12-") as temp:
            known_hosts = Path(temp) / "known_hosts"
            # Deterministic synthetic public key. No private key is created and no
            # production known-hosts file is read or changed.
            blob = struct.pack(">I", 11) + b"ssh-ed25519" + struct.pack(">I", 32) + (b"\x01" * 32)
            wrong_public_key = base64.b64encode(blob).decode("ascii")
            known_hosts.write_text(f"[127.0.0.1]:{ssh_port} ssh-ed25519 {wrong_public_key}\n", encoding="ascii")
            mismatch = run_bounded(ssh_base(user, ssh_port)[:-1] + ["-o", f"UserKnownHostsFile={known_hosts}", f"{user}@127.0.0.1", "true"], timeout_seconds=5, max_output_bytes=1024)
            mismatch_ok = is_host_key_mismatch(mismatch)
            lanes.append(lane("failure.host_key_mismatch", "PASS" if mismatch_ok else "FAIL", "INJECTED_FAILURE", "host_key_mismatch_classified" if mismatch_ok else "host_key_mismatch_injection_failed", mismatch.duration_ms))
    else:
        lanes.extend([
            lane("failure.auth_denied", "NOT_RUN", "INJECTED_FAILURE", "ssh_server_unavailable"),
            lane("failure.host_key_mismatch", "NOT_RUN", "INJECTED_FAILURE", "ssh_server_unavailable"),
        ])

    missing = run_bounded(["/usr/bin/env", "codecks-m12-tool-must-not-exist"], timeout_seconds=1, max_output_bytes=128)
    lanes.append(lane("failure.tool_missing", "PASS" if missing.return_code == 127 else "FAIL", "INJECTED_FAILURE", "tool_missing_classified" if missing.return_code == 127 else "tool_missing_injection_failed", missing.duration_ms))
    delays = [1_000, 2_000, 5_000, 10_000, 30_000]
    backoff_ok = delays == sorted(delays) and delays[-1] <= 30_000 and all(value > 0 for value in delays)
    lanes.append(lane("failure.backoff", "PASS" if backoff_ok else "FAIL", "INJECTED_FAILURE", "backoff_bounded" if backoff_ok else "backoff_invalid"))

    for lane_id, code in (
        ("mutation.sleep_wake", "primary_mac_mutation_not_approved"),
        ("mutation.helper_restart", "primary_service_mutation_not_approved"),
        ("mutation.account", "account_mutation_not_approved"),
        ("mutation.host_key", "host_key_mutation_not_approved"),
        ("mutation.permissions", "permission_mutation_not_approved"),
        ("external.intel", "intel_hardware_unavailable"),
        ("external.other_macos", "additional_macos_unavailable"),
        ("external.physical_dex", "physical_dex_external"),
        ("external.physical_hid", "physical_hid_external"),
    ):
        evidence = "EXTERNAL" if lane_id.startswith("external.") else "READ_ONLY_PROXY"
        lanes.append(lane(lane_id, "NOT_RUN", evidence, code))

    ordered = {item["id"]: item for item in lanes}
    if set(ordered) != set(LANE_IDS) or len(ordered) != len(lanes):
        raise RuntimeError("lane matrix incomplete or duplicated")
    lanes = [ordered[lane_id] for lane_id in LANE_IDS]
    counts = {status: sum(item["status"] == status for item in lanes) for status in ("PASS", "FAIL", "NOT_RUN")}
    receipt = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAtUtc": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "sourceCommit": safe_source_commit(repo_root),
        "bindings": expected_bindings(repo_root),
        "environment": {
            "platform": "macOS",
            "architecture": architecture,
            "macosVersion": version,
            "evidenceScope": "CURRENT_MAC_ONLY",
        },
        "safety": {
            "sleepStateChanged": False,
            "serviceRestarted": False,
            "accountChanged": False,
            "authorizationChanged": False,
            "keyMaterialChanged": False,
            "hidStateChanged": False,
            "clipboardContentRecorded": False,
        },
        "lanes": lanes,
        "summary": {
            "result": "PASS_WITH_NOT_RUN" if counts["FAIL"] == 0 and counts["NOT_RUN"] else ("PASS" if counts["FAIL"] == 0 else "FAIL"),
            "pass": counts["PASS"],
            "fail": counts["FAIL"],
            "notRun": counts["NOT_RUN"],
        },
    }
    validate_receipt(receipt, repo_root=repo_root)
    return receipt


def _walk(value: object, keys: Iterable[str] = ()) -> Iterable[tuple[tuple[str, ...], object]]:
    yield tuple(keys), value
    if isinstance(value, dict):
        for key, item in value.items():
            yield from _walk(item, (*keys, str(key)))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from _walk(item, (*keys, str(index)))


def validate_privacy(receipt: object) -> None:
    for path, value in _walk(receipt):
        if path and PROHIBITED_RECEIPT_KEYS.search(path[-1]) and path[-1] not in {"accountChanged", "keyMaterialChanged", "clipboardContentRecorded"}:
            raise ValueError(f"prohibited receipt key: {'.'.join(path)}")
        if isinstance(value, str) and any(pattern.search(value) for pattern in PROHIBITED_RECEIPT_VALUES):
            raise ValueError(f"sensitive receipt value: {'.'.join(path)}")


def validate_receipt(receipt: dict, *, repo_root: Path | None = None) -> None:
    validate_privacy(receipt)
    top = {"schemaVersion", "generatedAtUtc", "sourceCommit", "bindings", "environment", "safety", "lanes", "summary"}
    if set(receipt) != top or receipt.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("receipt top-level schema mismatch")
    if not re.fullmatch(r"[0-9a-f]{40}", receipt.get("sourceCommit", "")):
        raise ValueError("invalid source commit")
    if repo_root is not None:
        validate_source_commit(repo_root, receipt["sourceCommit"])
    generated = receipt.get("generatedAtUtc")
    if not isinstance(generated, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", generated):
        raise ValueError("invalid generation time")
    try:
        parsed_generated = datetime.strptime(generated, "%Y-%m-%dT%H:%M:%SZ")
    except (TypeError, ValueError) as error:
        raise ValueError("invalid generation calendar time") from error
    if parsed_generated.strftime("%Y-%m-%dT%H:%M:%SZ") != generated:
        raise ValueError("noncanonical generation time")
    bindings = receipt.get("bindings")
    required_bindings = {"evidenceParent", "evidenceParentSha256", "harnessSha256", "schemaSha256"}
    if not isinstance(bindings, dict) or set(bindings) != required_bindings:
        raise ValueError("invalid bindings")
    if bindings.get("evidenceParent") != EVIDENCE_PARENT:
        raise ValueError("evidence parent mismatch")
    if any(not isinstance(bindings.get(key), str) or not re.fullmatch(r"[0-9a-f]{64}", bindings[key]) for key in required_bindings - {"evidenceParent"}):
        raise ValueError("invalid binding digest")
    if repo_root is not None:
        schema = load_and_validate_schema(repo_root)
        validate_schema_instance(schema, receipt)
        if bindings != expected_bindings(repo_root):
            raise ValueError("evidence binding mismatch")
    environment = receipt.get("environment")
    if not isinstance(environment, dict) or set(environment) != {"platform", "architecture", "macosVersion", "evidenceScope"}:
        raise ValueError("invalid environment")
    if environment.get("platform") != "macOS" or environment.get("architecture") not in {"arm64", "x86_64"} or environment.get("evidenceScope") != "CURRENT_MAC_ONLY":
        raise ValueError("environment enum mismatch")
    if not isinstance(environment.get("macosVersion"), str) or not re.fullmatch(r"\d+(?:\.\d+){1,2}", environment["macosVersion"]):
        raise ValueError("invalid macOS version")
    safety = receipt.get("safety")
    required_safety = {"sleepStateChanged", "serviceRestarted", "accountChanged", "authorizationChanged", "keyMaterialChanged", "hidStateChanged", "clipboardContentRecorded"}
    if not isinstance(safety, dict) or set(safety) != required_safety or any(value is not False for value in safety.values()):
        raise ValueError("unsafe or incomplete safety receipt")
    lanes = receipt.get("lanes")
    if not isinstance(lanes, list) or [item.get("id") for item in lanes if isinstance(item, dict)] != list(LANE_IDS):
        raise ValueError("lane order or identity mismatch")
    for item in lanes:
        if not isinstance(item, dict) or set(item) != {"id", "status", "evidence", "code", "durationMs"}:
            raise ValueError("lane schema mismatch")
        lane(item["id"], item["status"], item["evidence"], item["code"], item["durationMs"])
    counts = {status: sum(item["status"] == status for item in lanes) for status in ("PASS", "FAIL", "NOT_RUN")}
    summary = receipt.get("summary")
    if not isinstance(summary, dict) or set(summary) != {"result", "pass", "fail", "notRun"}:
        raise ValueError("summary schema mismatch")
    if any(isinstance(summary.get(key), bool) or not isinstance(summary.get(key), int) or not 0 <= summary[key] <= len(LANE_IDS) for key in ("pass", "fail", "notRun")):
        raise ValueError("summary count type mismatch")
    if (summary["pass"], summary["fail"], summary["notRun"]) != (counts["PASS"], counts["FAIL"], counts["NOT_RUN"]):
        raise ValueError("summary count mismatch")
    expected_result = "PASS_WITH_NOT_RUN" if counts["FAIL"] == 0 and counts["NOT_RUN"] else ("PASS" if counts["FAIL"] == 0 else "FAIL")
    if summary["result"] != expected_result:
        raise ValueError("summary result mismatch")


def write_receipt(receipt: dict, destination: Path, *, repo_root: Path | None = None) -> None:
    validate_receipt(receipt, repo_root=repo_root)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    temporary.write_text(json.dumps(receipt, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    os.replace(temporary, destination)
