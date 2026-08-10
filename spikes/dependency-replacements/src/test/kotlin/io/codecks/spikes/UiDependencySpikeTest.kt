package io.codecks.spikes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiDependencySpikeTest {
    @Test
    fun colorStateIsBoundedAndContrastWarningCanBeComputed() {
        assertEquals(0f, ColorSafety.boundedChannel(-1f))
        assertEquals(1f, ColorSafety.boundedChannel(2f))
        assertTrue(ColorSafety.contrastRatio(1f, 0f) >= 4.5f)
        assertTrue(ColorSafety.contrastRatio(0.55f, 0.5f) < 4.5f)
    }

}
