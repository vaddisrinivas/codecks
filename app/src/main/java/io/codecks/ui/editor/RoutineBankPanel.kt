package io.codecks.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.data.routines.BundledRoutineBank
import io.codecks.domain.DeckAction
import io.codecks.domain.routines.RoutineDefinition
import io.codecks.domain.routines.RoutineId

@Composable
internal fun RoutineBankPanel(
    slots: List<DeckAction?>,
    allActions: List<DeckAction>,
    onAssign: (Int, DeckAction) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = remember(allActions) { allActions.associateBy(DeckAction::id) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<RoutineUiUndo?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val routines = BundledRoutineBank.bank.routines

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Routine Bank", style = MaterialTheme.typography.titleMedium)
                Text("Offline starter Decks. Preview before replacing slots.", style = MaterialTheme.typography.bodySmall)
            }
            if (undo != null) {
                TextButton(onClick = {
                    val receipt = undo ?: return@TextButton
                    if (!receipt.matches(slots)) {
                        status = "Undo unavailable: Deck changed after install."
                    } else {
                        receipt.before.forEachIndexed { index, action ->
                            if (action == null) onRemove(index) else onAssign(index, action)
                        }
                        status = "Restored previous Deck slots."
                        undo = null
                    }
                }) { Text("Undo") }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(routines, key = { it.id.value }) { routine ->
                Surface(
                    onClick = { previewId = routine.id.value },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.heightIn(min = 72.dp).testTag("routine-${routine.id.value}"),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(routine.title, style = MaterialTheme.typography.labelLarge)
                        Text(
                            routine.category.name.lowercase().replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
    }

    previewId?.let { rawId ->
        val routine = BundledRoutineBank.bank.get(RoutineId(rawId)) ?: return@let
        RoutinePreviewDialog(
            routine = routine,
            slots = slots,
            byId = byId,
            onDismiss = { previewId = null },
            onInstall = { resolved ->
                val count = minOf(resolved.size, slots.size)
                val before = slots.toList()
                val installed = slots.toMutableList().also { expected ->
                    resolved.take(count).forEachIndexed { index, action -> expected[index] = action }
                }
                resolved.take(count).forEachIndexed(onAssign)
                undo = RoutineUiUndo(before, installed)
                status = "Installed ${routine.title} in $count slot(s)."
                previewId = null
            },
        )
    }
}

@Composable
private fun RoutinePreviewDialog(
    routine: RoutineDefinition,
    slots: List<DeckAction?>,
    byId: Map<String, DeckAction>,
    onDismiss: () -> Unit,
    onInstall: (List<DeckAction>) -> Unit,
) {
    val resolved = routine.actionIds.mapNotNull(byId::get)
    val missing = routine.actionIds.filterNot(byId::containsKey)
    val count = minOf(resolved.size, slots.size)
    val conflicts = slots.take(count).count { it != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview ${routine.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(routine.summary)
                Text("${resolved.size} available button(s); $conflicts occupied slot(s) will be replaced.")
                if (resolved.size > slots.size) Text("Only the first ${slots.size} buttons fit this Deck.")
                if (missing.isNotEmpty()) Text("Unavailable: ${missing.joinToString()}", color = MaterialTheme.colorScheme.error)
                resolved.take(slots.size).forEach { action ->
                    Text("• ${action.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInstall(resolved) }, enabled = missing.isEmpty() && resolved.isNotEmpty() && slots.isNotEmpty()) {
                Text("Install")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class RoutineUiUndo(
    val before: List<DeckAction?>,
    val installed: List<DeckAction?>,
) {
    fun matches(slots: List<DeckAction?>): Boolean =
        slots == installed
}
