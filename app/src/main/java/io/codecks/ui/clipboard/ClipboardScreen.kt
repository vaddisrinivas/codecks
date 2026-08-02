package io.codecks.ui.clipboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.clipboard.ClipboardSessionPhase
import io.codecks.domain.clipboard.ClipboardReceipt
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.app.AccessibleStatus
import io.codecks.ui.app.AccessibleStatusKind
import io.codecks.ui.app.accessibilityTraversalOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ClipboardScreen(
    state: ClipboardUiState,
    contentPadding: PaddingValues,
    onRefreshPhone: () -> Unit,
    onPullFromMac: () -> Unit,
    onPushToMac: () -> Unit,
    onModeChange: (ClipboardSyncMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onForegroundVisibleChange: (Boolean) -> Unit,
    onRetrySharedText: () -> Unit = {},
    onDiscardSharedText: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LifecycleResumeEffect(Unit) {
        onForegroundVisibleChange(true)
        onPauseOrDispose { onForegroundVisibleChange(false) }
    }
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item { ClipboardStatusSummary(state, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) }
        if (state.pendingSharedText) {
            item {
                SharedTextPendingPanel(
                    isRunning = state.isRunning,
                    onRetry = onRetrySharedText,
                    onDiscard = onDiscardSharedText,
                )
            }
        }
        item {
            ClipboardSessionPanel(
                state = state,
                onStartSession = onStartSession,
                onStopSession = onStopSession,
            )
        }
        if (state.session.canReadPhoneClipboard) {
            item { ClipboardPreviewPanel(state) }
        }
        item {
            ClipboardSyncPolicyPanel(
                state = state,
                onModeVisible = state.liveSyncVisible,
            )
        }
        item {
            ClipboardManualPanel(
                state = state,
                onRefreshPhone = onRefreshPhone,
                onPullFromMac = onPullFromMac,
                onPushToMac = onPushToMac,
                onCancelConflict = { onModeChange(state.mode) },
            )
        }
        item {
            ClipboardSettingsPanel(
                state = state,
                onModeChange = onModeChange,
                onIntervalChange = onIntervalChange,
            )
        }
        state.lastSyncReceipt?.let { receipt ->
            item { ClipboardManualReceiptPanel(receipt = receipt) }
        }
        if (state.history.isNotEmpty()) {
            item { ClipboardHistoryPanel(state) }
        }
    }
}

