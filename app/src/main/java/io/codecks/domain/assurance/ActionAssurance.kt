package io.codecks.domain.assurance

import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException

enum class AssuranceSource {
    Deck,
    Rule,
    AiDraft,
    Reactive,
    TypedSshCatalog,
}

enum class AssuranceTransport {
    Hid,
    LocalRoute,
    Ssh,
    Helper,
    Composite,
}

data class AssuranceReview(
    val reviewedRevision: String,
    val approved: Boolean,
)

data class AssuranceRequest(
    val operationId: String,
    val idempotencyKey: String,
    val subjectId: String,
    val revision: String,
    val source: AssuranceSource,
    val transport: AssuranceTransport,
    val reviewRequired: Boolean,
    val review: AssuranceReview? = null,
    val permissionGranted: Boolean = true,
    val policyPassed: Boolean = true,
    val confirmationGranted: Boolean = true,
    val imported: Boolean = false,
    val reversible: Boolean = false,
) {
    init {
        listOf(operationId, idempotencyKey, subjectId, revision).forEach { value ->
            require(value.isNotBlank())
            require(value.toByteArray(Charsets.UTF_8).size <= 160)
        }
    }

    val signature: String = sha256(
        listOf(
            subjectId,
            revision,
            source.name,
            transport.name,
            reversible,
            reviewRequired,
            review?.reviewedRevision,
            review?.approved,
            permissionGranted,
            policyPassed,
            confirmationGranted,
            imported,
        ).joinToString("|"),
    )

    companion object {
        fun create(
            subjectId: String,
            revision: String,
            source: AssuranceSource,
            transport: AssuranceTransport,
            reviewRequired: Boolean,
            review: AssuranceReview? = null,
            permissionGranted: Boolean = true,
            policyPassed: Boolean = true,
            confirmationGranted: Boolean = true,
            imported: Boolean = false,
            reversible: Boolean = false,
            operationId: String = UUID.randomUUID().toString(),
            idempotencyKey: String = operationId,
        ): AssuranceRequest = AssuranceRequest(
            operationId = operationId,
            idempotencyKey = idempotencyKey,
            subjectId = subjectId,
            revision = revision,
            source = source,
            transport = transport,
            reviewRequired = reviewRequired,
            review = review,
            permissionGranted = permissionGranted,
            policyPassed = policyPassed,
            confirmationGranted = confirmationGranted,
            imported = imported,
            reversible = reversible,
        )
    }
}

enum class AssurancePreflightCode {
    CurrentRevision,
    Review,
    Permission,
    Policy,
    Confirmation,
    ImportDisabled,
    Transport,
}

data class AssurancePreflightCheck(
    val code: AssurancePreflightCode,
    val passed: Boolean,
    val retryable: Boolean = false,
)

enum class AssuranceComponentStatus {
    Succeeded,
    Failed,
    Canceled,
    Skipped,
}

data class AssuranceComponentExecution(
    val componentId: String,
    val status: AssuranceComponentStatus,
    val code: String,
    val retryable: Boolean = false,
    val undoToken: String? = null,
)

data class AssuranceComponentReceipt(
    val componentId: String,
    val status: AssuranceComponentStatus,
    val code: String,
    val retryable: Boolean,
    val undoAvailable: Boolean,
)

enum class AssuranceReceiptStatus {
    Succeeded,
    PartialFailure,
    Failed,
    Denied,
}

data class ActionAssuranceReceipt(
    val receiptId: String,
    val operationId: String,
    val idempotencyKey: String,
    val subjectId: String,
    val revision: String,
    val source: AssuranceSource,
    val transport: AssuranceTransport,
    val status: AssuranceReceiptStatus,
    val preflight: List<AssurancePreflightCheck>,
    val components: List<AssuranceComponentReceipt>,
    val retryToken: String?,
    val undoToken: String?,
    val completedAtMillis: Long,
)

sealed interface AssuranceUndoResult {
    data class Succeeded(val receipt: ActionAssuranceReceipt) : AssuranceUndoResult
    data class Denied(val code: String) : AssuranceUndoResult
}

interface ActionAssuranceAdapter {
    suspend fun preflight(request: AssuranceRequest): List<AssurancePreflightCheck> = emptyList()

    suspend fun execute(
        request: AssuranceRequest,
        componentIds: Set<String>? = null,
    ): List<AssuranceComponentExecution>

    suspend fun undo(request: AssuranceRequest, componentTokens: Map<String, String>): List<AssuranceComponentExecution> =
        listOf(AssuranceComponentExecution("undo", AssuranceComponentStatus.Failed, "undo_unsupported"))
}

