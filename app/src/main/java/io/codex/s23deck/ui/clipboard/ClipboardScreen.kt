package io.codex.s23deck.ui.clipboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.codex.s23deck.domain.clipboard.ClipboardSyncMode
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckFilterPill
import io.codex.s23deck.ui.designsystem.DeckPage

@Composable
fun ClipboardScreen(
    state: ClipboardUiState,
    contentPadding: PaddingValues,
    onRefreshPhone: () -> Unit,
    onPullFromMac: () -> Unit,
    onPushToMac: () -> Unit,
    onModeChange: (ClipboardSyncMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item { ClipboardStatusSummary(state, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) }
        item { ClipboardPreviewPanel(state) }
        item {
            ClipboardSettingsPanel(
                state = state,
                onModeChange = onModeChange,
                onIntervalChange = onIntervalChange,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Manual",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DeckActionButton(
                        label = if (state.hasConflict) "Use phone copy" else "Send to Mac",
                        onClick = onPushToMac,
                        enabled = state.connectionReady && !state.isRunning,
                        icon = Icons.Outlined.Upload,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    )
                    DeckActionButton(
                        label = if (state.hasConflict) "Use Mac copy" else "Get from Mac",
                        onClick = onPullFromMac,
                        enabled = state.connectionReady && !state.isRunning,
                        icon = Icons.Outlined.Download,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    )
                }
                DeckActionButton(
                    label = "Check status",
                    onClick = onRefreshPhone,
                    icon = Icons.Outlined.Schedule,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                )
            }
        }
        if (state.history.isNotEmpty()) {
            item { ClipboardHistoryPanel(state) }
        }
    }
}

@Composable
private fun ClipboardPreviewPanel(state: ClipboardUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        state.lastSafetyWarning?.let { warning ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null)
                    Column {
                        Text("Safety guard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(warning, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ClipboardPreviewCard(
                title = "Phone",
                preview = state.phonePreview,
                hash = state.phoneHash,
                modifier = Modifier.weight(1f),
            )
            ClipboardPreviewCard(
                title = "Mac",
                preview = state.macPreview,
                hash = state.macHash,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ClipboardPreviewCard(
    title: String,
    preview: String,
    hash: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = 112.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            Text(
                text = if (hash.isBlank()) "No hash yet" else "sha ${hash.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClipboardSettingsPanel(
    state: ClipboardUiState,
    onModeChange: (ClipboardSyncMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    val syncEnabled = state.mode != ClipboardSyncMode.Off
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Automatic sync",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            SyncDirectionRow(state, onModeChange)
            IntervalRow(state, onIntervalChange)
            OutlinedTextField(
                value = state.syncIntervalMinutes.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let(onIntervalChange) },
                label = { Text("Custom interval") },
                supportingText = {
                    Text(if (syncEnabled) "1 to 240 minutes" else "Turn on sync to edit the interval")
                },
                suffix = { Text("minutes") },
                enabled = syncEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SyncDirectionRow(
    state: ClipboardUiState,
    onModeChange: (ClipboardSyncMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(
            ClipboardSyncMode.Off to "Off",
            ClipboardSyncMode.PhoneToMac to "To Mac",
            ClipboardSyncMode.MacToPhone to "From Mac",
            ClipboardSyncMode.Bidirectional to "Two-way",
        ).forEach { (mode, label) ->
            DeckFilterPill(
                selected = state.mode == mode,
                onClick = { onModeChange(mode) },
                label = label,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun IntervalRow(
    state: ClipboardUiState,
    onIntervalChange: (Int) -> Unit,
) {
    val syncEnabled = state.mode != ClipboardSyncMode.Off
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(5, 15, 30).forEach { minutes ->
            DeckFilterPill(
                selected = syncEnabled && state.syncIntervalMinutes == minutes,
                onClick = { onIntervalChange(minutes) },
                label = "${minutes}m",
                enabled = syncEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun ClipboardStatusSummary(state: ClipboardUiState, modifier: Modifier = Modifier) {
    val attention = state.hasConflict || state.isRemoteOffline
    Surface(
        color = if (attention) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (attention) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = if (attention) Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule,
                contentDescription = null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.status, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        state.hasConflict -> "Both sides changed. Choose a direction below."
                        state.isRemoteOffline -> "Mac is offline. Sync resumes when connected."
                        state.mode == ClipboardSyncMode.Off -> "Automatic sync is off."
                        else -> "Syncs while open every ${state.syncIntervalMinutes} minutes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ClipboardHistoryPanel(state: ClipboardUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
            Text("Recent sync observations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            state.history.takeLast(5).asReversed().forEach { revision ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = revision.endpoint.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(0.35f),
                    )
                    Text(
                        text = "rev ${revision.revision} · ${revision.hash.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
