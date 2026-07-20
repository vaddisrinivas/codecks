package io.codecks.data.receipts

import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.ReceiptState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryActionReceiptRepositoryTest {
    @Test
    fun `receipt store is bounded newest first`() = runTest {
        val repository = InMemoryActionReceiptRepository(maxReceipts = 2)

        repository.append(receipt("one", startedAt = 1))
        repository.append(receipt("two", startedAt = 2))
        repository.append(receipt("three", startedAt = 3))

        val receipts = repository.observeReceipts().first()

        assertEquals(listOf("three", "two"), receipts.map { it.invocationId })
    }

    @Test
    fun `clear deletes local receipts`() = runTest {
        val repository = InMemoryActionReceiptRepository(maxReceipts = 2)

        repository.append(receipt("one", startedAt = 1))
        repository.clear()

        assertEquals(emptyList<ActionReceipt>(), repository.observeReceipts().first())
    }

    private fun receipt(id: String, startedAt: Long) = ActionReceipt(
        invocationId = id,
        state = ReceiptState.SUCCESS,
        targetId = "macbook",
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = startedAt + 1,
        safeSummary = "Safe summary $id",
    )
}

