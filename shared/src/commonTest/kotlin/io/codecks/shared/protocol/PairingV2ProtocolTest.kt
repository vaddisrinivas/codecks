package io.codecks.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingV2ProtocolTest {
    @Test fun transcriptIsUtf8ByteLengthCanonical() {
        assertEquals("2:é|1:x", pairingV2Canonical("é", "x"))
        assertEquals(
            "18:codecks.pairing.v2|22:AAAAAAAAAAAAAAAAAAAAAA|4:1000|6:121000|8:desk-mac|8:Desk Mac|18:codecks-mac-helper|64:${"a".repeat(64)}|12:192.168.1.20|5:47321|22:BBBBBBBBBBBBBBBBBBBBBQ|22:CCCCCCCCCCCCCCCCCCCCCg|22:DDDDDDDDDDDDDDDDDDDDDw",
            pairingV2Transcript(PairingV2TranscriptFields("AAAAAAAAAAAAAAAAAAAAAA", 1_000, 121_000, "desk-mac", "Desk Mac", "codecks-mac-helper", "a".repeat(64), "192.168.1.20", 47_321, "BBBBBBBBBBBBBBBBBBBBBQ", "CCCCCCCCCCCCCCCCCCCCCg", "DDDDDDDDDDDDDDDDDDDDDw")),
        )
    }

    @Test fun entropyTokenIsAsciiCanonical() {
        assertTrue(isCanonicalPairingV2EntropyToken("AAAAAAAAAAAAAAAAAAAAAA"))
        assertFalse(isCanonicalPairingV2EntropyToken("éAAAAAAAAAAAAAAAAAAAAA"))
        assertFalse(isCanonicalPairingV2EntropyToken("AAAAAAAAAAAAAAAAAAAAAB"))
    }
}
