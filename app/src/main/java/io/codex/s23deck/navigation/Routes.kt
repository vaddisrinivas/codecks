package io.codex.s23deck.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object ConnectionRoute : NavKey

@Serializable
data object BluetoothRoute : NavKey

@Serializable
data object MouseRoute : NavKey

@Serializable
data object KeyboardRoute : NavKey

@Serializable
data object ClipboardRoute : NavKey

@Serializable
data object AutomationsRoute : NavKey

@Serializable
data object ActivityRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object EditorRoute : NavKey

@Serializable
data object AdvancedRoute : NavKey

@Serializable
data object WidgetRoute : NavKey

@Serializable
data object AppearanceRoute : NavKey

@Serializable
data object DevicesRoute : NavKey

@Serializable
data object AiBuilderRoute : NavKey

@Serializable
data object ContextDeckRoute : NavKey

@Serializable
data object AiProviderRoute : NavKey

@Serializable
data object PremiumRoute : NavKey

fun NavKey.title(): String = when (this) {
    HomeRoute -> "Deck"
    ConnectionRoute -> "Connection"
    BluetoothRoute -> "Bluetooth"
    MouseRoute -> "Trackpad"
    KeyboardRoute -> "Keyboard"
    ClipboardRoute -> "Clipboard"
    AutomationsRoute -> "Automations"
    ActivityRoute -> "Activity"
    SettingsRoute -> "Settings"
    EditorRoute -> "Edit Deck"
    AdvancedRoute -> "Diagnostics"
    WidgetRoute -> "Widget"
    AppearanceRoute -> "Appearance"
    DevicesRoute -> "Devices"
    AiBuilderRoute -> "AI"
    ContextDeckRoute -> "Context Deck"
    AiProviderRoute -> "AI settings"
    PremiumRoute -> "Premium"
    else -> "Codecks"
}
