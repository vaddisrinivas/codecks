package io.codecks.data.catalog

import io.codecks.domain.catalog.CatalogAccessibility
import io.codecks.domain.catalog.CatalogAccessClass
import io.codecks.domain.catalog.CatalogBundle
import io.codecks.domain.catalog.CatalogCompatibility
import io.codecks.domain.catalog.CatalogDiscovery
import io.codecks.domain.catalog.CatalogId
import io.codecks.domain.catalog.CatalogSource
import io.codecks.domain.catalog.CatalogVersion
import io.codecks.domain.catalog.ProductCatalogEntry
import io.codecks.domain.catalog.ProductCatalogRepository
import io.codecks.domain.catalog.ReservedDiscoverySlot
import io.codecks.domain.catalog.ThemeContrastClass
import io.codecks.domain.catalog.ThemeMotionIntensity
import io.codecks.domain.sshpack.SshActionPack
import io.codecks.domain.sshpack.TypedSshAction

object BundledProductCatalog : ProductCatalogRepository {
    private val compatibility = CatalogCompatibility(
        minimumAppVersionCode = 1,
        minimumOsApiLevel = 28,
    )
    private val accessibility = CatalogAccessibility(
        talkBackLabel = "Catalog item",
        minimumTouchTargetDp = 48,
        minimumContrastRatio = 4.5,
        supportsReducedMotion = true,
        colorIndependentMeaning = true,
    )

    private val entries: List<ProductCatalogEntry> = listOf(
        ProductCatalogEntry.Routine(
            id = CatalogId("routine.deep_focus"),
            title = "Deep focus",
            summary = "Open coding tools, mute distractions, and keep the Mac awake.",
            contentRevision = 1,
            accessClass = CatalogAccessClass.FREE,
            compatibility = compatibility,
            accessibility = accessibility.copy(talkBackLabel = "Deep focus routine"),
            actionIds = listOf(CatalogId("coding_start"), CatalogId("mute"), CatalogId("focus_1h")),
        ),
        ProductCatalogEntry.Routine(
            id = CatalogId("routine.presentation_ready"),
            title = "Presentation ready",
            summary = "Prepare display controls and fast access to screenshots.",
            contentRevision = 1,
            accessClass = CatalogAccessClass.PREMIUM_DISPLAY_ONLY,
            compatibility = compatibility,
            accessibility = accessibility.copy(talkBackLabel = "Presentation ready routine"),
            actionIds = listOf(CatalogId("screenshot"), CatalogId("mute")),
        ),
        ProductCatalogEntry.Routine(
            id = CatalogId("routine.quick_launch"),
            title = "Quick launch",
            summary = "Open Finder, Terminal, and GitHub for a fresh work session.",
            contentRevision = 1,
            accessClass = CatalogAccessClass.FREE,
            compatibility = compatibility,
            accessibility = accessibility.copy(talkBackLabel = "Quick launch routine"),
            actionIds = listOf(CatalogId("finder"), CatalogId("terminal"), CatalogId("github")),
        ),
        ProductCatalogEntry.Theme(
            id = CatalogId("theme.codecks_green"),
            title = "Codecks Green",
            summary = "OLED black surfaces with crisp Android green controls.",
            contentRevision = 1,
            accessClass = CatalogAccessClass.FREE,
            compatibility = compatibility,
            accessibility = accessibility.copy(talkBackLabel = "Codecks Green theme"),
            presetId = CatalogId("preset.codecks_green"),
            previewColors = listOf("#000000", "#3DDC84", "#F3F7F4"),
            motionIntensity = ThemeMotionIntensity.REDUCED,
            contrastClass = ThemeContrastClass.HIGH_CONTRAST,
        ),
        ProductCatalogEntry.Theme(
            id = CatalogId("theme.aurora_glass"),
            title = "Aurora Glass",
            summary = "Cyan and violet accents with a reduced-motion fallback.",
            contentRevision = 1,
            accessClass = CatalogAccessClass.PREMIUM_DISPLAY_ONLY,
            compatibility = compatibility,
            accessibility = accessibility.copy(talkBackLabel = "Aurora Glass theme"),
            presetId = CatalogId("preset.aurora_glass"),
            previewColors = listOf("#071019", "#73D7F4", "#D1BCFF"),
            motionIntensity = ThemeMotionIntensity.FULL,
            contrastClass = ThemeContrastClass.STANDARD_AA,
        ),
        ProductCatalogEntry.SshPack(
            id = CatalogId("sshpack.developer_starter"),
            title = "Developer starter",
            summary = "Typed shortcuts for Finder, Terminal, and GitHub.",
            contentRevision = 1,
            accessClass = CatalogAccessClass.PREMIUM_DISPLAY_ONLY,
            compatibility = compatibility,
            accessibility = accessibility.copy(talkBackLabel = "Developer SSH action pack"),
            pack = SshActionPack(
                id = CatalogId("sshpack.developer_starter"),
                version = CatalogVersion(1, 0, 0),
                actions = listOf(
                    typed("developer.finder", "finder", "Open Finder"),
                    typed("developer.terminal", "terminal", "Open Terminal"),
                    typed("developer.github", "github", "Open GitHub"),
                ),
            ),
        ),
    ).sortedBy { it.id }

    private val bundleId = CatalogId("catalog.bundled_starter")
    private val bundleVersion = CatalogVersion(1, 0, 0)
    private val bundleSource = CatalogSource.Bundled
    private val bundle = CatalogBundle(
        id = bundleId,
        version = bundleVersion,
        source = bundleSource,
        payloadDigest = CatalogBundle.computePayloadDigest(bundleId, bundleVersion, bundleSource, entries),
        signature = null,
        entries = entries,
    )

    override fun explore(): CatalogDiscovery = CatalogDiscovery(
        entries = entries,
        reservedSlots = listOf(
            ReservedDiscoverySlot(
                id = CatalogId("slot.explore_reserved_1"),
                afterOrganicItems = 6,
                reservedHeightDp = 96,
            ),
        ),
    )

    override fun bundledBundle(): CatalogBundle = bundle

    private fun typed(id: String, catalogActionId: String, title: String): TypedSshAction =
        TypedSshAction(CatalogId(id), CatalogId(catalogActionId), title)
}
