package io.codecks.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsResolutionTest {
    @Test
    fun localOnlyReleaseKeepsPlayfulCustomizationButLocksOledMode() {
        val saved = CodecksThemeSettings(
            mode = CodecksThemeMode.Light,
            accent = CodecksAccent.Amber,
            surfaceStyle = CodecksSurfaceStyle.Colorful,
            borderStyle = CodecksBorderStyle.Strong,
            shapeStyle = CodecksShapeStyle.Soft,
            deckStyle = CodecksDeckStyle.OneUiGrid,
        )

        assertEquals(
            saved.copy(mode = CodecksThemeMode.Oled),
            saved.resolveForCodecksRelease(customizationEnabled = false),
        )
    }

    @Test
    fun streamDeckAndTablerAreTheReleaseDefaults() {
        assertEquals(CodecksDeckStyle.StreamDeckPro, CodecksReleaseThemeSettings.deckStyle)
        assertEquals(CodecksDeckStyle.StreamDeckPro, CodecksThemeSettings().deckStyle)
        assertEquals(CodecksIconPack.Tabler, CodecksReleaseThemeSettings.iconPack)
        assertEquals(CodecksIconPack.Tabler, CodecksThemeSettings().iconPack)
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
        val saved = CodecksThemeSettings(
            mode = CodecksThemeMode.Dark,
            accent = CodecksAccent.Green,
            surfaceStyle = CodecksSurfaceStyle.Balanced,
            borderStyle = CodecksBorderStyle.Subtle,
            shapeStyle = CodecksShapeStyle.Compact,
        )

        assertEquals(
            saved,
            saved.resolveForCodecksRelease(customizationEnabled = true),
        )
    }

    @Test
    fun localOnlyReleaseKeepsDeckStyleChoice() {
        val saved = CodecksThemeSettings(
            mode = CodecksThemeMode.Light,
            accent = CodecksAccent.Amber,
            surfaceStyle = CodecksSurfaceStyle.Colorful,
            borderStyle = CodecksBorderStyle.Strong,
            shapeStyle = CodecksShapeStyle.Soft,
            deckStyle = CodecksDeckStyle.NothingMonoDeck,
        )

        assertEquals(
            saved.copy(mode = CodecksThemeMode.Oled),
            saved.resolveForCodecksRelease(customizationEnabled = false),
        )
    }
}
