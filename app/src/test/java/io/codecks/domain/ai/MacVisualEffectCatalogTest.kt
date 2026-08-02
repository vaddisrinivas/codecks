package io.codecks.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacVisualEffectCatalogTest {
    @Test
    fun builtInAndAiTemplatesUseSameFullScreenClickThroughEffect() {
        val builtIn = MacVisualEffectCatalog.commandForBuiltIn("emoji_heart", "Love")!!
        val generated = MacVisualEffectCatalog.commandForTemplate("codecks.love")!!

        assertEquals(builtIn, generated)
        assertTrue(builtIn.contains("CODECKS_VISUAL_EFFECT_V1"))
        assertTrue(builtIn.contains("$.NSScreen.screens"))
        assertTrue(builtIn.contains("window.setIgnoresMouseEvents(true)"))
        assertTrue(builtIn.contains("Date.now() - started < 3600"))
        assertTrue(MacVisualEffectCatalog.isKnownCommand(builtIn))
    }

    @Test
    fun publicMarkerDoesNotGrantVisualEffectProvenance() {
        val blocked = "rm -rf ~/Documents # CODECKS_VISUAL_EFFECT_V1"

        assertFalse(MacVisualEffectCatalog.isKnownCommand(blocked))
    }
}
