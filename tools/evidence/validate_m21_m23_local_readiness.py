#!/usr/bin/env python3
"""Fail-closed validation for locally finishable M21/M23 preparation."""

from __future__ import annotations

import json
import os
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
import re
import struct
import subprocess
import sys
from pathlib import Path

from collect_m21_public_release_live import (
    API_URL, APK_NAME, BUILD_TOOLS_VERSION, CHECKSUM_NAME, DOWNLOAD_ROOT, RELEASE_URL,
    REPOSITORY, REPOSITORY_URL, SOURCE_PATHS, TAG, collect, sha, tool_binding,
)

ROOT = Path(__file__).resolve().parents[2]
GITHUB_GUIDE = ROOT / "docs/distribution/GITHUB_RELEASE_SUPPORT.md"
PLAY_DRAFT = ROOT / "docs/distribution/PLAY_STORE_DRAFT.md"
M23_DRAFT = ROOT / "docs/release/M23_LOCAL_PREFLIGHT.md"
TODO = ROOT / "tasks/AUTONOMOUS_MATURITY_TODO.md"
STATE = ROOT / "docs/release/production-state.json"
BASELINE = ROOT / "tasks/test-evidence/autonomous-maturity-m00-baseline.json"
LIVE_PUBLIC = ROOT / "tasks/test-evidence/m21-public-release-live.json"
METADATA = ROOT / "fastlane/metadata/android/en-US"
PR_MERGES = {
    18: "846435491d3493b0ca7182bc6d636f1255353a4a",
    19: "925f0eb9f7fe84465c71c4ef41bbc8ac398d042f",
    20: "cd5459db42bcded7cf4879f3f7dd40aa1b38678d",
    21: "0fc17bc6ef23137508077004b9082565f4d3caeb",
    22: "17e36493fa446f3a245c10547d5c512a378514bc",
    23: "5e683fbddca7e3c0eac6e3562224822077593036",
    24: "d1f1788f03fe59bb0dceb5822e9f5b19194090fd",
}
EXPECTED_REPOSITORY = REPOSITORY
EXPECTED_CHECKSUM_FILE = CHECKSUM_NAME
EXPECTED_PUBLIC_TAG = TAG
EXPECTED_PUBLIC_COMMIT = "0dc3cac1f7e6b02fa4d5f069eda8664789852370"
EXPECTED_TAG_OBJECT = "3d21b395d49fa87d8fa04ba1f06f917acee5d081"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def png_size(path: Path) -> tuple[int, int]:
    raw = path.read_bytes()[:24]
    require(raw[:8] == b"\x89PNG\r\n\x1a\n" and len(raw) == 24, f"invalid PNG: {path}")
    return struct.unpack(">II", raw[16:24])


def validate_state_contract(state: dict) -> None:
    public = state["public_release"]
    require(public["version"] == "0.1.37" and public["version_code"] == 37, "public release drift")
    require((public["tag"], public["commit"]) == (EXPECTED_PUBLIC_TAG, EXPECTED_PUBLIC_COMMIT), "public release provenance drift")
    require(state["candidate"] == {"version_assigned": False, "artifact_admitted": False, "status": "unreleased_working_state"}, "candidate was promoted")
    require(state["commercial"] == {
        "sign_in": "OFF", "cloud_sync": "OFF", "play_billing": "OFF",
        "premium_enforcement": "OFF", "ads": "OFF", "sdk_or_network_startup": "NONE",
    }, "commercial state is not dark")


