package io.codecks.core.trackpad

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
        assertEquals(18f, settings.tapMovementThresholdPx, 0.0001f)
        assertEquals(0, settings.tapCorrectionCount)
        assertEquals(0.48f, settings.backgroundOpacity, 0.0001f)
        assertTrue(settings.precisionScrollRailEnabled)
        assertEquals(0.28f, settings.precisionScrollSpeed, 0.0001f)
        assertEquals(0.25f, settings.precisionScrollAcceleration, 0.0001f)
        assertEquals(TrackpadGestureAction.WindowSwitcher, settings.twoFingerDoubleTapAction)
        assertEquals(TrackpadGestureAction.AppSwitcher, settings.threeFingerDoubleTapAction)
        assertEquals(TrackpadGestureAction.WindowSwitcher, settings.threeFingerHoldAction)
        assertEquals(TrackpadGestureAction.MissionControl, settings.fourFingerDoubleTapAction)
        assertEquals(TrackpadGestureAction.ShowDesktop, settings.fourFingerHoldAction)
        assertEquals(520, settings.multiFingerHoldMillis)
    }
}
