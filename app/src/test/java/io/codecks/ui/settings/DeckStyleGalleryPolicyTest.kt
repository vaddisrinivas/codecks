package io.codecks.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckStyleGalleryPolicyTest {
    @Test
    fun settingsShowsActualDeckStylePreviewGallery() {
        val settings = File("src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()
        val tiles = File("src/main/java/io/codecks/ui/designsystem/DeckComponents.kt").readText()
        val theme = File("src/main/java/io/codecks/ui/theme/ThemeSettingsRepository.kt").readText()

        assertTrue(settings.contains("DeckStylePreviewCard("))
        assertTrue(settings.contains("CodecksDeckStyle.entries"))
        assertTrue(settings.contains("Pick the deck personality"))
        assertTrue(settings.contains("DeckControlTile("))
        assertTrue(tiles.contains("deckStyle: CodecksDeckStyle = CodecksDeckStyle.StreamDeckPro"))
        assertTrue(theme.contains("deckStyle = CodecksDeckStyle.StreamDeckPro"))
        assertTrue(settings.contains("items(CodecksIconPack.entries.filterNot"))
    }
}
