package io.codecks.feature.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.codecks.core.designsystem.CodecksAppTheme
import io.codecks.core.designsystem.CodecksTokens
import io.codecks.core.designsystem.deck.DeckKeyState
import io.codecks.core.designsystem.deck.FancyDeckEffectKind
import io.codecks.core.designsystem.deck.FancyDeckKey
import io.codecks.core.designsystem.deck.FancyDeckThemes
import io.codecks.domain.codex.CodexCockpitSnapshot
import io.codecks.domain.codex.CodexBridgeSnapshotParser
import io.codecks.domain.codex.CodexTaskSnapshot
import io.codecks.domain.codex.CodexTaskState
import io.codecks.domain.codex.DeckButtonSpec
import io.codecks.domain.codex.DeckEffectKind
import io.codecks.domain.codex.DeckEffectSpec
import io.codecks.domain.codex.DeckEffectTrigger
import io.codecks.domain.codex.FancyButtonSafety
import io.codecks.domain.codex.MockCodexCockpit
import io.codecks.domain.codex.ThemePresetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Immutable
private data class CodexCockpitUiState(
    val snapshot: CodexCockpitSnapshot,
    val selectedTaskId: String,
    val selectedThemeId: String,
) {
    val selectedTask: CodexTaskSnapshot get() =
        snapshot.tasks.firstOrNull { it.id == selectedTaskId } ?: snapshot.tasks.first()
}

