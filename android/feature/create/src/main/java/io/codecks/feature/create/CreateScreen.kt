package io.codecks.feature.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class CreateDraftUiState(
    val prompt: String = "Create a deck that opens Finder, Notes, and the project folder.",
    val title: String = "Focus startup",
    val summary: String = "3 actions prepared. Finder test runs first.",
    val payload: String = "{\n  \"target\": \"local-mac\",\n  \"actions\": [\"open_finder\", \"open_notes\", \"open_folder\"]\n}",
    val warnings: List<String> = listOf("Folder path needs confirmation before run."),
    val manualPath: String = "<codecks-checkout>",
    val stateLabel: String = "Draft preview",
)

@Composable
fun CreateScreen() {
    CreateScreen(
        state = CreateDraftUiState(),
        onPromptChanged = {},
        onGenerateDraft = {},
        onRunDraft = {},
        onManualCreate = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    state: CreateDraftUiState,
    onPromptChanged: (String) -> Unit,
    onGenerateDraft: () -> Unit,
    onRunDraft: () -> Unit,
    onManualCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Create") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChanged,
                    label = { Text("Describe the deck") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onGenerateDraft) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Text("Draft")
                    }
                    OutlinedButton(onClick = onManualCreate) {
                        Text("Manual path")
                    }
                }
            }
            item {
                DraftPreview(state = state, onRunDraft = onRunDraft)
            }
            item {
                PayloadInspector(payload = state.payload)
            }
        }
    }
}

@Composable
private fun DraftPreview(state: CreateDraftUiState, onRunDraft: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(state.stateLabel) })
            }
            Text(state.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.warnings.forEach { warning ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(warning, color = MaterialTheme.colorScheme.error)
                }
            }
            Text("Manual path visible: ${state.manualPath}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRunDraft) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Run guarded")
            }
        }
    }
}

@Composable
private fun PayloadInspector(payload: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Code, contentDescription = null)
                Text("Payload inspector", fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = payload,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateScreenPreview() {
    MaterialTheme {
        CreateScreen(
            state = CreateDraftUiState(),
            onPromptChanged = {},
            onGenerateDraft = {},
            onRunDraft = {},
            onManualCreate = {},
        )
    }
}
