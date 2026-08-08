package io.codecks.commercial.sync

import io.codecks.domain.commercial.CommercialDecision
import io.codecks.domain.commercial.CommercialExecutionPolicy
import io.codecks.domain.commercial.CommercialSnapshotService
import io.codecks.domain.commercial.CommercialSurface
import io.codecks.domain.commercial.SnapshotDownloadRequest
import io.codecks.domain.commercial.SnapshotRestorePreviewRequest
import io.codecks.domain.commercial.SnapshotRestorePreviewResult
import io.codecks.domain.commercial.SnapshotTransferResult
import io.codecks.domain.commercial.SnapshotUploadRequest

interface SnapshotCloudTransport

interface PortableSnapshotStore

/** Production-dark sync seam. Factories cannot run while the compiled root policy denies. */
class ProductionPlaySnapshotAdapter(
    private val cloudFactory: () -> SnapshotCloudTransport,
    private val snapshotStoreFactory: () -> PortableSnapshotStore,
) : CommercialSnapshotService {
    private val policy = CommercialExecutionPolicy.PRODUCTION_DARK

    override suspend fun upload(request: SnapshotUploadRequest): SnapshotTransferResult =
        SnapshotTransferResult.Denied(denialReason())

    override suspend fun download(request: SnapshotDownloadRequest): SnapshotTransferResult =
        SnapshotTransferResult.Denied(denialReason())

    override suspend fun previewRestore(request: SnapshotRestorePreviewRequest): SnapshotRestorePreviewResult =
        SnapshotRestorePreviewResult.Denied(denialReason())

    private fun denialReason() =
        (policy.decide(CommercialSurface.CLOUD_SYNC) as CommercialDecision.Denied).reason

    @Suppress("unused")
    private fun unreachableFactories() = listOf(cloudFactory, snapshotStoreFactory)
}
