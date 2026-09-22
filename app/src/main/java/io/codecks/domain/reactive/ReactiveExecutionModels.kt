package io.codecks.domain.reactive

import io.codecks.protocol.InMemoryActionReceiptStore
import io.codecks.protocol.ProtocolActionReceipt
import io.codecks.protocol.ProtocolActionReceiptError
import io.codecks.protocol.ProtocolActionReceiptStatus
import java.time.Instant
import java.util.UUID
import io.codecks.domain.assurance.ActionAssuranceReceipt

@JvmInline
value class ReactiveOperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReactiveOperationId must not be blank." }
        require(value.toByteArray(Charsets.UTF_8).size <= 128) {
            "ReactiveOperationId must be at most 128 UTF-8 bytes."
        }
    }
}

@JvmInline
value class ReactiveIdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "ReactiveIdempotencyKey must not be blank." }
        require(value.toByteArray(Charsets.UTF_8).size <= 128) {
            "ReactiveIdempotencyKey must be at most 128 UTF-8 bytes."
        }
    }
}

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

data class ReactiveActionInvocation(
    val operationId: ReactiveOperationId,
    val idempotencyKey: ReactiveIdempotencyKey,
    val requestedAtMillis: Long,
    val timeoutMillis: Long = DefaultTimeoutMillis,
) {
    init {
        require(timeoutMillis > 0) { "ReactiveActionInvocation timeoutMillis must be positive." }
    }

    companion object {
        const val DefaultTimeoutMillis: Long = 10_000L

        fun create(
            control: ReactiveControl,
            nowMillis: Long,
            operationId: ReactiveOperationId = newReactiveOperationId(),
            idempotencyKey: ReactiveIdempotencyKey = ReactiveIdempotencyKey(operationId.value),
            timeoutMillis: Long = DefaultTimeoutMillis,
        ): ReactiveActionInvocation = ReactiveActionInvocation(
            operationId = operationId,
            idempotencyKey = idempotencyKey,
            requestedAtMillis = nowMillis,
            timeoutMillis = timeoutMillis,
        )
    }
}

data class ReactiveAuthorization(
    val confirmedActionRevision: ActionRevision? = null,
    val reviewedActionRevision: ActionRevision? = null,
)

@JvmInline
value class ReactiveUndoToken(val value: String) {
    init {
        require(value.isNotBlank()) { "ReactiveUndoToken must not be blank." }
        require(value.toByteArray(Charsets.UTF_8).size <= 128) {
            "ReactiveUndoToken must be at most 128 UTF-8 bytes."
        }
    }
}

data class ReactiveUndoAction(
    val label: String,
    val action: ReactiveAction,
    val expiresAtMillis: Long,
    val token: ReactiveUndoToken = newReactiveUndoToken(),
) {
    init {
        require(label.isNotBlank()) { "ReactiveUndoAction label must not be blank." }
    }
}

data class ReactiveActionReceipt(
    val id: ReceiptId,
    val operationId: ReactiveOperationId,
    val idempotencyKey: ReactiveIdempotencyKey,
    val controlId: ControlId,
    val actionRevision: ActionRevision,
    val completedAtMillis: Long,
    val result: ReactiveActionResult,
    val undo: ReactiveUndoAction?,
    val expiresAtMillis: Long?,
    val metadata: Map<String, String> = emptyMap(),
    val assuranceReceipt: ActionAssuranceReceipt? = null,
)

data class ReactiveExecutionOutcome(
    val result: ReactiveActionResult,
    val receipt: ReactiveActionReceipt? = null,
    val assuranceReceipt: ActionAssuranceReceipt? = null,
)

interface ReactiveActionExecutor {
    suspend fun execute(
        control: ReactiveControl,
        authorization: ReactiveAuthorization = ReactiveAuthorization(),
        nowMillis: Long,
        currentState: MacStateSnapshot? = null,
        invocation: ReactiveActionInvocation = ReactiveActionInvocation.create(control, nowMillis),
    ): ReactiveExecutionOutcome

    suspend fun undo(
        receiptId: ReceiptId,
        nowMillis: Long,
        invocation: ReactiveActionInvocation? = null,
    ): ReactiveUndoOutcome
}

sealed interface ReactiveUndoOutcome {
    data class Succeeded(val receipt: ReactiveActionReceipt) : ReactiveUndoOutcome
    data class Unsupported(val reasonCode: String) : ReactiveUndoOutcome
    data object Expired : ReactiveUndoOutcome
    data class Failed(val errorCode: String, val retryable: Boolean) : ReactiveUndoOutcome
}

