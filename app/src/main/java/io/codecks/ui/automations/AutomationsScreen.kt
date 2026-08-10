package io.codecks.ui.automations

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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.isReady
import io.codecks.ui.connection.simpleConnectionHealth

@Composable
fun AutomationsScreen(
    state: AutomationsUiState,
    connectionHealth: ConnectionHealth = simpleConnectionHealth(state.connectionReady),
    contentPadding: PaddingValues,
    onRunAutomation: (String) -> Unit,
    onValidateAutomation: (String) -> Unit = {},
    onPreflightAutomation: (String) -> Unit = {},
    onLiveTestAutomation: (String) -> Unit = {},
    onApproveAutomation: (String) -> Unit = {},
    onToggleAutomation: (String, Boolean) -> Unit = { _, _ -> },
    onDuplicateAutomation: (String) -> Unit = {},
    onDeleteAutomation: (String) -> Unit = {},
    onCheckTriggers: () -> Unit = {},
    onCreateAutomation: (AutomationDraftInput) -> Unit = {},
    onEditAutomation: (AutomationDraftInput) -> Unit = {},
    onResetRecovery: () -> Unit = {},
    onRestoreRecovery: () -> Unit = {},
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
                lastTriggerCheckedAtMillis = state.lastTriggerCheckedAtMillis,
                nextWindowStartAtMillis = state.nextWindowStartAtMillis,
                nextWindowEndAtMillis = state.nextWindowEndAtMillis,
                visibleCount = visibleItems.size,
                onCheckTriggers = onCheckTriggers,
                onCreate = { createOpen = true },
                onCreateWithAi = onCreateWithAi,
            )
            if (state.storageRecoveryRequired) {
                AutomationStorageRecovery(
                    onReset = onResetRecovery,
                    onRestore = onRestoreRecovery,
                )
            } else {
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
                        horizontal = CodecksDesignTokens.Spacing.xxl,
                        vertical = CodecksDesignTokens.Spacing.sm,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xs),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(visibleItems, key = { _, item -> item.id }) { index, item ->
                        AutomationRow(
                            item = item,
                            traversalOrder = index.toFloat(),
                            running = state.runningActionId == item.id,
                            connectionReady = state.connectionReady,
                            connectionHealth = connectionHealth,
                            onRun = { onRunAutomation(item.id) },
                            onValidate = { onValidateAutomation(item.id) },
                            onPreflight = { onPreflightAutomation(item.id) },
                            onLiveTest = { onLiveTestAutomation(item.id) },
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
                        itemsIndexed(visibleItems, key = { _, item -> item.id }) { index, item ->
                            AutomationRow(
                            item = item,
                            traversalOrder = index.toFloat(),
                            running = state.runningActionId == item.id,
                            connectionReady = state.connectionReady,
                            connectionHealth = connectionHealth,
                            onRun = { onRunAutomation(item.id) },
                            onValidate = { onValidateAutomation(item.id) },
                            onPreflight = { onPreflightAutomation(item.id) },
                            onLiveTest = { onLiveTestAutomation(item.id) },
                            onOptions = { optionsItem = item },
                            )
                        }
                    }
                }
            }
        }
    }
    optionsItem?.let { item ->
        AutomationOptionsDialog(
            item = item,
            onDismiss = { optionsItem = null },
            onValidate = {
                optionsItem = null
                onValidateAutomation(item.id)
            },
            onPreflight = {
                optionsItem = null
                onPreflightAutomation(item.id)
            },
            onLiveTest = {
                optionsItem = null
                onLiveTestAutomation(item.id)
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
            title = "Edit rule",
            initial = item.toDraftInput(),
            lastTestPreview = item.lastTestLabel,
            lastTestSucceeded = item.lastTestSucceeded,
            canEnable = item.canEnable,
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
private fun AutomationStorageRecovery(
    onReset: () -> Unit,
    onRestore: () -> Unit,
) {
    CodecksPanel(
        danger = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(CodecksDesignTokens.Spacing.xxl),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md),
            modifier = Modifier.padding(CodecksDesignTokens.Spacing.lg),
        ) {
            Text(
                text = "Automation storage needs recovery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Codecks preserved the unreadable data and did not replace it with defaults. Restore a backup, or reset only automations.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm)) {
                TextButton(onClick = onRestore) { Text("Restore backup") }
                TextButton(onClick = onReset) { Text("Reset automations") }
            }
        }
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
    lastTriggerCheckedAtMillis: Long,
    nextWindowStartAtMillis: Long,
    nextWindowEndAtMillis: Long,
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
        Text(
            text = "$enabledCount enabled · $visibleCount shown · $triggerMonitorLabel",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (lastTriggerCheckedAtMillis > 0L) {
            Text(
                text = "Last evaluation: ${formatAutomationTime(lastTriggerCheckedAtMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (nextWindowStartAtMillis > 0L && nextWindowEndAtMillis > 0L) {
            Text(
                text = "Next window: ${formatAutomationTime(nextWindowStartAtMillis)} to ${formatAutomationTime(nextWindowEndAtMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
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
                label = "Check now",
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
    title: String = "Create rule",
    initial: AutomationDraftInput = AutomationDraftInput(
        title = "",
        triggerType = AutomationTriggerDraftType.Manual,
        triggerValue = "",
        command = "",
    ),
    lastTestPreview: String? = null,
    lastTestSucceeded: Boolean? = null,
    canEnable: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (AutomationDraftInput) -> Unit,
) {
    var automationTitle by remember(initial.recipeId) { mutableStateOf(initial.title) }
    var command by remember(initial.recipeId) { mutableStateOf(initial.command) }
    var triggerValue by remember(initial.recipeId) { mutableStateOf(initial.triggerValue) }
    var triggerType by remember(initial.recipeId) { mutableStateOf(initial.triggerType) }
    var enabled by remember(initial.recipeId) { mutableStateOf(initial.enabled) }
    var weekdays by remember(initial.recipeId) { mutableStateOf(initial.weekdays) }
    val canEnableNow = initial.recipeId != null && canEnable
    val enableHelp = when {
        initial.recipeId == null -> "Save disabled. Validate + preflight + live test first."
        canEnableNow -> "Validation + preflight + live test passed. Safe to enable."
        lastTestSucceeded == true -> "Validation passed. Run preflight and live test before enabling."
        lastTestSucceeded == false -> "Validation failed. Fix command, validate again, then continue."
        else -> "Needs Validation + Preflight + Live test before enabling."
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
                    help = "Pick the event or schedule that makes this rule eligible.",
                )
                Text(
                    "Android checks enabled rules periodically. The 15-minute request is a minimum interval, not an exact run time; battery and system limits can delay it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    help = "Keep drafts disabled until validation + preflight + live test pass.",
                )
                TestPreviewCard(
                    preview = lastTestPreview,
                    succeeded = lastTestSucceeded,
                    stage = "Validation",
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
                            enabled = checked && canEnableNow
                        },
                        enabled = canEnableNow || enabled,
                    )
                }
                AutomationSectionHeader(
                    label = "Then",
                    help = "Use a safe template or write a reviewed Mac command. Validation is local and never executes commands.",
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
