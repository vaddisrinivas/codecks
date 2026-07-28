package io.codecks.domain.reactive.providers

import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CapabilityAvailability
import io.codecks.domain.reactive.CapabilityState
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.ControlId
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
import io.codecks.domain.reactive.ReactiveActionReceipt
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveControlPolicy
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveIdempotencyKey
import io.codecks.domain.reactive.ReactiveOperationId
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.ReactiveUndoAction
import io.codecks.domain.reactive.ReceiptId
import io.codecks.domain.reactive.SharedHidCommand
import io.codecks.domain.reactive.StateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveStateControlProvidersTest {
    private val now = 9_000L

    @Test
    fun mediaProviderEmitsPlaybackAndMuteControlsWhenMediaStateKnown() {
        val controls = MediaReactiveControlProvider().controls(
            state = state(
                media = observed(
                    MacMediaState(
                        app = MacApplication("com.spotify.client", "Spotify", MacAppKind.Media),
                        title = "Song",
                        artist = "Artist",
                        playing = true,
                        muted = false,
                    ),
                ),
                system = observed(MacSystemState(focusedSpaceLabel = null, volumePercent = 45, muted = false)),
                capabilities = setOf(
                    CapabilityState(CodecksCapability.MediaInput, CapabilityAvailability.Available),
                    CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available),
                ),
            ),
            context = ReactiveTrackpadContext(controlTtlMillis = 4_000L),
            nowMillis = now,
        )

        assertEquals(listOf("play_pause", "mute"), controls.map { it.actionId })
        assertEquals(ReactiveAction.Hid(SharedHidCommand.MediaPlayPause), controls.first().action)
        assertEquals(ReactiveControlSource.MediaState, controls.first().source)
        assertEquals(5_000L, controls.first().expiresAtMillis)
    }

    @Test
    fun mediaProviderDoesNotGuessWhenMediaUnavailable() {
        val controls = MediaReactiveControlProvider().controls(
            state = state(media = observed<MacMediaState>(null, ObservationStatus.Unavailable)),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertTrue(controls.isEmpty())
    }

    @Test
    fun windowProviderEmitsGenericWindowControlsFromKnownActiveWindow() {
        val controls = WindowReactiveControlProvider().controls(
            state = state(
                activeWindow = observed(
                    MacWindow(
                        app = MacApplication("com.apple.finder", "Finder", MacAppKind.Finder),
                        title = "Downloads",
                        role = "AXWindow",
                    ),
                ),
                capabilities = setOf(CapabilityState(CodecksCapability.WindowWrite, CapabilityAvailability.Available)),
            ),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("mission", "full_screen"), controls.map { it.actionId })
        assertTrue(controls.all { it.source == ReactiveControlSource.ConnectionState })
        assertTrue(controls.all { it.explanation.contains("Downloads") })
    }

    @Test
    fun undoProviderOnlyEmitsUnexpiredReceiptOwnedUndo() {
        val expired = receipt(
            receiptId = "expired",
            undo = ReactiveUndoAction(
                label = "Expired undo",
                action = ReactiveAction.Hid(SharedHidCommand.BrowserForward),
                expiresAtMillis = now - 1,
            ),
        )
        val current = receipt(
            receiptId = "current",
            undo = ReactiveUndoAction(
                label = "Undo back",
                action = ReactiveAction.Hid(SharedHidCommand.BrowserForward),
                expiresAtMillis = now + 1_000L,
            ),
        )
        val noUndo = receipt(receiptId = "no-undo", undo = null)

        val controls = UndoReceiptReactiveControlProvider { listOf(expired, noUndo, current) }.controls(
            state = state(),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertEquals(listOf("undo_reactive-current"), controls.map { it.actionId })
        assertEquals(ReactiveAction.Hid(SharedHidCommand.BrowserForward), controls.single().action)
        assertEquals(ReactiveControlSource.UndoReceipt, controls.single().source)
        assertEquals(ReactiveRisk.Safe, controls.single().risk)
    }

    @Test
    fun shortcutsParserCreatesTypedBoundedCatalog() {
        val catalog = AppleShortcutsListParser().parse(
            output = """
                Shortcuts
                Daily Standup
                Open Desk Setup
                Daily Standup
            """.trimIndent(),
            importedAtMillis = now,
        )

        assertEquals(listOf("Daily Standup", "Open Desk Setup"), catalog.shortcuts.map { it.displayName })
        assertEquals(2, catalog.shortcuts.map { it.stableId }.distinct().size)
        assertTrue(catalog.sourceRevision.value.startsWith("shortcuts-"))
    }

    @Test
    fun shortcutsProviderEmitsReviewedHelperControlsOnlyFromCatalog() {
        val catalog = AppleShortcutCatalog(
            shortcuts = listOf(
                AppleShortcutDefinition(
                    stableId = "shortcut-standup",
                    displayName = "Daily Standup",
                    acceptsInput = false,
                ),
            ),
            importedAtMillis = now,
            sourceRevision = ActionRevision("shortcuts-test"),
        )

        val controls = AppleShortcutsReactiveControlProvider { catalog }.controls(
            state = state(
                capabilities = setOf(CapabilityState(CodecksCapability.MacCommand, CapabilityAvailability.Available)),
            ),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        val control = controls.single()
        assertEquals("shortcut-standup", control.actionId)
        assertEquals(ReactiveControlSource.ShortcutCatalog, control.source)
        assertEquals(ReactiveRisk.Review, control.risk)
        assertEquals(ReactiveControlPolicy.RequiresReview, control.policy)
        assertEquals(
            ReactiveAction.Helper(
                actionId = "apple_shortcuts.run",
                arguments = mapOf(
                    "shortcutId" to "shortcut-standup",
                    "shortcutName" to "Daily Standup",
                    "acceptsInput" to "false",
                ),
            ),
            control.action,
        )
    }

    @Test
    fun shortcutsProviderStaysEmptyWithoutCatalog() {
        val controls = AppleShortcutsReactiveControlProvider().controls(
            state = state(),
            context = ReactiveTrackpadContext(),
            nowMillis = now,
        )

        assertTrue(controls.isEmpty())
    }

    private fun receipt(
        receiptId: String,
        undo: ReactiveUndoAction?,
    ): ReactiveActionReceipt = ReactiveActionReceipt(
        id = ReceiptId(receiptId),
        operationId = ReactiveOperationId("operation-$receiptId"),
        idempotencyKey = ReactiveIdempotencyKey("idempotency-$receiptId"),
        controlId = ControlId("reactive-$receiptId"),
        actionRevision = ActionRevision("revision-$receiptId"),
        completedAtMillis = now - 100L,
        result = ReactiveActionResult.Succeeded("ok"),
        undo = undo,
        expiresAtMillis = null,
    )

    private fun state(
        frontApp: Observed<MacApplication> = observed(
            MacApplication("com.apple.finder", "Finder", MacAppKind.Finder),
        ),
        activeWindow: Observed<MacWindow> = observed<MacWindow>(null, ObservationStatus.Unavailable),
        media: Observed<MacMediaState> = observed<MacMediaState>(null, ObservationStatus.Unavailable),
        system: Observed<MacSystemState> = observed<MacSystemState>(null, ObservationStatus.Unavailable),
        capabilities: Set<CapabilityState> = emptySet(),
    ): MacStateSnapshot = MacStateSnapshot(
        macId = MacId("123e4567-e89b-12d3-a456-426614174000"),
        snapshotRevision = 7L,
        capturedAtMillis = 1_000L,
        frontApp = frontApp,
        activeWindow = activeWindow,
        displays = observed<List<MacDisplay>>(emptyList(), ObservationStatus.Unavailable),
        cursor = observed<MacCursorState>(null, ObservationStatus.Unavailable),
        selection = observed(MacSelection.None),
        clipboard = observed<MacClipboardMetadata>(null, ObservationStatus.Unavailable),
        media = media,
        system = system,
        meeting = observed<MacMeetingState>(null, ObservationStatus.Unavailable),
        latestScreenshot = observed<MacScreenshotState>(null, ObservationStatus.Unavailable),
        capabilities = capabilities,
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
