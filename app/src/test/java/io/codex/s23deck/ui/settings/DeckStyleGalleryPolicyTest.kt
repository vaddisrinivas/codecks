package io.codex.s23deck.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckStyleGalleryPolicyTest {
    @Test
    fun settingsShowsActualDeckStylePreviewGallery() {
        val settings = File("src/main/java/io/codex/s23deck/ui/settings/SettingsScreen.kt").readText()
        val tiles = File("src/main/java/io/codex/s23deck/ui/designsystem/DeckComponents.kt").readText()
        val theme = File("src/main/java/io/codex/s23deck/ui/theme/ThemeSettingsRepository.kt").readText()

        assertTrue(settings.contains("DeckStylePreviewCard("))
        assertTrue(settings.contains("CodecksDeckStyle.StreamDeckPro, CodecksDeckStyle.NothingMonoDeck"))
        assertTrue(!settings.contains("items(CodecksDeckStyle.entries"))
        assertTrue(settings.contains("DeckControlTile("))
        assertTrue(tiles.contains("deckStyle: CodecksDeckStyle = CodecksDeckStyle.StreamDeckPro"))
        assertTrue(theme.contains("deckStyle = CodecksDeckStyle.StreamDeckPro"))
        assertTrue(settings.contains("items(CodecksIconPack.entries"))
    }
}