@Composable
fun CodexCockpitScreen(
    modifier: Modifier = Modifier,
    snapshot: CodexCockpitSnapshot = remember { MockCodexCockpit.snapshot() },
) {
    val context = LocalContext.current
    var liveSnapshot by remember { mutableStateOf(snapshot) }
    var selectedTaskId by rememberSaveable { mutableStateOf(snapshot.tasks.first().id) }
    var selectedThemeId by rememberSaveable { mutableStateOf(snapshot.activeThemeId) }
    var bridgeJson by rememberSaveable { mutableStateOf("") }
    var bridgeStatus by rememberSaveable { mutableStateOf("Paste bridge JSON when ready.") }
    var bridgeUrl by rememberSaveable { mutableStateOf("http://10.0.2.2:8765/codecks-local-codex-snapshot.json") }
    var actionStatus by rememberSaveable { mutableStateOf("No button action run yet.") }
    var editedButtons by remember {
        mutableStateOf(loadPersistedButtons(context, snapshot.buttons))
    }
    var editingButtonId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val state = CodexCockpitUiState(
        snapshot = liveSnapshot.copy(buttons = editedButtons),
        selectedTaskId = selectedTaskId,
        selectedThemeId = selectedThemeId,
    )

    LaunchedEffect(context, editedButtons) {
        persistButtons(context, editedButtons)
    }

    CodexCockpitScreen(
        state = state,
        onTaskSelected = { selectedTaskId = it.id },
        onThemeSelected = { selectedThemeId = it.id },
        onEditButton = { editingButtonId = it.id },
        onUpdateButton = { updated ->
            editedButtons = editedButtons.map { button ->
                if (button.id == updated.id) updated else button
            }
        },
        actionStatus = actionStatus,
        onRunButtonAction = { button ->
            when (val action = button.action.orEmpty()) {
                "" -> actionStatus = "No action id assigned."
                "bridge.fetch.local-codex" -> {
                    bridgeUrl = LocalCodexBridgeUrl
                    scope.launch {
                        actionStatus = "Fetching local Codex metadata..."
                        val result = fetchAndApplyBridgeSnapshot(LocalCodexBridgeUrl, editedButtons)
                        result.onSuccess { parsed ->
                            liveSnapshot = parsed.snapshot
                            selectedTaskId = parsed.tasks.firstOrNull()?.id ?: selectedTaskId
                            bridgeJson = result.getOrThrow().rawJson
                            bridgeStatus = "Fetched ${parsed.tasks.size} status-only tasks."
                            actionStatus = "Loaded local Codex metadata."
                        }.onFailure { error ->
                            actionStatus = error.message ?: "Local Codex metadata fetch failed."
                        }
                    }
                }
                "bridge.fetch.release" -> {
                    bridgeUrl = ReleaseBridgeUrl
                    scope.launch {
                        actionStatus = "Fetching release status..."
                        val result = fetchAndApplyBridgeSnapshot(ReleaseBridgeUrl, editedButtons)
                        result.onSuccess { parsed ->
                            liveSnapshot = parsed.snapshot
                            selectedTaskId = parsed.tasks.firstOrNull()?.id ?: selectedTaskId
                            bridgeJson = result.getOrThrow().rawJson
                            bridgeStatus = "Fetched ${parsed.tasks.size} status-only tasks."
                            actionStatus = "Loaded release status."
                        }.onFailure { error ->
                            actionStatus = error.message ?: "Release status fetch failed."
                        }
                    }
                }
                "bridge.fetch.mock" -> {
                    bridgeUrl = MockBridgeUrl
                    scope.launch {
                        actionStatus = "Fetching mock cockpit..."
                        val result = fetchAndApplyBridgeSnapshot(MockBridgeUrl, editedButtons)
                        result.onSuccess { parsed ->
                            liveSnapshot = parsed.snapshot
                            selectedTaskId = parsed.tasks.firstOrNull()?.id ?: selectedTaskId
                            bridgeJson = result.getOrThrow().rawJson
                            bridgeStatus = "Fetched ${parsed.tasks.size} status-only tasks."
                            actionStatus = "Loaded mock cockpit."
                        }.onFailure { error ->
                            actionStatus = error.message ?: "Mock cockpit fetch failed."
                        }
                    }
                }
                else -> actionStatus = "Action '$action' is saved, but no guarded executor is installed."
            }
        },
        bridgeJson = bridgeJson,
        bridgeStatus = bridgeStatus,
        bridgeUrl = bridgeUrl,
        onBridgeJsonChanged = { bridgeJson = it },
        onBridgeUrlChanged = { bridgeUrl = it },
        onApplyBridgeJson = {
            runCatching {
                CodexBridgeSnapshotParser.parse(bridgeJson)
            }.onSuccess { parsed ->
                liveSnapshot = parsed.copy(buttons = editedButtons)
                selectedTaskId = parsed.tasks.firstOrNull()?.id ?: selectedTaskId
                bridgeStatus = "Loaded ${parsed.tasks.size} status-only tasks."
            }.onFailure { error ->
                bridgeStatus = error.message ?: "Could not parse bridge JSON."
            }
        },
        onFetchBridgeJson = {
            scope.launch {
                bridgeStatus = "Fetching bridge snapshot..."
                fetchAndApplyBridgeSnapshot(bridgeUrl, editedButtons).onSuccess { parsed ->
                    liveSnapshot = parsed.snapshot
                    selectedTaskId = parsed.tasks.firstOrNull()?.id ?: selectedTaskId
                    bridgeJson = parsed.rawJson
                    bridgeStatus = "Fetched ${parsed.tasks.size} status-only tasks."
                }.onFailure { error ->
                    bridgeStatus = error.message ?: "Could not fetch bridge JSON."
                }
            }
        },
        editingButtonId = editingButtonId,
        onCloseEditor = { editingButtonId = null },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodexCockpitScreen(
    state: CodexCockpitUiState,
    onTaskSelected: (CodexTaskSnapshot) -> Unit,
    onThemeSelected: (ThemePresetSpec) -> Unit,
    onEditButton: (DeckButtonSpec) -> Unit,
    onUpdateButton: (DeckButtonSpec) -> Unit,
    actionStatus: String,
    onRunButtonAction: (DeckButtonSpec) -> Unit,
    bridgeJson: String,
    bridgeStatus: String,
    bridgeUrl: String,
    onBridgeJsonChanged: (String) -> Unit,
    onBridgeUrlChanged: (String) -> Unit,
    onApplyBridgeJson: () -> Unit,
    onFetchBridgeJson: () -> Unit,
    editingButtonId: String?,
    onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CodecksTokens.colors.oledBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Codex Cockpit")
                        Text(
                            text = "Mock-first agent dashboard. Local bridge comes next.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                QueueStrip(state.snapshot)
            }
            item {
                BridgeImportCard(
                    bridgeJson = bridgeJson,
                    bridgeStatus = bridgeStatus,
                    bridgeUrl = bridgeUrl,
                    onBridgeJsonChanged = onBridgeJsonChanged,
                    onBridgeUrlChanged = onBridgeUrlChanged,
                    onApplyBridgeJson = onApplyBridgeJson,
                    onFetchBridgeJson = onFetchBridgeJson,
                )
            }
            item {
                SelectedTaskPanel(task = state.selectedTask)
            }
            item {
                SectionTitle("Live tasks")
            }
            items(state.snapshot.tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    selected = task.id == state.selectedTask.id,
                    onClick = { onTaskSelected(task) },
                )
            }
            item {
                SectionTitle("Fancy buttons")
            }
            item {
                FancyButtonGrid(
                    buttons = state.snapshot.buttons,
                    onEditButton = onEditButton,
                )
            }
            item {
                ButtonLabCard(
                    editingButton = state.snapshot.buttons.firstOrNull { it.id == editingButtonId },
                    onEditFirstButton = {
                        state.snapshot.buttons.firstOrNull()?.let(onEditButton)
                    },
                    onUpdateButton = onUpdateButton,
                    actionStatus = actionStatus,
                    onRunButtonAction = onRunButtonAction,
                    onCloseEditor = onCloseEditor,
                )
            }
            item {
                SectionTitle("Themes")
            }
            items(state.snapshot.themes, key = { it.id }) { theme ->
                ThemeCard(
                    theme = theme,
                    selected = theme.id == state.selectedThemeId,
                    onClick = { onThemeSelected(theme) },
                )
            }
        }
    }
}

