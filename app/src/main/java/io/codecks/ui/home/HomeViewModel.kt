package io.codecks.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codecks.core.actions.AiGeneratedContentPlanner
import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.commandRevision
import io.codecks.core.actions.toActionSpec
import io.codecks.data.ActionRepository
import io.codecks.data.ConnectionRepository
import io.codecks.data.InMemoryRunHistoryRepository
import io.codecks.data.RunHistoryRepository
import io.codecks.data.ai.AiArtifactRepository
import io.codecks.domain.ActionStatus
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.DeckAction
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckTemplate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val actions: List<DeckAction> = emptyList(),
    val deckLayout: DeckLayout = DeckLayout.Empty,
    val allActions: List<DeckAction> = emptyList(),
    val deckTemplates: List<DeckTemplate> = emptyList(),
    val activeTemplateId: String = CUSTOM_TEMPLATE_ID,
    val activeMacApp: String? = null,
    val dynamicDeckEnabled: Boolean = false,
    val activity: List<ActionEvent> = emptyList(),
    val actionStatus: ActionStatus = ActionStatus.Idle,
    val connectionReady: Boolean = false,
    val pendingDeckUndo: PendingDeckUndo? = null,
    val pendingDeckPlacement: PendingDeckPlacement? = null,
)

data class ActionEvent(
    val actionId: String,
    val label: String,
    val message: String,
    val succeeded: Boolean,
    val timestampMillis: Long = System.currentTimeMillis(),
    val logs: String = message,
    val target: String? = null,
    val status: ActionResultStatus = if (succeeded) ActionResultStatus.Succeeded else ActionResultStatus.Failed,
)

data class PendingDeckUndo(
    val slot: Int,
    val action: DeckAction,
    val layoutBefore: DeckLayout? = null,
    val artifact: AiArtifact? = null,
)

data class PendingDeckPlacement(
    val actions: List<DeckAction>,
    val statusId: String,
    val statusLabel: String,
)

sealed interface HomeActionDispatchResult {
    data object Accepted : HomeActionDispatchResult
    data object Busy : HomeActionDispatchResult
    data class Rejected(val reason: String) : HomeActionDispatchResult
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val actionRepository: ActionRepository,
    private val connectionRepository: ConnectionRepository,
    private val actionRunner: ActionRunner,
    private val runHistoryRepository: RunHistoryRepository = InMemoryRunHistoryRepository(),
    private val aiGeneratedContentPlanner: AiGeneratedContentPlanner = AiGeneratedContentPlanner(),
    private val aiArtifactRepository: AiArtifactRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            actions = actionRepository.layout().actions,
            deckLayout = actionRepository.layout(),
            allActions = actionRepository.allActions(),
            deckTemplates = actionRepository.deckTemplates(),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var favoriteLayout: DeckLayout = actionRepository.layout()
    private var aiLibraryActions: List<DeckAction> = emptyList()
    private var aiArtifactIds: Set<String> = emptySet()
    private var aiArtifactsById: Map<String, AiArtifact> = emptyMap()
    private var terminalProofReady = false
    private var connectionConfigured = false

    init {
        viewModelScope.launch {
            actionRepository.observeLayout().collect { layout ->
                favoriteLayout = layout
                _uiState.update { state ->
                    val visibleLayout = if (state.activeTemplateId == CUSTOM_TEMPLATE_ID) {
                        layout
                    } else {
                        DeckLayout.fromActions(actionRepository.actionsForTemplate(state.activeTemplateId))
                    }
                    state.copy(
                        actions = visibleLayout.actions,
                        deckLayout = visibleLayout,
                        allActions = combinedActionLibrary(layout),
                    )
                }
            }
        }
        aiArtifactRepository?.let { repository ->
            viewModelScope.launch {
                repository.artifacts.collect { artifacts ->
                    aiArtifactIds = artifacts.map(AiArtifact::id).toSet()
                    aiArtifactsById = artifacts.associateBy(AiArtifact::id)
                    aiLibraryActions = artifacts.toDeckCatalogActions()
                    _uiState.update { state ->
                        state.copy(allActions = combinedActionLibrary(favoriteLayout))
                    }
                }
            }
        }
        viewModelScope.launch {
            connectionRepository.config.collect { config ->
                connectionConfigured = config.isReady
                _uiState.update { it.copy(connectionReady = config.isReady && terminalProofReady) }
            }
        }
        viewModelScope.launch {
            runHistoryRepository.results.collect { results ->
                _uiState.update { state ->
                    state.copy(activity = results.map(ActionResult::toActionEvent))
                }
            }
        }
    }

