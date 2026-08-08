package io.codecks.domain.catalog

import io.codecks.domain.sshpack.SshActionPackValidator
import io.codecks.domain.sshpack.SshCatalogActionResolver
import io.codecks.domain.sshpack.SshPackValidationResult
import java.util.Collections
import java.util.TreeMap

data class InstalledBundleMetadata(
    val version: CatalogVersion,
    val payloadDigest: CatalogPayloadDigest,
)

class LocalCatalogSnapshot(
    val revision: Long,
    entries: Map<CatalogId, ProductCatalogEntry>,
    bundles: Map<CatalogId, InstalledBundleMetadata>,
) {
    val entries: Map<CatalogId, ProductCatalogEntry> = Collections.unmodifiableMap(TreeMap(entries))
    val bundles: Map<CatalogId, InstalledBundleMetadata> = Collections.unmodifiableMap(TreeMap(bundles))

    init {
        require(revision >= 0L)
    }
}

interface LocalCatalogStore {
    fun snapshot(): LocalCatalogSnapshot
    fun replace(expectedRevision: Long, replacement: LocalCatalogSnapshot): Boolean
}

enum class CatalogValidationCode {
    BUNDLE_TOO_LARGE,
    DUPLICATE_ENTRY_ID,
    PAYLOAD_DIGEST_MISMATCH,
    SIGNATURE_REQUIRED,
    SIGNATURE_INVALID,
    STALE_BUNDLE,
    SAME_VERSION_DIFFERENT_PAYLOAD,
    INCOMPATIBLE_ENTRY,
    INCOMPATIBLE_ENTRY_ID,
    STALE_ENTRY,
    SAME_REVISION_DIFFERENT_ENTRY,
    INVALID_ROUTINE,
    INVALID_SSH_PACK,
    PREVIEW_STALE,
    CONFLICT_UNRESOLVED,
    ROLLBACK_STALE,
    ROLLBACK_ALREADY_USED,
}

data class CatalogConflict(
    val id: CatalogId,
    val installedRevision: Int,
    val incomingRevision: Int,
)

@JvmInline
value class CatalogPreviewId internal constructor(val value: Long)

@JvmInline
value class CatalogRollbackId internal constructor(val value: Long)

class CatalogInstallPreview(
    val previewId: CatalogPreviewId,
    val bundleId: CatalogId,
    val bundleVersion: CatalogVersion,
    val storeRevision: Long,
    additions: List<CatalogId>,
    deduplicated: List<CatalogId>,
    conflicts: List<CatalogConflict>,
    val requiresSshConfirmation: Boolean,
) {
    val additions: List<CatalogId> = Collections.unmodifiableList(additions.toList())
    val deduplicated: List<CatalogId> = Collections.unmodifiableList(deduplicated.toList())
    val conflicts: List<CatalogConflict> = Collections.unmodifiableList(conflicts.toList())

    init {
        require(this.additions == this.additions.sorted())
        require(this.deduplicated == this.deduplicated.sorted())
        require(this.conflicts == this.conflicts.sortedBy(CatalogConflict::id))
    }
}

sealed interface CatalogPreviewResult {
    data class Ready(val preview: CatalogInstallPreview) : CatalogPreviewResult
    data class Rejected(val code: CatalogValidationCode, val entryId: CatalogId? = null) : CatalogPreviewResult
}

enum class CatalogConflictResolution {
    KEEP_EXISTING,
    REPLACE,
}

data class CatalogInstallReceipt(
    val bundleId: CatalogId,
    val bundleVersion: CatalogVersion,
    val beforeRevision: Long,
    val afterRevision: Long,
    val installed: List<CatalogId>,
    val keptExisting: List<CatalogId>,
    val deduplicated: List<CatalogId>,
    val rollbackId: CatalogRollbackId,
)

sealed interface CatalogInstallResult {
    data class Installed(val receipt: CatalogInstallReceipt) : CatalogInstallResult
    data class Rejected(val code: CatalogValidationCode) : CatalogInstallResult
}

data class CatalogRollbackReceipt(
    val rollbackId: CatalogRollbackId,
    val restoredRevision: Long,
    val restoredEntryIds: List<CatalogId>,
)

sealed interface CatalogRollbackResult {
    data class RolledBack(val receipt: CatalogRollbackReceipt) : CatalogRollbackResult
    data class Rejected(val code: CatalogValidationCode) : CatalogRollbackResult
}

