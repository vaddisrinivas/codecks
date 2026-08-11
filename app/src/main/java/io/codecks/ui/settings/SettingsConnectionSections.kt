package io.codecks.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.app.AccessibleStatus
import io.codecks.ui.app.AccessibleStatusKind
import io.codecks.ui.connection.ConnectionOperation
import io.codecks.ui.connection.ConnectionUiState
import io.codecks.ui.connection.SetupStep
import io.codecks.ui.connection.connectionDiagnostic
import io.codecks.ui.connection.ConnectionRepair
import io.codecks.ui.connection.statusLabel
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.ui.designsystem.codecksSemanticColorTokens

@Composable
internal fun UpdateSettingsPanel(
    state: UpdateSettingsState,
    onCheck: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val failureFocusRequester = remember { FocusRequester() }
    val failureKey = (state as? UpdateSettingsState.Failure)?.kind?.name
    LaunchedEffect(failureKey) {
        if (failureKey != null) failureFocusRequester.requestFocus()
    }
    val (label, detail, kind) = when (state) {
        UpdateSettingsState.Idle -> Triple(
            "Update check idle",
            "Check GitHub manually for a newer Codecks release.",
            AccessibleStatusKind.Neutral,
        )
        UpdateSettingsState.Checking -> Triple(
            "Checking for updates",
            "Reading release metadata from GitHub.",
            AccessibleStatusKind.Busy,
        )
        is UpdateSettingsState.UpToDate -> Triple(
            "Up to date",
            "This Codecks version is current.",
            AccessibleStatusKind.Success,
        )
        is UpdateSettingsState.Available -> Triple(
            "Update available",
            "A newer Codecks release is available on GitHub.",
            AccessibleStatusKind.Information,
        )
        is UpdateSettingsState.Failure -> Triple(
            "Update check failed",
            when (state.kind) {
                UpdateFailureKind.NetworkOrMetadata -> "Could not verify the latest release. Try again later."
                UpdateFailureKind.BlockedReleaseUrl -> "GitHub returned a release link Codecks will not open."
                UpdateFailureKind.NoBrowser -> "No browser could open the GitHub release page."
                UpdateFailureKind.NotForeground -> "Keep Settings open while Codecks checks for updates."
            },
            AccessibleStatusKind.Error,
        )
    }
    CodecksPanel(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AccessibleStatus(
            stateDescription = label,
            detail = detail,
            kind = kind,
            announceChanges = state != UpdateSettingsState.Idle,
            modifier = if (failureKey != null) {
                Modifier.focusRequester(failureFocusRequester).focusable()
            } else {
                Modifier
            },
        )
        DeckActionButton(
            label = if (state == UpdateSettingsState.Checking) "Checking…" else "Check for updates",
            onClick = onCheck,
            enabled = state != UpdateSettingsState.Checking,
            icon = Icons.Outlined.SystemUpdate,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
        if (state is UpdateSettingsState.Available) {
            DeckActionButton(
                label = "Open GitHub release",
                onClick = onOpenRelease,
                icon = Icons.Outlined.OpenInBrowser,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
internal fun CodecksHelperPanel(
    state: CodecksHelperUiState,
    onConnect: () -> Unit,
    onOpenSetup: () -> Unit,
    onSearch: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("Codecks") }
    val largeText = LocalDensity.current.fontScale >= 2f
    val colors = codecksSemanticColorTokens()
    val connectRepair = state.repairs.firstOrNull { it == ConnectionRepair.RetryNow }
    val setupRepair = state.repairs.firstOrNull {
        it == ConnectionRepair.PairHelper || it == ConnectionRepair.OpenHelper || it == ConnectionRepair.ReviewIdentity
    }
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = CodecksDesignTokens.Spacing.page,
            vertical = CodecksDesignTokens.Spacing.sm,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap),
            modifier = Modifier.padding(CodecksDesignTokens.Spacing.lg),
        ) {
            Text("Codecks helper", style = MaterialTheme.typography.titleMedium)
            Text(
                state.statusDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentMuted,
            )
            state.supportCode?.let {
                Text(
                    "Support code $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.contentMuted,
                )
            }
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap)) {
                    HelperStatusBadges(state)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap), modifier = Modifier.fillMaxWidth()) {
                    HelperStatusBadges(state)
                }
            }
            state.pairedDisplayName?.let { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.contentMuted,
                )
            }
            if (largeText) Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap)) {
                DeckActionButton(
                    label = if (state.statusLabel == "Connecting…") "Connecting…" else connectRepair?.label ?: "Connect helper",
                    onClick = onConnect,
                    enabled = state.canConnect && connectRepair != null,
                    icon = Icons.Outlined.Link,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                )
                DeckActionButton(
                    label = setupRepair?.label ?: if (state.hasPairing) "Pairing JSON" else "Open setup",
                    onClick = onOpenSetup,
                    enabled = true,
                    icon = Icons.Outlined.Terminal,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                )
            } else Row(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap), modifier = Modifier.fillMaxWidth()) {
                DeckActionButton(
                    label = if (state.statusLabel == "Connecting…") "Connecting…" else connectRepair?.label ?: "Connect helper",
                    onClick = onConnect,
                    enabled = state.canConnect && connectRepair != null,
                    icon = Icons.Outlined.Link,
                    modifier = Modifier.weight(1f).heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                )
                DeckActionButton(
                    label = setupRepair?.label ?: if (state.hasPairing) "Pairing JSON" else "Open setup",
                    onClick = onOpenSetup,
                    enabled = true,
                    icon = Icons.Outlined.Terminal,
                    modifier = Modifier.weight(1f).heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                )
            }
            HorizontalDivider()
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Mac") },
                supportingText = { Text("Runs Spotlight through Codecks helper and returns a result count.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DeckActionButton(
                label = "Search Mac with Codecks",
                onClick = { onSearch(query.trim()) },
                enabled = state.canRunActions && query.isNotBlank(),
                icon = Icons.Outlined.Search,
                modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
            )
        }
    }
}

