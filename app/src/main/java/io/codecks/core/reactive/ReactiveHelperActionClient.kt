package io.codecks.core.reactive

import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveUndoAction
import io.codecks.platform.helper.ReactiveHelperClient
import io.codecks.shared.protocol.ReactiveHelperActionReceipt
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.shared.protocol.ReactiveResponseEnvelope
import io.codecks.shared.protocol.ReceiptStatus
import io.codecks.shared.protocol.validateActionReceipt
import kotlinx.serialization.json.Json

interface ReactiveHelperActionClient {
    suspend fun execute(
        request: ReactiveHelperRequest.Execute,
        deadlineMillis: Long,
    ): ReactiveHelperActionExecution
}

data object UnavailableReactiveHelperActionClient : ReactiveHelperActionClient {
    override suspend fun execute(
        request: ReactiveHelperRequest.Execute,
        deadlineMillis: Long,
    ): ReactiveHelperActionExecution = ReactiveHelperActionExecution.Unsupported("helper_unavailable")
}

class ReactiveHelperClientActionClient(
    private val client: ReactiveHelperClient,
    private val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        encodeDefaults = true
    },
) : ReactiveHelperActionClient {
    override suspend fun execute(
        request: ReactiveHelperRequest.Execute,
        deadlineMillis: Long,
    ): ReactiveHelperActionExecution =
        runCatching { client.request(request, deadlineMillis).toHelperActionExecution(json) }
            .getOrElse { ReactiveHelperActionExecution.Unsupported(it.toHelperUnavailableCode()) }
}

sealed interface ReactiveHelperActionExecution {
    data class Succeeded(
        val resultCode: String,
        val helperReceiptId: String,
        val completedAtMillis: Long,
        val undoToken: String? = null,
    ) : ReactiveHelperActionExecution

    data class Failed(
        val errorCode: String,
        val retryable: Boolean,
    ) : ReactiveHelperActionExecution

    data class Unsupported(val reasonCode: String) : ReactiveHelperActionExecution
    data class RequiresReview(val reason: String) : ReactiveHelperActionExecution
    data object Expired : ReactiveHelperActionExecution
}

private fun ReactiveResponseEnvelope.toHelperActionExecution(json: Json): ReactiveHelperActionExecution = when (status) {
    ReceiptStatus.Completed -> {
        val body = bodyJson ?: return ReactiveHelperActionExecution.Failed("helper_receipt_missing", false)
        val receipt = runCatching {
            json.decodeFromString(ReactiveHelperActionReceipt.serializer(), body).also(::validateActionReceipt)
        }.getOrElse {
            return ReactiveHelperActionExecution.Failed("helper_receipt_invalid", false)
        }
        if (receipt.status != ReceiptStatus.Completed) {
            return ReactiveHelperActionExecution.Failed("helper_receipt_status_mismatch", false)
        }
        ReactiveHelperActionExecution.Succeeded(
            resultCode = receipt.resultCode,
            helperReceiptId = receipt.receiptId,
            completedAtMillis = receipt.completedAtMillis,
            undoToken = receipt.undoToken,
        )
    }
    ReceiptStatus.Denied -> ReactiveHelperActionExecution.RequiresReview(code ?: "helper_denied")
    ReceiptStatus.Timeout -> ReactiveHelperActionExecution.Expired
    ReceiptStatus.PartialFailure,
    ReceiptStatus.Cancelled,
    ReceiptStatus.Failed,
    -> ReactiveHelperActionExecution.Failed(code ?: "helper_${status.name.lowercase()}", retryable)
    ReceiptStatus.Accepted -> ReactiveHelperActionExecution.Failed("helper_accepted_without_completion", true)
}

internal fun ReactiveHelperActionExecution.toReactiveActionResult(
    actionRevision: ActionRevision,
): ReactiveActionResult = when (this) {
    is ReactiveHelperActionExecution.Succeeded -> ReactiveActionResult.Succeeded(resultCode)
    is ReactiveHelperActionExecution.Failed -> ReactiveActionResult.Failed(errorCode, retryable)
    is ReactiveHelperActionExecution.Unsupported -> ReactiveActionResult.Unsupported(reasonCode)
    is ReactiveHelperActionExecution.RequiresReview -> ReactiveActionResult.RequiresReview(actionRevision, reason)
    ReactiveHelperActionExecution.Expired -> ReactiveActionResult.Expired
}

internal fun ReactiveHelperActionExecution.Succeeded.toUndoAction(nowMillis: Long): ReactiveUndoAction? {
    val token = undoToken ?: return null
    return ReactiveUndoAction(
        label = "Undo helper action",
        action = ReactiveAction.Helper(
            actionId = "helper.undo",
            arguments = mapOf("undoToken" to token),
        ),
        expiresAtMillis = nowMillis + 30_000L,
    )
}

private fun Throwable.toHelperUnavailableCode(): String = when {
    message?.contains("not open", ignoreCase = true) == true -> "helper_session_missing"
    message?.contains("expired", ignoreCase = true) == true -> "helper_session_expired"
    message?.contains("authentication", ignoreCase = true) == true -> "helper_authentication_failed"
    else -> "helper_unavailable"
}
