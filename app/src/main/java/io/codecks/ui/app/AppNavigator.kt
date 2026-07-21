package io.codecks.ui.app

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.navigation.AiBuilderRoute
import io.codecks.navigation.AiProviderRoute
import io.codecks.navigation.AutomationsRoute
import io.codecks.navigation.ClipboardRoute
import io.codecks.navigation.CommandPaletteRoute
import io.codecks.navigation.EditorRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.KeyboardRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.RunLogRoute
import io.codecks.navigation.SettingsRoute

data class AppDestination(
    val route: NavKey,
    val label: String,
    val summary: String,
    val icon: ImageVector,
)

fun mainDestinations(flags: Map<FeatureFlag, Boolean>): List<AppDestination> = buildList {
    PrimaryTab.entries.forEach { tab ->
        if (routeEnabled(tab.route, flags)) {
            add(AppDestination(tab.route, tab.label, tab.summary, tab.icon))
        }
    }
}.ifEmpty {
    listOf(AppDestination(SettingsRoute, "Settings", "Controls, theme, setup", PrimaryTab.Deck.icon))
}

fun routeEnabled(
    route: NavKey,
    flags: Map<FeatureFlag, Boolean>,
): Boolean = when (route) {
    HomeRoute -> flags.enabled(FeatureFlag.Deck)
    MouseRoute -> flags.enabled(FeatureFlag.Trackpad)
    KeyboardRoute -> flags.enabled(FeatureFlag.Trackpad)
    ClipboardRoute -> flags.enabled(FeatureFlag.Trackpad)
    AutomationsRoute -> flags.enabled(FeatureFlag.Automations)
    AiBuilderRoute -> flags.enabled(FeatureFlag.Ai)
    AiProviderRoute -> flags.enabled(FeatureFlag.Ai)
    SettingsRoute -> true
    RunLogRoute -> true
    CommandPaletteRoute -> true
    EditorRoute -> flags.enabled(FeatureFlag.Deck) && flags.enabled(FeatureFlag.DeckEditor)
    else -> false
}

fun guardRoute(
    route: NavKey,
    flags: Map<FeatureFlag, Boolean>,
    fallback: NavKey = SettingsRoute,
): NavKey = when {
    routeEnabled(route, flags) -> route
    routeEnabled(fallback, flags) -> fallback
    else -> SettingsRoute
}

fun destinationRequestToRoute(
    destinationRequest: String?,
    flags: Map<FeatureFlag, Boolean>,
): NavKey = guardRoute(
    route = when (destinationRequest) {
        null -> HomeRoute
        "home" -> HomeRoute
        "mouse", "trackpad" -> MouseRoute
        "keyboard", "text" -> KeyboardRoute
        "bluetooth" -> MouseRoute
        "clipboard" -> ClipboardRoute
        "automations" -> AutomationsRoute
        "connection", "pairing" -> SettingsRoute
        "ai" -> AiBuilderRoute
        "context", "contextdeck" -> SettingsRoute
        "editor" -> EditorRoute
        "palette", "command_palette" -> CommandPaletteRoute
        "settings" -> SettingsRoute
        else -> SettingsRoute
    },
    flags = flags,
)

private val PrimaryTab.summary: String
    get() = when (this) {
        PrimaryTab.Deck -> "Run your command keys"
        PrimaryTab.Trackpad -> "Control the Mac pointer"
        PrimaryTab.Keyboard -> "Type into your Mac"
        PrimaryTab.Clipboard -> "Sync clipboard"
        PrimaryTab.Automations -> "Safe local workflows"
        PrimaryTab.Ai -> "Chat, create, accept"
        PrimaryTab.Settings -> "Controls, theme, setup"
    }

fun Map<FeatureFlag, Boolean>.enabled(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)
