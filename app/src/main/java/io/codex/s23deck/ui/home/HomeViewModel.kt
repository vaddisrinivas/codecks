package io.codex.s23deck.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codex.s23deck.core.actions.AiGeneratedContentPlanner
import io.codex.s23deck.core.actions.ActionResultStatus
import io.codex.s23deck.core.actions.ActionRunner
import io.codex.s23deck.core.actions.toActionSpec
import io.codex.s23deck.data.ActionRepository
import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.domain.ActionStatus
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.domain.ai.AiArtifact
import io.codex.s23deck.domain.ai.GeneratedDraft
import io.codex.s23deck.domain.deck.DeckLayout
import io.codex.s23deck.domain.deck.DeckTemplate
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
)

data class ActionEvent(
    val actionId: String,
    val label: String,
    val message: String,
    val succeeded: Boolean,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class PendingDeckUndo(
    val slot: Int,
    val action: DeckAction,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val actionRepository: ActionRepository,
    private val connectionRepository: ConnectionRepository,
    private val actionRunner: ActionRunner,
    private val aiGeneratedContentPlanner: AiGeneratedContentPlanner = AiGeneratedContentPlanner(),
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
                        allActions = (state.allActions + layout.actions).distinctBy(DeckAction::id),
                    )
                }
            }
        }
        viewModelScope.launch {
            connectionRepository.config.collect { config ->
                _uiState.update { it.copy(connectionReady = config.isReady) }
            }
        }
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

    fun run(action: DeckAction) {
        if (_uiState.value.actionStatus is ActionStatus.Running) return
        if (action.kind == io.codex.s23deck.domain.ActionKind.Ssh && !_uiState.value.connectionReady) {
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed(action.id, "Connect your Mac first"),
                    activity = listOf(
                        ActionEvent(action.id, action.label, "Connect your Mac first", false),
                    ) + it.activity.take(49),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionStatus = ActionStatus.Running(action.id)) }
            val result = actionRunner.run(action.toActionSpec(), allowDangerous = false)
            when (result.status) {
                ActionResultStatus.Succeeded -> {
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Succeeded(action.id, result.message),
                            activity = listOf(
                                ActionEvent(action.id, action.label, result.message, true),
                            ) + it.activity.take(49),
                        )
                    }
                }
                ActionResultStatus.Failed,
                ActionResultStatus.RequiresConfirmation -> {
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Failed(action.id, result.message),
                            activity = listOf(
                                ActionEvent(action.id, action.label, result.message, false),
                            ) + it.activity.take(49),
                        )
                    }
                }
            }
        }
    }

    fun test(action: DeckAction) {
        if (_uiState.value.actionStatus is ActionStatus.Running) return
        if (action.kind == ActionKind.Ssh && !_uiState.value.connectionReady) {
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed(action.id, "Connect your Mac first"),
                    activity = listOf(
                        ActionEvent(action.id, "${action.label} test", "Connect your Mac first", false),
                    ) + it.activity.take(49),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(actionStatus = ActionStatus.Running(action.id)) }
            actionRepository.test(action)
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Succeeded(action.id, message),
                            activity = listOf(
                                ActionEvent(action.id, "${action.label} test", message, true),
                            ) + it.activity.take(49),
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Action test failed"
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Failed(action.id, message),
                            activity = listOf(
                                ActionEvent(action.id, "${action.label} test", message, false),
                            ) + it.activity.take(49),
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
        _uiState.update { it.copy(activity = emptyList()) }
    }

    fun assign(slot: Int, action: DeckAction) {
        if (slot !in favoriteLayout.slots.indices) return
        val next = favoriteLayout.replacingAction(slot, action)
        updateCustomDeck(next, pendingUndo = null)
    }

    fun move(from: Int, to: Int) {
        if (from !in favoriteLayout.slots.indices || to !in favoriteLayout.slots.indices) return
        val next = favoriteLayout.swapping(from, to)
        updateCustomDeck(next, pendingUndo = null)
    }

    fun resize(slot: Int, columnSpan: Int) {
        if (slot !in favoriteLayout.slots.indices) return
        updateCustomDeck(favoriteLayout.resizing(slot, columnSpan), pendingUndo = null)
    }

    fun remove(slot: Int) {
        val blank = _uiState.value.allActions.firstOrNull { it.id == "blank" } ?: return
        val previous = favoriteLayout.slots.getOrNull(slot)?.action ?: return
        if (previous.id in setOf("blank", "add_button")) return
        val next = favoriteLayout.replacingAction(slot, blank).resizing(slot, 1)
        updateCustomDeck(next, pendingUndo = PendingDeckUndo(slot, previous))
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

    fun undoLastDeckEdit() {
        val undo = _uiState.value.pendingDeckUndo ?: return
        if (undo.slot !in favoriteLayout.slots.indices) return
        val next = favoriteLayout.replacingAction(undo.slot, undo.action)
        updateCustomDeck(next, pendingUndo = null)
        _uiState.update {
            it.copy(
                actionStatus = ActionStatus.Succeeded("deck_undo", "Restored ${undo.action.label}"),
                activity = listOf(ActionEvent("deck_undo", "Deck", "Restored ${undo.action.label}", true)) + it.activity.take(49),
            )
        }
    }

    fun duplicateAction(action: DeckAction) {
        val currentActions = favoriteLayout.actions
        val copy = action.withUniqueId(currentActions.map(DeckAction::id).toSet(), suffix = "copy")
        val slot = currentActions.firstOpenDeckSlot()
        if (slot == null) {
            reportDeckFull("Duplicate", "Deck is full. Remove a control or empty a slot before duplicating ${action.label}.")
            return
        }
        updateCustomDeck(favoriteLayout.replacingAction(slot, copy).resizing(slot, 1), listOf(copy), pendingUndo = null)
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
                    activity = listOf(ActionEvent("ai", "AI creator", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        if (generatedActions.isEmpty()) {
            val message = "Draft did not include deck controls"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai", message),
                    activity = listOf(ActionEvent("ai", "AI creator", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        val current = favoriteLayout
        val slots = current.actions.openDeckSlots(generatedActions.size)
        if (slots.size < generatedActions.size) {
            reportDeckFull(
                label = "AI creator",
                message = "Deck needs ${generatedActions.size} empty slot(s). Empty slots before saving this draft.",
            )
            return
        }
        val nextActions = generatedActions.withUniqueIds(existingIds = current.actions.map(DeckAction::id).toSet()) { index ->
            "ai_${index + 1}"
        }
        var nextLayout = current
        nextActions.forEachIndexed { index, action ->
            nextLayout = nextLayout.replacingAction(slots[index], action).resizing(slots[index], 1)
        }
        val message = if (nextActions.size == 1) {
            "${nextActions.single().label} saved"
        } else {
            "${nextActions.size} deck controls saved"
        }
        updateCustomDeck(nextLayout, nextActions)
        viewModelScope.launch {
            actionRepository.saveLayout(nextLayout)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded("ai_deck", message),
                    activity = listOf(
                        ActionEvent("ai_deck", "AI creator", message, true),
                    ) + it.activity.take(49),
                )
            }
        }
    }

    fun saveArtifact(artifact: AiArtifact) {
        val generatedActions = aiGeneratedContentPlanner.deckActionsFromArtifact(artifact).getOrElse { error ->
            val message = error.message ?: "Artifact cannot be saved"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai_artifact", message),
                    activity = listOf(ActionEvent("ai_artifact", "AI artifact", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        if (generatedActions.isEmpty()) {
            val message = "Artifact did not include deck controls"
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Failed("ai_artifact", message),
                    activity = listOf(ActionEvent("ai_artifact", "AI artifact", message, false)) + it.activity.take(49),
                )
            }
            return
        }
        val current = favoriteLayout
        val slots = current.actions.openDeckSlots(generatedActions.size)
        if (slots.size < generatedActions.size) {
            reportDeckFull(
                label = "AI artifact",
                message = "Deck needs ${generatedActions.size} empty slot(s). Empty slots before saving this artifact.",
            )
            return
        }
        val nextActions = generatedActions.withUniqueIds(existingIds = current.actions.map(DeckAction::id).toSet()) { "artifact" }
        var nextLayout = current
        nextActions.forEachIndexed { index, action ->
            nextLayout = nextLayout.replacingAction(slots[index], action).resizing(slots[index], 1)
        }
        val message = if (nextActions.size == 1) {
            "${nextActions.single().label} saved"
        } else {
            "${nextActions.size} deck controls saved"
        }
        updateCustomDeck(nextLayout, nextActions)
        viewModelScope.launch {
            actionRepository.saveLayout(nextLayout)
            _uiState.update {
                it.copy(
                    actionStatus = ActionStatus.Succeeded("ai_artifact", message),
                    activity = listOf(ActionEvent("ai_artifact", "AI artifact", message, true)) + it.activity.take(49),
                )
            }
        }
    }

    private fun updateCustomDeck(
        layout: DeckLayout,
        newActions: List<DeckAction> = emptyList(),
        pendingUndo: PendingDeckUndo? = null,
    ) {
        favoriteLayout = layout.normalized()
        _uiState.update {
            it.copy(
                activeTemplateId = CUSTOM_TEMPLATE_ID,
                actions = favoriteLayout.actions,
                deckLayout = favoriteLayout,
                allActions = (it.allActions + favoriteLayout.actions + newActions).distinctBy(DeckAction::id),
                pendingDeckUndo = pendingUndo,
            )
        }
    }

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
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Succeeded(id, message),
                            activity = listOf(ActionEvent(id, label, message, true)) + it.activity.take(49),
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "Command failed"
                    _uiState.update {
                        it.copy(
                            actionStatus = ActionStatus.Failed(id, message),
                            activity = listOf(ActionEvent(id, label, message, false)) + it.activity.take(49),
                        )
                    }
                }
        }
    }

    private fun templateTitle(templateId: String): String =
        actionRepository.deckTemplates().firstOrNull { it.id == templateId }?.title ?: "Template"
}

const val CUSTOM_TEMPLATE_ID = "custom"

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
