package io.codecks.platform.helper

import io.codecks.shared.protocol.REACTIVE_PROTOCOL_SCHEMA
import io.codecks.shared.protocol.HelperIdentityPin
import io.codecks.shared.protocol.ReactiveAuthResult
import io.codecks.shared.protocol.ReactiveChallenge
import io.codecks.shared.protocol.ReactiveFrameCodec
import io.codecks.shared.protocol.ReactiveHello
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.shared.protocol.ReactiveProof
import io.codecks.shared.protocol.ReactiveRequestEnvelope
import io.codecks.shared.protocol.ReactiveResponseEnvelope
import io.codecks.shared.protocol.ReactiveResponseReplayGuard
import io.codecks.shared.protocol.clientProofTranscript
import io.codecks.shared.protocol.requestAuthTranscript
import io.codecks.shared.protocol.responseAuthTranscript
import io.codecks.shared.protocol.serverProofTranscript
import io.codecks.shared.protocol.validateAuthResult
import io.codecks.shared.protocol.validateChallenge
import io.codecks.shared.protocol.validateHelperIdentityPin
import io.codecks.shared.protocol.validateHello
import io.codecks.shared.protocol.validateRequestEnvelope
import io.codecks.shared.protocol.validateResponseEnvelope
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Framed transport boundary for the native Mac helper. It owns no policy. */
interface ReactiveHelperTransport {
    suspend fun exchange(frame: ByteArray): ByteArray
    suspend fun close() = Unit
}

data class ReactiveHelperCredentials(
    val expectedMacId: String,
    val pinnedHelperIdentity: HelperIdentityPin,
    val sharedSecret: ByteArray,
) {
    init {
        require(expectedMacId.isNotBlank()) { "expectedMacId must not be blank" }
        validateHelperIdentityPin(pinnedHelperIdentity)
        require(sharedSecret.size >= 32) { "Reactive helper secret must contain at least 256 bits" }
    }
}

data class ReactiveHelperSession(
    val sessionId: String,
    val macId: String,
    val expiresAtMillis: Long,
)

sealed interface ReactiveHelperClientState {
    data object Disconnected : ReactiveHelperClientState
    data object Authenticating : ReactiveHelperClientState
    data class Connected(val session: ReactiveHelperSession) : ReactiveHelperClientState
    data class Failed(val code: String) : ReactiveHelperClientState
}

/**
 * Authenticated, ordered helper client scaffold.
 *
 * Pairing-secret persistence and discovery are separate adapters. This client
 * never logs, exports, or stores secret bytes.
 */
