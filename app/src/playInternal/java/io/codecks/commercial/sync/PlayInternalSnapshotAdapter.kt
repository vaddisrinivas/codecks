package io.codecks.commercial.sync

import io.codecks.domain.commercial.BackendAccountId
import io.codecks.domain.commercial.BuildCapability
import io.codecks.domain.commercial.CloudSnapshotHandle
import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialDenyReason
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialFailureCode
import io.codecks.domain.commercial.CommercialGateState
import io.codecks.domain.commercial.CommercialRetryDirective
import io.codecks.domain.commercial.CommercialSnapshotService
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.Compatibility
import io.codecks.domain.commercial.ConsentState
import io.codecks.domain.commercial.EmergencyDeny
import io.codecks.domain.commercial.EntitlementProjection
import io.codecks.domain.commercial.LocalSnapshotHandle
import io.codecks.domain.commercial.OwnerActivation
import io.codecks.domain.commercial.RolloutAssignment
import io.codecks.domain.commercial.SnapshotChecksum
import io.codecks.domain.commercial.SnapshotDownloadReceipt
import io.codecks.domain.commercial.SnapshotDownloadRequest
import io.codecks.domain.commercial.SnapshotRestorePreview
import io.codecks.domain.commercial.SnapshotRestorePreviewRequest
import io.codecks.domain.commercial.SnapshotRestorePreviewResult
import io.codecks.domain.commercial.SnapshotTransferResult
import io.codecks.domain.commercial.SnapshotUploadReceipt
import io.codecks.domain.commercial.SnapshotUploadRequest
import io.codecks.domain.commercial.UserOptIn
import io.codecks.internalcommercial.CommercialTestOverrideMarker
import java.security.MessageDigest

class PortableSnapshotDocument(
    bytes: ByteArray,
    val additions: Int,
    val replacements: Int,
    val conflicts: Int,
    val unsupportedItems: Int,
    val importedAutomationsEnabled: Boolean,
) {
    private val canonicalBytes = bytes.copyOf()
    val byteCount: Int get() = canonicalBytes.size
    val checksum: SnapshotChecksum by lazy {
        SnapshotChecksum(
            MessageDigest.getInstance("SHA-256")
                .digest(canonicalBytes)
                .joinToString("") { "%02x".format(it) },
        )
    }

    init {
        require(canonicalBytes.isNotEmpty() && canonicalBytes.size <= 256 * 1024)
        require(additions >= 0 && replacements >= 0 && conflicts >= 0 && unsupportedItems >= 0)
    }

    fun inspect(block: (ByteArray) -> Boolean): Boolean = block(canonicalBytes.copyOf())

    fun safeForImport(): PortableSnapshotDocument = PortableSnapshotDocument(
        canonicalBytes,
        additions,
        replacements,
        conflicts,
        unsupportedItems,
        importedAutomationsEnabled = false,
    )

    override fun toString(): String = "PortableSnapshotDocument(redacted,$byteCount bytes)"
}

fun interface PortableSnapshotValidator {
    fun isValid(document: PortableSnapshotDocument): Boolean
}

fun interface ActiveAccountProvider {
    suspend fun currentAccountId(): BackendAccountId?
}

interface CloudSyncActivationPort {
    suspend fun isExplicitlyEnabled(accountId: BackendAccountId): Boolean
}

class InMemoryCloudSyncActivationPort : CloudSyncActivationPort {
    private val enabled = mutableSetOf<BackendAccountId>()
    fun enable(accountId: BackendAccountId) {
        enabled += accountId
    }
    override suspend fun isExplicitlyEnabled(accountId: BackendAccountId): Boolean = accountId in enabled
}

data class CloudDownload(
    val localDocument: PortableSnapshotDocument,
)

interface SnapshotCloudTransport {
    suspend fun upload(
        operationId: io.codecks.domain.commercial.CommercialOperationId,
        accountId: BackendAccountId,
        document: PortableSnapshotDocument,
    ): CloudSnapshotHandle

    suspend fun download(
        operationId: io.codecks.domain.commercial.CommercialOperationId,
        accountId: BackendAccountId,
        cloudSnapshot: CloudSnapshotHandle,
    ): CloudDownload
}

interface PortableSnapshotStore {
    suspend fun read(accountId: BackendAccountId, handle: LocalSnapshotHandle): PortableSnapshotDocument?
    suspend fun writeDownloaded(accountId: BackendAccountId, document: PortableSnapshotDocument): LocalSnapshotHandle
    suspend fun validateForRestore(document: PortableSnapshotDocument, mode: RestoreMode): Boolean
    suspend fun commitRestore(document: PortableSnapshotDocument, mode: RestoreMode)
}

