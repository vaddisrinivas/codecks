package io.codecks.domain.commercial

private val productionDarkReason = CommercialDenyReason.BUILD_PRODUCTION_DARK

object ProductionDarkOperationalConfigService : CommercialOperationalConfigService {
    private val denyAll = OperationalConfigResult.DenyState(
        SubtractiveOperationalConfig(
            deniedSurfaces = CommercialSurface.entries.toSet(),
            revision = 0L,
            expiresAtEpochMillis = null,
            source = OperationalDenySource.PRODUCTION_DARK,
        ),
    )

    override suspend fun currentDenyState(): OperationalConfigResult = denyAll

    override suspend fun refreshDenyState(operationId: CommercialOperationId): OperationalConfigResult = denyAll
}

object ProductionDarkAccountService : CommercialAccountService {
    override suspend fun currentSession(): AccountSessionResult =
        AccountSessionResult.Unavailable(CommercialUnavailableCode.PRODUCTION_DARK)

    override suspend fun signIn(operationId: CommercialOperationId): AccountSignInResult =
        AccountSignInResult.Denied(productionDarkReason)

    override suspend fun signOut(operationId: CommercialOperationId): AccountSignOutResult =
        AccountSignOutResult.Denied(productionDarkReason)

    override suspend fun deleteAccount(operationId: CommercialOperationId): AccountDeletionResult =
        AccountDeletionResult.Denied(productionDarkReason)
}

object ProductionDarkSnapshotService : CommercialSnapshotService {
    override suspend fun upload(request: SnapshotUploadRequest): SnapshotTransferResult =
        SnapshotTransferResult.Denied(productionDarkReason)

    override suspend fun download(request: SnapshotDownloadRequest): SnapshotTransferResult =
        SnapshotTransferResult.Denied(productionDarkReason)

    override suspend fun previewRestore(request: SnapshotRestorePreviewRequest): SnapshotRestorePreviewResult =
        SnapshotRestorePreviewResult.Denied(productionDarkReason)
}

object ProductionDarkEntitlementService : CommercialEntitlementService {
    override suspend fun cached(accountId: BackendAccountId): EntitlementStateResult =
        EntitlementStateResult.Unavailable(CommercialUnavailableCode.PRODUCTION_DARK)

    override suspend fun refresh(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): EntitlementRefreshResult = EntitlementRefreshResult.Denied(productionDarkReason)
}

object ProductionDarkIntegrityService : TransactionIntegrityService {
    override suspend fun collect(request: TransactionIntegrityRequest): TransactionIntegrityResult =
        TransactionIntegrityResult.Denied(productionDarkReason)
}

object ProductionDarkPurchaseService : CommercialPurchaseService {
    override suspend fun launch(request: PurchaseLaunchRequest): PurchaseLaunchResult =
        PurchaseLaunchResult.Denied(productionDarkReason)

    override suspend fun verify(request: PurchaseVerificationRequest): PurchaseVerificationResult =
        PurchaseVerificationResult.Denied(productionDarkReason)

    override suspend fun reconcile(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
    ): PurchaseVerificationResult = PurchaseVerificationResult.Denied(productionDarkReason)
}

object ProductionDarkPrivacyService : CommercialPrivacyService {
    override suspend fun currentStatus(): PrivacyStatusResult =
        PrivacyStatusResult.Unavailable(CommercialUnavailableCode.PRODUCTION_DARK)

    override suspend fun requestConsent(operationId: CommercialOperationId): PrivacyActionResult =
        PrivacyActionResult.Denied(productionDarkReason)

    override suspend fun openPrivacyOptions(operationId: CommercialOperationId): PrivacyActionResult =
        PrivacyActionResult.Denied(productionDarkReason)
}

object ProductionDarkAdEligibilityService : CommercialAdEligibilityService {
    override fun evaluate(request: AdEligibilityRequest): AdEligibilityResult =
        AdEligibilityResult.Denied(AdIneligibilityCode.PRODUCTION_DARK)
}

object ProductionDarkCommercialServices {
    val operationalConfig: CommercialOperationalConfigService = ProductionDarkOperationalConfigService
    val account: CommercialAccountService = ProductionDarkAccountService
    val snapshots: CommercialSnapshotService = ProductionDarkSnapshotService
    val entitlements: CommercialEntitlementService = ProductionDarkEntitlementService
    val integrity: TransactionIntegrityService = ProductionDarkIntegrityService
    val purchases: CommercialPurchaseService = ProductionDarkPurchaseService
    val privacy: CommercialPrivacyService = ProductionDarkPrivacyService
    val ads: CommercialAdEligibilityService = ProductionDarkAdEligibilityService
}
