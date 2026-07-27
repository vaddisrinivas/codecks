package io.codecks.ui.mouse.reactive

import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.ControlId
import io.codecks.domain.reactive.MacAppKind
import io.codecks.domain.reactive.MacApplication
import io.codecks.domain.reactive.MacId
import io.codecks.domain.reactive.MacSelection
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.Observed
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.StateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveTrackpadPresentationTest {
    @Test
    fun capsVisibleControlsAtFourAndMovesRestToOverflow() {
        val state = ReactiveTrackpadUiState(
            controls = listOf(
                control("1", "One"),
                control("2", "Two"),
                control("3", "Three"),
                control("4", "Four"),
                control("5", "Five"),
                control("6", "Six"),
            ),
        )

        val presentation = state.present()

        assertEquals(listOf("One", "Two", "Three", "Four"), presentation.visibleControls.map { it.title })
        assertEquals(listOf("Five", "Six"), presentation.overflowControls.map { it.title })
        assertTrue(presentation.hasOverflow)
        assertEquals(2, state.overflowCount())
    }

    @Test
    fun noOverflowWhenFourOrFewerControls() {
        val state = ReactiveTrackpadUiState(
            controls = listOf(
                control("1", "One"),
                control("2", "Two"),
                control("3", "Three"),
                control("4", "Four"),
            ),
        )

        val presentation = state.present()

        assertFalse(presentation.hasOverflow)
        assertEquals(0, state.overflowCount())
        assertEquals(4, presentation.visibleControls.size)
    }

    @Test
    fun summaryMentionsMoreWhenOverflowExists() {
        val state = ReactiveTrackpadUiState(
            macState = sampleMacState(),
            controls = listOf(
                control("1", "One"),
                control("2", "Two"),
                control("3", "Three"),
                control("4", "Four"),
                control("5", "Five"),
            ),
        )

        assertEquals(
            "Showing the top app-aware controls. Open More for the rest.",
            state.present().summary,
        )
    }

    @Test
    fun resultMessageMapsFailedState() {
        val state = ReactiveTrackpadUiState(
            lastResult = ReactiveActionResult.Failed("oops", retryable = true),
        )

        assertEquals("Could not run that control", state.present().resultMessage)
    }

    private fun control(id: String, title: String) = ReactiveControlUi(
        id = ControlId("control_$id"),
        title = title,
        subtitle = null,
        icon = ReactiveIcon.Generic,
        risk = ReactiveRisk.Safe,
        reversible = false,
        reason = "reason",
    )

    private fun sampleMacState(): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 1L,
        capturedAtMillis = 9_000L,
        frontApp = observed(
            MacApplication(
                bundleId = "com.google.Chrome",
                displayName = "Google Chrome",
                kind = MacAppKind.Browser,
            ),
        ),
        activeWindow = observed<io.codecks.domain.reactive.MacWindow>(null),
        displays = observed(emptyList()),
        cursor = observed<io.codecks.domain.reactive.MacCursorState>(null),
        selection = observed(MacSelection.None),
        clipboard = observed<io.codecks.domain.reactive.MacClipboardMetadata>(null),
        media = observed<io.codecks.domain.reactive.MacMediaState>(null),
        system = observed<io.codecks.domain.reactive.MacSystemState>(null),
        meeting = observed<io.codecks.domain.reactive.MacMeetingState>(null),
        latestScreenshot = observed<io.codecks.domain.reactive.MacScreenshotState>(null),
        capabilities = emptySet<CapabilityState>(),
    )

    private fun <T> observed(value: T?): Observed<T> = Observed(
        value = value,
        status = if (value == null) ObservationStatus.Unavailable else ObservationStatus.Fresh,
        observedAtMillis = 9_000L,
        source = StateSource.Helper,
    )
}