class CatalogInstallEngine(
    private val store: LocalCatalogStore,
    private val signatureVerifier: CatalogSignatureVerifier,
    private val actionReferenceResolver: CatalogActionReferenceResolver,
    private val sshResolver: SshCatalogActionResolver,
) {
    private data class PendingPreview(
        val bundle: CatalogBundle,
        val preview: CatalogInstallPreview,
    )

    private data class RollbackState(
        val before: LocalCatalogSnapshot,
        val installedRevision: Long,
        var used: Boolean = false,
    )

    private val pending = mutableMapOf<CatalogPreviewId, PendingPreview>()
    private val rollbacks = mutableMapOf<CatalogRollbackId, RollbackState>()
    private var nextToken = 1L

    @Synchronized
    fun preview(bundle: CatalogBundle, environment: CatalogEnvironment): CatalogPreviewResult {
        if (bundle.entries.size > MAX_BUNDLE_ENTRIES) {
            return CatalogPreviewResult.Rejected(CatalogValidationCode.BUNDLE_TOO_LARGE)
        }
        val duplicateId = bundle.entries.groupingBy { it.id }.eachCount().entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicateId != null) return CatalogPreviewResult.Rejected(
            CatalogValidationCode.DUPLICATE_ENTRY_ID,
            duplicateId,
        )
        val signedPayload = bundle.signedPayload()
        if (signedPayload.digest != bundle.payloadDigest) {
            return CatalogPreviewResult.Rejected(CatalogValidationCode.PAYLOAD_DIGEST_MISMATCH)
        }
        when (val source = bundle.source) {
            CatalogSource.Bundled -> Unit
            is CatalogSource.SignedPublisher -> {
                val signature = bundle.signature
                    ?: return CatalogPreviewResult.Rejected(CatalogValidationCode.SIGNATURE_REQUIRED)
                if (!signatureVerifier.verify(signedPayload, source.publisherId, signature)) {
                    return CatalogPreviewResult.Rejected(CatalogValidationCode.SIGNATURE_INVALID)
                }
            }
        }
        bundle.entries.firstOrNull { !it.compatibility.supports(environment) }?.let {
            return CatalogPreviewResult.Rejected(CatalogValidationCode.INCOMPATIBLE_ENTRY, it.id)
        }
        bundle.entries.filterIsInstance<ProductCatalogEntry.Routine>().firstOrNull { routine ->
            routine.actionIds.any { !actionReferenceResolver.contains(it) }
        }?.let {
            return CatalogPreviewResult.Rejected(CatalogValidationCode.INVALID_ROUTINE, it.id)
        }

        var requiresSshConfirmation = false
        bundle.entries.filterIsInstance<ProductCatalogEntry.SshPack>().forEach { entry ->
            when (val validation = SshActionPackValidator.validate(entry.pack, sshResolver)) {
                is SshPackValidationResult.Invalid -> return CatalogPreviewResult.Rejected(
                    CatalogValidationCode.INVALID_SSH_PACK,
                    entry.id,
                )
                is SshPackValidationResult.Valid -> requiresSshConfirmation =
                    requiresSshConfirmation || validation.requiresConfirmation
            }
        }

        val snapshot = store.snapshot()
        snapshot.bundles[bundle.id]?.let { installed ->
            if (bundle.version < installed.version) {
                return CatalogPreviewResult.Rejected(CatalogValidationCode.STALE_BUNDLE)
            }
            if (bundle.version == installed.version && bundle.payloadDigest != installed.payloadDigest) {
                return CatalogPreviewResult.Rejected(CatalogValidationCode.SAME_VERSION_DIFFERENT_PAYLOAD)
            }
        }
        val additions = mutableListOf<CatalogId>()
        val deduplicated = mutableListOf<CatalogId>()
        val conflicts = mutableListOf<CatalogConflict>()
        bundle.entries.forEach { incoming ->
            val existing = snapshot.entries[incoming.id]
            when {
                existing == null -> additions += incoming.id
                existing::class != incoming::class -> return CatalogPreviewResult.Rejected(
                    CatalogValidationCode.INCOMPATIBLE_ENTRY_ID,
                    incoming.id,
                )
                incoming.contentRevision < existing.contentRevision -> return CatalogPreviewResult.Rejected(
                    CatalogValidationCode.STALE_ENTRY,
                    incoming.id,
                )
                incoming.contentRevision == existing.contentRevision -> {
                    if (incoming.canonicalDigest() != existing.canonicalDigest()) {
                        return CatalogPreviewResult.Rejected(
                            CatalogValidationCode.SAME_REVISION_DIFFERENT_ENTRY,
                            incoming.id,
                        )
                    }
                    deduplicated += incoming.id
                }
                else -> conflicts += CatalogConflict(
                    incoming.id,
                    existing.contentRevision,
                    incoming.contentRevision,
                )
            }
        }
        val previewId = CatalogPreviewId(nextToken++)
        val preview = CatalogInstallPreview(
            previewId = previewId,
            bundleId = bundle.id,
            bundleVersion = bundle.version,
            storeRevision = snapshot.revision,
            additions = additions.sorted(),
            deduplicated = deduplicated.sorted(),
            conflicts = conflicts.sortedBy(CatalogConflict::id),
            requiresSshConfirmation = requiresSshConfirmation,
        )
        pending[previewId] = PendingPreview(bundle, preview)
        return CatalogPreviewResult.Ready(preview)
    }

    @Synchronized
    fun install(
        previewId: CatalogPreviewId,
        resolutions: Map<CatalogId, CatalogConflictResolution> = emptyMap(),
    ): CatalogInstallResult {
        val pendingPreview = pending.remove(previewId)
            ?: return CatalogInstallResult.Rejected(CatalogValidationCode.PREVIEW_STALE)
        val current = store.snapshot()
        if (current.revision != pendingPreview.preview.storeRevision) {
            return CatalogInstallResult.Rejected(CatalogValidationCode.PREVIEW_STALE)
        }
        val conflictIds = pendingPreview.preview.conflicts.map(CatalogConflict::id).toSet()
        if (!resolutions.keys.all { it in conflictIds } || !conflictIds.all(resolutions::containsKey)) {
            return CatalogInstallResult.Rejected(CatalogValidationCode.CONFLICT_UNRESOLVED)
        }
        val nextEntries = current.entries.toMutableMap()
        val installed = mutableListOf<CatalogId>()
        val kept = mutableListOf<CatalogId>()
        pendingPreview.bundle.entries.forEach { entry ->
            when {
                entry.id in pendingPreview.preview.deduplicated -> Unit
                resolutions[entry.id] == CatalogConflictResolution.KEEP_EXISTING -> kept += entry.id
                else -> {
                    nextEntries[entry.id] = entry
                    installed += entry.id
                }
            }
        }
        val nextBundles = current.bundles + (
            pendingPreview.bundle.id to InstalledBundleMetadata(
                pendingPreview.bundle.version,
                pendingPreview.bundle.payloadDigest,
            )
        )
        val replacement = LocalCatalogSnapshot(current.revision + 1L, nextEntries, nextBundles)
        if (!store.replace(current.revision, replacement)) {
            return CatalogInstallResult.Rejected(CatalogValidationCode.PREVIEW_STALE)
        }
        val rollbackId = CatalogRollbackId(nextToken++)
        rollbacks[rollbackId] = RollbackState(current, replacement.revision)
        return CatalogInstallResult.Installed(
            CatalogInstallReceipt(
                bundleId = pendingPreview.bundle.id,
                bundleVersion = pendingPreview.bundle.version,
                beforeRevision = current.revision,
                afterRevision = replacement.revision,
                installed = installed.sorted(),
                keptExisting = kept.sorted(),
                deduplicated = pendingPreview.preview.deduplicated,
                rollbackId = rollbackId,
            ),
        )
    }

    @Synchronized
    fun rollback(rollbackId: CatalogRollbackId): CatalogRollbackResult {
        val state = rollbacks[rollbackId]
            ?: return CatalogRollbackResult.Rejected(CatalogValidationCode.ROLLBACK_ALREADY_USED)
        if (state.used) return CatalogRollbackResult.Rejected(CatalogValidationCode.ROLLBACK_ALREADY_USED)
        val current = store.snapshot()
        if (current.revision != state.installedRevision) {
            return CatalogRollbackResult.Rejected(CatalogValidationCode.ROLLBACK_STALE)
        }
        val restored = LocalCatalogSnapshot(
            revision = current.revision + 1L,
            entries = state.before.entries,
            bundles = state.before.bundles,
        )
        if (!store.replace(current.revision, restored)) {
            return CatalogRollbackResult.Rejected(CatalogValidationCode.ROLLBACK_STALE)
        }
        state.used = true
        return CatalogRollbackResult.RolledBack(
            CatalogRollbackReceipt(
                rollbackId = rollbackId,
                restoredRevision = restored.revision,
                restoredEntryIds = restored.entries.keys.sorted(),
            ),
        )
    }

    private companion object {
        const val MAX_BUNDLE_ENTRIES = 256
    }
}
