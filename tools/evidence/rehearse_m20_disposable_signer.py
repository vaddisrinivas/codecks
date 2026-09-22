#!/usr/bin/env python3
"""Prove signer-continuity comparison with throwaway APKs and keys only."""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile
import zipfile

PINNED_BUILD_TOOLS_VERSION = "36.0.0"
SEMANTIC_VERSION = re.compile(r"^[0-9]+(?:\.[0-9]+){1,2}$")
PUBLIC_FIXTURE_NAMES = (
    "same-key-a.apk", "same-key-b.apk", "different-key.apk",
    "same-key-a.cert.txt", "same-key-b.cert.txt", "different-key.cert.txt",
)


def semantic_version(value: str) -> tuple[int, ...]:
    if not SEMANTIC_VERSION.fullmatch(value):
        raise ValueError(f"non-stable Build Tools version: {value}")
    return tuple(int(part) for part in value.split("."))


def select_apksigner(sdk: Path, pinned: str = PINNED_BUILD_TOOLS_VERSION) -> Path:
    semantic_version(pinned)
    candidates = {
        path.parent.name: path
        for path in (sdk / "build-tools").glob("*/apksigner")
        if SEMANTIC_VERSION.fullmatch(path.parent.name) and path.is_file()
    }
    # Sort for deterministic diagnostics and to prevent lexicographic version choice.
    available = sorted(candidates, key=semantic_version)
    if pinned not in candidates:
        raise RuntimeError(f"pinned apksigner {pinned} unavailable; stable versions={available}")
    return candidates[pinned]


def apksigner() -> tuple[Path, Path]:
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk")).resolve()
    return sdk, select_apksigner(sdk)


def tool_binding(sdk: Path, tool: Path) -> dict[str, str]:
    resolved = tool.resolve(strict=True)
    try:
        relative = resolved.relative_to(sdk.resolve(strict=True)).as_posix()
    except ValueError as error:
        raise RuntimeError("apksigner escapes Android SDK") from error
    version = subprocess.run(
        [str(resolved), "version"], check=True, capture_output=True, text=True,
    ).stdout.strip()
    if not version:
        raise RuntimeError("apksigner version unavailable")
    return {
        "buildToolsVersion": PINNED_BUILD_TOOLS_VERSION,
        "sdkRelativePath": relative,
        "reportedVersion": version,
        "sha256": hashlib.sha256(resolved.read_bytes()).hexdigest(),
    }


def create_unsigned(path: Path) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("AndroidManifest.xml", b"codecks disposable signer continuity fixture\n")
        archive.writestr("classes.dex", b"dex\n035\x00codecks-disposable\n")


def create_key(path: Path, alias: str, passphrase: str) -> None:
    keytool = shutil.which("keytool")
    if not keytool:
        raise RuntimeError("keytool unavailable")
    environment = {**os.environ, "CODECKS_M20_DISPOSABLE_PASS": passphrase}
    subprocess.run(
        [keytool, "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048",
         "-validity", "1", "-storetype", "PKCS12", "-keystore", str(path),
         "-storepass:env", "CODECKS_M20_DISPOSABLE_PASS",
         "-keypass:env", "CODECKS_M20_DISPOSABLE_PASS",
         "-dname", "CN=Codecks Disposable Signer,OU=Local Test,O=Codecks,C=US", "-noprompt"],
        check=True, capture_output=True, env=environment,
    )


def sign(tool: Path, source: Path, target: Path, key: Path, alias: str, passphrase: str) -> None:
    environment = {**os.environ, "CODECKS_M20_DISPOSABLE_PASS": passphrase}
    subprocess.run(
        [str(tool), "sign", "--ks", str(key), "--ks-key-alias", alias,
         "--ks-pass", "env:CODECKS_M20_DISPOSABLE_PASS",
         "--key-pass", "env:CODECKS_M20_DISPOSABLE_PASS",
         "--min-sdk-version", "28", "--v1-signing-enabled", "true",
         "--v2-signing-enabled", "false", "--v3-signing-enabled", "false",
         "--out", str(target), str(source)],
        check=True, capture_output=True, env=environment,
    )


