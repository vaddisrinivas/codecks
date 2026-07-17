package io.codex.s23deck.core.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadSessionPolicyTest {
    @Test
    fun accelerationSettingChangesPointerGain() {
        val slow = trackpadPointerGain(speed = 5f, dragging = false, stylus = false, acceleration = 0.5f)
        val normal = trackpadPointerGain(speed = 5f, dragging = false, stylus = false, acceleration = 1.0f)
        val fast = trackpadPointerGain(speed = 5f, dragging = false, stylus = false, acceleration = 1.75f)

        assertEquals(1.08f, normal, 0.0001f)
        assertTrue(slow < normal)
        assertTrue(fast > normal)
    }

    @Test
    fun scrollZoneFollowsRailSide() {
        assertTrue(isTrackpadScrollZone(x = 20f, width = 1000, railSide = TrackpadRailSide.Left))
        assertFalse(isTrackpadScrollZone(x = 980f, width = 1000, railSide = TrackpadRailSide.Left))
        assertTrue(isTrackpadScrollZone(x = 980f, width = 1000, railSide = TrackpadRailSide.Right))
        assertFalse(isTrackpadScrollZone(x = 20f, width = 1000, railSide = TrackpadRailSide.Right))
    }
}
