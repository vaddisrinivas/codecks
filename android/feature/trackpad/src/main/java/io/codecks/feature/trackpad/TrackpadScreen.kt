package io.codecks.feature.trackpad

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class TrackpadUiState(
    val targetName: String = "MacBook Pro",
    val isConnected: Boolean = true,
    val latencyMs: Int = 18,
    val backGuardHint: String = "Swipe from the very edge twice to leave trackpad mode.",
    val primaryButtonLabel: String = "Tap",
    val secondaryButtonLabel: String = "Right click",
)

@Composable
fun TrackpadScreen(
    onExit: () -> Unit = {},
    onOpenKeyboard: () -> Unit = {},
) {
    TrackpadScreen(
        state = TrackpadUiState(),
        onPrimaryClick = {},
        onSecondaryClick = {},
        onExit = onExit,
        onOpenKeyboard = onOpenKeyboard,
        onOpenQuickActions = {},
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackpadScreen(
    state: TrackpadUiState,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onExit: () -> Unit = {},
    onOpenKeyboard: () -> Unit,
    onOpenQuickActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Trackpad") },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(if (state.isConnected) "Connected" else "Offline")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.isConnected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.targetName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (state.isConnected) "Live input, ${state.latencyMs} ms round trip" else "Reconnect before gestures send",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                        shape = RoundedCornerShape(28.dp),
                    )
                    .clickable(enabled = state.isConnected, onClick = onPrimaryClick)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "Move, tap, scroll",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.backGuardHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimaryClick,
                    enabled = state.isConnected,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Mouse, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(state.primaryButtonLabel)
                }
                OutlinedButton(
                    onClick = onSecondaryClick,
                    enabled = state.isConnected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(state.secondaryButtonLabel)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrackpadEntryPoint(
                    icon = Icons.Filled.Keyboard,
                    label = "Keyboard",
                    onClick = onOpenKeyboard,
                )
                TrackpadEntryPoint(
                    icon = Icons.Filled.Bolt,
                    label = "Quick actions",
                    onClick = onOpenQuickActions,
                )
                TrackpadEntryPoint(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "Exit",
                    onClick = onExit,
                )
            }
        }
    }
}

@Composable
private fun TrackpadEntryPoint(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackpadScreenPreview() {
    MaterialTheme {
        TrackpadScreen(
            state = TrackpadUiState(),
            onPrimaryClick = {},
            onSecondaryClick = {},
            onExit = {},
            onOpenKeyboard = {},
            onOpenQuickActions = {},
        )
    }
}
