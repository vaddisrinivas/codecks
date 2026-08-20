#!/usr/bin/env python3
"""Three-phase M09D controller-lifecycle evidence collector."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import subprocess
import tempfile
import time
import unicodedata
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
BASE_COMMIT = "08655e7b3b90ec4d5c1cd41bbf05fee0a70528f2"
CLASS_NAME = "io.codecks.m09d.M09DControllerLifecycleTest"
METHODS = (
    "aiCreateSurvivesRepositoryBackedControllerRecreationProxy",
    "aiDeleteIsNonUndoableAcrossRepositoryBackedControllerRecreationProxy",
    "aiRefinePreservesSourceAcrossRepositoryBackedControllerRecreationProxy",
    "aiSaveOnlySurvivesRepositoryBackedControllerRecreationProxy",
    "aiTestSurvivesRepositoryBackedControllerRecreationProxy",
    "compositionPlacementBridgeDrivesHomePreferredSlotAndExactSavePath",
    "homeEditAssignPersistsExactSavePath",
    "homeMoveUndoRestoresExactLayoutAndSavePath",
    "homeReassignClearsPriorUndoPolicy",
    "homeRemoveUndoRestoresExactActionAndSavePath",
)
COMMAND = (
    "$GRADLE_DISTRIBUTION_ROOT/bin/gradle",
    ":app:validateReleaseSurface", ":app:testOssReleaseUnitTest", "--tests", CLASS_NAME,
    "--rerun-tasks", "--no-build-cache", "--no-configuration-cache", "--no-daemon", "--offline",
)
C1_PATHS = frozenset({
    "docs/evidence/M09D_CONTROLLER_LIFECYCLE.md",
    "docs/evidence/M09D_CONTROLLER_LIFECYCLE_TODO.md",
    "tools/evidence/collect_m09d_controller_lifecycle.py",
    "tools/evidence/schemas/codecks-m09d-controller-lifecycle-v1.schema.json",
    "tools/evidence/test_m09d_controller_lifecycle.py",
    "tools/evidence/validate_m09d_controller_lifecycle.py",
})
EVIDENCE_DIR = Path("tasks/test-evidence/m09d-controller-lifecycle")
RAW_JUNIT_XML = EVIDENCE_DIR / "junit.xml"
SANITIZED_LOG = EVIDENCE_DIR / "gradle.log"
ARTIFACT_MANIFEST = EVIDENCE_DIR / "artifacts.json"
RECEIPT = EVIDENCE_DIR / "receipt.json"
C2_PATHS = frozenset({RAW_JUNIT_XML.as_posix(), SANITIZED_LOG.as_posix(), ARTIFACT_MANIFEST.as_posix()})
C3_PATHS = frozenset({RECEIPT.as_posix()})
RESULT_XML = Path(f"app/build/test-results/testOssReleaseUnitTest/TEST-{CLASS_NAME}.xml")
PRODUCTION_CLASSES = Path("app/build/intermediates/built_in_kotlinc/ossRelease/compileOssReleaseKotlin/classes")
TEST_CLASSES = Path("app/build/intermediates/built_in_kotlinc/ossReleaseUnitTest/compileOssReleaseUnitTestKotlin/classes")
SOURCE_BINDINGS = (
    ("app/src/main/java/io/codecks/AppCompositionRoot.kt", "fun routeAiArtifactPlacement", "placeOnDeck = homeViewModel::requestArtifactPlacement"),
    ("app/src/main/java/io/codecks/ui/home/HomeViewModel.kt", "fun undoLastDeckEdit", "fun placePendingDeckPlacement"),
    ("app/src/main/java/io/codecks/ui/ai/AiProviderSettingsController.kt", "fun deleteArtifact", "fun startRefinement"),
)
MAX_XML_BYTES = 2 * 1024 * 1024
MAX_TREE_FILES = 30_000
MAX_TREE_BYTES = 512 * 1024 * 1024
MAX_DEPENDENCY_CACHE_BYTES = 6 * 1024 * 1024 * 1024
MAX_GRADLE_HOME_BYTES = 2 * 1024 * 1024 * 1024
MAX_LOG_BYTES = 256 * 1024
MAX_ARTIFACT_MANIFEST_BYTES = 1024 * 1024
MAX_RECEIPT_BYTES = 1024 * 1024
MAX_ZIP_CENTRAL_BYTES = 16 * 1024 * 1024
GRADLE_DISTRIBUTION_SHA256 = "2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb"
CHECKSUM_RECEIPT_SHA256 = "bf1e620f915bcde7c1c09738daecfe332fb59a6ca6b550813c8a41e72cb23782"
CHECKSUM_RECEIPT_URL = "https://gradle.org/release-checksums/"
GRADLE_PROPERTIES_SHA256 = "85ded62c7cf18436166cbd09f5e6f372a6d6b8e9568591bf05401783453f1b37"
ANDROID_HOME = "/Users/srinivasvaddi/Library/Android/sdk"
JAVA_HOME = "/Users/srinivasvaddi/Library/Java/JavaVirtualMachines/openjdk-20.0.2/Contents/Home"
ISOLATED_GRADLE_HOME = "/tmp/codecks-m09d-controller-gradle-home"
GRADLE_DISTRIBUTION_ZIP = Path(ISOLATED_GRADLE_HOME) / "provenance/gradle-9.4.1-bin.zip"
CHECKSUM_RECEIPT = Path(ISOLATED_GRADLE_HOME) / "provenance/gradle-release-checksums.html"
GRADLE_DISTRIBUTION_ROOT = Path(ISOLATED_GRADLE_HOME) / "wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj/gradle-9.4.1"
GRADLE_LAUNCHER = GRADLE_DISTRIBUTION_ROOT / "lib/gradle-launcher-9.4.1.jar"
GRADLE_READ_ONLY_CACHE = Path("/Users/srinivasvaddi/.gradle/caches")
GRADLE_READ_ONLY_DEPENDENCY_CACHE = GRADLE_READ_ONLY_CACHE / "modules-2/files-2.1"
GRADLE_PROPERTIES = Path(ISOLATED_GRADLE_HOME) / "gradle.properties"
ACTUAL_EXEC_ENV = {
    "ANDROID_HOME": ANDROID_HOME,
    "JAVA_HOME": JAVA_HOME,
    "HOME": "/Users/srinivasvaddi",
    "GRADLE_USER_HOME": ISOLATED_GRADLE_HOME,
    "GRADLE_RO_DEP_CACHE": GRADLE_READ_ONLY_CACHE.as_posix(),
    "PATH": f"{JAVA_HOME}/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
    "LANG": "C.UTF-8",
    "LC_ALL": "C.UTF-8",
    "GRADLE_OPTS": "",
    "JAVA_TOOL_OPTIONS": "",
    "_JAVA_OPTIONS": "",
}
EXEC_ENV = {
    "ANDROID_HOME": "$ANDROID_HOME", "JAVA_HOME": "$JAVA_HOME", "HOME": "$HOME",
    "GRADLE_USER_HOME": "$GRADLE_USER_HOME", "GRADLE_RO_DEP_CACHE": "$GRADLE_RO_DEP_CACHE",
    "PATH": "$JAVA_HOME/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
    "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8", "GRADLE_OPTS": "", "JAVA_TOOL_OPTIONS": "", "_JAVA_OPTIONS": "",
}
GRADLE_BINARY = GRADLE_DISTRIBUTION_ROOT / "bin/gradle"
EXEC_COMMAND = tuple(
    GRADLE_BINARY.as_posix() if item == "$GRADLE_DISTRIBUTION_ROOT/bin/gradle" else
    item
    for item in COMMAND
)
GRADLE_VERSION_COMMAND = (GRADLE_BINARY.as_posix(), "--version", "--no-daemon")
JDK_VERSION_COMMAND = ("java", "-version")
WRAPPER_PROPERTIES = ROOT / "gradle/wrapper/gradle-wrapper.properties"
JAVA_CRITICAL_PATHS = tuple(
    path for path in (
        Path(JAVA_HOME) / "bin/java", Path(JAVA_HOME) / "release",
        Path(JAVA_HOME) / "lib/modules", Path(JAVA_HOME) / "lib/jrt-fs.jar",
    ) if path.is_file()
)
FORBIDDEN_INIT_FILES = (
    Path(ISOLATED_GRADLE_HOME) / "init.gradle", Path(ISOLATED_GRADLE_HOME) / "init.gradle.kts",
    Path("/Users/srinivasvaddi/.gradle/init.gradle"), Path("/Users/srinivasvaddi/.gradle/init.gradle.kts"),
    Path("/etc/gradle/init.gradle"), Path("/etc/gradle/init.gradle.kts"),
)
INIT_DIRS = (
    Path(ISOLATED_GRADLE_HOME) / "init.d", Path("/Users/srinivasvaddi/.gradle/init.d"),
    Path("/etc/gradle/init.d"), GRADLE_DISTRIBUTION_ROOT / "init.d",
)


def sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha_path(path: Path) -> str:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(path, flags)
    except OSError as exc:
        raise ValueError(f"regular file required: {path}") from exc
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode):
            raise ValueError(f"regular file required: {path}")
        digest = hashlib.sha256()
        while True:
            chunk = os.read(fd, 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
        after = os.fstat(fd)
        if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) != (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns):
            raise ValueError(f"file changed while hashing: {path}")
        return digest.hexdigest()
    finally:
        os.close(fd)


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, text=True, capture_output=True).stdout.strip()


def changed_paths(commit: str) -> frozenset[str]:
    return frozenset(filter(None, git("diff-tree", "--no-commit-id", "--name-only", "-r", commit).splitlines()))


def require_clean_status() -> None:
    status = subprocess.run(["git", "status", "--porcelain=v1", "-z"], cwd=ROOT, check=True, capture_output=True).stdout
    validate_status_bytes(status)


def validate_status_bytes(status: bytes) -> None:
    if status:
        raise ValueError("worktree must be exactly clean")


def require_worktree_matches_commit(commit: str, paths: frozenset[str]) -> None:
    for path in sorted(paths):
        working = ROOT / path
        if working.is_symlink() or not working.is_file():
            raise ValueError(f"working file missing or symlinked: {path}")
        committed = subprocess.run(
            ["git", "show", f"{commit}:{path}"], cwd=ROOT, check=True, capture_output=True,
        ).stdout
        validate_working_bytes(path, working.read_bytes(), committed)


def validate_working_bytes(path: str, working: bytes, committed: bytes) -> None:
    if working != committed:
        raise ValueError(f"working file differs from committed blob: {path}")


def closed_environment() -> dict[str, str]:
    return dict(ACTUAL_EXEC_ENV)


def tokenize_private_path(value: str) -> str:
    replacements = (
        (GRADLE_READ_ONLY_DEPENDENCY_CACHE.as_posix(), "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1"),
        (GRADLE_READ_ONLY_CACHE.as_posix(), "$GRADLE_RO_DEP_CACHE"),
        (ISOLATED_GRADLE_HOME, "$GRADLE_USER_HOME"), (ANDROID_HOME, "$ANDROID_HOME"),
        (JAVA_HOME, "$JAVA_HOME"), (str(ROOT), "$REPO_ROOT"), (ACTUAL_EXEC_ENV["HOME"], "$HOME"),
    )
    for actual, token in replacements:
        value = value.replace(actual, token)
    return value


def validate_private_free(value: object) -> None:
    if isinstance(value, dict):
        for key, item in value.items():
            validate_private_free(key); validate_private_free(item)
    elif isinstance(value, list):
        for item in value:
            validate_private_free(item)
    elif isinstance(value, str):
        if any(token in value for token in ("/Users/", "/private/", "/tmp/", str(ROOT))):
            raise ValueError("private filesystem path leaked into evidence")


def validate_private_bytes(data: bytes) -> None:
    text = data.decode("utf-8", errors="strict")
    if any(token in text for token in ("/Users/", "/private/", "/tmp/", str(ROOT))):
        raise ValueError("private filesystem path leaked into durable bytes")
    if re.search(r"(?i)(?:api[_-]?key|access[_-]?token|password|secret)\s*[:=]\s*[^\s<]+", text):
        raise ValueError("secret-like value leaked into durable bytes")


def validate_xml_privacy(root: ET.Element) -> None:
    secret_name = re.compile(r"(?i)(?:api[_-]?key|access[_-]?token|password|secret)")
    for element in root.iter():
        values = [element.tag, element.text or "", element.tail or ""]
        for name, value in element.attrib.items():
            if secret_name.search(name) or secret_name.search(value):
                raise ValueError("secret-like XML attribute name/value leaked")
            values.extend((name, value))
        for value in values:
            validate_private_bytes(value.encode("utf-8"))


def validate_topology(
    source_commit: str,
    source_parent: str,
    source_paths: frozenset[str],
    artifact_commit: str | None = None,
    artifact_parent: str | None = None,
    artifact_paths: frozenset[str] | None = None,
    receipt_parent: str | None = None,
    receipt_paths: frozenset[str] | None = None,
) -> None:
    if source_parent != BASE_COMMIT or source_paths != C1_PATHS:
        raise ValueError("stale or substituted C1 topology")
    if artifact_parent is not None and (artifact_parent != source_commit or artifact_commit in {None, source_commit} or artifact_paths != C2_PATHS):
        raise ValueError("stale, swapped, or substituted C2 topology")
    if receipt_parent is not None and (receipt_parent != artifact_commit or receipt_paths != C3_PATHS):
        raise ValueError("stale, swapped, or substituted C3 topology")


def validate_source_binding_values(values: dict[str, str]) -> None:
    for path, *tokens in SOURCE_BINDINGS:
        data = values.get(path, "")
        for token in tokens:
            if token not in data:
                raise ValueError(f"missing source binding {token!r} in {path}")


def validate_no_shrink(build: str) -> None:
    section = re.search(r"(?ms)^\s*buildTypes\s*\{.*?^\s*release\s*\{(?P<body>.*?)^\s{8}\}", build)
    body = section.group("body") if section else ""
    minify = re.findall(r"(?m)^\s*isMinifyEnabled\s*=\s*(true|false)\s*$", body)
    shrink = re.findall(r"(?m)^\s*isShrinkResources\s*=\s*(true|false)\s*$", body)
    if minify != ["false"] or shrink != ["false"]:
        raise ValueError("release no-shrink contract missing")


def require_source_bindings() -> list[dict]:
    values = {path: (ROOT / path).read_text(encoding="utf-8") for path, *_ in SOURCE_BINDINGS}
    validate_source_binding_values(values)
    result = []
    for path, *tokens in SOURCE_BINDINGS:
        result.append({"path": path, "sha256": sha_path(ROOT / path)})
    build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    validate_no_shrink(build)
    return result


def parse_junit(data: bytes) -> list[str]:
    upper = data.upper()
    declaration = b'<?xml version="1.0" encoding="UTF-8"?>\n'
    lexical = data[len(declaration):] if data.startswith(declaration) else data
    if len(data) > MAX_XML_BYTES or b"<!DOCTYPE" in upper or b"<!ENTITY" in upper or b"<!--" in data or b"<?" in lexical:
        raise ValueError("unsafe JUnit XML")
    root = ET.fromstring(data)
    validate_xml_privacy(root)
    suite_attributes = {"name", "tests", "skipped", "failures", "errors", "timestamp", "hostname", "time"}
    if root.tag != "testsuite" or set(root.attrib) != suite_attributes or root.attrib.get("name") != CLASS_NAME:
        raise ValueError("wrong JUnit class")
    if root.text and root.text.strip():
        raise ValueError("unexpected JUnit suite text")
    timestamp = root.attrib.get("timestamp", "")
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})", timestamp) is None:
        raise ValueError("JUnit timestamp missing or malformed")
    counts = {key: int(root.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
    if counts != {"tests": 10, "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError(f"wrong JUnit counts: {counts}")
    if not root.attrib["hostname"] or re.fullmatch(r"\d+(?:\.\d+)?", root.attrib["time"]) is None:
        raise ValueError("JUnit hostname/time malformed")
    children = list(root)
    if not children or children[0].tag != "properties" or sum(child.tag == "properties" for child in children) != 1:
        raise ValueError("exactly one leading empty JUnit properties element required")
    properties = children[0]
    if properties.attrib or list(properties) or (properties.text and properties.text.strip()) or (properties.tail and properties.tail.strip()):
        raise ValueError("JUnit properties element must be exactly empty")
    if len(children) < 3 or [child.tag for child in children[-2:]] != ["system-out", "system-err"] or sum(child.tag == "system-out" for child in children) != 1 or sum(child.tag == "system-err" for child in children) != 1:
        raise ValueError("exactly one trailing empty system-out/system-err required")
    for stream in children[-2:]:
        if stream.attrib or list(stream) or (stream.text and stream.text.strip()) or (stream.tail and stream.tail.strip()):
            raise ValueError("JUnit system output/error must be exactly empty")
    cases = children[1:-2]
    if any(case.tag != "testcase" for case in cases):
        raise ValueError("unexpected JUnit suite child")
    names = sorted(case.attrib.get("name", "") for case in cases)
    if names != sorted(METHODS) or any(set(case.attrib) != {"name", "classname", "time"} or case.attrib.get("classname") != CLASS_NAME or re.fullmatch(r"\d+(?:\.\d+)?", case.attrib.get("time", "")) is None for case in cases):
        raise ValueError("wrong JUnit class or method set")
    if any(list(case) or (case.text and case.text.strip()) or (case.tail and case.tail.strip()) for case in cases):
        raise ValueError("unexpected JUnit testcase content")
    return names


def junit_timestamp_ns(data: bytes) -> int:
    root = ET.fromstring(data)
    value = root.attrib.get("timestamp", "")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("JUnit timestamp missing or malformed") from exc
    if parsed.tzinfo is None:
        raise ValueError("JUnit timestamp must include UTC offset")
    parsed = parsed.astimezone(timezone.utc)
    return int(parsed.timestamp() * 1_000_000_000)


def remove_focused_result(path: Path = RESULT_XML) -> None:
    target = ROOT / path
    if target.exists() or target.is_symlink():
        if target.is_symlink() or not target.is_file():
            raise ValueError("focused JUnit result is not a regular file")
        target.unlink()
    if target.exists() or target.is_symlink():
        raise ValueError("focused JUnit result must be absent before execution")


def bind_fresh_focused_result(start_ns: int, end_ns: int, path: Path = RESULT_XML) -> tuple[bytes, dict]:
    target = ROOT / path
    if target.is_symlink() or not target.is_file():
        raise ValueError("focused JUnit result was not newly created")
    metadata = target.stat()
    if not (start_ns <= metadata.st_mtime_ns <= end_ns):
        raise ValueError("focused JUnit result mtime is outside execution interval")
    raw = read_bounded_file(target, MAX_XML_BYTES)
    parse_junit(raw)
    suite_timestamp_ns = junit_timestamp_ns(raw)
    if not (start_ns <= suite_timestamp_ns <= end_ns):
        raise ValueError("JUnit suite timestamp is outside execution interval")
    birthtime = getattr(metadata, "st_birthtime", None)
    birthtime_ns = int(birthtime * 1_000_000_000) if birthtime is not None else None
    proof = "BIRTHTIME_IN_INTERVAL" if birthtime_ns is not None and start_ns <= birthtime_ns <= end_ns else "NOT_PROVEN"
    if birthtime_ns is not None and proof != "BIRTHTIME_IN_INTERVAL":
        raise ValueError("focused JUnit birthtime is outside execution interval")
    return raw, {
        "path": RAW_JUNIT_XML.as_posix(), "sha256": sha_bytes(raw),
        "executionStartNs": start_ns, "executionEndNs": end_ns, "sourceMtimeNs": metadata.st_mtime_ns,
        "sourceBirthtimeNs": birthtime_ns, "sourceInode": metadata.st_ino, "newFileProof": proof,
        "suiteTimestampNs": suite_timestamp_ns,
    }


def validate_raw_junit_binding(binding: object, raw: bytes) -> None:
    required = {"path", "sha256", "executionStartNs", "executionEndNs", "sourceMtimeNs", "sourceBirthtimeNs", "sourceInode", "newFileProof", "suiteTimestampNs"}
    if not isinstance(binding, dict) or set(binding) != required or binding["path"] != RAW_JUNIT_XML.as_posix():
        raise ValueError("raw focused JUnit binding substituted")
    start, end, mtime, suite = binding["executionStartNs"], binding["executionEndNs"], binding["sourceMtimeNs"], binding["suiteTimestampNs"]
    if not all(type(value) is int and value > 0 for value in (start, end, mtime, suite, binding["sourceInode"])) or not (start <= mtime <= end and start <= suite <= end):
        raise ValueError("raw focused JUnit interval substituted")
    birth = binding["sourceBirthtimeNs"]
    if birth is None:
        if binding["newFileProof"] != "NOT_PROVEN":
            raise ValueError("raw focused JUnit new-file proof substituted")
    elif type(birth) is not int or not start <= birth <= end or binding["newFileProof"] != "BIRTHTIME_IN_INTERVAL":
        raise ValueError("raw focused JUnit birthtime proof substituted")
    if not valid_sha(binding["sha256"]) or sha_bytes(raw) != binding["sha256"]:
        raise ValueError("raw focused JUnit hash substituted")
    validate_private_bytes(raw)
    parse_junit(raw)
    if junit_timestamp_ns(raw) != suite:
        raise ValueError("raw focused JUnit suite timestamp substituted")


def sanitize_log(data: bytes) -> bytes:
    if len(data) > 16 * 1024 * 1024:
        raise ValueError("raw Gradle log exceeds bound")
    text = data.decode("utf-8", errors="strict")
    lines = tuple(line.strip() for line in text.splitlines())
    required_tasks = (
        "> Task :app:validateReleaseSurface",
        "> Task :app:testOssReleaseUnitTest",
    )
    focused_tasks = tuple(line for line in lines if any(line.startswith(task) for task in required_tasks))
    if focused_tasks != required_tasks:
        raise ValueError("unexpected, reordered, or non-executed Gradle task marker")
    if sum(line.startswith("BUILD SUCCESSFUL") for line in lines) != 1:
        raise ValueError("Gradle log lacks success marker")
    actionable = "50 actionable tasks: 50 executed"
    if lines.count(actionable) != 1 or sum(re.fullmatch(r"\d+ actionable tasks:.*", line) is not None for line in lines) != 1:
        raise ValueError("exact release/test task execution markers missing")
    header = "COMMAND\t" + json.dumps(list(COMMAND), separators=(",", ":")) + "\n"
    canonical_lines = (*required_tasks, actionable, "BUILD SUCCESSFUL")
    result = (header + "\n".join(canonical_lines) + "\n").encode("utf-8")
    if len(result) > MAX_LOG_BYTES or b"/Users/" in result or b"/private/" in result or b"/tmp/" in result:
        raise ValueError("sanitized Gradle log privacy/size bound failed")
    validate_private_bytes(result)
    return result


def validate_command_header(data: bytes) -> None:
    if len(data) > MAX_LOG_BYTES or sanitize_log(data) != data:
        raise ValueError("command or success marker substitution")


def sanitize_tool_output(data: bytes) -> str:
    text = data.decode("utf-8", errors="strict")
    for actual, token in (
        (str(ROOT), "$REPO_ROOT"), (ANDROID_HOME, "$ANDROID_HOME"),
        (JAVA_HOME, "$JAVA_HOME"), (ACTUAL_EXEC_ENV["HOME"], "$HOME"),
        (ISOLATED_GRADLE_HOME, "$GRADLE_USER_HOME"),
        (GRADLE_READ_ONLY_CACHE.as_posix(), "$GRADLE_RO_DEP_CACHE"),
    ):
        text = text.replace(actual, token)
    normalized = "\n".join(line.rstrip() for line in text.replace("\r\n", "\n").replace("\r", "\n").splitlines()).strip()
    if not normalized:
        raise ValueError("empty tool version output")
    return normalized


def _scan_tree(directory: Path, root_label: str, max_bytes: int, include_manifest: bool, max_files: int = MAX_TREE_FILES) -> dict:
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError(f"compiled tree missing: {directory}")
    files: list[Path] = []
    total = 0
    for path in directory.rglob("*"):
        if path.is_symlink():
            raise ValueError("compiled tree symlink found")
        if not path.is_file():
            continue
        files.append(path)
        total += path.stat().st_size
        if len(files) > max_files or total > max_bytes:
            raise ValueError("compiled tree count/byte bound exceeded")
    files.sort(key=lambda path: path.relative_to(directory).as_posix())
    if not files:
        raise ValueError("compiled tree byte bound exceeded or tree empty")
    manifest = [
        {"path": path.relative_to(directory).as_posix(), "bytes": path.stat().st_size, "sha256": sha_path(path)}
        for path in files
    ]
    result = {"root": root_label, "files": len(files), "bytes": total, "sha256": manifest_sha(manifest)}
    if include_manifest:
        result["manifest"] = manifest
    return result


def tree_digest(directory: Path, max_bytes: int = MAX_TREE_BYTES, max_files: int = MAX_TREE_FILES) -> dict:
    value = _scan_tree(directory, directory.as_posix(), max_bytes, False, max_files=max_files)
    return {"root": value["root"], "files": value["files"], "bytes": value["bytes"], "digest": value["sha256"]}


def manifest_sha(manifest: list[dict]) -> str:
    return sha_bytes(json.dumps(manifest, separators=(",", ":"), sort_keys=True).encode())


def tree_manifest(directory: Path, root_label: str, max_bytes: int = MAX_TREE_BYTES, max_files: int = MAX_TREE_FILES) -> dict:
    return _scan_tree(directory, root_label, max_bytes, True, max_files=max_files)


def preparse_zip_directory(path: Path, max_files: int = MAX_TREE_FILES, max_central_bytes: int = MAX_ZIP_CENTRAL_BYTES) -> None:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags)
    try:
        metadata = os.fstat(fd)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_size < 22:
            raise ValueError("regular ZIP with EOCD required")
        tail_size = min(metadata.st_size, 65_557)
        os.lseek(fd, metadata.st_size - tail_size, os.SEEK_SET)
        tail = os.read(fd, tail_size)
        offset = tail.rfind(b"PK\x05\x06")
        if offset < 0 or len(tail) - offset < 22:
            raise ValueError("ZIP EOCD missing")
        disk, central_disk, disk_entries, total_entries, central_size, central_offset, comment_size = struct.unpack_from("<HHHHIIH", tail, offset + 4)
        if offset + 22 + comment_size != len(tail) or disk != 0 or central_disk != 0 or disk_entries != total_entries:
            raise ValueError("ZIP EOCD topology substituted")
        if total_entries in {0, 0xFFFF} or total_entries > max_files or central_size in {0xFFFFFFFF} or central_size > max_central_bytes:
            raise ValueError("ZIP central-directory count/size cap failed")
        if central_offset + central_size > metadata.st_size - (22 + comment_size):
            raise ValueError("ZIP central-directory range substituted")
    finally:
        os.close(fd)


def zip_manifest(path: Path, max_files: int = MAX_TREE_FILES, max_bytes: int = MAX_TREE_BYTES) -> dict:
    entries: list[dict] = []
    seen: dict[str, str] = {}
    kinds: dict[str, str] = {}
    total = 0
    root_directory_seen = False
    preparse_zip_directory(path, max_files=max_files)
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        if len(infos) > max_files or sum(info.file_size for info in infos) > max_bytes:
            raise ValueError("Gradle ZIP declared count/byte bound failed")
        for info in infos:
            name = info.filename
            if not name or "\\" in name or "\x00" in name or unicodedata.normalize("NFC", name) != name:
                raise ValueError("unsafe Gradle ZIP entry encoding")
            parts = Path(name).parts
            mode = (info.external_attr >> 16) & 0o170000
            is_directory = info.is_dir()
            if parts == ("gradle-9.4.1",) and is_directory:
                if root_directory_seen or mode not in {0, stat.S_IFDIR}:
                    raise ValueError("duplicate or non-directory Gradle ZIP root")
                root_directory_seen = True
                continue
            if len(parts) < 2 or name.startswith("/") or parts[0] != "gradle-9.4.1" or any(part in {"", ".", ".."} for part in parts):
                raise ValueError("unsafe Gradle ZIP entry path")
            relative = Path(*parts[1:]).as_posix().rstrip("/")
            collision = unicodedata.normalize("NFC", relative).casefold()
            if collision in seen:
                raise ValueError("duplicate/casefold/Unicode Gradle ZIP entry collision")
            seen[collision] = relative
            if (is_directory and mode not in {0, stat.S_IFDIR}) or (not is_directory and mode not in {0, stat.S_IFREG}):
                raise ValueError("non-regular Gradle ZIP directory/file entry")
            kinds[relative] = "directory" if is_directory else "file"
            if is_directory:
                continue
            if info.file_size > max_bytes:
                raise ValueError("oversized Gradle ZIP entry")
            digest = hashlib.sha256(); actual_size = 0
            with archive.open(info, "r") as stream:
                while True:
                    chunk = stream.read(min(1024 * 1024, max_bytes - total - actual_size + 1))
                    if not chunk:
                        break
                    actual_size += len(chunk)
                    if total + actual_size > max_bytes or actual_size > info.file_size:
                        raise ValueError("Gradle ZIP actual byte bound failed")
                    digest.update(chunk)
            if actual_size != info.file_size:
                raise ValueError("truncated Gradle ZIP entry")
            total += actual_size
            entries.append({"path": relative, "bytes": actual_size, "sha256": digest.hexdigest()})
    for relative, kind in kinds.items():
        parts = relative.split("/")
        for index in range(1, len(parts)):
            parent = "/".join(parts[:index])
            if kinds.get(parent) == "file":
                raise ValueError("Gradle ZIP file/directory path collision")
    entries.sort(key=lambda item: item["path"])
    if len(entries) > max_files or total > max_bytes or not entries:
        raise ValueError("Gradle ZIP manifest bound failed")
    return {"root": "$GRADLE_DISTRIBUTION_ROOT", "files": len(entries), "bytes": total, "sha256": manifest_sha(entries), "manifest": entries}


def require_distribution_matches_zip() -> dict:
    archived = zip_manifest(GRADLE_DISTRIBUTION_ZIP)
    extracted = tree_manifest(GRADLE_DISTRIBUTION_ROOT, "$GRADLE_DISTRIBUTION_ROOT")
    if archived != extracted:
        raise ValueError("Gradle ZIP/extracted tree mismatch")
    return archived


def require_gradle_properties() -> dict:
    expected = b"org.gradle.daemon=false\norg.gradle.caching=false\norg.gradle.configuration-cache=false\n"
    validate_gradle_properties_bytes(GRADLE_PROPERTIES.read_bytes())
    if GRADLE_PROPERTIES.is_symlink():
        raise ValueError("isolated gradle.properties substituted")
    return file_binding(GRADLE_PROPERTIES, "$GRADLE_USER_HOME/gradle.properties")


def validate_gradle_properties_bytes(data: bytes) -> None:
    expected = b"org.gradle.daemon=false\norg.gradle.caching=false\norg.gradle.configuration-cache=false\n"
    if data != expected:
        raise ValueError("isolated gradle.properties substituted")


def require_checksum_receipt() -> dict:
    if sha_path(CHECKSUM_RECEIPT) != CHECKSUM_RECEIPT_SHA256:
        raise ValueError("official checksum receipt body substituted")
    body = CHECKSUM_RECEIPT.read_bytes()
    if body.count(GRADLE_DISTRIBUTION_SHA256.encode()) != 1:
        raise ValueError("official checksum receipt does not bind distribution")
    return {"url": CHECKSUM_RECEIPT_URL, "bodySha256": CHECKSUM_RECEIPT_SHA256, "acquisitionAuth": "NOT_PROVEN"}


def repo_source_snapshot() -> dict:
    return {
        "head": git("rev-parse", "HEAD"), "headTree": git("rev-parse", "HEAD^{tree}"),
        "indexTree": git("write-tree"),
        "status": sha_bytes(subprocess.run(["git", "status", "--porcelain=v1", "-z"], cwd=ROOT, check=True, capture_output=True).stdout),
        "c1": manifest_sha([{"path": path, "sha256": sha_path(ROOT / path)} for path in sorted(C1_PATHS)]),
    }


def committed_c1_digest(source_commit: str) -> str:
    entries = []
    for path in sorted(C1_PATHS):
        payload = subprocess.run(["git", "show", f"{source_commit}:{path}"], cwd=ROOT, check=True, capture_output=True).stdout
        entries.append({"path": path, "sha256": sha_bytes(payload)})
    return manifest_sha(entries)


def validate_source_snapshot_pair(
    before: dict,
    after: dict,
    source_commit: str,
    source_tree: str,
    c1_digest: str,
) -> None:
    expected = {
        "head": source_commit, "headTree": source_tree, "indexTree": source_tree,
        "status": sha_bytes(b""), "c1": c1_digest,
    }
    if before != expected or after != expected:
        raise ValueError("source pre/post snapshot not bound to clean source commit/tree/C1")


def validate_pre_post(before: dict, after: dict, label: str) -> None:
    if before != after:
        raise ValueError(f"{label} changed during execution")


def dependency_cache_manifest() -> dict:
    result = tree_manifest(
        GRADLE_READ_ONLY_DEPENDENCY_CACHE, "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1",
        max_bytes=MAX_DEPENDENCY_CACHE_BYTES,
    )
    allowed = re.compile(r"^[^/]+/[^/]+/[^/]+/[0-9a-f]{32,64}/[^/]+\.(?:aar|jar|klib|module|pom|tar\.gz)$")
    if any(not allowed.fullmatch(item["path"]) for item in result["manifest"]):
        raise ValueError("dependency cache contains a non-allowlisted path")
    return result


def jdk_distribution_manifest(
    root: Path = Path(JAVA_HOME),
    root_label: str = "$JAVA_HOME",
    max_files: int = MAX_TREE_FILES,
    max_bytes: int = MAX_TREE_BYTES,
) -> dict:
    paths: list[Path] = []
    declared_total = 0
    for path in root.rglob("*"):
        if not (path.is_file() or path.is_symlink()):
            continue
        paths.append(path)
        declared_total += len(("SYMLINK\0" + os.readlink(path)).encode()) if path.is_symlink() else path.stat().st_size
        if len(paths) > max_files or declared_total > max_bytes:
            raise ValueError("JDK distribution count/declared-byte bound failed")
    paths.sort(key=lambda path: path.relative_to(root).as_posix())
    if not paths:
        raise ValueError("JDK distribution empty")
    entries = []
    total = 0
    for path in paths:
        relative = path.relative_to(root).as_posix()
        if path.is_symlink():
            target = os.readlink(path)
            resolved = (path.parent / target).resolve()
            if root.resolve() not in resolved.parents and resolved != root.resolve():
                raise ValueError("JDK symlink escapes Java home")
            payload = ("SYMLINK\0" + target).encode()
        else:
            size = path.stat().st_size
            total += size
            entries.append({"path": relative, "bytes": size, "sha256": sha_path(path)})
            continue
        total += len(payload)
        entries.append({"path": relative, "bytes": len(payload), "sha256": sha_bytes(payload)})
    if not entries or len(entries) > max_files or total > max_bytes:
        raise ValueError("JDK distribution manifest bound failed")
    return {"root": root_label, "files": len(entries), "bytes": total, "sha256": manifest_sha(entries), "manifest": entries}


def writable_gradle_home_manifest() -> dict:
    return tree_manifest(Path(ISOLATED_GRADLE_HOME), "$GRADLE_USER_HOME", max_bytes=MAX_GRADLE_HOME_BYTES)


def sdk_required_manifests() -> list[dict]:
    roots = (
        (Path(ANDROID_HOME) / "platforms/android-37.0", "$ANDROID_HOME/platforms/android-37.0"),
        (Path(ANDROID_HOME) / "build-tools/36.0.0", "$ANDROID_HOME/build-tools/36.0.0"),
        (Path(ANDROID_HOME) / "platform-tools", "$ANDROID_HOME/platform-tools"),
    )
    return [tree_manifest(path, label) for path, label in roots]


def local_properties_binding() -> dict:
    path = ROOT / "local.properties"
    if not path.exists():
        return {"path": "local.properties", "state": "ABSENT"}
    if path.is_symlink() or not path.is_file():
        raise ValueError("local.properties is not a regular file")
    data = tokenize_private_path(path.read_text(encoding="utf-8"))
    validate_private_free(data)
    return {"path": "local.properties", "state": "PRESENT", "sha256": sha_bytes(data.encode())}


def parse_runtime_versions(jdk_text: str, gradle_text: str) -> dict:
    if 'openjdk version "20.0.2"' not in jdk_text or "OpenJDK Runtime Environment (build 20.0.2+9-78)" not in jdk_text:
        raise ValueError("JDK version/runtime substituted")
    expected_gradle = (
        "Gradle 9.4.1", "Launcher JVM:  20.0.2 (Oracle Corporation 20.0.2+9-78)",
        "Daemon JVM:    $JAVA_HOME (no Daemon JVM specified, using current Java home)",
    )
    if any(token not in gradle_text for token in expected_gradle):
        raise ValueError("Gradle launcher/daemon JVM substituted")
    return {"vendor": "Oracle Corporation", "version": "20.0.2", "runtime": "20.0.2+9-78", "gradle": "9.4.1"}


def require_no_init_scripts() -> dict:
    present = [path.as_posix() for path in FORBIDDEN_INIT_FILES if path.exists()]
    for directory in INIT_DIRS:
        if directory.is_dir():
            present.extend(
                path.as_posix() for path in directory.rglob("*")
                if path.is_file() and not (
                    directory == GRADLE_DISTRIBUTION_ROOT / "init.d"
                    and path.relative_to(directory).as_posix() == "readme.txt"
                )
            )
    if present:
        raise ValueError(f"Gradle init scripts forbidden: {present}")
    checked_paths = FORBIDDEN_INIT_FILES + INIT_DIRS
    return {
        "scopedUser": [], "defaultUser": [], "system": [], "distribution": [],
        "checked": [tokenize_private_path(path.as_posix()) for path in checked_paths],
    }


def require_wrapper_checksum_property(text: str | None = None) -> None:
    value = text if text is not None else WRAPPER_PROPERTIES.read_text(encoding="utf-8")
    expected = f"distributionSha256Sum={GRADLE_DISTRIBUTION_SHA256}"
    if value.count(expected) != 1 or "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.4.1-bin.zip" not in value:
        raise ValueError("Gradle wrapper URL/checksum property missing or substituted")


def file_binding(path: Path, label: str) -> dict:
    return {"path": label, "sha256": sha_path(path)}


def collect_toolchain_data(include_dependency_cache: bool = True) -> dict:
    require_wrapper_checksum_property()
    init_scripts = require_no_init_scripts()
    if sha_path(GRADLE_DISTRIBUTION_ZIP) != GRADLE_DISTRIBUTION_SHA256:
        raise ValueError("Gradle distribution ZIP checksum mismatch")
    checksum_receipt = require_checksum_receipt()
    gradle_properties = require_gradle_properties()
    distribution = require_distribution_matches_zip()
    jdk = subprocess.run(JDK_VERSION_COMMAND, cwd=ROOT, env=closed_environment(), check=True, capture_output=True)
    gradle = subprocess.run(GRADLE_VERSION_COMMAND, cwd=ROOT, env=closed_environment(), check=True, capture_output=True)
    jdk_text = sanitize_tool_output(jdk.stdout + jdk.stderr)
    gradle_text = sanitize_tool_output(gradle.stdout + gradle.stderr)
    return {
        "wrapperProperties": sha_path(WRAPPER_PROPERTIES),
        "officialChecksumContent": checksum_receipt,
        "distributionZip": file_binding(GRADLE_DISTRIBUTION_ZIP, "$GRADLE_DISTRIBUTION_ZIP"),
        "distribution": distribution,
        "zipExtract": {"equal": True, "sha256": distribution["sha256"]},
        "launcher": file_binding(GRADLE_LAUNCHER, "$GRADLE_DISTRIBUTION_ROOT/lib/gradle-launcher-9.4.1.jar"),
        "gradleBinary": file_binding(GRADLE_BINARY, "$GRADLE_DISTRIBUTION_ROOT/bin/gradle"),
        "wrapperDirectory": tree_manifest(GRADLE_DISTRIBUTION_ROOT.parent, "$GRADLE_USER_HOME/wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj"),
        "dependencyCache": dependency_cache_manifest() if include_dependency_cache else None,
        "dependencyCacheOrigin": "NOT_PROVEN",
        "gradleProperties": gradle_properties,
        "sdkRequired": sdk_required_manifests(),
        "localProperties": local_properties_binding(),
        "javaHome": "$JAVA_HOME",
        "jdkDistribution": jdk_distribution_manifest(),
        "javaExecutable": file_binding(Path(JAVA_HOME) / "bin/java", "$JAVA_HOME/bin/java"),
        "versions": parse_runtime_versions(jdk_text, gradle_text),
        "jdkCommand": list(JDK_VERSION_COMMAND), "jdkVersion": jdk_text,
        "gradleVersionCommand": ["$GRADLE_DISTRIBUTION_ROOT/bin/gradle", "--version", "--no-daemon"], "gradleVersion": gradle_text,
        "initScripts": init_scripts,
    }


def atomic_write(path: Path, data: bytes) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, raw = tempfile.mkstemp(prefix=f".{target.name}.", dir=target.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(raw, target)
    finally:
        Path(raw).unlink(missing_ok=True)


def valid_sha(value: object) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def validate_bound_manifest(value: object, root: str | None = None) -> None:
    if not isinstance(value, dict) or set(value) != {"root", "files", "bytes", "sha256", "manifest"}:
        raise ValueError("bound tree manifest topology substituted")
    if root is not None and value["root"] != root:
        raise ValueError("bound tree manifest root substituted")
    entries = value["manifest"]
    if type(value["files"]) is not int or type(value["bytes"]) is not int or value["files"] <= 0 or value["bytes"] < 0 or not valid_sha(value["sha256"]):
        raise ValueError("bound tree manifest aggregate type substituted")
    if not isinstance(entries, list) or not entries or value["files"] != len(entries) or value["sha256"] != manifest_sha(entries):
        raise ValueError("bound tree manifest digest/count substituted")
    paths: list[str] = []
    for item in entries:
        if not isinstance(item, dict) or set(item) != {"path", "bytes", "sha256"} or not valid_sha(item["sha256"]):
            raise ValueError("bound tree manifest entry substituted")
        path = item["path"]
        if not isinstance(path, str) or not path or PurePosixPath(path).as_posix() != path or path.startswith("/") or "//" in path or "\\" in path or any(ord(char) < 32 or ord(char) == 127 for char in path) or unicodedata.normalize("NFC", path) != path or any(part in {"", ".", ".."} for part in PurePosixPath(path).parts):
            raise ValueError("unsafe bound tree manifest path")
        if type(item["bytes"]) is not int or item["bytes"] < 0:
            raise ValueError("bound tree manifest byte count substituted")
        paths.append(path)
    if paths != sorted(paths) or len({unicodedata.normalize("NFC", path).casefold() for path in paths}) != len(paths):
        raise ValueError("bound tree manifest paths unordered or duplicated")
    if value["bytes"] != sum(item["bytes"] for item in entries):
        raise ValueError("bound tree manifest total bytes substituted")


def manifest_aggregate(value: dict) -> dict:
    validate_bound_manifest(value)
    return {"root": value["root"], "files": value["files"], "bytes": value["bytes"], "digest": value["sha256"]}


def validate_manifest_aggregate(value: object, root: str, max_files: int, max_bytes: int) -> None:
    if not isinstance(value, dict) or set(value) != {"root", "files", "bytes", "digest"} or value["root"] != root:
        raise ValueError("manifest aggregate topology substituted")
    if type(value["files"]) is not int or not 0 < value["files"] <= max_files:
        raise ValueError("manifest aggregate file cap/type substituted")
    if type(value["bytes"]) is not int or not 0 <= value["bytes"] <= max_bytes or not valid_sha(value["digest"]):
        raise ValueError("manifest aggregate byte cap/digest substituted")


MANIFEST_TABLE_NAMES = frozenset({
    "distribution", "wrapperDirectory", "executionDependencyCache", "jdkDistribution",
    "sdkPlatform", "sdkBuildTools", "sdkPlatformTools", "gradleHomeBefore", "gradleHomeAfter",
})


def manifest_reference(name: str) -> dict:
    return {"manifestRef": name}


def dedupe_manifest_tables(tools: dict, snapshots: dict) -> tuple[dict, dict, dict]:
    compact_tools = dict(tools)
    compact_snapshots = dict(snapshots)
    tables = {
        "distribution": manifest_aggregate(compact_tools.pop("distribution")),
        "wrapperDirectory": manifest_aggregate(compact_tools.pop("wrapperDirectory")),
        "executionDependencyCache": manifest_aggregate(compact_tools.pop("dependencyCache")),
        "jdkDistribution": manifest_aggregate(compact_tools.pop("jdkDistribution")),
        "gradleHomeBefore": manifest_aggregate(compact_snapshots.pop("gradleHomeBefore")),
        "gradleHomeAfter": manifest_aggregate(compact_snapshots.pop("gradleHomeAfter")),
    }
    compact_snapshots.pop("dependencyCacheBefore")
    compact_snapshots.pop("dependencyCacheAfter")
    sdk_names = ("sdkPlatform", "sdkBuildTools", "sdkPlatformTools")
    sdk_tables = compact_tools.pop("sdkRequired")
    for name, table in zip(sdk_names, sdk_tables):
        tables[name] = manifest_aggregate(table)
    compact_tools.update({
        "distribution": manifest_reference("distribution"),
        "wrapperDirectory": manifest_reference("wrapperDirectory"),
        "dependencyCache": manifest_reference("executionDependencyCache"),
        "jdkDistribution": manifest_reference("jdkDistribution"),
        "sdkRequired": [manifest_reference(name) for name in sdk_names],
    })
    compact_snapshots.update({
        "gradleHomeBefore": manifest_reference("gradleHomeBefore"),
        "gradleHomeAfter": manifest_reference("gradleHomeAfter"),
        "sdkBefore": [manifest_reference(name) for name in sdk_names],
        "sdkAfter": [manifest_reference(name) for name in sdk_names],
        "dependencyCacheBefore": manifest_reference("executionDependencyCache"),
        "dependencyCacheAfter": manifest_reference("executionDependencyCache"),
    })
    return compact_tools, compact_snapshots, tables


def validate_manifest_tables(tables: dict) -> None:
    if set(tables) != MANIFEST_TABLE_NAMES:
        raise ValueError("named manifest table topology substituted")
    caps = {
        "distribution": (MAX_TREE_FILES, MAX_TREE_BYTES), "wrapperDirectory": (MAX_TREE_FILES, MAX_TREE_BYTES),
        "executionDependencyCache": (MAX_TREE_FILES, MAX_DEPENDENCY_CACHE_BYTES),
        "jdkDistribution": (MAX_TREE_FILES, MAX_TREE_BYTES), "sdkPlatform": (MAX_TREE_FILES, MAX_TREE_BYTES),
        "sdkBuildTools": (MAX_TREE_FILES, MAX_TREE_BYTES), "sdkPlatformTools": (MAX_TREE_FILES, MAX_TREE_BYTES),
        "gradleHomeBefore": (MAX_TREE_FILES, MAX_GRADLE_HOME_BYTES), "gradleHomeAfter": (MAX_TREE_FILES, MAX_GRADLE_HOME_BYTES),
    }
    for name, table in tables.items():
        validate_manifest_aggregate(table, table.get("root") if isinstance(table, dict) else "", *caps[name])


def validate_manifest_references(tools: dict, snapshots: dict) -> None:
    sdk_refs = [manifest_reference(name) for name in ("sdkPlatform", "sdkBuildTools", "sdkPlatformTools")]
    expected_tools = {
        "distribution": manifest_reference("distribution"),
        "wrapperDirectory": manifest_reference("wrapperDirectory"),
        "dependencyCache": manifest_reference("executionDependencyCache"),
        "jdkDistribution": manifest_reference("jdkDistribution"),
        "sdkRequired": sdk_refs,
    }
    for key, expected in expected_tools.items():
        if tools.get(key) != expected:
            raise ValueError(f"{key} manifest reference substituted")
    expected_snapshots = {
        "gradleHomeBefore": manifest_reference("gradleHomeBefore"),
        "gradleHomeAfter": manifest_reference("gradleHomeAfter"),
        "sdkBefore": sdk_refs,
        "sdkAfter": sdk_refs,
        "dependencyCacheBefore": manifest_reference("executionDependencyCache"),
        "dependencyCacheAfter": manifest_reference("executionDependencyCache"),
    }
    for key, expected in expected_snapshots.items():
        if snapshots.get(key) != expected:
            raise ValueError(f"{key} manifest reference substituted")


def serialize_artifact_manifest(manifest: dict, max_bytes: int = MAX_ARTIFACT_MANIFEST_BYTES) -> bytes:
    chunks: list[bytes] = []
    total = 0
    encoder = json.JSONEncoder(indent=2, sort_keys=True)
    for text in encoder.iterencode(manifest):
        chunk = text.encode("utf-8")
        total += len(chunk)
        if total + 1 > max_bytes:
            raise ValueError("artifact manifest exceeds canonical size cap")
        chunks.append(chunk)
    chunks.append(b"\n")
    return b"".join(chunks)


def read_bounded_file(path: Path, max_bytes: int) -> bytes:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags)
    try:
        metadata = os.fstat(fd)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > max_bytes:
            raise ValueError("bounded regular file required")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(fd, min(1024 * 1024, max_bytes - total + 1))
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                raise ValueError("file exceeds bounded read cap")
            chunks.append(chunk)
        return b"".join(chunks)
    finally:
        os.close(fd)


def load_bounded_json(path: Path, max_bytes: int = MAX_ARTIFACT_MANIFEST_BYTES) -> dict:
    data = json.loads(read_bounded_file(path, max_bytes).decode("utf-8", errors="strict"))
    if not isinstance(data, dict):
        raise ValueError("JSON root must be an object")
    return data


def validate_artifact_data(
    data: dict,
    source_commit: str,
    verify_current: bool = True,
    verify_live_build_outputs: bool | None = None,
) -> None:
    if verify_live_build_outputs is None:
        verify_live_build_outputs = verify_current
    if verify_current and (ROOT / ARTIFACT_MANIFEST).exists() and (ROOT / ARTIFACT_MANIFEST).stat().st_size > MAX_ARTIFACT_MANIFEST_BYTES:
        raise ValueError("artifact manifest exceeds canonical size cap")
    if set(data) != {"schema", "sourceCommit", "command", "environment", "className", "methods", "junit", "log", "compiledTrees", "tools", "snapshots", "manifestTables", "claims"}:
        raise ValueError("artifact manifest keys substituted")
    validate_manifest_references(data["tools"], data["snapshots"])
    validate_manifest_tables(data["manifestTables"])
    tables = data["manifestTables"]
    if data["schema"] != "codecks.m09d.controller-lifecycle-artifacts.v1" or data["sourceCommit"] != source_commit:
        raise ValueError("artifact source binding mismatch")
    if data["command"] != list(COMMAND) or data["className"] != CLASS_NAME or data["methods"] != sorted(METHODS):
        raise ValueError("artifact command, class, or method substitution")
    if data["environment"] != EXEC_ENV:
        raise ValueError("artifact execution environment substituted")
    snapshots = data["snapshots"]
    if set(snapshots) != {"sourceBefore", "sourceAfter", "gradleHomeBefore", "gradleHomeAfter", "sdkBefore", "sdkAfter", "dependencyCacheBefore", "dependencyCacheAfter"}:
        raise ValueError("execution snapshot topology substituted")
    if snapshots["sourceBefore"] != snapshots["sourceAfter"] or snapshots["sdkBefore"] != snapshots["sdkAfter"]:
        raise ValueError("source or required SDK changed during execution")
    if verify_current:
        validate_source_snapshot_pair(
            snapshots["sourceBefore"], snapshots["sourceAfter"], source_commit,
            git("rev-parse", f"{source_commit}^{{tree}}"), committed_c1_digest(source_commit),
        )
    validate_manifest_aggregate(tables["gradleHomeBefore"], "$GRADLE_USER_HOME", MAX_TREE_FILES, MAX_GRADLE_HOME_BYTES)
    validate_manifest_aggregate(tables["gradleHomeAfter"], "$GRADLE_USER_HOME", MAX_TREE_FILES, MAX_GRADLE_HOME_BYTES)
    if verify_live_build_outputs and tables["gradleHomeAfter"] != manifest_aggregate(writable_gradle_home_manifest()):
        raise ValueError("post-execution writable Gradle home changed")
    log_binding = data["log"]
    if set(log_binding) != {"path", "sha256", "capture"} or log_binding["path"] != SANITIZED_LOG.as_posix() or log_binding["capture"] != "DIRECT_SUBPROCESS_STDOUT_STDERR" or not valid_sha(log_binding["sha256"]):
        raise ValueError("artifact log binding substituted")
    junit_binding = data["junit"]
    required_junit = {"path", "sha256", "executionStartNs", "executionEndNs", "sourceMtimeNs", "sourceBirthtimeNs", "sourceInode", "newFileProof", "suiteTimestampNs"}
    if not isinstance(junit_binding, dict) or set(junit_binding) != required_junit or junit_binding["path"] != RAW_JUNIT_XML.as_posix() or not valid_sha(junit_binding["sha256"]):
        raise ValueError("raw focused JUnit metadata substituted")
    start, end, mtime, suite = junit_binding["executionStartNs"], junit_binding["executionEndNs"], junit_binding["sourceMtimeNs"], junit_binding["suiteTimestampNs"]
    if not all(type(value) is int and value > 0 for value in (start, end, mtime, suite, junit_binding["sourceInode"])) or not (start <= mtime <= end and start <= suite <= end):
        raise ValueError("raw focused JUnit interval substituted")
    birth = junit_binding["sourceBirthtimeNs"]
    if birth is None:
        if junit_binding["newFileProof"] != "NOT_PROVEN":
            raise ValueError("raw focused JUnit new-file proof substituted")
    elif type(birth) is not int or not start <= birth <= end or junit_binding["newFileProof"] != "BIRTHTIME_IN_INTERVAL":
        raise ValueError("raw focused JUnit birthtime proof substituted")
    if verify_current:
        raw_junit = read_bounded_file(ROOT / RAW_JUNIT_XML, MAX_XML_BYTES)
        validate_raw_junit_binding(data["junit"], raw_junit)
        if sha_path(ROOT / SANITIZED_LOG) != log_binding["sha256"]:
            raise ValueError("artifact log inner hash mismatch")
        durable_log = read_bounded_file(ROOT / SANITIZED_LOG, MAX_LOG_BYTES)
        validate_private_bytes(durable_log)
        validate_command_header(durable_log)
    trees = data["compiledTrees"]
    if not isinstance(trees, list) or len(trees) != 2:
        raise ValueError("compiled tree topology substituted")
    for tree, root in zip(trees, (PRODUCTION_CLASSES, TEST_CLASSES)):
        if set(tree) != {"root", "files", "bytes", "digest"} or tree["root"] != root.as_posix():
            raise ValueError("compiled tree root substituted")
        if type(tree["files"]) is not int or not 0 < tree["files"] <= MAX_TREE_FILES or type(tree["bytes"]) is not int or not 0 < tree["bytes"] <= MAX_TREE_BYTES or not valid_sha(tree["digest"]):
            raise ValueError("compiled tree digest or count substituted")
        if verify_live_build_outputs and tree != tree_digest(root):
            raise ValueError("compiled tree does not match current bytes")
    tools = data["tools"]
    tool_keys = {
        "wrapperProperties", "officialChecksumContent", "distributionZip", "distribution",
        "zipExtract", "launcher", "gradleBinary", "wrapperDirectory", "dependencyCache", "gradleProperties",
        "sdkRequired", "localProperties", "javaHome", "jdkDistribution", "javaExecutable", "versions", "jdkCommand", "jdkVersion",
        "gradleVersionCommand", "gradleVersion", "initScripts", "dependencyCacheOrigin",
    }
    if set(tools) != tool_keys:
        raise ValueError("tool topology substituted")
    if snapshots["sdkBefore"] != tools["sdkRequired"] or snapshots["sdkAfter"] != tools["sdkRequired"]:
        raise ValueError("SDK pre/post snapshots not bound to tool manifest")
    if not valid_sha(tools["wrapperProperties"]):
        raise ValueError("tool hash is not canonical SHA-256")
    for key in ("distributionZip", "launcher", "gradleBinary", "gradleProperties", "javaExecutable"):
        if set(tools[key]) != {"path", "sha256"} or not valid_sha(tools[key]["sha256"]):
            raise ValueError(f"{key} binding substituted")
    if tools["gradleProperties"] != {"path": "$GRADLE_USER_HOME/gradle.properties", "sha256": GRADLE_PROPERTIES_SHA256}:
        raise ValueError("isolated Gradle properties substituted")
    if tools["officialChecksumContent"] != {"url": CHECKSUM_RECEIPT_URL, "bodySha256": CHECKSUM_RECEIPT_SHA256, "acquisitionAuth": "NOT_PROVEN"}:
        raise ValueError("official checksum receipt substituted")
    if tools["localProperties"] not in ({"path": "local.properties", "state": "ABSENT"},):
        raise ValueError("local.properties binding substituted")
    if tools["distributionZip"] != {"path": "$GRADLE_DISTRIBUTION_ZIP", "sha256": GRADLE_DISTRIBUTION_SHA256}:
        raise ValueError("official distribution ZIP binding substituted")
    expected_roots = {
        "distribution": "$GRADLE_DISTRIBUTION_ROOT",
        "wrapperDirectory": "$GRADLE_USER_HOME/wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj",
        "executionDependencyCache": "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1",
        "jdkDistribution": "$JAVA_HOME",
        "sdkPlatform": "$ANDROID_HOME/platforms/android-37.0",
        "sdkBuildTools": "$ANDROID_HOME/build-tools/36.0.0",
        "sdkPlatformTools": "$ANDROID_HOME/platform-tools",
    }
    for name, root in expected_roots.items():
        max_bytes = MAX_DEPENDENCY_CACHE_BYTES if name == "executionDependencyCache" else MAX_TREE_BYTES
        validate_manifest_aggregate(tables[name], root, MAX_TREE_FILES, max_bytes)
    if tools["dependencyCacheOrigin"] != "NOT_PROVEN":
        raise ValueError("dependency cache origin overclaimed")
    if tools["zipExtract"] != {"equal": True, "sha256": tables["distribution"]["digest"]}:
        raise ValueError("ZIP/extracted distribution proof substituted")
    if tools["javaHome"] != "$JAVA_HOME":
        raise ValueError("Java home substituted")
    if tools["versions"] != {"vendor": "Oracle Corporation", "version": "20.0.2", "runtime": "20.0.2+9-78", "gradle": "9.4.1"}:
        raise ValueError("parsed JDK/Gradle runtime substituted")
    if tools["jdkCommand"] != list(JDK_VERSION_COMMAND) or tools["gradleVersionCommand"] != ["$GRADLE_DISTRIBUTION_ROOT/bin/gradle", "--version", "--no-daemon"]:
        raise ValueError("JDK command or version substituted")
    if not isinstance(tools["jdkVersion"], str) or not tools["jdkVersion"].strip() or not isinstance(tools["gradleVersion"], str) or not tools["gradleVersion"].strip():
        raise ValueError("empty JDK or Gradle JVM output")
    init_scripts = tools["initScripts"]
    if set(init_scripts) != {"scopedUser", "defaultUser", "system", "distribution", "checked"} or any(init_scripts[key] for key in ("scopedUser", "defaultUser", "system", "distribution")):
        raise ValueError("Gradle init-script attestation substituted")
    if verify_current:
        live = collect_toolchain_data()
        for key, value in live.items():
            if key in {"distribution", "wrapperDirectory", "dependencyCache", "jdkDistribution", "sdkRequired"}:
                continue
            if tools.get(key) != value:
                raise ValueError("live Gradle/JDK toolchain bytes substituted")
        for name, value in {
            "distribution": live["distribution"], "wrapperDirectory": live["wrapperDirectory"],
            "jdkDistribution": live["jdkDistribution"], "sdkPlatform": live["sdkRequired"][0],
            "sdkBuildTools": live["sdkRequired"][1], "sdkPlatformTools": live["sdkRequired"][2],
        }.items():
            if tables[name] != manifest_aggregate(value):
                raise ValueError("live tool manifest aggregate substituted")
        if tables["executionDependencyCache"] != manifest_aggregate(live["dependencyCache"]):
            raise ValueError("live execution dependency-cache aggregate substituted")
    if data["claims"] != {"providerNetwork": "NOT_RUN", "networkDenial": "NOT_PROVEN", "dependencyCacheOrigin": "NOT_PROVEN", "freshnessAdversaryResistance": "NOT_PROVEN", "longPressUi": "NOT_RUN", "device": "NOT_RUN", "physicalPhone": "NOT_RUN", "publicRelease": "NOT_RUN", "aiDeleteUndo": "NOT_AVAILABLE"}:
        raise ValueError("artifact claim substitution")
    validate_private_free(data)


def collect_artifacts() -> None:
    source = git("rev-parse", "HEAD")
    validate_topology(source, git("rev-parse", f"{source}^"), changed_paths(source))
    require_clean_status()
    require_worktree_matches_commit(source, C1_PATHS)
    require_source_bindings()
    require_wrapper_checksum_property()
    require_no_init_scripts()
    if sha_path(GRADLE_DISTRIBUTION_ZIP) != GRADLE_DISTRIBUTION_SHA256:
        raise ValueError("Gradle distribution ZIP checksum mismatch before execution")
    remove_focused_result()
    source_before = repo_source_snapshot()
    tools_before = collect_toolchain_data()
    gradle_home_before = writable_gradle_home_manifest()
    execution_start_ns = time.time_ns()
    process = subprocess.run(EXEC_COMMAND, cwd=ROOT, env=closed_environment(), text=False, capture_output=True)
    execution_end_ns = time.time_ns()
    log = sanitize_log(process.stdout + process.stderr)
    if process.returncode != 0:
        raise ValueError("focused Gradle command failed")
    raw_junit, junit_binding = bind_fresh_focused_result(execution_start_ns, execution_end_ns)
    validate_pre_post(source_before, repo_source_snapshot(), "repository source/index/worktree")
    tools_after = collect_toolchain_data()
    immutable_before = {key: value for key, value in tools_before.items() if key != "dependencyCache"}
    immutable_after = {key: value for key, value in tools_after.items() if key != "dependencyCache"}
    validate_pre_post(immutable_before, immutable_after, "immutable toolchain/ZIP/wrapper bytes")
    validate_pre_post(
        manifest_aggregate(tools_before["dependencyCache"]),
        manifest_aggregate(tools_after["dependencyCache"]),
        "execution dependency-cache aggregate",
    )
    gradle_home_after = writable_gradle_home_manifest()
    source_after = repo_source_snapshot()
    atomic_write(RAW_JUNIT_XML, raw_junit)
    atomic_write(SANITIZED_LOG, log)
    full_snapshots = {
        "sourceBefore": source_before, "sourceAfter": source_after,
        "gradleHomeBefore": gradle_home_before, "gradleHomeAfter": gradle_home_after,
        "sdkBefore": tools_before["sdkRequired"], "sdkAfter": tools_after["sdkRequired"],
        "dependencyCacheBefore": tools_before["dependencyCache"],
        "dependencyCacheAfter": tools_after["dependencyCache"],
    }
    compact_tools, compact_snapshots, manifest_tables = dedupe_manifest_tables(tools_before, full_snapshots)
    manifest = {
        "schema": "codecks.m09d.controller-lifecycle-artifacts.v1",
        "sourceCommit": source,
        "command": list(COMMAND), "environment": EXEC_ENV, "className": CLASS_NAME, "methods": sorted(METHODS),
        "junit": junit_binding,
        "log": {"path": SANITIZED_LOG.as_posix(), "sha256": sha_bytes(log), "capture": "DIRECT_SUBPROCESS_STDOUT_STDERR"},
        "compiledTrees": [tree_digest(PRODUCTION_CLASSES), tree_digest(TEST_CLASSES)],
        "tools": compact_tools,
        "snapshots": compact_snapshots,
        "manifestTables": manifest_tables,
        "claims": {"providerNetwork": "NOT_RUN", "networkDenial": "NOT_PROVEN", "dependencyCacheOrigin": "NOT_PROVEN", "freshnessAdversaryResistance": "NOT_PROVEN", "longPressUi": "NOT_RUN", "device": "NOT_RUN", "physicalPhone": "NOT_RUN", "publicRelease": "NOT_RUN", "aiDeleteUndo": "NOT_AVAILABLE"},
    }
    validate_artifact_data(manifest, source, verify_live_build_outputs=True)
    serialized = serialize_artifact_manifest(manifest)
    atomic_write(ARTIFACT_MANIFEST, serialized)


def collect_receipt(artifact_commit: str | None = None) -> dict:
    artifact = artifact_commit or git("rev-parse", "HEAD")
    source = git("rev-parse", f"{artifact}^")
    validate_topology(
        source, git("rev-parse", f"{source}^"), changed_paths(source),
        artifact_commit=artifact, artifact_parent=git("rev-parse", f"{artifact}^"), artifact_paths=changed_paths(artifact),
    )
    require_clean_status()
    require_worktree_matches_commit(source, C1_PATHS)
    require_worktree_matches_commit(artifact, C2_PATHS)
    artifact_data = load_bounded_json(ROOT / ARTIFACT_MANIFEST)
    validate_artifact_data(artifact_data, source)
    durable_log = read_bounded_file(ROOT / SANITIZED_LOG, MAX_LOG_BYTES)
    validate_private_bytes(durable_log)
    validate_command_header(durable_log)
    return {
        "schema": "codecks.m09d.controller-lifecycle.v1", "status": "PASS",
        "scope": "CURRENT_SOURCE_CPU_CONTROLLER_LIFECYCLE_ONLY",
        "commitChain": {"base": BASE_COMMIT, "source": source, "artifact": artifact},
        "sourceBinding": {"files": require_source_bindings(), "c1Paths": sorted(C1_PATHS)},
        "artifacts": [{"path": path, "sha256": sha_path(ROOT / path)} for path in sorted(C2_PATHS)],
        "test": {"className": CLASS_NAME, "methods": sorted(METHODS), "tests": 10, "failures": 0, "errors": 0, "skipped": 0, "command": list(COMMAND), "environment": EXEC_ENV},
        "claims": artifact_data["claims"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--artifacts", action="store_true")
    group.add_argument("--receipt", action="store_true")
    args = parser.parse_args()
    if args.artifacts:
        collect_artifacts(); print("M09D_C2_ARTIFACTS_WRITTEN")
    else:
        atomic_write(RECEIPT, (json.dumps(collect_receipt(), indent=2, sort_keys=True) + "\n").encode())
        print("M09D_C3_RECEIPT_WRITTEN")


if __name__ == "__main__":
    main()
