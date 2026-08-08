package io.codecks.internalcommercial.labui

internal enum class LabSessionState { SIGNED_OUT, ACTIVE, DELETED }
internal enum class LabDeletionState {
    IDLE,
    CONFIRMATION_REQUIRED,
    REAUTH_REQUIRED,
    SESSIONS_REVOKED,
    SNAPSHOTS_REMOVED,
    COMPLETE,
}
internal enum class LabRestoreMode { MERGE, REPLACE }
internal enum class LabRestoreState { IDLE, PREVIEW_READY, COMMITTED, ROLLED_BACK }
internal enum class LabPurchaseState { IDLE, PENDING, VERIFIED_UNACKNOWLEDGED, ACTIVE }
internal enum class LabConsentState { UNKNOWN, GRANTED }

@JvmInline
internal value class LabSnapshotChecksum(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}")))
    }

    override fun toString(): String = "LabSnapshotChecksum(redacted)"
}

internal data class LabRestorePreview(
    val additions: Int,
    val replacements: Int,
    val conflicts: Int,
    val unsupported: Int,
    val checksum: LabSnapshotChecksum,
    val importedAutomationsEnabled: Boolean = false,
) {
    init {
        require(listOf(additions, replacements, conflicts, unsupported).all { it >= 0 })
        require(!importedAutomationsEnabled)
    }
}

internal enum class LabReceiptKind { AUTH, DELETION, SYNC, RESTORE, BILLING, PRIVACY, ADS }

internal enum class LabReceiptCode(val kind: LabReceiptKind, val summary: String) {
    SIGN_IN_ESTABLISHED(LabReceiptKind.AUTH, "Internal session established"),
    SIGN_IN_DENIED_DELETED(LabReceiptKind.AUTH, "Sign-in denied: test account deleted or deletion active"),
    SESSION_RECOVERY_VERIFIED(LabReceiptKind.AUTH, "Session recovery verified"),
    NO_RECOVERABLE_SESSION(LabReceiptKind.AUTH, "No recoverable session"),
    SIGN_OUT_REVOKED(LabReceiptKind.AUTH, "Session revoked and local session cleared"),
    SIGN_OUT_ALREADY_DELETED(LabReceiptKind.AUTH, "Sign-out ignored: test account already deleted"),
    DELETION_SIGN_IN_REQUIRED(LabReceiptKind.DELETION, "Deletion denied: sign-in required"),
    DELETION_CANCELLED(LabReceiptKind.DELETION, "Deletion cancelled"),
    DELETION_REAUTH_FAILED(LabReceiptKind.DELETION, "Deletion stopped: reauthentication failed"),
    DELETION_SESSIONS_REVOKED(LabReceiptKind.DELETION, "Deletion stage 1: sessions revoked"),
    DELETION_SNAPSHOTS_REMOVED(LabReceiptKind.DELETION, "Deletion stage 2: snapshots removed"),
    DELETION_ACCOUNT_DELETED(LabReceiptKind.DELETION, "Deletion stage 3: account deleted; Play subscription remains managed by Play"),
    MUTATION_BLOCKED_BY_DELETION(LabReceiptKind.DELETION, "Mutation blocked: account deletion is active"),
    SNAPSHOT_UPLOAD_SIGN_IN_REQUIRED(LabReceiptKind.SYNC, "Upload denied: sign-in required"),
    SNAPSHOT_UPLOADED(LabReceiptKind.SYNC, "Portable snapshot uploaded; checksum receipt verified"),
    RESTORE_PREVIEW_SIGN_IN_REQUIRED(LabReceiptKind.SYNC, "Restore preview denied: sign-in required"),
    RESTORE_PREVIEW_READY(LabReceiptKind.RESTORE, "Restore preview ready; imported automations disabled"),
    RESTORE_PREVIEW_REQUIRED(LabReceiptKind.RESTORE, "Restore denied: preview required"),
    MERGE_RESTORE_COMMITTED(LabReceiptKind.RESTORE, "Merge restore validated and committed"),
    REPLACE_RESTORE_COMMITTED(LabReceiptKind.RESTORE, "Replace restore validated and committed"),
    MERGE_RESTORE_ROLLED_BACK(LabReceiptKind.RESTORE, "Merge restore failed; safety backup rolled back"),
    REPLACE_RESTORE_ROLLED_BACK(LabReceiptKind.RESTORE, "Replace restore failed; safety backup rolled back"),
    PURCHASE_SIGN_IN_REQUIRED(LabReceiptKind.BILLING, "Purchase denied: sign-in required"),
    PURCHASE_PENDING(LabReceiptKind.BILLING, "Purchase pending; no entitlement granted"),
    VERIFICATION_NO_PENDING_PURCHASE(LabReceiptKind.BILLING, "Verification denied: no pending purchase"),
    SERVER_VERIFICATION_ACCEPTED(LabReceiptKind.BILLING, "Server verification accepted; acknowledgement required"),
    ACKNOWLEDGEMENT_REQUIRES_VERIFICATION(LabReceiptKind.BILLING, "Acknowledgement denied: server verification required"),
    ENTITLEMENT_REFRESHED(LabReceiptKind.BILLING, "Purchase acknowledged; backend entitlement refreshed"),
    PURCHASE_RESTORE_SIGN_IN_REQUIRED(LabReceiptKind.BILLING, "Purchase restore denied: sign-in required"),
    PURCHASES_RECONCILED(LabReceiptKind.BILLING, "Purchase restore reconciled against backend state"),
    MANAGE_PURCHASES_OPENED(LabReceiptKind.BILLING, "Internal manage-purchases sandbox opened"),
    CONSENT_GRANTED(LabReceiptKind.PRIVACY, "Internal consent granted"),
    PRIVACY_OPTIONS_OPENED(LabReceiptKind.PRIVACY, "Internal privacy options opened"),
    AD_REQUESTED(LabReceiptKind.ADS, "Routine-bank discovery ad fake requested"),
    AD_DENIED(LabReceiptKind.ADS, "Ad denied: verified session and consent required"),
}

