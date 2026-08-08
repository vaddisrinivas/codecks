package io.codecks.data.catalog

import io.codecks.domain.catalog.CatalogAccessClass
import io.codecks.domain.catalog.CatalogEnvironment
import io.codecks.domain.catalog.CatalogInstallEngine
import io.codecks.domain.catalog.CatalogInstallResult
import io.codecks.domain.catalog.CatalogPlatform
import io.codecks.domain.catalog.CatalogPreviewResult
import io.codecks.domain.catalog.ProductCatalogEntry
import io.codecks.domain.sshpack.SshCatalogActionContract
import io.codecks.domain.sshpack.SshCatalogActionResolver
import io.codecks.domain.sshpack.SshPreflightRequirement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledProductCatalogTest {
    @Test
    fun starterCatalogIsDeterministicUniqueAndUsefulOffline() {
        val first = BundledProductCatalog.explore()
        val second = BundledProductCatalog.explore()
        val ids = first.entries.map { it.id }

        assertEquals(ids.sorted(), ids)
        assertEquals(ids, second.entries.map { it.id })
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(first.entries.any { it is ProductCatalogEntry.Routine })
        assertTrue(first.entries.any { it is ProductCatalogEntry.Theme })
        assertTrue(first.entries.any { it is ProductCatalogEntry.SshPack })
    }

    @Test
    fun everyEntryCarriesAccessibleDiscoveryMetadata() {
        BundledProductCatalog.explore().entries.forEach { entry ->
            assertTrue(entry.accessibility.talkBackLabel.isNotBlank())
            assertTrue(entry.accessibility.minimumTouchTargetDp >= 48)
            assertTrue(entry.accessibility.minimumContrastRatio >= 4.5)
            assertTrue(entry.accessibility.colorIndependentMeaning)
            assertTrue(entry.accessibility.supportsReducedMotion)
        }
    }

    @Test
    fun premiumIsDisplayMetadataAndNeverBlocksLocalInstall() {
        val premium = BundledProductCatalog.explore().entries
            .filter { it.accessClass == CatalogAccessClass.PREMIUM_DISPLAY_ONLY }
        assertTrue(premium.isNotEmpty())
        assertTrue(premium.all { it.accessClass.permitsLocalUse })

        val store = InMemoryLocalCatalogStore()
        val resolver = SshCatalogActionResolver { id ->
            SshCatalogActionContract(
                catalogActionId = id,
                commandRevision = "a".repeat(64),
                preflight = setOf(
                    SshPreflightRequirement.SSH_CONNECTION,
                    SshPreflightRequirement.PINNED_HOST_IDENTITY,
                    SshPreflightRequirement.COMMAND_SAFETY,
                ),
                requiresConfirmation = false,
            )
        }
        val engine = CatalogInstallEngine(
            store,
            signatureVerifier = errorVerifier(),
            actionReferenceResolver = { true },
            sshResolver = resolver,
        )
        val preview = engine.preview(
            BundledProductCatalog.bundledBundle(),
            CatalogEnvironment(CatalogPlatform.ANDROID, appVersionCode = 1, osApiLevel = 35),
        ) as CatalogPreviewResult.Ready

        assertTrue(engine.install(preview.preview.previewId) is CatalogInstallResult.Installed)
        assertTrue(premium.all { it.id in store.snapshot().entries })
    }

    @Test
    fun reservedDiscoverySlotCannotRequestAdsOrShiftContent() {
        val slots = BundledProductCatalog.explore().reservedSlots
        val organicCount = BundledProductCatalog.explore().entries.size
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.afterOrganicItems >= 6 && !it.shiftsContentWhenFilled })
        assertTrue(slots.all { it.afterOrganicItems <= organicCount })

        val ownedSource = listOf(
            File("src/main/java/io/codecks/domain/catalog"),
            File("src/main/java/io/codecks/data/catalog"),
        ).flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .joinToString("\n") { it.readText() }
        listOf("AdRequest", "AdRequester", "loadAd(", "MobileAds", "AdView").forEach {
            assertFalse(it, ownedSource.contains(it))
        }
    }

    private fun errorVerifier() = io.codecks.domain.catalog.CatalogSignatureVerifier { _, _, _ ->
        error("bundled offline catalog must not invoke signature/network verification")
    }
}
