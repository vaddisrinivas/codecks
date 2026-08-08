package io.codecks.internalcommercial.billing

import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CanonicalRequestDigest
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.CommercialPurchaseService
import io.codecks.domain.commercial.EntitlementRefreshResult
import io.codecks.domain.commercial.PaidCapability
import io.codecks.domain.commercial.ProductCatalogId
import io.codecks.domain.commercial.PurchaseAttemptHandle
import io.codecks.domain.commercial.PurchaseLaunchRequest
import io.codecks.domain.commercial.PurchaseLaunchResult
import io.codecks.domain.commercial.PurchaseServerLifecycleState
import io.codecks.domain.commercial.PurchaseVerificationReceipt
import io.codecks.domain.commercial.PurchaseVerificationRequest
import io.codecks.domain.commercial.PurchaseVerificationResult
import io.codecks.domain.commercial.TransactionIntegrityEvidence
import io.codecks.internalcommercial.entitlement.InternalBackendEntitlement
import io.codecks.internalcommercial.entitlement.InternalEntitlementBackendPort
import io.codecks.internalcommercial.entitlement.InternalEntitlementSandbox
import io.codecks.internalcommercial.integrity.InternalIntegrityContext
import io.codecks.internalcommercial.integrity.InternalIntegritySandbox

/** Product metadata deliberately contains no price, offer, or Play product identifiers. */
internal data class InternalCatalogEntry(
    val catalogId: ProductCatalogId,
    val capabilities: Set<PaidCapability>,
)

internal sealed interface InternalCatalogQueryResult {
    data class Available(val entries: List<InternalCatalogEntry>) : InternalCatalogQueryResult
    data object Offline : InternalCatalogQueryResult
}

internal sealed interface InternalManageResult {
    data object OpenedSandbox : InternalManageResult
    data object Offline : InternalManageResult
}

internal class InternalPurchaseToken private constructor(private val canonical: String) {
    init {
        require(canonical.matches(Regex("[A-Za-z0-9._:-]{12,256}")))
    }

    override fun equals(other: Any?): Boolean =
        other is InternalPurchaseToken && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()
    override fun toString(): String = "InternalPurchaseToken(redacted)"

    companion object {
        fun fromPlaySandbox(value: String): InternalPurchaseToken = InternalPurchaseToken(value)
    }
}

internal sealed interface InternalPlayPurchaseResult {
    data class Purchased(val token: InternalPurchaseToken) : InternalPlayPurchaseResult
    data object Pending : InternalPlayPurchaseResult
    data object Cancelled : InternalPlayPurchaseResult
    data object Offline : InternalPlayPurchaseResult
}

internal sealed interface InternalAcknowledgementResult {
    data object Acknowledged : InternalAcknowledgementResult
    data class Retryable(val code: CommercialFailureCode) : InternalAcknowledgementResult
}

/** Internal fake boundary for Play purchase acquisition and acknowledgement. */
internal interface InternalPlayBillingPort {
    suspend fun launch(
        accountId: BackendAccountId,
        productId: ProductCatalogId,
        attempt: PurchaseAttemptHandle,
    ): InternalPlayPurchaseResult

    suspend fun acknowledge(
        accountId: BackendAccountId,
        token: InternalPurchaseToken,
    ): InternalAcknowledgementResult
}

/** Backend accepts the client attempt only after transaction integrity was consumed. */
internal interface InternalPurchaseBackendPort : InternalEntitlementBackendPort {
    suspend fun verify(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        attempt: PurchaseAttemptHandle,
        token: InternalPurchaseToken,
    ): InternalBackendEntitlement?

    suspend fun recordAcknowledged(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        token: InternalPurchaseToken,
    ): InternalBackendEntitlement?

    suspend fun restore(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): InternalBackendEntitlement?
}

/**
 * Internal-only billing sandbox. It is lazy: creating it performs no query,
 * connection, integrity collection, acknowledgement, or backend refresh.
 */