internal data class LabReceipt(
    val sequence: Int,
    val code: LabReceiptCode,
) {
    val kind: LabReceiptKind get() = code.kind
    val summary: String get() = code.summary
}

internal data class CommercialLabState(
    val session: LabSessionState = LabSessionState.SIGNED_OUT,
    val deletion: LabDeletionState = LabDeletionState.IDLE,
    val restore: LabRestoreState = LabRestoreState.IDLE,
    val restorePreview: LabRestorePreview? = null,
    val lastSnapshotChecksum: LabSnapshotChecksum? = null,
    val lastRestoreMode: LabRestoreMode? = null,
    val purchase: LabPurchaseState = LabPurchaseState.IDLE,
    val entitlementActive: Boolean = false,
    val consent: LabConsentState = LabConsentState.UNKNOWN,
    val receipts: List<LabReceipt> = emptyList(),
    val nextReceiptSequence: Int = 1,
) {
    init {
        require(nextReceiptSequence >= 1)
        require(receipts.size <= MAX_RECEIPTS)
        require(receipts.all { it.sequence >= 1 && it.summary.length <= MAX_RECEIPT_LENGTH })
        require(receipts.map { it.sequence }.distinct().size == receipts.size)
        require(receipts.zipWithNext().all { (newer, older) -> newer.sequence > older.sequence })
        require(nextReceiptSequence > (receipts.maxOfOrNull { it.sequence } ?: 0))
        require(nextReceiptSequence <= MAX_RECEIPT_SEQUENCE)
        require(restorePreview == null || restorePreview.checksum == lastSnapshotChecksum)
        require(restore != LabRestoreState.IDLE || restorePreview == null)
        require(entitlementActive == (purchase == LabPurchaseState.ACTIVE))
        if (purchase != LabPurchaseState.IDLE) {
            require(session == LabSessionState.ACTIVE && deletion == LabDeletionState.IDLE)
        }
        if (session == LabSessionState.DELETED) require(deletion == LabDeletionState.COMPLETE)
        if (deletion == LabDeletionState.CONFIRMATION_REQUIRED || deletion == LabDeletionState.REAUTH_REQUIRED) {
            require(session == LabSessionState.ACTIVE)
        }
        if (deletion == LabDeletionState.SESSIONS_REVOKED || deletion == LabDeletionState.SNAPSHOTS_REMOVED) {
            require(session == LabSessionState.SIGNED_OUT)
        }
    }

    companion object {
        const val MAX_RECEIPTS = 20
        const val MAX_RECEIPT_LENGTH = 160
        const val MAX_RECEIPT_SEQUENCE = 1_000_000
    }
}

