package io.codecks.data.targets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.util.Base64

class OpenSshPublicKeyEncoderTest {
    @Test
    fun rsaPublicKeyEncodesAsOpenSshWirePayload() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val encoded = OpenSshPublicKeyEncoder.encode(keyPair.public, "codecks-test")
        val parts = encoded.split(" ")

        assertEquals("ssh-rsa", parts[0])
        assertEquals("codecks-test", parts[2])
        assertEquals("ssh-rsa", firstSshString(Base64.getDecoder().decode(parts[1])))
    }

    @Test
    fun commentCannotInjectNewLine() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val encoded = OpenSshPublicKeyEncoder.encode(keyPair.public, "codecks\nbad")

        assertFalse(encoded.contains("\n"))
        assertTrue(encoded.endsWith("codecks bad"))
    }

    private fun firstSshString(payload: ByteArray): String {
        val buffer = ByteBuffer.wrap(payload)
        val size = buffer.int
        val bytes = ByteArray(size)
        buffer.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }
}
