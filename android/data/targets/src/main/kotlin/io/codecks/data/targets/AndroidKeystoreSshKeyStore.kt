package io.codecks.data.targets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey

class AndroidKeystoreSshKeyStore(
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) },
) {
    fun getOrCreateKeyPair(alias: String): KeyPair {
        val existingPrivateKey = keyStore.getKey(alias, null) as? PrivateKey
        val existingPublicKey = keyStore.getCertificate(alias)?.publicKey
        if (existingPrivateKey != null && existingPublicKey != null) {
            return KeyPair(existingPublicKey, existingPrivateKey)
        }
        return generateKeyPair(alias)
    }

    fun publicKeyOpenSsh(alias: String, comment: String): String =
        OpenSshPublicKeyEncoder.encode(getOrCreateKeyPair(alias).public, comment)

    fun delete(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    private fun generateKeyPair(alias: String): KeyPair {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setKeySize(KEY_SIZE_BITS)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 3072
    }
}
