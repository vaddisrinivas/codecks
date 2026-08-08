package io.codecks.domain.commercial

enum class PaidCapability {
    PRO_LIFETIME,
    SYNC,
    AD_FREE,
    PREMIUM_THEMES,
    PREMIUM_ROUTINES,
    ADVANCED_SSH,
}

class ServerEntitlementState private constructor(
    val metadata: AccountBoundCacheMetadata,
    capabilities: Set<PaidCapability>,
) {
    val capabilities: Set<PaidCapability> = capabilities.toSet()

    fun allows(capability: PaidCapability): Boolean = capability in capabilities

    companion object {
        /** This factory belongs at the verified backend response boundary. */
        internal fun verified(
            metadata: AccountBoundCacheMetadata,
            capabilities: Set<PaidCapability>,
        ): ServerEntitlementState = ServerEntitlementState(metadata, capabilities)
    }
}

sealed interface EntitlementStateResult {
    data class Cached(val state: ServerEntitlementState) : EntitlementStateResult
    data object Missing : EntitlementStateResult
    data class Unavailable(val code: CommercialUnavailableCode) : EntitlementStateResult
}

sealed interface EntitlementRefreshResult {
    data class Refreshed(val state: ServerEntitlementState) : EntitlementRefreshResult
    data class Denied(val reason: CommercialDenyReason) : EntitlementRefreshResult
    data class RetryableFailure(
        val code: CommercialFailureCode,
        val retry: CommercialRetryDirective,
    ) : EntitlementRefreshResult
    data class Unavailable(val code: CommercialUnavailableCode) : EntitlementRefreshResult
}

interface CommercialEntitlementService {
    suspend fun cached(accountId: BackendAccountId): EntitlementStateResult
    suspend fun refresh(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): EntitlementRefreshResult
}

data class ProductCatalogId(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9_.]{2,63}")))
    }
}

class PurchaseAttemptHandle private constructor(private val canonical: String) {
    init {
        require(canonical.matches(OPAQUE_ID_REGEX))
    }

    override fun equals(other: Any?): Boolean =
        other is PurchaseAttemptHandle && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = "PurchaseAttemptHandle(redacted)"

    companion object {
        internal fun trusted(value: String): PurchaseAttemptHandle = PurchaseAttemptHandle(value)
    }
}

data class PurchaseLaunchRequest(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val productId: ProductCatalogId,
)

sealed interface PurchaseLaunchResult {
    data class AwaitingServerVerification(val attempt: PurchaseAttemptHandle) : PurchaseLaunchResult
    data class Pending(val attempt: PurchaseAttemptHandle) : PurchaseLaunchResult
    data object Cancelled : PurchaseLaunchResult
    data object AlreadyOwnedNeedsReconciliation : PurchaseLaunchResult
    data class Denied(val reason: CommercialDenyReason) : PurchaseLaunchResult
    data class Unavailable(val code: CommercialUnavailableCode) : PurchaseLaunchResult
    data class Failed(val code: CommercialFailureCode) : PurchaseLaunchResult
}

data class CanonicalRequestDigest(val sha256: String) {
    init {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

class IntegrityEvidenceHandle private constructor(private val canonical: String) {
    init {
        require(canonical.matches(OPAQUE_ID_REGEX))
    }

    override fun equals(other: Any?): Boolean =
        other is IntegrityEvidenceHandle && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = "IntegrityEvidenceHandle(redacted)"

    companion object {
        internal fun trusted(value: String): IntegrityEvidenceHandle = IntegrityEvidenceHandle(value)
    }
}

data class TransactionIntegrityRequest(
    val operationId: CommercialOperationId,
    val requestDigest: CanonicalRequestDigest,
)

data class TransactionIntegrityEvidence(
    val operationId: CommercialOperationId,
    val requestDigest: CanonicalRequestDigest,
    val evidence: IntegrityEvidenceHandle,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(expiresAtEpochMillis >= 0L)
    }
}

sealed interface TransactionIntegrityResult {
    data class Ready(val evidence: TransactionIntegrityEvidence) : TransactionIntegrityResult
    data class Denied(val reason: CommercialDenyReason) : TransactionIntegrityResult
    data class Unavailable(val code: CommercialUnavailableCode) : TransactionIntegrityResult
    data class Failed(val code: CommercialFailureCode) : TransactionIntegrityResult
}

interface TransactionIntegrityService {
    /** Integrity is collected only for this request; there is no startup operation. */
    suspend fun collect(request: TransactionIntegrityRequest): TransactionIntegrityResult
}

data class PurchaseVerificationRequest(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val attempt: PurchaseAttemptHandle,
    val integrityEvidence: TransactionIntegrityEvidence,
) {
    init {
        require(integrityEvidence.operationId == operationId) {
            "Integrity evidence must be bound to the purchase operation"
        }
    }
}

enum class PurchaseServerLifecycleState {
    PENDING,
    PURCHASED_UNACKNOWLEDGED,
    ACTIVE,
    GRACE_PERIOD,
    ON_HOLD,
    PAUSED,
    CANCELED_PENDING_EXPIRY,
    EXPIRED,
    REFUNDED,
    REVOKED,
    CHARGEBACK,
}

data class PurchaseVerificationReceipt(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val lifecycleState: PurchaseServerLifecycleState,
    val backendRevision: Long,
    val verifiedAtEpochMillis: Long,
) {
    val entitlementRefreshRequired: Boolean = true

    init {
        require(backendRevision >= 0L)
        require(verifiedAtEpochMillis >= 0L)
    }
}

sealed interface PurchaseVerificationResult {
    /** Caller must refresh backend entitlements; verification never grants locally. */
    data class ServerStateRecorded(val receipt: PurchaseVerificationReceipt) : PurchaseVerificationResult
    data class Pending(val operationId: CommercialOperationId) : PurchaseVerificationResult
    data class Rejected(val code: CommercialFailureCode) : PurchaseVerificationResult
    data class RetryableFailure(
        val code: CommercialFailureCode,
        val retry: CommercialRetryDirective,
    ) : PurchaseVerificationResult
    data class Denied(val reason: CommercialDenyReason) : PurchaseVerificationResult
    data class Unavailable(val code: CommercialUnavailableCode) : PurchaseVerificationResult
}

interface CommercialPurchaseService {
    suspend fun launch(request: PurchaseLaunchRequest): PurchaseLaunchResult
    suspend fun verify(request: PurchaseVerificationRequest): PurchaseVerificationResult
    suspend fun reconcile(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): PurchaseVerificationResult
}
