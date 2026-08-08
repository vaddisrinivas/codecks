package io.codecks.backend.contracts

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SnapshotServiceTest {
    @Test
    fun `canonical bytes reject empty and oversized payloads`() {
        assertEquals(
            BackendErrorCode.INVALID_SNAPSHOT,
            rejectedCode(CanonicalSafeSnapshotBytes.fromCanonicalSerializer(byteArrayOf(), hash('a'))),
        )
        assertEquals(
            BackendErrorCode.SNAPSHOT_TOO_LARGE,
            rejectedCode(
                CanonicalSafeSnapshotBytes.fromCanonicalSerializer(
                    ByteArray(MAX_SAFE_SNAPSHOT_BYTES + 1),
                    hash('a'),
                ),
            ),
        )
    }

    @Test
    fun `upload rejects unknown schema and mismatched byte count`() {
        val storage = MemorySnapshotStorage()
        val service = SnapshotService(setOf(1), storage)
        val bytes = safeBytes("{}".encodeToByteArray())
        assertEquals(
            BackendErrorCode.SNAPSHOT_VERSION_UNSUPPORTED,
            rejectedCode(service.upload(op("snapop01"), metadata(version = 2, byteCount = 2), bytes)),
        )
        assertEquals(
            BackendErrorCode.INVALID_SNAPSHOT,
            rejectedCode(service.upload(op("snapop02"), metadata(version = 1, byteCount = 1), bytes)),
        )
        assertEquals(
            BackendErrorCode.INVALID_SNAPSHOT,
            rejectedCode(
                service.upload(
                    op("snapop09"),
                    metadata(version = 1, byteCount = 2).copy(checksum = hash('b')),
                    bytes,
                ),
            ),
        )
    }

    @Test
    fun `upload is idempotent and canonical bytes are defensively copied`() {
        val source = "{}".encodeToByteArray()
        val bytes = safeBytes(source)
        source[0] = 'X'.code.toByte()
        val storage = MemorySnapshotStorage()
        val service = SnapshotService(setOf(1), storage)
        val metadata = metadata(version = 1, byteCount = 2)

        assertIs<BackendResult.Success<SnapshotMetadata>>(service.upload(op("snapop03"), metadata, bytes))
        assertIs<BackendResult.Replayed<SnapshotMetadata>>(service.upload(op("snapop03"), metadata, bytes))
        val stored = assertIs<BackendResult.Success<StoredSnapshot>>(
            service.download(metadata.accountId, metadata.snapshotId),
        ).value
        assertContentEquals("{}".encodeToByteArray(), stored.bytes.copyForStorage())
        assertEquals(1, storage.putCount)

        assertEquals(
            BackendErrorCode.IDEMPOTENCY_CONFLICT,
            rejectedCode(
                service.upload(op("snapop03"), metadata.copy(revision = Revision(2L)), bytes),
            ),
        )
    }

    @Test
    fun `storage cannot cross account or snapshot identity boundaries`() {
        val requestedAccount = AccountId.fromVerifiedGoogleSubject(subject()) { "account-00000001" }
        val otherAccount = AccountId.fromVerifiedGoogleSubject(subject()) { "account-00000002" }
        val requestedSnapshot = SnapshotId.trusted("snapshot-0000001")
        val bytes = safeBytes("{}".encodeToByteArray())
        val wrong = StoredSnapshot(
            metadata = metadata(version = 1, byteCount = 2).copy(
                accountId = otherAccount,
                snapshotId = requestedSnapshot,
            ),
            bytes = bytes,
        )
        val storage = object : SnapshotStoragePort {
            override fun put(snapshot: StoredSnapshot, operationId: OperationId): BackendResult<SnapshotMetadata> =
                BackendResult.Success(snapshot.metadata.copy(accountId = otherAccount))

            override fun get(accountId: AccountId, snapshotId: SnapshotId): BackendResult<StoredSnapshot> =
                BackendResult.Success(wrong)

            override fun deleteAll(accountId: AccountId, operationId: OperationId): BackendResult<Unit> =
                BackendResult.Success(Unit)
        }
        val service = SnapshotService(setOf(1), storage)

        assertEquals(
            BackendErrorCode.ACCOUNT_MISMATCH,
            rejectedCode(
                service.upload(
                    op("snapop10"),
                    metadata(version = 1, byteCount = 2).copy(accountId = requestedAccount),
                    bytes,
                ),
            ),
        )
        assertEquals(
            BackendErrorCode.ACCOUNT_MISMATCH,
            rejectedCode(service.download(requestedAccount, requestedSnapshot)),
        )
    }
}

private class MemorySnapshotStorage : SnapshotStoragePort {
    private val snapshots = mutableMapOf<Pair<AccountId, SnapshotId>, StoredSnapshot>()
    var putCount: Int = 0

    override fun put(snapshot: StoredSnapshot, operationId: OperationId): BackendResult<SnapshotMetadata> {
        putCount += 1
        snapshots[snapshot.metadata.accountId to snapshot.metadata.snapshotId] = snapshot
        return BackendResult.Success(snapshot.metadata)
    }

    override fun get(accountId: AccountId, snapshotId: SnapshotId): BackendResult<StoredSnapshot> =
        snapshots[accountId to snapshotId]
            ?.let { BackendResult.Success(it) }
            ?: BackendResult.Rejected(BackendErrorCode.INVALID_SNAPSHOT)

    override fun deleteAll(accountId: AccountId, operationId: OperationId): BackendResult<Unit> {
        snapshots.keys.removeAll { it.first == accountId }
        return BackendResult.Success(Unit)
    }
}

private fun safeBytes(value: ByteArray): CanonicalSafeSnapshotBytes =
    assertIs<BackendResult.Success<CanonicalSafeSnapshotBytes>>(
        CanonicalSafeSnapshotBytes.fromCanonicalSerializer(value, hash('a')),
    ).value

private fun metadata(version: Int, byteCount: Int): SnapshotMetadata {
    val account = AccountId.fromVerifiedGoogleSubject(subject()) { "account-00000001" }
    return SnapshotMetadata(
        snapshotId = SnapshotId.trusted("snapshot-0000001"),
        accountId = account,
        schemaVersion = version,
        checksum = hash('a'),
        byteCount = byteCount,
        revision = Revision(1L),
        createdAtEpochMillis = 10L,
    )
}
