package io.codecks.commercial.sync

import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.CloudSnapshotHandle
import io.codecks.domain.commercial.CommercialDenyReason
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialOperationId
import io.codecks.domain.commercial.LocalSnapshotHandle
import io.codecks.domain.commercial.SnapshotDownloadRequest
import io.codecks.domain.commercial.SnapshotRestorePreviewRequest
import io.codecks.domain.commercial.SnapshotRestorePreviewResult
import io.codecks.domain.commercial.SnapshotTransferResult
import io.codecks.domain.commercial.SnapshotUploadRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayInternalSnapshotAdapterTest {
    @Test
    fun firstSignInCannotTransferUntilExplicitActivationAndRetriesProduceReceipt() = runTest {
        val account = BackendAccountId.verified("account-sync-alpha")
        val other = BackendAccountId.verified("account-sync-other")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort()
        val local = FakePortableSnapshotStore()
        val document = document("safe portable snapshot")
        val localHandle = local.add(account, document)
        val cloud = FakeSnapshotCloudTransport().also { it.failNextUpload = true }
        var cloudConstructions = 0
        val service = service(active, activation, local, { cloudConstructions++; cloud })
        val request = SnapshotUploadRequest(
            operation("upload-explicit"),
            account,
            localHandle,
            document.checksum,
            document.byteCount,
            schemaVersion = 1,
        )

        val beforeOptIn = service.upload(request)
        assertTrue(beforeOptIn is SnapshotTransferResult.Denied)
        assertEquals(CommercialDenyReason.USER_OPT_IN_UNKNOWN, (beforeOptIn as SnapshotTransferResult.Denied).reason)
        assertEquals(0, cloudConstructions)

        activation.enable(account)
        val first = service.upload(request)
        assertTrue(first is SnapshotTransferResult.RetryableFailure)
        assertEquals(CommercialFailureCode.TIMEOUT, (first as SnapshotTransferResult.RetryableFailure).code)
        val retry = service.upload(request)
        assertTrue(retry is SnapshotTransferResult.Uploaded)
        assertEquals(document.checksum, (retry as SnapshotTransferResult.Uploaded).receipt.checksum)
        assertEquals(1, cloudConstructions)
        val replay = service.upload(request)
        assertEquals(retry, replay)
        assertEquals(2, cloud.uploadAttempts)

        val otherDocument = document("other account snapshot")
        val otherHandle = local.add(other, otherDocument)

        activation.enable(other)
        val crossAccount = service.download(
            SnapshotDownloadRequest(operation("download-other"), other, retry.receipt.cloudSnapshot),
        )
        assertTrue(crossAccount is SnapshotTransferResult.Denied)
        assertEquals(CommercialDenyReason.ENTITLEMENT_DENIED, (crossAccount as SnapshotTransferResult.Denied).reason)

        active.accountId = other
        val crossAccountUpload = service.upload(
            SnapshotUploadRequest(
                request.operationId,
                other,
                otherHandle,
                otherDocument.checksum,
                otherDocument.byteCount,
                1,
            ),
        )
        assertTrue(crossAccountUpload is SnapshotTransferResult.RetryableFailure)
        assertEquals(
            CommercialFailureCode.ACCOUNT_MISMATCH,
            (crossAccountUpload as SnapshotTransferResult.RetryableFailure).code,
        )
        assertEquals(2, cloud.uploadAttempts)
        val isolatedAtTransport = service.download(
            SnapshotDownloadRequest(operation("download-foreign-handle"), other, retry.receipt.cloudSnapshot),
        )
        assertTrue(isolatedAtTransport is SnapshotTransferResult.RetryableFailure)
        assertEquals(
            CommercialFailureCode.ACCOUNT_MISMATCH,
            (isolatedAtTransport as SnapshotTransferResult.RetryableFailure).code,
        )
    }

    @Test
    fun downloadAndPreviewAreAccountBoundAndReceipted() = runTest {
        val account = BackendAccountId.verified("account-download-01")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort().also { it.enable(account) }
        val local = FakePortableSnapshotStore()
        val cloud = FakeSnapshotCloudTransport()
        val source = document("downloadable portable snapshot")
        val cloudHandle = cloud.put(account, source)
        val service = service(active, activation, local, { cloud })

        val downloaded = service.download(
            SnapshotDownloadRequest(operation("download-receipt"), account, cloudHandle),
        )
        assertTrue(downloaded is SnapshotTransferResult.Downloaded)
        val receipt = (downloaded as SnapshotTransferResult.Downloaded).receipt
        assertEquals(source.checksum, receipt.checksum)
        val preview = service.previewRestore(
            SnapshotRestorePreviewRequest(operation("preview-download"), account, receipt.localSnapshot),
        )
        assertTrue(preview is SnapshotRestorePreviewResult.Ready)
        assertFalse((preview as SnapshotRestorePreviewResult.Ready).preview.importedAutomationsEnabled)
    }

    @Test
    fun restoreRequiresPreviewSurvivesServiceRecreationAndDisablesImportedAutomations() = runTest {
        val account = BackendAccountId.verified("account-restore-01")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort().also { it.enable(account) }
        val local = FakePortableSnapshotStore()
        val handle = local.add(account, document("restore document", automationsEnabled = true))
        val process = InMemoryRestoreProcessStore()
        val safety = FakeSafetyBackupPort()
        val operation = operation("restore-process")
        val firstService = service(active, activation, local, { FakeSnapshotCloudTransport() }, safety, process)

        val noPreview = firstService.executeRestore(RestoreExecutionRequest(operation, account, handle, RestoreMode.REPLACE))
        assertTrue(noPreview is RestoreExecutionResult.Failed)
        firstService.previewRestore(SnapshotRestorePreviewRequest(operation, account, handle))

        val recreated = service(active, activation, local, { FakeSnapshotCloudTransport() }, safety, process)
        val restored = recreated.executeRestore(RestoreExecutionRequest(operation, account, handle, RestoreMode.REPLACE))
        assertTrue(restored is RestoreExecutionResult.Committed)
        val receipt = (restored as RestoreExecutionResult.Committed).receipt
        assertEquals(RestoreMode.REPLACE, receipt.mode)
        assertFalse(receipt.importedAutomationsEnabled)
        assertFalse(requireNotNull(local.committed).importedAutomationsEnabled)
        assertEquals(1, safety.created)
        assertEquals(RestorePhase.COMMITTED, requireNotNull(process.get(operation)).phase)
    }

    @Test
    fun restoreCommitFailureRollsBackAndPersistsTerminalProcessState() = runTest {
        val account = BackendAccountId.verified("account-rollback-01")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort().also { it.enable(account) }
        val local = FakePortableSnapshotStore().also { it.failCommit = true }
        val handle = local.add(account, document("rollback document"))
        val process = InMemoryRestoreProcessStore()
        val safety = FakeSafetyBackupPort()
        val operation = operation("restore-rollback")
        val service = service(active, activation, local, { FakeSnapshotCloudTransport() }, safety, process)
        service.previewRestore(SnapshotRestorePreviewRequest(operation, account, handle))

        val result = service.executeRestore(RestoreExecutionRequest(operation, account, handle, RestoreMode.MERGE))
        assertTrue(result is RestoreExecutionResult.Failed)
        assertTrue((result as RestoreExecutionResult.Failed).rolledBack)
        assertEquals(1, safety.rolledBack)
        assertEquals(RestorePhase.ROLLED_BACK, requireNotNull(process.get(operation)).phase)
    }

    @Test
    fun restoreRejectsSnapshotChangedAfterPersistedPreviewAndRollsBack() = runTest {
        val account = BackendAccountId.verified("account-swap-restore")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort().also { it.enable(account) }
        val local = FakePortableSnapshotStore()
        val handle = local.add(account, document("previewed document"))
        val process = InMemoryRestoreProcessStore()
        val safety = FakeSafetyBackupPort()
        val operation = operation("restore-swap")
        val service = service(active, activation, local, { FakeSnapshotCloudTransport() }, safety, process)
        service.previewRestore(SnapshotRestorePreviewRequest(operation, account, handle))
        local.replace(account, handle, document("different valid document"))

        val result = service.executeRestore(RestoreExecutionRequest(operation, account, handle, RestoreMode.REPLACE))

        assertTrue(result is RestoreExecutionResult.Failed)
        assertTrue((result as RestoreExecutionResult.Failed).rolledBack)
        assertEquals(null, local.committed)
        assertEquals(RestorePhase.ROLLED_BACK, requireNotNull(process.get(operation)).phase)
    }

    @Test
    fun validatedRestoreRevalidatesAfterRecreationAndReportsRollbackFailure() = runTest {
        val account = BackendAccountId.verified("account-revalidate-1")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort().also { it.enable(account) }
        val local = FakePortableSnapshotStore()
        val document = document("revalidate document")
        val handle = local.add(account, document)
        val process = InMemoryRestoreProcessStore()
        val safety = FakeSafetyBackupPort().also { it.failRollback = true }
        val operation = operation("restore-revalidate")
        service(active, activation, local, { FakeSnapshotCloudTransport() }, safety, process)
            .previewRestore(SnapshotRestorePreviewRequest(operation, account, handle))
        process.put(
            requireNotNull(process.get(operation)).copy(
                phase = RestorePhase.VALIDATED,
                mode = RestoreMode.MERGE,
                safetyBackup = SafetyBackupHandle.trusted("persisted-backup-01"),
            ),
        )
        local.failValidation = true

        val recreated = service(active, activation, local, { FakeSnapshotCloudTransport() }, safety, process)
        val result = recreated.executeRestore(RestoreExecutionRequest(operation, account, handle, RestoreMode.MERGE))

        assertTrue(result is RestoreExecutionResult.Failed)
        assertFalse((result as RestoreExecutionResult.Failed).rolledBack)
        assertEquals(1, local.validationCalls)
        assertEquals(RestorePhase.ROLLBACK_FAILED, requireNotNull(process.get(operation)).phase)
    }

    @Test
    fun secretCanaryIsRejectedWithoutLeakingIntoResultOrProcessState() = runTest {
        val account = BackendAccountId.verified("account-canary-0001")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort().also { it.enable(account) }
        val local = FakePortableSnapshotStore()
        val canary = "SECRET_CANARY_DO_NOT_SYNC"
        val handle = local.add(account, document(canary))
        val process = InMemoryRestoreProcessStore()
        val service = service(active, activation, local, { FakeSnapshotCloudTransport() }, process = process)
        val operation = operation("preview-canary")

        val result = service.previewRestore(SnapshotRestorePreviewRequest(operation, account, handle))
        assertTrue(result is SnapshotRestorePreviewResult.Failed)
        assertFalse(result.toString().contains(canary))
        assertEquals(null, process.get(operation))
    }

    @Test
    fun validatorExceptionCannotLeakSecretCanary() = runTest {
        val account = BackendAccountId.verified("account-canary-throw")
        val active = MutableAccountProvider(account)
        val activation = InMemoryCloudSyncActivationPort().also { it.enable(account) }
        val local = FakePortableSnapshotStore()
        val canary = "SECRET_CANARY_VALIDATOR_EXCEPTION"
        val handle = local.add(account, document(canary))
        val process = InMemoryRestoreProcessStore()
        val operation = operation("preview-canary-throw")
        val service = service(
            active,
            activation,
            local,
            { FakeSnapshotCloudTransport() },
            process = process,
            validator = PortableSnapshotValidator { document ->
                document.inspect { bytes -> error(bytes.decodeToString()) }
            },
        )

        val result = service.previewRestore(SnapshotRestorePreviewRequest(operation, account, handle))

        assertTrue(result is SnapshotRestorePreviewResult.Failed)
        assertEquals(CommercialFailureCode.UNKNOWN, (result as SnapshotRestorePreviewResult.Failed).code)
        assertFalse(result.toString().contains(canary))
        assertEquals(null, process.get(operation))
    }

    private fun service(
        active: MutableAccountProvider,
        activation: InMemoryCloudSyncActivationPort,
        local: FakePortableSnapshotStore,
        cloud: () -> FakeSnapshotCloudTransport,
        safety: FakeSafetyBackupPort = FakeSafetyBackupPort(),
        process: InMemoryRestoreProcessStore = InMemoryRestoreProcessStore(),
        validator: PortableSnapshotValidator = PortableSnapshotValidator { document ->
            document.inspect { bytes -> !bytes.decodeToString().contains("SECRET_CANARY") }
        },
    ) = PlayInternalSnapshotAdapter(
        accountProvider = active,
        activation = activation,
        cloudFactory = cloud,
        localStore = local,
        validator = validator,
        safetyBackup = safety,
        processStore = process,
        clock = { 9_000L },
    )

    private fun document(value: String, automationsEnabled: Boolean = false) = PortableSnapshotDocument(
        value.encodeToByteArray(),
        additions = 2,
        replacements = 1,
        conflicts = 0,
        unsupportedItems = 0,
        importedAutomationsEnabled = automationsEnabled,
    )

    private fun operation(value: String) = CommercialOperationId.trusted("operation-$value")
}

