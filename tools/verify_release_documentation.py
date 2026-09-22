#!/usr/bin/env python3
"""Fail closed when public release documentation drifts from build truth."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
STATE_PATH = ROOT / "docs/release/production-state.json"


class DuplicateKey(ValueError):
    pass


def unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKey(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def load_state(errors: list[str]) -> dict[str, object]:
    if not STATE_PATH.is_file() or STATE_PATH.is_symlink():
        errors.append("production-state.json must be a regular repository file")
        return {}
    try:
        STATE_PATH.resolve().relative_to(ROOT.resolve())
    except ValueError:
        errors.append("production-state.json escapes repository")
        return {}
    try:
        state = json.loads(STATE_PATH.read_text(), object_pairs_hook=unique_object)
    except (OSError, json.JSONDecodeError, DuplicateKey) as error:
        errors.append(f"invalid production-state.json: {error}")
        return {}
    expected = {"schema", "public_release", "candidate", "commercial", "activation", "evidence"}
    require(set(state) == expected, "production state must use the closed top-level schema", errors)
    return state


def parse_gradle_text(text: str, label: str, errors: list[str]) -> tuple[str, int]:
    versions = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"', text, re.MULTILINE)
    codes = re.findall(r"^\s*versionCode\s*=\s*(\d+)", text, re.MULTILINE)
    require(len(versions) == 1, f"{label} must declare exactly one versionName", errors)
    require(len(codes) == 1, f"{label} must declare exactly one versionCode", errors)
    return (versions[0] if len(versions) == 1 else "", int(codes[0]) if len(codes) == 1 else -1)


def parse_gradle(errors: list[str]) -> tuple[str, int]:
    return parse_gradle_text((ROOT / "app/build.gradle.kts").read_text(), "Gradle", errors)


def git_call(arguments: list[str]) -> tuple[int, str]:
    result = subprocess.run(
        ["git", *arguments],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    return result.returncode, result.stdout.strip()


def validate_git_release(
    public: dict[str, object],
    version: str,
    code: int,
    errors: list[str],
    runner=git_call,
) -> None:
    tag = public.get("tag")
    expected_commit = public.get("commit")
    require(isinstance(tag, str) and re.fullmatch(r"v\d+\.\d+\.\d+", tag) is not None, "invalid release tag", errors)
    require(isinstance(expected_commit, str) and re.fullmatch(r"[0-9a-f]{40}", expected_commit) is not None, "invalid release commit", errors)
    if not isinstance(tag, str) or not isinstance(expected_commit, str):
        return

    peel_code, peeled = runner(["rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}"])
    require(peel_code == 0, f"release tag missing: {tag}", errors)
    require(peel_code != 0 or peeled == expected_commit, f"release tag moved: {tag}", errors)

    head_code, head = runner(["rev-parse", "HEAD"])
    require(head_code == 0 and re.fullmatch(r"[0-9a-f]{40}", head or "") is not None, "cannot resolve HEAD", errors)
    if peel_code == 0 and head_code == 0:
        ancestor_code, _ = runner(["merge-base", "--is-ancestor", expected_commit, head])
        require(ancestor_code == 0, "release commit is not an ancestor of HEAD", errors)
        require(head != expected_commit, "working HEAD must be strictly after the public release tag", errors)

    gradle_code, tag_gradle = runner(["show", f"{tag}:app/build.gradle.kts"])
    require(gradle_code == 0, "cannot read Gradle snapshot from release tag", errors)
    if gradle_code == 0:
        tag_version, tag_code = parse_gradle_text(tag_gradle, "tag Gradle snapshot", errors)
        require((tag_version, tag_code) == (version, code), "release tag version/code does not match production state", errors)

    notes_path = f"docs/release/RELEASE_NOTES_v{version}.md"
    notes_code, tag_notes = runner(["show", f"{tag}:{notes_path}"])
    require(notes_code == 0, "cannot read release notes from release tag", errors)
    if notes_code == 0:
        require(f"# Codecks v{version} release notes" in tag_notes, "tag release-note heading drifted", errors)
        require(f"/releases/tag/v{version}" in tag_notes, "tag release-note URL drifted", errors)


def markdown_fragments(document: Path) -> set[str]:
    fragments: set[str] = set()
    counts: dict[str, int] = {}
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*$", document.read_text(), re.MULTILINE):
        slug = re.sub(r"[^\w\- ]", "", heading.lower()).strip().replace(" ", "-")
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        fragments.add(slug if count == 0 else f"{slug}-{count}")
    return fragments


def validate_local_links(paths: list[Path], errors: list[str]) -> None:
    for document in paths:
        for raw_target in re.findall(r"!?\[[^]]*\]\(([^)]+)\)", document.read_text()):
            raw_target = raw_target.strip().strip("<>")
            if re.match(r"^[a-z][a-z0-9+.-]*:", raw_target, re.IGNORECASE):
                continue
            target, _, raw_fragment = raw_target.partition("#")
            fragment = unquote(raw_fragment).lower()
            resolved = document if not target else document.parent / target
            require(not resolved.is_symlink(), f"symlinked local link: {document.relative_to(ROOT)} -> {target}", errors)
            resolved = resolved.resolve()
            try:
                resolved.relative_to(ROOT.resolve())
            except ValueError:
                errors.append(f"local link escapes repository: {document.relative_to(ROOT)} -> {target}")
                continue
            require(resolved.exists(), f"broken local link: {document.relative_to(ROOT)} -> {target}", errors)
            if fragment and resolved.is_file():
                require(fragment in markdown_fragments(resolved), f"broken local fragment: {document.relative_to(ROOT)} -> {raw_target}", errors)


def validate_completed_checklist(text: str, errors: list[str]) -> None:
    completed = re.findall(r"^- \[x\] (.*?)(?=^- \[[ x]\] |\Z)", text, re.MULTILINE | re.DOTALL)
    require(bool(completed), "commercial checklist has no completed items", errors)
    for index, item in enumerate(completed, start=1):
        require("](" in item, f"completed commercial checklist item {index} lacks an evidence link", errors)


def validate_evidence(evidence: list[object], errors: list[str]) -> None:
    seen: set[str] = set()
    for raw in evidence:
        require(isinstance(raw, str) and bool(raw), "evidence entries must be non-empty paths", errors)
        if not isinstance(raw, str) or not raw:
            continue
        require(raw not in seen, f"duplicate evidence path: {raw}", errors)
        seen.add(raw)
        path = ROOT / raw
        require(path.is_file() and not path.is_symlink(), f"missing or symlinked evidence: {raw}", errors)
        try:
            path.resolve().relative_to(ROOT.resolve())
        except ValueError:
            errors.append(f"evidence escapes repository: {raw}")


def validate_document_truth(docs: dict[str, str], version: str, code: int, errors: list[str]) -> None:
    for label, text in docs.items():
        require(f"v{version}" in text, f"{label} does not name v{version}", errors)
    require(f"Applies to: public beta v{version}" in docs["feature guide"], "feature guide release header drifted", errors)
    require(f"Current public release is `v{version}`" in docs["launch plan"], "launch-plan release decision drifted", errors)
    require(f"| Version | `{version}` (`versionCode` {code}) |" in docs["release ledger"], "release ledger version drifted", errors)
    require(":app:testOssReleaseUnitTest :app:lintOssDebug :app:assembleOssDebug" in docs["README.md"], "README build command drifted", errors)
    require("Current release is `v0.1.36`" not in docs["launch plan"], "stale v0.1.36 launch baseline returned", errors)
    require("`v0.1.25` release workflow" not in docs["launch plan"], "stale v0.1.25 workflow claim returned", errors)
    require("Current public beta is `v0.1.25`" not in docs["FOSS readiness"], "stale FOSS version returned", errors)


def main() -> int:
    errors: list[str] = []
    state = load_state(errors)
    if not state:
        return fail(errors)

    public = state.get("public_release")
    candidate = state.get("candidate")
    commercial = state.get("commercial")
    evidence = state.get("evidence")
    require(isinstance(public, dict), "public_release must be an object", errors)
    require(isinstance(candidate, dict), "candidate must be an object", errors)
    require(isinstance(commercial, dict), "commercial must be an object", errors)
    require(isinstance(evidence, list) and bool(evidence), "evidence must be a non-empty array", errors)
    if not all((isinstance(public, dict), isinstance(candidate, dict), isinstance(commercial, dict), isinstance(evidence, list))):
        return fail(errors)

    version, code = parse_gradle(errors)
    require(state.get("schema") == "codecks.production-state.v1", "unsupported production-state schema", errors)
    require(public == {
        "version": version,
        "version_code": code,
        "tag": f"v{version}",
        "commit": "a65415a790b2517108fee9f47fa95404ae511bdd",
        "status": "public_beta",
        "release_notes": f"docs/release/RELEASE_NOTES_v{version}.md",
    }, "public_release does not exactly match Gradle/release-note truth", errors)
    validate_git_release(public, version, code, errors)
    require(candidate == {
        "version_assigned": False,
        "artifact_admitted": False,
        "status": "unreleased_working_state",
    }, "candidate must remain explicitly unversioned and unadmitted", errors)

    expected_commercial = {
        "sign_in": "OFF",
        "cloud_sync": "OFF",
        "play_billing": "OFF",
        "premium_enforcement": "OFF",
        "ads": "OFF",
        "sdk_or_network_startup": "NONE",
    }
    require(commercial == expected_commercial, "commercial public state must remain exactly production-dark", errors)
    require(state.get("activation") == "EXPLICIT_OWNER_APPROVAL_REQUIRED_PER_SURFACE", "activation authority drifted", errors)

    docs = {
        "README.md": (ROOT / "README.md").read_text(),
        "launch plan": (ROOT / "docs/release/PRODUCTION_LAUNCH_PLAN.md").read_text(),
        "release ledger": (ROOT / "docs/release/CODECKS_RELEASE_LEDGER.md").read_text(),
        "feature guide": (ROOT / "docs/product/FEATURE_GUIDE.md").read_text(),
        "commercial plan": (ROOT / "tasks/plan.md").read_text(),
        "commercial checklist": (ROOT / "tasks/todo.md").read_text(),
        "FOSS readiness": (ROOT / "docs/distribution/FOSS_READINESS.md").read_text(),
    }
    validate_document_truth(docs, version, code, errors)

    validate_completed_checklist(docs["commercial checklist"], errors)

    validate_local_links([
        ROOT / "README.md",
        ROOT / "CONTRIBUTING.md",
        ROOT / "docs/product/FEATURE_GUIDE.md",
        ROOT / "docs/release/CODECKS_RELEASE_LEDGER.md",
        ROOT / "docs/release/PRODUCTION_LAUNCH_PLAN.md",
        ROOT / "docs/release/RELEASING.md",
        ROOT / "docs/distribution/FOSS_READINESS.md",
        ROOT / "tasks/plan.md",
        ROOT / "tasks/todo.md",
    ], errors)

    notes = ROOT / str(public.get("release_notes", ""))
    require(notes.is_file() and not notes.is_symlink(), "release notes missing or symlinked", errors)
    if notes.is_file():
        notes_text = notes.read_text()
        require(f"# Codecks v{version} release notes" in notes_text, "release-note heading drifted", errors)
        require(f"/releases/tag/v{version}" in notes_text, "release-note tag URL drifted", errors)

    validate_evidence(evidence, errors)

    return fail(errors) if errors else success(version, code, len(evidence))


def success(version: str, code: int, evidence_count: int) -> int:
    print(f"PASS release docs: v{version} code={code}; commercial=OFF; evidence={evidence_count}")
    return 0


def fail(errors: list[str]) -> int:
    for error in errors:
        print(f"FAIL: {error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