class InMemoryReactiveReceiptStore(
    private val maxReceipts: Int = 50,
    private val protocolMirror: InMemoryActionReceiptStore = InMemoryActionReceiptStore(maxReceipts),
) {
    private val receipts = ArrayDeque<ReactiveActionReceipt>()
    private val idempotentReceipts = linkedMapOf<ReactiveIdempotencyKey, IdempotentReceipt>()

    fun record(receipt: ReactiveActionReceipt) {
        receipts.removeAll { it.id == receipt.id }
        receipts.addFirst(receipt)
        while (receipts.size > maxReceipts) {
            receipts.removeLast()
        }
        protocolMirror.record(receipt.toProtocolReceipt())
    }

    fun recordIdempotent(
        signature: String,
        rawIdempotencyKey: ReactiveIdempotencyKey,
        rawResult: ReactiveActionResult,
        receipt: ReactiveActionReceipt,
    ) {
        record(receipt)
        idempotentReceipts[rawIdempotencyKey] = IdempotentReceipt(signature, rawResult, receipt)
        while (idempotentReceipts.size > maxReceipts) {
            val eldest = idempotentReceipts.keys.firstOrNull() ?: break
            idempotentReceipts.remove(eldest)
        }
    }

    fun replaceReceipt(receipt: ReactiveActionReceipt) {
        record(receipt)
        idempotentReceipts.replaceAll { _, stored ->
            if (stored.receipt.id == receipt.id) stored.copy(receipt = receipt) else stored
        }
    }

    fun findByIdempotencyKey(key: ReactiveIdempotencyKey): IdempotentReceipt? =
        idempotentReceipts[key]

    fun findByReceiptId(receiptId: ReceiptId): ReactiveActionReceipt? =
        receipts.firstOrNull { it.id == receiptId }

    fun latest(): ReactiveActionReceipt? = receipts.firstOrNull()

    fun all(): List<ReactiveActionReceipt> = receipts.toList()

    fun protocolReceipts(): List<ProtocolActionReceipt> = protocolMirror.all()
}

data class IdempotentReceipt(
    val signature: String,
    val result: ReactiveActionResult,
    val receipt: ReactiveActionReceipt,
)

internal fun newReactiveReceiptId(): ReceiptId =
    ReceiptId(UUID.randomUUID().toString().lowercase())

internal fun newReactiveOperationId(): ReactiveOperationId =
    ReactiveOperationId(UUID.randomUUID().toString().lowercase())

internal fun newReactiveUndoToken(): ReactiveUndoToken =
    ReactiveUndoToken(UUID.randomUUID().toString().lowercase())

private fun ReactiveActionReceipt.toProtocolReceipt(): ProtocolActionReceipt =
    ProtocolActionReceipt(
        schemaVersion = "1.0",
        receiptId = id.value,
        invocationId = operationId.value,
        actionId = receiptHash(metadata["actionId"] ?: controlId.value),
        status = result.toProtocolStatus(),
        startedAt = Instant.ofEpochMilli(completedAtMillis).toString(),
        finishedAt = Instant.ofEpochMilli(completedAtMillis).toString(),
        message = receiptHash(result.message()),
        outputs = metadata.mapValues { receiptHash(it.value) } + mapOf(
            "controlId" to receiptHash(controlId.value),
            "actionRevision" to receiptHash(actionRevision.value),
            "idempotencyKey" to receiptHash(idempotencyKey.value),
        ),
        error = result.errorCodeOrNull(),
    )

private fun ReactiveActionResult.toProtocolStatus(): ProtocolActionReceiptStatus = when (this) {
    is ReactiveActionResult.Succeeded -> ProtocolActionReceiptStatus.Completed
    is ReactiveActionResult.Failed -> ProtocolActionReceiptStatus.Failed
    is ReactiveActionResult.RequiresConfirmation,
    is ReactiveActionResult.RequiresReview,
    is ReactiveActionResult.Unsupported,
    ReactiveActionResult.Expired,
    -> ProtocolActionReceiptStatus.Denied
}

private fun ReactiveActionResult.message(): String = when (this) {
    is ReactiveActionResult.Succeeded -> messageCode
    is ReactiveActionResult.Failed -> errorCode
    is ReactiveActionResult.RequiresConfirmation -> "requires_confirmation"
    is ReactiveActionResult.RequiresReview -> "requires_review"
    is ReactiveActionResult.Unsupported -> reasonCode
    ReactiveActionResult.Expired -> "expired"
}

private fun ReactiveActionResult.errorCodeOrNull(): ProtocolActionReceiptError? = when (this) {
    is ReactiveActionResult.Failed -> receiptHash(errorCode).let { ProtocolActionReceiptError(it, it, retryable) }
    is ReactiveActionResult.Unsupported -> receiptHash(reasonCode).let { ProtocolActionReceiptError(it, it, retryable = false) }
    ReactiveActionResult.Expired -> receiptHash("expired").let { ProtocolActionReceiptError(it, it, retryable = false) }
    is ReactiveActionResult.RequiresConfirmation -> ProtocolActionReceiptError(
        receiptHash("requires_confirmation"),
        receiptHash("requires_confirmation"),
        retryable = false,
    )
    is ReactiveActionResult.RequiresReview -> ProtocolActionReceiptError(
        receiptHash("requires_review"),
        receiptHash(reason),
        retryable = false,
    )
    is ReactiveActionResult.Succeeded -> null
}

private fun receiptHash(value: String): String = "redacted_${sha256Hex(value).take(12)}"