class ReactiveHelperClient(
    private val transport: ReactiveHelperTransport,
    private val deviceId: String,
    credentials: ReactiveHelperCredentials,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        encodeDefaults = true
    },
) {
    private val expectedMacId = credentials.expectedMacId
    private val pinnedHelperIdentity = credentials.pinnedHelperIdentity
    private val sharedSecret = credentials.sharedSecret.copyOf()
    private val requestMutex = Mutex()
    private val _state = MutableStateFlow<ReactiveHelperClientState>(ReactiveHelperClientState.Disconnected)
    val state: StateFlow<ReactiveHelperClientState> = _state.asStateFlow()

    private var session: ReactiveHelperSession? = null
    private var sequence = 0L
    private var responseGuard: ReactiveResponseReplayGuard? = null

    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
    }

    suspend fun open(clientNonce: String): ReactiveHelperSession = requestMutex.withLock {
        _state.value = ReactiveHelperClientState.Authenticating
        runCatching {
            val hello = ReactiveHello(
                deviceId = deviceId,
                clientNonce = clientNonce,
                clientTimeMillis = nowMillis(),
            )
            validateHello(hello)
            val challenge = decode<ReactiveChallenge>(transport.exchange(encode(hello)))
            validateChallenge(challenge, nowMillis())
            require(challenge.macId == expectedMacId) { "Helper identity mismatch" }
            require(challenge.helperIdentity.helperId == pinnedHelperIdentity.helperId) {
                "Helper identity mismatch"
            }
            require(
                constantTimeEquals(
                    challenge.helperIdentity.publicKeyFingerprint,
                    pinnedHelperIdentity.publicKeyFingerprint,
                ),
            ) { "Helper identity mismatch" }

            val proof = ReactiveProof(
                sessionId = challenge.sessionId,
                proof = hmacHex(clientProofTranscript(hello, challenge)),
                pinAcknowledgement = hmacHex(pinAcknowledgementTranscript(challenge)),
            )
            val authResult = decode<ReactiveAuthResult>(transport.exchange(encode(proof)))
            validateAuthResult(authResult, challenge.sessionId, nowMillis())
            require(authResult.accepted) { authResult.code ?: "Helper authentication denied" }
            authResult.pinnedHelperIdentity?.let { serverPin ->
                require(serverPin.helperId == pinnedHelperIdentity.helperId) {
                    "Helper identity mismatch"
                }
                require(
                    constantTimeEquals(
                        serverPin.publicKeyFingerprint,
                        pinnedHelperIdentity.publicKeyFingerprint,
                    ),
                ) { "Helper identity mismatch" }
            }
            require(
                constantTimeEquals(
                    authResult.serverProof,
                    hmacHex(serverProofTranscript(hello, challenge)),
                ),
            ) { "Helper server proof mismatch" }

            ReactiveHelperSession(
                sessionId = challenge.sessionId,
                macId = challenge.macId,
                expiresAtMillis = minOf(challenge.expiresAtMillis, authResult.expiresAtMillis),
            ).also { opened ->
                session = opened
                sequence = 0L
                responseGuard = ReactiveResponseReplayGuard(opened.sessionId) { envelope ->
                    constantTimeEquals(envelope.authTag, hmacHex(responseAuthTranscript(envelope)))
                }
                _state.value = ReactiveHelperClientState.Connected(opened)
            }
        }.getOrElse { error ->
            clearSession()
            _state.value = ReactiveHelperClientState.Failed(error.toFailureCode())
            throw error
        }
    }

    suspend fun request(
        request: ReactiveHelperRequest,
        deadlineMillis: Long = nowMillis() + DEFAULT_REQUEST_TIMEOUT_MS,
    ): ReactiveResponseEnvelope = requestMutex.withLock {
        val active = session ?: error("Helper session is not open")
        check(active.expiresAtMillis >= nowMillis()) { "Helper session expired" }
        require(deadlineMillis >= nowMillis()) { "deadline already expired" }

        val nextSequence = sequence + 1L
        val requestId = "$deviceId-$nextSequence"
        val bodyJson = json.encodeToString(ReactiveHelperRequest.serializer(), request)
        val unsigned = ReactiveRequestEnvelope(
            sessionId = active.sessionId,
            sequence = nextSequence,
            requestId = requestId,
            deadlineMillis = deadlineMillis,
            bodyJson = bodyJson,
            authTag = "",
        )
        val envelope = unsigned.copy(authTag = hmacHex(requestAuthTranscript(unsigned)))
        validateRequestEnvelope(envelope, nowMillis())

        val response = decode<ReactiveResponseEnvelope>(transport.exchange(encode(envelope)))
        validateResponseEnvelope(response)
        check(response.sessionId == active.sessionId) { "Helper response session mismatch" }
        check(response.sequence == nextSequence) { "Helper response sequence mismatch" }
        check(response.requestId == requestId) { "Helper response request mismatch" }
        check(responseGuard?.accept(response) == true) { "Helper response replay/authentication rejected" }
        sequence = nextSequence
        response
    }

    suspend fun close() = requestMutex.withLock {
        clearSession()
        _state.value = ReactiveHelperClientState.Disconnected
        transport.close()
    }

    private fun clearSession() {
        session = null
        sequence = 0L
        responseGuard = null
    }

    private inline fun <reified T> decode(frame: ByteArray): T =
        json.decodeFromString(ReactiveFrameCodec.decode(frame).decodeToString())

    private inline fun <reified T> encode(value: T): ByteArray =
        ReactiveFrameCodec.encode(json.encodeToString(value).encodeToByteArray())

    private fun hmacHex(message: String): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(sharedSecret, algorithm))
        doFinal(message.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun pinAcknowledgementTranscript(challenge: ReactiveChallenge): String =
        listOf(
            "pin-ack",
            challenge.schema,
            challenge.sessionId,
            challenge.macId,
            challenge.helperIdentity.helperId,
            challenge.helperIdentity.publicKeyFingerprint,
        ).joinToString("\u0000")

    private fun constantTimeEquals(actual: String, expected: String): Boolean =
        MessageDigest.isEqual(actual.encodeToByteArray(), expected.encodeToByteArray())

    private fun Throwable.toFailureCode(): String = when {
        message?.contains("identity", ignoreCase = true) == true -> "identity_mismatch"
        message?.contains("proof", ignoreCase = true) == true -> "authentication_failed"
        message?.contains("expired", ignoreCase = true) == true -> "expired"
        else -> "protocol_error"
    }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MS = 3_000L
    }
}
