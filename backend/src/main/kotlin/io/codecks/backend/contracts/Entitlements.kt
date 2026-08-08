package io.codecks.backend.contracts

enum class EntitlementLifecycle(val grantsAccess: Boolean) {
    PENDING(false),
    ACTIVE(true),
    GRACE(true),
    REVOKED(false),
    REFUNDED(false),
    EXPIRED(false),
}

enum class AcknowledgementState {
    REQUIRED_BY_BACKEND,
    CONFIRMED,
    NOT_APPLICABLE,
}

data class VerifiedPlayPurchase(
    val tokenHash: PurchaseTokenHash,
    val accountId: AccountId,
    val productId: String,
    val lifecycle: EntitlementLifecycle,
    val providerRevision: Long,
    val providerEventTimeEpochMillis: Long,
    val acknowledgedByPlay: Boolean,
) {
    init {
        require(productId.matches(Regex("[a-z][a-z0-9_.]{2,63}")))
        require(providerRevision >= 0L)
        require(providerEventTimeEpochMillis >= 0L)
    }
}

interface PlayPurchaseRequeryPort {
    /** Returns authoritative Play state. RTDN/client assertions are never entitlement state. */
    fun requery(tokenHash: PurchaseTokenHash): BackendResult<VerifiedPlayPurchase>
}

interface PlayAcknowledgementPort {
    /** Backend owns acknowledgement; clients cannot claim it. */
    fun acknowledge(tokenHash: PurchaseTokenHash, accountId: AccountId): BackendResult<Unit>
}

data class EntitlementRecord(
    val accountId: AccountId,
    val tokenHash: PurchaseTokenHash,
    val productId: String,
    val lifecycle: EntitlementLifecycle,
    val acknowledgement: AcknowledgementState,
    val providerRevision: Long,
    val backendRevision: Revision,
    val verifiedAtEpochMillis: Long,
) {
    val grantsAccess: Boolean = lifecycle.grantsAccess && acknowledgement == AcknowledgementState.CONFIRMED

    init {
        require(providerRevision >= 0L)
        require(verifiedAtEpochMillis >= 0L)
    }
}

data class RtdnSignal(
    val operationId: OperationId,
    val tokenHash: PurchaseTokenHash,
    val receivedAtEpochMillis: Long,
) {
    init {
        require(receivedAtEpochMillis >= 0L)
    }
}

data class AccountSwitchInvalidation(
    val previousAccountId: AccountId?,
    val activeAccountId: AccountId?,
    val invalidated: Boolean = previousAccountId != activeAccountId,
)

