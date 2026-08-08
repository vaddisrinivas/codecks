package io.codecks.domain.commercial

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CommercialServiceContractsTest {
    private val operationId = CommercialOperationId.trusted("operation-0001")
    private val accountId = BackendAccountId.verified("account-0001")
    private val localSnapshot = LocalSnapshotHandle.trusted("local-snapshot-0001")
    private val cloudSnapshot = CloudSnapshotHandle.trusted("cloud-snapshot-0001")
    private val checksum = SnapshotChecksum("a".repeat(64))
    private val digest = CanonicalRequestDigest("b".repeat(64))

    @Test
    fun everyProductionDarkServiceIsInertAndTyped() = runBlocking {
        val services = ProductionDarkCommercialServices

        val config = services.operationalConfig.currentDenyState() as OperationalConfigResult.DenyState
        assertEquals(CommercialSurface.entries.toSet(), config.config.deniedSurfaces)
        assertEquals(OperationalDenySource.PRODUCTION_DARK, config.config.source)
        assertTrue(services.operationalConfig.refreshDenyState(operationId) is OperationalConfigResult.DenyState)

        assertTrue(services.account.currentSession() is AccountSessionResult.Unavailable)
        assertTrue(services.account.signIn(operationId) is AccountSignInResult.Denied)
        assertTrue(services.account.signOut(operationId) is AccountSignOutResult.Denied)
        assertTrue(services.account.deleteAccount(operationId) is AccountDeletionResult.Denied)

        assertTrue(services.snapshots.upload(uploadRequest()) is SnapshotTransferResult.Denied)
        assertTrue(services.snapshots.download(downloadRequest()) is SnapshotTransferResult.Denied)
        assertTrue(services.snapshots.previewRestore(previewRequest()) is SnapshotRestorePreviewResult.Denied)

        assertTrue(services.entitlements.cached(accountId) is EntitlementStateResult.Unavailable)
        assertTrue(services.entitlements.refresh(operationId, accountId) is EntitlementRefreshResult.Denied)
        assertTrue(services.integrity.collect(integrityRequest()) is TransactionIntegrityResult.Denied)
        assertTrue(services.purchases.launch(purchaseRequest()) is PurchaseLaunchResult.Denied)
        assertTrue(services.purchases.verify(verificationRequest()) is PurchaseVerificationResult.Denied)
        assertTrue(services.purchases.reconcile(operationId, accountId) is PurchaseVerificationResult.Denied)

        assertTrue(services.privacy.currentStatus() is PrivacyStatusResult.Unavailable)
        assertTrue(services.privacy.requestConsent(operationId) is PrivacyActionResult.Denied)
        assertTrue(services.privacy.openPrivacyOptions(operationId) is PrivacyActionResult.Denied)
        assertEquals(
            AdEligibilityResult.Denied(AdIneligibilityCode.PRODUCTION_DARK),
            services.ads.evaluate(adRequest()),
        )
    }

    @Test
    fun purchaseClientResultsNeverContainAnEntitlementGrant() {
        val attempt = PurchaseAttemptHandle.trusted("purchase-attempt-0001")
        val launchResults: List<PurchaseLaunchResult> = listOf(
            PurchaseLaunchResult.AwaitingServerVerification(attempt),
            PurchaseLaunchResult.Pending(attempt),
            PurchaseLaunchResult.Cancelled,
            PurchaseLaunchResult.AlreadyOwnedNeedsReconciliation,
            PurchaseLaunchResult.Denied(CommercialDenyReason.BUILD_PRODUCTION_DARK),
            PurchaseLaunchResult.Unavailable(CommercialUnavailableCode.PRODUCTION_DARK),
            PurchaseLaunchResult.Failed(CommercialFailureCode.REJECTED_BY_SERVER),
        )

        assertFalse(
            launchResults.any { result ->
                result.javaClass.declaredFields.any { field -> field.type == ServerEntitlementState::class.java }
            },
        )
        val receipt = PurchaseVerificationReceipt(
            operationId = operationId,
            accountId = accountId,
            lifecycleState = PurchaseServerLifecycleState.ACTIVE,
            backendRevision = 1L,
            verifiedAtEpochMillis = 1L,
        )
        assertTrue(receipt.entitlementRefreshRequired)
    }

    @Test
    fun opaqueIdentityAndHandlesDoNotRenderTheirValues() {
        listOf(operationId, accountId, localSnapshot, cloudSnapshot).forEach { opaque ->
            assertFalse(opaque.toString().contains("0001"))
            assertTrue(opaque.toString().contains("redacted"))
        }
    }

    @Test
    fun mutableInputsCannotElevateDenyOrEntitlementState() {
        val denied = mutableSetOf(CommercialSurface.ACCOUNT)
        val config = SubtractiveOperationalConfig(
            deniedSurfaces = denied,
            revision = 1L,
            expiresAtEpochMillis = null,
            source = OperationalDenySource.EMERGENCY,
        )
        denied.clear()
        assertTrue(config.denies(CommercialSurface.ACCOUNT))

        val capabilities = mutableSetOf(PaidCapability.SYNC)
        val state = ServerEntitlementState.verified(
            AccountBoundCacheMetadata(accountId, 1L, 2L, 1L),
            capabilities,
        )
        capabilities += PaidCapability.AD_FREE
        assertFalse(state.allows(PaidCapability.AD_FREE))
    }

    @Test
    fun restorePreviewCanNeverEnableImportedAutomations() {
        val preview = SnapshotRestorePreview(operationId, 1, 2, 3, 4)
        assertFalse(preview.importedAutomationsEnabled)
    }

    @Test
    fun serverLifecycleCoversTerminalRevocationAndRecoveryStates() {
        assertTrue(PurchaseServerLifecycleState.entries.contains(PurchaseServerLifecycleState.PENDING))
        assertTrue(PurchaseServerLifecycleState.entries.contains(PurchaseServerLifecycleState.GRACE_PERIOD))
        assertTrue(PurchaseServerLifecycleState.entries.contains(PurchaseServerLifecycleState.ON_HOLD))
        assertTrue(PurchaseServerLifecycleState.entries.contains(PurchaseServerLifecycleState.REFUNDED))
        assertTrue(PurchaseServerLifecycleState.entries.contains(PurchaseServerLifecycleState.REVOKED))
        assertTrue(PurchaseServerLifecycleState.entries.contains(PurchaseServerLifecycleState.CHARGEBACK))
    }

    @Test
    fun deletionReceiptRequiresSessionRevocationAndCloudRemovalForIdempotentRecovery() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountDeletionReceipt(
                operationId = operationId,
                completedAtEpochMillis = 1L,
                sessionsRevokedFirst = false,
                cloudSnapshotsDeleted = true,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountDeletionReceipt(
                operationId = operationId,
                completedAtEpochMillis = 1L,
                sessionsRevokedFirst = true,
                cloudSnapshotsDeleted = false,
            )
        }
        val recovered = AccountDeletionResult.AlreadyDeleted(operationId)
        assertEquals(operationId, recovered.operationId)
    }

    @Test
    fun integrityEvidenceCannotBeReplayedAcrossCommercialOperations() {
        val evidence = TransactionIntegrityEvidence(
            operationId = operationId,
            requestDigest = digest,
            evidence = IntegrityEvidenceHandle.trusted("integrity-evidence-0001"),
            expiresAtEpochMillis = 10L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            PurchaseVerificationRequest(
                operationId = CommercialOperationId.trusted("operation-0002"),
                accountId = accountId,
                attempt = PurchaseAttemptHandle.trusted("purchase-attempt-0001"),
                integrityEvidence = evidence,
            )
        }
    }

    private fun uploadRequest() = SnapshotUploadRequest(
        operationId = operationId,
        accountId = accountId,
        localSnapshot = localSnapshot,
        checksum = checksum,
        byteCount = 100,
        schemaVersion = 1,
    )

    private fun downloadRequest() = SnapshotDownloadRequest(operationId, accountId, cloudSnapshot)

    private fun previewRequest() = SnapshotRestorePreviewRequest(operationId, accountId, localSnapshot)

    private fun integrityRequest() = TransactionIntegrityRequest(operationId, digest)

    private fun purchaseRequest() = PurchaseLaunchRequest(
        operationId,
        accountId,
        ProductCatalogId("codecks.pro"),
    )

    private fun verificationRequest(): PurchaseVerificationRequest {
        val evidence = TransactionIntegrityEvidence(
            operationId = operationId,
            requestDigest = digest,
            evidence = IntegrityEvidenceHandle.trusted("integrity-evidence-0001"),
            expiresAtEpochMillis = 10L,
        )
        return PurchaseVerificationRequest(
            operationId,
            accountId,
            PurchaseAttemptHandle.trusted("purchase-attempt-0001"),
            evidence,
        )
    }

    private fun adRequest() = AdEligibilityRequest(
        placement = ApprovedAdPlacement.ROUTINE_BANK_CARD,
        commercialDecision = CommercialExecutionPolicy.PRODUCTION_DARK.decide(CommercialSurface.ADS),
        consentStatus = PrivacyConsentStatus.GRANTED,
        isForeground = true,
        organicItemsBefore = 10,
        userRequestedReward = true,
        hasAdFreeEntitlement = false,
    )
}
