package io.codex.s23deck.core.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrackpadSettingsTest {
    @Test
    fun defaultsFavorBatteryAndPreventAccidentalDoubleTap() {
        val settings = TrackpadSettings()

        assertFalse(settings.pointerTraceEnabled)
        assertEquals(620, settings.doubleTapTimeoutMillis)
        assertEquals(0.48f, settings.backgroundOpacity, 0.0001f)
    }
}
