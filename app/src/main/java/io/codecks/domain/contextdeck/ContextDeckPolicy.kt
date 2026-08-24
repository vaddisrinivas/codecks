package io.codecks.domain.contextdeck

import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.reactive.ObservationStatus

object ContextDeckPolicy {
    const val LIVE_STATE_MAX_AGE_MILLIS: Long = 5_000L
    const val APP_OFFER_MAX_AGE_MILLIS: Long = 30_000L

    fun visibleLiveSignals(
        signals: List<LiveSignal>,
        nowMillis: Long,
    ): List<LiveSignal> = LiveSignalId.entries.map { id ->
        signals.lastOrNull { it.id == id }
            ?.takeIf { signal -> signal.isCurrent(nowMillis) }
            ?: LiveSignal(
                id = id,
                value = LiveSignalValue.Unknown,
                status = ObservationStatus.Unavailable,
                observedAtMillis = null,
                source = null,
            )
    }

    fun appOffer(
        bundleId: String?,
        appName: String?,
        templateId: String?,
        templateTitle: String?,
        activeTemplateId: String,
        dismissedBundleIds: Set<String>,
        observedAtMillis: Long,
        nowMillis: Long,
    ): AppDeckOffer? {
        if (bundleId.isNullOrBlank() || appName.isNullOrBlank()) return null
        if (templateId.isNullOrBlank() || templateTitle.isNullOrBlank()) return null
        if (templateId == activeTemplateId || bundleId in dismissedBundleIds) return null
        if (observedAtMillis < 0 || nowMillis < observedAtMillis) return null
        if (nowMillis - observedAtMillis > APP_OFFER_MAX_AGE_MILLIS) return null
        return runCatching {
            AppDeckOffer(bundleId, appName, templateId, templateTitle, observedAtMillis)
        }.getOrNull()
    }

    fun actionsForLayer(
        base: List<DeckAction>,
        layer: ModifierLayer?,
        pressedModifierActionId: String?,
    ): List<DeckAction> = if (layer != null && layer.modifierActionId == pressedModifierActionId) {
        layer.actions
    } else {
        base
    }

    fun contextStrip(candidates: List<ContextSuggestion>): List<ContextSuggestion> = candidates
        .filter { it.action.liveSafe || it.action.kind == ActionKind.Local }
        .sortedWith(compareByDescending<ContextSuggestion> { it.score }.thenBy { it.action.id })
        .distinctBy { it.action.id }
        .take(MAX_CONTEXT_SUGGESTIONS)

    fun recordSuccessfulAction(
        recording: WorkflowRecording,
        action: DeckAction,
        succeeded: Boolean,
    ): WorkflowRecording {
        if (!recording.recording || !succeeded || recording.steps.size >= WorkflowRecording.MAX_STEPS) return recording
        return recording.copy(
            steps = recording.steps + RecordedWorkflowStep(action.id, action.targetSelector),
        )
    }

    fun startRecording(nowMillis: Long): WorkflowRecording {
        require(nowMillis >= 0) { "Workflow start time must be non-negative." }
        return WorkflowRecording(recording = true, startedAtMillis = nowMillis)
    }

    fun stopAsDisabledDraft(recording: WorkflowRecording): WorkflowDraft? =
        recording.steps.takeIf { it.isNotEmpty() }?.let { steps ->
            WorkflowDraft(
                id = "recorded-${recording.startedAtMillis}",
                title = "Recorded workflow",
                steps = steps,
                enabled = false,
            )
        }

    fun handoffPlan(
        actionId: String,
        selector: TargetSelector,
        readyTargetIds: List<DeviceId>,
        explicitMultiTargetConfirmation: Boolean,
    ): Result<HandoffPlan> = runCatching {
        HandoffPlan(
            actionId = actionId,
            selector = selector,
            resolvedTargetIds = readyTargetIds,
            explicitMultiTargetConfirmation = explicitMultiTargetConfirmation,
        )
    }

    fun isMiniDeckAdmitted(commands: List<MiniDeckCommand>): Boolean =
        commands.size == MINI_DECK_SIZE && commands.distinct().size == MINI_DECK_SIZE

    fun canDispatchMiniDeckCommand(
        admittedCommands: List<MiniDeckCommand>,
        command: MiniDeckCommand,
        restrictedPointerAllowed: Boolean,
        hidConnected: Boolean,
    ): Boolean = restrictedPointerAllowed && hidConnected &&
        isMiniDeckAdmitted(admittedCommands) && command in admittedCommands

    private fun LiveSignal.isCurrent(nowMillis: Long): Boolean {
        val observed = observedAtMillis ?: return false
        return nowMillis >= observed && nowMillis - observed <= LIVE_STATE_MAX_AGE_MILLIS &&
            status in setOf(ObservationStatus.Fresh, ObservationStatus.Stale)
    }

    const val MAX_CONTEXT_SUGGESTIONS = 3
    const val MINI_DECK_SIZE = 4
}

data class WorkflowDraft(
    val id: String,
    val title: String,
    val steps: List<RecordedWorkflowStep>,
    val enabled: Boolean,
) {
    init {
        require(id.isSafeToken()) { "Workflow draft id is invalid." }
        require(title.isSafeLabel()) { "Workflow draft title is invalid." }
        require(steps.isNotEmpty() && steps.size <= WorkflowRecording.MAX_STEPS) { "Workflow draft steps are invalid." }
        require(!enabled) { "Recorded workflows must begin disabled." }
    }
}
