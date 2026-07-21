package io.codecks.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsResolutionTest {
    @Test
    fun localOnlyReleaseKeepsPlayfulCustomizationButLocksOledMode() {
        val saved = DeckBridgeThemeSettings(
            mode = DeckBridgeThemeMode.Light,
            accent = DeckBridgeAccent.Amber,
            surfaceStyle = DeckBridgeSurfaceStyle.Colorful,
            borderStyle = DeckBridgeBorderStyle.Strong,
            shapeStyle = DeckBridgeShapeStyle.Soft,
            deckStyle = CodecksDeckStyle.OneUiWidgetGrid,
        )

        assertEquals(
            saved.copy(mode = DeckBridgeThemeMode.Oled),
            saved.resolveForCodecksRelease(customizationEnabled = false),
        )
    }

    @Test
    fun streamDeckAndTablerAreTheReleaseDefaults() {
        assertEquals(CodecksDeckStyle.StreamDeckPro, CodecksReleaseThemeSettings.deckStyle)
        assertEquals(CodecksDeckStyle.StreamDeckPro, DeckBridgeThemeSettings().deckStyle)
        assertEquals(CodecksIconPack.Tabler, CodecksReleaseThemeSettings.iconPack)
        assertEquals(CodecksIconPack.Tabler, DeckBridgeThemeSettings().iconPack)
    }

    @Test
    fun rejectedAuroraDefaultMigratesExactlyOnce() {
        assertEquals(
            CodecksDeckStyle.StreamDeckPro,
            resolvePersistedDeckStyle(
                savedDeckStyle = CodecksDeckStyle.AuroraGlass,
                userSelectedDeckStyle = true,
                deckStyleRevision = CURRENT_DECK_STYLE_REVISION - 1,
            ),
        )
        assertEquals(
            CodecksDeckStyle.AuroraGlass,
            resolvePersistedDeckStyle(
                savedDeckStyle = CodecksDeckStyle.AuroraGlass,
                userSelectedDeckStyle = true,
                deckStyleRevision = CURRENT_DECK_STYLE_REVISION,
            ),
        )
    }

    @Test
    fun visualSystemMigrationReplacesLegacyExplicitStylesOnce() {
        assertEquals(
            CodecksDeckStyle.StreamDeckPro,
            resolvePersistedDeckStyle(
                savedDeckStyle = CodecksDeckStyle.CandyPop,
                userSelectedDeckStyle = true,
                deckStyleRevision = 0,
            ),
        )
    }

    @Test
    fun customizationFlagCanRestoreSavedThemeCombinationsLater() {
        val saved = DeckBridgeThemeSettings(
            mode = DeckBridgeThemeMode.Dark,
            accent = DeckBridgeAccent.Green,
            surfaceStyle = DeckBridgeSurfaceStyle.Balanced,
            borderStyle = DeckBridgeBorderStyle.Subtle,
            shapeStyle = DeckBridgeShapeStyle.Compact,
        )

        assertEquals(
            saved,
            saved.resolveForCodecksRelease(customizationEnabled = true),
        )
    }

    @Test
    fun localOnlyReleaseKeepsDeckStyleChoice() {
        val saved = DeckBridgeThemeSettings(
            mode = DeckBridgeThemeMode.Light,
            accent = DeckBridgeAccent.Amber,
            surfaceStyle = DeckBridgeSurfaceStyle.Colorful,
            borderStyle = DeckBridgeBorderStyle.Strong,
            shapeStyle = DeckBridgeShapeStyle.Soft,
            deckStyle = CodecksDeckStyle.NothingMonoDeck,
        )

        assertEquals(
            saved.copy(mode = DeckBridgeThemeMode.Oled),
            saved.resolveForCodecksRelease(customizationEnabled = false),
        )
    }
}
