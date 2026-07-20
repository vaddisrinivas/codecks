package io.codecks.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SensitiveCharsTest {
    @Test
    fun toStringNeverPrintsSecret() {
        val secret = SensitiveChars.copyOf("hunter2")

        assertFalse(secret.toString().contains("hunter2"))
    }

    @Test
    fun copiedInputCanBeClearedWithoutMutatingSource() {
        val source = "abc".toCharArray()
        val secret = SensitiveChars.copyOf(source)
        source.fill('x')

        assertEquals("abc", String(secret.copyChars()))
        secret.close()
        assertNotEquals("abc", String(source))
    }

    @Test(expected = IllegalStateException::class)
    fun closedValueCannotBeRead() {
        val secret = SensitiveChars.copyOf("abc")
        secret.close()

        secret.copyChars()
    }
}
