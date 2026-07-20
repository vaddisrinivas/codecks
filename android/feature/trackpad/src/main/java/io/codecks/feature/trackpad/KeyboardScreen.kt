package io.codecks.feature.trackpad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class KeyboardUiState(
    val targetName: String = "MacBook Pro",
    val isConnected: Boolean = true,
    val quickActions: List<KeyboardAction> = defaultKeyboardActions,
)

data class KeyboardAction(
    val label: String,
    val icon: ImageVector,
)

private val defaultKeyboardActions = listOf(
    KeyboardAction("Copy", Icons.Filled.ContentCopy),
    KeyboardAction("Paste", Icons.Filled.ContentPaste),
    KeyboardAction("Select all", Icons.Filled.SelectAll),
    KeyboardAction("Undo", Icons.AutoMirrored.Filled.Undo),
    KeyboardAction("Redo", Icons.AutoMirrored.Filled.Redo),
    KeyboardAction("Find", Icons.Filled.Search),
    KeyboardAction("Save", Icons.Filled.Save),
)

@Composable
fun KeyboardScreen(
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    KeyboardScreen(
        state = KeyboardUiState(),
        onClose = onClose,
        onSendText = {},
        onAction = {},
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    state: KeyboardUiState,
    onClose: () -> Unit,
    onSendText: (String) -> Unit,
    onAction: (KeyboardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var composedText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Keyboard") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (state.isConnected) state.targetName else "Offline") },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Send text and shortcuts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = composedText,
                onValueChange = { composedText = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isConnected,
                label = { Text("Text composer") },
                minLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onSendText(composedText)
                        composedText = ""
                    },
                    enabled = state.isConnected && composedText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Send")
                }
                OutlinedButton(
                    onClick = { onAction(KeyboardAction("Enter", Icons.AutoMirrored.Filled.KeyboardReturn)) },
                    enabled = state.isConnected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Enter")
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Quick shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.quickActions.forEach { action ->
                            AssistChip(
                                onClick = { onAction(action) },
                                enabled = state.isConnected,
                                label = { Text(action.label) },
                                leadingIcon = {
                                    Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Deck and Trackpad can open this panel without changing the selected Mac.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KeyboardScreenPreview() {
    MaterialTheme {
        KeyboardScreen()
    }
}
