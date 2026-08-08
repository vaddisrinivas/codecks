package io.codecks.backend.contracts

const val MAX_SAFE_SNAPSHOT_BYTES: Int = 256 * 1024

class CanonicalSafeSnapshotBytes private constructor(
    bytes: ByteArray,
    val checksum: RequestHash,
) : RedactedValue {
    private val content = bytes.copyOf()
    val size: Int get() = content.size

    fun copyForStorage(): ByteArray = content.copyOf()
    override fun toString(): String = "CanonicalSafeSnapshotBytes(redacted,size=$size)"

    companion object {
        /** Called only after the shared safe-snapshot serializer has classified and canonicalized fields. */
        internal fun fromCanonicalSerializer(
            bytes: ByteArray,
            checksum: RequestHash,
        ): BackendResult<CanonicalSafeSnapshotBytes> = when {
            bytes.isEmpty() -> BackendResult.Rejected(BackendErrorCode.INVALID_SNAPSHOT)
            bytes.size > MAX_SAFE_SNAPSHOT_BYTES -> BackendResult.Rejected(BackendErrorCode.SNAPSHOT_TOO_LARGE)
            else -> BackendResult.Success(CanonicalSafeSnapshotBytes(bytes, checksum))
        }
    }
}

data class SnapshotMetadata(
    val snapshotId: SnapshotId,
    val accountId: AccountId,
    val schemaVersion: Int,
    val checksum: RequestHash,
    val byteCount: Int,
    val revision: Revision,
    val createdAtEpochMillis: Long,
) {
    init {
        require(byteCount in 1..MAX_SAFE_SNAPSHOT_BYTES)
        require(createdAtEpochMillis >= 0L)
    }
}

data class StoredSnapshot(
    val metadata: SnapshotMetadata,
    val bytes: CanonicalSafeSnapshotBytes,
) {
    init {
        require(metadata.byteCount == bytes.size)
        require(metadata.checksum == bytes.checksum)
    }
}

interface SnapshotStoragePort : AccountSnapshotDeletionPort {
    fun put(snapshot: StoredSnapshot, operationId: OperationId): BackendResult<SnapshotMetadata>
    fun get(accountId: AccountId, snapshotId: SnapshotId): BackendResult<StoredSnapshot>
}

class SnapshotService(
    supportedSchemaVersions: Set<Int>,
    private val storage: SnapshotStoragePort,
) {
    private val supportedSchemaVersions = supportedSchemaVersions.toSet().also {
        require(it.isNotEmpty())
        require(it.all { version -> version > 0 })
    }
    private val operations = mutableMapOf<OperationId, SnapshotMetadata>()

    fun upload(
        operationId: OperationId,
        metadata: SnapshotMetadata,
        bytes: CanonicalSafeSnapshotBytes,
    ): BackendResult<SnapshotMetadata> {
        operations[operationId]?.let { previous ->
            return if (previous == metadata) {
                BackendResult.Replayed(previous)
            } else {
                BackendResult.Rejected(BackendErrorCode.IDEMPOTENCY_CONFLICT)
            }
        }
        if (metadata.schemaVersion !in supportedSchemaVersions) {
            return BackendResult.Rejected(BackendErrorCode.SNAPSHOT_VERSION_UNSUPPORTED)
        }
        if (metadata.byteCount != bytes.size) {
            return BackendResult.Rejected(BackendErrorCode.INVALID_SNAPSHOT)
        }
        if (metadata.checksum != bytes.checksum) {
            return BackendResult.Rejected(BackendErrorCode.INVALID_SNAPSHOT)
        }
        return when (val stored = storage.put(StoredSnapshot(metadata, bytes), operationId)) {
            is BackendResult.Success -> validateStoredMetadata(metadata, stored.value)?.let { it }
                ?: stored.also { operations[operationId] = metadata }
            is BackendResult.Replayed -> validateStoredMetadata(metadata, stored.value)?.let { it }
                ?: stored.also { operations[operationId] = metadata }
            is BackendResult.Rejected -> stored
        }
    }

    fun download(accountId: AccountId, snapshotId: SnapshotId): BackendResult<StoredSnapshot> = when (
        val stored = storage.get(accountId, snapshotId)
    ) {
        is BackendResult.Success -> validateDownloadedSnapshot(accountId, snapshotId, stored.value)?.let { it } ?: stored
        is BackendResult.Replayed -> validateDownloadedSnapshot(accountId, snapshotId, stored.value)?.let { it } ?: stored
        is BackendResult.Rejected -> stored
    }

    private fun validateStoredMetadata(
        expected: SnapshotMetadata,
        actual: SnapshotMetadata,
    ): BackendResult.Rejected? = when {
        actual.accountId != expected.accountId -> BackendResult.Rejected(BackendErrorCode.ACCOUNT_MISMATCH)
        actual != expected -> BackendResult.Rejected(BackendErrorCode.INVALID_SNAPSHOT)
        else -> null
    }

    private fun validateDownloadedSnapshot(
        expectedAccountId: AccountId,
        expectedSnapshotId: SnapshotId,
        actual: StoredSnapshot,
    ): BackendResult.Rejected? = when {
        actual.metadata.accountId != expectedAccountId -> BackendResult.Rejected(BackendErrorCode.ACCOUNT_MISMATCH)
        actual.metadata.snapshotId != expectedSnapshotId -> BackendResult.Rejected(BackendErrorCode.INVALID_SNAPSHOT)
        else -> null
    }
}
