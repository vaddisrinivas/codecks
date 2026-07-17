package io.codex.s23deck.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.codex.s23deck.HidState
import io.codex.s23deck.ui.connection.canSendInput
import io.codex.s23deck.ui.connection.hidHealth
import io.codex.s23deck.ui.connection.statusLabel
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckPage
import io.codex.s23deck.ui.designsystem.DeckSectionLabel
import io.codex.s23deck.ui.keyboard.HidHostHeader

@Composable
fun BluetoothSetupScreen(
    state: HidState,
    contentPadding: PaddingValues,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onRefreshHosts: () -> Unit,
    onConnect: (String) -> Unit,
    onOpenTrackpad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = state.hidHealth(permissionGranted)
    DeckPage(contentPadding = contentPadding, modifier = modifier) {
        item { DeckSectionLabel("Target") }
        item {
            HidHostHeader(
                title = "Bluetooth targets",
                disconnectedTitle = "Bluetooth ready",
                connectedTitle = "Bluetooth connected",
                icon = Icons.Outlined.Bluetooth,
                state = state,
                permissionGranted = permissionGranted,
                onRequestPermission = onRequestPermission,
                onStart = onStart,
                onRefreshHosts = onRefreshHosts,
                onConnect = onConnect,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            ListItem(
                headlineContent = {
                    Text(health.title)
                },
                supportingContent = {
                    Text(health.detail)
                },
                leadingContent = {
                    Icon(
                        imageVector = if (health.canSendInput) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = if (health.canSendInput) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text("Needed", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Bluetooth permission, a paired Mac, and one connected HID target. The app remembers the last target and reconnects automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Current: ${health.statusLabel()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DeckActionButton(
                        label = "Open Trackpad",
                        onClick = onOpenTrackpad,
                        icon = Icons.Outlined.Bluetooth,
                        enabled = health.canSendInput,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                }
            }
        }
    }
}