internal data class CommercialLabSavedState(
    val session: LabSessionState,
    val deletion: LabDeletionState,
    val restore: LabRestoreState,
    val lastSnapshotChecksum: LabSnapshotChecksum?,
    val lastRestoreMode: LabRestoreMode?,
    val purchase: LabPurchaseState,
    val entitlementActive: Boolean,
    val consent: LabConsentState,
    val restorePreview: LabRestorePreview?,
    val receipts: List<LabReceipt>,
    val nextReceiptSequence: Int,
)

/** Deterministic internal fake. It never constructs an SDK client or performs network I/O. */
internal class CommercialLabStateMachine(initial: CommercialLabState = CommercialLabState()) {
    var state: CommercialLabState = initial
        private set

    fun signIn() {
        if (state.session == LabSessionState.DELETED || state.deletion != LabDeletionState.IDLE) {
            return receipt(LabReceiptCode.SIGN_IN_DENIED_DELETED)
        }
        state = state.copy(session = LabSessionState.ACTIVE, deletion = LabDeletionState.IDLE)
        receipt(LabReceiptCode.SIGN_IN_ESTABLISHED)
    }

    fun recoverSession() {
        receipt(if (state.session == LabSessionState.ACTIVE) LabReceiptCode.SESSION_RECOVERY_VERIFIED else LabReceiptCode.NO_RECOVERABLE_SESSION)
    }

    fun signOut() {
        if (state.session == LabSessionState.DELETED) {
            return receipt(LabReceiptCode.SIGN_OUT_ALREADY_DELETED)
        }
        if (state.deletion != LabDeletionState.IDLE) return receipt(LabReceiptCode.MUTATION_BLOCKED_BY_DELETION)
        state = state.copy(
            session = LabSessionState.SIGNED_OUT,
            purchase = LabPurchaseState.IDLE,
            entitlementActive = false,
            restore = LabRestoreState.IDLE,
            restorePreview = null,
            lastSnapshotChecksum = null,
            lastRestoreMode = null,
        )
        receipt(LabReceiptCode.SIGN_OUT_REVOKED)
    }

    fun requestAccountDeletion() {
        if (state.session != LabSessionState.ACTIVE) {
            return receipt(LabReceiptCode.DELETION_SIGN_IN_REQUIRED)
        }
        if (state.deletion != LabDeletionState.IDLE) return
        state = state.copy(
            deletion = LabDeletionState.CONFIRMATION_REQUIRED,
            purchase = LabPurchaseState.IDLE,
            entitlementActive = false,
        )
    }

    fun confirmAccountDeletion(confirmed: Boolean) {
        if (state.deletion != LabDeletionState.CONFIRMATION_REQUIRED) return
        if (!confirmed) {
            state = state.copy(deletion = LabDeletionState.IDLE)
            return receipt(LabReceiptCode.DELETION_CANCELLED)
        }
        state = state.copy(deletion = LabDeletionState.REAUTH_REQUIRED)
    }

