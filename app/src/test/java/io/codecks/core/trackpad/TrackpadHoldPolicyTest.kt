package io.codecks.core.trackpad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadHoldPolicyTest {
    @Test
    fun threeAndFourStationaryFingersCanTriggerHold() {
        assertTrue(shouldTriggerTrackpadHold(3, 3, 4f, 18f, hasCommand = true))
        assertTrue(shouldTriggerTrackpadHold(4, 4, 4f, 18f, hasCommand = true))
    }

    @Test
    fun movementLiftOrMissingActionCancelsHold() {
        assertFalse(shouldTriggerTrackpadHold(3, 2, 4f, 18f, hasCommand = true))
        assertFalse(shouldTriggerTrackpadHold(4, 4, 400f, 18f, hasCommand = true))
        assertFalse(shouldTriggerTrackpadHold(3, 3, 4f, 18f, hasCommand = false))
    }
}
