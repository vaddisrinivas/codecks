package io.codecks.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

enum class DeckBridgeThemeMode(val label: String, val description: String) {
    System("System", "Follow Android light and dark mode"),
    Light("Light", "Bright Material surfaces"),
    Dark("Dark", "Dark Material surfaces"),
    Oled("OLED black", "Pure black surfaces for trackpad-heavy use"),
}

enum class DeckBridgeAccent(val label: String) {
    Blue("Blue"),
    Cyan("Cyan"),
    Green("Green"),
    Violet("Violet"),
    Amber("Amber"),
    Rose("Hot pink"),
    Coral("Coral"),
    Lime("Lime"),
}

enum class DeckBridgeSurfaceStyle(val label: String, val description: String) {
    Balanced("Balanced", "Material surfaces with calm contrast"),
    Crisp("Crisp", "More separation between cards, rows, and fields"),
    Colorful("Colorful", "More accent color in active controls"),
}

enum class DeckBridgeBorderStyle(val label: String, val description: String) {
    Subtle("Subtle", "Soft outlines"),
    Visible("Visible", "Clear control borders"),
    Strong("Strong", "High-contrast borders for every control"),
}

enum class DeckBridgeShapeStyle(val label: String, val description: String) {
    Native("Native", "Google-style rounded controls"),
    Compact("Compact", "Sharper utility controls"),
    Soft("Soft", "Softer cards and sheets"),
}

enum class CodecksDeckStyle(val label: String, val description: String) {
    AuroraGlass(
        "Aurora Glass",
        "Cyan, violet, and hot-pink light blooming through deep smoked glass.",
    ),
    CandyPop(
        "Candy Pop",
        "Playful coral, mango, mint, and lilac keys with soft translucent faces.",
    ),
    ArcadeNeon(
        "Arcade Neon",
        "Electric lime, cyan, magenta, and orange with a punchier control-deck glow.",
    ),
    OneUiWidgetGrid(
        "One UI Widget Grid",
        "Dark rounded widget tiles, big icons, tiny labels, almost no toy keycap effect.",
    ),
    StreamDeckPro(
        "Codecks Green",
        "Near-black controls, Android green light, quiet outlines, and crisp utility hierarchy.",
    ),
    NothingMonoDeck(
        "Nothing Mono Deck",
        "AMOLED black, white outlines, monochrome controls, red only for danger.",
    ),
    CodexMicroGlass(
        "Codecks Micro Glass",
        "Frosted physical key feel with restrained live status glow.",
    ),
}

data class DeckBridgeThemeSettings(
    val mode: DeckBridgeThemeMode = DeckBridgeThemeMode.Oled,
    val accent: DeckBridgeAccent = DeckBridgeAccent.Green,
    val surfaceStyle: DeckBridgeSurfaceStyle = DeckBridgeSurfaceStyle.Balanced,
    val borderStyle: DeckBridgeBorderStyle = DeckBridgeBorderStyle.Visible,
    val shapeStyle: DeckBridgeShapeStyle = DeckBridgeShapeStyle.Soft,
    val deckStyle: CodecksDeckStyle = CodecksDeckStyle.StreamDeckPro,
    val iconPack: CodecksIconPack = CodecksIconPack.Tabler,
)

val CodecksReleaseThemeSettings = DeckBridgeThemeSettings(
    mode = DeckBridgeThemeMode.Oled,
    accent = DeckBridgeAccent.Green,
    surfaceStyle = DeckBridgeSurfaceStyle.Balanced,
    borderStyle = DeckBridgeBorderStyle.Visible,
    shapeStyle = DeckBridgeShapeStyle.Soft,
    deckStyle = CodecksDeckStyle.StreamDeckPro,
    iconPack = CodecksIconPack.Tabler,
)

internal const val CURRENT_DECK_STYLE_REVISION = 3
internal const val CURRENT_VISUAL_SYSTEM_REVISION = 1

internal fun resolvePersistedDeckStyle(
    savedDeckStyle: CodecksDeckStyle?,
    userSelectedDeckStyle: Boolean,
    deckStyleRevision: Int,
): CodecksDeckStyle = when {
    deckStyleRevision < CURRENT_DECK_STYLE_REVISION -> CodecksDeckStyle.StreamDeckPro
    !userSelectedDeckStyle && savedDeckStyle in setOf(null, CodecksDeckStyle.OneUiWidgetGrid, CodecksDeckStyle.CodexMicroGlass) -> CodecksDeckStyle.StreamDeckPro
    savedDeckStyle != null -> savedDeckStyle
    else -> CodecksDeckStyle.StreamDeckPro
}

fun DeckBridgeThemeSettings.resolveForCodecksRelease(
    customizationEnabled: Boolean,
): DeckBridgeThemeSettings =
    if (customizationEnabled) this else CodecksReleaseThemeSettings.copy(
        accent = accent,
        surfaceStyle = surfaceStyle,
        borderStyle = borderStyle,
        shapeStyle = shapeStyle,
        deckStyle = deckStyle,
        iconPack = iconPack,
    )

