package io.codecks.feature.automations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class AutomationSummary(
    val id: String,
    val name: String,
    val whenText: String,
    val ifText: String,
    val thenText: String,
    val isEnabled: Boolean,
    val lastReceipt: AutomationReceiptState,
)

enum class AutomationReceiptState {
    NotRun,
    DryRunReady,
    DryRunPassed,
    ActionSent,
    NeedsAttention,
}

data class AutomationEditorState(
    val title: String = "Open daily deck",
    val whenText: String = "WHEN target connects",
    val ifText: String = "IF Finder is frontmost",
    val thenText: String = "THEN open deck Daily Standup",
    val dryRunState: AutomationReceiptState = AutomationReceiptState.DryRunReady,
)

@Composable
fun AutomationsScreen() {
    AutomationsScreen(
        automations = listOf(
            AutomationSummary(
                id = "finder-daily",
                name = "Daily Finder check",
                whenText = "Target connects on trusted Wi-Fi",
                ifText = "Finder is available and no dry-run warning exists",
                thenText = "Open Finder and capture a receipt",
                isEnabled = true,
                lastReceipt = AutomationReceiptState.DryRunPassed,
            ),
            AutomationSummary(
                id = "draft-safe",
                name = "Draft safe run",
                whenText = "AI draft is approved",
                ifText = "Payload has no destructive command",
                thenText = "Run guarded actions and save receipt",
                isEnabled = false,
                lastReceipt = AutomationReceiptState.NeedsAttention,
            ),
        ),
        editorState = AutomationEditorState(),
        onCreate = {},
        onEdit = {},
        onDryRun = {},
        onSave = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    automations: List<AutomationSummary>,
    editorState: AutomationEditorState?,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDryRun: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Automations") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = "Create automation")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(automations, key = { it.id }) { automation ->
                AutomationCard(automation = automation, onEdit = { onEdit(automation.id) })
            }
            if (editorState != null) {
                item {
                    AutomationEditor(
                        state = editorState,
                        onDryRun = onDryRun,
                        onSave = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationCard(
    automation: AutomationSummary,
    onEdit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(automation.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (automation.isEnabled) "Enabled" else "Paused",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ReceiptChip(automation.lastReceipt)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${automation.name}")
                }
            }
            ReadableRuleRow("WHEN", automation.whenText)
            ReadableRuleRow("IF", automation.ifText)
            ReadableRuleRow("THEN", automation.thenText)
        }
    }
}

@Composable
private fun AutomationEditor(
    state: AutomationEditorState,
    onDryRun: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = state.title, onValueChange = {}, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.whenText, onValueChange = {}, label = { Text("WHEN") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.ifText, onValueChange = {}, label = { Text("IF") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.thenText, onValueChange = {}, label = { Text("THEN") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDryRun) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Dry run")
                }
                Button(onClick = onSave) {
                    Text("Save")
                }
                ReceiptChip(state.dryRunState)
            }
        }
    }
}

@Composable
private fun ReadableRuleRow(label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.22f),
        )
        Text(text = text, modifier = Modifier.weight(0.78f))
    }
}

@Composable
private fun ReceiptChip(state: AutomationReceiptState) {
    val label = when (state) {
        AutomationReceiptState.NotRun -> "No receipt"
        AutomationReceiptState.DryRunReady -> "Ready"
        AutomationReceiptState.DryRunPassed -> "Dry run passed"
        AutomationReceiptState.ActionSent -> "Sent"
        AutomationReceiptState.NeedsAttention -> "Check"
    }
    val icon = if (state == AutomationReceiptState.NeedsAttention) Icons.Filled.Warning else Icons.Filled.CheckCircle
    AssistChip(onClick = {}, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) })
}

@Preview(showBackground = true)
@Composable
private fun AutomationsScreenPreview() {
    MaterialTheme {
        AutomationsScreen(
            automations = listOf(
                AutomationSummary(
                    id = "1",
                    name = "Morning desk",
                    whenText = "Target connects on office Wi-Fi",
                    ifText = "Battery is above 40%",
                    thenText = "Open Finder and Daily Standup",
                    isEnabled = true,
                    lastReceipt = AutomationReceiptState.DryRunPassed,
                ),
            ),
            editorState = AutomationEditorState(),
            onCreate = {},
            onEdit = {},
            onDryRun = {},
            onSave = {},
        )
    }
}
