package io.codecks.feature.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.targets.TargetSetupDraft
import io.codecks.domain.targets.TargetSetupPolicy
import io.codecks.domain.targets.TargetSetupStepState

data class ConnectionSetupUiState(
    val targetName: String = "My Mac",
    val address: String = "192.168.1.42",
    val username: String = "user",
    val fingerprint: String = "SHA256: 6E:91:40:AC:77:29",
    val phonePublicKey: String? = null,
    val installPassword: String = "",
    val installError: String? = null,
    val canTrustFingerprint: Boolean = true,
    val canInstallKey: Boolean = false,
    val canMarkKeyInstalled: Boolean = false,
    val canTestFinder: Boolean = false,
    val lastReceipt: ActionReceipt? = null,
    val steps: List<ConnectionSetupStep> = defaultConnectionSteps,
) {
    companion object {
        fun fromDraft(
            draft: TargetSetupDraft,
            phonePublicKey: String? = draft.phonePublicKey,
            installPassword: String = "",
            installError: String? = null,
            lastReceipt: ActionReceipt? = null,
        ): ConnectionSetupUiState = ConnectionSetupUiState(
            targetName = draft.targetName,
            address = if (draft.port == 22) draft.host else "${draft.host}:${draft.port}",
            username = draft.username,
            fingerprint = draft.fingerprintSha256 ?: "Fingerprint pending",
            phonePublicKey = phonePublicKey,
            installPassword = installPassword,
            installError = installError,
            canTrustFingerprint = draft.hasAddress && draft.hasFingerprint && !draft.fingerprintTrusted,
            canInstallKey = draft.fingerprintTrusted && !draft.keyInstalled && !phonePublicKey.isNullOrBlank() && installPassword.isNotBlank(),
            canMarkKeyInstalled = draft.fingerprintTrusted && !draft.keyInstalled && !phonePublicKey.isNullOrBlank(),
            canTestFinder = draft.keyInstalled && !draft.finderProofSucceeded,
            lastReceipt = lastReceipt,
            steps = TargetSetupPolicy.checklist(draft).map {
                ConnectionSetupStep(
                    title = it.title,
                    detail = it.detail,
                    status = when (it.state) {
                        TargetSetupStepState.TODO -> StepStatus.Todo
                        TargetSetupStepState.ACTIVE -> StepStatus.Active
                        TargetSetupStepState.DONE -> StepStatus.Done
                        TargetSetupStepState.BLOCKED -> StepStatus.Warning
                    },
                )
            },
        )
    }
}

data class ConnectionSetupStep(
    val title: String,
    val detail: String,
    val status: StepStatus,
)

enum class StepStatus {
    Todo,
    Active,
    Done,
    Warning,
}

private val defaultConnectionSteps = listOf(
    ConnectionSetupStep("Name target", "Give this Mac a name you will recognize.", StepStatus.Done),
    ConnectionSetupStep("Enter or find address", "Use local IP, hostname, or discovery.", StepStatus.Active),
    ConnectionSetupStep("Trust fingerprint", "Compare the fingerprint shown on the Mac.", StepStatus.Todo),
    ConnectionSetupStep("Install key", "Add the app key for local command access.", StepStatus.Todo),
    ConnectionSetupStep("Test Finder", "Open Finder as a harmless proof action.", StepStatus.Todo),
)

