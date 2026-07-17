package io.codex.s23deck.core.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointerDeltaAccumulatorTest {
    @Test
    fun subPixelMotion_accumulatesUntilWholeDeltaExists() {
        val accumulator = PointerDeltaAccumulator()

        assertNull(accumulator.consume(0.25f, 0.25f))
        assertNull(accumulator.consume(0.25f, 0.25f))
        assertNull(accumulator.consume(0.25f, 0.25f))

        assertEquals(PointerDelta(1, 1), accumulator.consume(0.25f, 0.25f))
    }

    @Test
    fun negativeSubPixelMotion_accumulatesTowardNegativeDelta() {
        val accumulator = PointerDeltaAccumulator()

        assertNull(accumulator.consume(-0.4f, 0f))
        assertNull(accumulator.consume(-0.4f, 0f))

        assertEquals(PointerDelta(-1, 0), accumulator.consume(-0.4f, 0f))
    }

    @Test
    fun reset_clearsResidualMotion() {
        val accumulator = PointerDeltaAccumulator()

        assertNull(accumulator.consume(0.75f, 0f))
        accumulator.reset()

        assertNull(accumulator.consume(0.25f, 0f))
    }
}