class InMemoryActionAssuranceStore(private val limit: Int = 100) {
    private val byReceipt = linkedMapOf<String, StoredAssuranceReceipt>()
    private val byIdempotency = linkedMapOf<String, StoredAssuranceReceipt>()
    private val usedUndoTokens = mutableSetOf<String>()
    private val usedRetryTokens = mutableSetOf<String>()

    internal fun record(stored: StoredAssuranceReceipt) {
        byReceipt[stored.receipt.receiptId] = stored
        byIdempotency.putIfAbsent(stored.receipt.idempotencyKey, stored)
        while (byReceipt.size > limit) byReceipt.remove(byReceipt.keys.first())
        while (byIdempotency.size > limit) byIdempotency.remove(byIdempotency.keys.first())
    }

    internal fun byIdempotency(key: String): StoredAssuranceReceipt? = byIdempotency[key]
    internal fun byReceipt(id: String): StoredAssuranceReceipt? = byReceipt[id]
    @Synchronized
    internal fun consumeUndo(token: String): Boolean = usedUndoTokens.add(token)

    @Synchronized
    internal fun undoConsumed(token: String): Boolean = token in usedUndoTokens

    @Synchronized
    internal fun consumeRetry(token: String): Boolean = usedRetryTokens.add(token)
}

internal data class StoredAssuranceReceipt(
    val request: AssuranceRequest,
    val receipt: ActionAssuranceReceipt,
    val componentUndoTokens: Map<String, String>,
)