@Composable
private fun SharedTextPendingPanel(
    isRunning: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            AccessibleStatus(
                stateDescription = if (isRunning) "Sending shared text" else "Shared text waiting",
                detail = "Retained until Mac delivery is applied or you discard it.",
                kind = if (isRunning) AccessibleStatusKind.Busy else AccessibleStatusKind.Information,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckActionButton(
                    label = "Retry shared text",
                    onClick = onRetry,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                DeckActionButton(
                    label = "Discard shared text",
                    onClick = onDiscard,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ClipboardStatusSummary(state: ClipboardUiState, modifier: Modifier = Modifier) {
    val connectionStatus = clipboardConnectionStatus(state)
    val attention = state.hasConflict ||
        connectionStatus == ClipboardConnectionStatus.Offline ||
        connectionStatus == ClipboardConnectionStatus.Failed
    AccessibleStatus(
        stateDescription = connectionStatus.label,
        detail = clipboardStatusDetail(state),
        kind = if (attention) AccessibleStatusKind.Error else if (state.isRunning) {
            AccessibleStatusKind.Busy
        } else {
            AccessibleStatusKind.Success
        },
        announceChanges = !state.isRunning,
        announcementKey = "${connectionStatus.name}:${state.hasConflict}:${state.lastFailureClass}",
        modifier = modifier.accessibilityTraversalOrder(0f),
    )
}

@Composable
private fun ClipboardSessionPanel(
    state: ClipboardUiState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
) {
    val active = state.session.phase == ClipboardSessionPhase.ActiveVisible
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text("Phone clipboard access", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = when (state.session.phase) {
                    ClipboardSessionPhase.Inactive -> "Off. Codecks cannot read the phone clipboard."
                    ClipboardSessionPhase.Hidden -> "Paused until Codecks and Clipboard are visible again."
                    ClipboardSessionPhase.Locked -> "Paused while the phone is locked."
                    ClipboardSessionPhase.Expired -> "Expired. Start another 15-minute session to read again."
                    ClipboardSessionPhase.ActiveVisible -> "Active for this visible, unlocked 15-minute session."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            DeckActionButton(
                label = if (active) "Stop clipboard session" else "Start 15-minute session",
                onClick = if (active) onStopSession else onStartSession,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).accessibilityTraversalOrder(1f),
            )
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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 480.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClipboardPreviewCard(
                        title = "Phone",
                        preview = state.phonePreview,
                        hash = state.phoneHash,
                        riskLabel = state.phoneRisk,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ClipboardPreviewCard(
                        title = "Mac",
                        preview = state.macPreview,
                        hash = state.macHash,
                        riskLabel = state.macRisk,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ClipboardPreviewCard(
                        title = "Phone",
                        preview = state.phonePreview,
                        hash = state.phoneHash,
                        riskLabel = state.phoneRisk,
                        modifier = Modifier.weight(1f),
                    )
                    ClipboardPreviewCard(
                        title = "Mac",
                        preview = state.macPreview,
                        hash = state.macHash,
                        riskLabel = state.macRisk,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal enum class ClipboardConnectionStatus(val label: String) {
    Ready("Ready"),
    Offline("Offline"),
    SetupNeeded("Setup needed"),
    Checking("Checking…"),
    Failed("Failed"),
}

internal fun clipboardConnectionStatus(state: ClipboardUiState): ClipboardConnectionStatus = when {
    state.isRunning -> ClipboardConnectionStatus.Checking
    (state.connectionConfigured || state.connectionReady) && state.isRemoteOffline ->
        ClipboardConnectionStatus.Offline
    !state.connectionReady -> ClipboardConnectionStatus.SetupNeeded
    state.hasConflict || state.lastFailureClass != null -> ClipboardConnectionStatus.Failed
    else -> ClipboardConnectionStatus.Ready
}

internal fun clipboardStatusDetail(state: ClipboardUiState): String = when {
    state.isRunning -> "Checking the phone and Mac clipboards."
    state.hasConflict -> "Both sides changed. Choose the copy to keep."
    (state.connectionConfigured || state.connectionReady) && state.isRemoteOffline ->
        "The Mac is offline. Manual and automatic sync cannot run."
    !state.connectionReady -> "Connect a Mac in Settings before transferring text."
    state.lastFailureClass != null -> "The last transfer failed. Try again or check Mac setup."
    state.mode == ClipboardSyncMode.Off -> "Manual transfer is available. Automatic sync is off."
    state.batterySaverActive -> "Battery Saver paused automatic sync. Manual refresh remains available."
    state.liveSyncVisible -> "Automatic sync checks while Clipboard is open."
    state.staleEndpoints.isNotEmpty() -> "Clipboard information needs another check."
    else -> "Automatic sync resumes when Clipboard is open."
}

@Composable
private fun ClipboardPreviewCard(
    title: String,
    preview: String,
    hash: String,
    riskLabel: String?,
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
                text = if (preview == "Empty" || hash.isBlank()) "No content yet" else "fingerprint ${hash.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            riskLabel?.let { label ->
                Text("Sensitive: $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ClipboardSyncPolicyPanel(state: ClipboardUiState, onModeVisible: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Sync behavior",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Upload, contentDescription = null)
                Column {
                    Text("Manual", style = MaterialTheme.typography.labelLarge)
                    Text("Send/Get any time while connected to Mac", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Column {
                    Text("Visible live sync", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = when {
                            state.mode == ClipboardSyncMode.Off -> "Disabled"
                            state.batterySaverActive -> "Paused by Battery Saver; manual refresh remains available"
                            onModeVisible -> "Active every ${state.syncIntervalMinutes} minutes"
                            else -> "Paused (open Clipboard to resume)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Visibility, contentDescription = null)
                Column {
                    Text("Background read", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (state.mode == ClipboardSyncMode.Off || !state.liveSyncVisible) {
                            "Unavailable until screen is open"
                        } else if (state.batterySaverActive) {
                            "Automatic reads paused; use manual refresh"
                        } else {
                            if (state.nextSyncDelaySeconds <= 0L) {
                                "Next read soon"
                            } else {
                                "Next check in ${state.nextSyncDelaySeconds}s"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardManualPanel(
    state: ClipboardUiState,
    onRefreshPhone: () -> Unit,
    onPullFromMac: () -> Unit,
    onPushToMac: () -> Unit,
    onCancelConflict: () -> Unit,
) {
    val sessionActive = state.session.canReadPhoneClipboard
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (state.hasConflict) {
            AccessibleStatus(
                stateDescription = "Clipboard conflict",
                detail = "Choose the phone copy, Mac copy, or cancel without changing either side.",
                kind = AccessibleStatusKind.Error,
                announceChanges = true,
                announcementKey = "clipboard-conflict:${state.phoneHash}:${state.macHash}",
                modifier = Modifier.accessibilityTraversalOrder(3f),
            )
        }
        Text(
            text = "Manual sync",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val actions: @Composable (Modifier) -> Unit = { actionModifier ->
            DeckActionButton(
                label = if (state.hasConflict) "Use phone copy" else "Send to Mac",
                onClick = onPushToMac,
                enabled = !state.isRunning && sessionActive,
                icon = Icons.Outlined.Upload,
                modifier = actionModifier.heightIn(min = 56.dp),
            )
            DeckActionButton(
                label = if (state.hasConflict) "Use Mac copy" else "Get from Mac",
                onClick = onPullFromMac,
                enabled = !state.isRunning && sessionActive,
                icon = Icons.Outlined.Download,
                modifier = actionModifier.heightIn(min = 56.dp),
            )
        }
        if (state.hasConflict) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                actions(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                actions(Modifier.weight(1f))
            }
        }
        DeckActionButton(
            label = "Refresh phone clipboard",
            onClick = onRefreshPhone,
            icon = Icons.Outlined.Schedule,
            enabled = !state.isRunning && sessionActive,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
        if (state.hasConflict) {
            DeckActionButton(
                label = "Cancel conflict",
                onClick = onCancelConflict,
                enabled = !state.isRunning && sessionActive,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
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
private fun ClipboardManualReceiptPanel(receipt: ClipboardReceipt) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    val startedAt = timeFormatter.format(Instant.ofEpochMilli(receipt.startedAtMillis))
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Text("Last clipboard result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Direction: ${receipt.direction.name}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Started: $startedAt  |  Duration: ${receipt.completedAtMillis - receipt.startedAtMillis}ms",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Result: ${receipt.terminalResult.name}", style = MaterialTheme.typography.bodySmall)
            if (receipt.failureCode != io.codecks.domain.clipboard.ClipboardFailureCode.None) {
                Text("Failure: ${receipt.failureCode.name}", style = MaterialTheme.typography.bodySmall)
            }
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
private fun ClipboardHistoryPanel(state: ClipboardUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        text = "rev ${revision.revision}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