private class MutableAccountProvider(var accountId: BackendAccountId?) : ActiveAccountProvider {
    override suspend fun currentAccountId(): BackendAccountId? = accountId
}

private class FakePortableSnapshotStore : PortableSnapshotStore {
    private val documents = mutableMapOf<Pair<BackendAccountId, LocalSnapshotHandle>, PortableSnapshotDocument>()
    private var nextHandle = 0
    var committed: PortableSnapshotDocument? = null
    var failCommit = false
    var failValidation = false
    var validationCalls = 0

    fun add(accountId: BackendAccountId, document: PortableSnapshotDocument): LocalSnapshotHandle {
        val handle = LocalSnapshotHandle.trusted("local-handle-${++nextHandle}")
        documents[accountId to handle] = document
        return handle
    }

    fun replace(
        accountId: BackendAccountId,
        handle: LocalSnapshotHandle,
        document: PortableSnapshotDocument,
    ) {
        documents[accountId to handle] = document
    }

    override suspend fun read(
        accountId: BackendAccountId,
        handle: LocalSnapshotHandle,
    ): PortableSnapshotDocument? = documents[accountId to handle]

    override suspend fun writeDownloaded(
        accountId: BackendAccountId,
        document: PortableSnapshotDocument,
    ): LocalSnapshotHandle = add(accountId, document)

