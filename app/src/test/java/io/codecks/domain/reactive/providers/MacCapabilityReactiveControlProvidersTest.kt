package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.AccessibilityDiscoveryRequest
import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacClipboardMetadata
import io.codecks.domain.reactive.MacCursorState
import io.codecks.domain.reactive.MacDisplay
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacMeetingState
import io.codecks.domain.reactive.MacMediaState
import io.codecks.domain.reactive.MacScreenshotState
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.MacSystemState
import io.codecks.domain.reactive.MacWindow
import io.codecks.domain.reactive.MonitorBrightnessRequest
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveRequestProvenance
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.StateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacCapabilityReactiveControlProvidersTest {
    private val now = 10_000L
    private val provenance = ReactiveRequestProvenance(
        macId = MacId("mac-1"),
        snapshotRevision = 7L,
        source = StateSource.Helper,
        observedAtMillis = 9_000L,
    )

    @Test
    fun brightnessProviderRequiresDedicatedCapabilityAndMatchingProvenance() {
        val request = MonitorBrightnessRequest(
            displayId = "external-dell-1",
            percent = 45,
            provenance = provenance,
            adapterId = "betterdisplay",
        )
        val provider = MonitorBrightnessReactiveControlProvider { listOf(request) }

        assertTrue(provider.controls(state(), ReactiveTrackpadContext(), now).isEmpty())

        val controls = provider.controls(
            state(capabilities = setOf(CapabilityState(CodecksCapability.MonitorBrightness, CapabilityAvailability.Available))),
            ReactiveTrackpadContext(),
            now,
        )

        val control = controls.single()
        assertEquals("monitor_brightness_set", control.actionId)
        assertEquals(ReactiveRisk.Review, control.risk)
        assertEquals(
            ReactiveAction.Helper(
                actionId = "monitor_brightness.set",
                arguments = mapOf(
                    "displayId" to "external-dell-1",
                    "percent" to "45",
                    "adapterId" to "betterdisplay",
                ),
            ),
            control.action,
        )
    }

    @Test
    fun accessibilityDiscoveryProviderIsPrivateAndHelperOnly() {
        val request = AccessibilityDiscoveryRequest(
            frontAppBundleId = "com.apple.finder",
            provenance = provenance,
            maxActions = 5,
        )
        val provider = AccessibilityDiscoveryReactiveControlProvider { listOf(request) }

        assertTrue(
            provider.controls(
                state(
                    source = StateSource.SshProbe,
                    capabilities = setOf(
                        CapabilityState(CodecksCapability.AccessibilityDiscovery, CapabilityAvailability.Available),
                    ),
                ),
                ReactiveTrackpadContext(),
                now,
            ).isEmpty(),
        )

        val controls = provider.controls(
            state(
                capabilities = setOf(
                    CapabilityState(CodecksCapability.AccessibilityDiscovery, CapabilityAvailability.Available),
                ),
            ),
            ReactiveTrackpadContext(),
            now,
        )

        val control = controls.single()
        assertEquals("accessibility_discover_actions", control.actionId)
        assertEquals(ReactiveRisk.Private, control.risk)
        assertEquals(
            ReactiveAction.Helper(
                actionId = "accessibility.discover",
                arguments = mapOf(
                    "frontAppBundleId" to "com.apple.finder",
                    "maxActions" to "5",
                ),
            ),
            control.action,
        )
    }

    @Test
    fun staleStateDisablesCapabilityProviders() {
        val request = MonitorBrightnessRequest(
            displayId = "external-dell-1",
            percent = 45,
            provenance = provenance,
            adapterId = "betterdisplay",
        )

        val controls = MonitorBrightnessReactiveControlProvider { listOf(request) }.controls(
            state(
                stale = true,
                capabilities = setOf(CapabilityState(CodecksCapability.MonitorBrightness, CapabilityAvailability.Available)),
            ),
            ReactiveTrackpadContext(),
            now,
        )

        assertTrue(controls.isEmpty())
    }

    private fun state(
        source: StateSource = StateSource.Helper,
        stale: Boolean = false,
        capabilities: Set<CapabilityState> = emptySet(),
    ): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("mac-1"),
        snapshotRevision = 7L,
        capturedAtMillis = 9_000L,
        frontApp = observed(MacApplication("com.apple.finder", "Finder", MacAppKind.Finder)),
        activeWindow = observed<MacWindow>(null, ObservationStatus.Unavailable),
        displays = observed<List<MacDisplay>>(emptyList(), ObservationStatus.Unavailable),
        cursor = observed<MacCursorState>(null, ObservationStatus.Unavailable),
        selection = observed(MacSelection.None),
        clipboard = observed<MacClipboardMetadata>(null, ObservationStatus.Unavailable),
        media = observed<MacMediaState>(null, ObservationStatus.Unavailable),
        system = observed<MacSystemState>(null, ObservationStatus.Unavailable),
        meeting = observed<MacMeetingState>(null, ObservationStatus.Unavailable),
        latestScreenshot = observed<MacScreenshotState>(null, ObservationStatus.Unavailable),
        capabilities = capabilities,
        source = source,
        stale = stale,
    )

    private fun <T> observed(
        value: T?,
        status: ObservationStatus = ObservationStatus.Fresh,
    ): Observed<T> = Observed(
        value = value,
        status = status,
        observedAtMillis = now - 100L,
        source = StateSource.Helper,
    )
}
