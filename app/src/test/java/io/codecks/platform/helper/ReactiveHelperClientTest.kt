package io.codecks.platform.helper

import io.codecks.shared.protocol.ReactiveAuthResult
import io.codecks.shared.protocol.ReactiveChallenge
import io.codecks.shared.protocol.ReactiveFrameCodec
import io.codecks.shared.protocol.ReactiveHello
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.shared.protocol.ReactiveProof
import io.codecks.shared.protocol.ReactiveRequestEnvelope
import io.codecks.shared.protocol.ReactiveResponseEnvelope
import io.codecks.shared.protocol.ReceiptStatus
import io.codecks.shared.protocol.clientProofTranscript
import io.codecks.shared.protocol.responseAuthTranscript
import io.codecks.shared.protocol.serverProofTranscript
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveHelperClientTest {
    private val secret = ByteArray(32) { index -> (index + 1).toByte() }
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
    }

    @Test
    fun authenticatesServerAndReturnsTypedOrderedReceipt() = runTest {
        val helper = FakeHelperTransport(secret, json)
        val client = client(helper)

        assertEquals("mac-1", client.open("client-nonce").macId)
        val result = client.request(ReactiveHelperRequest.BasicState)

        assertEquals(ReceiptStatus.Completed, result.status)
        assertEquals(1L, result.sequence)
        assertTrue(result.bodyJson.orEmpty().contains("frontAppName"))
        assertTrue(client.state.value is ReactiveHelperClientState.Connected)
    }

    @Test
    fun rejectsWrongPinnedMacIdentity() = runTest {
        val helper = FakeHelperTransport(secret, json, macId = "other-mac")
        val client = client(helper)

        val failure = runCatching { client.open("client-nonce") }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("identity"))
        assertEquals(ReactiveHelperClientState.Failed("identity_mismatch"), client.state.value)
    }

    @Test
    fun rejectsBadServerProof() = runTest {
        val helper = FakeHelperTransport(secret, json, corruptServerProof = true)
        val client = client(helper)

        val failure = runCatching { client.open("client-nonce") }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("proof"))
        assertEquals(ReactiveHelperClientState.Failed("authentication_failed"), client.state.value)
    }

    @Test
    fun rejectsReplayedResponse() = runTest {
        val helper = FakeHelperTransport(secret, json, replayFirstResponse = true)
        val client = client(helper)
        client.open("client-nonce")
        client.request(ReactiveHelperRequest.BasicState)

        val failure = runCatching {
            client.request(ReactiveHelperRequest.Capabilities)
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("sequence"))
    }

    private fun client(transport: ReactiveHelperTransport) = ReactiveHelperClient(
        transport = transport,
        deviceId = "phone-1",
        credentials = ReactiveHelperCredentials(
            expectedMacId = "mac-1",
            sharedSecret = secret,
        ),
        nowMillis = { 10_000L },
        json = json,
    )
}

private class FakeHelperTransport(
    private val secret: ByteArray,
    private val json: Json,
    private val macId: String = "mac-1",
    private val corruptServerProof: Boolean = false,
    private val replayFirstResponse: Boolean = false,
) : ReactiveHelperTransport {
    private var hello: ReactiveHello? = null
    private var challenge: ReactiveChallenge? = null
    private var firstResponse: ReactiveResponseEnvelope? = null

    override suspend fun exchange(frame: ByteArray): ByteArray {
        val payload = ReactiveFrameCodec.decode(frame).decodeToString()
        return when {
            payload.contains("\"clientNonce\"") -> onHello(payload)
            payload.contains("\"proof\"") -> onProof(payload)
            payload.contains("\"bodyJson\"") -> onRequest(payload)
            else -> error("Unexpected helper payload")
        }
    }

    private fun onHello(payload: String): ByteArray {
        hello = json.decodeFromString<ReactiveHello>(payload)
        challenge = ReactiveChallenge(
            sessionId = "session-1",
            serverNonce = "server-nonce",
            expiresAtMillis = 20_000L,
            macId = macId,
        )
        return encode(challenge)
    }

    private fun onProof(payload: String): ByteArray {
        val proof = json.decodeFromString<ReactiveProof>(payload)
        val currentHello = checkNotNull(hello)
        val currentChallenge = checkNotNull(challenge)
        assertTrue(
            MessageDigest.isEqual(
                proof.proof.encodeToByteArray(),
                hmacHex(secret, clientProofTranscript(currentHello, currentChallenge)).encodeToByteArray(),
            ),
        )
        val serverProof = if (corruptServerProof) {
            "bad"
        } else {
            hmacHex(secret, serverProofTranscript(currentHello, currentChallenge))
        }
        return encode(
            ReactiveAuthResult(
                sessionId = currentChallenge.sessionId,
                accepted = true,
                expiresAtMillis = currentChallenge.expiresAtMillis,
                serverProof = serverProof,
            ),
        )
    }

    private fun onRequest(payload: String): ByteArray {
        val request = json.decodeFromString<ReactiveRequestEnvelope>(payload)
        if (replayFirstResponse && firstResponse != null) {
            return encode(firstResponse)
        }
        val unsigned = ReactiveResponseEnvelope(
            sessionId = request.sessionId,
            sequence = request.sequence,
            requestId = request.requestId,
            status = ReceiptStatus.Completed,
            bodyJson = """{"macId":"mac-1","snapshotRevision":1,"capturedAtMillis":10000,"frontAppName":"Finder"}""",
            authTag = "",
        )
        val response = unsigned.copy(authTag = hmacHex(secret, responseAuthTranscript(unsigned)))
        firstResponse = response
        return encode(response)
    }

    private inline fun <reified T> encode(value: T): ByteArray =
        ReactiveFrameCodec.encode(json.encodeToString(value).encodeToByteArray())
}

private fun hmacHex(secret: ByteArray, message: String): String = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(secret, algorithm))
    doFinal(message.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
