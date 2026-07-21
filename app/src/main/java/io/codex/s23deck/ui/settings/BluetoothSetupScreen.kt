package io.codex.s23deck.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.codex.s23deck.HidState
import io.codex.s23deck.ui.connection.canSendInput
import io.codex.s23deck.ui.connection.hidHealth
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckPage
import io.codex.s23deck.ui.designsystem.DeckSectionLabel

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
        item { DeckSectionLabel("Bluetooth mouse + keyboard") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (health.canSendInput) Icons.Outlined.CheckCircle else Icons.Outlined.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(health.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = health.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DeckActionButton(
                        label = if (permissionGranted) "Scan Bluetooth targets" else "Allow Bluetooth",
                        onClick = {
                            if (permissionGranted) {
                                onStart()
                                onRefreshHosts()
                            } else {
                                onRequestPermission()
                            }
                        },
                        icon = if (permissionGranted) Icons.Outlined.Refresh else Icons.Outlined.Bluetooth,
                        selected = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DeckActionButton(
                            label = "Reconnect",
                            onClick = {
                                onStart()
                                onRefreshHosts()
                                state.selectedHostAddress?.let(onConnect)
                            },
                            icon = Icons.Outlined.Bluetooth,
                            enabled = permissionGranted && state.selectedHostAddress != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                        DeckActionButton(
                            label = "Trackpad",
                            onClick = onOpenTrackpad,
                            icon = Icons.Outlined.CheckCircle,
                            enabled = health.canSendInput,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        item { DeckSectionLabel("Known targets") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (health.canSendInput) {
                        "Connected target is ready for Trackpad."
                    } else {
                        "Allow Bluetooth, then scan. Targets will appear here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
