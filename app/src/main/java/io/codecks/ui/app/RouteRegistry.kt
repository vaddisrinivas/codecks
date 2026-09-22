package io.codecks.ui.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.navigation.AiBuilderRoute
import io.codecks.navigation.AiProviderRoute
import io.codecks.navigation.AppRoute
import io.codecks.navigation.AutomationsRoute
import io.codecks.navigation.ClipboardRoute
import io.codecks.navigation.CommandPaletteRoute
import io.codecks.navigation.EditorRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.KeyboardRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.RunLogRoute
import io.codecks.navigation.SettingsRoute
import kotlin.reflect.KClass

enum class RouteVisibility { PRIMARY, SECONDARY, TRANSIENT }

enum class RouteGroup(val label: String) { CONTROL("Control"), BUILD("Build"), MANAGE("Manage") }

enum class DeepLinkEligibility { NONE, PUBLIC }

enum class LockscreenRoutePolicy { BLOCKED, RESTRICTED_POINTER }

enum class RouteBuildExposure {
    PUBLIC,
    INTERNAL_LAB;

    fun allows(routeExposure: RouteBuildExposure): Boolean =
        routeExposure == PUBLIC || this == INTERNAL_LAB

    companion object {
        fun fromDistributionChannel(channel: String): RouteBuildExposure =
            if (channel == "play_internal") INTERNAL_LAB else PUBLIC
    }
}

enum class RestorePolicy { KEEP, HOME }

enum class RouteFeature {
    ALWAYS,
    DECK,
    TRACKPAD,
    KEYBOARD,
    CLIPBOARD,
    AUTOMATIONS,
    AI,
    DECK_EDITOR,
}

data class RouteDescriptor<R : AppRoute>(
    val route: R,
    val stateKey: String,
    val title: String,
    val label: String,
    val summary: String,
    val icon: ImageVector,
    val visibility: RouteVisibility,
    val group: RouteGroup,
    val feature: RouteFeature,
    val requestAliases: Set<String>,
    val deepLinkEligibility: DeepLinkEligibility,
    val publicDeepLinks: Set<String>,
    val lockscreenPolicy: LockscreenRoutePolicy,
    val testTag: String,
    val buildExposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
    val deepLinkAllowsQuery: Boolean = false,
    val restorePolicy: RestorePolicy = RestorePolicy.KEEP,
    val clearsBackStack: Boolean = false,
    val navigationOrder: Int = Int.MAX_VALUE,
    val moreOrder: Int = Int.MAX_VALUE,
) {
    val destinationClass: KClass<out AppRoute> = route::class
}

