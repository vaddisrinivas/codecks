package io.codex.s23deck.ui.automations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codex.s23deck.core.design.DeckBridgeDesignTokens
import io.codex.s23deck.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codex.s23deck.ui.designsystem.CodecksPanel
import io.codex.s23deck.ui.designsystem.DeckEmptyState
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckFilterPill
import io.codex.s23deck.ui.connection.ConnectionHealth
import io.codex.s23deck.ui.connection.isReady
import io.codex.s23deck.ui.connection.simpleConnectionHealth
import io.codex.s23deck.ui.connection.statusLabel

@Composable
fun AutomationsScreen(
    state: AutomationsUiState,
    connectionHealth: ConnectionHealth = simpleConnectionHealth(state.connectionReady),
    contentPadding: PaddingValues,
    onRunAutomation: (String) -> Unit,
    onTestAutomation: (String) -> Unit = {},
    onApproveAutomation: (String) -> Unit = {},
    onToggleAutomation: (String, Boolean) -> Unit = { _, _ -> },
    onDuplicateAutomation: (String) -> Unit = {},
    onDeleteAutomation: (String) -> Unit = {},
    onCheckTriggers: () -> Unit = {},
    onCreateAutomation: (AutomationDraftInput) -> Unit = {},
    onEditAutomation: (AutomationDraftInput) -> Unit = {},
    onCreateWithAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AutomationCategory?>(null) }
    var optionsItem by remember { mutableStateOf<AutomationItem?>(null) }
    var historyItem by remember { mutableStateOf<AutomationItem?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<AutomationItem?>(null) }
    val visibleItems = remember(state.automations, query, selectedCategory) {
        state.automations.filter { item ->
            (selectedCategory == null || item.category == selectedCategory) &&
                (query.isBlank() || item.label.contains(query, ignoreCase = true) ||
                    item.description.contains(query, ignoreCase = true))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.fillMaxSize())
        val wide = maxWidth >= 840.dp
        Column(modifier = Modifier.fillMaxSize()) {
            AutomationHeader(
                query = query,
                onQueryChange = { query = it },
                connectionReady = state.connectionReady,
                connectionHealth = connectionHealth,
                automationCount = state.automations.size,
                enabledCount = state.automations.count { it.enabled },
                triggerCount = state.automations.map { it.triggerLabel }.distinct().size,
                triggerMonitorLabel = state.triggerMonitorLabel,
                visibleCount = visibleItems.size,
                onCheckTriggers = onCheckTriggers,
                onCreate = { createOpen = true },
                onCreateWithAi = onCreateWithAi,
            )
            CategoryFilters(
                categories = state.automations.map(AutomationItem::category).distinct(),
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
            )
            if (visibleItems.isEmpty()) {
                EmptyAutomations(hasFilter = query.isNotBlank() || selectedCategory != null)
            } else if (wide) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        horizontal = DeckBridgeDesignTokens.Spacing.xxl,
                        vertical = DeckBridgeDesignTokens.Spacing.sm,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(DeckBridgeDesignTokens.Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DeckBridgeDesignTokens.Spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleItems, key = AutomationItem::id) { item ->
                        AutomationRow(
                            item = item,
                            running = state.runningActionId == item.id,
                            connectionReady = state.connectionReady,
                            connectionHealth = connectionHealth,
                            onRun = { onRunAutomation(item.id) },
                            onOptions = { optionsItem = item },
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleItems, key = AutomationItem::id) { item ->
                        AutomationRow(
                            item = item,
                            running = state.runningActionId == item.id,
                            connectionReady = state.connectionReady,
                            connectionHealth = connectionHealth,
                            onRun = { onRunAutomation(item.id) },
                            onOptions = { optionsItem = item },
                        )
                    }
                }
            }
        }
    }
    optionsItem?.let { item ->
        AutomationOptionsDialog(
            item = item,
            onDismiss = { optionsItem = null },
            onTest = {
                optionsItem = null
                onTestAutomation(item.id)
            },
            onDuplicate = {
                optionsItem = null
                onDuplicateAutomation(item.id)
            },
            onEdit = {
                optionsItem = null
                editItem = item
            },
            onHistory = {
                optionsItem = null
                historyItem = item
            },
            onDelete = {
                optionsItem = null
                onDeleteAutomation(item.id)
            },
            onApprove = {
                optionsItem = null
                onApproveAutomation(item.id)
            },
            onEnabledChange = { enabled ->
                optionsItem = item.copy(enabled = enabled)
                onToggleAutomation(item.id, enabled)
            },
        )
    }
    editItem?.let { item ->
        CreateAutomationDialog(
            title = "Edit automation",
            initial = item.toDraftInput(),
            lastTestPreview = item.lastTestLabel,
            lastTestSucceeded = item.lastTestSucceeded,
            onDismiss = { editItem = null },
            onSave = { input ->
                editItem = null
                onEditAutomation(input)
            },
        )
    }
    historyItem?.let { item ->
        AutomationHistoryDialog(
            item = item,
            onDismiss = { historyItem = null },
        )
    }
    if (createOpen) {
        CreateAutomationDialog(
            onDismiss = { createOpen = false },
            onSave = { input ->
                createOpen = false
                onCreateAutomation(input)
            },
        )
    }
}

