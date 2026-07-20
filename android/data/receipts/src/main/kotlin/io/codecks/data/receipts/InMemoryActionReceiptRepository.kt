package io.codecks.data.receipts

import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.ActionReceiptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryActionReceiptRepository(
    private val maxReceipts: Int = 64,
) : ActionReceiptRepository {
    private val receipts = MutableStateFlow<List<ActionReceipt>>(emptyList())

    init {
        require(maxReceipts in 1..1_000) { "Receipt bound must be 1..1000" }
    }

    override fun observeReceipts(): Flow<List<ActionReceipt>> = receipts

    override suspend fun append(receipt: ActionReceipt) {
        receipts.value = (listOf(receipt) + receipts.value).take(maxReceipts)
    }

    override suspend fun clear() {
        receipts.value = emptyList()
    }
}
