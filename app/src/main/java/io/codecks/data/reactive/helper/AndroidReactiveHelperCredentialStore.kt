package io.codecks.data.reactive.helper

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.data.ai.AndroidSecureApiKeyStore
import io.codecks.data.ai.SecretValue
import io.codecks.platform.helper.ReactiveHelperIdentityStore
import io.codecks.platform.helper.ReactiveHelperSecretStore
import io.codecks.platform.helper.StoredReactiveHelperIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import io.codecks.data.persistence.BoundedPayload
import io.codecks.data.persistence.PersistenceRead
import io.codecks.data.persistence.valueForMutation

private val Context.reactiveHelperDataStore by preferencesDataStore(name = "reactive_helper_credentials")

@Singleton
class AndroidReactiveHelperCredentialStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReactiveHelperIdentityStore, ReactiveHelperSecretStore, ReactiveHelperPairingStore {
    private val secureStore = AndroidSecureApiKeyStore(context)
    private val mutationMutex = Mutex()

    override suspend fun identities(): List<StoredReactiveHelperIdentity> =
        (ReactiveHelperIdentityCodec.decode(context.reactiveHelperDataStore.data.first()[IDENTITIES]) as? PersistenceRead.Value)
            ?.value.orEmpty()

    override suspend fun save(identity: StoredReactiveHelperIdentity) {
        mutationMutex.withLock { saveIdentity(identity) }
    }

    override suspend fun forget(macId: String) {
        mutationMutex.withLock {
            var removed = emptyList<StoredReactiveHelperIdentity>()
            context.reactiveHelperDataStore.edit { preferences ->
                val current = ReactiveHelperIdentityCodec.decode(preferences[IDENTITIES])
                    .valueForMutation("Helper identities") { emptyList() }
                removed = current.filter { it.macId == macId }
                preferences[IDENTITIES] = ReactiveHelperIdentityCodec.encode(current.filterNot { it.macId == macId })
            }
            removed.forEach { secureStore.deleteKey(it.secretAlias) }
        }
    }

    override suspend fun secret(alias: String): ByteArray? =
        secureStore.loadKey(alias)
            ?.revealForProviderCall()
            ?.hexToByteArrayOrNull()

    override suspend fun savePairing(identity: StoredReactiveHelperIdentity, sharedSecret: ByteArray) {
        mutationMutex.withLock {
            val before = context.reactiveHelperDataStore.data.first()[IDENTITIES]
            val current = ReactiveHelperIdentityCodec.decode(before)
                .valueForMutation("Helper identities") { emptyList() }
            // Validate and bound the complete identity update before touching the secret.
            val prepared = ReactiveHelperIdentityCodec.encode(
                current.filterNot { it.macId == identity.macId }.plus(identity).sortedBy { it.displayName.lowercase() },
            )
            val oldSecret = secureStore.loadKey(identity.secretAlias)
            commitPreparedPairing(
                preparedIdentity = prepared,
                existingSecret = oldSecret,
                writeSecret = { secureStore.saveKey(identity.secretAlias, SecretValue.of(sharedSecret.toHex())) },
                commitIdentity = { encoded ->
                    context.reactiveHelperDataStore.edit { preferences ->
                        check(preferences[IDENTITIES] == before) { "Helper identities changed during pairing" }
                        preferences[IDENTITIES] = encoded
                    }
                },
                restoreSecret = { previous -> secureStore.saveKey(identity.secretAlias, previous) },
                deleteSecret = { secureStore.deleteKey(identity.secretAlias) },
            )
        }
    }

    private suspend fun saveIdentity(identity: StoredReactiveHelperIdentity) {
        context.reactiveHelperDataStore.edit { preferences ->
            val current = ReactiveHelperIdentityCodec.decode(preferences[IDENTITIES])
                .valueForMutation("Helper identities") { emptyList() }
            preferences[IDENTITIES] = ReactiveHelperIdentityCodec.encode(
                current.filterNot { it.macId == identity.macId }.plus(identity).sortedBy { it.displayName.lowercase() },
            )
        }
    }

