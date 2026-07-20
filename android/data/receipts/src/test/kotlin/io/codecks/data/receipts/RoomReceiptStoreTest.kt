package io.codecks.data.receipts

import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.actions.RepairHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomReceiptStoreTest {
    @Test
    fun receiptEntityRoundTripsRepairHint() {
        val receipt = ActionReceipt(
            invocationId = "inv-1",
            state = ReceiptState.FAILURE,
            targetId = "mac-1",
            startedAtEpochMillis = 10L,
            endedAtEpochMillis = 20L,
            safeSummary = "Finder proof failed",
            repair = RepairHint(
                title = "Grant Automation",
                actionLabel = "Open Settings",
            ),
        )

        val entity = receipt.toEntity()
        val restored = entity.toReceiptOrNull()

        assertEquals("FAILURE", entity.state)
        assertEquals(receipt, restored)
    }

    @Test
    fun receiptEntityDropsUnknownState() {
        val entity = ActionReceiptEntity(
            invocationId = "inv-2",
            state = "CORRUPT",
            targetId = "mac-1",
            startedAtEpochMillis = 10L,
            endedAtEpochMillis = null,
            safeSummary = "Bad row",
            repairTitle = null,
            repairActionLabel = null,
        )

        assertNull(entity.toReceiptOrNull())
    }
}
