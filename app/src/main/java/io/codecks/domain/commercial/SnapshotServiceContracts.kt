package io.codecks.domain.commercial

class LocalSnapshotHandle private constructor(private val canonical: String) {
    init {
        require(canonical.matches(OPAQUE_ID_REGEX))
    }

    override fun equals(other: Any?): Boolean =
        other is LocalSnapshotHandle && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = "LocalSnapshotHandle(redacted)"

    companion object {
        internal fun trusted(value: String): LocalSnapshotHandle = LocalSnapshotHandle(value)
    }
}

class CloudSnapshotHandle private constructor(private val canonical: String) {
    init {
        require(canonical.matches(OPAQUE_ID_REGEX))
    }

    override fun equals(other: Any?): Boolean =
        other is CloudSnapshotHandle && canonical == other.canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = "CloudSnapshotHandle(redacted)"

    companion object {
        internal fun trusted(value: String): CloudSnapshotHandle = CloudSnapshotHandle(value)
    }
}

data class SnapshotChecksum(val sha256: String) {
    init {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

data class SnapshotUploadRequest(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val localSnapshot: LocalSnapshotHandle,
    val checksum: SnapshotChecksum,
    val byteCount: Int,
    val schemaVersion: Int,
) {
    init {
        require(byteCount in 1..MAX_PORTABLE_SNAPSHOT_BYTES)
        require(schemaVersion > 0)
    }
}

data class SnapshotUploadReceipt(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val cloudSnapshot: CloudSnapshotHandle,
    val checksum: SnapshotChecksum,
    val completedAtEpochMillis: Long,
) {
    init {
        require(completedAtEpochMillis >= 0L)
    }
}

data class SnapshotDownloadRequest(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val cloudSnapshot: CloudSnapshotHandle,
)

data class SnapshotDownloadReceipt(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val localSnapshot: LocalSnapshotHandle,
    val checksum: SnapshotChecksum,
    val completedAtEpochMillis: Long,
) {
    init {
        require(completedAtEpochMillis >= 0L)
    }
}

data class SnapshotRestorePreviewRequest(
    val operationId: CommercialOperationId,
    val accountId: BackendAccountId,
    val localSnapshot: LocalSnapshotHandle,
)

data class SnapshotRestorePreview(
    val operationId: CommercialOperationId,
    val additions: Int,
    val replacements: Int,
    val conflicts: Int,
    val unsupportedItems: Int,
) {
    val importedAutomationsEnabled: Boolean = false

    init {
        require(additions >= 0)
        require(replacements >= 0)
        require(conflicts >= 0)
        require(unsupportedItems >= 0)
    }
}

sealed interface SnapshotTransferResult {
    data class Uploaded(val receipt: SnapshotUploadReceipt) : SnapshotTransferResult
    data class Downloaded(val receipt: SnapshotDownloadReceipt) : SnapshotTransferResult
    data class RetryableFailure(
        val operationId: CommercialOperationId,
        val code: CommercialFailureCode,
        val retry: CommercialRetryDirective,
    ) : SnapshotTransferResult
    data class Denied(val reason: CommercialDenyReason) : SnapshotTransferResult
    data class Unavailable(val code: CommercialUnavailableCode) : SnapshotTransferResult
}

sealed interface SnapshotRestorePreviewResult {
    data class Ready(val preview: SnapshotRestorePreview) : SnapshotRestorePreviewResult
    data class Denied(val reason: CommercialDenyReason) : SnapshotRestorePreviewResult
    data class Unavailable(val code: CommercialUnavailableCode) : SnapshotRestorePreviewResult
    data class Failed(val code: CommercialFailureCode) : SnapshotRestorePreviewResult
}

interface CommercialSnapshotService {
    suspend fun upload(request: SnapshotUploadRequest): SnapshotTransferResult
    suspend fun download(request: SnapshotDownloadRequest): SnapshotTransferResult
    suspend fun previewRestore(request: SnapshotRestorePreviewRequest): SnapshotRestorePreviewResult
}

private const val MAX_PORTABLE_SNAPSHOT_BYTES = 256 * 1024
