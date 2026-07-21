package io.codecks.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardContentGuardTest {
    @Test
    fun riskFor_allowsOrdinaryText() {
        assertNull(ClipboardContentGuard.riskFor("send the notes after lunch"))
    }

    @Test
    fun riskFor_flagsSecrets() {
        assertNotNull(ClipboardContentGuard.riskFor("OPENAI_API_KEY=sk-abc12345678901234567890"))
        assertNotNull(ClipboardContentGuard.riskFor("password: correct horse battery staple"))
    }

    @Test
    fun safePreview_hidesSensitiveText() {
        assertEquals(
            "Hidden: sensitive-looking text",
            ClipboardContentGuard.safePreview("token: abc123"),
        )
    }
}