@Composable
fun ConnectionScreen(
    onTryDemo: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    val demoDraft = TargetSetupDraft(
        targetName = "My Mac",
        host = "192.168.1.42",
        username = "user",
        fingerprintSha256 = "SHA256:6E9140AC7729",
    )
    ConnectionSetupScreen(
        state = ConnectionSetupUiState.fromDraft(demoDraft),
        onTargetNameChanged = {},
        onAddressChanged = {},
        onUsernameChanged = {},
        onFindAddress = onTryDemo,
        onTrustFingerprint = {},
        onInstallPasswordChanged = {},
        onCopyPublicKey = {},
        onInstallKey = {},
        onMarkKeyInstalled = {},
        onTestFinder = onFinish,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSetupScreen(
    state: ConnectionSetupUiState,
    onTargetNameChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onFindAddress: () -> Unit,
    onTrustFingerprint: () -> Unit,
    onInstallPasswordChanged: (String) -> Unit,
    onCopyPublicKey: () -> Unit,
    onInstallKey: () -> Unit,
    onMarkKeyInstalled: () -> Unit,
    onTestFinder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Connect Mac") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "First-run setup",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                OutlinedTextField(
                    value = state.targetName,
                    onValueChange = onTargetNameChanged,
                    label = { Text("Target name") },
                    leadingIcon = { Icon(Icons.Filled.Computer, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = onAddressChanged,
                        label = { Text("Address") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onFindAddress) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Text("Find")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChanged,
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SetupActionCard(
                    icon = Icons.Filled.Security,
                    title = "Fingerprint trust",
                    detail = state.fingerprint,
                    action = "Trust",
                    enabled = state.canTrustFingerprint,
                    onAction = onTrustFingerprint,
                )
            }
            item {
                PublicKeyCard(
                    publicKey = state.phonePublicKey,
                    onCopyPublicKey = onCopyPublicKey,
                    onMarkKeyInstalled = onMarkKeyInstalled,
                    canMarkKeyInstalled = state.canMarkKeyInstalled,
                )
            }
            item {
                OutlinedTextField(
                    value = state.installPassword,
                    onValueChange = onInstallPasswordChanged,
                    label = { Text("Mac password") },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canMarkKeyInstalled,
                )
            }
            state.installError?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SetupActionCard(
                    icon = Icons.Filled.Key,
                    title = "Key install",
                    detail = "Password is used once and not saved.",
                    action = "Install",
                    enabled = state.canInstallKey,
                    onAction = onInstallKey,
                )
            }
            item {
                SetupActionCard(
                    icon = Icons.Filled.Folder,
                    title = "Test Finder",
                    detail = "Runs a visible Finder test before full control.",
                    action = "Test",
                    enabled = state.canTestFinder,
                    onAction = onTestFinder,
                )
            }
            state.lastReceipt?.let { receipt ->
                item {
                    ReceiptCard(receipt)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.steps.forEach { step ->
                        ChecklistRow(step)
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicKeyCard(
    publicKey: String?,
    onCopyPublicKey: () -> Unit,
    onMarkKeyInstalled: () -> Unit,
    canMarkKeyInstalled: Boolean,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Phone public key", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = publicKey ?: "Generating key...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            publicKey?.let {
                SelectionContainer {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyPublicKey,
                    enabled = publicKey != null,
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("Copy", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onMarkKeyInstalled,
                    enabled = canMarkKeyInstalled,
                ) {
                    Text("I installed it")
                }
            }
        }
    }
}

@Composable
private fun SetupActionCard(
    icon: ImageVector,
    title: String,
    detail: String,
    action: String,
    enabled: Boolean = true,
    onAction: () -> Unit,
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
            Button(onClick = onAction, enabled = enabled) {
                Text(action)
            }
        }
    }
}

@Composable
private fun ReceiptCard(receipt: ActionReceipt) {
    val isSuccess = receipt.state == ReceiptState.SUCCESS
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Filled.Receipt else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isSuccess) "Finder proof complete" else "Finder proof failed",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(receipt.safeSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                receipt.repair?.let {
                    Text(it.title, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(step: ConnectionSetupStep) {
    val icon = when (step.status) {
        StepStatus.Done -> Icons.Filled.CheckCircle
        StepStatus.Warning -> Icons.Filled.Warning
        else -> Icons.Filled.Computer
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null)
        Column {
            Text(step.title, fontWeight = FontWeight.SemiBold)
            Text(step.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionSetupScreenPreview() {
    MaterialTheme {
        ConnectionSetupScreen(
            state = ConnectionSetupUiState(),
            onTargetNameChanged = {},
            onAddressChanged = {},
            onUsernameChanged = {},
            onFindAddress = {},
            onTrustFingerprint = {},
            onInstallPasswordChanged = {},
            onCopyPublicKey = {},
            onInstallKey = {},
            onMarkKeyInstalled = {},
            onTestFinder = {},
        )
    }
}
