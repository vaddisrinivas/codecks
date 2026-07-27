package io.codecks.platform.helper

import kotlinx.coroutines.flow.Flow

data class DiscoveredReactiveHelper(
    val serviceName: String,
    val host: String,
    val port: Int,
    val protocolSchema: String,
) {
    init {
        require(serviceName.isNotBlank()) { "serviceName must not be blank" }
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65_535) { "port must be valid" }
        require(protocolSchema.isNotBlank()) { "protocolSchema must not be blank" }
    }
}

/**
 * Discovery names locate helpers but never establish identity. Identity is
 * accepted only after the authenticated client verifies its pinned Mac ID.
 */
interface ReactiveHelperDiscovery {
    val helpers: Flow<List<DiscoveredReactiveHelper>>
    fun start()
    fun stop()
}

data class StoredReactiveHelperIdentity(
    val macId: String,
    val displayName: String,
    val secretAlias: String,
) {
    init {
        require(macId.isNotBlank()) { "macId must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(secretAlias.isNotBlank()) { "secretAlias must not be blank" }
    }
}

/**
 * Secret aliases point to non-exportable Android Keystore material. Raw
 * pairing secrets must never be returned by this persistence boundary.
 */
interface ReactiveHelperIdentityStore {
    suspend fun identities(): List<StoredReactiveHelperIdentity>
    suspend fun save(identity: StoredReactiveHelperIdentity)
    suspend fun forget(macId: String)
}

data class ReactiveReconnectPolicy(
    val delaysMillis: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L),
) {
    init {
        require(delaysMillis.isNotEmpty()) { "Reconnect policy must not be empty" }
        require(delaysMillis.all { it > 0 }) { "Reconnect delays must be positive" }
        require(delaysMillis == delaysMillis.sorted()) { "Reconnect delays must be nondecreasing" }
    }

    fun delayForAttempt(attempt: Int): Long =
        delaysMillis[attempt.coerceIn(0, delaysMillis.lastIndex)]
}
