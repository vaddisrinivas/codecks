package io.codecks.platform.helper

import io.codecks.core.reactive.ReactiveHelperActionExecution
import io.codecks.shared.protocol.HelperIdentityPin
import io.codecks.shared.protocol.HelperTrustState
import io.codecks.shared.protocol.ReactiveAuthResult
import io.codecks.shared.protocol.ReactiveChallenge
import io.codecks.shared.protocol.ReactiveFrameCodec
import io.codecks.shared.protocol.ReactiveHello
import io.codecks.shared.protocol.ReactiveHelperActionReceipt
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

class ReactiveHelperSessionManagerTest {
    private val secret = ByteArray(32) { index -> (index + 1).toByte() }
    private val endpoint = ReactiveHelperEndpoint("127.0.0.1", 47321)
    private val identity = StoredReactiveHelperIdentity(
        macId = "mac-1",
        displayName = "Desk Mac",
        helperId = "helper-1",
        publicKeyFingerprint = "a".repeat(64),
        secretAlias = "codecks-helper-mac-1",
    )
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
    }

    @Test
    fun missingPinnedIdentityKeepsHelperActionsUnavailable() = runTest {
        val manager = manager(identityStore = FakeIdentityStore(emptyList()))

        val status = manager.connect(endpoint)
        val result = manager.actionClient.value.execute(executeRequest(), deadlineMillis = 11_000L)

        assertEquals(ReactiveHelperSessionStatus.Failed("helper_identity_missing"), status)
        assertEquals(ReactiveHelperActionExecution.Unsupported("helper_unavailable"), result)
    }

    @Test
    fun missingSecretAliasKeepsHelperActionsUnavailable() = runTest {
        val manager = manager(secretStore = FakeSecretStore(emptyMap()))

        val status = manager.connect(endpoint)

        assertEquals(ReactiveHelperSessionStatus.Failed("helper_secret_missing"), status)
        assertEquals(ReactiveHelperSessionStatus.Failed("helper_secret_missing"), manager.status.value)
    }

    @Test
    fun authenticatedPinnedHelperExposesExecutableActionClient() = runTest {
        val transport = FakeSessionTransport(secret, json)
        val manager = manager(transportFactory = FakeTransportFactory(transport))

        val status = manager.connect(endpoint)
        val result = manager.actionClient.value.execute(executeRequest(), deadlineMillis = 11_000L)

        assertEquals(
            ReactiveHelperSessionStatus.Connected(
                macId = "mac-1",
                sessionId = "session-1",
                expiresAtMillis = 20_000L,
            ),
            status,
        )
        assertEquals(
            ReactiveHelperActionExecution.Succeeded(
                resultCode = "shortcut_completed",
                helperReceiptId = "receipt-1",
                completedAtMillis = 10_100L,
                undoToken = "undo-1",
            ),
            result,
        )
        assertTrue(transport.closeCount == 0)
    }

    @Test
    fun helperWithDifferentMacIdentityIsRejectedAndClosed() = runTest {
        val transport = FakeSessionTransport(secret, json, macId = "other-mac")
        val manager = manager(transportFactory = FakeTransportFactory(transport))

        val status = manager.connect(endpoint)
        val result = manager.actionClient.value.execute(executeRequest(), deadlineMillis = 11_000L)

        assertEquals(ReactiveHelperSessionStatus.Failed("helper_identity_mismatch"), status)
        assertEquals(ReactiveHelperActionExecution.Unsupported("helper_unavailable"), result)
        assertEquals(1, transport.closeCount)
    }

    private fun manager(
        identityStore: ReactiveHelperIdentityStore = FakeIdentityStore(listOf(identity)),
        secretStore: ReactiveHelperSecretStore = FakeSecretStore(mapOf(identity.secretAlias to secret)),
        transportFactory: ReactiveHelperTransportFactory = FakeTransportFactory(FakeSessionTransport(secret, json)),
    ) = ReactiveHelperSessionManager(
        identityStore = identityStore,
        secretStore = secretStore,
        transportFactory = transportFactory,
        deviceId = "phone-1",
        nowMillis = { 10_000L },
        nonceFactory = { "client-nonce" },
    )

    private fun executeRequest() = ReactiveHelperRequest.Execute(
        actionId = "apple_shortcuts.run",
        actionRevision = "rev-1",
        operationId = "op-1",
        idempotencyKey = "idem-1",
        timeoutMillis = 1_000L,
        cancellationToken = "cancel-1",
        arguments = mapOf("name" to "Focus Mode"),
    )
}

