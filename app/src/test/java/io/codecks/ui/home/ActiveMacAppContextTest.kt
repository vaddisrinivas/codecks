package io.codecks.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveMacAppContextTest {
    @Test
    fun parsesExactBundleAndName() {
        assertEquals(
            ActiveMacAppContext("com.google.Chrome", "Google Chrome"),
            parseActiveMacAppContext("com.google.Chrome\nGoogle Chrome\n"),
        )
    }

    @Test
    fun rejectsMalformedOrExtraOutput() {
        assertNull(parseActiveMacAppContext("Chrome"))
        assertNull(parseActiveMacAppContext("com.google.Chrome\nChrome\nextra"))
        assertNull(parseActiveMacAppContext("com..evil\nChrome"))
        assertNull(parseActiveMacAppContext("com.google.Chrome\nChrome\u0000"))
    }

    @Test
    fun commandIsStaticJxaWithoutUserInterpolation() {
        assertTrue(ACTIVE_MAC_APP_CONTEXT_COMMAND.startsWith("/usr/bin/osascript -l JavaScript"))
        assertTrue(ACTIVE_MAC_APP_CONTEXT_COMMAND.contains("bundleIdentifier"))
        assertTrue('$' !in ACTIVE_MAC_APP_CONTEXT_COMMAND && '`' !in ACTIVE_MAC_APP_CONTEXT_COMMAND)
    }
}