    fun setTerminalProofReady(ready: Boolean) {
        terminalProofReady = ready
        _uiState.update { it.copy(connectionReady = connectionConfigured && ready) }
    }

    fun setDynamicDeckEnabled(enabled: Boolean) {
        _uiState.update { it.copy(dynamicDeckEnabled = enabled) }
        if (!enabled) applyTemplate(CUSTOM_TEMPLATE_ID)
    }

    fun applyTemplate(templateId: String) {
        val layout = if (templateId == CUSTOM_TEMPLATE_ID) {
            favoriteLayout
        } else {
            actionRepository.actionsForTemplate(templateId)
                .takeIf { it.isNotEmpty() }
                ?.let { DeckLayout.fromActions(it) }
                ?: favoriteLayout
        }
        _uiState.update {
            it.copy(
                activeTemplateId = templateId,
                actions = layout.actions,
                deckLayout = layout,
                actionStatus = ActionStatus.Succeeded(
                    templateId,
                    if (templateId == CUSTOM_TEMPLATE_ID) "Custom deck active" else "${templateTitle(templateId)} deck active",
                ),
            )
        }
    }

    fun refreshActiveMacApp() {
        if (!_uiState.value.connectionReady) return
        viewModelScope.launch {
            connectionRepository.runCommand(
                "osascript -e 'tell application \"System Events\" to get name of first application process whose frontmost is true'",
            ).onSuccess { appName ->
                val activeApp = appName.trim().lineSequence().firstOrNull().orEmpty()
                val matchedTemplate = actionRepository.templateForActiveApp(activeApp)
                _uiState.update { state ->
                    val nextTemplateId = if (state.dynamicDeckEnabled) {
                        matchedTemplate?.id ?: state.activeTemplateId
                    } else {
                        state.activeTemplateId
                    }
                    val nextActions = if (state.dynamicDeckEnabled && matchedTemplate != null) {
                        actionRepository.actionsForTemplate(nextTemplateId).ifEmpty { state.actions }
                    } else {
                        state.actions
                    }
                    state.copy(
                        activeMacApp = activeApp.ifBlank { null },
                        activeTemplateId = nextTemplateId,
                        actions = nextActions,
                        deckLayout = if (state.dynamicDeckEnabled && matchedTemplate != null) {
                            DeckLayout.fromActions(nextActions)
                        } else {
                            state.deckLayout
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        activeMacApp = null,
                        activity = listOf(
                            ActionEvent("active_app", "Dynamic deck", error.message ?: "Could not read active app", false),
                        ) + it.activity.take(49),
                    )
                }
            }
        }
    }

    fun run(action: DeckAction, allowDangerous: Boolean = false): HomeActionDispatchResult {
        if (_uiState.value.actionStatus is ActionStatus.Running) return HomeActionDispatchResult.Busy
        if (action.kind == io.codecks.domain.ActionKind.Ssh && !_uiState.value.connectionReady) {
            val message = "Connect your Mac first"
            val result = actionResult(action.id, action.label, message, false)
            recordRun(result)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed(action.id, message),
                    activity = listOf(result.toActionEvent()) + it.activity.take(49),
                )
            }
            return HomeActionDispatchResult.Rejected(message)
        }
        _uiState.update { it.copy(actionStatus = ActionStatus.Running(action.id)) }
        viewModelScope.launch {
            val result = actionRunner.run(action.toActionSpec(), allowDangerous = allowDangerous)
            recordRun(result)
            when (result.status) {
                ActionResultStatus.Succeeded -> {
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Succeeded(action.id, result.message),
                            activity = listOf(result.toActionEvent()) + it.activity.take(49),
                        )
                    }
                }
                ActionResultStatus.Failed,
                ActionResultStatus.RequiresConfirmation,
                ActionResultStatus.RequiresReview -> {
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Failed(action.id, result.message),
                            activity = listOf(result.toActionEvent()) + it.activity.take(49),
                        )
                    }
                }
            }
        }
        return HomeActionDispatchResult.Accepted
    }

    fun test(action: DeckAction) {
        if (_uiState.value.actionStatus is ActionStatus.Running) return
        if (action.kind == ActionKind.Ssh && !_uiState.value.connectionReady) {
            val result = actionResult(action.id, "${action.label} test", "Connect your Mac first", false)
            recordRun(result)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed(action.id, "Connect your Mac first"),
                    activity = listOf(result.toActionEvent()) + it.activity.take(49),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionStatus = ActionStatus.Running(action.id)) }
            actionRepository.test(action)
                .onSuccess { message ->
                    val verifiedAction = action.copy(
                        liveSafe = true,
                        requiresTest = false,
                        commandReview = action.commandReview.copy(checkedRevision = action.commandRevision()),
                    )
                    val verifiedLayout = favoriteLayout.replacingAction(action.id, verifiedAction)
                    updateCustomDeck(verifiedLayout, listOf(verifiedAction), pendingUndo = null)
                    actionRepository.saveLayout(verifiedLayout)
                    val result = actionResult(action.id, "${action.label} test", message, true)
                    recordRun(result)
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Succeeded(action.id, message),
                            activity = listOf(result.toActionEvent()) + it.activity.take(49),
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Action test failed"
                    val result = actionResult(action.id, "${action.label} test", message, false)
                    recordRun(result)
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Failed(action.id, message),
                            activity = listOf(result.toActionEvent()) + it.activity.take(49),
                        )
                    }
                }
        }
    }

    fun consumeResult() {
        if (_uiState.value.actionStatus !is ActionStatus.Running) {
            _uiState.update { it.copy(actionStatus = ActionStatus.Idle) }
        }
    }

    fun clearActivity() {
        viewModelScope.launch { runHistoryRepository.clear() }
        _uiState.update { it.copy(activity = emptyList()) }
    }

    fun assign(slot: Int, action: DeckAction) {
        val current = editableLayout()
        if (slot !in current.slots.indices) return
        val next = current.replacingAction(slot, action)
        updateCustomDeck(next, pendingUndo = null)
        persistDeck(next)
    }

    fun move(from: Int, to: Int) {
        val current = editableLayout()
        if (from !in current.slots.indices || to !in current.slots.indices) return
        if (from == to) return
        val movedAction = current.slots[from].action
        val next = current.swapping(from, to)
        updateCustomDeck(
            next,
            pendingUndo = PendingDeckUndo(
                slot = -1,
                action = movedAction,
                layoutBefore = current,
            ),
        )
        persistDeck(next)
        _uiState.update {
            it.copy(
                actionStatus = ActionStatus.Succeeded(
                    "deck_move",
                    "Moved ${movedAction.label} to slot ${to + 1}",
                ),
                activity = listOf(
                    ActionEvent(
                        "deck_move",
                        "Deck",
                        "Moved ${movedAction.label} to slot ${to + 1}",
                        true,
                    ),
                ) + it.activity.take(49),
            )
        }
    }

    fun resize(slot: Int, columnSpan: Int) {
        val current = editableLayout()
        if (slot !in current.slots.indices) return
        val currentSlot = current.slots[slot]
        if (currentSlot.columnSpan == columnSpan.coerceIn(1, current.columns)) return
        val next = current.resizing(slot, columnSpan)
        updateCustomDeck(
            next,
            pendingUndo = PendingDeckUndo(
                slot = -1,
                action = currentSlot.action,
                layoutBefore = current,
            ),
        )
        persistDeck(next)
        _uiState.update {
            it.copy(
                actionStatus = ActionStatus.Succeeded(
                    "deck_resize",
                    "Resized ${currentSlot.action.label}",
                ),
                activity = listOf(
                    ActionEvent(
                        "deck_resize",
                        "Deck",
                        "Resized ${currentSlot.action.label}",
                        true,
                    ),
                ) + it.activity.take(49),
            )
        }
    }

    fun remove(slot: Int) {
        val current = editableLayout()
        val blank = _uiState.value.allActions.firstOrNull { it.id == "blank" } ?: return
        val previous = current.slots.getOrNull(slot)?.action ?: return
        if (previous.id in setOf("blank", "add_button")) return
        val next = current.replacingAction(slot, blank).resizing(slot, 1)
        updateCustomDeck(next, pendingUndo = PendingDeckUndo(slot, previous))
        persistDeck(next)
        _uiState.update {
            it.copy(
                actionStatus = ActionStatus.Succeeded("deck_remove", "Removed ${previous.label}"),
                activity = listOf(ActionEvent("deck_remove", "Deck", "Removed ${previous.label}", true)) + it.activity.take(49),
            )
        }
    }

    fun removeAction(actionId: String) {
        val slot = _uiState.value.actions.indexOfFirst { it.id == actionId }
        if (slot >= 0) remove(slot)
    }

    fun forgetAction(action: DeckAction) {
        if (!action.isForgettable()) return
        val current = editableLayout()
        val blank = _uiState.value.allActions.firstOrNull { it.id == "blank" } ?: return
        val artifact = artifactIdForAction(action.id)?.let(aiArtifactsById::get)
        val next = current.copy(
            slots = current.slots.map { slot ->
                if (slot.action.id == action.id) slot.copy(action = blank, columnSpan = 1) else slot
            },
        )
        updateCustomDeck(
            next,
            pendingUndo = PendingDeckUndo(
                slot = -1,
                action = action,
                layoutBefore = current,
                artifact = artifact,
            ),
        )
        viewModelScope.launch {
            artifactIdForAction(action.id)?.let { artifactId ->
                aiArtifactRepository?.delete(artifactId)
            }
            actionRepository.saveLayout(next)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded("deck_forget", "Forgot ${action.label}"),
                    activity = listOf(ActionEvent("deck_forget", "Deck", "Forgot ${action.label}", true)) + it.activity.take(49),
                )
            }
        }
    }

    fun undoLastDeckEdit() {
        val undo = _uiState.value.pendingDeckUndo ?: return
        val next = undo.layoutBefore ?: run {
            if (undo.slot !in favoriteLayout.slots.indices) return
            favoriteLayout.replacingAction(undo.slot, undo.action)
        }
        updateCustomDeck(next, pendingUndo = null)
        viewModelScope.launch {
            undo.artifact?.let { aiArtifactRepository?.save(it) }
            actionRepository.saveLayout(next)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded("deck_undo", "Restored ${undo.action.label}"),
                    activity = listOf(ActionEvent("deck_undo", "Deck", "Restored ${undo.action.label}", true)) + it.activity.take(49),
                )
            }
        }
    }

    fun duplicateAction(action: DeckAction) {
        val current = editableLayout()
        val currentActions = current.actions
        val copy = action.withUniqueId(currentActions.map(DeckAction::id).toSet(), suffix = "copy")
        val slot = currentActions.firstOpenDeckSlot()
        if (slot == null) {
            reportDeckFull("Duplicate", "Deck is full. Remove a control or empty a slot before duplicating ${action.label}.")
            return
        }
        val next = current.replacingAction(slot, copy).resizing(slot, 1)
        updateCustomDeck(next, listOf(copy), pendingUndo = null)
        persistDeck(next)
    }

    fun pinAction(action: DeckAction) {
        val currentActions = favoriteLayout.actions
        if (currentActions.any { it.id == action.id }) {
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded(action.id, "${action.label} is already pinned"),
                    activity = listOf(ActionEvent(action.id, "Smart Deck", "${action.label} is already pinned", true)) + it.activity.take(49),
                )
            }
            return
        }
        val slot = currentActions.firstOpenDeckSlot()
        if (slot == null) {
            reportDeckFull("Smart Deck", "Deck is full. Empty a slot before pinning ${action.label}.")
            return
        }
        val next = favoriteLayout.replacingAction(slot, action).resizing(slot, 1)
        updateCustomDeck(next, listOf(action), pendingUndo = null)
        viewModelScope.launch {
            actionRepository.saveLayout(next)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded(action.id, "${action.label} pinned"),
                    activity = listOf(ActionEvent(action.id, "Smart Deck", "${action.label} pinned", true)) + it.activity.take(49),
                )
            }
        }
    }

    fun saveDeck() {
        viewModelScope.launch {
            actionRepository.saveLayout(favoriteLayout)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded("deck", "Deck saved"),
                    pendingDeckUndo = null,
                    activity = listOf(ActionEvent("deck", "Decks", "Deck saved", true)) + it.activity.take(49),
                )
            }
        }
    }

    fun saveGeneratedDraft(generated: GeneratedDraft) {
        val generatedActions = aiGeneratedContentPlanner.deckActionsFromDraft(generated).getOrElse { error ->
            val message = error.message ?: "Draft cannot be saved"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai", message),
                    activity = listOf(ActionEvent("ai", "AI Builder", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        if (generatedActions.isEmpty()) {
            val message = "Draft did not include Deck buttons"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai", message),
                    activity = listOf(ActionEvent("ai", "AI Builder", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        val current = favoriteLayout
        val normalizedActions = generatedActions.withUniqueIds(existingIds = current.actions.map(DeckAction::id).toSet()) { index ->
            "ai_${index + 1}"
        }
        val openSlots = current.actions.openDeckSlots(normalizedActions.size)
        if (openSlots.size < normalizedActions.size) {
            val fillCount = openSlots.size.coerceAtMost(normalizedActions.size)
            val immediateActions = normalizedActions.take(fillCount)
            val remainingActions = normalizedActions.drop(fillCount)
            var nextLayout = current
            openSlots.forEachIndexed { index, slot ->
                val action = immediateActions.getOrNull(index) ?: return@forEachIndexed
                nextLayout = nextLayout.replacingAction(slot, action).resizing(slot, 1)
            }
            if (openSlots.isNotEmpty() && remainingActions.isNotEmpty()) {
                viewModelScope.launch { actionRepository.saveLayout(nextLayout) }
            }
            if (remainingActions.isNotEmpty()) {
                updateCustomDeck(
                    nextLayout,
                    newActions = normalizedActions,
                    pendingDeckPlacement = PendingDeckPlacement(
                        actions = remainingActions,
                        statusId = "ai_deck",
                        statusLabel = "AI Builder",
                    ),
                    pendingUndo = null,
                )
                _uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.Failed(
                            "deck_full",
                            "Choose ${remainingActions.size} slot(s) for ${remainingActions.size} generated button(s).",
                        ),
                        activity = listOf(
                            ActionEvent(
                                "deck_full",
                                "AI Builder",
                                "Choose ${remainingActions.size} slot(s) for ${remainingActions.size} generated button(s).",
                                false,
                            ),
                        ) + it.activity.take(49),
                    )
                }
                return
            }
            if (openSlots.isNotEmpty()) {
                updateCustomDeck(nextLayout, normalizedActions)
                viewModelScope.launch {
                    actionRepository.saveLayout(nextLayout)
                }
                _uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.Succeeded(
                            "ai_deck",
                            if (normalizedActions.size == 1) {
                                "${normalizedActions.single().label} saved"
                            } else {
                                "${normalizedActions.size} Deck buttons saved"
                            },
                        )
                    )
                }
                return
            }
            val message = "Choose ${normalizedActions.size} slot(s) for ${normalizedActions.size} generated button(s)."
            updateCustomDeck(
                nextLayout,
                newActions = normalizedActions,
                pendingDeckPlacement = PendingDeckPlacement(
                    actions = normalizedActions,
                    statusId = "ai_deck",
                    statusLabel = "AI Builder",
                ),
            )
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("deck_full", message),
                    activity = listOf(ActionEvent("deck_full", "AI Builder", message, false)) + it.activity.take(49),
                )
            }
            return
        }

        val nextActions = normalizedActions
        var nextLayout = current
        openSlots.forEachIndexed { index, slot ->
            val action = nextActions[index]
            nextLayout = nextLayout.replacingAction(slot, action).resizing(slot, 1)
        }
        val message = if (nextActions.size == 1) {
            "${nextActions.single().label} saved"
        } else {
            "${nextActions.size} Deck buttons saved"
        }
        updateCustomDeck(nextLayout, nextActions)
        viewModelScope.launch {
            actionRepository.saveLayout(nextLayout)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded("ai_deck", message),
                    activity = listOf(
                        ActionEvent("ai_deck", "AI Builder", message, true),
                    ) + it.activity.take(49),
                )
            }
        }
    }

    fun saveArtifact(artifact: AiArtifact) {
        val generatedActions = aiGeneratedContentPlanner.deckActionsFromArtifact(artifact).getOrElse { error ->
            val message = error.message ?: "Draft cannot be saved"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai_draft", message),
                    activity = listOf(ActionEvent("ai_draft", "AI draft", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        if (generatedActions.isEmpty()) {
            val message = "Draft did not include Deck buttons"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai_draft", message),
                    activity = listOf(ActionEvent("ai_draft", "AI draft", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        val current = favoriteLayout
        val normalizedActions = generatedActions.withUniqueIds(existingIds = current.actions.map(DeckAction::id).toSet()) { "artifact" }
        val openSlots = current.actions.openDeckSlots(normalizedActions.size)
        if (openSlots.size < normalizedActions.size) {
            val fillCount = openSlots.size.coerceAtMost(normalizedActions.size)
            val immediateActions = normalizedActions.take(fillCount)
            val remainingActions = normalizedActions.drop(fillCount)
            var nextLayout = current
            openSlots.forEachIndexed { index, slot ->
                val action = immediateActions.getOrNull(index) ?: return@forEachIndexed
                nextLayout = nextLayout.replacingAction(slot, action).resizing(slot, 1)
            }
            if (openSlots.isNotEmpty() && remainingActions.isNotEmpty()) {
                viewModelScope.launch { actionRepository.saveLayout(nextLayout) }
            }
            if (remainingActions.isNotEmpty()) {
                updateCustomDeck(
                    nextLayout,
                    newActions = normalizedActions,
                    pendingDeckPlacement = PendingDeckPlacement(
                        actions = remainingActions,
                        statusId = "ai_draft",
                        statusLabel = "AI draft",
                    ),
                    pendingUndo = null,
                )
                _uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.Failed(
                            "deck_full",
                            "Choose ${remainingActions.size} slot(s) for ${remainingActions.size} generated button(s).",
                        ),
                        activity = listOf(
                            ActionEvent(
                                "deck_full",
                                "AI draft",
                                "Choose ${remainingActions.size} slot(s) for ${remainingActions.size} generated button(s).",
                                false,
                            ),
                        ) + it.activity.take(49),
                    )
                }
                return
            }
            if (openSlots.isNotEmpty()) {
                updateCustomDeck(nextLayout, normalizedActions)
                viewModelScope.launch {
                    actionRepository.saveLayout(nextLayout)
                }
                _uiState.update {
                    it.copy(
                        actionStatus = ActionStatus.Succeeded(
                            "ai_draft",
                            if (normalizedActions.size == 1) {
                                "${normalizedActions.single().label} saved"
                            } else {
                                "${normalizedActions.size} Deck buttons saved"
                            },
                        )
                    )
                }
                return
            }
            val message = "Choose ${normalizedActions.size} slot(s) for ${normalizedActions.size} generated button(s)."
            updateCustomDeck(
                nextLayout,
                newActions = normalizedActions,
                pendingDeckPlacement = PendingDeckPlacement(
                    actions = normalizedActions,
                    statusId = "ai_draft",
                    statusLabel = "AI draft",
                ),
            )
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("deck_full", message),
                    activity = listOf(ActionEvent("deck_full", "AI draft", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        val nextActions = normalizedActions
        var nextLayout = current
        openSlots.forEachIndexed { index, slot ->
            val action = nextActions[index]
            nextLayout = nextLayout.replacingAction(slot, action).resizing(slot, 1)
        }
        val message = if (nextActions.size == 1) {
            "${nextActions.single().label} saved"
        } else {
            "${nextActions.size} Deck buttons saved"
        }
        updateCustomDeck(nextLayout, nextActions)
        viewModelScope.launch {
            actionRepository.saveLayout(nextLayout)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded("ai_draft", message),
                    activity = listOf(ActionEvent("ai_draft", "AI draft", message, true)) + it.activity.take(49),
                )
            }
        }
    }

    fun requestArtifactPlacement(
        artifact: AiArtifact,
        preferredSlot: Int? = null,
    ) {
        val generatedActions = aiGeneratedContentPlanner.deckActionsFromArtifact(artifact).getOrElse { error ->
            val message = error.message ?: "Draft cannot be placed"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai_draft", message),
                    activity = listOf(ActionEvent("ai_draft", "AI Builder", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        if (generatedActions.isEmpty()) {
            val message = "Draft did not include Deck buttons"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai_draft", message),
                    activity = listOf(ActionEvent("ai_draft", "AI Builder", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        val current = favoriteLayout
        val normalizedActions = generatedActions.withUniqueIds(
            existingIds = current.actions.map(DeckAction::id).toSet(),
        ) { "artifact" }
        if (normalizedActions.size == 1 && preferredSlot != null && preferredSlot in current.slots.indices) {
            val action = normalizedActions.single()
            val next = current.replacingAction(preferredSlot, action).resizing(preferredSlot, 1)
            updateCustomDeck(
                next,
                newActions = normalizedActions,
                pendingUndo = PendingDeckUndo(
                    slot = -1,
                    action = action,
                    layoutBefore = current,
                ),
            )
            persistDeck(next)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded(
                        "ai_draft",
                        "${action.label} placed in slot ${preferredSlot + 1}",
                    ),
                    activity = listOf(
                        ActionEvent(
                            "ai_draft",
                            "AI Builder",
                            "${action.label} placed in slot ${preferredSlot + 1}",
                            true,
                        ),
                    ) + it.activity.take(49),
                )
            }
            return
        }
        updateCustomDeck(
            current,
            newActions = normalizedActions,
            pendingDeckPlacement = PendingDeckPlacement(
                actions = normalizedActions,
                statusId = "ai_draft",
                statusLabel = "AI Builder",
            ),
            pendingUndo = null,
        )
        _uiState.update {
            val count = normalizedActions.size
            val message = "Choose $count Deck slot${if (count == 1) "" else "s"}"
            it.copy(
                actionStatus = ActionStatus.Succeeded("ai_draft", message),
                activity = listOf(ActionEvent("ai_draft", "AI Builder", message, true)) + it.activity.take(49),
            )
        }
    }

    private fun updateCustomDeck(
        layout: DeckLayout,
        newActions: List<DeckAction> = emptyList(),
        pendingUndo: PendingDeckUndo? = null,
        pendingDeckPlacement: PendingDeckPlacement? = null,
    ) {
        favoriteLayout = layout.normalized()
        _uiState.update {
            it.copy(
                activeTemplateId = CUSTOM_TEMPLATE_ID,
                actions = favoriteLayout.actions,
                deckLayout = favoriteLayout,
                allActions = (combinedActionLibrary(favoriteLayout) + newActions).distinctBy(DeckAction::id),
                pendingDeckUndo = pendingUndo,
                pendingDeckPlacement = pendingDeckPlacement,
            )
        }
    }

    fun placePendingDeckPlacement(selectedSlots: List<Int>) {
        val pending = _uiState.value.pendingDeckPlacement ?: return
        if (selectedSlots.size != pending.actions.size) {
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed(
                        "deck_full",
                        "Choose ${pending.actions.size} slot(s) for the generated button(s).",
                    ),
                )
            }
            return
        }
        val distinctSlots = selectedSlots.distinct()
        if (distinctSlots.size != pending.actions.size) {
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed(
                        "deck_full",
                        "Choose ${pending.actions.size} different slots for the generated button(s).",
                    ),
                )
            }
            return
        }

        val current = editableLayout()
        val availableIndices = current.slots.indices.toSet()
        if (distinctSlots.any { it !in availableIndices }) {
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed(
                        "deck_full",
                        "Selected deck slot is not valid for placement.",
                    ),
                )
            }
            return
        }

        var next = current
        pending.actions.zip(distinctSlots) { action, slot ->
            next = next.replacingAction(slot, action).resizing(slot, 1)
        }
        updateCustomDeck(next, pending.actions, pendingDeckPlacement = null)
        viewModelScope.launch {
            actionRepository.saveLayout(next)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded(
                        pending.statusId,
                        "${pending.actions.size} generated button(s) placed",
                    ),
                    activity = listOf(
                        ActionEvent(
                            pending.statusId,
                            pending.statusLabel,
                            "${pending.actions.size} generated button(s) placed",
                            true,
                        ),
                    ) + it.activity.take(49),
                )
            }
        }
    }

    fun clearPendingDeckPlacement() {
        _uiState.update { it.copy(pendingDeckPlacement = null) }
    }

    private fun combinedActionLibrary(layout: DeckLayout): List<DeckAction> =
        (actionRepository.allActions() + layout.actions + aiLibraryActions).distinctBy(DeckAction::id)

    private fun editableLayout(): DeckLayout =
        if (_uiState.value.activeTemplateId == CUSTOM_TEMPLATE_ID) {
            favoriteLayout
        } else {
            _uiState.value.deckLayout.normalized()
        }

    private fun persistDeck(layout: DeckLayout) {
        viewModelScope.launch { actionRepository.saveLayout(layout) }
    }

    private fun DeckAction.isForgettable(): Boolean =
        commandOrigin != CommandOrigin.Bundled || id.startsWith("artifact_") || id.startsWith("ai_") || id.startsWith("custom_")

    private fun artifactIdForAction(actionId: String): String? =
        aiArtifactIds
            .filter { artifactId -> actionId.startsWith("${artifactId}_") }
            .maxByOrNull(String::length)

    private fun List<AiArtifact>.toDeckCatalogActions(): List<DeckAction> =
        filter { artifact -> artifact.kind == AiArtifactKind.Button || artifact.kind == AiArtifactKind.Deck }
            .flatMap { artifact ->
                aiGeneratedContentPlanner.deckActionsFromArtifact(artifact).getOrDefault(emptyList())
            }
            .distinctBy(DeckAction::id)

    private fun reportDeckFull(label: String, message: String) {
        _uiState.update {
            it.copy(
                actionStatus = ActionStatus.Failed("deck_full", message),
                activity = listOf(ActionEvent("deck_full", label, message, false)) + it.activity.take(49),
            )
        }
    }

    fun runRaw(label: String, command: String) {
        if (_uiState.value.actionStatus is ActionStatus.Running) return
        viewModelScope.launch {
            val id = "advanced"
            _uiState.update { it.copy(actionStatus = ActionStatus.Running(id)) }
            connectionRepository.runCommand(command)
                .onSuccess { message ->
                    val result = actionResult(id, label, message, true)
                    recordRun(result)
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Succeeded(id, message),
                            activity = listOf(result.toActionEvent()) + it.activity.take(49),
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Command failed"
                    val result = actionResult(id, label, message, false)
                    recordRun(result)
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Failed(id, message),
                            activity = listOf(result.toActionEvent()) + it.activity.take(49),
                        )
                    }
                }
        }
    }

    private fun recordRun(result: ActionResult) {
        viewModelScope.launch { runHistoryRepository.record(result) }
    }

    private fun templateTitle(templateId: String): String =
        actionRepository.deckTemplates().firstOrNull { it.id == templateId }?.title ?: "Template"
}

