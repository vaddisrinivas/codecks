package io.codecks.ui.home

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckTemplate

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

internal fun actionResult(
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

internal fun ActionResult.toActionEvent(): ActionEvent = ActionEvent(
    actionId = actionId,
    label = title,
    message = message,
    succeeded = succeeded,
    timestampMillis = timestampMillis,
    logs = logs,
    target = target,
    status = status,
)

internal fun DeckAction.withUniqueId(existingIds: Set<String>, suffix: String): DeckAction {
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

internal fun List<DeckAction>.withUniqueIds(
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

internal fun List<DeckAction>.firstOpenDeckSlot(): Int? =
    indexOfFirst { it.id in OPEN_DECK_SLOT_IDS }.takeIf { it >= 0 }

internal fun List<DeckAction>.openDeckSlots(required: Int): List<Int> =
    mapIndexedNotNull { index, action -> index.takeIf { action.id in OPEN_DECK_SLOT_IDS } }.take(required)

private val OPEN_DECK_SLOT_IDS = setOf("add_button", "blank")
