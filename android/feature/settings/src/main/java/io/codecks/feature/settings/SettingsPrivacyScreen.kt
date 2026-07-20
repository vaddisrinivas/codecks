package io.codecks.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class PrivacyCenterUiState(
    val targetName: String = "My Mac",
    val tokenStatus: String = "Stored on device",
    val permissionStatus: String = "Finder allowed, Screen Recording off",
    val dataStatus: String = "Drafts and receipts stay local",
)

@Composable
fun SettingsScreen() {
    SettingsPrivacyScreen(
        state = PrivacyCenterUiState(),
        onRotateToken = {},
        onOpenPermissions = {},
        onClearLocalData = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(
    state: PrivacyCenterUiState,
    onRotateToken: () -> Unit,
    onOpenPermissions: () -> Unit,
    onClearLocalData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Privacy Center") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudOff, contentDescription = null)
                            Text("Local-first, no cloud", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Commands, tokens, receipts, and drafts stay between this device and ${state.targetName}.")
                        AssistChip(onClick = {}, label = { Text("No account sync") })
                    }
                }
            }
            item {
                PrivacySection(
                    icon = Icons.Filled.Key,
                    title = "Tokens",
                    detail = state.tokenStatus,
                    action = "Rotate",
                    onAction = onRotateToken,
                )
            }
            item {
                PrivacySection(
                    icon = Icons.Filled.Security,
                    title = "Permissions",
                    detail = state.permissionStatus,
                    action = "Review",
                    onAction = onOpenPermissions,
                )
            }
            item {
                PrivacySection(
                    icon = Icons.Filled.Lock,
                    title = "Data",
                    detail = state.dataStatus,
                    action = "Clear",
                    destructive = true,
                    onAction = onClearLocalData,
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    icon: ImageVector,
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    destructive: Boolean = false,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (destructive) {
                OutlinedButton(onClick = onAction) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(action)
                }
            } else {
                Button(onClick = onAction) {
                    Text(action)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPrivacyScreenPreview() {
    MaterialTheme {
        SettingsPrivacyScreen(
            state = PrivacyCenterUiState(),
            onRotateToken = {},
            onOpenPermissions = {},
            onClearLocalData = {},
        )
    }
}
