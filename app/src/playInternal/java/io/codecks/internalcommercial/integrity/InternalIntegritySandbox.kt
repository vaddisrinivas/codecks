package io.codecks.internalcommercial.integrity

import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CanonicalRequestDigest
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.IntegrityEvidenceHandle
import io.codecks.domain.commercial.TransactionIntegrityEvidence
import io.codecks.domain.commercial.TransactionIntegrityResult

/** Canonical transaction-only integrity context. It is intentionally not a startup API. */
internal data class InternalIntegrityContext(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val requestDigest: CanonicalRequestDigest,
    val requestedAtEpochMillis: Long,
) {
    init {
        require(requestedAtEpochMillis >= 0L)
    }
}

internal class InternalIntegritySandbox(
    private val clockEpochMillis: () -> Long,
    private val ttlMillis: Long = 60_000L,
) {
    private val contexts = mutableMapOf<TransactionIntegrityEvidence, InternalIntegrityContext>()
    private var nextEvidence = 0L

    init {
        require(ttlMillis > 0L)
    }

    suspend fun collect(context: InternalIntegrityContext): TransactionIntegrityResult {
        val now = clockEpochMillis()
        if (
            context.requestedAtEpochMillis > now ||
            now - context.requestedAtEpochMillis >= ttlMillis ||
            now > Long.MAX_VALUE - ttlMillis
        ) {
            return TransactionIntegrityResult.Failed(CommercialFailureCode.INVALID_STATE)
        }
        val evidence = TransactionIntegrityEvidence(
            operationId = context.operationId,
            requestDigest = context.requestDigest,
            evidence = IntegrityEvidenceHandle.trusted(
                "integrity-${context.requestedAtEpochMillis}-${nextEvidence++.toString().padStart(8, '0')}",
            ),
            expiresAtEpochMillis = now + ttlMillis,
        )
        contexts[evidence] = context
        return TransactionIntegrityResult.Ready(evidence)
    }

    fun consume(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        evidence: TransactionIntegrityEvidence,
    ): CommercialFailureCode? {
        val context = contexts.remove(evidence) ?: return CommercialFailureCode.REJECTED_BY_SERVER
        if (evidence.expiresAtEpochMillis <= clockEpochMillis()) return CommercialFailureCode.TIMEOUT
        return when {
            context.operationId != operationId -> CommercialFailureCode.REJECTED_BY_SERVER
            context.accountId != accountId -> CommercialFailureCode.ACCOUNT_MISMATCH
            context.requestDigest != evidence.requestDigest -> CommercialFailureCode.REJECTED_BY_SERVER
            else -> null
        }
    }
}
