package io.codecks.data.reactive.helper

import io.codecks.platform.helper.ReactiveHelperEndpoint
import io.codecks.platform.helper.ReactiveHelperTransport
import io.codecks.platform.helper.ReactiveHelperTransportFactory
import io.codecks.shared.protocol.ReactiveFrameCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class ReactiveHelperPairingV2ClientTest {
    @Test fun malformedServerResponseIsRejected() = runTest {
        val client = ReactiveHelperPairingV2Client(factory("not-json".encodeToByteArray()))
        assertThrows(Exception::class.java) { kotlinx.coroutines.runBlocking { client.begin(pending()) } }
    }

    @Test fun oversizedServerResponseIsRejected() = runTest {
        val oversized = "{\"padding\":\"${"a".repeat(4 * 1024)}\"}".encodeToByteArray()
        val client = ReactiveHelperPairingV2Client(factory(oversized))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { client.begin(pending()) } }
    }

    private fun factory(payload: ByteArray) = object : ReactiveHelperTransportFactory {
        override suspend fun connect(endpoint: ReactiveHelperEndpoint): ReactiveHelperTransport = object : ReactiveHelperTransport {
            override suspend fun exchange(frame: ByteArray): ByteArray = ReactiveFrameCodec.encode(payload)
        }
    }

    private fun pending(): PendingReactiveHelperPairing {
        val secret = (0 until 32).joinToString("") { "%02x".format(it) }
        val json = """{"schema":"codecks.pairing.v2","offerId":"AAAAAAAAAAAAAAAAAAAAAA","issuedAtMillis":1000,"expiresAtMillis":121000,"macId":"desk-mac","displayName":"Desk Mac","helperId":"codecks-mac-helper","publicKeyFingerprint":"${"a".repeat(64)}","host":"192.168.1.20","port":47321,"offerNonce":"BBBBBBBBBBBBBBBBBBBBBQ","offerSecretHex":"$secret"}"""
        return ReactiveHelperPairingImporter(NoopStore).prepareV2(
            json, "CCCCCCCCCCCCCCCCCCCCCg", "DDDDDDDDDDDDDDDDDDDDDw", 1_000,
        )
    }

    private object NoopStore : ReactiveHelperPairingStore {
        override suspend fun savePairing(identity: io.codecks.platform.helper.StoredReactiveHelperIdentity, sharedSecret: ByteArray) = Unit
    }
}
