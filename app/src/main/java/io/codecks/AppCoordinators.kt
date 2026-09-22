package io.codecks

import androidx.navigation3.runtime.NavKey
import io.codecks.domain.features.FeatureFlag
import io.codecks.navigation.AiBuilderRoute
import io.codecks.navigation.AiProviderRoute
import io.codecks.navigation.SettingsRoute
import io.codecks.ui.app.RouteBuildExposure
import io.codecks.ui.app.destinationRequestToRoute
import io.codecks.ui.app.guardRoute
import io.codecks.ui.app.restoredRouteFromStateKey

internal data class NavigationBootstrap(
    val initialRoute: NavKey,
    val exposure: RouteBuildExposure,
)

internal object AppNavigationCoordinator {
    fun bootstrap(
        restoredStateKey: String?,
        flags: Map<FeatureFlag, Boolean>,
        distributionChannel: String,
    ): NavigationBootstrap {
        val exposure = RouteBuildExposure.fromDistributionChannel(distributionChannel)
        return NavigationBootstrap(
            initialRoute = restoredRouteFromStateKey(restoredStateKey, flags, exposure),
            exposure = exposure,
        )
    }

    fun guard(route: NavKey, flags: Map<FeatureFlag, Boolean>, exposure: RouteBuildExposure): NavKey =
        guardRoute(route, flags, exposure = exposure)

    fun request(alias: String?, flags: Map<FeatureFlag, Boolean>, exposure: RouteBuildExposure): NavKey =
        destinationRequestToRoute(alias, flags, exposure)
}

internal object AppBootstrapCoordinator {
    fun needsHelper(route: NavKey, pendingPairing: Boolean, reactiveEnabled: Boolean): Boolean =
        pendingPairing || route == SettingsRoute || reactiveEnabled

    fun needsAi(route: NavKey): Boolean = route == AiBuilderRoute || route == AiProviderRoute || route == SettingsRoute
}
