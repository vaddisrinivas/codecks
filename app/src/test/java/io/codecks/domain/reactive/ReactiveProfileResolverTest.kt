package io.codecks.domain.reactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveProfileResolverTest {
    private val now = 9_000L
    private val resolver = ReactiveProfileResolver()

    @Test
    fun hiddenControlsAreMergedAndRemovedFromPins() {
        val hidden = ControlId("reactive-hidden")
        val baseHidden = ControlId("reactive-base-hidden")
        val pinned = ControlId("reactive-pinned")

        val resolved = resolver.resolve(
            preferences = ReactiveProfilePreferences(
                hiddenControlIds = setOf(hidden),
                pinnedControlIds = listOf(hidden, pinned),
            ),
            state = state(),
            baseContext = ReactiveTrackpadContext(hiddenControlIds = setOf(baseHidden)),
        )

        assertEquals(setOf(baseHidden, hidden), resolved.context.hiddenControlIds)
        assertEquals(listOf(pinned), resolved.context.pinnedControlIds)
    }

    @Test
    fun disabledProvidersAreMergedIntoContextAndOptions() {
        val resolved = resolver.resolve(
            preferences = ReactiveProfilePreferences(disabledProviderIds = setOf("finder")),
            state = state(),
            baseContext = ReactiveTrackpadContext(disabledProviderIds = setOf("browser")),
        )

        assertEquals(setOf("browser", "finder"), resolved.context.disabledProviderIds)
        assertEquals(setOf("browser", "finder"), resolved.disabledProviderIds)
    }

    @Test
    fun maxControlsAndModePreferenceAreResolved() {
        val resolved = resolver.resolve(
            preferences = ReactiveProfilePreferences(maxControls = 4),
            state = state(),
        )

        assertEquals(ReactiveTrackpadMode.Pointer, resolved.context.mode)
        assertEquals(4, resolved.context.maxControls)
        assertEquals(4, resolved.maxControls)
        assertEquals(1L, resolved.stateRevision)
    }

    @Test
    fun invalidLimitsAreRejected() {
        assertFailsWithMessage("maxControls") {
            ReactiveProfilePreferences(maxControls = 0)
        }
        assertFailsWithMessage("maxControls") {
            ReactiveProfilePreferences(maxControls = 13)
        }
        assertFailsWithMessage("controlTtlMillis") {
            ReactiveProfilePreferences(controlTtlMillis = 0L)
        }
    }

    private fun state(): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 1L,
        capturedAtMillis = now,
        frontApp = observed(
            MacApplication(
                bundleId = "com.google.Chrome",
                displayName = "Google Chrome",
                kind = MacAppKind.Browser,
            ),
        ),
        activeWindow = observed<MacWindow>(null),
        displays = observed(emptyList()),
        cursor = observed<MacCursorState>(null),
        selection = observed(MacSelection.None),
        clipboard = observed<MacClipboardMetadata>(null),
        media = observed<MacMediaState>(null),
        system = observed<MacSystemState>(null),
        meeting = observed<MacMeetingState>(null),
        latestScreenshot = observed<MacScreenshotState>(null),
        capabilities = setOf(CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available)),
    )

    private fun <T> observed(value: T?): Observed<T> = Observed(
        value = value,
        status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
        observedAtMillis = now,
        source = StateSource.Helper,
    )

    private fun assertFailsWithMessage(expected: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected IllegalArgumentException", error is IllegalArgumentException)
        assertTrue("Expected message to contain $expected, was ${error?.message}", error?.message?.contains(expected) == true)
    }
}