@Composable
private fun BridgeImportCard(
    bridgeJson: String,
    bridgeStatus: String,
    bridgeUrl: String,
    onBridgeJsonChanged: (String) -> Unit,
    onBridgeUrlChanged: (String) -> Unit,
    onApplyBridgeJson: () -> Unit,
    onFetchBridgeJson: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050C12)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RadioButtonChecked, contentDescription = null, tint = Color(0xFF64F4FF))
                Text(
                    text = "Bridge intake",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Paste or fetch a status-only bridge snapshot. Parser rejects prompt/source content by default.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = bridgeUrl,
                onValueChange = onBridgeUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bridge URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = bridgeJson,
                onValueChange = onBridgeJsonChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
                label = { Text("Codex cockpit JSON") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onFetchBridgeJson, enabled = bridgeUrl.isNotBlank()) {
                    Text("Fetch bridge")
                }
                Button(onClick = onApplyBridgeJson, enabled = bridgeJson.isNotBlank()) {
                    Text("Load bridge")
                }
                Text(
                    text = bridgeStatus,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ButtonLabCard(
    editingButton: DeckButtonSpec?,
    onEditFirstButton: () -> Unit,
    onUpdateButton: (DeckButtonSpec) -> Unit,
    actionStatus: String,
    onRunButtonAction: (DeckButtonSpec) -> Unit,
    onCloseEditor: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF081018)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD166))
                Text(
                    text = "Empty button lab",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Tap a fancy key to edit it locally. Persistence and action assignment come after the bridge slice.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editingButton == null) {
                Button(onClick = onEditFirstButton) {
                    Text("Edit first button")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("emoji picker ready") })
                    AssistChip(onClick = {}, label = { Text("effect picker ready") })
                    AssistChip(onClick = {}, label = { Text("in-memory save") })
                }
            } else {
                ButtonEditor(
                    button = editingButton,
                    onUpdateButton = onUpdateButton,
                    actionStatus = actionStatus,
                    onRunButtonAction = onRunButtonAction,
                    onCloseEditor = onCloseEditor,
                )
            }
        }
    }
}