class ActionAssuranceEngine(
    private val adapter: ActionAssuranceAdapter,
    private val store: InMemoryActionAssuranceStore = InMemoryActionAssuranceStore(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(
        request: AssuranceRequest,
        currentRevision: String,
    ): ActionAssuranceReceipt {
        store.byIdempotency(request.idempotencyKey)?.let { stored ->
            return if (stored.request.signature == request.signature) {
                stored.receipt
            } else {
                denied(request, "idempotency_conflict", AssurancePreflightCode.Policy)
            }
        }
        val mandatoryChecks = ActionAssurancePolicy.evaluate(request, currentRevision)
        if (mandatoryChecks.any { !it.passed }) return record(request, mandatoryChecks, emptyList())
        val checks = mandatoryChecks + safePreflight(request)
        if (checks.any { !it.passed }) return record(request, checks, emptyList())
        val executed = try {
            adapter.execute(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            listOf(AssuranceComponentExecution("transport", AssuranceComponentStatus.Failed, "transport_failed", true))
        }
        return record(request, checks, executed)
    }

    suspend fun retry(
        receiptId: String,
        retryToken: String,
        request: AssuranceRequest,
        currentRevision: String,
    ): ActionAssuranceReceipt {
        val previous = store.byReceipt(receiptId)
            ?: return denied(request, "retry_receipt_missing", AssurancePreflightCode.Policy)
        if (previous.receipt.retryToken != retryToken || previous.request.signature != request.signature) {
            return denied(request, "retry_token_invalid", AssurancePreflightCode.Policy)
        }
        if (request.revision != currentRevision) {
            return denied(request, "retry_revision_stale", AssurancePreflightCode.CurrentRevision)
        }
        val targets = previous.receipt.components
            .filter { it.status == AssuranceComponentStatus.Failed && it.retryable }
            .mapTo(linkedSetOf(), AssuranceComponentReceipt::componentId)
        if (targets.isEmpty()) return denied(request, "retry_unavailable", AssurancePreflightCode.Policy)
        val mandatoryChecks = ActionAssurancePolicy.evaluate(request, currentRevision)
        if (mandatoryChecks.any { !it.passed }) return record(request, mandatoryChecks, emptyList())
        if (!store.consumeRetry(retryToken)) {
            return denied(request, "retry_token_consumed", AssurancePreflightCode.Policy)
        }
        val checks = mandatoryChecks + safePreflight(request)
        if (checks.any { !it.passed }) return record(request, checks, emptyList())
        val executed = try {
            adapter.execute(request, targets)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            targets.map { AssuranceComponentExecution(it, AssuranceComponentStatus.Failed, "transport_failed", true) }
        }
        return record(request, checks, executed)
    }

    suspend fun undo(receiptId: String, undoToken: String): AssuranceUndoResult {
        val stored = store.byReceipt(receiptId) ?: return AssuranceUndoResult.Denied("undo_receipt_missing")
        if (stored.receipt.undoToken != undoToken) return AssuranceUndoResult.Denied("undo_token_invalid")
        if (store.undoConsumed(undoToken)) return AssuranceUndoResult.Denied("undo_token_consumed")
        if (stored.componentUndoTokens.isEmpty()) return AssuranceUndoResult.Denied("undo_unavailable")
        val undoRequest = stored.request.copy(
            operationId = UUID.randomUUID().toString(),
            idempotencyKey = UUID.randomUUID().toString(),
            reviewRequired = false,
            review = null,
        )
        val executed = try {
            adapter.undo(undoRequest, stored.componentUndoTokens)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            listOf(AssuranceComponentExecution("undo", AssuranceComponentStatus.Failed, "undo_failed"))
        }
        if (executed.isEmpty() || executed.any { it.status != AssuranceComponentStatus.Succeeded }) {
            return AssuranceUndoResult.Denied("undo_failed")
        }
        if (!store.consumeUndo(undoToken)) return AssuranceUndoResult.Denied("undo_token_consumed")
        return AssuranceUndoResult.Succeeded(record(undoRequest, emptyList(), executed))
    }

    private suspend fun safePreflight(request: AssuranceRequest): List<AssurancePreflightCheck> = try {
        adapter.preflight(request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        listOf(AssurancePreflightCheck(AssurancePreflightCode.Transport, passed = false, retryable = false))
    }

    private fun denied(
        request: AssuranceRequest,
        code: String,
        preflightCode: AssurancePreflightCode,
    ): ActionAssuranceReceipt = record(
        request,
        listOf(AssurancePreflightCheck(preflightCode, false)),
        listOf(AssuranceComponentExecution("gate", AssuranceComponentStatus.Skipped, code)),
    )

    private fun record(
        request: AssuranceRequest,
        preflight: List<AssurancePreflightCheck>,
        executed: List<AssuranceComponentExecution>,
    ): ActionAssuranceReceipt {
        val components = executed.map { result ->
            AssuranceComponentReceipt(
                componentId = safeCode(result.componentId),
                status = result.status,
                code = safeCode(result.code),
                retryable = result.retryable,
                undoAvailable = result.undoToken != null,
            )
        }
        val succeeded = components.count { it.status == AssuranceComponentStatus.Succeeded }
        val failed = components.count { it.status == AssuranceComponentStatus.Failed }
        val denied = preflight.any { !it.passed }
        val status = when {
            denied -> AssuranceReceiptStatus.Denied
            failed == 0 && components.isNotEmpty() -> AssuranceReceiptStatus.Succeeded
            failed > 0 && succeeded > 0 -> AssuranceReceiptStatus.PartialFailure
            else -> AssuranceReceiptStatus.Failed
        }
        val componentUndo = executed.mapNotNull { item -> item.undoToken?.let { item.componentId to it } }.toMap()
        val receipt = ActionAssuranceReceipt(
            receiptId = UUID.randomUUID().toString(),
            operationId = safeCode(request.operationId),
            idempotencyKey = safeCode(request.idempotencyKey),
            subjectId = safeCode(request.subjectId),
            revision = safeCode(request.revision),
            source = request.source,
            transport = request.transport,
            status = status,
            preflight = preflight.toList(),
            components = components,
            retryToken = components.takeIf { it.any { item -> item.status == AssuranceComponentStatus.Failed && item.retryable } }
                ?.let { UUID.randomUUID().toString() },
            undoToken = componentUndo.takeIf { request.reversible && it.isNotEmpty() }
                ?.let { UUID.randomUUID().toString() },
            completedAtMillis = nowMillis(),
        )
        store.record(StoredAssuranceReceipt(request, receipt, componentUndo))
        return receipt
    }
}

object ActionAssurancePolicy {
    fun evaluate(request: AssuranceRequest, currentRevision: String): List<AssurancePreflightCheck> = listOf(
        AssurancePreflightCheck(AssurancePreflightCode.CurrentRevision, request.revision == currentRevision),
        AssurancePreflightCheck(
            AssurancePreflightCode.Review,
            !request.reviewRequired ||
                (request.review?.approved == true && request.review.reviewedRevision == request.revision),
        ),
        AssurancePreflightCheck(AssurancePreflightCode.Permission, request.permissionGranted),
        AssurancePreflightCheck(AssurancePreflightCode.Policy, request.policyPassed),
        AssurancePreflightCheck(AssurancePreflightCode.Confirmation, request.confirmationGranted),
        AssurancePreflightCheck(AssurancePreflightCode.ImportDisabled, !request.imported),
    )
}

private fun safeCode(raw: String): String {
    val normalized = raw.lowercase()
    return if (normalized.matches(Regex("[a-z0-9_-]{1,96}"))) {
        normalized
    } else {
        "redacted_${sha256(raw).take(12)}"
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
