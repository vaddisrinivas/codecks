package io.codecks.shared.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ReactiveProtocolTest {
    @Test
    fun requestRejectsInvalidSequenceExpiredDeadlineAndOversizedBody() {
        assertFailsWith<IllegalArgumentException> {
            validateRequestEnvelope(request(sequence = 0), nowMillis = 50)
        }
        assertFailsWith<IllegalArgumentException> {
            validateRequestEnvelope(request(deadlineMillis = 49), nowMillis = 50)
        }
        assertFailsWith<IllegalArgumentException> {
            validateRequestEnvelope(
                request(bodyJson = "x".repeat(REACTIVE_MAX_BODY_BYTES + 1)),
                nowMillis = 50,
            )
        }
    }

    @Test
    fun proofTranscriptsAreRoleSeparatedAndDeterministic() {
        val hello = ReactiveHello(
            deviceId = "phone",
            clientNonce = "client-nonce",
            clientTimeMillis = 1,
        )
        val challenge = ReactiveChallenge(
            sessionId = "session",
            serverNonce = "server-nonce",
            expiresAtMillis = 100,
            macId = "mac",
        )

        assertEquals(clientProofTranscript(hello, challenge), clientProofTranscript(hello, challenge))
        assertTrue(clientProofTranscript(hello, challenge) != serverProofTranscript(hello, challenge))
    }

    @Test
    fun frameCodecRoundTripsAndRejectsPartialOrOversizedFrames() {
        val payload = """{"type":"ping"}""".encodeToByteArray()
        assertContentEquals(payload, ReactiveFrameCodec.decode(ReactiveFrameCodec.encode(payload)))
        assertFailsWith<IllegalArgumentException> {
            ReactiveFrameCodec.decode(byteArrayOf(0, 0, 0, 2, 1))
        }
        assertFailsWith<IllegalArgumentException> {
            ReactiveFrameCodec.encode(ByteArray(REACTIVE_MAX_BODY_BYTES + 1))
        }
    }

    @Test
    fun authenticationResultRequiresMatchingLiveSession() {
        val result = ReactiveAuthResult(
            sessionId = "session",
            accepted = true,
            expiresAtMillis = 100,
            serverProof = "proof",
        )
        validateAuthResult(result, expectedSessionId = "session", nowMillis = 50)
        assertFailsWith<IllegalArgumentException> {
            validateAuthResult(result, expectedSessionId = "other", nowMillis = 50)
        }
        assertFailsWith<IllegalArgumentException> {
            validateAuthResult(result, expectedSessionId = "session", nowMillis = 101)
        }
    }

    @Test
    fun typedHelperRequestRoundTripsWithExplicitDiscriminator() {
        val json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
        }
        val request: ReactiveHelperRequest = ReactiveHelperRequest.Execute(
            actionId = "reload",
            actionRevision = "rev-1",
            arguments = mapOf("target" to "current"),
        )
        val encoded = json.encodeToString(ReactiveHelperRequest.serializer(), request)
        val decoded = json.decodeFromString(ReactiveHelperRequest.serializer(), encoded)

        assertEquals(request, decoded)
        assertTrue(encoded.contains("\"type\":\"execute\""))
        assertFalse(encoded.contains("ReactiveHelperRequest.Execute"))
    }

    private fun request(
        sequence: Long = 1,
        deadlineMillis: Long = 100,
        bodyJson: String = "{}",
    ) = ReactiveRequestEnvelope(
        sessionId = "session",
        sequence = sequence,
        requestId = "request",
        deadlineMillis = deadlineMillis,
        bodyJson = bodyJson,
        authTag = "tag",
    )
}
