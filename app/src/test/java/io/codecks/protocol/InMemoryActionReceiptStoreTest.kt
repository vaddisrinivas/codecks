package io.codecks.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryActionReceiptStoreTest {
    @Test
    fun storeKeepsMostRecentBoundedReceipts() {
        val store = InMemoryActionReceiptStore(maxReceipts = 2)
        val first = receipt("rcpt_1", "inv_1")
        val second = receipt("rcpt_2", "inv_2")
        val third = receipt("rcpt_3", "inv_3")

        store.record(first)
        store.record(second)
        store.record(third)

        assertEquals(listOf(third, second), store.all())
        assertNull(store.findByReceiptId("rcpt_1"))
    }

    @Test
    fun latestInvocationLookupTracksNewestReceipt() {
        val store = InMemoryActionReceiptStore()
        val original = receipt("rcpt_1", "inv_shared")
        val newer = receipt("rcpt_2", "inv_shared")

        store.record(original)
        store.record(newer)

        assertEquals(newer, store.findLatestByInvocationId("inv_shared"))
    }

    private fun receipt(receiptId: String, invocationId: String): ProtocolActionReceipt =
        ProtocolActionReceipt(
            schemaVersion = "1.0",
            receiptId = receiptId,
            invocationId = invocationId,
            actionId = "macos.finder.reveal",
            status = ProtocolActionReceiptStatus.Completed,
            startedAt = "2026-07-15T12:00:01Z",
            finishedAt = "2026-07-15T12:00:02Z",
            message = "done",
            outputs = mapOf("app" to "Finder"),
        )
}
