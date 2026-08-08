from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import extract_managed_commercial_receipt as extractor  # noqa: E402


def make_log(path: Path, *, tamper: bool = False, omit_last: bool = False) -> None:
    receipt = {
        "schema": extractor.SCHEMA,
        "target_package": "app.codecks",
        "overall": "NOT_RUN",
        "checks": [{"id": "network.packet_capture", "status": "NOT_RUN"}],
    }
    raw = json.dumps(receipt, separators=(",", ":")).encode()
    encoded = base64.b64encode(raw).decode()
    chunks = [encoded[index:index + 24] for index in range(0, len(encoded), 24)]
    digest = hashlib.sha256(raw).hexdigest()
    lines = [f"I System.out: CODECKS_COMMERCIAL_PROOF_RECEIPT_META=chunks:{len(chunks)};sha256:{digest}"]
    for index, chunk in enumerate(chunks, start=1):
        if omit_last and index == len(chunks):
            continue
        if tamper and index == 1:
            chunk = ("A" if chunk[0] != "A" else "B") + chunk[1:]
        lines.append(
            f"I System.out: CODECKS_COMMERCIAL_PROOF_RECEIPT_CHUNK={index}/{len(chunks)}:{chunk}",
        )
    path.write_text("\n".join(lines))


class ManagedCommercialReceiptExtractorTest(unittest.TestCase):
    def test_valid_chunks_are_reconstructed_and_verified(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "proof.log"
            make_log(log)
            result = extractor.extract(log)
        self.assertEqual("app.codecks", result["target_package"])
        self.assertEqual("NOT_RUN", result["overall"])

    def test_missing_chunk_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "proof.log"
            make_log(log, omit_last=True)
            with self.assertRaisesRegex(ValueError, "missing receipt chunks"):
                extractor.extract(log)

    def test_tampered_chunk_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "proof.log"
            make_log(log, tamper=True)
            with self.assertRaisesRegex(ValueError, "digest mismatch"):
                extractor.extract(log)


if __name__ == "__main__":
    unittest.main()
