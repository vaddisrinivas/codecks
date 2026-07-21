package io.codecks.domain.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun redact_removesSecretsAndPersonalIdentifiers() {
        val redacted = DiagnosticRedactor.redact(
            "password=abc token:xyz user=user@example.com host=192.168.1.44 url=https://example.com/a path=/Users/me/.ssh/id"
        )

        assertFalse(redacted.contains("abc"))
        assertFalse(redacted.contains("xyz"))
        assertFalse(redacted.contains("user@example.com"))
        assertFalse(redacted.contains("192.168.1.44"))
        assertFalse(redacted.contains("https://example.com/a"))
        assertFalse(redacted.contains("/Users/me"))
        assertTrue(redacted.contains("<redacted>"))
        assertTrue(redacted.contains("<email>"))
        assertTrue(redacted.contains("<ip>"))
        assertTrue(redacted.contains("<url>"))
        assertTrue(redacted.contains("<path>"))
    }

    @Test
    fun redact_capsLength() {
        val redacted = DiagnosticRedactor.redact("x".repeat(500), maxLength = 32)

        assertTrue(redacted.length <= 32)
    }
}
