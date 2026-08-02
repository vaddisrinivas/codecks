package io.codecks.domain.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ClipboardReceiptTest {
    @Test
    fun operationPublishesExactlyOneTerminalReceipt() {
        val published = mutableListOf<ClipboardReceipt>()
        val operation = ClipboardOperation(
            direction = ClipboardDirection.PhoneToMac,
            startedAtMillis = 100,
            onTerminal = published::add,
        )

        val first = operation.finish(
            terminalResult = ClipboardTerminalResult.AppliedUnverified,
            completedAtMillis = 120,
        )
        val duplicate = operation.finish(
            terminalResult = ClipboardTerminalResult.VerifiedSuccess,
            completedAtMillis = 130,
        )

        assertSame(first, duplicate)
        assertEquals(1, published.size)
        assertEquals(ClipboardTerminalResult.AppliedUnverified, published.single().terminalResult)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFailureReceiptRejectsFailureCode() {
        ClipboardReceipt(
            direction = ClipboardDirection.MacToPhone,
            terminalResult = ClipboardTerminalResult.VerifiedSuccess,
            failureCode = ClipboardFailureCode.Unknown,
            startedAtMillis = 100,
            completedAtMillis = 101,
        )
    }
}