internal class InternalBillingSandbox(
    private val catalog: List<InternalCatalogEntry>,
    private val backend: InternalPurchaseBackendPort,
    private val playBilling: InternalPlayBillingPort,
    private val integrity: InternalIntegritySandbox,
    private val entitlements: InternalEntitlementSandbox,
    private val clockEpochMillis: () -> Long,
) : CommercialPurchaseService {
    private data class AttemptState(
        val accountId: BackendAccountId,
        val token: InternalPurchaseToken?,
    )

    private val attempts = mutableMapOf<PurchaseAttemptHandle, AttemptState>()
    private val acknowledged = mutableSetOf<PurchaseAttemptHandle>()
    private var online = true
    private var activeAccount: BackendAccountId? = null
    private var nextAttempt = 0L

    fun setOnline(online: Boolean) {
        this.online = online
    }

    suspend fun queryCatalog(): InternalCatalogQueryResult =
        if (online) InternalCatalogQueryResult.Available(catalog.toList()) else InternalCatalogQueryResult.Offline

    fun openManage(): InternalManageResult =
        if (online) InternalManageResult.OpenedSandbox else InternalManageResult.Offline

    fun onAccountChanged(accountId: BackendAccountId?) {
        if (activeAccount != accountId) {
            attempts.clear()
            acknowledged.clear()
            entitlements.clearForAccountSwitch(accountId)
        }
        activeAccount = accountId
    }

    override suspend fun launch(request: PurchaseLaunchRequest): PurchaseLaunchResult {
        onAccountChanged(request.accountId)
        if (!online) return PurchaseLaunchResult.Unavailable(io.codecks.domain.commercial.CommercialUnavailableCode.OFFLINE)
        if (catalog.none { it.catalogId == request.productId }) {
            return PurchaseLaunchResult.Failed(CommercialFailureCode.INVALID_STATE)
        }
        val attempt = PurchaseAttemptHandle.trusted(
            "attempt-${nextAttempt++.toString().padStart(8, '0')}",
        )
        return when (val client = playBilling.launch(request.accountId, request.productId, attempt)) {
            is InternalPlayPurchaseResult.Purchased -> {
                attempts[attempt] = AttemptState(request.accountId, client.token)
                PurchaseLaunchResult.AwaitingServerVerification(attempt)
            }
            InternalPlayPurchaseResult.Pending -> {
                attempts[attempt] = AttemptState(request.accountId, token = null)
                PurchaseLaunchResult.Pending(attempt)
            }
            InternalPlayPurchaseResult.Cancelled -> PurchaseLaunchResult.Cancelled
            InternalPlayPurchaseResult.Offline ->
                PurchaseLaunchResult.Unavailable(io.codecks.domain.commercial.CommercialUnavailableCode.OFFLINE)
        }
    }

    /** Sandbox-only helper that models Play's pending callback without granting anything. */
    fun markPending(attempt: PurchaseAttemptHandle): PurchaseLaunchResult {
        val state = attempts[attempt]
            ?: return PurchaseLaunchResult.Failed(CommercialFailureCode.INVALID_STATE)
        attempts[attempt] = state.copy(token = null)
        return PurchaseLaunchResult.Pending(attempt)
    }

    suspend fun collectIntegrity(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        requestDigest: CanonicalRequestDigest,
    ) = integrity.collect(
        InternalIntegrityContext(
            operationId = operationId,
            accountId = accountId,
            requestDigest = requestDigest,
            requestedAtEpochMillis = clockEpochMillis(),
        ),
    )

    override suspend fun verify(request: PurchaseVerificationRequest): PurchaseVerificationResult {
        if (!online) return retryable(CommercialFailureCode.TIMEOUT)
        val attemptState = attempts[request.attempt]
            ?: return PurchaseVerificationResult.Rejected(CommercialFailureCode.INVALID_STATE)
        if (attemptState.accountId != request.accountId) {
            onAccountChanged(request.accountId)
            return PurchaseVerificationResult.Rejected(CommercialFailureCode.ACCOUNT_MISMATCH)
        }
        onAccountChanged(request.accountId)
        integrity.consume(request.operationId, request.accountId, request.integrityEvidence)?.let {
            return PurchaseVerificationResult.Rejected(it)
        }
        val token = attemptState.token
            ?: return PurchaseVerificationResult.Pending(request.operationId)
        var server = backend.verify(request.operationId, request.accountId, request.attempt, token)
            ?: return retryable(CommercialFailureCode.TIMEOUT)
        if (server.accountId != request.accountId) return PurchaseVerificationResult.Rejected(CommercialFailureCode.ACCOUNT_MISMATCH)
        if (server.lifecycle == PurchaseServerLifecycleState.PENDING) {
            return PurchaseVerificationResult.Pending(request.operationId)
        }
        if (server.lifecycle == PurchaseServerLifecycleState.PURCHASED_UNACKNOWLEDGED) {
            when (val acknowledgement = playBilling.acknowledge(request.accountId, token)) {
                InternalAcknowledgementResult.Acknowledged -> Unit
                is InternalAcknowledgementResult.Retryable -> return retryable(acknowledgement.code)
            }
            server = backend.recordAcknowledged(request.operationId, request.accountId, token)
                ?: return retryable(CommercialFailureCode.TIMEOUT)
            if (
                server.accountId != request.accountId ||
                server.lifecycle == PurchaseServerLifecycleState.PURCHASED_UNACKNOWLEDGED ||
                server.lifecycle == PurchaseServerLifecycleState.PENDING
            ) {
                return PurchaseVerificationResult.Rejected(CommercialFailureCode.REJECTED_BY_SERVER)
            }
            acknowledged += request.attempt
        }
        return PurchaseVerificationResult.ServerStateRecorded(
            receiptFrom(request.operationId, request.accountId, server),
        )
    }

    override suspend fun reconcile(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): PurchaseVerificationResult = restore(operationId, accountId)

    suspend fun restore(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): PurchaseVerificationResult {
        onAccountChanged(accountId)
        if (!online) return retryable(CommercialFailureCode.TIMEOUT)
        val server = backend.restore(operationId, accountId)
            ?: return retryable(CommercialFailureCode.TIMEOUT)
        if (server.accountId != accountId) return PurchaseVerificationResult.Rejected(CommercialFailureCode.ACCOUNT_MISMATCH)
        return when (server.lifecycle) {
            PurchaseServerLifecycleState.PENDING -> PurchaseVerificationResult.Pending(operationId)
            PurchaseServerLifecycleState.PURCHASED_UNACKNOWLEDGED ->
                retryable(CommercialFailureCode.INVALID_STATE)
            else -> PurchaseVerificationResult.ServerStateRecorded(receiptFrom(operationId, accountId, server))
        }
    }

    suspend fun refreshEntitlements(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): EntitlementRefreshResult = if (online) {
        entitlements.refresh(operationId, accountId)
    } else {
        EntitlementRefreshResult.RetryableFailure(
            CommercialFailureCode.TIMEOUT,
            io.codecks.domain.commercial.CommercialRetryDirective(retryAfterMillis = 1_000L),
        )
    }

    fun isAcknowledged(attempt: PurchaseAttemptHandle): Boolean = attempt in acknowledged

    private fun receiptFrom(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        server: InternalBackendEntitlement,
    ) = PurchaseVerificationReceipt(
        operationId = operationId,
        accountId = accountId,
        lifecycleState = server.lifecycle,
        backendRevision = server.revision,
        verifiedAtEpochMillis = server.verifiedAtEpochMillis,
    )

    private fun retryable(code: CommercialFailureCode) = PurchaseVerificationResult.RetryableFailure(
        code = code,
        retry = io.codecks.domain.commercial.CommercialRetryDirective(retryAfterMillis = 1_000L),
    )

}
