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

    @Test
    fun redact_removesHeadersCookiesIpv6AndWindowsPaths() {
        val secret = "super-secret-token-12345"
        val redacted = DiagnosticRedactor.redact(
            "Authorization: Bearer $secret\nCookie: sid=$secret\nhost=2001:db8:85a3::8a2e:370:7334 " +
                "path=C:\\Users\\me\\secrets.txt",
        )

        assertFalse(redacted.contains(secret))
        assertFalse(redacted.contains("2001:db8"))
        assertFalse(redacted.contains("\\Users\\me"))
        assertTrue(redacted.contains("<redacted>"))
        assertTrue(redacted.contains("<ip>"))
        assertTrue(redacted.contains("<path>"))
    }

    @Test
    fun redact_removesProviderHeadersAndUnlabelledKnownTokenShapes() {
        val redacted = DiagnosticRedactor.redact(
            "x-api-key: key-material-123456 sk-proj-abcdefghijklmnop " +
                "aaaaaaaaaaaaaaaa.bbbbbbbbbbbbbbbb.cccccccccccccccc",
        )

        assertFalse(redacted.contains("key-material"))
        assertFalse(redacted.contains("sk-proj"))
        assertFalse(redacted.contains("aaaaaaaaaaaaaaaa"))
    }
}