@Composable
private fun HelperStatusBadge(label: String, state: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .heightIn(min = CodecksDesignTokens.Size.minTouchTarget)
            .semantics(mergeDescendants = true) { stateDescription = state },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.lg, vertical = CodecksDesignTokens.Spacing.sm),
        )
    }
}

@Composable
private fun HelperStatusBadges(state: CodecksHelperUiState) {
    HelperStatusBadge(
        label = state.statusLabel,
        state = if (state.canRunActions) "Ready" else state.statusLabel,
    )
    HelperStatusBadge(
        label = if (state.discoveredCount == 1) "1 nearby" else "${state.discoveredCount} nearby",
        state = if (state.discoveredCount > 0) "Helper discovered" else "No nearby helper",
    )
}

@Composable
internal fun MacConnectionSettingsPanel(
    state: ConnectionUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSelectHost: (String) -> Unit,
    onScan: () -> Unit,
    onScanLocalNetwork: () -> Unit,
    onVerifyHostKey: () -> Unit,
    onConfirmHostKey: () -> Unit,
    onAuthorize: () -> Unit,
    onRotateKey: () -> Unit,
    onResetTrust: () -> Unit,
    onRemoveTarget: () -> Unit,
    onSavePassword: () -> Unit,
    onUseSavedPassword: () -> Unit,
    onTest: () -> Unit,
    onReactiveHelperPairingImport: (String) -> Unit,
    onOpenMacHelper: () -> Unit,
) {
    val parsedPort = state.port.toIntOrNull()
    val trustedEndpoint = state.config.host == state.host.trim() &&
        state.config.user == state.user.trim() &&
        state.config.port == parsedPort &&
        state.config.hostKey.isNotBlank()
    val idle = state.operation == ConnectionOperation.Idle
    val canVerify = state.host.isNotBlank() && state.user.isNotBlank() && parsedPort in 1..65535 && idle
    val canAuthorize = state.host.isNotBlank() &&
        state.user.isNotBlank() &&
        state.password.isNotEmpty() &&
        parsedPort in 1..65535 &&
        trustedEndpoint &&
        idle
    val step = when (state.setupSnapshot.firstMandatoryNotPassed) {
        SetupStep.FindMac -> MacPairingStep.FindMac
        SetupStep.TrustMac -> MacPairingStep.TrustMac
        SetupStep.Authorize -> MacPairingStep.Authorize
        SetupStep.VerifyControls,
        null,
        -> MacPairingStep.Done
    }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var reactiveHelperPairingJson by rememberSaveable { mutableStateOf("") }
    var identityReviewOpen by rememberSaveable { mutableStateOf(false) }
    val blockingDiagnostic = state.error?.let { state.connectionDiagnostic() }
    val credentialRepairRequired = blockingDiagnostic?.repairActions?.contains(
        ConnectionRepair.ReenterCredentials,
    ) == true
    val failureFocusRequester = remember { FocusRequester() }
    val credentialFocusRequester = remember { FocusRequester() }
    val failureKey = blockingDiagnostic?.let { it.issueCode?.name ?: it.state.name }
    LaunchedEffect(failureKey) {
        if (failureKey != null && !credentialRepairRequired) failureFocusRequester.requestFocus()
    }
    LaunchedEffect(credentialRepairRequired) {
        if (credentialRepairRequired) credentialFocusRequester.requestFocus()
    }
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Text("Connect a Mac", style = MaterialTheme.typography.titleMedium)
            Text(
                "One setup flow for Deck, Clipboard, and Rules. Your password is used once; Codecks keeps a secure key after pairing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MacPairingStepper(current = step, snapshot = state.setupSnapshot)
            HorizontalDivider()
            when (step) {
                MacPairingStep.FindMac -> {
                    Text("Find", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Scan your network, pick your Mac, or enter its hostname manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DeckActionButton(
                        label = if (state.operation == ConnectionOperation.Scanning) "Scanning…" else "Find Macs",
                        onClick = onScan,
                        enabled = idle,
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                }
                MacPairingStep.TrustMac -> {
                    Text("Trust Mac", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Confirm this is your Mac before installing the Codecks control key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DeckActionButton(
                        label = if (state.operation == ConnectionOperation.Verifying) "Checking…" else "Trust this Mac",
                        onClick = onVerifyHostKey,
                        enabled = canVerify,
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                    state.pendingFingerprint?.let { fingerprint ->
                        Text(fingerprint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DeckActionButton(
                            label = "Confirm this is my Mac",
                            onClick = onConfirmHostKey,
                            enabled = idle,
                            icon = Icons.Outlined.CheckCircle,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        )
                    }
                }
                MacPairingStep.Authorize -> {
                    Text("Authorize once", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Enter your Mac password once to install a secure key. The password is not stored unless you choose Save password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MacPairingStep.Done -> {
                    Text("Mac paired", style = MaterialTheme.typography.titleSmall)
                    Text(
                        state.config.host.ifBlank { "Saved Mac" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        DeckActionButton(
                            label = "Test",
                            onClick = onTest,
                            enabled = idle,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        )
                        DeckActionButton(
                            label = "Rotate key",
                            onClick = onRotateKey,
                            enabled = idle,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = onHostChange,
                    label = { Text("Mac") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.38f),
                )
            }
            if (state.discoveredHosts.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.discoveredHosts, key = { it }) { host ->
                        DeckFilterPill(
                            label = host,
                            selected = state.host == host,
                            onClick = { onSelectHost(host) },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.user,
                onValueChange = onUserChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (step == MacPairingStep.Authorize || credentialRepairRequired) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Mac password") },
                    supportingText = { Text("Used once; not stored unless you save it") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(credentialFocusRequester)
                        .testTag("mac-credential-repair-editor"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DeckActionButton(
                        label = "Use saved password",
                        onClick = onUseSavedPassword,
                        enabled = idle,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    )
                    DeckActionButton(
                        label = "Save password",
                        onClick = onSavePassword,
                        enabled = state.host.isNotBlank() && state.user.isNotBlank() && state.password.isNotBlank() && idle,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    )
                }
                DeckActionButton(
                    label = if (state.operation == ConnectionOperation.Connecting) "Saving…" else "Save Mac",
                    onClick = onAuthorize,
                    enabled = canAuthorize,
                    icon = Icons.Outlined.Link,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                )
            }
            SettingsRow(
                icon = Icons.Outlined.Terminal,
                title = "Advanced Mac controls",
                summary = "Manual scan, reset trust, remove Mac, key maintenance, and GitHub helper page.",
                value = if (advancedOpen) "Open" else null,
                onClick = { advancedOpen = !advancedOpen },
            )
            if (advancedOpen) {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "Open GitHub helper page",
                    summary = "Browser-only fallback with copyable JS snippets. Use this only if in-app SSH pairing gets stuck.",
                    onClick = onOpenMacHelper,
                )
                OutlinedTextField(
                    value = reactiveHelperPairingJson,
                    onValueChange = { reactiveHelperPairingJson = it },
                    label = { Text("Codecks helper pairing JSON") },
                    supportingText = { Text("Paste output from: codecks-mac-helper print-pairing-json") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                DeckActionButton(
                    label = "Import helper pairing",
                    onClick = {
                        onReactiveHelperPairingImport(reactiveHelperPairingJson)
                        reactiveHelperPairingJson = ""
                    },
                    enabled = reactiveHelperPairingJson.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DeckActionButton(
                        label = "Test",
                        onClick = onTest,
                        enabled = idle,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    )
                    DeckActionButton(
                        label = "Rotate key",
                        onClick = onRotateKey,
                        enabled = idle,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DeckActionButton(
                        label = "Reset trust",
                        onClick = { identityReviewOpen = true },
                        enabled = idle,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    )
                    DeckActionButton(
                        label = "Remove Mac",
                        onClick = onRemoveTarget,
                        enabled = idle,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    )
                }
                DeckActionButton(
                    label = "Scan network",
                    onClick = onScanLocalNetwork,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            blockingDiagnostic?.let { diagnostic ->
                AccessibleStatus(
                    stateDescription = diagnostic.title,
                    detail = buildString {
                        append(diagnostic.detail)
                        diagnostic.repairActions.firstOrNull()?.let { append(" Next: ${it.label}") }
                        diagnostic.supportCode?.let { append(" Support code $it.") }
                    },
                    kind = AccessibleStatusKind.Error,
                    announceChanges = true,
                    modifier = Modifier
                        .focusRequester(failureFocusRequester)
                        .focusable(),
                )
                diagnostic.repairActions.firstOrNull()?.let { repair ->
                    when (repair) {
                        ConnectionRepair.RetryNow -> DeckActionButton(
                            label = repair.label,
                            onClick = onTest,
                            enabled = idle,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("mac-repair-retry"),
                        )
                        ConnectionRepair.ReviewIdentity -> DeckActionButton(
                            label = repair.label,
                            onClick = { identityReviewOpen = true },
                            enabled = idle,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("mac-repair-identity"),
                        )
                        ConnectionRepair.ReenterCredentials -> Unit // Password editor is opened and focused above.
                        ConnectionRepair.OpenConnectionSetup -> Unit // This panel is the setup destination.
                        else -> Unit
                    }
                }
            }
        }
    }
    if (identityReviewOpen) {
        AlertDialog(
            onDismissRequest = { identityReviewOpen = false },
            title = { Text("Review changed Mac identity") },
            text = {
                Text("Reset trust only after confirming this is the same Mac. Codecks will require the new fingerprint before reconnecting.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        identityReviewOpen = false
                        onResetTrust()
                    },
                    modifier = Modifier.testTag("mac-repair-identity-confirm"),
                ) { Text("Reset trust and review") }
            },
            dismissButton = {
                TextButton(
                    onClick = { identityReviewOpen = false },
                    modifier = Modifier.testTag("mac-repair-identity-cancel"),
                ) { Text("Cancel") }
            },
        )
    }
}

internal enum class MacPairingStep(val label: String) {
    FindMac("Find"),
    TrustMac("Trust"),
    Authorize("Authorize"),
    Done("Done"),
}