class ThemeSettingsRepository(private val context: Context) {
    val settings: Flow<DeckBridgeThemeSettings> = context.themeDataStore.data.map { preferences ->
        val useCodecksGreenDefaults = (preferences[VISUAL_SYSTEM_REVISION] ?: 0) < CURRENT_VISUAL_SYSTEM_REVISION
        val savedDeckStyle = preferences[DECK_STYLE]
            ?.let { saved -> CodecksDeckStyle.entries.firstOrNull { it.name == saved } }
        val userSelectedDeckStyle = preferences[DECK_STYLE_USER_SELECTED] == true
        val deckStyleRevision = preferences[DECK_STYLE_REVISION] ?: 0
        DeckBridgeThemeSettings(
            mode = if (useCodecksGreenDefaults) DeckBridgeThemeMode.Oled else preferences[MODE]?.let { saved -> DeckBridgeThemeMode.entries.firstOrNull { it.name == saved } }
                ?: DeckBridgeThemeMode.Oled,
            accent = if (useCodecksGreenDefaults) DeckBridgeAccent.Green else preferences[ACCENT]?.let { saved -> DeckBridgeAccent.entries.firstOrNull { it.name == saved } }
                ?: DeckBridgeAccent.Green,
            surfaceStyle = if (useCodecksGreenDefaults) DeckBridgeSurfaceStyle.Balanced else preferences[SURFACE_STYLE]?.let { saved -> DeckBridgeSurfaceStyle.entries.firstOrNull { it.name == saved } }
                ?: DeckBridgeSurfaceStyle.Balanced,
            borderStyle = if (useCodecksGreenDefaults) DeckBridgeBorderStyle.Visible else preferences[BORDER_STYLE]?.let { saved -> DeckBridgeBorderStyle.entries.firstOrNull { it.name == saved } }
                ?: DeckBridgeBorderStyle.Visible,
            shapeStyle = if (useCodecksGreenDefaults) DeckBridgeShapeStyle.Soft else preferences[SHAPE_STYLE]?.let { saved -> DeckBridgeShapeStyle.entries.firstOrNull { it.name == saved } }
                ?: DeckBridgeShapeStyle.Soft,
            deckStyle = if (useCodecksGreenDefaults) CodecksDeckStyle.StreamDeckPro else resolvePersistedDeckStyle(savedDeckStyle, userSelectedDeckStyle, deckStyleRevision),
            iconPack = if (useCodecksGreenDefaults) CodecksIconPack.Tabler else preferences[ICON_PACK]
                ?.let { saved -> CodecksIconPack.entries.firstOrNull { it.name == saved } }
                ?.takeUnless { it == CodecksIconPack.FontAwesome }
                ?: CodecksIconPack.Tabler,
        )
    }

    val mode: Flow<DeckBridgeThemeMode> = context.themeDataStore.data.map { preferences ->
        preferences[MODE]?.let { saved ->
            DeckBridgeThemeMode.entries.firstOrNull { it.name == saved }
        } ?: DeckBridgeThemeMode.Oled
    }

    suspend fun setMode(mode: DeckBridgeThemeMode) {
        context.themeDataStore.edit { it[MODE] = mode.name }
    }

    suspend fun setAccent(accent: DeckBridgeAccent) {
        context.themeDataStore.edit { it[ACCENT] = accent.name }
    }

    suspend fun setSurfaceStyle(style: DeckBridgeSurfaceStyle) {
        context.themeDataStore.edit { it[SURFACE_STYLE] = style.name }
    }

    suspend fun setBorderStyle(style: DeckBridgeBorderStyle) {
        context.themeDataStore.edit { it[BORDER_STYLE] = style.name }
    }

    suspend fun setShapeStyle(style: DeckBridgeShapeStyle) {
        context.themeDataStore.edit { it[SHAPE_STYLE] = style.name }
    }

    suspend fun setDeckStyle(style: CodecksDeckStyle) {
        context.themeDataStore.edit {
            it[DECK_STYLE] = style.name
            it[DECK_STYLE_USER_SELECTED] = true
            it[DECK_STYLE_REVISION] = CURRENT_DECK_STYLE_REVISION
        }
    }

    suspend fun setIconPack(iconPack: CodecksIconPack) {
        context.themeDataStore.edit { it[ICON_PACK] = iconPack.name }
    }

    suspend fun migrateToCurrentVisualSystem() {
        context.themeDataStore.edit { preferences ->
            if ((preferences[VISUAL_SYSTEM_REVISION] ?: 0) >= CURRENT_VISUAL_SYSTEM_REVISION) return@edit
            preferences[MODE] = DeckBridgeThemeMode.Oled.name
            preferences[ACCENT] = DeckBridgeAccent.Green.name
            preferences[SURFACE_STYLE] = DeckBridgeSurfaceStyle.Balanced.name
            preferences[BORDER_STYLE] = DeckBridgeBorderStyle.Visible.name
            preferences[SHAPE_STYLE] = DeckBridgeShapeStyle.Soft.name
            preferences[DECK_STYLE] = CodecksDeckStyle.StreamDeckPro.name
            preferences[DECK_STYLE_USER_SELECTED] = false
            preferences[DECK_STYLE_REVISION] = CURRENT_DECK_STYLE_REVISION
            preferences[ICON_PACK] = CodecksIconPack.Tabler.name
            preferences[VISUAL_SYSTEM_REVISION] = CURRENT_VISUAL_SYSTEM_REVISION
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val ACCENT = stringPreferencesKey("accent")
        val SURFACE_STYLE = stringPreferencesKey("surface_style")
        val BORDER_STYLE = stringPreferencesKey("border_style")
        val SHAPE_STYLE = stringPreferencesKey("shape_style")
        val DECK_STYLE = stringPreferencesKey("deck_style")
        val DECK_STYLE_USER_SELECTED = booleanPreferencesKey("deck_style_user_selected")
        val DECK_STYLE_REVISION = intPreferencesKey("deck_style_revision")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val VISUAL_SYSTEM_REVISION = intPreferencesKey("visual_system_revision")
    }
}
