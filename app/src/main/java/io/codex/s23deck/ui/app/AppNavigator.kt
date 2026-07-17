package io.codex.s23deck.ui.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.codex.s23deck.domain.commerce.DEFAULT_FEATURE_FLAGS
import io.codex.s23deck.domain.commerce.FeatureFlag
import io.codex.s23deck.navigation.ActivityRoute
import io.codex.s23deck.navigation.AdvancedRoute
import io.codex.s23deck.navigation.AiBuilderRoute
import io.codex.s23deck.navigation.AiProviderRoute
import io.codex.s23deck.navigation.AppearanceRoute
import io.codex.s23deck.navigation.AutomationsRoute
import io.codex.s23deck.navigation.BluetoothRoute
import io.codex.s23deck.navigation.ClipboardRoute
import io.codex.s23deck.navigation.ConnectionRoute
import io.codex.s23deck.navigation.ContextDeckRoute
import io.codex.s23deck.navigation.DevicesRoute
import io.codex.s23deck.navigation.EditorRoute
import io.codex.s23deck.navigation.HomeRoute
import io.codex.s23deck.navigation.KeyboardRoute
import io.codex.s23deck.navigation.MouseRoute
import io.codex.s23deck.navigation.PremiumRoute
import io.codex.s23deck.navigation.SettingsRoute
import io.codex.s23deck.navigation.WidgetRoute

data class AppDestination(
    val route: NavKey,
    val label: String,
    val summary: String,
    val icon: ImageVector,
)

fun mainDestinations(flags: Map<FeatureFlag, Boolean>): List<AppDestination> = buildList {
    if (routeEnabled(HomeRoute, flags)) add(AppDestination(HomeRoute, "Deck", "Run your command keys", Icons.Outlined.Home))
    if (routeEnabled(MouseRoute, flags)) add(AppDestination(MouseRoute, "Trackpad", "Control the Mac pointer", Icons.Outlined.Mouse))
    if (routeEnabled(AutomationsRoute, flags)) add(AppDestination(AutomationsRoute, "Automations", "Safe local workflows", Icons.Outlined.Edit))
    if (flags.enabled(FeatureFlag.Settings)) add(AppDestination(SettingsRoute, "Settings", "Controls, theme, setup", Icons.Outlined.Settings))
}.ifEmpty {
    listOf(AppDestination(SettingsRoute, "Settings", "Controls, theme, setup", Icons.Outlined.Settings))
}

fun routeEnabled(
    route: NavKey,
    flags: Map<FeatureFlag, Boolean>,
): Boolean = when (route) {
    HomeRoute -> flags.enabled(FeatureFlag.Deck)
    MouseRoute -> flags.enabled(FeatureFlag.Trackpad)
    AutomationsRoute -> flags.enabled(FeatureFlag.Automations)
    AiBuilderRoute -> flags.enabled(FeatureFlag.Ai) && flags.enabled(FeatureFlag.AiBuilder)
    ContextDeckRoute -> flags.enabled(FeatureFlag.Ai) && flags.enabled(FeatureFlag.ContextDeck)
    AiProviderRoute -> flags.enabled(FeatureFlag.Ai)
    SettingsRoute -> true
    ConnectionRoute -> flags.enabled(FeatureFlag.Connection)
    BluetoothRoute -> flags.enabled(FeatureFlag.Connection)
    EditorRoute -> flags.enabled(FeatureFlag.Deck) && flags.enabled(FeatureFlag.DeckEditor)
    ActivityRoute -> flags.enabled(FeatureFlag.Activity)
    DevicesRoute -> flags.enabled(FeatureFlag.Devices)
    PremiumRoute -> flags.enabled(FeatureFlag.Premium) && flags.enabled(FeatureFlag.Paywall)
    WidgetRoute -> flags.enabled(FeatureFlag.Widget)
    AppearanceRoute -> flags.enabled(FeatureFlag.Appearance)
    AdvancedRoute -> flags.enabled(FeatureFlag.Advanced)
    KeyboardRoute -> false
    ClipboardRoute -> false
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
        "mouse", "trackpad", "keyboard" -> MouseRoute
        "bluetooth" -> BluetoothRoute
        "clipboard" -> SettingsRoute
        "automations" -> AutomationsRoute
        "activity" -> ActivityRoute
        "connection" -> ConnectionRoute
        "devices" -> DevicesRoute
        "ai" -> AiBuilderRoute
        "context", "contextdeck" -> ContextDeckRoute
        "premium" -> PremiumRoute
        "editor" -> EditorRoute
        "advanced" -> AdvancedRoute
        "settings" -> SettingsRoute
        "widget" -> WidgetRoute
        "appearance" -> AppearanceRoute
        else -> SettingsRoute
    },
    flags = flags,
)

fun Map<FeatureFlag, Boolean>.enabled(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)
