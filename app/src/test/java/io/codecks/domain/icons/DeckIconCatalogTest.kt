package io.codecks.domain.icons

import io.codecks.data.icons.BundledDeckIconCatalog
import io.codecks.domain.ActionIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

class DeckIconCatalogTest {
    private val catalog = BundledDeckIconCatalog.catalog

    @Test
    fun fourPermissivePacksExposeDeterministicProvenance() {
        assertEquals(listOf("pack.feather", "pack.material", "pack.rounded", "pack.tabler"), catalog.packs.map { it.id.value })
        assertTrue(catalog.packs.all { it.license.spdxId in setOf("MIT", "Apache-2.0") })
        assertTrue(catalog.packs.all { it.license.upstreamUrl.startsWith("https://") })
        assertEquals(1, catalog.packs.count { it.rounded })
    }

    @Test
    fun semanticIdentityDoesNotDependOnVisualPack() {
        val terminal = catalog.find(SemanticIconId("icon.terminal"))
        catalog.packs.forEach { assertEquals(ActionIcon.Terminal, terminal.actionIcon) }
    }

    @Test
    fun searchCategoriesFavoritesRecentsAndFallbackAreBounded() {
        val initial = IconPreferenceState()
            .toggleFavorite(SemanticIconId("icon.terminal"))
            .recordRecent(SemanticIconId("icon.search"))
            .recordRecent(SemanticIconId("icon.terminal"))

        assertEquals(listOf("icon.terminal"), catalog.search("developer", favoritesOnly = true, preferences = initial).map { it.id.value })
        assertTrue(catalog.search("audio", IconCategory.MEDIA).all { it.category == IconCategory.MEDIA })
        assertEquals(listOf("icon.terminal", "icon.search"), catalog.recent(initial).map { it.id.value })
        assertEquals("icon.fallback", catalog.find(SemanticIconId("icon.missing")).id.value)
        assertFalse(catalog.search("definitely absent").isNotEmpty())

        var state = IconPreferenceState()
        repeat(80) { index ->
            state = state.toggleFavorite(SemanticIconId("icon.user_$index"))
            state = state.recordRecent(SemanticIconId("icon.recent_$index"))
        }
        assertEquals(IconPreferenceState.MAX_FAVORITES, state.favorites.size)
        assertEquals(IconPreferenceState.MAX_RECENTS, state.recents.size)
    }

    @Test
    fun catalogWithoutFallbackIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DeckIconCatalog(catalog.packs, catalog.icons.filterNot { it.id.value == "icon.fallback" })
        }
    }

    @Test
    fun searchCaseFoldingIsStableUnderTurkishLocale() {
        val prior = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("icon.finder"), catalog.search("FINDER").map { it.id.value })
        } finally {
            Locale.setDefault(prior)
        }
    }
}
