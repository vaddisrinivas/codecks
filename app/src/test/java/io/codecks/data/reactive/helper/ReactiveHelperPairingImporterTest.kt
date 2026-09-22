package io.codecks.data.reactive.helper

import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReactiveHelperPairingImporterTest {
    private val json = """{"schema":"codecks.pairing.v2","offerId":"AAAAAAAAAAAAAAAAAAAAAA","issuedAtMillis":1000,"expiresAtMillis":121000,"macId":"desk-mac","displayName":"Desk Mac","helperId":"codecks-mac-helper","publicKeyFingerprint":"${"a".repeat(64)}","host":"192.168.1.20","port":47321,"offerNonce":"BBBBBBBBBBBBBBBBBBBBBQ","offerSecretHex":"${(0 until 32).joinToString("") { "%02x".format(it) }}"}"""

    @Test fun goldenTranscriptHkdfAndProofsMatchSwift() {
        val importer = ReactiveHelperPairingImporter(NoopStore)
        val pending = importer.prepareV2(json, "CCCCCCCCCCCCCCCCCCCCCg", "DDDDDDDDDDDDDDDDDDDDDw", 1_000)
        assertEquals("18:codecks.pairing.v2|22:AAAAAAAAAAAAAAAAAAAAAA|4:1000|6:121000|8:desk-mac|8:Desk Mac|18:codecks-mac-helper|64:${"a".repeat(64)}|12:192.168.1.20|5:47321|22:BBBBBBBBBBBBBBBBBBBBBQ|22:CCCCCCCCCCCCCCCCCCCCCg|22:DDDDDDDDDDDDDDDDDDDDDw", pending.transcript)
        assertEquals("6f21ecfead9128987be11a8388884f23bf24b5f16c1874ea2e1dc00441bc5746", pending.beginProof)
        assertEquals("f4544b58892e6462d9d1e6672ce774760beb396d9378e71759ff32dd9f9ec0c5", pending.derivedCredential.joinToString("") { "%02x".format(it) })
        assertEquals("025776", pending.matchingCode)
        assertEquals("b20cfd5c3f55fa8b98a3bbf9dc4e5b757145ba138a710bbecdf243c6b4e37357", pending.confirmationProof)
    }

    @Test fun phoneClockIsAdvisoryButLifetimeIsStrict() {
        assertEquals(true, json.decodePairingV2Offer(121_001).locallyExpired(121_001))
        assertThrows(IllegalArgumentException::class.java) { json.replace("121000", "121001").decodePairingV2Offer(1_000) }
    }

    @Test fun publicDeepLinkAcceptsBoundedV2AndRejectsPermanentV1Secret() {
        val prefix = "codecks://helper-pair"
        val encoded = URLEncoder.encode(json, "UTF-8")
        assertEquals(json, reactiveHelperPairingJsonFromUri("$prefix?payload=$encoded", prefix))
        val legacy = """{"macId":"desk","sharedSecretHex":"${"01".repeat(32)}"}"""
        assertNull(reactiveHelperPairingJsonFromUri("$prefix?payload=${URLEncoder.encode(legacy, "UTF-8")}", prefix))
        assertNull(reactiveHelperPairingJsonFromUri("codecks://trackpad?payload=$encoded", prefix))
        assertNull(reactiveHelperPairingJsonFromUri("$prefix?payload=$encoded&payload=$encoded", prefix))
        assertNull(reactiveHelperPairingJsonFromUri("$prefix?payload=$encoded&extra=1", prefix))
        val duplicate = json.replaceFirst("\"offerId\":", "\"offerId\":\"AAAAAAAAAAAAAAAAAAAAAA\",\"offerId\":")
        assertNull(reactiveHelperPairingJsonFromUri("$prefix?payload=${URLEncoder.encode(duplicate, "UTF-8")}", prefix))
        val unknown = json.dropLast(1) + ",\"extra\":1}"
        assertNull(reactiveHelperPairingJsonFromUri("$prefix?payload=${URLEncoder.encode(unknown, "UTF-8")}", prefix))
    }

    @Test fun persistenceRequiresVerifiedServerAcceptanceAndIsOneTime() = kotlinx.coroutines.test.runTest {
        var saves = 0
        val store = object : ReactiveHelperPairingStore {
            override suspend fun savePairing(identity: io.codecks.platform.helper.StoredReactiveHelperIdentity, sharedSecret: ByteArray) { saves++ }
        }
        val importer = ReactiveHelperPairingImporter(store)
        val pending = importer.prepareV2(json, "CCCCCCCCCCCCCCCCCCCCCg", "DDDDDDDDDDDDDDDDDDDDDw", 1_000)
        val wrong = PairingV2ServerAcceptance(pending.offer.offerId, pending.deviceId, true, "bad")
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { importer.commitConfirmed(pending, wrong) } }
        assertEquals(0, saves)
        val valid = wrong.copy(serverProof = PairingV2Crypto.serverAcceptanceProof(pending.derivedCredential, pending.transcript))
        importer.commitConfirmed(pending, valid)
        assertEquals(1, saves)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { importer.commitConfirmed(pending, valid) } }
    }

    private object NoopStore : ReactiveHelperPairingStore {
        override suspend fun savePairing(identity: io.codecks.platform.helper.StoredReactiveHelperIdentity, sharedSecret: ByteArray) = Unit
    }
}
