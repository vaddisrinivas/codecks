package io.codecks.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val REACTIVE_PROTOCOL_SCHEMA = "reactive.v1"
const val REACTIVE_MAX_BODY_BYTES = 64 * 1024
const val REACTIVE_MAX_TOKEN_BYTES = 128
const val REACTIVE_REPLAY_CACHE_SIZE = 256

@Serializable
data class ReactiveHello(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val deviceId: String,
    val clientNonce: String,
    val clientTimeMillis: Long,
)

@Serializable
data class ReactiveChallenge(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val serverNonce: String,
    val expiresAtMillis: Long,
    val macId: String,
)

@Serializable
data class ReactiveProof(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val proof: String,
)

@Serializable
data class ReactiveAuthResult(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val accepted: Boolean,
    val expiresAtMillis: Long,
    val serverProof: String,
    val code: String? = null,
)

@Serializable
data class ReactiveRequestEnvelope(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val sequence: Long,
    val requestId: String,
    val deadlineMillis: Long,
    val bodyJson: String,
    val authTag: String,
)

@Serializable
data class ReactiveResponseEnvelope(
    val schema: String = REACTIVE_PROTOCOL_SCHEMA,
    val sessionId: String,
    val sequence: Long,
    val requestId: String,
    val status: ReceiptStatus,
    val code: String? = null,
    val retryable: Boolean = false,
    val bodyJson: String? = null,
    val authTag: String,
)

@Serializable
enum class ReceiptStatus {
    @SerialName("accepted") Accepted,
    @SerialName("completed") Completed,
    @SerialName("failed") Failed,
    @SerialName("denied") Denied,
}

@Serializable
sealed interface ReactiveHelperRequest {
    @Serializable
    @SerialName("ping")
    data class Ping(val clientTimeMillis: Long) : ReactiveHelperRequest

    @Serializable
    @SerialName("capabilities")
    data object Capabilities : ReactiveHelperRequest

    @Serializable
    @SerialName("basic_state")
    data object BasicState : ReactiveHelperRequest