const val CUSTOM_TEMPLATE_ID = "custom"

private fun actionResult(
    id: String,
    title: String,
    message: String,
    succeeded: Boolean,
): ActionResult = ActionResult(
    actionId = id,
    title = title,
    status = if (succeeded) ActionResultStatus.Succeeded else ActionResultStatus.Failed,
    message = message,
    logs = message,
)

private fun ActionResult.toActionEvent(): ActionEvent = ActionEvent(
    actionId = actionId,
    label = title,
    message = message,
    succeeded = succeeded,
    timestampMillis = timestampMillis,
    logs = logs,
    target = target,
    status = status,
)

private fun DeckAction.withUniqueId(existingIds: Set<String>, suffix: String): DeckAction {
    if (id !in existingIds) return this
    val base = "${id}_${suffix}"
    var candidate = base
    var index = 2
    while (candidate in existingIds) {
        candidate = "${base}_$index"
        index += 1
    }
    return copy(id = candidate)
}

private fun List<DeckAction>.withUniqueIds(
    existingIds: Set<String>,
    suffixForIndex: (Int) -> String,
): List<DeckAction> {
    val usedIds = existingIds.toMutableSet()
    return mapIndexed { index, action ->
        val uniqueAction = action.withUniqueId(usedIds, suffixForIndex(index))
        usedIds += uniqueAction.id
        uniqueAction
    }
}

private fun List<DeckAction>.firstOpenDeckSlot(): Int? =
    indexOfFirst { it.id in OPEN_DECK_SLOT_IDS }.takeIf { it >= 0 }

private fun List<DeckAction>.openDeckSlots(required: Int): List<Int> =
    mapIndexedNotNull { index, action -> index.takeIf { action.id in OPEN_DECK_SLOT_IDS } }.take(required)

private val OPEN_DECK_SLOT_IDS = setOf("add_button", "blank")