    private companion object {
        val IDENTITIES = stringPreferencesKey("identities_v1")
    }
}

internal suspend fun <S> commitPreparedPairing(
    preparedIdentity: String,
    existingSecret: S?,
    writeSecret: suspend () -> Unit,
    commitIdentity: suspend (String) -> Unit,
    restoreSecret: suspend (S) -> Unit,
    deleteSecret: suspend () -> Unit,
) {
    require(preparedIdentity.isNotBlank())
    writeSecret()
    try {
        commitIdentity(preparedIdentity)
    } catch (failure: Throwable) {
        val rollback = runCatching {
            if (existingSecret == null) deleteSecret() else restoreSecret(existingSecret)
        }.exceptionOrNull()
        if (rollback != null) failure.addSuppressed(rollback)
        throw failure
    }
}

internal object ReactiveHelperIdentityCodec {
    private const val SCHEMA_VERSION = 2
    private const val MAX_IDENTITIES = 16
    private const val MAX_BYTES = 64 * 1024

    fun encode(identities: List<StoredReactiveHelperIdentity>): String {
        require(identities.size <= MAX_IDENTITIES) { "Too many helper identities" }
        val array = JSONArray()
        identities.forEach { identity ->
            array.put(
                JSONObject()
                    .put("macId", identity.macId)
                    .put("displayName", identity.displayName)
                    .put("helperId", identity.helperId)
                    .put("publicKeyFingerprint", identity.publicKeyFingerprint)
                    .put("secretAlias", identity.secretAlias)
                    .apply {
                        identity.host?.let { put("host", it) }
                        identity.port?.let { put("port", it) }
                    },
            )
        }
        return BoundedPayload.utf8(
            JSONObject().put("schemaVersion", SCHEMA_VERSION).put("identities", array).toString(),
            MAX_BYTES,
        )
    }

    fun decode(raw: String?): PersistenceRead<List<StoredReactiveHelperIdentity>> {
        if (raw == null) return PersistenceRead.Missing
        if (raw.isBlank()) return PersistenceRead.Corrupt("blank")
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return PersistenceRead.Corrupt("oversized")
        return runCatching {
            val trimmed = raw.trimStart()
            val (array, migrated) = if (trimmed.startsWith("[")) {
                JSONArray(raw) to 1
            } else {
                val root = JSONObject(raw)
                val rawVersion = root.opt("schemaVersion") as? Number ?: return PersistenceRead.Corrupt("schema")
                val version = rawVersion.toInt().takeIf { it.toDouble() == rawVersion.toDouble() }
                    ?: return PersistenceRead.Corrupt("schema")
                if (version > SCHEMA_VERSION) return PersistenceRead.FutureVersion(version, SCHEMA_VERSION)
                if (version != SCHEMA_VERSION) return PersistenceRead.Corrupt("schema")
                root.getJSONArray("identities") to null
            }
            if (array.length() > MAX_IDENTITIES) return PersistenceRead.Corrupt("count")
            PersistenceRead.Value(
                (0 until array.length()).map { index -> decodeIdentity(array.getJSONObject(index)) },
                migratedFrom = migrated,
            )
        }.getOrElse { PersistenceRead.Corrupt("decode") }
    }

    private fun decodeIdentity(item: JSONObject): StoredReactiveHelperIdentity {
        val port = if (item.has("port")) {
            val rawPort = item.opt("port") as? Number ?: error("Invalid helper port")
            rawPort.toInt().also { require(it.toDouble() == rawPort.toDouble()) }
        } else null
        return StoredReactiveHelperIdentity(
            macId = item.getString("macId"),
            displayName = item.getString("displayName"),
            helperId = item.getString("helperId"),
            publicKeyFingerprint = item.getString("publicKeyFingerprint"),
            secretAlias = item.getString("secretAlias"),
            host = if (item.has("host")) item.getString("host").takeIf(String::isNotBlank) else null,
            port = port,
        )
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToByteArrayOrNull(): ByteArray? {
    val hex = trim()
    if (hex.isEmpty() || hex.length % 2 != 0) return null
    return runCatching {
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()
}
