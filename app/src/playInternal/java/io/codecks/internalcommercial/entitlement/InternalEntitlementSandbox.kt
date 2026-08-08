package io.codecks.internalcommercial.entitlement

import io.codecks.domain.commercial.AccountBoundCacheMetadata
import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CommercialEntitlementService
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.CommercialRetryDirective
import io.codecks.domain.commercial.EntitlementRefreshResult
import io.codecks.domain.commercial.EntitlementStateResult
import io.codecks.domain.commercial.PaidCapability
import io.codecks.domain.commercial.PurchaseServerLifecycleState
import io.codecks.domain.commercial.ServerEntitlementState

/** Trusted test-backend response; client purchase data cannot create this type. */
internal data class InternalBackendEntitlement(
    val accountId: BackendAccountId,
    val lifecycle: PurchaseServerLifecycleState,
    val capabilities: Set<PaidCapability>,
    val revision: Long,
    val verifiedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(revision >= 0L)
        require(expiresAtEpochMillis >= verifiedAtEpochMillis)
    }

    fun asVerifiedState(): ServerEntitlementState = ServerEntitlementState.verified(
        metadata = AccountBoundCacheMetadata(
            accountId = accountId,
            verifiedAtEpochMillis = verifiedAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            backendRevision = revision,
        ),
        capabilities = if (lifecycle in ACTIVE_LIFECYCLES) capabilities else emptySet(),
    )

    private companion object {
        val ACTIVE_LIFECYCLES = setOf(
            PurchaseServerLifecycleState.ACTIVE,
            PurchaseServerLifecycleState.GRACE_PERIOD,
        )
    }
}

/** Test-only server port. Implementations own truth; local caches are advisory only. */
internal interface InternalEntitlementBackendPort {
    suspend fun fetch(accountId: BackendAccountId): InternalBackendEntitlement?
}

internal class InternalEntitlementSandbox(
    private val backend: InternalEntitlementBackendPort,
    private val clockEpochMillis: () -> Long,
) : CommercialEntitlementService {
    private var activeAccount: BackendAccountId? = null
    private val cache = mutableMapOf<BackendAccountId, ServerEntitlementState>()

    override suspend fun cached(accountId: BackendAccountId): EntitlementStateResult {
        val state = cache[accountId] ?: return EntitlementStateResult.Missing
        return if (state.metadata.expiresAtEpochMillis > clockEpochMillis()) {
            EntitlementStateResult.Cached(state)
        } else {
            cache.remove(accountId)
            EntitlementStateResult.Missing
        }
    }

    override suspend fun refresh(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): EntitlementRefreshResult {
        clearForAccountSwitch(accountId)
        val backendState = backend.fetch(accountId)
            ?: return EntitlementRefreshResult.RetryableFailure(
                CommercialFailureCode.TIMEOUT,
                CommercialRetryDirective(retryAfterMillis = 1_000L),
            )
        if (backendState.accountId != accountId) {
            cache.remove(accountId)
            return EntitlementRefreshResult.RetryableFailure(
                CommercialFailureCode.ACCOUNT_MISMATCH,
                CommercialRetryDirective(retryAfterMillis = 0L),
            )
        }
        val currentRevision = cache[accountId]?.metadata?.backendRevision
        if (currentRevision != null && backendState.revision < currentRevision) {
            cache.remove(accountId)
            return EntitlementRefreshResult.RetryableFailure(
                CommercialFailureCode.INVALID_STATE,
                CommercialRetryDirective(retryAfterMillis = 0L),
            )
        }
        val state = backendState.asVerifiedState()
        cache[accountId] = state
        return EntitlementRefreshResult.Refreshed(state)
    }

    fun clearForAccountSwitch(nextAccount: BackendAccountId?) {
        if (activeAccount != nextAccount) cache.clear()
        activeAccount = nextAccount
    }

    fun clearAll() {
        activeAccount = null
        cache.clear()
    }
}