@Composable
private fun AutomationHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    connectionReady: Boolean,
    connectionHealth: ConnectionHealth,
    automationCount: Int,
    enabledCount: Int,
    triggerCount: Int,
    triggerMonitorLabel: String,
    visibleCount: Int,
    onCheckTriggers: () -> Unit,
    onCreate: () -> Unit,
    onCreateWithAi: () -> Unit,
) {
    val controlsReady = connectionReady && connectionHealth.isReady
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Rules",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$enabledCount on · $visibleCount shown · $triggerMonitorLabel",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DeckActionButton(
            label = "New rule",
            onClick = onCreate,
            icon = Icons.Outlined.Add,
            selected = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search rules") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DeckActionButton(
                label = "Dry check",
                onClick = onCheckTriggers,
                enabled = controlsReady,
                icon = Icons.Outlined.Bolt,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
            )
            DeckActionButton(
                label = "AI draft",
                onClick = onCreateWithAi,
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
            )
        }
    }
}

@Composable
private fun CreateAutomationDialog(
    title: String = "Create automation",
    initial: AutomationDraftInput = AutomationDraftInput(
        title = "",
        triggerType = AutomationTriggerDraftType.Manual,
        triggerValue = "",
        command = "",
    ),
    lastTestPreview: String? = null,
    lastTestSucceeded: Boolean? = null,
    onDismiss: () -> Unit,
    onSave: (AutomationDraftInput) -> Unit,
) {
    var automationTitle by remember(initial.recipeId) { mutableStateOf(initial.title) }
    var command by remember(initial.recipeId) { mutableStateOf(initial.command) }
    var triggerValue by remember(initial.recipeId) { mutableStateOf(initial.triggerValue) }
    var triggerType by remember(initial.recipeId) { mutableStateOf(initial.triggerType) }
    var enabled by remember(initial.recipeId) { mutableStateOf(initial.enabled) }
    var weekdays by remember(initial.recipeId) { mutableStateOf(initial.weekdays) }
    val canEnable = initial.recipeId != null && lastTestSucceeded == true
    val enableHelp = when {
        initial.recipeId == null -> "Save disabled, test it from options, then enable."
        lastTestSucceeded == true -> "Last test passed. Safe to enable."
        lastTestSucceeded == false -> "Last test failed. Fix command, test again, then enable."
        else -> "No test result yet. Run Test before enabling."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = automationTitle,
                    onValueChange = { automationTitle = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AutomationSectionHeader(
                    label = "When",
                    help = "Pick the event or schedule that makes this automation eligible.",
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AutomationTriggerDraftType.entries, key = AutomationTriggerDraftType::name) { type ->
                        DeckFilterPill(
                            label = type.label,
                            selected = triggerType == type,
                            onClick = {
                                triggerType = type
                                triggerValue = when (type) {
                                    AutomationTriggerDraftType.TimeOfDay -> triggerValue.ifBlank { "09:00" }
                                    AutomationTriggerDraftType.FileChanged -> triggerValue.ifBlank { "~/Downloads" }
                                    AutomationTriggerDraftType.BatteryBelow -> triggerValue.ifBlank { "20" }
                                    AutomationTriggerDraftType.Manual,
                                    AutomationTriggerDraftType.MacAwake -> ""
                                    else -> triggerValue
                                }
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
                triggerType.presets().takeIf { it.isNotEmpty() }?.let { presets ->
                    Text("Quick values", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(presets, key = { it }) { preset ->
                            DeckFilterPill(
                                label = preset,
                                selected = triggerValue == preset,
                                onClick = { triggerValue = preset },
                                modifier = Modifier.heightIn(min = 40.dp),
                            )
                        }
                    }
                }
                if (triggerType !in setOf(AutomationTriggerDraftType.Manual, AutomationTriggerDraftType.MacAwake)) {
                    OutlinedTextField(
                        value = triggerValue,
                        onValueChange = { triggerValue = it },
                        label = { Text("Trigger value") },
                        placeholder = { Text(triggerType.hint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (triggerType == AutomationTriggerDraftType.TimeOfDay) {
                    Text("Days", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(WEEKDAYS, key = { it }) { day ->
                            DeckFilterPill(
                                label = day,
                                selected = day in weekdays,
                                onClick = {
                                    weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                        }
                    }
                    Text(
                        text = if (weekdays.isEmpty()) "Runs every day." else "Runs on ${weekdays.joinToString(", ")}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AutomationSectionHeader(
                    label = "If",
                    help = "Keep drafts disabled until you test them; dangerous commands still require confirmation.",
                )
                TestPreviewCard(
                    preview = lastTestPreview,
                    succeeded = lastTestSucceeded,
                    help = enableHelp,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked && canEnable
                        },
                        enabled = canEnable || enabled,
                    )
                }
                AutomationSectionHeader(
                    label = "Then",
                    help = "Use a safe template or write a reviewed Mac command. Test before enable.",
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(SAFE_COMMAND_TEMPLATES, key = AutomationCommandTemplate::label) { template ->
                        DeckFilterPill(
                            label = template.label,
                            selected = command == template.command,
                            onClick = {
                                command = template.command
                                if (automationTitle.isBlank()) automationTitle = template.title
                            },
                            modifier = Modifier.heightIn(min = 42.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command") },
                    placeholder = { Text("open -a Safari") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        AutomationDraftInput(
                            recipeId = initial.recipeId,
                            title = automationTitle,
                            triggerType = triggerType,
                            triggerValue = triggerValue,
                            command = command,
                            enabled = enabled,
                            weekdays = if (triggerType == AutomationTriggerDraftType.TimeOfDay) weekdays else emptySet(),
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TestPreviewCard(
    preview: String?,
    succeeded: Boolean?,
    help: String,
) {
    val danger = succeeded == false
    CodecksPanel(
        selected = succeeded == true,
        danger = danger,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = when (succeeded) {
                    true -> "Test passed"
                    false -> "Test failed"
                    null -> "Test required"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = preview ?: help,
                style = MaterialTheme.typography.bodySmall,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview != null) {
                Text(
                    text = help,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutomationSectionHeader(
    label: String,
    help: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryFilters(
    categories: List<AutomationCategory>,
    selected: AutomationCategory?,
    onSelect: (AutomationCategory?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DeckFilterPill(
                label = "All",
                selected = selected == null,
                onClick = { onSelect(null) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        items(categories, key = AutomationCategory::name) { category ->
            DeckFilterPill(
                label = category.label,
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                modifier = Modifier.heightIn(min = 48.dp),
                icon = if (selected == category) Icons.Outlined.Bolt else null,
            )
        }
    }
}

@Composable
private fun AutomationRow(
    item: AutomationItem,
    running: Boolean,
    connectionReady: Boolean,
    connectionHealth: ConnectionHealth,
    onRun: () -> Unit,
    onOptions: () -> Unit,
) {
    val controlsReady = connectionReady && connectionHealth.isReady
    val enabled = item.enabled && controlsReady && !running
    val status = automationStatus(item, running, connectionHealth)
    val ifLine = when {
        item.lastTestSucceeded == true -> "Dry run passed"
        item.lastTestSucceeded == false -> "Dry run failed; fix before enabling"
        item.canEnable -> "Approved and ready"
        item.enabled -> "Enabled; run a dry check when changed"
        else -> "Paused until checked"
    }
    CodecksPanel(
        selected = running || item.lastTestSucceeded == true,
        danger = item.dangerous || item.lastRunSucceeded == false || item.lastTestSucceeded == false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { if (running) stateDescription = "Running" },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (item.enabled) "Enabled" else "Paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AutomationStatusPill(status)
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    IconButton(onClick = onRun, enabled = enabled) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Run automation",
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onOptions) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit automation")
                }
            }
            AutomationRuleLine("WHEN", item.triggerLabel)
            AutomationRuleLine("IF", ifLine, failed = item.lastTestSucceeded == false)
            AutomationRuleLine("THEN", item.draftCommand.ifBlank { item.description })
            item.lastRunLabel?.let { label ->
                AutomationRuleLine("LAST", label, failed = item.lastRunSucceeded == false, compact = true)
            }
        }
    }
}

@Composable
private fun AutomationRuleLine(
    label: String,
    value: String,
    failed: Boolean = false,
    compact: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = value,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class AutomationStatus(
    val label: String,
    val tone: AutomationStatusTone,
)

private enum class AutomationStatusTone {
    Positive,
    Neutral,
    Warning,
    Error,
}

private fun automationStatus(
    item: AutomationItem,
    running: Boolean,
    connectionHealth: ConnectionHealth,
): AutomationStatus = when {
    running -> AutomationStatus("Running", AutomationStatusTone.Warning)
    !connectionHealth.isReady -> AutomationStatus(connectionHealth.statusLabel(), AutomationStatusTone.Warning)
    !item.enabled -> AutomationStatus("Paused", AutomationStatusTone.Neutral)
    item.lastRunSucceeded == false -> AutomationStatus("Failed", AutomationStatusTone.Error)
    item.lastRunSucceeded == true -> AutomationStatus("Last OK", AutomationStatusTone.Positive)
    else -> AutomationStatus("Enabled", AutomationStatusTone.Positive)
}

@Composable
private fun AutomationStatusPill(status: AutomationStatus) {
    val colorScheme = MaterialTheme.colorScheme
    val container = when (status.tone) {
        AutomationStatusTone.Positive -> colorScheme.primaryContainer
        AutomationStatusTone.Neutral -> colorScheme.surfaceContainerHigh
        AutomationStatusTone.Warning -> colorScheme.tertiaryContainer
        AutomationStatusTone.Error -> colorScheme.errorContainer
    }
    val content = when (status.tone) {
        AutomationStatusTone.Positive -> colorScheme.onPrimaryContainer
        AutomationStatusTone.Neutral -> colorScheme.onSurfaceVariant
        AutomationStatusTone.Warning -> colorScheme.onTertiaryContainer
        AutomationStatusTone.Error -> colorScheme.onErrorContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AutomationOptionsDialog(
    item: AutomationItem,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onApprove: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val canEnable = item.enabled || item.canEnable
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(item.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.triggerLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { checked ->
                            if (!checked || canEnable) onEnabledChange(checked)
                        },
                        enabled = canEnable,
                    )
                }
                if (!canEnable) {
                    Text(
                        "Validate first. Enable unlocks after a passing result for this exact automation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item.lastTestLabel?.let {
                    TestPreviewCard(
                        preview = it,
                        succeeded = item.lastTestSucceeded,
                        help = if (item.lastTestSucceeded == true) {
                            "Ready to enable."
                        } else {
                            "Fix and test again before enabling."
                        },
                    )
                }
                if (item.runHistory.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recent runs", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${item.runHistory.first().statusLabel} · ${formatAutomationTime(item.runHistory.first().timestampMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.approvalPending) {
                    DeckActionButton(
                        label = "Approve and run",
                        onClick = onApprove,
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                    TextButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("History") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTest) { Text("Validate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun AutomationHistoryDialog(
    item: AutomationItem,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Run history") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (item.runHistory.isEmpty()) {
                    Text(
                        "No runs yet. Validation results are shown separately from real executions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    item.runHistory.forEachIndexed { index, run ->
                        CodecksPanel(
                            selected = index == 0 && run.succeeded,
                            danger = !run.succeeded,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${run.statusLabel} · ${formatAutomationTime(run.timestampMillis)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (run.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                                Text(run.message, style = MaterialTheme.typography.bodySmall)
                                if (run.logs.isNotBlank() && run.logs != run.message) {
                                    Text(
                                        run.logs.take(600),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

private fun formatAutomationTime(timestampMillis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(timestampMillis))

@Composable
private fun EmptyAutomations(hasFilter: Boolean) {
    DeckEmptyState(
        title = if (hasFilter) "No matching actions" else "No automations yet",
        body = if (hasFilter) "Try another search or category." else "Create an automation with AI or add one from the deck library.",
        icon = if (hasFilter) Icons.Outlined.Search else Icons.Outlined.Bolt,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun AutomationItem.toDraftInput(): AutomationDraftInput =
    AutomationDraftInput(
        recipeId = id,
        title = label,
        triggerType = draftTriggerType,
        triggerValue = draftTriggerValue,
        command = draftCommand,
        enabled = enabled,
        weekdays = draftWeekdays,
    )

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class AutomationCommandTemplate(
    val label: String,
    val title: String,
    val command: String,
)

private val SAFE_COMMAND_TEMPLATES = listOf(
    AutomationCommandTemplate("Open Safari", "Open Safari", "open -a Safari"),
    AutomationCommandTemplate("Open Downloads", "Open Downloads", "open ~/Downloads"),
    AutomationCommandTemplate("Notify", "Show notification", "osascript -e 'display notification \"Done\" with title \"Codecks\"'"),
    AutomationCommandTemplate("Say ready", "Say ready", "say 'Codecks automation ran'"),
)

private fun AutomationTriggerDraftType.presets(): List<String> = when (this) {
    AutomationTriggerDraftType.Manual,
    AutomationTriggerDraftType.MacAwake -> emptyList()
    AutomationTriggerDraftType.TimeOfDay -> listOf("09:00", "13:00", "17:30", "22:00")
    AutomationTriggerDraftType.ActiveApp -> listOf("Safari", "Chrome", "Codex", "Terminal")
    AutomationTriggerDraftType.ClipboardContains -> listOf("TODO", "http", "@", "invoice")
    AutomationTriggerDraftType.WifiSsid -> listOf("Home", "Office", "Phone Hotspot")
    AutomationTriggerDraftType.FileChanged -> listOf("~/Downloads", "~/Desktop", "~/Documents")
    AutomationTriggerDraftType.BatteryBelow -> listOf("20", "35", "50")
}

private fun AutomationCategory.icon(): ImageVector = when (this) {
    AutomationCategory.Routines -> Icons.Outlined.Code
    AutomationCategory.Workspace -> Icons.Outlined.Computer
    AutomationCategory.Browser -> Icons.Outlined.Language
    AutomationCategory.Media -> Icons.Outlined.MusicNote
    AutomationCategory.System -> Icons.Outlined.Settings
}