class EntitlementService(
    private val play: PlayPurchaseRequeryPort,
    private val acknowledgements: PlayAcknowledgementPort,
    private val integrityGate: TransactionIntegrityGate,
) {
    private enum class OperationKind {
        CLIENT_ASSERTION,
        VERIFIED_MUTATION,
        RTDN,
        RECONCILE,
    }

    private data class OperationFingerprint(
        val kind: OperationKind,
        val accountId: AccountId,
        val tokenHash: PurchaseTokenHash,
        val requestHash: RequestHash? = null,
    )

    private data class AppliedOperation(
        val fingerprint: OperationFingerprint,
        val result: BackendResult<EntitlementRecord>,
    )

    private val tokenBindings = mutableMapOf<PurchaseTokenHash, AccountId>()
    private val records = mutableMapOf<PurchaseTokenHash, EntitlementRecord>()
    private val operations = mutableMapOf<OperationId, AppliedOperation>()
    private var cachedAccountId: AccountId? = null
    private var cachedRecords: List<EntitlementRecord> = emptyList()

    /** Client purchase claims are informational only and can never grant access. */
    fun submitClientAssertion(
        operationId: OperationId,
        accountId: AccountId,
        tokenHash: PurchaseTokenHash,
    ): BackendResult<EntitlementRecord> {
        val fingerprint = OperationFingerprint(OperationKind.CLIENT_ASSERTION, accountId, tokenHash)
        operations[operationId]?.let { return replayOrConflict(it, fingerprint) }
        val rejected = BackendResult.Rejected(BackendErrorCode.SERVER_VERIFICATION_REQUIRED)
        operations[operationId] = AppliedOperation(fingerprint, rejected)
        return rejected
    }

    fun verifyPurchaseMutation(
        operationId: OperationId,
        accountId: AccountId,
        tokenHash: PurchaseTokenHash,
        requestHash: RequestHash,
        evidence: VerifiedIntegrityEvidence?,
        nowEpochMillis: Long,
    ): BackendResult<EntitlementRecord> {
        val fingerprint = OperationFingerprint(OperationKind.VERIFIED_MUTATION, accountId, tokenHash, requestHash)
        operations[operationId]?.let { return replayOrConflict(it, fingerprint) }
        val binding = IntegrityBinding(accountId, operationId, requestHash, nowEpochMillis)
        when (val authorized = integrityGate.authorizeMutation(binding, evidence, nowEpochMillis)) {
            is BackendResult.Rejected -> return rememberRejected(operationId, fingerprint, authorized)
            is BackendResult.Success,
            is BackendResult.Replayed,
            -> Unit
        }
        return queryAndApply(operationId, fingerprint, nowEpochMillis)
    }

    /** RTDN is only a wake-up signal; authoritative state always comes from a Play requery. */
    fun processRtdn(signal: RtdnSignal): BackendResult<EntitlementRecord> {
        val expectedAccount = tokenBindings[signal.tokenHash]
            ?: return BackendResult.Rejected(BackendErrorCode.SERVER_VERIFICATION_REQUIRED)
        val fingerprint = OperationFingerprint(OperationKind.RTDN, expectedAccount, signal.tokenHash)
        operations[signal.operationId]?.let { return replayOrConflict(it, fingerprint) }
        return queryAndApply(signal.operationId, fingerprint, signal.receivedAtEpochMillis)
    }

    fun reconcile(
        operationId: OperationId,
        accountId: AccountId,
        tokenHash: PurchaseTokenHash,
        nowEpochMillis: Long,
    ): BackendResult<EntitlementRecord> {
        val fingerprint = OperationFingerprint(OperationKind.RECONCILE, accountId, tokenHash)
        operations[operationId]?.let { return replayOrConflict(it, fingerprint) }
        return queryAndApply(operationId, fingerprint, nowEpochMillis)
    }

    fun cachedFor(accountId: AccountId): List<EntitlementRecord> =
        if (cachedAccountId == accountId) cachedRecords else emptyList()

    fun onAccountSwitch(accountId: AccountId?): AccountSwitchInvalidation {
        val previous = cachedAccountId
        if (previous != accountId) cachedRecords = emptyList()
        cachedAccountId = accountId
        if (accountId != null) {
            cachedRecords = records.values.filter { it.accountId == accountId }
        }
        return AccountSwitchInvalidation(previous, accountId)
    }

    private fun queryAndApply(
        operationId: OperationId,
        fingerprint: OperationFingerprint,
        nowEpochMillis: Long,
    ): BackendResult<EntitlementRecord> {
        val expectedAccountId = fingerprint.accountId
        val tokenHash = fingerprint.tokenHash
        val verified = when (val queried = play.requery(tokenHash)) {
            is BackendResult.Success -> queried.value
            is BackendResult.Replayed -> queried.value
            is BackendResult.Rejected -> return rememberRejected(operationId, fingerprint, queried)
        }
        if (verified.tokenHash != tokenHash || verified.accountId != expectedAccountId) {
            return rememberRejected(
                operationId,
                fingerprint,
                BackendResult.Rejected(BackendErrorCode.ACCOUNT_MISMATCH),
            )
        }
        val existingBinding = tokenBindings[tokenHash]
        if (existingBinding != null && existingBinding != expectedAccountId) {
            return rememberRejected(
                operationId,
                fingerprint,
                BackendResult.Rejected(BackendErrorCode.PURCHASE_TOKEN_BOUND_TO_OTHER_ACCOUNT),
            )
        }
        tokenBindings[tokenHash] = expectedAccountId

        val current = records[tokenHash]
        if (current != null && verified.providerRevision < current.providerRevision) {
            val stale = BackendResult.Rejected(BackendErrorCode.STALE_EVENT)
            operations[operationId] = AppliedOperation(fingerprint, stale)
            return stale
        }
        if (current != null && verified.providerRevision == current.providerRevision) {
            if (verified.productId != current.productId || verified.lifecycle != current.lifecycle) {
                val conflict = BackendResult.Rejected(BackendErrorCode.STALE_EVENT)
                operations[operationId] = AppliedOperation(fingerprint, conflict)
                return conflict
            }
            if (current.acknowledgement == AcknowledgementState.REQUIRED_BY_BACKEND) {
                // Retry a failed backend acknowledgement without requiring Play to advance revision.
            } else {
                val replayed = BackendResult.Replayed(current)
                operations[operationId] = AppliedOperation(fingerprint, replayed)
                return replayed
            }
        }

        val acknowledgement = acknowledgementFor(verified)
        val next = EntitlementRecord(
            accountId = expectedAccountId,
            tokenHash = tokenHash,
            productId = verified.productId,
            lifecycle = verified.lifecycle,
            acknowledgement = acknowledgement,
            providerRevision = verified.providerRevision,
            backendRevision = current?.backendRevision?.next() ?: Revision(0L),
            verifiedAtEpochMillis = nowEpochMillis,
        )
        records[tokenHash] = next
        if (cachedAccountId == expectedAccountId) {
            cachedRecords = records.values.filter { it.accountId == expectedAccountId }
        }
        val success = BackendResult.Success(next)
        operations[operationId] = AppliedOperation(fingerprint, success)
        return success
    }

    private fun acknowledgementFor(purchase: VerifiedPlayPurchase): AcknowledgementState {
        if (purchase.lifecycle !in setOf(EntitlementLifecycle.ACTIVE, EntitlementLifecycle.GRACE)) {
            return AcknowledgementState.NOT_APPLICABLE
        }
        if (purchase.acknowledgedByPlay) return AcknowledgementState.CONFIRMED
        return when (acknowledgements.acknowledge(purchase.tokenHash, purchase.accountId)) {
            is BackendResult.Success,
            is BackendResult.Replayed,
            -> AcknowledgementState.CONFIRMED
            is BackendResult.Rejected -> AcknowledgementState.REQUIRED_BY_BACKEND
        }
    }

    private fun replayOrConflict(
        applied: AppliedOperation,
        fingerprint: OperationFingerprint,
    ): BackendResult<EntitlementRecord> {
        if (applied.fingerprint != fingerprint) {
            return BackendResult.Rejected(BackendErrorCode.IDEMPOTENCY_CONFLICT)
        }
        return when (val result = applied.result) {
            is BackendResult.Success -> BackendResult.Replayed(result.value)
            is BackendResult.Replayed -> result
            is BackendResult.Rejected -> result
        }
    }

    private fun rememberRejected(
        operationId: OperationId,
        fingerprint: OperationFingerprint,
        rejected: BackendResult.Rejected,
    ): BackendResult.Rejected {
        operations[operationId] = AppliedOperation(fingerprint, rejected)
        return rejected
    }
}