object RouteRegistry {
    val descriptors: List<RouteDescriptor<out AppRoute>> = listOf(
        RouteDescriptor(
            HomeRoute, "home", "Deck", "Deck", "Run buttons on your Mac", Icons.Outlined.GridView,
            RouteVisibility.PRIMARY, RouteGroup.CONTROL, RouteFeature.DECK, setOf("home"),
            DeepLinkEligibility.NONE, emptySet(), LockscreenRoutePolicy.BLOCKED, "destination-deck",
            clearsBackStack = true, navigationOrder = 0,
        ),
        RouteDescriptor(
            MouseRoute, "mouse", "Trackpad", "Trackpad", "Control your Mac pointer", Icons.Outlined.Mouse,
            RouteVisibility.PRIMARY, RouteGroup.CONTROL, RouteFeature.TRACKPAD, setOf("mouse", "trackpad", "bluetooth"),
            DeepLinkEligibility.PUBLIC, setOf("codecks://trackpad"), LockscreenRoutePolicy.RESTRICTED_POINTER,
            "destination-trackpad", clearsBackStack = true, navigationOrder = 1,
        ),
        RouteDescriptor(
            KeyboardRoute, "keyboard", "Keyboard", "Keyboard", "Type on your Mac", Icons.Outlined.Keyboard,
            RouteVisibility.PRIMARY, RouteGroup.CONTROL, RouteFeature.KEYBOARD, setOf("keyboard", "text"),
            DeepLinkEligibility.NONE, emptySet(), LockscreenRoutePolicy.BLOCKED, "destination-keyboard",
            clearsBackStack = true, navigationOrder = 2,
        ),
        RouteDescriptor(
            ClipboardRoute, "clipboard", "Clipboard", "Clipboard", "Move text between phone and Mac",
            Icons.Outlined.ContentPaste, RouteVisibility.PRIMARY, RouteGroup.CONTROL, RouteFeature.CLIPBOARD,
            setOf("clipboard"), DeepLinkEligibility.NONE, emptySet(), LockscreenRoutePolicy.BLOCKED,
            "destination-clipboard", clearsBackStack = true, navigationOrder = 3,
        ),
        RouteDescriptor(
            AutomationsRoute, "automations", "Rules", "Rules", "Run local rules", Icons.Outlined.Bolt,
            RouteVisibility.PRIMARY, RouteGroup.BUILD, RouteFeature.AUTOMATIONS, setOf("automations"),
            DeepLinkEligibility.NONE, emptySet(), LockscreenRoutePolicy.BLOCKED, "destination-rules",
            navigationOrder = 4, moreOrder = 1,
        ),
        RouteDescriptor(
            AiBuilderRoute, "ai_builder", "AI Builder", "AI Builder", "Create buttons, Decks, and Rules",
            Icons.Outlined.AutoAwesome, RouteVisibility.PRIMARY, RouteGroup.BUILD, RouteFeature.AI, setOf("ai"),
            DeepLinkEligibility.PUBLIC, setOf("codecks://ai"), LockscreenRoutePolicy.BLOCKED, "destination-ai-builder",
            navigationOrder = 5, moreOrder = 0,
        ),
        RouteDescriptor(
            SettingsRoute, "settings", "Settings", "Settings", "Configure Macs and controls", Icons.Outlined.Settings,
            RouteVisibility.PRIMARY, RouteGroup.MANAGE, RouteFeature.ALWAYS,
            setOf("connection", "pairing", "context", "contextdeck", "settings"), DeepLinkEligibility.PUBLIC,
            setOf("codecks://helper-pair"), LockscreenRoutePolicy.BLOCKED, "destination-settings",
            deepLinkAllowsQuery = true, navigationOrder = 6, moreOrder = 3,
        ),
        RouteDescriptor(
            RunLogRoute, "run_log", "Run Log", "Run history", "Inspect local action outcomes", Icons.Outlined.History,
            RouteVisibility.SECONDARY, RouteGroup.MANAGE, RouteFeature.ALWAYS, emptySet(), DeepLinkEligibility.NONE,
            emptySet(), LockscreenRoutePolicy.BLOCKED, "destination-run-history", restorePolicy = RestorePolicy.HOME,
            navigationOrder = 7, moreOrder = 2,
        ),
        RouteDescriptor(
            EditorRoute, "editor", "Edit Deck", "Edit Deck", "Edit Deck layout and actions", Icons.Outlined.Edit,
            RouteVisibility.TRANSIENT, RouteGroup.CONTROL, RouteFeature.DECK_EDITOR, setOf("editor"),
            DeepLinkEligibility.NONE, emptySet(), LockscreenRoutePolicy.BLOCKED, "destination-editor",
            restorePolicy = RestorePolicy.HOME,
        ),
        RouteDescriptor(
            AiProviderRoute, "ai_provider", "AI settings", "AI settings", "Configure local AI providers",
            Icons.Outlined.AutoAwesome, RouteVisibility.TRANSIENT, RouteGroup.BUILD, RouteFeature.AI, emptySet(),
            DeepLinkEligibility.NONE, emptySet(), LockscreenRoutePolicy.BLOCKED, "destination-ai-provider",
            restorePolicy = RestorePolicy.HOME,
        ),
        RouteDescriptor(
            CommandPaletteRoute, "command_palette", "Command Palette", "Command Palette", "Run local commands",
            Icons.Outlined.Terminal, RouteVisibility.TRANSIENT, RouteGroup.CONTROL, RouteFeature.ALWAYS,
            setOf("palette", "command_palette"), DeepLinkEligibility.NONE, emptySet(), LockscreenRoutePolicy.BLOCKED,
            "destination-command-palette", restorePolicy = RestorePolicy.HOME,
        ),
    )