def validate_public_receipt(
    receipt: dict,
    public: dict,
    artifact: dict,
    evidence_root: Path = ROOT,
    now: datetime | None = None,
    live_recollect: bool = False,
) -> None:
    require(set(receipt) == {"schema", "startedAtUtc", "completedAtUtc", "requests", "tagLookup", "release", "artifact", "tooling", "sourceBindings", "limitations"}, "public receipt keys changed")
    require(receipt["schema"] == "codecks.m21.public-release-live.v2", "public receipt schema changed")
    require(all(isinstance(receipt[name], str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", receipt[name])
                for name in ("startedAtUtc", "completedAtUtc")), "public receipt timestamps must be exact UTC seconds")
    try:
        started = datetime.strptime(receipt["startedAtUtc"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        completed = datetime.strptime(receipt["completedAtUtc"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except (TypeError, ValueError) as error:
        raise ValueError("public receipt timestamps must be exact UTC seconds") from error
    now = datetime.now(timezone.utc) if now is None else now
    require(started <= completed <= started + timedelta(minutes=10), "public collection duration invalid")
    require(completed <= now + timedelta(minutes=5), "public receipt completion is in the future")

    expected_urls = {"releaseMetadata": API_URL, "checksum": f"{DOWNLOAD_ROOT}/{CHECKSUM_NAME}", "apk": f"{DOWNLOAD_ROOT}/{APK_NAME}"}
    require(set(receipt["requests"]) == set(expected_urls), "request provenance inventory changed")
    for name, request in receipt["requests"].items():
        require(set(request) == {"requestUrl", "status", "headers"}, f"request shape changed: {name}")
        require(request["requestUrl"] == expected_urls[name] and request["status"] == 200, f"request provenance changed: {name}")
        require(set(request["headers"]) == {"date", "etag", "lastModified", "contentLength", "contentType"}, f"response headers changed: {name}")
        require(isinstance(request["headers"]["date"], str), f"response date invalid: {name}")
        try:
            date = parsedate_to_datetime(request["headers"]["date"])
        except (TypeError, ValueError) as error:
            raise ValueError(f"response date invalid: {name}") from error
        require(date.tzinfo is not None and started - timedelta(minutes=5) <= date <= completed + timedelta(minutes=5), f"response date invalid: {name}")

    tag = receipt["tagLookup"]
    require(tag == {
        "repositoryUrl": REPOSITORY_URL,
        "tagRef": f"refs/tags/{TAG}",
        "peeledRef": f"refs/tags/{TAG}^{{}}",
        "tagObjectSha": EXPECTED_TAG_OBJECT,
        "peeledCommitSha": EXPECTED_PUBLIC_COMMIT,
        "rawOutput": f"{EXPECTED_TAG_OBJECT}\trefs/tags/{TAG}\n{EXPECTED_PUBLIC_COMMIT}\trefs/tags/{TAG}^{{}}\n",
    }, "live tag provenance changed")
    expected_release = {
        "repository": REPOSITORY, "tag": TAG, "url": RELEASE_URL, "targetCommitish": "main",
        "draft": False, "prerelease": False, "publishedAt": artifact["published_at"],
        "assets": sorted([
            {"name": artifact["apk_name"], "url": f"{DOWNLOAD_ROOT}/{artifact['apk_name']}", "size": artifact["apk_size_bytes"], "digest": f"sha256:{artifact['apk_sha256']}", "contentType": "application/vnd.android.package-archive", "state": "uploaded"},
            {"name": CHECKSUM_NAME, "url": f"{DOWNLOAD_ROOT}/{CHECKSUM_NAME}", "size": 86, "digest": f"sha256:{artifact['checksum_file_sha256']}", "contentType": "text/plain; charset=utf-8", "state": "uploaded"},
        ], key=lambda item: item["name"]),
    }
    require(receipt["release"] == expected_release, "canonical release identity changed")
    expected_artifact = {
        "apkName": artifact["apk_name"], "apkSizeBytes": artifact["apk_size_bytes"], "apkSha256": artifact["apk_sha256"],
        "checksumFile": CHECKSUM_NAME, "checksumFileSha256": artifact["checksum_file_sha256"],
        "signerCertificateSha256": artifact["signer_certificate_sha256"], "package": artifact["package"],
        "versionCode": artifact["version_code"], "versionName": artifact["version_name"],
        "minSdk": artifact["min_sdk"], "targetSdk": artifact["target_sdk"],
    }
    require(receipt["artifact"] == expected_artifact, "public artifact identity changed")
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    expected_tools = {name: tool_binding(sdk, name)[1] for name in ("apksigner", "aapt2")}
    require(receipt["tooling"] == expected_tools, "Android Build Tools binding changed")
    require(receipt["sourceBindings"] == {path: sha(evidence_root / path) for path in SOURCE_PATHS}, "collector/validator source binding changed")
    require(receipt["limitations"] == [
        "Public GitHub verification only; no candidate, update, device, protected-package, private-key, Play, publication, or installation proof.",
        "The exact APK is downloaded to an auto-deleted temporary directory and is never retained by this collector.",
    ], "public receipt limitations changed")
    if live_recollect:
        fresh = collect(evidence_root)
        for key in ("tagLookup", "release", "artifact", "tooling", "sourceBindings"):
            require(fresh[key] == receipt[key], f"live recollection changed: {key}")
        validate_public_receipt(fresh, public, artifact, evidence_root, now=now, live_recollect=False)


def validate_commercial_static(root: Path) -> None:
    result = subprocess.run(
        [sys.executable, "tools/commercial_proof_harness.py", "repo-static", "--root", str(root)],
        cwd=root, text=True, capture_output=True,
    )
    require(result.returncode == 0, "commercial static proof failed")
    receipt = json.loads(result.stdout)
    require(receipt["kind"] == "repo_static" and receipt["overall"] == "PASS", "commercial static proof is not PASS")


def validate_document_contract(github: str, play: str, m23: str, artifact: dict) -> None:
    for value in (artifact["apk_sha256"], artifact["signer_certificate_sha256"]):
        require(value in github, "GitHub guide lacks public artifact binding")
    for text in ("Never uninstall", "hard stop", "partial", "Security Advisory", "Android 9 / API 28"):
        require(text in github, f"GitHub guide missing: {text}")
    for text in ("DRAFT_ONLY", "not an accepted declaration", "production remains unauthorized", "creates no server account", "external console state"):
        require(text in play, f"Play draft missing: {text}")
    for text in ("NOT_A_CANDIDATE", "check remains `NOT_RUN`", "Still `NOT_RUN`", "version unassigned"):
        require(text in m23, f"M23 boundary missing: {text}")


def validate(root: Path = ROOT, env: dict[str, str] | None = None) -> dict:
    env = os.environ if env is None else env
    signing_names = (
        "CODECKS_RELEASE_STORE_FILE", "CODECKS_RELEASE_KEY_ALIAS",
        "CODECKS_RELEASE_STORE_PASSWORD", "CODECKS_RELEASE_KEY_PASSWORD",
    )
    signing_present = [name for name in signing_names if env.get(name)]
    require(not signing_present, "signing inputs unexpectedly present; do not proceed in local-only mode")
    state = json.loads((root / STATE.relative_to(ROOT)).read_text())
    baseline = json.loads((root / BASELINE.relative_to(ROOT)).read_text())
    live_public = json.loads((root / LIVE_PUBLIC.relative_to(ROOT)).read_text())
    github = (root / GITHUB_GUIDE.relative_to(ROOT)).read_text()
    play = (root / PLAY_DRAFT.relative_to(ROOT)).read_text()
    m23 = (root / M23_DRAFT.relative_to(ROOT)).read_text()
    todo = (root / TODO.relative_to(ROOT)).read_text()

    artifact = baseline["release_artifact"]
    source = baseline["source"]
    require((source["repository"], source["release_tag"], source["release_tag_object_sha"], source["release_tag_commit_sha"]) == (
        f"https://github.com/{EXPECTED_REPOSITORY}", EXPECTED_PUBLIC_TAG, EXPECTED_TAG_OBJECT, EXPECTED_PUBLIC_COMMIT,
    ), "baseline repository or tag provenance changed")
    validate_state_contract(state)
    validate_public_receipt(live_public, state["public_release"], artifact, root, live_recollect=True)
    validate_document_contract(github, play, m23, artifact)
    require("- [ ] M21 execute clean install, in-place update, broken-network UI" in todo,
            "M21 external device lane was promoted or removed")
    validate_commercial_static(root)

    title = (root / METADATA.relative_to(ROOT) / "title.txt").read_text().strip()
    short = (root / METADATA.relative_to(ROOT) / "short_description.txt").read_text().strip()
    full = (root / METADATA.relative_to(ROOT) / "full_description.txt").read_text().strip()
    require(1 <= len(title) <= 30, "Play title length invalid")
    require(1 <= len(short) <= 80, "Play short description length invalid")
    require(1 <= len(full) <= 4000, "Play full description length invalid")
    screenshots = sorted((root / METADATA.relative_to(ROOT) / "images/phoneScreenshots").glob("*.png"))
    require(len(screenshots) == 8, "expected exactly eight review screenshots")
    require(all(png_size(path) == (1080, 2400) for path in screenshots), "screenshot dimensions changed")

    for number, commit in PR_MERGES.items():
        result = subprocess.run(["git", "merge-base", "--is-ancestor", commit, "HEAD"], cwd=root)
        require(result.returncode == 0, f"dependency PR #{number} is absent")
    build = (root / "app/build.gradle.kts").read_text()
    require("isMinifyEnabled = false" in build and "isShrinkResources = false" in build, "release shrink invariant missing")
    return {
        "m21": "LOCAL_SUPPORT_PACKAGE_READY",
        "m23": "BLOCKED_SIGNING_AND_EXACT_ARTIFACT",
        "publicRelease": "v0.1.37",
        "screenshots": 8,
        "dependencyPrs": sorted(PR_MERGES),
        "commercial": "STATIC_PRODUCTION_DARK_PASS",
        "signingInputs": "AMBIENT_UNSET_CANONICAL_NOT_RUN",
        "candidateBuilt": False,
        "externalActionLanes": "NOT_RUN",
    }


def main() -> int:
    try:
        result = validate()
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
