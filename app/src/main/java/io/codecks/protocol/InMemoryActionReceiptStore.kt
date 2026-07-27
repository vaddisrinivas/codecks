package io.codecks.protocol

class InMemoryActionReceiptStore(
    private val maxReceipts: Int = 50,
) {
    private val receipts = ArrayDeque<ProtocolActionReceipt>()

    fun record(receipt: ProtocolActionReceipt) {
        receipts.removeAll { it.receiptId == receipt.receiptId }
        receipts.addFirst(receipt)
        while (receipts.size > maxReceipts) {
            receipts.removeLast()
        }
    }

    fun findByReceiptId(receiptId: String): ProtocolActionReceipt? =
        receipts.firstOrNull { it.receiptId == receiptId }

    fun findLatestByInvocationId(invocationId: String): ProtocolActionReceipt? =
        receipts.firstOrNull { it.invocationId == invocationId }

    fun all(): List<ProtocolActionReceipt> = receipts.toList()
}
