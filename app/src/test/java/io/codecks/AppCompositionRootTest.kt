package io.codecks

import dagger.Lazy
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.SettingsRoute
import io.codecks.ui.app.RouteBuildExposure
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompositionRootTest {
    @Test
    fun mainActivity_isThinAndContainsNoOptionalConstruction() {
        val source = File("src/main/java/io/codecks/MainActivity.kt").readText()

        assertTrue("MainActivity must remain below 400 lines", source.lineSequence().count() < 400)
        listOf(
            "ReactiveHelperSessionManager(",
            "TcpReactiveHelperTransportFactory(",
            "AndroidSecureApiKeyStore(",
            "SmartDeckViewModel(",
            "reactiveTrackpadViewModelFactory(",
        ).forEach { construction -> assertFalse(source.contains(construction)) }
        assertTrue(source.contains("Lazy<ReactiveHelperDiscovery>"))
        assertTrue(source.contains("AppFeatureBindings("))
    }

    @Test
    fun extractedProductionBoundariesStayFocusedAndBelowCeiling() {
        val files = listOf(
            "AppCompositionRoot.kt",
            "AppActionRuntime.kt",
            "AppBackupRuntime.kt",
            "AppCoordinators.kt",
            "AppDestinationSupport.kt",
            "AppFeatureBinders.kt",
            "AppHelperRuntime.kt",
            "AppInputReadinessRuntime.kt",
            "AppSettingsSupportRuntime.kt",
        )
        files.forEach { name ->
            val lines = File("src/main/java/io/codecks/$name").readLines().size
            assertTrue("$name must remain below 1000 lines; was $lines", lines < 1_000)
        }
    }

    @Test
    fun coldCoreStartup_doesNotResolveOptionalBinders() {
        var resolutions = 0
        fun <T> absent(): Lazy<T> = Lazy {
            resolutions += 1
            error("optional binder absent")
        }
        val optional = OptionalFeatureBinders(absent(), absent(), absent(), absent())

        val helperRequired = AppBootstrapCoordinator.needsHelper(
            HomeRoute,
            pendingPairing = false,
            reactiveEnabled = false,
        )
        val startup = optional.bindForStartup(helperRequired) {
            error("Home startup must not request Android context for optional construction")
        }

        assertFalse(helperRequired)
        assertFalse(AppBootstrapCoordinator.needsAi(HomeRoute))
        assertEquals(null, startup)
        assertEquals(0, resolutions)
    }

    @Test
    fun movedTrackpadDestinationPreservesDirectHidTimingAndCallbacks() {
        val source = File("src/main/java/io/codecks/AppDestinationSupport.kt").readText()
        val mouse = source.substringAfter("internal fun MouseDestination(")
            .substringBefore("private fun isLockTaskActive")

        assertTrue(mouse.contains("if (bluetoothPermissionGranted) viewModel.start()"))
        assertTrue(mouse.contains("delay(1_000L)"))
        assertTrue(mouse.contains("onMove = viewModel::move"))
        assertTrue(mouse.contains("onScroll = viewModel::scroll"))
        assertTrue(mouse.contains("onPress = viewModel::press"))
        assertTrue(mouse.contains("onReleaseButtons = viewModel::releaseButtons"))
        assertTrue(mouse.contains("onCommand = viewModel::send"))
    }

    @Test
    fun navigationCoordinator_preservesRegistryFailClosedPolicy() {
        val disabled = DEFAULT_FEATURE_FLAGS + (FeatureFlag.Trackpad to false)
        val bootstrap = AppNavigationCoordinator.bootstrap("mouse", disabled, "oss")

        assertEquals(RouteBuildExposure.PUBLIC, bootstrap.exposure)
        assertEquals(SettingsRoute, bootstrap.initialRoute)
        assertEquals(SettingsRoute, AppNavigationCoordinator.guard(MouseRoute, disabled, bootstrap.exposure))
        assertEquals(SettingsRoute, AppNavigationCoordinator.request("forged", disabled, bootstrap.exposure))
    }
}
