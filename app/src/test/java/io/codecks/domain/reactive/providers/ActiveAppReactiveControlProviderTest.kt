package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveCatalogControlSpec
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.StateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveAppReactiveControlProviderTest {
    private val now = 9_000L

    @Test
    fun browserContextPromotesBrowserControls() {
        val provider = ActiveAppReactiveControlProvider(
            mappings = listOf(
                ReactiveAppActionMapping(
                    appTokens = setOf("chrome", "safari"),
                    actionTokens = setOf("reload", "back", "forward"),
                    reason = "Useful while browser is active",
                    score = 18,
                ),
            ),
            controls = listOf(
                ReactiveCatalogControlSpec(
                    actionId = "reload",
                    title = "Reload",
                    subtitle = "Refresh current tab",
                    icon = ReactiveIcon.Reload,
                    requiredCapabilities = setOf(CodecksCapability.MacCommand),
                ),
                ReactiveCatalogControlSpec(
                    actionId = "browser_back",
                    title = "Back",
                    subtitle = "Go back",
                    icon = ReactiveIcon.ArrowLeft,
                    requiredCapabilities = setOf(CodecksCapability.KeyboardInput),
                ),
            ),
        )

        val controls = provider.controls(
            state = browserState(),
            context = ReactiveTrackpadContext(controlTtlMillis = 4_000L),
            nowMillis = now,
        )

        assertEquals(2, controls.size)
        assertEquals(ReactiveAction.ExistingCatalog("reload"), controls.first().action)
        assertEquals(5_000L, controls.first().expiresAtMillis)
        assertTrue(controls.all { it.reason.contains("browser") })
    }

    @Test
    fun missingFrontAppProducesNoControls() {
        val provider = ActiveAppReactiveControlProvider(
            mappings = listOf(
                ReactiveAppActionMapping(
                    appTokens = setOf("chrome"),
                    actionTokens = setOf("reload"),
                    reason = "Useful while browser is active",
                    score = 18,
                ),
            ),
            controls = listOf(
                ReactiveCatalogControlSpec(actionId = "reload", title = "Reload"),
            ),
        )

        val controls = provider.controls(
            state = browserState(frontApp = observed<MacApplication>(null, ObservationStatus.Unavailable)),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertTrue(controls.isEmpty())
    }

    @Test
    fun revisionsAndIdsStayDeterministicForSameInputs() {
        val provider = ActiveAppReactiveControlProvider(
            mappings = listOf(
                ReactiveAppActionMapping(
                    appTokens = setOf("chrome"),
                    actionTokens = setOf("reload"),
                    reason = "Useful while browser is active",
                    score = 18,
                ),
            ),
            controls = listOf(
                ReactiveCatalogControlSpec(
                    actionId = "reload",
                    title = "Reload",
                    subtitle = "Refresh current tab",
                    icon = ReactiveIcon.Reload,
                    requiredCapabilities = setOf(CodecksCapability.MacCommand),
                ),
            ),
        )

        val first = provider.controls(browserState(), ReactiveTrackpadContext(), now).single()
        val second = provider.controls(browserState(), ReactiveTrackpadContext(), now).single()

        assertEquals(first.id, second.id)
        assertEquals(first.actionRevision, second.actionRevision)
    }

    @Test
    fun defaultProviderEmitsBundledBrowserControls() {
        val controls = ActiveAppReactiveControlProvider().controls(
            state = browserState(),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("browser_back", "browser_reload"), controls.map { it.actionId })
        assertTrue(controls.all { it.providerId == "active-app" })
        assertTrue(controls.all { it.explanation.contains("Google Chrome") })
    }

    private fun browserState(
        frontApp: Observed<MacApplication> = observed(
            MacApplication(
                bundleId = "com.google.Chrome",
                displayName = "Google Chrome",
                kind = MacAppKind.Browser,
            ),
            ObservationStatus.Fresh,
        ),
    ): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 7L,
        capturedAtMillis = 1_000L,
        frontApp = frontApp,
        activeWindow = observed(null, ObservationStatus.Unavailable),
        displays = observed(emptyList(), ObservationStatus.Unavailable),
        cursor = observed(null, ObservationStatus.Unavailable),
        selection = observed(MacSelection.None, ObservationStatus.Unavailable),
        clipboard = observed(null, ObservationStatus.Unavailable),
        media = observed(null, ObservationStatus.Unavailable),
        system = observed(null, ObservationStatus.Unavailable),
        meeting = observed(null, ObservationStatus.Unavailable),
        latestScreenshot = observed(null, ObservationStatus.Unavailable),
        capabilities = emptySet(),
    )

    private fun <T> observed(value: T?, status: ObservationStatus): Observed<T> = Observed(
        value = value,
        status = status,
        observedAtMillis = 900L,
        source = StateSource.Helper,
    )
}