    fun completeDeletionReauthentication(success: Boolean) {
        if (state.deletion != LabDeletionState.REAUTH_REQUIRED) return
        if (!success) {
            return receipt(LabReceiptCode.DELETION_REAUTH_FAILED)
        }
        state = state.copy(
            session = LabSessionState.SIGNED_OUT,
            deletion = LabDeletionState.SESSIONS_REVOKED,
            entitlementActive = false,
            purchase = LabPurchaseState.IDLE,
        )
        receipt(LabReceiptCode.DELETION_SESSIONS_REVOKED)
        state = state.copy(
            deletion = LabDeletionState.SNAPSHOTS_REMOVED,
            restore = LabRestoreState.IDLE,
            restorePreview = null,
            lastSnapshotChecksum = null,
            lastRestoreMode = null,
        )
        receipt(LabReceiptCode.DELETION_SNAPSHOTS_REMOVED)
        state = state.copy(session = LabSessionState.DELETED, deletion = LabDeletionState.COMPLETE)
        receipt(LabReceiptCode.DELETION_ACCOUNT_DELETED)
    }

    fun uploadSnapshot() {
        if (!requireSession(LabReceiptCode.SNAPSHOT_UPLOAD_SIGN_IN_REQUIRED)) return
        state = state.copy(lastSnapshotChecksum = FAKE_SNAPSHOT_CHECKSUM)
        receipt(LabReceiptCode.SNAPSHOT_UPLOADED)
    }

    fun downloadAndPreviewRestore() {
        if (!requireSession(LabReceiptCode.RESTORE_PREVIEW_SIGN_IN_REQUIRED)) return
        state = state.copy(
            restore = LabRestoreState.PREVIEW_READY,
            restorePreview = LabRestorePreview(4, 2, 1, 1, FAKE_SNAPSHOT_CHECKSUM),
            lastSnapshotChecksum = FAKE_SNAPSHOT_CHECKSUM,
            lastRestoreMode = null,
        )
        receipt(LabReceiptCode.RESTORE_PREVIEW_READY)
    }

    fun executeRestore(mode: LabRestoreMode, simulateFailure: Boolean) {
        if (!mutationAllowed()) return
        if (state.restore != LabRestoreState.PREVIEW_READY || state.restorePreview == null) {
            return receipt(LabReceiptCode.RESTORE_PREVIEW_REQUIRED)
        }
        state = if (simulateFailure) {
            state.copy(restore = LabRestoreState.ROLLED_BACK, lastRestoreMode = mode)
        } else {
            state.copy(restore = LabRestoreState.COMMITTED, lastRestoreMode = mode)
        }
        receipt(
            when {
                mode == LabRestoreMode.MERGE && simulateFailure -> LabReceiptCode.MERGE_RESTORE_ROLLED_BACK
                mode == LabRestoreMode.REPLACE && simulateFailure -> LabReceiptCode.REPLACE_RESTORE_ROLLED_BACK
                mode == LabRestoreMode.MERGE -> LabReceiptCode.MERGE_RESTORE_COMMITTED
                else -> LabReceiptCode.REPLACE_RESTORE_COMMITTED
            },
        )
    }

    fun startPurchase() {
        if (!requireSession(LabReceiptCode.PURCHASE_SIGN_IN_REQUIRED)) return
        state = state.copy(purchase = LabPurchaseState.PENDING, entitlementActive = false)
        receipt(LabReceiptCode.PURCHASE_PENDING)
    }

    fun verifyPurchase() {
        if (!mutationAllowed()) return
        if (state.purchase != LabPurchaseState.PENDING) {
            return receipt(LabReceiptCode.VERIFICATION_NO_PENDING_PURCHASE)
        }
        state = state.copy(purchase = LabPurchaseState.VERIFIED_UNACKNOWLEDGED, entitlementActive = false)
        receipt(LabReceiptCode.SERVER_VERIFICATION_ACCEPTED)
    }

    fun acknowledgeAndRefreshEntitlement() {
        if (!mutationAllowed()) return
        if (state.purchase != LabPurchaseState.VERIFIED_UNACKNOWLEDGED) {
            return receipt(LabReceiptCode.ACKNOWLEDGEMENT_REQUIRES_VERIFICATION)
        }
        state = state.copy(purchase = LabPurchaseState.ACTIVE, entitlementActive = true)
        receipt(LabReceiptCode.ENTITLEMENT_REFRESHED)
    }

