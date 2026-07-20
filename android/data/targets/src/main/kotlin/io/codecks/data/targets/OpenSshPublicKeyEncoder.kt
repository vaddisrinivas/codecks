package io.codecks.data.targets

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

object OpenSshPublicKeyEncoder {
    fun encode(publicKey: PublicKey, comment: String): String = when (publicKey) {
        is RSAPublicKey -> encodeRsa(publicKey, comment)
        else -> error("Unsupported SSH public key algorithm: ${publicKey.algorithm}")
    }

    private fun encodeRsa(publicKey: RSAPublicKey, comment: String): String {
        val payload = ByteArrayOutputStream().apply {
            writeSshString("ssh-rsa".toByteArray(Charsets.US_ASCII))
            writeMpint(publicKey.publicExponent)
            writeMpint(publicKey.modulus)
        }.toByteArray()
        return "ssh-rsa ${Base64.getEncoder().encodeToString(payload)} ${comment.sanitizeComment()}"
    }

    private fun ByteArrayOutputStream.writeSshString(bytes: ByteArray) {
        writeUInt32(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeMpint(value: BigInteger) {
        writeSshString(value.toByteArray())
    }

    private fun ByteArrayOutputStream.writeUInt32(value: Int) {
        write(byteArrayOf(
            ((value ushr 24) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            (value and 0xff).toByte(),
        ))
    }

    private fun String.sanitizeComment(): String = replace(Regex("[\\r\\n\\t]"), " ").trim()
}
