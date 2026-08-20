#!/usr/bin/env python3
"""Three-phase M09D controller-lifecycle evidence collector."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import unicodedata
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE_COMMIT = "91e4ff965737df8bbc68e5e3bb9b6852e85fc4a0"
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
    "app/src/main/java/io/codecks/AppCompositionRoot.kt",
    "app/src/test/java/io/codecks/m09d/M09DControllerLifecycleTest.kt",
    "docs/evidence/M09D_CONTROLLER_LIFECYCLE.md",
    "docs/evidence/M09D_CONTROLLER_LIFECYCLE_TODO.md",
    "tools/evidence/collect_m09d_controller_lifecycle.py",
    "tools/evidence/schemas/codecks-m09d-controller-lifecycle-v1.schema.json",
    "tools/evidence/test_m09d_controller_lifecycle.py",
    "tools/evidence/validate_m09d_controller_lifecycle.py",
    "gradle/wrapper/gradle-wrapper.properties",
})
EVIDENCE_DIR = Path("tasks/test-evidence/m09d-controller-lifecycle")
CANONICAL_XML = EVIDENCE_DIR / "junit.xml"
SANITIZED_LOG = EVIDENCE_DIR / "gradle.log"
ARTIFACT_MANIFEST = EVIDENCE_DIR / "artifacts.json"
RECEIPT = EVIDENCE_DIR / "receipt.json"
C2_PATHS = frozenset({CANONICAL_XML.as_posix(), SANITIZED_LOG.as_posix(), ARTIFACT_MANIFEST.as_posix()})
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
GRADLE_DEPENDENCY_CACHE = GRADLE_READ_ONLY_CACHE / "modules-2/files-2.1"
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
        (GRADLE_DEPENDENCY_CACHE.as_posix(), "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1"),
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
    if len(data) > MAX_XML_BYTES or b"<!DOCTYPE" in data or b"<!ENTITY" in data:
        raise ValueError("unsafe JUnit XML")
    root = ET.fromstring(data)
    if root.tag != "testsuite" or root.attrib.get("name") != CLASS_NAME:
        raise ValueError("wrong JUnit class")
    counts = {key: int(root.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
    if counts != {"tests": 10, "failures": 0, "errors": 0, "skipped": 0}:
        raise ValueError(f"wrong JUnit counts: {counts}")
    cases = root.findall("testcase")
    names = sorted(case.attrib.get("name", "") for case in cases)
    if names != sorted(METHODS) or any(case.attrib.get("classname") != CLASS_NAME for case in cases):
        raise ValueError("wrong JUnit class or method set")
    if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
        raise ValueError("JUnit case is not a clean pass")
    return names


def canonical_junit(data: bytes) -> bytes:
    names = parse_junit(data)
    suite = ET.Element("testsuite", {"name": CLASS_NAME, "tests": "10", "failures": "0", "errors": "0", "skipped": "0"})
    for name in names:
        ET.SubElement(suite, "testcase", {"classname": CLASS_NAME, "name": name})
    ET.indent(suite, space="  ")
    return ET.tostring(suite, encoding="utf-8", xml_declaration=True) + b"\n"


def sanitize_log(data: bytes) -> bytes:
    if len(data) > 16 * 1024 * 1024:
        raise ValueError("raw Gradle log exceeds bound")
    text = data.decode("utf-8", errors="strict")
    allowed = tuple(
        line.strip() for line in text.splitlines()
        if line.startswith("> Task :app:validateReleaseSurface")
        or line.startswith("> Task :app:testOssReleaseUnitTest")
        or line.startswith("BUILD SUCCESSFUL")
        or re.fullmatch(r"\d+ actionable tasks:.*", line.strip())
    )
    if not any(line.startswith("BUILD SUCCESSFUL") for line in allowed):
        raise ValueError("Gradle log lacks success marker")
    if not any(line.startswith("> Task :app:validateReleaseSurface") for line in allowed):
        raise ValueError("release-surface task was not executed")
    header = "COMMAND\t" + json.dumps(list(COMMAND), separators=(",", ":")) + "\n"
    result = (header + "\n".join(allowed) + "\n").encode("utf-8")
    if len(result) > MAX_LOG_BYTES or b"/Users/" in result or b"/private/" in result or b"/tmp/" in result:
        raise ValueError("sanitized Gradle log privacy/size bound failed")
    return result


def validate_command_header(data: bytes) -> None:
    expected = ("COMMAND\t" + json.dumps(list(COMMAND), separators=(",", ":")) + "\n").encode()
    if len(data) > MAX_LOG_BYTES or not data.startswith(expected) or b"BUILD SUCCESSFUL" not in data or b"> Task :app:validateReleaseSurface" not in data:
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
    files = sorted(path for path in directory.rglob("*") if path.is_file())
    if len(files) > max_files or any(path.is_symlink() for path in files):
        raise ValueError("compiled tree file bound exceeded or symlink found")
    total = sum(path.stat().st_size for path in files)
    if total > max_bytes or not files:
        raise ValueError("compiled tree byte bound exceeded or tree empty")
    manifest = [{"path": path.relative_to(directory).as_posix(), "sha256": sha_path(path)} for path in files]
    result = {"root": root_label, "files": len(files), "bytes": total, "sha256": manifest_sha(manifest)}
    if include_manifest:
        result["manifest"] = manifest
    return result


def tree_digest(directory: Path, max_bytes: int = MAX_TREE_BYTES, max_files: int = MAX_TREE_FILES) -> dict:
    return _scan_tree(directory, directory.as_posix(), max_bytes, False, max_files=max_files)


def manifest_sha(manifest: list[dict]) -> str:
    return sha_bytes(json.dumps(manifest, separators=(",", ":"), sort_keys=True).encode())


def tree_manifest(directory: Path, root_label: str, max_bytes: int = MAX_TREE_BYTES, max_files: int = MAX_TREE_FILES) -> dict:
    return _scan_tree(directory, root_label, max_bytes, True, max_files=max_files)


def zip_manifest(path: Path, max_files: int = MAX_TREE_FILES, max_bytes: int = MAX_TREE_BYTES) -> dict:
    entries: list[dict] = []
    seen: dict[str, str] = {}
    kinds: dict[str, str] = {}
    total = 0
    root_directory_seen = False
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
            entries.append({"path": relative, "sha256": digest.hexdigest()})
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
        GRADLE_DEPENDENCY_CACHE, "$GRADLE_RO_DEP_CACHE/modules-2/files-2.1",
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
    paths = sorted(path for path in root.rglob("*") if path.is_file() or path.is_symlink())
    if not paths or len(paths) > max_files:
        raise ValueError("JDK distribution file-count bound failed")
    declared_total = sum(len(("SYMLINK\0" + os.readlink(path)).encode()) if path.is_symlink() else path.stat().st_size for path in paths)
    if declared_total > max_bytes:
        raise ValueError("JDK distribution declared-byte bound failed")
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
            entries.append({"path": relative, "sha256": sha_path(path)})
            continue
        total += len(payload)
        entries.append({"path": relative, "sha256": sha_bytes(payload)})
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


def collect_toolchain_data() -> dict:
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
        "dependencyCache": dependency_cache_manifest(),
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
    if not isinstance(entries, list) or not entries or value["files"] != len(entries) or value["sha256"] != manifest_sha(entries):
        raise ValueError("bound tree manifest digest/count substituted")
    if any(set(item) != {"path", "sha256"} or not valid_sha(item["sha256"]) for item in entries):
        raise ValueError("bound tree manifest entry substituted")


def validate_artifact_data(data: dict, source_commit: str, verify_current: bool = True) -> None:
    if set(data) != {"schema", "sourceCommit", "command", "environment", "className", "methods", "junit", "log", "compiledTrees", "tools", "snapshots", "claims"}:
        raise ValueError("artifact manifest keys substituted")
    if data["schema"] != "codecks.m09d.controller-lifecycle-artifacts.v1" or data["sourceCommit"] != source_commit:
        raise ValueError("artifact source binding mismatch")
    if data["command"] != list(COMMAND) or data["className"] != CLASS_NAME or data["methods"] != sorted(METHODS):
        raise ValueError("artifact command, class, or method substitution")
    if data["environment"] != EXEC_ENV:
        raise ValueError("artifact execution environment substituted")
    snapshots = data["snapshots"]
    if set(snapshots) != {"sourceBefore", "sourceAfter", "gradleHomeBefore", "gradleHomeAfter", "sdkBefore", "sdkAfter"}:
        raise ValueError("execution snapshot topology substituted")
    if snapshots["sourceBefore"] != snapshots["sourceAfter"] or snapshots["sdkBefore"] != snapshots["sdkAfter"]:
        raise ValueError("source or required SDK changed during execution")
    if verify_current:
        validate_source_snapshot_pair(
            snapshots["sourceBefore"], snapshots["sourceAfter"], source_commit,
            git("rev-parse", f"{source_commit}^{{tree}}"), committed_c1_digest(source_commit),
        )
    validate_bound_manifest(snapshots["gradleHomeBefore"], "$GRADLE_USER_HOME")
    validate_bound_manifest(snapshots["gradleHomeAfter"], "$GRADLE_USER_HOME")
    if verify_current and snapshots["gradleHomeAfter"] != writable_gradle_home_manifest():
        raise ValueError("post-execution writable Gradle home changed")
    expected_files = (("junit", CANONICAL_XML), ("log", SANITIZED_LOG))
    for key, path in expected_files:
        value = data[key]
        if set(value) != {"path", "sha256"} or value["path"] != path.as_posix() or not valid_sha(value["sha256"]):
            raise ValueError(f"artifact {key} binding substituted")
        if verify_current and sha_path(ROOT / path) != value["sha256"]:
            raise ValueError(f"artifact {key} inner hash mismatch")
    if verify_current:
        parse_junit((ROOT / CANONICAL_XML).read_bytes())
        validate_command_header((ROOT / SANITIZED_LOG).read_bytes())
    trees = data["compiledTrees"]
    if not isinstance(trees, list) or len(trees) != 2:
        raise ValueError("compiled tree topology substituted")
    for tree, root in zip(trees, (PRODUCTION_CLASSES, TEST_CLASSES)):
        if set(tree) != {"root", "files", "bytes", "sha256"} or tree["root"] != root.as_posix():
            raise ValueError("compiled tree root substituted")
        if not isinstance(tree["files"], int) or tree["files"] <= 0 or not isinstance(tree["bytes"], int) or tree["bytes"] <= 0 or not valid_sha(tree["sha256"]):
            raise ValueError("compiled tree digest or count substituted")
        if verify_current and tree != tree_digest(root):
            raise ValueError("compiled tree does not match current bytes")
    tools = data["tools"]
    tool_keys = {
        "wrapperProperties", "officialChecksumContent", "distributionZip", "distribution",
        "zipExtract", "launcher", "gradleBinary", "wrapperDirectory", "dependencyCache", "gradleProperties",
        "sdkRequired", "localProperties", "javaHome", "jdkDistribution", "javaExecutable", "versions", "jdkCommand", "jdkVersion",
        "gradleVersionCommand", "gradleVersion", "initScripts",
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
    for key in ("distribution", "wrapperDirectory", "dependencyCache", "jdkDistribution"):
        required = {"root", "files", "bytes", "sha256", "manifest"}
        if set(tools[key]) != required or not valid_sha(tools[key]["sha256"]) or tools[key]["files"] <= 0 or tools[key]["bytes"] <= 0:
            raise ValueError(f"{key} digest topology substituted")
        manifest = tools[key]["manifest"]
        if not isinstance(manifest, list) or not manifest or any(set(item) != {"path", "sha256"} or not valid_sha(item["sha256"]) for item in manifest):
            raise ValueError(f"{key} manifest substituted")
        if tools[key]["files"] != len(manifest) or tools[key]["sha256"] != manifest_sha(manifest):
            raise ValueError(f"{key} manifest digest/count mismatch")
    if not isinstance(tools["sdkRequired"], list) or [item.get("root") for item in tools["sdkRequired"]] != [
        "$ANDROID_HOME/platforms/android-37.0", "$ANDROID_HOME/build-tools/36.0.0", "$ANDROID_HOME/platform-tools",
    ]:
        raise ValueError("required Android SDK topology substituted")
    for item in tools["sdkRequired"]:
        if set(item) != {"root", "files", "bytes", "sha256", "manifest"} or item["sha256"] != manifest_sha(item["manifest"]):
            raise ValueError("required Android SDK manifest substituted")
    launcher_entry = next((item for item in tools["distribution"]["manifest"] if item["path"] == "lib/gradle-launcher-9.4.1.jar"), None)
    if launcher_entry is None or tools["launcher"]["sha256"] != launcher_entry["sha256"]:
        raise ValueError("Gradle launcher not bound to distribution manifest")
    binary_entry = next((item for item in tools["distribution"]["manifest"] if item["path"] == "bin/gradle"), None)
    if binary_entry is None or tools["gradleBinary"]["sha256"] != binary_entry["sha256"]:
        raise ValueError("Gradle binary not bound to distribution manifest")
    if tools["zipExtract"] != {"equal": True, "sha256": tools["distribution"]["sha256"]}:
        raise ValueError("ZIP/extracted distribution proof substituted")
    if tools["javaHome"] != "$JAVA_HOME":
        raise ValueError("Java home substituted")
    java_entry = next((item for item in tools["jdkDistribution"]["manifest"] if item["path"] == "bin/java"), None)
    if java_entry is None or tools["javaExecutable"] != java_entry:
        expected_java = {"path": "$JAVA_HOME/bin/java", "sha256": java_entry["sha256"]} if java_entry else None
        if tools["javaExecutable"] != expected_java:
            raise ValueError("Java executable not bound to JDK manifest")
    if tools["versions"] != {"vendor": "Oracle Corporation", "version": "20.0.2", "runtime": "20.0.2+9-78", "gradle": "9.4.1"}:
        raise ValueError("parsed JDK/Gradle runtime substituted")
    if tools["jdkCommand"] != list(JDK_VERSION_COMMAND) or tools["gradleVersionCommand"] != ["$GRADLE_DISTRIBUTION_ROOT/bin/gradle", "--version", "--no-daemon"]:
        raise ValueError("JDK command or version substituted")
    if not isinstance(tools["jdkVersion"], str) or not tools["jdkVersion"].strip() or not isinstance(tools["gradleVersion"], str) or not tools["gradleVersion"].strip():
        raise ValueError("empty JDK or Gradle JVM output")
    init_scripts = tools["initScripts"]
    if set(init_scripts) != {"scopedUser", "defaultUser", "system", "distribution", "checked"} or any(init_scripts[key] for key in ("scopedUser", "defaultUser", "system", "distribution")):
        raise ValueError("Gradle init-script attestation substituted")
    if verify_current and tools != collect_toolchain_data():
        raise ValueError("live Gradle/JDK toolchain bytes substituted")
    if data["claims"] != {"providerNetwork": "NOT_RUN", "networkDenial": "NOT_PROVEN", "longPressUi": "NOT_RUN", "device": "NOT_RUN", "physicalPhone": "NOT_RUN", "publicRelease": "NOT_RUN", "aiDeleteUndo": "NOT_AVAILABLE"}:
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
    source_before = repo_source_snapshot()
    tools_before = collect_toolchain_data()
    gradle_home_before = writable_gradle_home_manifest()
    process = subprocess.run(EXEC_COMMAND, cwd=ROOT, env=closed_environment(), text=False, capture_output=True)
    log = sanitize_log(process.stdout + process.stderr)
    if process.returncode != 0:
        raise ValueError("focused Gradle command failed")
    validate_pre_post(source_before, repo_source_snapshot(), "repository source/index/worktree")
    tools_after = collect_toolchain_data()
    validate_pre_post(tools_before, tools_after, "toolchain/dependency/ZIP/wrapper bytes")
    gradle_home_after = writable_gradle_home_manifest()
    source_after = repo_source_snapshot()
    xml = canonical_junit((ROOT / RESULT_XML).read_bytes())
    atomic_write(CANONICAL_XML, xml)
    atomic_write(SANITIZED_LOG, log)
    manifest = {
        "schema": "codecks.m09d.controller-lifecycle-artifacts.v1",
        "sourceCommit": source,
        "command": list(COMMAND), "environment": EXEC_ENV, "className": CLASS_NAME, "methods": sorted(METHODS),
        "junit": {"path": CANONICAL_XML.as_posix(), "sha256": sha_bytes(xml)},
        "log": {"path": SANITIZED_LOG.as_posix(), "sha256": sha_bytes(log)},
        "compiledTrees": [tree_digest(PRODUCTION_CLASSES), tree_digest(TEST_CLASSES)],
        "tools": tools_before,
        "snapshots": {
            "sourceBefore": source_before, "sourceAfter": source_after,
            "gradleHomeBefore": gradle_home_before, "gradleHomeAfter": gradle_home_after,
            "sdkBefore": tools_before["sdkRequired"], "sdkAfter": tools_after["sdkRequired"],
        },
        "claims": {"providerNetwork": "NOT_RUN", "networkDenial": "NOT_PROVEN", "longPressUi": "NOT_RUN", "device": "NOT_RUN", "physicalPhone": "NOT_RUN", "publicRelease": "NOT_RUN", "aiDeleteUndo": "NOT_AVAILABLE"},
    }
    validate_artifact_data(manifest, source)
    atomic_write(ARTIFACT_MANIFEST, (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode())


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
    artifact_data = json.loads((ROOT / ARTIFACT_MANIFEST).read_text(encoding="utf-8"))
    validate_artifact_data(artifact_data, source)
    parse_junit((ROOT / CANONICAL_XML).read_bytes())
    validate_command_header((ROOT / SANITIZED_LOG).read_bytes())
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
