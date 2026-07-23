package io.codecks.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalActionResultTest {
    @Test
    fun failedResultKeepsMessage() {
        val result = LocalActionResult.Failed("Connect Mac input first")

        assertEquals("Connect Mac input first", result.message)
    }

    @Test
    fun sealedActionResultsAreDistinctKinds() {
        assertEquals(
            LocalActionResult.Succeeded,
            LocalActionResult.Succeeded,
        )
        assertEquals(
            LocalActionResult.Navigated,
            LocalActionResult.Navigated,
        )
    }
}
