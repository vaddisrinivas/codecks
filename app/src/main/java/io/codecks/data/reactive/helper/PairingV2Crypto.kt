package io.codecks.data.reactive.helper

import io.codecks.shared.protocol.pairingV2Canonical
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object PairingV2Crypto {
    fun beginProof(secret: ByteArray, transcript: String): String =
        hmac(secret, pairingV2Canonical("pairing-begin", transcript).encodeToByteArray()).toHex()

    fun deriveCredential(secret: ByteArray, transcript: String): ByteArray = hkdfSha256(
        input = secret,
        salt = pairingV2Canonical("pairing-salt", transcript).encodeToByteArray(),
        info = pairingV2Canonical("pairing-credential", transcript).encodeToByteArray(),
        outputSize = 32,
    )

    fun matchingCode(credential: ByteArray, transcript: String): String {
        val digest = hmac(credential, pairingV2Canonical("pairing-code", transcript).encodeToByteArray())
        val value = digest.take(4).fold(0L) { result, byte -> (result shl 8) or (byte.toLong() and 0xff) } % 1_000_000
        return value.toString().padStart(6, '0')
    }

    fun confirmationProof(credential: ByteArray, transcript: String): String =
        hmac(credential, pairingV2Canonical("pairing-confirm", transcript).encodeToByteArray()).toHex()

    fun serverAcceptanceProof(credential: ByteArray, transcript: String): String =
        hmac(credential, pairingV2Canonical("pairing-accepted", transcript).encodeToByteArray()).toHex()

    private fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, outputSize: Int): ByteArray {
        val pseudoRandomKey = hmac(salt, input)
        val output = ArrayList<Byte>(outputSize)
        var previous = ByteArray(0)
        var counter = 1
        while (output.size < outputSize) {
            previous = hmac(pseudoRandomKey, previous + info + counter.toByte())
            previous.forEach { if (output.size < outputSize) output += it }
            counter++
        }
        return output.toByteArray()
    }

    private fun hmac(key: ByteArray, bytes: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(bytes)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
