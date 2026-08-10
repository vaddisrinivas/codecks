package io.codecks.ui.automations

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckEmptyState
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.isReady
import io.codecks.ui.connection.statusLabel
import io.codecks.ui.app.AccessibleStatus
import io.codecks.ui.app.AccessibleStatusKind
import io.codecks.ui.app.accessibilityTraversalOrder

@Composable
internal fun TestPreviewCard(
    preview: String?,
    succeeded: Boolean?,
    stage: String = "Validation",
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
                    true -> "$stage passed"
                    false -> "$stage failed"
                    null -> "$stage required"
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
internal fun AutomationSectionHeader(
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
internal fun CategoryFilters(
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
internal fun AutomationRow(
    item: AutomationItem,
    traversalOrder: Float,
    running: Boolean,
    connectionReady: Boolean,
    connectionHealth: ConnectionHealth,
    onRun: () -> Unit,
    onValidate: () -> Unit,
    onPreflight: () -> Unit,
    onLiveTest: () -> Unit,
    onOptions: () -> Unit,
) {
    val controlsReady = connectionReady && connectionHealth.isReady
    val enabled = item.enabled && controlsReady && !running
    val status = automationStatus(item, running, connectionHealth)
    val ifLine = when {
        !controlsReady -> "Needs Mac connection before it can run"
        item.lastLiveTestSucceeded == true -> "Ready for background execution"
        item.lastPreflightSucceeded == false -> "Preflight failed; rerun preflight"
        item.lastPreflightSucceeded == true -> "Preflight passed; run live test"
        item.lastTestSucceeded == true -> "Validation passed; preflight and live test required"
        item.lastTestSucceeded == false -> "Validation failed; fix before enabling"
        item.canEnable -> "Ready to enable once action is scheduled"
        item.enabled -> "Enabled; requires latest receipts"
        else -> "Paused until validated and tested"
    }
    val selected = running || item.lastLiveTestSucceeded == true
    CodecksPanel(
        selected = selected,
        danger = item.dangerous || item.lastRunSucceeded == false || item.lastTestSucceeded == false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .accessibilityTraversalOrder(traversalOrder)
            .semantics {
                stateDescription = when {
                    item.recoveryRequired -> "Recovery required"
                    item.cleanupPassed == false -> "Cleanup failed"
                    running -> "Running"
                    else -> status.label
                }
            },
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
                        text = when {
                            !controlsReady -> "Needs Mac"
                            item.enabled -> "Enabled"
                            else -> "Paused"
                        },
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
                            contentDescription = "Run rule",
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onOptions) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit rule")
                }
            }
            AutomationRuleLine("WHEN", item.triggerLabel)
            AutomationRuleLine("IF", ifLine, failed = item.lastTestSucceeded == false)
            AutomationRuleLine("THEN", item.draftCommand.ifBlank { item.description })
            item.triggerSimulationReason?.let {
                AutomationRuleLine("SIM", it, compact = true)
            }
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
    item.recoveryRequired -> AutomationStatus("Recovery required", AutomationStatusTone.Error)
    item.cleanupPassed == false -> AutomationStatus("Cleanup failed", AutomationStatusTone.Error)
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
internal fun AutomationOptionsDialog(
    item: AutomationItem,
    onDismiss: () -> Unit,
    onValidate: () -> Unit,
    onPreflight: () -> Unit,
    onLiveTest: () -> Unit,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            ) {
                if (item.recoveryRequired || item.cleanupPassed == false) {
                    AccessibleStatus(
                        stateDescription = if (item.recoveryRequired) "Recovery required" else "Cleanup failed",
                        detail = "Resolve cleanup and recovery before enabling or rerunning this rule.",
                        kind = AccessibleStatusKind.Error,
                        announceChanges = true,
                        announcementKey = "${item.id}:${item.recoveryRequired}:${item.cleanupPassed}",
                    )
                }
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
                        "Validation + preflight + live test are required before enabling this exact revision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item.lastTestLabel?.let {
                    TestPreviewCard(
                        preview = it,
                        succeeded = item.lastTestSucceeded,
                        stage = "Validation",
                        help = if (item.lastTestSucceeded == true) {
                            "Local validation passed."
                        } else {
                            "Run validation to verify this rule."
                        },
                    )
                }
                item.lastPreflightLabel?.let {
                    TestPreviewCard(
                        preview = it,
                        succeeded = item.lastPreflightSucceeded,
                        stage = "Preflight",
                        help = if (item.lastPreflightSucceeded == true) {
                            "Mac preflight passed."
                        } else {
                            "Run preflight to refresh capability checks."
                        },
                    )
                }
                item.lastLiveTestLabel?.let {
                    TestPreviewCard(
                        preview = it,
                        succeeded = item.lastLiveTestSucceeded,
                        stage = "Live test",
                        help = if (item.lastLiveTestSucceeded == true) {
                            "Live assertions and cleanup passed."
                        } else {
                            "Run a bounded live test with assertions and cleanup."
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
                    TextButton(onClick = onValidate, modifier = Modifier.weight(1f)) { Text("Validate") }
                    TextButton(onClick = onPreflight, modifier = Modifier.weight(1f)) { Text("Preflight") }
                    TextButton(onClick = onLiveTest, modifier = Modifier.weight(1f)) { Text("Live test") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
internal fun AutomationHistoryDialog(
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
                        "No runs yet. Test results are shown separately from real runs.",
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

internal fun formatAutomationTime(timestampMillis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(timestampMillis))

@Composable
internal fun EmptyAutomations(hasFilter: Boolean) {
    DeckEmptyState(
        title = if (hasFilter) "No matching rules" else "No rules yet",
        body = if (hasFilter) "Try another search or category." else "Create a rule with AI or add one from the deck library.",
        icon = if (hasFilter) Icons.Outlined.Search else Icons.Outlined.Bolt,
        modifier = Modifier.padding(top = 8.dp),
    )
}

internal fun AutomationItem.toDraftInput(): AutomationDraftInput =
    AutomationDraftInput(
        recipeId = id,
        title = label,
        triggerType = draftTriggerType,
        triggerValue = draftTriggerValue,
        command = draftCommand,
        enabled = enabled,
        weekdays = draftWeekdays,
    )

internal val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

internal data class AutomationCommandTemplate(
    val label: String,
    val title: String,
    val command: String,
)

internal val SAFE_COMMAND_TEMPLATES = listOf(
    AutomationCommandTemplate("Open Safari", "Open Safari", "open -a Safari"),
    AutomationCommandTemplate("Open Downloads", "Open Downloads", "open ~/Downloads"),
    AutomationCommandTemplate("Notify", "Show notification", "osascript -e 'display notification \"Done\" with title \"Codecks\"'"),
    AutomationCommandTemplate("Say ready", "Say ready", "say 'Codecks rule ran'"),
)

internal fun AutomationTriggerDraftType.presets(): List<String> = when (this) {
    AutomationTriggerDraftType.Manual,
    AutomationTriggerDraftType.MacAwake -> emptyList()
    AutomationTriggerDraftType.TimeOfDay -> listOf("09:00", "13:00", "17:30", "22:00")
    AutomationTriggerDraftType.ActiveApp -> listOf("Safari", "Chrome", "Codecks", "Terminal")
    AutomationTriggerDraftType.ClipboardContains -> listOf("TODO", "http", "@", "invoice")
    AutomationTriggerDraftType.WifiSsid -> listOf("Home", "Office", "Phone Hotspot")
    AutomationTriggerDraftType.FileChanged -> listOf("~/Downloads", "~/Desktop", "~/Documents")
    AutomationTriggerDraftType.BatteryBelow -> listOf("20", "35", "50")
}

internal fun AutomationCategory.icon(): ImageVector = when (this) {
    AutomationCategory.Routines -> Icons.Outlined.Code
    AutomationCategory.Workspace -> Icons.Outlined.Computer
    AutomationCategory.Browser -> Icons.Outlined.Language
    AutomationCategory.Media -> Icons.Outlined.MusicNote
    AutomationCategory.System -> Icons.Outlined.Settings
}
