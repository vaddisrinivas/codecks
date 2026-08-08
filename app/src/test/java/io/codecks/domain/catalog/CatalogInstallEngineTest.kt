package io.codecks.domain.catalog

import io.codecks.data.catalog.InMemoryLocalCatalogStore
import io.codecks.domain.sshpack.SshCatalogActionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogInstallEngineTest {
    private val environment = CatalogEnvironment(CatalogPlatform.ANDROID, 10, 35)
    private val resolver = SshCatalogActionResolver { null }

    @Test
    fun duplicateAndIncompatibleEntriesFailClosed() {
        val oversized = bundle(entries = (0..256).map { routine("routine.item_$it", 1) })
        assertRejected(oversized, CatalogValidationCode.BUNDLE_TOO_LARGE)

        val duplicate = bundle(entries = listOf(routine("routine.same", 1), routine("routine.same", 1)))
        assertRejected(duplicate, CatalogValidationCode.DUPLICATE_ENTRY_ID)

        val incompatible = bundle(
            entries = listOf(routine("routine.future", 1, minimumAppVersion = 11)),
        )
        assertRejected(incompatible, CatalogValidationCode.INCOMPATIBLE_ENTRY)

        val unknownActionEngine = CatalogInstallEngine(
            InMemoryLocalCatalogStore(),
            CatalogSignatureVerifier { _, _, _ -> true },
            CatalogActionReferenceResolver { false },
            resolver,
        )
        assertRejected(bundle(), CatalogValidationCode.INVALID_ROUTINE, unknownActionEngine)
    }

    @Test
    fun externalBundlesRequireValidSignature() {
        val unsigned = bundle(source = CatalogSource.SignedPublisher(CatalogId("publisher.safe")))
        assertRejected(unsigned, CatalogValidationCode.SIGNATURE_REQUIRED)

        val signed = bundle(
            source = CatalogSource.SignedPublisher(CatalogId("publisher.safe")),
            signature = CatalogSignature.fromTransport("a".repeat(32)),
        )
        val engine = engine(verifier = CatalogSignatureVerifier { _, _, _ -> false })
        val result = engine.preview(signed, environment) as CatalogPreviewResult.Rejected
        assertEquals(CatalogValidationCode.SIGNATURE_INVALID, result.code)
    }

    @Test
    fun canonicalDigestBindsEveryEntryAndMetadataFieldBeforeSignatureVerification() {
        val source = CatalogSource.SignedPublisher(CatalogId("publisher.safe"))
        val original = bundle(
            source = source,
            signature = CatalogSignature.fromTransport("a".repeat(32)),
            entries = listOf(routine("routine.alpha", 1), routine("routine.beta", 1)),
        )
        val reorderedDigest = CatalogBundle.computePayloadDigest(
            original.id,
            original.version,
            original.source,
            original.entries.reversed(),
        )
        assertEquals(original.payloadDigest, reorderedDigest)

        var verifierCalls = 0
        val engine = engine(verifier = CatalogSignatureVerifier { payload, publisher, signature ->
            verifierCalls += 1
            val signatureBytes = signature.encoded()
            signatureBytes[0] = 0
            val payloadBytes = payload.encoded()
            payloadBytes[0] = 0
            payload.digest == original.payloadDigest &&
                publisher == CatalogId("publisher.safe") &&
                signature.encoded()[0] != 0.toByte() &&
                payload.encoded()[0] != 0.toByte()
        })
        assertTrue(engine.preview(original, environment) is CatalogPreviewResult.Ready)
        assertEquals(1, verifierCalls)

        val tamperedEntries = listOf(routine("routine.alpha", 2), routine("routine.beta", 1))
        val tampered = CatalogBundle(
            original.id,
            original.version,
            source,
            original.payloadDigest,
            CatalogSignature.fromTransport("a".repeat(32)),
            tamperedEntries,
        )
        assertRejected(tampered, CatalogValidationCode.PAYLOAD_DIGEST_MISMATCH, engine)
        assertEquals(1, verifierCalls)
    }

    @Test
    fun sameVersionWithDifferentDigestIsRejected() {
        val store = InMemoryLocalCatalogStore()
        val engine = engine(store)
        val original = engine.preview(bundle(), environment) as CatalogPreviewResult.Ready
        assertTrue(engine.install(original.preview.previewId) is CatalogInstallResult.Installed)
        val changed = CatalogBundle(
            id = CatalogId("catalog.fixture"),
            version = CatalogVersion(1, 0, 0),
            source = CatalogSource.Bundled,
            payloadDigest = CatalogBundle.computePayloadDigest(
                id = CatalogId("catalog.fixture"),
                version = CatalogVersion(1, 0, 0),
                source = CatalogSource.Bundled,
                entries = listOf(routine("routine.fixture", 2)),
            ),
            signature = null,
            entries = listOf(routine("routine.fixture", 2)),
        )
        assertRejected(changed, CatalogValidationCode.SAME_VERSION_DIFFERENT_PAYLOAD, engine)
    }

    @Test
    fun previewReportsAdditionsDedupeAndConflictDeterministically() {
        val store = InMemoryLocalCatalogStore(
            LocalCatalogSnapshot(
                2,
                mapOf(
                    CatalogId("routine.keep") to routine("routine.keep", 1),
                    CatalogId("routine.replace") to routine("routine.replace", 1),
                ),
                emptyMap(),
            ),
        )
        val engine = engine(store)
        val preview = engine.preview(
            bundle(
                entries = listOf(
                    routine("routine.z_add", 1),
                    routine("routine.replace", 2),
                    routine("routine.keep", 1),
                    routine("routine.a_add", 1),
                ),
            ),
            environment,
        ) as CatalogPreviewResult.Ready

        assertEquals(listOf(CatalogId("routine.a_add"), CatalogId("routine.z_add")), preview.preview.additions)
        assertEquals(listOf(CatalogId("routine.keep")), preview.preview.deduplicated)
        assertEquals(listOf(CatalogId("routine.replace")), preview.preview.conflicts.map { it.id })
        assertEquals(
            CatalogValidationCode.CONFLICT_UNRESOLVED,
            (engine.install(preview.preview.previewId) as CatalogInstallResult.Rejected).code,
        )
    }

    @Test
    fun staleChangedAndTypeIncompatibleEntryIdsFailClosed() {
        val installed = routine("routine.same", 2)
        val store = InMemoryLocalCatalogStore(
            LocalCatalogSnapshot(2, mapOf(installed.id to installed), emptyMap()),
        )
        val engine = engine(store)

        assertRejected(
            bundle(entries = listOf(routine("routine.same", 1))),
            CatalogValidationCode.STALE_ENTRY,
            engine,
        )
        assertRejected(
            bundle(entries = listOf(routine("routine.same", 2, summary = "Changed without revision"))),
            CatalogValidationCode.SAME_REVISION_DIFFERENT_ENTRY,
            engine,
        )
        assertRejected(
            bundle(entries = listOf(theme("routine.same", 3))),
            CatalogValidationCode.INCOMPATIBLE_ENTRY_ID,
            engine,
        )
    }

    @Test
    fun installRollbackIsLocalSafeAndExactlyOnce() {
        val original = routine("routine.replace", 1)
        val store = InMemoryLocalCatalogStore(LocalCatalogSnapshot(0, mapOf(original.id to original), emptyMap()))
        val engine = engine(store)
        val preview = engine.preview(bundle(entries = listOf(routine("routine.replace", 2))), environment)
            as CatalogPreviewResult.Ready
        val installed = engine.install(
            preview.preview.previewId,
            mapOf(CatalogId("routine.replace") to CatalogConflictResolution.REPLACE),
        ) as CatalogInstallResult.Installed

        assertEquals(2, store.snapshot().entries.getValue(original.id).contentRevision)
        val rollback = engine.rollback(installed.receipt.rollbackId) as CatalogRollbackResult.RolledBack
        assertEquals(1, store.snapshot().entries.getValue(original.id).contentRevision)
        assertEquals(store.snapshot().revision, rollback.receipt.restoredRevision)
        assertEquals(
            CatalogValidationCode.ROLLBACK_ALREADY_USED,
            (engine.rollback(installed.receipt.rollbackId) as CatalogRollbackResult.Rejected).code,
        )
    }

    @Test
    fun stalePreviewRollbackAndBundleAreRejected() {
        val store = InMemoryLocalCatalogStore()
        val engine = engine(store)
        val first = engine.preview(bundle(version = CatalogVersion(2, 0, 0)), environment) as CatalogPreviewResult.Ready
        val installed = engine.install(first.preview.previewId) as CatalogInstallResult.Installed

        val another = engine.preview(
            bundle(id = "catalog.other", entries = listOf(routine("routine.other", 1))),
            environment,
        ) as CatalogPreviewResult.Ready
        engine.install(another.preview.previewId)
        assertEquals(
            CatalogValidationCode.ROLLBACK_STALE,
            (engine.rollback(installed.receipt.rollbackId) as CatalogRollbackResult.Rejected).code,
        )
        assertRejected(bundle(version = CatalogVersion(1, 0, 0)), CatalogValidationCode.STALE_BUNDLE, engine)
    }

    @Test
    fun previewTokenIsBoundToStoreRevisionAndSingleUse() {
        val store = InMemoryLocalCatalogStore()
        val engine = engine(store)
        val first = engine.preview(bundle(), environment) as CatalogPreviewResult.Ready
        store.replace(0, LocalCatalogSnapshot(1, emptyMap(), emptyMap()))
        assertEquals(
            CatalogValidationCode.PREVIEW_STALE,
            (engine.install(first.preview.previewId) as CatalogInstallResult.Rejected).code,
        )
        assertEquals(
            CatalogValidationCode.PREVIEW_STALE,
            (engine.install(first.preview.previewId) as CatalogInstallResult.Rejected).code,
        )
    }

    @Test
    fun bundlePreviewAndStoreSnapshotsCannotBeMutatedAfterValidation() {
        val inputEntries = mutableListOf(routine("routine.immutable", 1))
        val bundleId = CatalogId("catalog.immutable")
        val version = CatalogVersion(1, 0, 0)
        val source = CatalogSource.Bundled
        val immutableBundle = CatalogBundle(
            bundleId,
            version,
            source,
            CatalogBundle.computePayloadDigest(bundleId, version, source, inputEntries),
            null,
            inputEntries,
        )
        val store = InMemoryLocalCatalogStore()
        val engine = engine(store)
        val preview = engine.preview(immutableBundle, environment) as CatalogPreviewResult.Ready

        inputEntries.clear()
        assertEquals(1, immutableBundle.entries.size)
        assertTrue(runCatching { (immutableBundle.entries as MutableList<ProductCatalogEntry>).clear() }.isFailure)
        val routine = immutableBundle.entries.single() as ProductCatalogEntry.Routine
        assertTrue(runCatching { (routine.actionIds as MutableList<CatalogId>).clear() }.isFailure)
        assertTrue(runCatching { (preview.preview.additions as MutableList<CatalogId>).clear() }.isFailure)

        assertTrue(engine.install(preview.preview.previewId) is CatalogInstallResult.Installed)
        assertTrue(
            runCatching {
                (store.snapshot().entries as MutableMap<CatalogId, ProductCatalogEntry>).clear()
            }.isFailure,
        )
        assertTrue(CatalogId("routine.immutable") in store.snapshot().entries)
    }

    private fun assertRejected(
        bundle: CatalogBundle,
        code: CatalogValidationCode,
        engine: CatalogInstallEngine = engine(),
    ) {
        assertEquals(code, (engine.preview(bundle, environment) as CatalogPreviewResult.Rejected).code)
    }

    private fun engine(
        store: InMemoryLocalCatalogStore = InMemoryLocalCatalogStore(),
        verifier: CatalogSignatureVerifier = CatalogSignatureVerifier { _, _, _ -> true },
    ) = CatalogInstallEngine(store, verifier, CatalogActionReferenceResolver { true }, resolver)

    private fun bundle(
        id: String = "catalog.fixture",
        version: CatalogVersion = CatalogVersion(1, 0, 0),
        source: CatalogSource = CatalogSource.Bundled,
        signature: CatalogSignature? = null,
        entries: List<ProductCatalogEntry> = listOf(routine("routine.fixture", 1)),
    ): CatalogBundle {
        val catalogId = CatalogId(id)
        return CatalogBundle(
            catalogId,
            version,
            source,
            CatalogBundle.computePayloadDigest(catalogId, version, source, entries),
            signature,
            entries,
        )
    }

    private fun routine(
        id: String,
        revision: Int,
        minimumAppVersion: Int = 1,
        summary: String = "Safe local routine",
    ) = ProductCatalogEntry.Routine(
        id = CatalogId(id),
        title = id,
        summary = summary,
        contentRevision = revision,
        accessClass = CatalogAccessClass.FREE,
        compatibility = CatalogCompatibility(minimumAppVersionCode = minimumAppVersion, minimumOsApiLevel = 28),
        accessibility = CatalogAccessibility("Safe routine", 48, 4.5, true, true),
        actionIds = listOf(CatalogId("finder")),
    )

    private fun theme(id: String, revision: Int) = ProductCatalogEntry.Theme(
        id = CatalogId(id),
        title = id,
        summary = "Accessible local theme",
        contentRevision = revision,
        accessClass = CatalogAccessClass.FREE,
        compatibility = CatalogCompatibility(minimumAppVersionCode = 1, minimumOsApiLevel = 28),
        accessibility = CatalogAccessibility("Safe theme", 48, 4.5, true, true),
        presetId = CatalogId("preset.fixture"),
        previewColors = listOf("#000000", "#FFFFFF"),
        motionIntensity = ThemeMotionIntensity.NONE,
        contrastClass = ThemeContrastClass.HIGH_CONTRAST,
    )
}
