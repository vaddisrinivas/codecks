package io.codecks.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.domain.DeckAction
import io.codecks.domain.contextdeck.ContextMacTarget
import io.codecks.domain.contextdeck.WindowSpaceMap
import io.codecks.domain.contextdeck.WorkflowDraft
import io.codecks.domain.contextdeck.WorkflowRecording
import io.codecks.domain.device.DeviceId

@Composable
internal fun ContextDeckToolsDialog(
    windowSpaceMap: WindowSpaceMap?,
    windowSpaceStatus: String?,
    workflowRecording: WorkflowRecording,
    workflowDraft: WorkflowDraft?,
    targets: List<ContextMacTarget>,
    onRefreshWindows: () -> Unit,
    onFocusWindow: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRenameDraft: (String) -> Unit,
    onRemoveDraftStep: (Int) -> Unit,
    onDiscardDraft: () -> Unit,
    onPickFileDrop: (DeviceId) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Context tools") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 560.dp).testTag("context-tools-dialog"),
            ) {
                item {
                    SectionTitle("Window map")
                    Text(
                        windowSpaceMap?.spaces?.firstOrNull()?.label ?: windowSpaceStatus ?: "Current Space not read yet",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onRefreshWindows) { Text("Refresh") }
                }
                windowSpaceMap?.let { map ->
                    items(map.windows.size, key = { map.windows[it].id }) { index ->
                        val window = map.windows[index]
                        Surface(
                            onClick = { onFocusWindow(window.id) },
                            enabled = !window.focused,
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().testTag("window-${window.id}"),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    if (window.focused) "${window.appName} · Focused" else window.appName,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(window.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                item {
                    SectionTitle("Workflow recorder")
                    Text(
                        if (workflowRecording.recording) {
                            "Recording successful Deck actions · ${workflowRecording.steps.size}/24"
                        } else {
                            "Records approved, successful actions into a disabled draft."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = if (workflowRecording.recording) onStopRecording else onStartRecording) {
                        Text(if (workflowRecording.recording) "Stop and make draft" else "Start recording")
                    }
                }
                workflowDraft?.let { draft ->
                    item {
                        OutlinedTextField(
                            value = draft.title,
                            onValueChange = onRenameDraft,
                            label = { Text("Draft name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Disabled until you review and save it.", style = MaterialTheme.typography.bodySmall)
                    }
                    itemsIndexed(draft.steps, key = { index, step -> "${step.actionId}-$index" }) { index, step ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${index + 1}. ${step.actionId}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRemoveDraftStep(index) }, enabled = draft.steps.size > 1) {
                                Text("Remove")
                            }
                        }
                    }
                    item { TextButton(onClick = onDiscardDraft) { Text("Discard draft") } }
                }
                item {
                    SectionTitle("Macs")
                    Text(
                        if (targets.isEmpty()) "No saved Macs" else "${targets.count { it.ready }} of ${targets.size} ready",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                targets.filter(ContextMacTarget::ready).forEach { target ->
                    item("file-drop-${target.id.value}") {
                        TextButton(
                            onClick = { onPickFileDrop(target.id) },
                            modifier = Modifier.fillMaxWidth().testTag("file-drop-${target.id.value}"),
                        ) {
                            Text("Send files to ${target.displayName}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
internal fun MultiMacHandoffDialog(
    action: DeckAction,
    targets: List<ContextMacTarget>,
    selectedTargetIds: List<DeviceId>,
    onTargetToggle: (DeviceId) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    val readyTargets = remember(targets) { targets.filter(ContextMacTarget::ready) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run ${action.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose each Mac explicitly. Runs happen sequentially.")
                if (readyTargets.isEmpty()) Text("No ready Macs.")
                readyTargets.forEach { target ->
                    val checked = target.id in selectedTargetIds
                    Surface(
                        onClick = { onTargetToggle(target.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().testTag("handoff-${target.id.value}"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onTargetToggle(target.id) })
                            Text(target.displayName)
                        }
                    }
                }
                if (selectedTargetIds.size > 1) {
                    Text("This will run on ${selectedTargetIds.size} Macs.", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRun, enabled = selectedTargetIds.isNotEmpty()) {
                Text("Run on selected")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}
