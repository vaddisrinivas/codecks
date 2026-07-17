package io.codex.s23deck.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.codex.s23deck.domain.device.DeviceGroup
import io.codex.s23deck.domain.device.TargetDevice
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckEmptyState
import io.codex.s23deck.ui.designsystem.DeckPage
import io.codex.s23deck.ui.designsystem.DeckSectionLabel

@Composable
fun DevicesScreen(
    contentPadding: PaddingValues,
    state: DevicesUiState,
    onSelectDevice: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeckPage(contentPadding = contentPadding, modifier = modifier) {
        item {
            DevicesHero(
                deviceCount = state.devices.size,
                readyCount = state.devices.count(TargetDevice::online),
                groupCount = state.groups.size,
            )
        }
        item { DeckSectionLabel("Targets") }
        if (state.devices.isEmpty()) {
            item {
                DeckEmptyState(
                    title = "No Mac targets yet",
                    body = "Add a Mac once. Codecks saves it as a reusable target for Deck, Trackpad, AI, and Automations.",
                    icon = Icons.Outlined.Computer,
                )
            }
        }
        items(state.devices, key = { it.id.value }) { device ->
            DeviceRow(
                device = device,
                current = device.id.value == state.currentDeviceId,
                onSelect = { onSelectDevice(device.id.value) },
            )
        }
        item { DeckSectionLabel("Groups") }
        if (state.groups.isEmpty()) {
            item { InfoCard("Groups appear after at least one Mac target is saved.") }
        } else {
            items(state.groups, key = { it.id.value }) { group ->
                GroupRow(group)
            }
        }
        item {
            state.message?.let { InfoCard(it) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                DeckActionButton(
                    label = "Refresh",
                    onClick = onRefresh,
                    icon = Icons.Outlined.Refresh,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
                DeckActionButton(
                    label = "Add Mac",
                    onClick = onAddDevice,
                    icon = Icons.Outlined.Add,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun DevicesHero(
    deviceCount: Int,
    readyCount: Int,
    groupCount: Int,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = CircleShape,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Workspaces, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text("Targets & groups", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$readyCount/$deviceCount ready · $groupCount groups",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: TargetDevice,
    current: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        color = if (current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = if (current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                headlineContent = { Text(device.name) },
                supportingContent = {
                    Text("${device.platform} · ${device.transports.joinToString { it.value }}")
                },
                leadingContent = {
                    StatusIcon(
                        icon = Icons.Outlined.Computer,
                        ready = device.online,
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (current) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Current target", tint = MaterialTheme.colorScheme.primary)
                        }
                        DeckActionButton(
                            label = if (current) "Current" else "Use",
                            onClick = onSelect,
                            enabled = !current,
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                },
            )
            if (device.online) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp)) {
                    CapabilityPill(icon = Icons.Outlined.Mouse, label = "Trackpad")
                    CapabilityPill(icon = Icons.Outlined.Keyboard, label = "Keyboard")
                    CapabilityPill(icon = Icons.Outlined.Sync, label = "Clipboard")
                }
            }
        }
    }
}

@Composable
private fun GroupRow(group: DeviceGroup) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            headlineContent = { Text(group.name) },
            supportingContent = { Text("${group.memberIds.size} target${if (group.memberIds.size == 1) "" else "s"}") },
            leadingContent = { StatusIcon(Icons.Outlined.Workspaces, ready = true) },
        )
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    ready: Boolean,
) {
    Surface(
        color = if (ready) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        shape = CircleShape,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CapabilityPill(
    icon: ImageVector,
    label: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
