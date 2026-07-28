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
import org.json.JSONArray
import org.json.JSONObject

private val Context.reactiveHelperDataStore by preferencesDataStore(name = "reactive_helper_credentials")

@Singleton
class AndroidReactiveHelperCredentialStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReactiveHelperIdentityStore, ReactiveHelperSecretStore, ReactiveHelperPairingStore {
    private val secureStore = AndroidSecureApiKeyStore(context)

    override suspend fun identities(): List<StoredReactiveHelperIdentity> =
        context.reactiveHelperDataStore.data.first()[IDENTITIES]
            ?.decodeIdentities()
            .orEmpty()

    override suspend fun save(identity: StoredReactiveHelperIdentity) {
        val next = identities()
            .filterNot { it.macId == identity.macId }
            .plus(identity)
            .sortedBy { it.displayName.lowercase() }
        context.reactiveHelperDataStore.edit { preferences ->
            preferences[IDENTITIES] = next.encodeIdentities()
        }
    }

    override suspend fun forget(macId: String) {
        val current = identities()
        val removed = current.filter { it.macId == macId }
        context.reactiveHelperDataStore.edit { preferences ->
            preferences[IDENTITIES] = current.filterNot { it.macId == macId }.encodeIdentities()
        }
        removed.forEach { secureStore.deleteKey(it.secretAlias) }
    }

    override suspend fun secret(alias: String): ByteArray? =
        secureStore.loadKey(alias)
            ?.revealForProviderCall()
            ?.hexToByteArrayOrNull()

    override suspend fun savePairing(identity: StoredReactiveHelperIdentity, sharedSecret: ByteArray) {
        secureStore.saveKey(identity.secretAlias, SecretValue.of(sharedSecret.toHex()))
        save(identity)
    }

    private companion object {
        val IDENTITIES = stringPreferencesKey("identities_v1")
    }
}

private fun List<StoredReactiveHelperIdentity>.encodeIdentities(): String {
    val array = JSONArray()
    forEach { identity ->
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
    return array.toString()
}

private fun String.decodeIdentities(): List<StoredReactiveHelperIdentity> {
    val array = JSONArray(this)
    return (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        StoredReactiveHelperIdentity(
            macId = item.getString("macId"),
            displayName = item.getString("displayName"),
            helperId = item.getString("helperId"),
            publicKeyFingerprint = item.getString("publicKeyFingerprint"),
            secretAlias = item.getString("secretAlias"),
            host = item.optString("host").takeIf(String::isNotBlank),
            port = if (item.has("port")) item.getInt("port") else null,
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
