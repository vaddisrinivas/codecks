package io.codecks.domain.contextdeck

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.StateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDeckPolicyTest {
    @Test
    fun liveRailIsClosedOrderedAndExpiresOldSignals() {
        val signals = listOf(
            signal(LiveSignalId.Music, LiveSignalValue.Active, 9_000L),
            signal(LiveSignalId.Mute, LiveSignalValue.Active, 1_000L),
            signal(LiveSignalId.Music, LiveSignalValue.Inactive, 9_500L),
        )

        val visible = ContextDeckPolicy.visibleLiveSignals(signals, nowMillis = 10_000L)

        assertEquals(LiveSignalId.entries, visible.map(LiveSignal::id))
        assertEquals(LiveSignalValue.Unknown, visible.first { it.id == LiveSignalId.Mute }.value)
        assertEquals(LiveSignalValue.Inactive, visible.first { it.id == LiveSignalId.Music }.value)
    }

    @Test
    fun appOfferIsApplyOnlyFreshAndDismissible() {
        val offer = ContextDeckPolicy.appOffer(
            bundleId = "com.google.Chrome",
            appName = "Chrome",
            templateId = "browser",
            templateTitle = "Browser",
            activeTemplateId = "custom",
            dismissedBundleIds = emptySet(),
            observedAtMillis = 1_000L,
            nowMillis = 2_000L,
        )
        assertNotNull(offer)
        assertNull(
            ContextDeckPolicy.appOffer(
                "com.google.Chrome", "Chrome", "browser", "Browser", "custom",
                setOf("com.google.Chrome"), 1_000L, 2_000L,
            ),
        )
        assertNull(
            ContextDeckPolicy.appOffer(
                "com.google.Chrome", "Chrome", "browser", "Browser", "custom",
                emptySet(), 1_000L, 40_000L,
            ),
        )
    }

    @Test
    fun modifierLayerOnlyReplacesButtonsWhileExactModifierIsPressed() {
        val base = listOf(action("base"))
        val layer = ModifierLayer("shift", listOf(action("alternate")))

        assertEquals(base, ContextDeckPolicy.actionsForLayer(base, layer, null))
        assertEquals(base, ContextDeckPolicy.actionsForLayer(base, layer, "other"))
        assertEquals(listOf("alternate"), ContextDeckPolicy.actionsForLayer(base, layer, "shift").map(DeckAction::id))
    }

    @Test
    fun contextStripIsSafeUniqueDeterministicAndCappedAtThree() {
        val candidates = listOf(
            suggestion("d", 70, safe = true),
            suggestion("b", 90, safe = true),
            suggestion("a", 90, safe = true),
            suggestion("unsafe", 100, safe = false),
            suggestion("a", 99, safe = true),
            suggestion("c", 80, safe = true),
        )

        val strip = ContextDeckPolicy.contextStrip(candidates)

        assertEquals(listOf("a", "b", "c"), strip.map { it.action.id })
    }

    @Test
    fun workflowRecordsOnlySuccessfulActionsAndProducesDisabledDraft() {
        val running = ContextDeckPolicy.startRecording(100L)
        val failed = ContextDeckPolicy.recordSuccessfulAction(running, action("failed"), succeeded = false)
        val recorded = ContextDeckPolicy.recordSuccessfulAction(failed, action("ok"), succeeded = true)
        val draft = ContextDeckPolicy.stopAsDisabledDraft(recorded)

        assertEquals(listOf("ok"), draft?.steps?.map(RecordedWorkflowStep::actionId))
        assertFalse(draft!!.enabled)
        assertNull(ContextDeckPolicy.stopAsDisabledDraft(WorkflowRecording()))
    }

    @Test
    fun multiMacRequiresExplicitConfirmationAndUniqueTargets() {
        val targets = listOf(DeviceId("mac-a"), DeviceId("mac-b"))

        assertTrue(
            ContextDeckPolicy.handoffPlan("mute", TargetSelector.AllCompatibleDevices, targets, false).isFailure,
        )
        assertTrue(
            ContextDeckPolicy.handoffPlan("mute", TargetSelector.AllCompatibleDevices, targets, true).isSuccess,
        )
        assertTrue(
            ContextDeckPolicy.handoffPlan("mute", TargetSelector.AllCompatibleDevices, listOf(targets[0], targets[0]), true).isFailure,
        )
    }

    @Test
    fun miniDeckRequiresExactlyFourDistinctSafeCommandsAndLiveHidPolicy() {
        val commands = MiniDeckCommand.entries

        assertTrue(ContextDeckPolicy.isMiniDeckAdmitted(commands))
        assertTrue(ContextDeckPolicy.canDispatchMiniDeckCommand(commands, MiniDeckCommand.Mute, true, true))
        assertFalse(ContextDeckPolicy.canDispatchMiniDeckCommand(commands, MiniDeckCommand.Mute, false, true))
        assertFalse(ContextDeckPolicy.canDispatchMiniDeckCommand(commands.take(3), MiniDeckCommand.Mute, true, true))
    }

    @Test
    fun boundedFileShelfAndWindowMapRejectInvalidShapes() {
        val item = FileDropItem("doc-1", "notes.pdf", 10, "application/pdf")
        assertEquals(10, FileDropShelf(listOf(item), DeviceId("mac"), "downloads").items.single().byteCount)
        assertTrue(runCatching { FileDropShelf(List(9) { item.copy(uriToken = "doc-$it") }, null, null) }.isFailure)
        assertTrue(
            runCatching {
                WindowSpaceMap(
                    windows = listOf(
                        WindowCard("one", "One", "Finder", "com.apple.finder", null, null, true),
                        WindowCard("two", "Two", "Finder", "com.apple.finder", null, null, true),
                    ),
                    spaces = emptyList(),
                    status = ObservationStatus.Fresh,
                    observedAtMillis = 1L,
                )
            }.isFailure,
        )
    }

    @Test
    fun analogValuesAndUnavailableTruthAreStrict() {
        assertTrue(AnalogControl(AnalogControlKind.Volume, 42, ObservationStatus.Fresh).enabled)
        assertFalse(AnalogControl(AnalogControlKind.Volume, null, ObservationStatus.Unavailable, "Mac offline").enabled)
        assertTrue(runCatching { AnalogControl(AnalogControlKind.Volume, 101, ObservationStatus.Fresh) }.isFailure)
        assertTrue(runCatching { AnalogControl(AnalogControlKind.Volume, 50, ObservationStatus.Unavailable) }.isFailure)
    }

    private fun action(id: String, safe: Boolean = true) = DeckAction(
        id = id,
        label = id,
        kind = ActionKind.Ssh,
        icon = ActionIcon.Control,
        liveSafe = safe,
    )

    private fun suggestion(id: String, score: Int, safe: Boolean) =
        ContextSuggestion(action(id, safe), score, "Local context")

    private fun signal(id: LiveSignalId, value: LiveSignalValue, observedAt: Long) = LiveSignal(
        id = id,
        value = value,
        status = ObservationStatus.Fresh,
        observedAtMillis = observedAt,
        source = StateSource.Helper,
    )
}
