package io.codecks.data.clipboard

import io.codecks.domain.clipboard.ClipboardBatteryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardBackoffTest {
    @Test
    fun retryBackoffIsBoundedAndMonotonic() {
        val delays = (1..ClipboardBatteryPolicy.MAX_RETRY_COUNT + 4)
            .map(ClipboardBatteryPolicy::retryDelayMillis)

        assertEquals(3_000L, delays.first())
        assertEquals(120_000L, delays.last())
        assertTrue(delays.zipWithNext().all { (first, second) -> second >= first })
        assertTrue(delays.all { it <= 120_000L })
    }
}