private class FakeIdentityStore(
    private val values: List<StoredReactiveHelperIdentity>,
) : ReactiveHelperIdentityStore {
    override suspend fun identities(): List<StoredReactiveHelperIdentity> = values
    override suspend fun save(identity: StoredReactiveHelperIdentity) = Unit
    override suspend fun forget(macId: String) = Unit
}

private class FakeSecretStore(
    private val values: Map<String, ByteArray>,
) : ReactiveHelperSecretStore {
    override suspend fun secret(alias: String): ByteArray? = values[alias]?.copyOf()
}

private class FakeTransportFactory(
    private val transport: ReactiveHelperTransport,
) : ReactiveHelperTransportFactory {
    override suspend fun connect(endpoint: ReactiveHelperEndpoint): ReactiveHelperTransport = transport
}

private class FakeSessionTransport(
    private val secret: ByteArray,
    private val json: Json,
    private val macId: String = "mac-1",
    private val helperIdentity: HelperIdentityPin = helperPin(),
) : ReactiveHelperTransport {
    private var hello: ReactiveHello? = null
    private var challenge: ReactiveChallenge? = null
    var closeCount = 0
        private set

    override suspend fun exchange(frame: ByteArray): ByteArray {
        val payload = ReactiveFrameCodec.decode(frame).decodeToString()
        return when {
            payload.contains("\"clientNonce\"") -> onHello(payload)
            payload.contains("\"proof\"") -> onProof(payload)
            payload.contains("\"bodyJson\"") -> onRequest(payload)
            else -> error("Unexpected helper payload")
        }
    }

    override suspend fun close() {
        closeCount += 1
    }

    private fun onHello(payload: String): ByteArray {
        hello = json.decodeFromString<ReactiveHello>(payload)
        challenge = ReactiveChallenge(
            sessionId = "session-1",
            serverNonce = "server-nonce",
            expiresAtMillis = 20_000L,
            macId = macId,
            helperIdentity = helperIdentity,
            verificationCode = "123456",
        )
        return encode(checkNotNull(challenge))
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
        assertTrue(
            MessageDigest.isEqual(
                proof.pinAcknowledgement.encodeToByteArray(),
                hmacHex(secret, pinAcknowledgementTranscript(currentChallenge)).encodeToByteArray(),
            ),
        )
        return encode(
            ReactiveAuthResult(
                sessionId = currentChallenge.sessionId,
                accepted = true,
                expiresAtMillis = currentChallenge.expiresAtMillis,
                serverProof = hmacHex(secret, serverProofTranscript(currentHello, currentChallenge)),
                pinnedHelperIdentity = helperIdentity,
            ),
        )
    }

    private fun onRequest(payload: String): ByteArray {
        val request = json.decodeFromString<ReactiveRequestEnvelope>(payload)
        val body = json.encodeToString(
            ReactiveHelperActionReceipt.serializer(),
            ReactiveHelperActionReceipt(
                receiptId = "receipt-1",
                operationId = "op-1",
                idempotencyKey = "idem-1",
                actionId = "apple_shortcuts.run",
                actionRevision = "rev-1",
                status = ReceiptStatus.Completed,
                startedAtMillis = 10_000L,
                completedAtMillis = 10_100L,
                resultCode = "shortcut_completed",
                undoToken = "undo-1",
            ),
        )
        val unsigned = ReactiveResponseEnvelope(
            sessionId = request.sessionId,
            sequence = request.sequence,
            requestId = request.requestId,
            status = ReceiptStatus.Completed,
            bodyJson = body,
            authTag = "",
        )
        return encode(unsigned.copy(authTag = hmacHex(secret, responseAuthTranscript(unsigned))))
    }

    private inline fun <reified T> encode(value: T): ByteArray =
        ReactiveFrameCodec.encode(json.encodeToString(value).encodeToByteArray())
}

private fun helperPin() = HelperIdentityPin(
    helperId = "helper-1",
    publicKeyFingerprint = "a".repeat(64),
    issuedAtMillis = 1_000L,
    trustState = HelperTrustState.Verified,
)

private fun pinAcknowledgementTranscript(challenge: ReactiveChallenge): String =
    listOf(
        "pin-ack",
        challenge.schema,
        challenge.sessionId,
        challenge.macId,
        challenge.helperIdentity.helperId,
        challenge.helperIdentity.publicKeyFingerprint,
    ).joinToString("\u0000")

private fun hmacHex(secret: ByteArray, message: String): String = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(secret, algorithm))
    doFinal(message.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
