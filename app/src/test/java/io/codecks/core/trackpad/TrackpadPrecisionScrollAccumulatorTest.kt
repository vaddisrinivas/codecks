package io.codecks.core.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadPrecisionScrollAccumulatorTest {
    @Test
    fun tinyMovementAccumulatesInsteadOfBeingRoundedAway() {
        val accumulator = TrackpadPrecisionScrollAccumulator()
        val outputs = List(20) { accumulator.add(delta = 1.5f, speed = 0.28f, acceleration = 0f) }

        assertTrue(outputs.any { it > 0f })
    }

    @Test
    fun accelerationAddsGainOnlyForFasterMovement() {
        val plain = TrackpadPrecisionScrollAccumulator().add(24f, 0.5f, 0f)
        val accelerated = TrackpadPrecisionScrollAccumulator().add(24f, 0.5f, 1f)

        assertTrue(accelerated > plain)
        assertEquals(2f, plain, 0f)
    }

    @Test
    fun fastRailProducesMoreScrollThanSlowRail() {
        val fast = TrackpadPrecisionScrollAccumulator()
        val slow = TrackpadPrecisionScrollAccumulator()
        val fastTotal = List(12) { fast.add(8f, 1f, 0f) }.sum()
        val slowTotal = List(12) { slow.add(8f, 0.28f, 0.25f) }.sum()

        assertTrue(fastTotal > slowTotal)
        assertTrue(slowTotal > 0f)
    }
}