    @Serializable
    @SerialName("execute")
    data class Execute(
        val actionId: String,
        val actionRevision: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : ReactiveHelperRequest
}

@Serializable
data class ReactiveHelperCapabilities(
    val values: Set<String>,
)

@Serializable
data class ReactiveHelperBasicState(
    val macId: String,
    val snapshotRevision: Long,
    val capturedAtMillis: Long,
    val frontAppBundleId: String? = null,
    val frontAppName: String? = null,
    val capabilities: Set<String> = emptySet(),
)

@Serializable
data class ReactiveHelperActionReceipt(
    val receiptId: String,
    val actionId: String,
    val actionRevision: String,
    val completedAtMillis: Long,
    val resultCode: String,
)

fun requireReactiveSchema(schema: String) {
    require(schema == REACTIVE_PROTOCOL_SCHEMA) {
        "Unsupported Reactive protocol schema: $schema"
    }
}

fun validateHello(hello: ReactiveHello) {
    requireReactiveSchema(hello.schema)
    requireToken("deviceId", hello.deviceId)
    requireToken("clientNonce", hello.clientNonce)
    require(hello.clientTimeMillis >= 0) { "clientTimeMillis must not be negative" }
}

fun validateChallenge(challenge: ReactiveChallenge, nowMillis: Long) {
    requireReactiveSchema(challenge.schema)
    requireToken("sessionId", challenge.sessionId)
    requireToken("serverNonce", challenge.serverNonce)
    requireToken("macId", challenge.macId)
    require(challenge.expiresAtMillis >= nowMillis) { "Helper challenge expired" }
}

fun validateAuthResult(result: ReactiveAuthResult, expectedSessionId: String, nowMillis: Long) {
    requireReactiveSchema(result.schema)
    require(result.sessionId == expectedSessionId) { "Authentication session mismatch" }
    require(result.expiresAtMillis >= nowMillis) { "Authentication result expired" }
    require(result.serverProof.isNotBlank()) { "serverProof must not be blank" }
}

fun validateRequestEnvelope(envelope: ReactiveRequestEnvelope, nowMillis: Long) {
    requireReactiveSchema(envelope.schema)
    requireToken("sessionId", envelope.sessionId)
    requireToken("requestId", envelope.requestId)
    require(envelope.sequence > 0) { "sequence must be positive" }
    require(envelope.deadlineMillis >= nowMillis) { "request deadline expired" }
    requireBoundedBody(envelope.bodyJson)
    require(envelope.authTag.isNotBlank()) { "authTag must not be blank" }
}

fun validateResponseEnvelope(envelope: ReactiveResponseEnvelope) {
    requireReactiveSchema(envelope.schema)
    requireToken("sessionId", envelope.sessionId)
    requireToken("requestId", envelope.requestId)
    require(envelope.sequence > 0) { "sequence must be positive" }
    envelope.bodyJson?.let(::requireBoundedBody)
    require(envelope.authTag.isNotBlank()) { "authTag must not be blank" }
}

fun clientProofTranscript(
    hello: ReactiveHello,
    challenge: ReactiveChallenge,
): String = canonicalFields(
    "client-proof",
    hello.schema,
    hello.deviceId,
    hello.clientNonce,
    challenge.serverNonce,
    challenge.sessionId,
    challenge.expiresAtMillis.toString(),
    challenge.macId,
)

fun serverProofTranscript(
    hello: ReactiveHello,
    challenge: ReactiveChallenge,
): String = canonicalFields(
    "server-proof",
    hello.schema,
    hello.deviceId,
    hello.clientNonce,
    challenge.serverNonce,
    challenge.sessionId,
    challenge.expiresAtMillis.toString(),
    challenge.macId,
)

fun requestAuthTranscript(envelope: ReactiveRequestEnvelope): String = canonicalFields(
    "request",
    envelope.schema,
    envelope.sessionId,
    envelope.sequence.toString(),
    envelope.requestId,
    envelope.deadlineMillis.toString(),
    envelope.bodyJson,
)

fun responseAuthTranscript(envelope: ReactiveResponseEnvelope): String = canonicalFields(
    "response",
    envelope.schema,
    envelope.sessionId,
    envelope.sequence.toString(),
    envelope.requestId,
    envelope.status.name,
    envelope.code.orEmpty(),
    envelope.retryable.toString(),
    envelope.bodyJson.orEmpty(),
)

/**
 * Per-session replay/deadline gate. Authentication is supplied by the
 * platform crypto adapter; state advances only after authentication succeeds.
 */
class ReactiveReplayGuard(
    private val sessionId: String,
    private val verifyAuth: (ReactiveRequestEnvelope) -> Boolean,
) {
    private val sequenceGuard = StrictSequenceGuard(sessionId)

    fun accept(envelope: ReactiveRequestEnvelope, nowMillis: Long): Boolean {
        validateRequestEnvelope(envelope, nowMillis)
        if (!verifyAuth(envelope)) return false
        return sequenceGuard.accept(envelope.sessionId, envelope.sequence, envelope.requestId)
    }

    fun lastAcceptedSequence(): Long = sequenceGuard.lastAcceptedSequence()
}

class ReactiveResponseReplayGuard(
    private val sessionId: String,
    private val verifyAuth: (ReactiveResponseEnvelope) -> Boolean,
) {
    private val sequenceGuard = StrictSequenceGuard(sessionId)

    fun accept(envelope: ReactiveResponseEnvelope): Boolean {
        validateResponseEnvelope(envelope)
        if (!verifyAuth(envelope)) return false
        return sequenceGuard.accept(envelope.sessionId, envelope.sequence, envelope.requestId)
    }

    fun lastAcceptedSequence(): Long = sequenceGuard.lastAcceptedSequence()
}

object ReactiveFrameCodec {
    fun encode(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "frame payload must not be empty" }
        require(payload.size <= REACTIVE_MAX_BODY_BYTES) { "frame payload exceeds limit" }
        val size = payload.size
        return byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ) + payload
    }

    fun decode(frame: ByteArray): ByteArray {
        require(frame.size >= 4) { "frame header incomplete" }
        val size = ((frame[0].toInt() and 0xff) shl 24) or
            ((frame[1].toInt() and 0xff) shl 16) or
            ((frame[2].toInt() and 0xff) shl 8) or
            (frame[3].toInt() and 0xff)
        require(size in 1..REACTIVE_MAX_BODY_BYTES) { "invalid frame length" }
        require(frame.size == size + 4) { "frame length mismatch" }
        return frame.copyOfRange(4, frame.size)
    }
}

private class StrictSequenceGuard(
    private val sessionId: String,
) {
    private var highestSequence = 0L
    private val completedRequests = linkedSetOf<String>()

    fun accept(candidateSessionId: String, sequence: Long, requestId: String): Boolean {
        if (candidateSessionId != sessionId) return false
        if (sequence != highestSequence + 1) return false
        if (requestId in completedRequests) return false
        highestSequence = sequence
        completedRequests += requestId
        while (completedRequests.size > REACTIVE_REPLAY_CACHE_SIZE) {
            completedRequests.remove(completedRequests.first())
        }
        return true
    }

    fun lastAcceptedSequence(): Long = highestSequence
}

private fun canonicalFields(vararg values: String): String = values.joinToString("\u0000")

private fun requireToken(name: String, value: String) {
    require(value.isNotBlank()) { "$name must not be blank" }
    require(value.encodeToByteArray().size <= REACTIVE_MAX_TOKEN_BYTES) {
        "$name exceeds $REACTIVE_MAX_TOKEN_BYTES bytes"
    }
}

private fun requireBoundedBody(value: String) {
    require(value.isNotBlank()) { "body must not be blank" }
    require(value.encodeToByteArray().size <= REACTIVE_MAX_BODY_BYTES) {
        "body exceeds $REACTIVE_MAX_BODY_BYTES bytes"
    }
}
