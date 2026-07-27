package io.codecks.domain.reactive

import io.codecks.protocol.InMemoryActionReceiptStore
import io.codecks.protocol.ProtocolActionReceipt
import io.codecks.protocol.ProtocolActionReceiptStatus
import java.time.Instant
import java.util.UUID

sealed interface ReactiveActionResult {
    data class Succeeded(val messageCode: String) : ReactiveActionResult
    data class Failed(val errorCode: String, val retryable: Boolean) : ReactiveActionResult
    data class RequiresConfirmation(
        val actionRevision: ActionRevision,
        val title: String,
        val body: String,
    ) : ReactiveActionResult

    data class RequiresReview(
        val actionRevision: ActionRevision,
        val reason: String,
    ) : ReactiveActionResult

    data class Unsupported(val reasonCode: String) : ReactiveActionResult
    data object Expired : ReactiveActionResult
}

data class ReactiveAuthorization(
    val confirmedActionRevision: ActionRevision? = null,
    val reviewedActionRevision: ActionRevision? = null,
)

data class ReactiveUndoAction(
    val label: String,
    val action: ReactiveAction,
    val expiresAtMillis: Long,
) {
    init {
        require(label.isNotBlank()) { "ReactiveUndoAction label must not be blank." }
    }
}

data class ReactiveActionReceipt(
    val id: ReceiptId,
    val controlId: ControlId,
    val actionRevision: ActionRevision,
    val completedAtMillis: Long,
    val result: ReactiveActionResult.Succeeded,
    val undo: ReactiveUndoAction?,
    val expiresAtMillis: Long?,
    val metadata: Map<String, String> = emptyMap(),
)

data class ReactiveExecutionOutcome(
    val result: ReactiveActionResult,
    val receipt: ReactiveActionReceipt? = null,
)

interface ReactiveActionExecutor {
    suspend fun execute(
        control: ReactiveControl,
        authorization: ReactiveAuthorization = ReactiveAuthorization(),
        nowMillis: Long,
        currentState: MacStateSnapshot? = null,
    ): ReactiveExecutionOutcome
}

class InMemoryReactiveReceiptStore(
    private val maxReceipts: Int = 50,
    private val protocolMirror: InMemoryActionReceiptStore = InMemoryActionReceiptStore(maxReceipts),
) {
    private val receipts = ArrayDeque<ReactiveActionReceipt>()

    fun record(receipt: ReactiveActionReceipt) {
        receipts.removeAll { it.id == receipt.id }
        receipts.addFirst(receipt)
        while (receipts.size > maxReceipts) {
            receipts.removeLast()
        }
        protocolMirror.record(receipt.toProtocolReceipt())
    }

    fun findByReceiptId(receiptId: ReceiptId): ReactiveActionReceipt? =
        receipts.firstOrNull { it.id == receiptId }

    fun latest(): ReactiveActionReceipt? = receipts.firstOrNull()

    fun all(): List<ReactiveActionReceipt> = receipts.toList()

    fun protocolReceipts(): List<ProtocolActionReceipt> = protocolMirror.all()
}

internal fun newReactiveReceiptId(): ReceiptId =
    ReceiptId(UUID.randomUUID().toString().lowercase())

private fun ReactiveActionReceipt.toProtocolReceipt(): ProtocolActionReceipt =
    ProtocolActionReceipt(
        schemaVersion = "1.0",
        receiptId = id.value,
        invocationId = "reactive:${controlId.value}:${completedAtMillis}",
        actionId = metadata["actionId"] ?: controlId.value,
        status = ProtocolActionReceiptStatus.Completed,
        startedAt = Instant.ofEpochMilli(completedAtMillis).toString(),
        finishedAt = Instant.ofEpochMilli(completedAtMillis).toString(),
        message = result.messageCode,
        outputs = metadata,
        error = null,
    )
