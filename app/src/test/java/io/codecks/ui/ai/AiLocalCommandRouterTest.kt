package io.codecks.ui.ai

import io.codecks.core.trackpad.TrackpadSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiLocalCommandRouterTest {
    @Test
    fun createButtonPromptFallsThroughToAiGeneration() = runTest {
        var openedDeck = false

        val result = handleLocalAiCommand(
            prompt = "create a love emoji confetti button",
            actions = emptyList(),
            onRunAction = {},
            trackpadSettings = TrackpadSettings(),
            onTrackpadSettingsChange = null,
            onThemeModeChange = {},
            onThemeAccentChange = {},
            onThemeSurfaceStyleChange = {},
            onThemeBorderStyleChange = {},
            onThemeShapeStyleChange = {},
            onOpenDeck = { openedDeck = true },
            onOpenTrackpad = {},
            onOpenSettings = {},
            onOpenAiSettings = {},
        )

        assertNull(result)
        assertEquals(false, openedDeck)
    }

    @Test
    fun plainDeckPromptStillOpensDeck() = runTest {
        var openedDeck = false

        val result = handleLocalAiCommand(
            prompt = "open deck",
            actions = emptyList(),
            onRunAction = {},
            trackpadSettings = TrackpadSettings(),
            onTrackpadSettingsChange = null,
            onThemeModeChange = {},
            onThemeAccentChange = {},
            onThemeSurfaceStyleChange = {},
            onThemeBorderStyleChange = {},
            onThemeShapeStyleChange = {},
            onOpenDeck = { openedDeck = true },
            onOpenTrackpad = {},
            onOpenSettings = {},
            onOpenAiSettings = {},
        )

        assertEquals("Opened Deck.", result?.message)
        assertEquals(true, openedDeck)
    }
}