@Composable
private fun ButtonEditor(
    button: DeckButtonSpec,
    onUpdateButton: (DeckButtonSpec) -> Unit,
    actionStatus: String,
    onRunButtonAction: (DeckButtonSpec) -> Unit,
    onCloseEditor: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Editing ${button.label}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = button.label,
            onValueChange = { onUpdateButton(button.copy(label = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Label") },
            singleLine = true,
        )
        OutlinedTextField(
            value = button.statusLabel.orEmpty(),
            onValueChange = { raw ->
                onUpdateButton(button.copy(statusLabel = raw.ifBlank { null }))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Status label") },
            singleLine = true,
        )
        OutlinedTextField(
            value = button.action.orEmpty(),
            onValueChange = { raw ->
                onUpdateButton(button.copy(action = raw.ifBlank { null }))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Action id") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🎉", "🚀", "🧠", "✨").forEach { emoji ->
                OutlinedButton(onClick = { onUpdateButton(button.copy(emoji = emoji, symbol = null)) }) {
                    Text(emoji)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EffectChip("Confetti", button, DeckEffectKind.ConfettiBurst, onUpdateButton)
            EffectChip("Rain", button, DeckEffectKind.EmojiRain, onUpdateButton)
            EffectChip("Glow", button, DeckEffectKind.CalmGlow, onUpdateButton)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip("Local Codex", button, "bridge.fetch.local-codex", onUpdateButton)
            ActionChip("Release", button, "bridge.fetch.release", onUpdateButton)
            ActionChip("Mock", button, "bridge.fetch.mock", onUpdateButton)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onUpdateButton(button.copy(themeRole = "success", safetyLevel = FancyButtonSafety.Safe)) },
                label = { Text("success") },
            )
            AssistChip(
                onClick = { onUpdateButton(button.copy(themeRole = "arcade", safetyLevel = FancyButtonSafety.Safe)) },
                label = { Text("arcade") },
            )
            AssistChip(
                onClick = { onUpdateButton(button.copy(themeRole = "danger", safetyLevel = FancyButtonSafety.Dangerous)) },
                label = { Text("danger") },
            )
        }
        Text(
            text = actionStatus,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRunButtonAction(button) }) {
                Text("Run action")
            }
            Button(onClick = onCloseEditor) {
                Text("Keep")
            }
            OutlinedButton(
                onClick = {
                    onUpdateButton(
                        button.copy(
                            emoji = "◇",
                            statusLabel = "assign later",
                            themeRole = "empty",
                            safetyLevel = FancyButtonSafety.Safe,
                            effect = button.effect.copy(kind = DeckEffectKind.CalmGlow),
                        ),
                    )
                    onCloseEditor()
                },
            ) {
                Text("Make empty")
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    button: DeckButtonSpec,
    action: String,
    onUpdateButton: (DeckButtonSpec) -> Unit,
) {
    AssistChip(
        onClick = {
            onUpdateButton(
                button.copy(
                    action = action,
                    statusLabel = label,
                    safetyLevel = FancyButtonSafety.Safe,
                ),
            )
        },
        label = { Text(label) },
    )
}

@Composable
private fun EffectChip(
    label: String,
    button: DeckButtonSpec,
    effect: DeckEffectKind,
    onUpdateButton: (DeckButtonSpec) -> Unit,
) {
    AssistChip(
        onClick = {
            onUpdateButton(
                button.copy(
                    effect = DeckEffectSpec(
                        id = "effect-${effect.name.lowercase()}",
                        kind = effect,
                        trigger = DeckEffectTrigger.Press,
                        emojiSet = listOfNotNull(button.emoji),
                    ),
                ),
            )
        },
        label = { Text(label) },
    )
}

@Composable
private fun QueueStrip(snapshot: CodexCockpitSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard("Running", snapshot.runningCount.toString(), Color(0xFF64F4FF), Modifier.weight(1f))
        MetricCard("Queue", snapshot.queueCount.toString(), Color(0xFFFFD166), Modifier.weight(1f))
        MetricCard("Attention", snapshot.attentionCount.toString(), Color(0xFFFF5D7A), Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071015)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SelectedTaskPanel(task: CodexTaskSnapshot) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF06131A)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(task.state.icon(), contentDescription = null, tint = task.state.accent())
                Text(
                    text = task.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AssistChip(onClick = {}, label = { Text(task.state.name) })
            }
            Text(task.safeSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(task.elapsedLabel) })
                AssistChip(onClick = {}, label = { Text(task.effortMode.name) })
                AssistChip(onClick = {}, label = { Text(task.source) })
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: CodexTaskSnapshot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) Color(0xFF0A1E28) else Color(0xFF05090C),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(task.state.icon(), contentDescription = null, tint = task.state.accent())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${task.branch} • ${task.elapsedLabel} • ${task.updatedLabel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (task.needsAttention || task.hasUnread) {
                Text("!", color = Color(0xFFFFD166), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun FancyButtonGrid(
    buttons: List<DeckButtonSpec>,
    onEditButton: (DeckButtonSpec) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(buttons, key = { it.id }) { button ->
            FancyDeckKey(
                title = button.label,
                subtitle = button.statusLabel,
                emoji = button.emoji ?: button.symbol,
                state = button.state(),
                effect = button.effect.kind.toFancyEffect(),
                visualTheme = FancyDeckThemes.byRole(button.themeRole),
                minHeight = 104.dp,
                onClick = { onEditButton(button) },
            )
        }
    }
}

@Composable
private fun ThemeCard(
    theme: ThemePresetSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) Color(0xFF111D16) else Color(0xFF05090C),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(Color(android.graphics.Color.parseColor(theme.accent)), RoundedCornerShape(999.dp))
                    .padding(10.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(theme.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(theme.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF7CFFB2))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = CodecksTokens.colors.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun DeckButtonSpec.state(): DeckKeyState = when {
    !enabled -> DeckKeyState.Disabled
    safetyLevel.name == "Dangerous" -> DeckKeyState.Danger
    themeRole == "success" -> DeckKeyState.Success
    else -> DeckKeyState.Idle
}

private fun DeckEffectKind.toFancyEffect(): FancyDeckEffectKind = when (this) {
    DeckEffectKind.None -> FancyDeckEffectKind.None
    DeckEffectKind.ConfettiBurst -> FancyDeckEffectKind.ConfettiBurst
    DeckEffectKind.EmojiRain -> FancyDeckEffectKind.EmojiRain
    DeckEffectKind.SparkTrail -> FancyDeckEffectKind.SparkTrail
    DeckEffectKind.ShockwavePulse -> FancyDeckEffectKind.ShockwavePulse
    DeckEffectKind.FireworkGrid -> FancyDeckEffectKind.FireworkGrid
    DeckEffectKind.NeonSweep -> FancyDeckEffectKind.NeonSweep
    DeckEffectKind.DangerPulse -> FancyDeckEffectKind.DangerPulse
    DeckEffectKind.CalmGlow -> FancyDeckEffectKind.CalmGlow
}

private fun CodexTaskState.icon(): ImageVector = when (this) {
    CodexTaskState.Idle -> Icons.Rounded.RadioButtonChecked
    CodexTaskState.Queued -> Icons.Rounded.Pending
    CodexTaskState.Running -> Icons.Rounded.PlayCircle
    CodexTaskState.NeedsAttention -> Icons.Rounded.Warning
    CodexTaskState.Blocked -> Icons.Rounded.Warning
    CodexTaskState.Succeeded -> Icons.Rounded.CheckCircle
    CodexTaskState.Failed -> Icons.Rounded.Error
    CodexTaskState.Released -> Icons.Rounded.AutoAwesome
    CodexTaskState.Offline -> Icons.Rounded.Error
}

private fun CodexTaskState.accent(): Color = when (this) {
    CodexTaskState.Idle -> Color(0xFF99ADB3)
    CodexTaskState.Queued -> Color(0xFFFFD166)
    CodexTaskState.Running -> Color(0xFF64F4FF)
    CodexTaskState.NeedsAttention -> Color(0xFFFFD166)
    CodexTaskState.Blocked -> Color(0xFFFF5D7A)
    CodexTaskState.Succeeded -> Color(0xFF7CFFB2)
    CodexTaskState.Failed -> Color(0xFFFF5D7A)
    CodexTaskState.Released -> Color(0xFF7CFFB2)
    CodexTaskState.Offline -> Color(0xFF536267)
}

private const val CodexCockpitPrefs = "codecks_codex_cockpit"
private const val PersistedButtonsKey = "fancy_buttons_json"

private val ButtonJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun loadPersistedButtons(
    context: android.content.Context,
    fallback: List<DeckButtonSpec>,
): List<DeckButtonSpec> {
    val raw = context
        .getSharedPreferences(CodexCockpitPrefs, android.content.Context.MODE_PRIVATE)
        .getString(PersistedButtonsKey, null)
        ?: return fallback

    return runCatching {
        ButtonJson.decodeFromString(ListSerializer(DeckButtonSpec.serializer()), raw)
    }.getOrElse {
        fallback
    }
}

private fun persistButtons(
    context: android.content.Context,
    buttons: List<DeckButtonSpec>,
) {
    val encoded = ButtonJson.encodeToString(ListSerializer(DeckButtonSpec.serializer()), buttons)
    context
        .getSharedPreferences(CodexCockpitPrefs, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(PersistedButtonsKey, encoded)
        .apply()
}

private const val LocalCodexBridgeUrl = "http://10.0.2.2:8765/codecks-local-codex-snapshot.json"
private const val ReleaseBridgeUrl = "http://10.0.2.2:8765/codecks-release-cockpit-snapshot.json"
private const val MockBridgeUrl = "http://10.0.2.2:8765/codecks-codex-cockpit-snapshot.json"

private data class ParsedBridgeSnapshot(
    val snapshot: CodexCockpitSnapshot,
    val rawJson: String,
) {
    val tasks: List<CodexTaskSnapshot> get() = snapshot.tasks
}

private suspend fun fetchAndApplyBridgeSnapshot(
    rawUrl: String,
    buttons: List<DeckButtonSpec>,
): Result<ParsedBridgeSnapshot> = runCatching {
    val rawJson = fetchBridgeJson(rawUrl)
    val parsed = CodexBridgeSnapshotParser.parse(rawJson).copy(buttons = buttons)
    ParsedBridgeSnapshot(snapshot = parsed, rawJson = rawJson)
}

private suspend fun fetchBridgeJson(rawUrl: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 3000
        readTimeout = 3000
        requestMethod = "GET"
    }
    try {
        val code = connection.responseCode
        require(code in 200..299) { "Bridge returned HTTP $code." }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp,dpi=420", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CodexCockpitScreenPreview() {
    CodecksAppTheme {
        CodexCockpitScreen()
    }
}