    private val byRoute = descriptors.associateBy { it.route }
    private val byStateKey = descriptors.associateBy { it.stateKey }
    private val byRequestAlias = descriptors.flatMap { descriptor ->
        descriptor.requestAliases.map { alias -> alias to descriptor }
    }.toMap()
    private val index = RouteRegistryIndex(descriptors)

    fun visibleDestinations(
        exposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
    ): List<RouteDescriptor<out AppRoute>> = descriptors
        .filter { exposure.allows(it.buildExposure) && it.visibility != RouteVisibility.TRANSIENT }
        .sortedBy { it.navigationOrder }

    fun primaryDestinations(
        exposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
    ): List<RouteDescriptor<out AppRoute>> = visibleDestinations(exposure)
        .filter { it.visibility == RouteVisibility.PRIMARY }

    fun descriptor(route: NavKey): RouteDescriptor<out AppRoute>? = byRoute[route]

    fun requestAlias(route: NavKey): String =
        descriptor(route)?.requestAliases?.firstOrNull() ?: error("Route has no request alias: $route")

    fun publicDeepLinkRoute(uri: String?, exposure: RouteBuildExposure): AppRoute? =
        index.publicDeepLinkRoute(uri, exposure)

    fun fromStateKey(stateKey: String?): AppRoute = byStateKey[stateKey]?.route ?: HomeRoute

    fun fromRequestAlias(alias: String?): AppRoute = alias?.let(byRequestAlias::get)?.route ?: SettingsRoute
}

internal class RouteRegistryIndex(
    private val descriptors: List<RouteDescriptor<out AppRoute>>,
) {
    fun publicDeepLinkRoute(uri: String?, exposure: RouteBuildExposure): AppRoute? =
        descriptors.firstOrNull { descriptor ->
            descriptor.availableIn(exposure) &&
                descriptor.deepLinkEligibility == DeepLinkEligibility.PUBLIC &&
                descriptor.publicDeepLinks.any { link ->
                    uri == link || (descriptor.deepLinkAllowsQuery && uri?.startsWith("$link?") == true)
                }
        }?.route
}

fun RouteDescriptor<*>.enabled(flags: Map<FeatureFlag, Boolean>): Boolean = when (feature) {
    RouteFeature.ALWAYS -> true
    RouteFeature.DECK -> flags.enabled(FeatureFlag.Deck)
    RouteFeature.TRACKPAD -> flags.enabled(FeatureFlag.Trackpad)
    RouteFeature.KEYBOARD -> flags.enabled(FeatureFlag.Keyboard)
    RouteFeature.CLIPBOARD -> flags.enabled(FeatureFlag.Clipboard)
    RouteFeature.AUTOMATIONS -> flags.enabled(FeatureFlag.Automations)
    RouteFeature.AI -> flags.enabled(FeatureFlag.Ai)
    RouteFeature.DECK_EDITOR -> flags.enabled(FeatureFlag.Deck) && flags.enabled(FeatureFlag.DeckEditor)
}

fun RouteDescriptor<*>.availableIn(exposure: RouteBuildExposure): Boolean = exposure.allows(buildExposure)

fun NavKey.title(): String = RouteRegistry.descriptor(this)?.title ?: "Codecks"

fun Map<FeatureFlag, Boolean>.enabled(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)
