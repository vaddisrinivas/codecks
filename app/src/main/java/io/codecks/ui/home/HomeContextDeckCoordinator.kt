package io.codecks.ui.home

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.toActionSpec
import io.codecks.data.ConnectionRepository
import io.codecks.data.contextdeck.ConnectionContextDeckLiveRepository
import io.codecks.data.contextdeck.ConnectionWindowSpaceRepository
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.contextdeck.AnalogControlKind
import io.codecks.domain.contextdeck.ContextDeckPolicy
import io.codecks.domain.contextdeck.ContextMacTarget
import io.codecks.domain.contextdeck.WorkflowRecording
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class HomeContextDeckCoordinator(
    private val scope: CoroutineScope,
    private val connectionRepository: ConnectionRepository,
    private val actionRunner: ActionRunner,
    private val state: () -> HomeUiState,
    private val stateFlow: MutableStateFlow<HomeUiState>,
    private val templateForApp: (String) -> Pair<String, String>?,
    private val applyTemplate: (String) -> Unit,
    private val recordRun: (ActionResult) -> Unit,
) {
    private val liveRepository = ConnectionContextDeckLiveRepository(connectionRepository)
    private val windowRepository = ConnectionWindowSpaceRepository(connectionRepository)
    private val dismissedBundles = mutableSetOf<String>()
    private var appRefreshPending = false
    private var liveRefreshPending = false

    fun refreshActiveMacApp() {
        if (!state().connectionReady || appRefreshPending) return
        appRefreshPending = true
        scope.launch {
            try {
                connectionRepository.runBundledCommand(ACTIVE_MAC_APP_CONTEXT_COMMAND)
                    .mapCatching { requireNotNull(parseActiveMacAppContext(it)) { "Mac returned invalid active-app state" } }
                    .fold(
                        onSuccess = { context ->
                            val observedAt = System.currentTimeMillis()
                            val template = templateForApp(context.appName)
                            stateFlow.update { current ->
                                current.copy(
                                    activeMacApp = context.appName,
                                    activeMacBundleId = context.bundleId,
                                    activeAppObservedAtMillis = observedAt,
                                    appDeckOffer = if (current.dynamicDeckEnabled) ContextDeckPolicy.appOffer(
                                        context.bundleId,
                                        context.appName,
                                        template?.first,
                                        template?.second,
                                        current.activeTemplateId,
                                        dismissedBundles,
                                        observedAt,
                                        observedAt,
                                    ) else null,
                                )
                            }
                        },
                        onFailure = { error ->
                            stateFlow.update {
                                it.copy(
                                    activeMacApp = null,
                                    activeMacBundleId = null,
                                    activeAppObservedAtMillis = null,
                                    appDeckOffer = null,
                                    activity = listOf(ActionEvent("active_app", "App Deck", error.message ?: "Could not read active app", false)) + it.activity.take(49),
                                )
                            }
                        },
                    )
            } finally {
                appRefreshPending = false
            }
        }
    }

    fun refreshLiveState() {
        if (!state().connectionReady || liveRefreshPending) return
        liveRefreshPending = true
        scope.launch {
            try {
                liveRepository.refresh().fold(
                    onSuccess = { snapshot -> stateFlow.update {
                        it.copy(
                            liveSignals = ContextDeckPolicy.visibleLiveSignals(snapshot.signals, snapshot.observedAtMillis),
                            analogControls = snapshot.analogControls,
                            contextDeckStatus = null,
                        )
                    } },
                    onFailure = { error -> stateFlow.update { it.copy(contextDeckStatus = error.message ?: "Live state unavailable") } },
                )
            } finally {
                liveRefreshPending = false
            }
        }
    }

    fun setAnalog(kind: AnalogControlKind, value: Int) {
        if (!state().connectionReady) return
        val bounded = value.coerceIn(0, 100)
        scope.launch {
            liveRepository.setAnalog(kind, bounded).fold(
                onSuccess = {
                    stateFlow.update { current ->
                        current.copy(
                            analogControls = current.analogControls.map { if (it.kind == kind) it.copy(valuePercent = bounded) else it },
                            contextDeckStatus = null,
                        )
                    }
                    refreshLiveState()
                },
                onFailure = { error -> stateFlow.update { it.copy(contextDeckStatus = error.message ?: "Control unavailable") } },
            )
        }
    }

    fun setModifierPressed(pressed: Boolean) {
        val modifierId = state().modifierLayer?.modifierActionId ?: return
        stateFlow.update { it.copy(pressedModifierActionId = modifierId.takeIf { pressed }) }
    }

    fun refreshWindows() {
        if (!state().connectionReady) return
        scope.launch {
            stateFlow.update { it.copy(windowSpaceStatus = "Refreshing windows…") }
            windowRepository.refresh().fold(
                onSuccess = { map -> stateFlow.update { it.copy(windowSpaceMap = map, windowSpaceStatus = null) } },
                onFailure = { error -> stateFlow.update { it.copy(windowSpaceStatus = error.message ?: "Window map unavailable") } },
            )
        }
    }

    fun focusWindow(windowId: String) {
        if (!state().connectionReady) return
        scope.launch {
            windowRepository.focus(windowId).fold(
                onSuccess = { stateFlow.update { it.copy(windowSpaceStatus = "Window focused") }; refreshWindows() },
                onFailure = { error -> stateFlow.update { it.copy(windowSpaceStatus = error.message ?: "Could not focus window") } },
            )
        }
    }

    fun refreshTargets() {
        scope.launch {
            val targets = connectionRepository.savedTargets().take(8)
                .map { ContextMacTarget(DeviceId(it.id), it.host, it.isReady) }
            stateFlow.update { it.copy(availableMacTargets = targets) }
        }
    }

    fun runOnTargets(action: DeckAction, targetIds: List<DeviceId>, confirmed: Boolean, allowDangerous: Boolean) {
        if (state().actionStatus is ActionStatus.Running) return
        val readyIds = state().availableMacTargets.filter(ContextMacTarget::ready).map(ContextMacTarget::id).toSet()
        if (targetIds.isEmpty() || targetIds.any { it !in readyIds }) {
            stateFlow.update { it.copy(actionStatus = ActionStatus.Failed(action.id, "Refresh and choose only ready Macs")) }
            return
        }
        if (action.dangerous && !allowDangerous) {
            stateFlow.update { it.copy(actionStatus = ActionStatus.Failed(action.id, "Confirm this dangerous action in its normal run flow")) }
            return
        }
        val selector = if (targetIds.size == 1) TargetSelector.SpecificDevice(targetIds.single()) else TargetSelector.AllCompatibleDevices
        val plan = ContextDeckPolicy.handoffPlan(action.id, selector, targetIds, confirmed).getOrElse { error ->
            stateFlow.update { it.copy(actionStatus = ActionStatus.Failed(action.id, error.message ?: "Choose a ready Mac")) }
            return
        }
        stateFlow.update { it.copy(actionStatus = ActionStatus.Running(action.id)) }
        scope.launch {
            val results = plan.resolvedTargetIds.map { targetId ->
                actionRunner.run(action.copy(targetSelector = TargetSelector.SpecificDevice(targetId)).toActionSpec(), allowDangerous).also(recordRun)
            }
            val succeeded = results.count { it.status == ActionResultStatus.Succeeded }
            val message = when {
                succeeded == results.size -> "${action.label} completed on $succeeded Mac${if (succeeded == 1) "" else "s"}"
                succeeded > 0 -> "${action.label} completed on $succeeded/${results.size} Macs"
                else -> results.firstOrNull()?.message ?: "${action.label} failed"
            }
            stateFlow.update {
                it.copy(
                    actionStatus = if (succeeded > 0) ActionStatus.Succeeded(action.id, message) else ActionStatus.Failed(action.id, message),
                    workflowRecording = ContextDeckPolicy.recordSuccessfulAction(it.workflowRecording, action.copy(targetSelector = selector), succeeded == results.size),
                )
            }
        }
    }

    fun startRecording() {
        if (state().workflowRecording.recording) return
        stateFlow.update { it.copy(workflowRecording = ContextDeckPolicy.startRecording(System.currentTimeMillis()), workflowDraft = null) }
    }

    fun stopRecording() {
        val recording = state().workflowRecording
        if (!recording.recording) return
        val draft = ContextDeckPolicy.stopAsDisabledDraft(recording)
        stateFlow.update {
            it.copy(
                workflowRecording = WorkflowRecording(),
                workflowDraft = draft,
                actionStatus = if (draft == null) ActionStatus.Failed("workflow_recording", "No successful actions were recorded")
                    else ActionStatus.Succeeded("workflow_recording", "Editable workflow draft ready"),
            )
        }
    }

    fun discardDraft() = stateFlow.update { it.copy(workflowDraft = null) }

    fun renameDraft(title: String) {
        val normalized = title.trim().take(96)
        if (normalized.isBlank() || normalized.any(Char::isISOControl)) return
        stateFlow.update { it.copy(workflowDraft = it.workflowDraft?.copy(title = normalized)) }
    }

    fun removeDraftStep(index: Int) = stateFlow.update {
        val draft = it.workflowDraft ?: return@update it
        if (index !in draft.steps.indices || draft.steps.size == 1) return@update it
        it.copy(workflowDraft = draft.copy(steps = draft.steps.filterIndexed { step, _ -> step != index }))
    }

    fun applyOffer() = state().appDeckOffer?.let { applyTemplate(it.templateId) } ?: Unit

    fun dismissOffer() {
        val offer = state().appDeckOffer ?: return
        dismissedBundles += offer.bundleId
        stateFlow.update { it.copy(appDeckOffer = null) }
    }
}