    override suspend fun validateForRestore(document: PortableSnapshotDocument, mode: RestoreMode): Boolean {
        validationCalls++
        return !failValidation
    }

    override suspend fun commitRestore(document: PortableSnapshotDocument, mode: RestoreMode) {
        if (failCommit) error("commit failed")
        committed = document
    }
}

private class FakeSnapshotCloudTransport : SnapshotCloudTransport {
    private val documents = mutableMapOf<Pair<BackendAccountId, CloudSnapshotHandle>, PortableSnapshotDocument>()
    private val operationReceipts = mutableMapOf<CommercialOperationId, CloudSnapshotHandle>()
    private var nextHandle = 0
    var failNextUpload = false
    var uploadAttempts = 0

    fun put(accountId: BackendAccountId, document: PortableSnapshotDocument): CloudSnapshotHandle {
        val handle = CloudSnapshotHandle.trusted("cloud-handle-${++nextHandle}")
        documents[accountId to handle] = document
        return handle
    }

    override suspend fun upload(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        document: PortableSnapshotDocument,
    ): CloudSnapshotHandle {
        uploadAttempts++
        if (failNextUpload) {
            failNextUpload = false
            throw RetryableSnapshotException(CommercialFailureCode.TIMEOUT, 10)
        }
        return operationReceipts.getOrPut(operationId) { put(accountId, document) }
    }

    override suspend fun download(
        operationId: CommercialOperationId,
        accountId: BackendAccountId,
        cloudSnapshot: CloudSnapshotHandle,
    ): CloudDownload {
        val document = documents[accountId to cloudSnapshot]
            ?: throw RetryableSnapshotException(CommercialFailureCode.ACCOUNT_MISMATCH)
        return CloudDownload(document)
    }
}

private class FakeSafetyBackupPort : RestoreSafetyBackupPort {
    var created = 0
    var rolledBack = 0
    var failRollback = false
    override suspend fun create(operationId: CommercialOperationId): SafetyBackupHandle =
        SafetyBackupHandle.trusted("backup-${++created}-handle")
    override suspend fun rollback(handle: SafetyBackupHandle) {
        if (failRollback) error("rollback failed")
        rolledBack++
    }
}
