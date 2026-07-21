package io.codecks.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object MouseRoute : NavKey

@Serializable
data object KeyboardRoute : NavKey

@Serializable
data object ClipboardRoute : NavKey

@Serializable
data object AutomationsRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object EditorRoute : NavKey

@Serializable
data object AiBuilderRoute : NavKey

@Serializable
data object AiProviderRoute : NavKey

@Serializable
data object RunLogRoute : NavKey

@Serializable
data object CommandPaletteRoute : NavKey

fun NavKey.title(): String = when (this) {
    HomeRoute -> "Deck"
    MouseRoute -> "Trackpad"
    KeyboardRoute -> "Keyboard"
    ClipboardRoute -> "Clipboard"
    AutomationsRoute -> "Automations"
    SettingsRoute -> "Settings"
    EditorRoute -> "Edit Deck"
    AiBuilderRoute -> "AI"
    AiProviderRoute -> "AI settings"
    RunLogRoute -> "Run Log"
    CommandPaletteRoute -> "Command Palette"
    else -> "Codecks"
}
