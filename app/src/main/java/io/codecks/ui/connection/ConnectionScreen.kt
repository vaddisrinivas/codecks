package io.codecks.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.designsystem.DeckSectionLabel

@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    contentPadding: PaddingValues,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSelectHost: (String) -> Unit,
    onScan: () -> Unit,
    onScanLocalNetwork: () -> Unit = {},
    onVerifyHostKey: () -> Unit,
    onConfirmHostKey: () -> Unit,
    onAuthorize: () -> Unit,
    onRotateKey: () -> Unit,
    onResetTrust: () -> Unit = {},
    onRemoveTarget: () -> Unit = {},
    onSavePassword: () -> Unit,
    onUseSavedPassword: () -> Unit,
    onTest: () -> Unit,
    hidHealth: HidHealth? = null,
    onOpenHidSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val parsedPort = state.port.toIntOrNull()
    val trustedEndpoint = state.config.host == state.host.trim() &&
        state.config.user == state.user.trim() &&
        state.config.port == parsedPort &&
        state.config.hostKey.isNotBlank()
    val canAuthorize = state.host.isNotBlank() &&
        state.user.isNotBlank() &&
        state.password.isNotEmpty() &&
        parsedPort in 1..65535 &&
        trustedEndpoint &&
        state.operation == ConnectionOperation.Idle
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item {
            UnifiedSetupStatus(
                macHealth = state.connectionHealth(),
                hidHealth = hidHealth,
                operation = state.operation,
                onOpenHidSetup = onOpenHidSetup,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
            if (!state.config.isReady) {
                item {
                    MacPairingStepper(
                        state = state,
                        trustedEndpoint = trustedEndpoint,
                        canAuthorize = canAuthorize,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                SectionTitle(
                    title = if (state.config.isReady) "Mac" else "Find your Mac",
                    body = if (state.config.isReady) {
                        "Saved Mac"
                    } else {
                        "Turn on Remote Login in Mac System Settings, then scan or enter the Mac hostname or IP address."
                    },
                )
            }
            if (state.discoveredHosts.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(state.discoveredHosts, key = { it }) { host ->
                            DeckFilterPill(
                                label = host,
                                selected = state.host == host,
                                onClick = { onSelectHost(host) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                icon = Icons.Outlined.Computer,
                            )
                        }
                    }
                }
            }
            item {
                if (state.config.isReady) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            headlineContent = { Text(state.host.ifBlank { "Saved Mac" }) },
                            supportingContent = { Text("Selected Mac") },
                            leadingContent = {
                                Icon(Icons.Outlined.Computer, contentDescription = null)
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = onScan,
                                    enabled = state.operation == ConnectionOperation.Idle,
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Switch Mac")
                                }
                            },
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = state.host,
                                onValueChange = onHostChange,
                                label = { Text("Mac hostname or IP") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            DeckActionButton(
                                label = "Scan",
                                onClick = onScan,
                                enabled = state.operation == ConnectionOperation.Idle,
                                icon = Icons.Outlined.Refresh,
                                modifier = Modifier.widthIn(min = 112.dp, max = 136.dp).heightIn(min = 56.dp),
                            )
                        }
                        DeckActionButton(
                            label = "Scan local network",
                            onClick = onScanLocalNetwork,
                            enabled = state.operation == ConnectionOperation.Idle,
                            icon = Icons.Outlined.SettingsEthernet,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        )
                    }
                }
            }
            if (state.message != null && !state.config.isReady) {
                item {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (!state.config.isReady) {
                item {
                    SectionTitle(
                        title = "Authorize once",
                        body = "Trust this Mac, then enter your Mac login once so Codecks can save its control key.",
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        OutlinedTextField(
                            value = state.user,
                            onValueChange = onUserChange,
                            label = { Text("Mac username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.port,
                            onValueChange = onPortChange,
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.42f),
                        )
                    }
                }
                item {
                    DeckActionButton(
                        label = if (trustedEndpoint) "Mac trusted" else "Trust this Mac",
                        onClick = onVerifyHostKey,
                        enabled = state.host.isNotBlank() &&
                            state.user.isNotBlank() &&
                            parsedPort in 1..65535 &&
                            state.operation == ConnectionOperation.Idle,
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
                    )
                }
                state.pendingFingerprint?.let { fingerprint ->
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text(
                                text = fingerprint,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            DeckActionButton(
                                label = "Confirm this is my Mac",
                                onClick = onConfirmHostKey,
                                enabled = state.operation == ConnectionOperation.Idle,
                                icon = Icons.Outlined.CheckCircle,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text("Mac password") },
                        supportingText = { Text("Used once to install the Codecks connection key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        DeckActionButton(
                            label = "Use saved password",
                            onClick = onUseSavedPassword,
                            enabled = state.operation == ConnectionOperation.Idle,
                            icon = Icons.Outlined.Key,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        )
                        DeckActionButton(
                            label = "Save to passwords",
                            onClick = onSavePassword,
                            enabled = state.host.isNotBlank() &&
                                state.user.isNotBlank() &&
                                state.password.isNotBlank() &&
                                parsedPort in 1..65535 &&
                                state.operation == ConnectionOperation.Idle,
                            icon = Icons.Outlined.Key,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        )
                    }
                }
                item {
                    DeckActionButton(
                        label = "Save Mac connection",
                        onClick = onAuthorize,
                        enabled = canAuthorize,
                        icon = Icons.Outlined.Key,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
                    )
                }
            } else {
                item {
                    ConnectionRepairPanel(
                        state = state,
                        onTest = onTest,
                        onRotateKey = onRotateKey,
                        onResetTrust = onResetTrust,
                        onRemoveTarget = onRemoveTarget,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            state.error?.let { error ->
                item {
                    ConnectionErrorCard(
                        error = error,
                        health = state.connectionHealth(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            state.message?.takeIf { state.config.isReady }?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
    }
}

@Composable
private fun MacPairingStepper(
    state: ConnectionUiState,
    trustedEndpoint: Boolean,
    canAuthorize: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasMac = state.host.isNotBlank()
    val done = state.config.isReady
    val steps = listOf(
        PairingStep(
            number = 1,
            title = "Find",
            detail = if (hasMac) state.host else "Scan or enter your Mac.",
            complete = hasMac,
            active = !hasMac,
        ),
        PairingStep(
            number = 2,
            title = "Trust",
            detail = when {
                trustedEndpoint -> "This Mac is trusted."
                hasMac -> "Confirm this is your Mac."
                else -> "Choose a Mac first."
            },
            complete = trustedEndpoint,
            active = hasMac && !trustedEndpoint,
        ),
        PairingStep(
            number = 3,
            title = "Authorize",
            detail = when {
                state.config.hasKey -> "Codecks key is saved."
                canAuthorize -> "Enter your Mac password once."
                trustedEndpoint -> "Enter your Mac password once."
                else -> "Trust the Mac first."
            },
            complete = state.config.hasKey,
            active = trustedEndpoint && !state.config.hasKey,
        ),
        PairingStep(
            number = 4,
            title = "Done",
            detail = if (done) "Deck, Clipboard, and Rules can use this Mac." else "Test connection after authorizing.",
            complete = done,
            active = state.config.hasKey && !done,
        ),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
            Text("Connect a Mac", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            steps.forEach { step ->
                PairingStepRow(step)
            }
        }
    }
}

private data class PairingStep(
    val number: Int,
    val title: String,
    val detail: String,
    val complete: Boolean,
    val active: Boolean,
)

@Composable
private fun PairingStepRow(step: PairingStep) {
    val color = when {
        step.complete -> MaterialTheme.colorScheme.primary
        step.active -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            color = if (step.complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (step.complete) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = if (step.complete) "✓" else step.number.toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.labelLarge, color = color)
            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupChecklist(
    hidHealth: HidHealth?,
    onOpenHidSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text("Fast setup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        listOf(
            "1. Put phone and Mac on same network",
            "2. Enable Remote Login on Mac",
            "3. Trust this Mac, then authorize once",
            "4. Pair Trackpad mouse/keyboard",
        ).forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (index == 3) Icons.Outlined.Computer else if (index < 2) Icons.Outlined.SettingsEthernet else Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hidHealth != null && !hidHealth.canSendInput) {
            DeckActionButton(
                label = "Open Trackpad setup",
                onClick = onOpenHidSetup,
                icon = Icons.Outlined.Computer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
        }
    }
}

@Composable
private fun ConnectionRepairPanel(
    state: ConnectionUiState,
    onTest: () -> Unit,
    onRotateKey: () -> Unit,
    onResetTrust: () -> Unit,
    onRemoveTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = state.operation == ConnectionOperation.Idle
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mac repair", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Start with Test. Rotate only when the key is stale. Reset Mac trust only if this Mac changed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionHealthRow(
                icon = Icons.Outlined.Computer,
                title = "Mac",
                value = "${state.config.user}@${state.config.host}:${state.config.port}",
            )
            ConnectionHealthRow(
                icon = Icons.Outlined.CheckCircle,
                title = "Mac trust",
                value = if (state.config.hostKey.isNotBlank()) "Trusted" else "Needs verification",
            )
            ConnectionHealthRow(
                icon = Icons.Outlined.Key,
                title = "Control key",
                value = if (state.config.hasKey) "Installed" else "Needs install",
            )
            DeckActionButton(
                label = "Test connection",
                onClick = onTest,
                enabled = idle,
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DeckActionButton(
                    label = "Rotate key",
                    onClick = onRotateKey,
                    enabled = idle,
                    icon = Icons.Outlined.Key,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
                DeckActionButton(
                    label = "Reset trust",
                    onClick = onResetTrust,
                    enabled = idle,
                    icon = Icons.Outlined.Refresh,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
            }
            DeckActionButton(
                label = "Remove this Mac",
                onClick = onRemoveTarget,
                enabled = idle,
                icon = Icons.Outlined.DeleteOutline,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun ConnectionHealthRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UnifiedSetupStatus(
    macHealth: ConnectionHealth,
    hidHealth: HidHealth?,
    operation: ConnectionOperation,
    onOpenHidSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val macReady = macHealth.isReady
    val hidReady = hidHealth?.canSendInput
    val title = when {
        macReady && hidReady == true -> "Mac ready / Trackpad ready"
        macReady && hidReady == false -> "Mac ready / Trackpad setup needed"
        !macReady && hidReady == true -> "Mac setup needed / Trackpad ready"
        else -> "Setup needed"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (operation != ConnectionOperation.Idle) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Icon(
                        imageVector = if (macReady && hidReady != false) Icons.Outlined.CheckCircle else Icons.Outlined.Computer,
                        contentDescription = null,
                        tint = if (macReady && hidReady != false) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        macHealth.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ReadinessLine(
                label = "Mac",
                ready = macReady,
                status = macHealth.statusLabel(),
                detail = macHealth.actionHint ?: macHealth.title,
            )
            hidHealth?.let { health ->
                ReadinessLine(
                    label = "Trackpad",
                    ready = health.canSendInput,
                    status = health.statusLabel(),
                    detail = health.detail,
                )
                if (!health.canSendInput) {
                    DeckActionButton(
                        label = "Open Trackpad setup",
                        onClick = onOpenHidSetup,
                        icon = Icons.Outlined.Computer,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadinessLine(
    label: String,
    ready: Boolean,
    status: String,
    detail: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Refresh,
            contentDescription = null,
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionErrorCard(
    error: String,
    health: ConnectionHealth,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Text("What went wrong", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(error, style = MaterialTheme.typography.bodyMedium)
            health.actionHint?.let { hint ->
                Text(hint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeckSectionLabel(title)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
