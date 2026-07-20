package io.codecks.transport.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class OutputCaptureTest {
    @Test
    fun outputCaptureStoresOnlySharedBudgetAndDrainsInput() {
        val budget = OutputBudget(maxBytes = 4)

        val text = ByteArrayInputStream("abcdef".toByteArray()).readUtf8Capped(budget)

        assertEquals("abcd", text)
        assertTrue(budget.truncated)
    }
}
