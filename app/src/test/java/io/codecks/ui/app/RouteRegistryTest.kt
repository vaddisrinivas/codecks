package io.codecks.ui.app

import androidx.navigation3.runtime.NavKey
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.navigation.AppRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.SettingsRoute
import java.io.File
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRegistryTest {
    @Test
    fun registry_isCompleteForEveryTypedRoute() {
        assertEquals(
            AppRoute::class.java.permittedSubclasses.orEmpty().map { it.name }.toSet(),
            RouteRegistry.descriptors.map { it.destinationClass.java.name }.toSet(),
        )
        RouteRegistry.descriptors.forEach { descriptor ->
            assertEquals(descriptor, RouteRegistry.descriptor(descriptor.route))
        }
    }

    @Test
    fun registry_hasNoDuplicateIdentityOrReachabilityMetadata() {
        assertUnique(RouteRegistry.descriptors.map { it.route })
        assertUnique(RouteRegistry.descriptors.map { it.destinationClass })
        assertUnique(RouteRegistry.descriptors.map { it.stateKey })
        assertUnique(RouteRegistry.descriptors.map { it.testTag })
        assertUnique(RouteRegistry.descriptors.flatMap { it.requestAliases })
        assertUnique(RouteRegistry.descriptors.flatMap { it.publicDeepLinks })
    }

    @Test
    fun registry_generatesStateRestoreGuardAndAliasBehavior() {
        RouteRegistry.descriptors.forEach { descriptor ->
            assertEquals(descriptor.route, navRouteFromStateKey(descriptor.stateKey))
            assertEquals(descriptor.stateKey, routeStateKey(descriptor.route))
            descriptor.requestAliases.forEach { alias ->
                assertEquals(
                    guardRoute(descriptor.route, DEFAULT_FEATURE_FLAGS),
                    destinationRequestToRoute(alias, DEFAULT_FEATURE_FLAGS),
                )
            }
            val expectedRestore = if (descriptor.restorePolicy == RestorePolicy.HOME) HomeRoute else descriptor.route
            assertEquals(expectedRestore, launchRouteForRestoredTop(descriptor.route))
        }
    }

    @Test
    fun unknownRestoredAndForgedRoutes_failClosed() {
        assertEquals(HomeRoute, navRouteFromStateKey("forged-route"))
        assertEquals(SettingsRoute, destinationRequestToRoute("forged-route", DEFAULT_FEATURE_FLAGS))
        assertFalse(routeEnabled(ForgedRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(ForgedRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(HomeRoute, launchRouteForRestoredTop(ForgedRoute))
        assertNull(RouteRegistry.descriptor(ForgedRoute))
    }

    @Test
    fun disabledRestoredRoute_isGuardedBeforeBackStackConstruction() {
        val disabledTrackpad = DEFAULT_FEATURE_FLAGS + (io.codecks.domain.features.FeatureFlag.Trackpad to false)

        assertEquals(
            SettingsRoute,
            restoredRouteFromStateKey("mouse", disabledTrackpad, RouteBuildExposure.PUBLIC),
        )
    }

    @Test
    fun publicDeepLinksAndLockscreenPolicy_areExplicitAndProductionSafe() {
        assertEquals(
            setOf("codecks://trackpad", "codecks://ai", "codecks://helper-pair"),
            RouteRegistry.descriptors.flatMap { it.publicDeepLinks }.toSet(),
        )
        assertEquals(
            setOf(MouseRoute),
            RouteRegistry.descriptors
                .filter { it.lockscreenPolicy == LockscreenRoutePolicy.RESTRICTED_POINTER }
                .map { it.route }
                .toSet(),
        )
        assertTrue(RouteRegistry.descriptors.all { it.buildExposure == RouteBuildExposure.PUBLIC })
        assertTrue(RouteRegistry.primaryDestinations(RouteBuildExposure.PUBLIC).all { it.buildExposure == RouteBuildExposure.PUBLIC })
        val internalOnly = requireNotNull(RouteRegistry.descriptor(MouseRoute)).copy(
            buildExposure = RouteBuildExposure.INTERNAL_LAB,
        )
        assertFalse(internalOnly.availableIn(RouteBuildExposure.PUBLIC))
        assertTrue(internalOnly.availableIn(RouteBuildExposure.INTERNAL_LAB))
        val internalRegistry = RouteRegistryIndex(listOf(internalOnly))
        assertNull(internalRegistry.publicDeepLinkRoute("codecks://trackpad", RouteBuildExposure.PUBLIC))
        assertEquals(
            MouseRoute,
            internalRegistry.publicDeepLinkRoute("codecks://trackpad", RouteBuildExposure.INTERNAL_LAB),
        )
        RouteRegistry.descriptors.forEach { descriptor ->
            assertEquals(
                descriptor.deepLinkEligibility == DeepLinkEligibility.PUBLIC,
                descriptor.publicDeepLinks.isNotEmpty(),
            )
        }
        assertFalse(
            RouteRegistry.descriptors.any { descriptor ->
                descriptor.destinationClass.java.name.contains(Regex("commercial|internal|lab", RegexOption.IGNORE_CASE))
            },
        )
    }

    @Test
    fun registryRoutes_areReachableFromNavHostAndTestTagsAreConsumed() {
        val mainActivity = File("src/main/java/io/codecks/MainActivity.kt").readText()
        val compositionRoot = File("src/main/java/io/codecks/AppCompositionRoot.kt").readText()
        val navigationCoordinator = File("src/main/java/io/codecks/AppCoordinators.kt").readText()
        val shell = File("src/main/java/io/codecks/ui/app/CodecksAppShell.kt").readText()

        RouteRegistry.descriptors.forEach { descriptor ->
            assertTrue(
                compositionRoot.contains("entry<${descriptor.destinationClass.java.simpleName}>"),
            )
        }
        assertTrue(shell.contains("testTag(destination.testTag)"))
        assertTrue(navigationCoordinator.contains("RouteBuildExposure.fromDistributionChannel(distributionChannel)"))
        assertTrue(compositionRoot.contains("RouteRegistry.primaryDestinations(routeBuildExposure)"))
        assertTrue(compositionRoot.contains("flags = featureFlagRepository.currentFlags"))
        assertTrue(compositionRoot.indexOf("AppNavigationCoordinator.bootstrap(") < compositionRoot.indexOf("rememberNavBackStack(navigationBootstrap.initialRoute)"))
        assertFalse(mainActivity.contains("destinationRequest = \"palette\""))
        assertFalse(mainActivity.contains("destinationRequest = \"pairing\""))
        assertFalse(mainActivity.contains("mutableStateOf(\"home\")"))
    }

    @Test
    fun registry_isTheOnlyRouteMetadataOwner() {
        val metadataConsumers = listOf(
            File("src/main/java/io/codecks/navigation/Routes.kt"),
            File("src/main/java/io/codecks/ui/app/AppNavigator.kt"),
            File("src/main/java/io/codecks/ui/app/CodecksAppShell.kt"),
        ).associateWith(File::readText)
        val metadataValues = RouteRegistry.descriptors.flatMap { descriptor ->
            listOf(descriptor.stateKey, descriptor.title, descriptor.label, descriptor.summary, descriptor.testTag) +
                descriptor.requestAliases + descriptor.publicDeepLinks
        }.filter(String::isNotEmpty).toSet()

        metadataConsumers.forEach { (file, source) ->
            metadataValues.forEach { value ->
                assertFalse("${file.name} repeats registry metadata: $value", source.contains("\"$value\""))
            }
            assertFalse("${file.name} declares route exposure metadata", source.contains("buildExposure ="))
        }
        assertFalse(File("src/main/java/io/codecks/ui/app/PrimaryTab.kt").exists())
    }

    @Test
    fun publicDeepLinks_matchManifestDeclarations() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val manifestLinks = Regex("""android:host="([^"]+)"\s+android:scheme="([^"]+)"""")
            .findAll(manifest)
            .map { match -> "${match.groupValues[2]}://${match.groupValues[1]}" }
            .toSet()
        val registryLinks = RouteRegistry.descriptors.flatMap { it.publicDeepLinks }.toSet()

        assertEquals(registryLinks, manifestLinks)
        assertEquals(
            SettingsRoute,
            RouteRegistry.publicDeepLinkRoute(
                "codecks://helper-pair?payload=value",
                RouteBuildExposure.PUBLIC,
            ),
        )
        assertNull(
            RouteRegistry.publicDeepLinkRoute(
                "codecks://trackpad?unexpected=value",
                RouteBuildExposure.PUBLIC,
            ),
        )
        assertNull(
            RouteRegistry.publicDeepLinkRoute(
                "codecks://ai?unexpected=value",
                RouteBuildExposure.PUBLIC,
            ),
        )
    }

    private fun <T> assertUnique(values: List<T>) {
        assertEquals(values.size, values.toSet().size)
    }
}

@Serializable
private data object ForgedRoute : NavKey