    fun restorePurchases() {
        if (!requireSession(LabReceiptCode.PURCHASE_RESTORE_SIGN_IN_REQUIRED)) return
        receipt(LabReceiptCode.PURCHASES_RECONCILED)
    }

    fun openManagePurchases() {
        receipt(LabReceiptCode.MANAGE_PURCHASES_OPENED)
    }

    fun requestConsent() {
        if (state.deletion != LabDeletionState.IDLE) return receipt(LabReceiptCode.MUTATION_BLOCKED_BY_DELETION)
        state = state.copy(consent = LabConsentState.GRANTED)
        receipt(LabReceiptCode.CONSENT_GRANTED)
    }

    fun openPrivacyOptions() {
        receipt(LabReceiptCode.PRIVACY_OPTIONS_OPENED)
    }

    fun requestApprovedDiscoveryAd() {
        val allowed = state.session == LabSessionState.ACTIVE &&
            state.deletion == LabDeletionState.IDLE &&
            state.consent == LabConsentState.GRANTED
        receipt(if (allowed) LabReceiptCode.AD_REQUESTED else LabReceiptCode.AD_DENIED)
    }

    fun savedState(): CommercialLabSavedState = CommercialLabSavedState(
        session = state.session,
        deletion = state.deletion,
        restore = state.restore,
        lastSnapshotChecksum = state.lastSnapshotChecksum,
        lastRestoreMode = state.lastRestoreMode,
        purchase = state.purchase,
        entitlementActive = state.entitlementActive,
        consent = state.consent,
        restorePreview = state.restorePreview,
        receipts = state.receipts,
        nextReceiptSequence = state.nextReceiptSequence,
    )

    private fun requireSession(deniedCode: LabReceiptCode): Boolean {
        if (state.deletion != LabDeletionState.IDLE) {
            receipt(LabReceiptCode.MUTATION_BLOCKED_BY_DELETION)
            return false
        }
        if (state.session == LabSessionState.ACTIVE) return true
        receipt(deniedCode)
        return false
    }

    private fun mutationAllowed(): Boolean {
        if (state.deletion == LabDeletionState.IDLE && state.session == LabSessionState.ACTIVE) return true
        receipt(
            if (state.deletion != LabDeletionState.IDLE) LabReceiptCode.MUTATION_BLOCKED_BY_DELETION
            else LabReceiptCode.NO_RECOVERABLE_SESSION,
        )
        return false
    }

    private fun receipt(code: LabReceiptCode) {
        check(state.nextReceiptSequence < CommercialLabState.MAX_RECEIPT_SEQUENCE)
        val receipt = LabReceipt(state.nextReceiptSequence, code)
        state = state.copy(
            receipts = (listOf(receipt) + state.receipts).take(CommercialLabState.MAX_RECEIPTS),
            nextReceiptSequence = state.nextReceiptSequence + 1,
        )
    }

    companion object {
        private val FAKE_SNAPSHOT_CHECKSUM = LabSnapshotChecksum("c".repeat(64))

        fun restore(saved: CommercialLabSavedState): CommercialLabStateMachine = CommercialLabStateMachine(
            CommercialLabState(
                session = saved.session,
                deletion = saved.deletion,
                restore = saved.restore,
                restorePreview = saved.restorePreview,
                lastSnapshotChecksum = saved.lastSnapshotChecksum,
                lastRestoreMode = saved.lastRestoreMode,
                purchase = saved.purchase,
                entitlementActive = saved.entitlementActive,
                consent = saved.consent,
                receipts = saved.receipts.take(CommercialLabState.MAX_RECEIPTS),
                nextReceiptSequence = saved.nextReceiptSequence,
            ),
        )
    }
}

internal object CommercialLabLaunchPolicy {
    const val INTERNAL_APPLICATION_ID = "app.codecks.internal"
    fun allows(applicationId: String): Boolean = applicationId == INTERNAL_APPLICATION_ID
}