class RetryableSnapshotException(
    val code: CommercialFailureCode,
    val retryAfterMillis: Long? = null,
) : Exception("RetryableSnapshotException($code)")

enum class RestoreMode {
    MERGE,
    REPLACE,
}

class SafetyBackupHandle private constructor(private val value: String) {
    init {
        require(value.matches(Regex("[A-Za-z0-9._:-]{8,128}")))
    }
    override fun toString(): String = "SafetyBackupHandle(redacted)"
    companion object {
        fun trusted(value: String): SafetyBackupHandle = SafetyBackupHandle(value)
    }
}

interface RestoreSafetyBackupPort {
    suspend fun create(operationId: io.codecks.domain.commercial.CommercialOperationId): SafetyBackupHandle
    suspend fun rollback(handle: SafetyBackupHandle)
}

enum class RestorePhase {
    PREVIEWED,
    SAFETY_BACKUP_CREATED,
    VALIDATED,
    COMMITTED,
    ROLLED_BACK,
    ROLLBACK_FAILED,
}

data class RestoreProcessState(
    val operationId: io.codecks.domain.commercial.CommercialOperationId,
    val accountId: BackendAccountId,
    val localSnapshot: LocalSnapshotHandle,
    val previewChecksum: SnapshotChecksum,
    val phase: RestorePhase,
    val mode: RestoreMode? = null,
    val safetyBackup: SafetyBackupHandle? = null,
)

interface RestoreProcessStore {
    suspend fun get(operationId: io.codecks.domain.commercial.CommercialOperationId): RestoreProcessState?
    suspend fun put(state: RestoreProcessState)
}

class InMemoryRestoreProcessStore : RestoreProcessStore {
    private val states = mutableMapOf<io.codecks.domain.commercial.CommercialOperationId, RestoreProcessState>()
    override suspend fun get(operationId: io.codecks.domain.commercial.CommercialOperationId): RestoreProcessState? =
        states[operationId]
    override suspend fun put(state: RestoreProcessState) {
        states[state.operationId] = state
    }
}

data class RestoreExecutionRequest(
    val operationId: io.codecks.domain.commercial.CommercialOperationId,
    val accountId: BackendAccountId,
    val localSnapshot: LocalSnapshotHandle,
    val mode: RestoreMode,
)

data class RestoreCommitReceipt(
    val operationId: io.codecks.domain.commercial.CommercialOperationId,
    val accountId: BackendAccountId,
    val mode: RestoreMode,
    val importedAutomationsEnabled: Boolean = false,
)

sealed interface RestoreExecutionResult {
    data class Committed(val receipt: RestoreCommitReceipt) : RestoreExecutionResult
    data class Denied(val reason: CommercialDenyReason) : RestoreExecutionResult
    data class Failed(val code: CommercialFailureCode, val rolledBack: Boolean) : RestoreExecutionResult
}

