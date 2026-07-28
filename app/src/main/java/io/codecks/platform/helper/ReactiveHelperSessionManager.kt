package io.codecks.platform.helper

import io.codecks.core.reactive.ReactiveHelperActionClient
import io.codecks.core.reactive.ReactiveHelperClientActionClient
import io.codecks.core.reactive.UnavailableReactiveHelperActionClient
import io.codecks.shared.protocol.HelperIdentityPin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReactiveHelperEndpoint(
    val host: String,
    val port: Int,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65_535) { "port must be valid" }
    }
}

interface ReactiveHelperTransportFactory {
    suspend fun connect(endpoint: ReactiveHelperEndpoint): ReactiveHelperTransport
}

/**
 * Resolves Android Keystore aliases to in-memory session material.
 *
 * Implementations must not log, persist, or expose returned bytes beyond the
 * immediate authenticated helper session setup.
 */
interface ReactiveHelperSecretStore {
    suspend fun secret(alias: String): ByteArray?
}

sealed interface ReactiveHelperSessionStatus {
    data object Idle : ReactiveHelperSessionStatus
    data class Connecting(val macId: String) : ReactiveHelperSessionStatus
    data class Connected(
        val macId: String,
        val sessionId: String,
        val expiresAtMillis: Long,
    ) : ReactiveHelperSessionStatus

    data class Failed(val code: String) : ReactiveHelperSessionStatus
}

/**
 * App-level helper session boundary.
 *
 * Discovery finds a host. This manager only trusts a helper after a stored
 * pinned identity plus alias-backed secret complete the authenticated protocol.
 */
class ReactiveHelperSessionManager(
    private val identityStore: ReactiveHelperIdentityStore,
    private val secretStore: ReactiveHelperSecretStore,
    private val transportFactory: ReactiveHelperTransportFactory,
    private val deviceId: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nonceFactory: () -> String = { "nonce-${System.nanoTime().toString(16)}" },
) {
    private val _status = MutableStateFlow<ReactiveHelperSessionStatus>(ReactiveHelperSessionStatus.Idle)
    val status: StateFlow<ReactiveHelperSessionStatus> = _status.asStateFlow()

    private val _actionClient = MutableStateFlow<ReactiveHelperActionClient>(UnavailableReactiveHelperActionClient)
    val actionClient: StateFlow<ReactiveHelperActionClient> = _actionClient.asStateFlow()

    private val _client = MutableStateFlow<ReactiveHelperClient?>(null)
    val client: StateFlow<ReactiveHelperClient?> = _client.asStateFlow()

    private var activeClient: ReactiveHelperClient? = null

    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
    }

    suspend fun connect(
        endpoint: ReactiveHelperEndpoint,
        macId: String? = null,
    ): ReactiveHelperSessionStatus {
        disconnect()
        val identity = selectIdentity(macId)
            ?: return fail("helper_identity_missing")
        _status.value = ReactiveHelperSessionStatus.Connecting(identity.macId)

        val storedSecret = secretStore.secret(identity.secretAlias)
            ?: return fail("helper_secret_missing")
        val sessionSecret = storedSecret.copyOf()
        val transport = runCatching { transportFactory.connect(endpoint) }
            .getOrElse { return fail(it.toSessionFailureCode()) }

        return runCatching {
            val client = ReactiveHelperClient(
                transport = transport,
                deviceId = deviceId,
                credentials = ReactiveHelperCredentials(
                    expectedMacId = identity.macId,
                    pinnedHelperIdentity = identity.toHelperIdentityPin(),
                    sharedSecret = sessionSecret,
                ),
                nowMillis = nowMillis,
            )
            val session = client.open(nonceFactory())
            check(session.macId == identity.macId) { "Helper identity mismatch" }
            activeClient = client
            _client.value = client
            _actionClient.value = ReactiveHelperClientActionClient(client)
            ReactiveHelperSessionStatus.Connected(
                macId = session.macId,
                sessionId = session.sessionId,
                expiresAtMillis = session.expiresAtMillis,
            ).also { _status.value = it }
        }.getOrElse { error ->
            runCatching { transport.close() }
            fail(error.toSessionFailureCode())
        }.also {
            sessionSecret.fill(0)
        }
    }

    suspend fun disconnect() {
        val client = activeClient
        activeClient = null
        _client.value = null
        _actionClient.value = UnavailableReactiveHelperActionClient
        if (client != null) {
            runCatching { client.close() }
        }
        _status.value = ReactiveHelperSessionStatus.Idle
    }

    private suspend fun selectIdentity(macId: String?): StoredReactiveHelperIdentity? {
        val identities = identityStore.identities()
        return if (macId == null) {
            identities.firstOrNull()
        } else {
            identities.firstOrNull { it.macId == macId }
        }
    }

    private fun fail(code: String): ReactiveHelperSessionStatus.Failed =
        ReactiveHelperSessionStatus.Failed(code).also {
            activeClient = null
            _client.value = null
            _actionClient.value = UnavailableReactiveHelperActionClient
            _status.value = it
        }
}

private fun StoredReactiveHelperIdentity.toHelperIdentityPin(): HelperIdentityPin =
    HelperIdentityPin(
        helperId = helperId,
        publicKeyFingerprint = publicKeyFingerprint,
        issuedAtMillis = 0L,
        trustState = io.codecks.shared.protocol.HelperTrustState.Verified,
    )

private fun Throwable.toSessionFailureCode(): String = when {
    message?.contains("identity", ignoreCase = true) == true -> "helper_identity_mismatch"
    message?.contains("secret", ignoreCase = true) == true -> "helper_secret_missing"
    message?.contains("proof", ignoreCase = true) == true -> "helper_authentication_failed"
    message?.contains("expired", ignoreCase = true) == true -> "helper_session_expired"
    message?.contains("connect", ignoreCase = true) == true -> "helper_connection_failed"
    else -> "helper_session_failed"
}
