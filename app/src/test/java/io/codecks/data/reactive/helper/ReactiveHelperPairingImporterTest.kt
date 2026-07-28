package io.codecks.data.reactive.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReactiveHelperPairingImporterTest {
    @Test
    fun parsesValidPairingPayload() {
        val payload = """
            {
              "macId": "Desk Mac",
              "displayName": "Desk Mac",
              "helperId": "codecks-mac-helper",
              "publicKeyFingerprint": "${"a".repeat(64)}",
              "sharedSecretHex": "${"01".repeat(32)}",
              "host": "192.168.1.20",
              "port": 47321
            }
        """.decodePairingPayload()

        assertEquals("Desk Mac", payload.macId)
        assertEquals("codecks-mac-helper", payload.helperId)
        assertEquals("192.168.1.20", payload.host)
        assertEquals(47321, payload.port)
    }

    @Test
    fun rejectsShortSecretAndUnsafeHost() {
        assertThrows(IllegalArgumentException::class.java) {
            """
                {
                  "macId": "Desk Mac",
                  "displayName": "Desk Mac",
                  "helperId": "codecks-mac-helper",
                  "publicKeyFingerprint": "${"a".repeat(64)}",
                  "sharedSecretHex": "01"
                }
            """.decodePairingPayload()
        }
        assertThrows(IllegalArgumentException::class.java) {
            """
                {
                  "macId": "Desk Mac",
                  "displayName": "Desk Mac",
                  "helperId": "codecks-mac-helper",
                  "publicKeyFingerprint": "${"a".repeat(64)}",
                  "sharedSecretHex": "${"01".repeat(32)}",
                  "host": "bad/host"
                }
            """.decodePairingPayload()
        }
    }
}
