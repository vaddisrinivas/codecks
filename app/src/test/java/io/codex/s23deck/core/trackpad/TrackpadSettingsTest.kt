package io.codex.s23deck.core.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadSettingsTest {
    @Test
    fun defaultsFavorBatteryAndPreventAccidentalDoubleTap() {
        val settings = TrackpadSettings()

        assertFalse(settings.pointerTraceEnabled)
        assertTrue(settings.quietModeEnabled)
        assertEquals(120_000, settings.idleBlankTimeoutMillis)
        assertEquals(620, settings.doubleTapTimeoutMillis)
        assertEquals(10f, settings.tapMovementThresholdPx, 0.0001f)
        assertEquals(0, settings.tapCorrectionCount)
        assertEquals(0.48f, settings.backgroundOpacity, 0.0001f)
    }
}
