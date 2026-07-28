package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacClipboardMetadata
import io.codecks.domain.reactive.MacCursorState
import io.codecks.domain.reactive.MacDisplay
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacMediaState
import io.codecks.domain.reactive.MacMeetingState
import io.codecks.domain.reactive.MacScreenshotState
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.MacSystemState
import io.codecks.domain.reactive.MacWindow
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveRequestProvenance
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.SpotlightSearchRequest
import io.codecks.domain.reactive.StateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightSftpReactiveControlProviderTest {
    private val now = 9_000L
    private val provenance = ReactiveRequestProvenance(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 7L,
        source = StateSource.Helper,
        observedAtMillis = 1_000L,
    )

    @Test
    fun emitsSpotlightOnlyWhenHelperSourceAndCapabilityAvailable() {
        val request = SpotlightSearchRequest("Quarterly Deck", provenance = provenance)
        val provider = SpotlightSftpReactiveControlProvider(spotlightRequests = { listOf(request) })

        val controls = provider.controls(
            state = state(
                source = StateSource.Helper,
                capabilities = setOf(CapabilityState(CodecksCapability.SpotlightSearch, CapabilityAvailability.Available)),
            ),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("spotlight_search"), controls.map { it.actionId })
        val action = controls.single().action
        assertTrue(action is ReactiveAction.Helper)
        action as ReactiveAction.Helper
        assertEquals("spotlight.search", action.actionId)
        assertEquals("Quarterly Deck", action.arguments["query"])
        assertEquals("8", action.arguments["maxResults"])
        assertEquals(ReactiveRisk.Private, controls.single().risk)
    }

    @Test
    fun hidesControlsForLocalCacheOrMissingCapability() {
        val request = SpotlightSearchRequest("Quarterly Deck", provenance = provenance)
        val provider = SpotlightSftpReactiveControlProvider(spotlightRequests = { listOf(request) })

        assertTrue(
            provider.controls(
                state = state(source = StateSource.LocalCache),
                context = ReactiveTrackpadContext(),
                nowMillis = now,
            ).isEmpty(),
        )
        assertTrue(
            provider.controls(
                state = state(
                    source = StateSource.Helper,
                    capabilities = setOf(
                        CapabilityState(CodecksCapability.SpotlightSearch, CapabilityAvailability.PermissionNeeded),
                    ),
                ),
                context = ReactiveTrackpadContext(),
                nowMillis = now,
            ).isEmpty(),
        )
    }

    @Test
    fun capsSpotlightControlsAndRequiresMatchingProvenance() {
        val matching = (1..5).map { index ->
            SpotlightSearchRequest("Deck $index", provenance = provenance)
        }
        val staleProvenance = provenance.copy(snapshotRevision = 6L)
        val provider = SpotlightSftpReactiveControlProvider(
            spotlightRequests = { matching + SpotlightSearchRequest("Wrong state", provenance = staleProvenance) },
        )

        val controls = provider.controls(
            state = state(
                source = StateSource.Helper,
                capabilities = setOf(CapabilityState(CodecksCapability.SpotlightSearch, CapabilityAvailability.Available)),
            ),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(3, controls.size)
        assertTrue(controls.all { it.explanation.contains("Helper") })
    }

    private fun state(
        source: StateSource,
        capabilities: Set<CapabilityState> = emptySet(),
    ): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 7L,
        capturedAtMillis = 1_000L,
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
