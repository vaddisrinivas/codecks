#!/usr/bin/env python3
"""Atomically collect exact public-release evidence without retaining the APK."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "tasks/test-evidence/m21-public-release-live.json"
REPOSITORY = "vaddisrinivas/codecks"
REPOSITORY_URL = f"https://github.com/{REPOSITORY}.git"
TAG = "v0.1.37"
API_URL = f"https://api.github.com/repos/{REPOSITORY}/releases/tags/{TAG}"
RELEASE_URL = f"https://github.com/{REPOSITORY}/releases/tag/{TAG}"
DOWNLOAD_ROOT = f"https://github.com/{REPOSITORY}/releases/download/{TAG}"
APK_NAME = "codecks-release.apk"
CHECKSUM_NAME = "SHA256SUMS.txt"
BUILD_TOOLS_VERSION = "36.0.0"
SOURCE_PATHS = (
    "tools/evidence/collect_m21_public_release_live.py",
    "tools/evidence/validate_m21_m23_local_readiness.py",
)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_headers(raw: bytes) -> dict[str, str | None]:
    blocks = [block for block in raw.replace(b"\r\n", b"\n").split(b"\n\n") if block.startswith(b"HTTP/")]
    if not blocks:
        raise RuntimeError("HTTP response headers unavailable")
    headers: dict[str, str] = {}
    for line in blocks[-1].decode("iso-8859-1").splitlines()[1:]:
        if ":" in line:
            name, value = line.split(":", 1)
            headers[name.lower()] = value.strip()
    return headers


def request_provenance(headers: dict[str, str | None], request_url: str, status: int) -> dict:
    return {
        "requestUrl": request_url,
        "status": status,
        "headers": {
            "date": headers.get("date"),
            "etag": headers.get("etag"),
            "lastModified": headers.get("last-modified"),
            "contentLength": headers.get("content-length"),
            "contentType": headers.get("content-type"),
        },
    }


def fetch_bytes(url: str) -> tuple[bytes, dict]:
    with tempfile.TemporaryDirectory(prefix="codecks-m21-http-") as raw:
        path = Path(raw) / "response"
        provenance = fetch_file(url, path, accept_json=True)
        return path.read_bytes(), provenance


def fetch_file(url: str, output: Path, accept_json: bool = False) -> dict:
    curl = shutil.which("curl")
    if not curl:
        raise RuntimeError("curl unavailable")
    headers = output.with_suffix(output.suffix + ".headers")
    command = [curl, "--fail", "--silent", "--show-error", "--location", "--max-time", "120",
               "--user-agent", "codecks-m21-evidence", "--dump-header", str(headers), "--output", str(output),
               "--write-out", "%{http_code}\n"]
    if accept_json:
        command.extend(["--header", "Accept: application/vnd.github+json"])
    command.append(url)
    result = subprocess.run(command, check=True, capture_output=True, text=True)
    lines = result.stdout.splitlines()
    if len(lines) != 1:
        raise RuntimeError("curl status provenance unavailable")
    status = int(lines[0])
    provenance = request_provenance(parse_headers(headers.read_bytes()), url, status)
    headers.unlink()
    if status != 200:
        raise RuntimeError(f"HTTP {status} for {url}")
    return provenance


def tool_binding(sdk: Path, name: str) -> tuple[Path, dict]:
    tool = (sdk / "build-tools" / BUILD_TOOLS_VERSION / name).resolve(strict=True)
    relative = tool.relative_to(sdk.resolve(strict=True)).as_posix()
    version_result = subprocess.run([str(tool), "version"], check=True, capture_output=True, text=True)
    version = (version_result.stdout or version_result.stderr).strip()
    if not version:
        raise RuntimeError(f"{name} version unavailable")
    return tool, {
        "buildToolsVersion": BUILD_TOOLS_VERSION,
        "sdkRelativePath": relative,
        "reportedVersion": version,
        "sha256": sha(tool),
    }


def canonical_release(raw: dict) -> dict:
    assets = []
    for item in raw["assets"]:
        if item["name"] in {APK_NAME, CHECKSUM_NAME}:
            assets.append({
                "name": item["name"],
                "url": item["browser_download_url"],
                "size": item["size"],
                "digest": item["digest"],
                "contentType": item["content_type"],
                "state": item["state"],
            })
    return {
        "repository": REPOSITORY,
        "tag": raw["tag_name"],
        "url": raw["html_url"],
        "targetCommitish": raw["target_commitish"],
        "draft": raw["draft"],
        "prerelease": raw["prerelease"],
        "publishedAt": raw["published_at"],
        "assets": sorted(assets, key=lambda item: item["name"]),
    }


def collect(root: Path = ROOT) -> dict:
    started = utc_now()
    metadata_bytes, metadata_request = fetch_bytes(API_URL)
    metadata = canonical_release(json.loads(metadata_bytes))
    assets = {item["name"]: item for item in metadata["assets"]}
    if set(assets) != {APK_NAME, CHECKSUM_NAME}:
        raise RuntimeError("exact public assets unavailable")

    tag_ref = f"refs/tags/{TAG}"
    peeled_ref = f"{tag_ref}^{{}}"
    tag_result = subprocess.run(
        ["git", "ls-remote", REPOSITORY_URL, tag_ref, peeled_ref],
        check=True, capture_output=True, text=True,
    )
    tag_lines = dict(line.split("\t", 1)[::-1] for line in tag_result.stdout.splitlines())
    if set(tag_lines) != {tag_ref, peeled_ref}:
        raise RuntimeError("tag object and peeled commit unavailable")

    with tempfile.TemporaryDirectory(prefix="codecks-m21-live-") as raw:
        temporary = Path(raw)
        checksum_path = temporary / CHECKSUM_NAME
        apk_path = temporary / APK_NAME
        checksum_request = fetch_file(assets[CHECKSUM_NAME]["url"], checksum_path)
        apk_request = fetch_file(assets[APK_NAME]["url"], apk_path)
        checksum_text = checksum_path.read_text()
        expected_checksum_line = f"{sha(apk_path)}  {APK_NAME}\n"
        if checksum_text != expected_checksum_line:
            raise RuntimeError("published checksum does not match downloaded APK")

        sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
        apksigner, apksigner_binding = tool_binding(sdk, "apksigner")
        aapt2, aapt2_binding = tool_binding(sdk, "aapt2")
        signer_result = subprocess.run(
            [str(apksigner), "verify", "--verbose", "--print-certs", str(apk_path)],
            check=True, capture_output=True, text=True,
        )
        signer_match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})", signer_result.stdout)
        if "Verified using v2 scheme (APK Signature Scheme v2): true" not in signer_result.stdout or not signer_match:
            raise RuntimeError("APK v2 signature or signer unavailable")
        aapt_result = subprocess.run(
            [str(aapt2), "dump", "badging", str(apk_path)],
            check=True, capture_output=True, text=True,
        )
        package_match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", aapt_result.stdout)
        min_match = re.search(r"minSdkVersion:'([^']+)'", aapt_result.stdout)
        target_match = re.search(r"targetSdkVersion:'([^']+)'", aapt_result.stdout)
        if not all((package_match, min_match, target_match)):
            raise RuntimeError("APK package/version/SDK metadata unavailable")
        artifact = {
            "apkName": APK_NAME,
            "apkSizeBytes": apk_path.stat().st_size,
            "apkSha256": sha(apk_path),
            "checksumFile": CHECKSUM_NAME,
            "checksumFileSha256": sha(checksum_path),
            "signerCertificateSha256": signer_match.group(1),
            "package": package_match.group(1),
            "versionCode": int(package_match.group(2)),
            "versionName": package_match.group(3),
            "minSdk": int(min_match.group(1)),
            "targetSdk": int(target_match.group(1)),
        }

    completed = utc_now()
    return {
        "schema": "codecks.m21.public-release-live.v2",
        "startedAtUtc": started,
        "completedAtUtc": completed,
        "requests": {
            "releaseMetadata": metadata_request,
            "checksum": checksum_request,
            "apk": apk_request,
        },
        "tagLookup": {
            "repositoryUrl": REPOSITORY_URL,
            "tagRef": tag_ref,
            "peeledRef": peeled_ref,
            "tagObjectSha": tag_lines[tag_ref],
            "peeledCommitSha": tag_lines[peeled_ref],
            "rawOutput": tag_result.stdout,
        },
        "release": metadata,
        "artifact": artifact,
        "tooling": {"apksigner": apksigner_binding, "aapt2": aapt2_binding},
        "sourceBindings": {path: sha(root / path) for path in SOURCE_PATHS},
        "limitations": [
            "Public GitHub verification only; no candidate, update, device, protected-package, private-key, Play, publication, or installation proof.",
            "The exact APK is downloaded to an auto-deleted temporary directory and is never retained by this collector.",
        ],
    }


def atomic_write(output: Path, payload: bytes, root: Path = ROOT) -> None:
    output = output.absolute()
    output.relative_to(root.absolute())
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.is_symlink():
        raise ValueError("receipt output symlink forbidden")
    descriptor, temporary = tempfile.mkstemp(prefix=f".{output.name}.", suffix=".tmp", dir=output.parent)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, output)
        directory = os.open(output.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass


def main() -> int:
    try:
        receipt = collect()
        atomic_write(OUTPUT, (json.dumps(receipt, indent=2, sort_keys=True) + "\n").encode())
    except (OSError, RuntimeError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("PASS: live public release collected; APK temporary; no device or publication action")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