def signer_evidence(tool: Path, path: Path) -> tuple[str, str]:
    result = subprocess.run(
        [str(tool), "verify", "--min-sdk-version", "28", "--print-certs", str(path)],
        check=True, capture_output=True, text=True,
    )
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})", result.stdout)
    if not match:
        raise RuntimeError("signer digest unavailable")
    return match.group(1), result.stdout


def persist_public_fixture(output_dir: Path, name: str, payload: bytes) -> dict[str, object]:
    if name not in PUBLIC_FIXTURE_NAMES:
        raise ValueError(f"unexpected public fixture: {name}")
    if len(payload) > 128 * 1024 or b"PRIVATE KEY" in payload or b"CODECKS_M20_DISPOSABLE_PASS" in payload:
        raise ValueError(f"unsafe public fixture: {name}")
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / name
    temporary = output_dir / f".{name}.{os.getpid()}.{secrets.token_hex(8)}.tmp"
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o600)
    try:
        view = memoryview(payload)
        while view:
            written = os.write(descriptor, view)
            view = view[written:]
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    try:
        os.replace(temporary, target)
        directory = os.open(output_dir, os.O_RDONLY | os.O_DIRECTORY | getattr(os, "O_NOFOLLOW", 0))
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
    return {"name": name, "sizeBytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}


def rehearse(output_dir: Path | None = None) -> dict[str, object]:
    sdk, tool = apksigner()
    binding = tool_binding(sdk, tool)
    raw = ""
    with tempfile.TemporaryDirectory(prefix="codecks-m20-disposable-") as raw:
        root = Path(raw)
        unsigned = root / "unsigned.apk"
        create_unsigned(unsigned)
        password = secrets.token_urlsafe(24)
        key_a, key_b = root / "a.p12", root / "b.p12"
        create_key(key_a, "a", password)
        create_key(key_b, "b", password)
        a1, a2, b1 = root / "a1.apk", root / "a2.apk", root / "b1.apk"
        sign(tool, unsigned, a1, key_a, "a", password)
        sign(tool, unsigned, a2, key_a, "a", password)
        sign(tool, unsigned, b1, key_b, "b", password)
        evidence_a1, evidence_a2, evidence_b1 = (signer_evidence(tool, path) for path in (a1, a2, b1))
        digest_a1, digest_a2, digest_b1 = evidence_a1[0], evidence_a2[0], evidence_b1[0]
        if digest_a1 != digest_a2 or digest_a1 == digest_b1:
            raise RuntimeError("signer continuity comparison failed")
        if tool_binding(sdk, tool) != binding:
            raise RuntimeError("apksigner changed during rehearsal")
        artifacts = []
        if output_dir is not None:
            payloads = {
                "same-key-a.apk": a1.read_bytes(), "same-key-b.apk": a2.read_bytes(),
                "different-key.apk": b1.read_bytes(),
                "same-key-a.cert.txt": evidence_a1[1].encode(), "same-key-b.cert.txt": evidence_a2[1].encode(),
                "different-key.cert.txt": evidence_b1[1].encode(),
            }
            artifacts = [persist_public_fixture(output_dir, name, payloads[name]) for name in PUBLIC_FIXTURE_NAMES]
        result = {
            "sameKeyAccepted": True,
            "differentKeyRejected": True,
            "sameSignerCertificateSha256": digest_a1,
            "differentSignerCertificateSha256": digest_b1,
            "publicArtifacts": artifacts,
            "apksigner": binding,
            "privateMaterialPersisted": False,
            "productionKeyAccessed": False,
            "protectedPackageTouched": False,
        }
    if Path(raw).exists():
        raise RuntimeError("disposable private-material directory survived cleanup")
    return result


def main() -> int:
    result = rehearse()
    if not all(result[key] for key in ("sameKeyAccepted", "differentKeyRejected")):
        return 1
    print("PASS: disposable signer continuity; production keys and app.codecks untouched")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
