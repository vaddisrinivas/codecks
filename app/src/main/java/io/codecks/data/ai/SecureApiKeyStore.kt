package io.codecks.data.ai

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecureApiKeyStore {
    suspend fun hasKey(providerId: String): Boolean
    suspend fun saveKey(providerId: String, key: SecretValue)
    suspend fun loadKey(providerId: String): SecretValue?
    suspend fun deleteKey(providerId: String)
}

class SecretValue private constructor(private val raw: String) {
    fun revealForProviderCall(): String = raw

    override fun toString(): String = REDACTED

    companion object {
        const val REDACTED = "[redacted]"

        fun of(raw: String): SecretValue {
            require(raw.isNotBlank()) { "Secret value cannot be blank" }
            return SecretValue(raw)
        }
    }
}

private val Context.aiCredentialDataStore by preferencesDataStore(name = "ai_credentials")

class AndroidSecureApiKeyStore(
    private val context: Context,
) : SecureApiKeyStore {
    override suspend fun hasKey(providerId: String): Boolean = loadCiphertext(providerId) != null

    override suspend fun saveKey(providerId: String, key: SecretValue) {
        val sealed = EncryptedApiKeyCodec(providerId).encrypt(key.revealForProviderCall())
        context.aiCredentialDataStore.edit { preferences ->
            preferences[keyFor(providerId)] = sealed
        }
    }

    override suspend fun loadKey(providerId: String): SecretValue? {
        val sealed = loadCiphertext(providerId) ?: return null
        return SecretValue.of(EncryptedApiKeyCodec(providerId).decrypt(sealed))
    }

    override suspend fun deleteKey(providerId: String) {
        context.aiCredentialDataStore.edit { preferences ->
            preferences.remove(keyFor(providerId))
        }
    }

    private suspend fun loadCiphertext(providerId: String): String? =
        context.aiCredentialDataStore.data.first()[keyFor(providerId)]

    private fun keyFor(providerId: String) = stringPreferencesKey("api_key_$providerId")
}

internal class EncryptedApiKeyCodec(
    private val providerId: String,
) {
    fun encrypt(raw: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(raw.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    fun decrypt(sealed: String): String {
        val decoded = Base64.decode(sealed, Base64.NO_WRAP)
        require(decoded.size > IV_LENGTH_BYTES) { "Stored secret is corrupted" }
        val iv = decoded.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = decoded.copyOfRange(IV_LENGTH_BYTES, decoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val alias = aliasFor(providerId)
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEY_STORE)
        val spec =
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun aliasFor(providerId: String): String = "io.codecks.ai.$providerId"

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
