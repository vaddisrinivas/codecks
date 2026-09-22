package io.codecks.ui.app

import androidx.navigation3.runtime.NavKey
import io.codecks.domain.features.FeatureFlag
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.SettingsRoute

typealias AppDestination = RouteDescriptor<out io.codecks.navigation.AppRoute>

fun mainDestinations(flags: Map<FeatureFlag, Boolean>): List<AppDestination> =
    RouteRegistry.primaryDestinations().filter { it.enabled(flags) }.ifEmpty {
        listOf(requireNotNull(RouteRegistry.descriptor(SettingsRoute)))
    }

fun routeEnabled(
    route: NavKey,
    flags: Map<FeatureFlag, Boolean>,
    exposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
): Boolean = RouteRegistry.descriptor(route)?.let { it.availableIn(exposure) && it.enabled(flags) } == true

fun guardRoute(
    route: NavKey,
    flags: Map<FeatureFlag, Boolean>,
    fallback: NavKey = SettingsRoute,
    exposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
): NavKey = when {
    routeEnabled(route, flags, exposure) -> route
    routeEnabled(fallback, flags, exposure) -> fallback
    else -> SettingsRoute
}

fun launchRouteForRestoredTop(route: NavKey): NavKey = when (RouteRegistry.descriptor(route)?.restorePolicy) {
    RestorePolicy.KEEP -> route
    RestorePolicy.HOME, null -> HomeRoute
}

fun navRouteFromStateKey(routeName: String?): NavKey = RouteRegistry.fromStateKey(routeName)

fun restoredRouteFromStateKey(
    routeName: String?,
    flags: Map<FeatureFlag, Boolean>,
    exposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
): NavKey = guardRoute(
    route = launchRouteForRestoredTop(navRouteFromStateKey(routeName)),
    flags = flags,
    exposure = exposure,
)

fun routeStateKey(route: NavKey): String = RouteRegistry.descriptor(route)?.stateKey ?: HomeRoute.let {
    requireNotNull(RouteRegistry.descriptor(it)).stateKey
}

fun destinationRequestToRoute(
    destinationRequest: String?,
    flags: Map<FeatureFlag, Boolean>,
    exposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
): NavKey = guardRoute(
    route = if (destinationRequest == null) HomeRoute else RouteRegistry.fromRequestAlias(destinationRequest),
    flags = flags,
    exposure = exposure,
)