class PlayInternalSnapshotAdapter(
    private val accountProvider: ActiveAccountProvider,
    private val activation: CloudSyncActivationPort,
    cloudFactory: () -> SnapshotCloudTransport,
    private val localStore: PortableSnapshotStore,
    private val validator: PortableSnapshotValidator,
    private val safetyBackup: RestoreSafetyBackupPort,
    private val processStore: RestoreProcessStore,
    private val clock: () -> Long,
) : CommercialSnapshotService {
    private data class UploadReplay(
        val request: SnapshotUploadRequest,
        val result: SnapshotTransferResult.Uploaded,
    )

    private data class DownloadReplay(
        val request: SnapshotDownloadRequest,
        val result: SnapshotTransferResult.Downloaded,
    )

    private val policy = CommercialTestOverrideMarker.policyFor(
        CommercialGateState(
            buildCapability = BuildCapability.INTERNAL_TEST_CAPABLE,
            ownerActivation = OwnerActivation.APPROVED,
            compatibility = Compatibility.SUPPORTED,
            emergencyDeny = EmergencyDeny.CLEAR,
            rolloutAssignment = RolloutAssignment.INCLUDED,
            entitlementProjection = EntitlementProjection.NOT_REQUIRED,
            consentState = ConsentState.NOT_REQUIRED,
            userOptIn = UserOptIn.ACCEPTED,
        ),
    )
    private val cloud by lazy(cloudFactory)
    private val uploadReplays = mutableMapOf<io.codecks.domain.commercial.CommercialOperationId, UploadReplay>()
    private val downloadReplays = mutableMapOf<io.codecks.domain.commercial.CommercialOperationId, DownloadReplay>()

    override suspend fun upload(request: SnapshotUploadRequest): SnapshotTransferResult {
        accessDenial(request.accountId)?.let { return SnapshotTransferResult.Denied(it) }
        uploadReplays[request.operationId]?.let { replay ->
            return if (replay.request == request) {
                replay.result
            } else {
                failedTransfer(
                    request,
                    if (replay.request.accountId == request.accountId) {
                        CommercialFailureCode.INVALID_STATE
                    } else {
                        CommercialFailureCode.ACCOUNT_MISMATCH
                    },
                )
            }
        }
        return try {
            val document = localStore.read(request.accountId, request.localSnapshot)
                ?: return failedTransfer(request, CommercialFailureCode.INVALID_STATE)
            if (
                !validator.isValid(document) ||
                document.byteCount != request.byteCount ||
                document.checksum != request.checksum
            ) {
                return failedTransfer(request, CommercialFailureCode.INVALID_STATE)
            }
            val cloudHandle = cloud.upload(request.operationId, request.accountId, document)
            SnapshotTransferResult.Uploaded(
                SnapshotUploadReceipt(
                    request.operationId,
                    request.accountId,
                    cloudHandle,
                    document.checksum,
                    clock(),
                ),
            ).also { uploadReplays[request.operationId] = UploadReplay(request, it) }
        } catch (failure: RetryableSnapshotException) {
            SnapshotTransferResult.RetryableFailure(
                request.operationId,
                failure.code,
                CommercialRetryDirective(failure.retryAfterMillis),
            )
        } catch (_: Exception) {
            failedTransfer(request, CommercialFailureCode.UNKNOWN)
        }
    }

    override suspend fun download(request: SnapshotDownloadRequest): SnapshotTransferResult {
        accessDenial(request.accountId)?.let { return SnapshotTransferResult.Denied(it) }
        downloadReplays[request.operationId]?.let { replay ->
            return if (replay.request == request) {
                replay.result
            } else {
                SnapshotTransferResult.RetryableFailure(
                    request.operationId,
                    if (replay.request.accountId == request.accountId) {
                        CommercialFailureCode.INVALID_STATE
                    } else {
                        CommercialFailureCode.ACCOUNT_MISMATCH
                    },
                    CommercialRetryDirective(null),
                )
            }
        }
        return try {
            val downloaded = cloud.download(request.operationId, request.accountId, request.cloudSnapshot)
            if (!validator.isValid(downloaded.localDocument)) {
                return SnapshotTransferResult.RetryableFailure(
                    request.operationId,
                    CommercialFailureCode.INVALID_STATE,
                    CommercialRetryDirective(null),
                )
            }
            val localHandle = localStore.writeDownloaded(request.accountId, downloaded.localDocument)
            SnapshotTransferResult.Downloaded(
                SnapshotDownloadReceipt(
                    request.operationId,
                    request.accountId,
                    localHandle,
                    downloaded.localDocument.checksum,
                    clock(),
                ),
            ).also { downloadReplays[request.operationId] = DownloadReplay(request, it) }
        } catch (failure: RetryableSnapshotException) {
            SnapshotTransferResult.RetryableFailure(
                request.operationId,
                failure.code,
                CommercialRetryDirective(failure.retryAfterMillis),
            )
        } catch (_: Exception) {
            SnapshotTransferResult.RetryableFailure(
                request.operationId,
                CommercialFailureCode.UNKNOWN,
                CommercialRetryDirective(null),
            )
        }
    }

    override suspend fun previewRestore(request: SnapshotRestorePreviewRequest): SnapshotRestorePreviewResult {
        accessDenial(request.accountId)?.let { return SnapshotRestorePreviewResult.Denied(it) }
        return try {
            val document = localStore.read(request.accountId, request.localSnapshot)
                ?: return SnapshotRestorePreviewResult.Failed(CommercialFailureCode.INVALID_STATE)
            if (!validator.isValid(document)) return SnapshotRestorePreviewResult.Failed(CommercialFailureCode.INVALID_STATE)
            if (processStore.get(request.operationId) != null) {
                return SnapshotRestorePreviewResult.Failed(CommercialFailureCode.INVALID_STATE)
            }
            processStore.put(
                RestoreProcessState(
                    request.operationId,
                    request.accountId,
                    request.localSnapshot,
                    document.checksum,
                    RestorePhase.PREVIEWED,
                ),
            )
            SnapshotRestorePreviewResult.Ready(
                SnapshotRestorePreview(
                    request.operationId,
                    document.additions,
                    document.replacements,
                    document.conflicts,
                    document.unsupportedItems,
                ),
            )
        } catch (_: Exception) {
            SnapshotRestorePreviewResult.Failed(CommercialFailureCode.UNKNOWN)
        }
    }

    suspend fun executeRestore(request: RestoreExecutionRequest): RestoreExecutionResult {
        accessDenial(request.accountId)?.let { return RestoreExecutionResult.Denied(it) }
        var state = processStore.get(request.operationId)
            ?: return RestoreExecutionResult.Failed(CommercialFailureCode.INVALID_STATE, rolledBack = false)
        if (
            state.accountId != request.accountId ||
            state.localSnapshot != request.localSnapshot ||
            (state.mode != null && state.mode != request.mode)
        ) {
            return RestoreExecutionResult.Failed(CommercialFailureCode.INVALID_STATE, rolledBack = false)
        }
        if (state.phase == RestorePhase.COMMITTED) {
            return RestoreExecutionResult.Committed(
                RestoreCommitReceipt(request.operationId, request.accountId, request.mode),
            )
        }
        if (state.phase == RestorePhase.ROLLED_BACK) {
            return RestoreExecutionResult.Failed(CommercialFailureCode.INVALID_STATE, rolledBack = true)
        }
        if (state.phase == RestorePhase.ROLLBACK_FAILED) {
            return RestoreExecutionResult.Failed(CommercialFailureCode.INVALID_STATE, rolledBack = false)
        }
        if (state.phase == RestorePhase.PREVIEWED) {
            val createdBackup = try {
                safetyBackup.create(request.operationId)
            } catch (_: Exception) {
                return RestoreExecutionResult.Failed(CommercialFailureCode.UNKNOWN, rolledBack = false)
            }
            state = state.copy(
                phase = RestorePhase.SAFETY_BACKUP_CREATED,
                mode = request.mode,
                safetyBackup = createdBackup,
            )
            processStore.put(state)
        }
        val backup = state.safetyBackup
            ?: return RestoreExecutionResult.Failed(CommercialFailureCode.INVALID_STATE, rolledBack = false)
        return try {
            val document = localStore.read(request.accountId, request.localSnapshot)
                ?: error("restore snapshot disappeared")
            if (!validator.isValid(document)) error("restore validation failed")
            if (document.checksum != state.previewChecksum) error("restore snapshot changed after preview")
            val safeDocument = document.safeForImport()
            if (!localStore.validateForRestore(safeDocument, request.mode)) error("restore validation failed")
            if (state.phase == RestorePhase.SAFETY_BACKUP_CREATED) {
                state = state.copy(
                    phase = RestorePhase.VALIDATED,
                    mode = request.mode,
                    safetyBackup = backup,
                )
                processStore.put(state)
            }
            localStore.commitRestore(safeDocument, request.mode)
            processStore.put(
                state.copy(
                    phase = RestorePhase.COMMITTED,
                    mode = request.mode,
                    safetyBackup = backup,
                ),
            )
            RestoreExecutionResult.Committed(
                RestoreCommitReceipt(request.operationId, request.accountId, request.mode),
            )
        } catch (_: Exception) {
            val rolledBack = runCatching { safetyBackup.rollback(backup) }.isSuccess
            runCatching {
                processStore.put(
                    state.copy(
                        phase = if (rolledBack) RestorePhase.ROLLED_BACK else RestorePhase.ROLLBACK_FAILED,
                        mode = request.mode,
                        safetyBackup = backup,
                    ),
                )
            }
            RestoreExecutionResult.Failed(CommercialFailureCode.INVALID_STATE, rolledBack = rolledBack)
        }
    }

    private suspend fun accessDenial(accountId: BackendAccountId): CommercialDenyReason? {
        when (val decision = policy.decide(CommercialSurface.CLOUD_SYNC)) {
            is CommercialDecision.Denied -> return decision.reason
            is CommercialDecision.Allowed -> Unit
        }
        if (accountProvider.currentAccountId() != accountId) return CommercialDenyReason.ENTITLEMENT_DENIED
        if (!activation.isExplicitlyEnabled(accountId)) return CommercialDenyReason.USER_OPT_IN_UNKNOWN
        return null
    }

    private fun failedTransfer(request: SnapshotUploadRequest, code: CommercialFailureCode) =
        SnapshotTransferResult.RetryableFailure(request.operationId, code, CommercialRetryDirective(null))
}
