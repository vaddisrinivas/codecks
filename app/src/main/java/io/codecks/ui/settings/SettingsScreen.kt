package io.codecks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.codecks.HidState
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.context.ContextFeatureStatus
import io.codecks.data.context.NotificationPrivacySettings
import io.codecks.core.trackpad.TrackpadClockStyle
import io.codecks.core.trackpad.TrackpadFloatingMenuLayout
import io.codecks.core.trackpad.TrackpadGestureAction
import io.codecks.core.trackpad.TrackpadRailSide
import io.codecks.core.trackpad.TrackpadRotation
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.ActionIcon
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckComponentState
import io.codecks.ui.designsystem.DeckControlTile
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.ConnectionOperation
import io.codecks.ui.connection.ConnectionUiState
import io.codecks.ui.connection.canSendInput
import io.codecks.ui.connection.hidHealth
import io.codecks.ui.connection.isReady
import io.codecks.ui.connection.simpleConnectionHealth
import io.codecks.ui.connection.codecksReadiness
import io.codecks.ui.connection.statusLabel
import io.codecks.ui.icons.imageVector
import io.codecks.ui.theme.CodecksDeckStyle
import io.codecks.ui.theme.CodecksIconPack
import io.codecks.ui.theme.CodecksAccent
import io.codecks.ui.theme.CodecksBorderStyle
import io.codecks.ui.theme.CodecksShapeStyle
import io.codecks.ui.theme.CodecksSurfaceStyle
import io.codecks.ui.theme.CodecksThemeMode
import io.codecks.ui.theme.CodecksThemeSettings

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    connectionReady: Boolean,
    connectionHealth: ConnectionHealth = simpleConnectionHealth(connectionReady),
    hidState: HidState,
    bluetoothPermissionGranted: Boolean,
    notificationAccessReady: Boolean,
    notificationPrivacySettings: NotificationPrivacySettings = NotificationPrivacySettings(),
    contextFeatureStatus: ContextFeatureStatus = ContextFeatureStatus(
        compiledIntoBuild = true,
        componentEnabled = false,
        specialAccessGranted = notificationAccessReady,
        runtimeFeatureEnabled = false,
        privacyLaneEnabled = notificationPrivacySettings.showOnTrackpad,
        allowedPackageCount = notificationPrivacySettings.allowedPackages.size,
    ),
    clipboardSettings: ClipboardSyncSettings,
    aiProviderReady: Boolean,
    automationsReady: Boolean,
    fullscreen: Boolean = false,
    connectionState: ConnectionUiState? = null,
    onConnection: () -> Unit,
    onBluetooth: () -> Unit,
    onFullscreen: () -> Unit = {},
    onConnectionHostChange: (String) -> Unit = {},
    onConnectionPortChange: (String) -> Unit = {},
    onConnectionUserChange: (String) -> Unit = {},
    onConnectionPasswordChange: (String) -> Unit = {},
    onConnectionSelectHost: (String) -> Unit = {},
    onConnectionScan: () -> Unit = {},
    onConnectionScanLocalNetwork: () -> Unit = {},
    onConnectionVerifyHostKey: () -> Unit = {},
    onConnectionConfirmHostKey: () -> Unit = {},
    onConnectionAuthorize: () -> Unit = {},
    onConnectionRotateKey: () -> Unit = {},
    onConnectionResetTrust: () -> Unit = {},
    onConnectionRemoveTarget: () -> Unit = {},
    onConnectionSavePassword: () -> Unit = {},
    onConnectionUseSavedPassword: () -> Unit = {},
    onConnectionTest: () -> Unit = {},
    onOpenMacHelper: () -> Unit = {},
    onNotificationAccess: () -> Unit,
    onNotificationPrivacyChange: ((NotificationPrivacySettings) -> NotificationPrivacySettings) -> Unit = {},
    onAutomations: () -> Unit,
    onDevices: () -> Unit,
    onDeck: () -> Unit,
    onKeyboard: () -> Unit = {},
    onClipboard: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onClipboardModeChange: (ClipboardSyncMode) -> Unit = {},
    onClipboardIntervalChange: (Int) -> Unit = {},
    onAiBuilder: () -> Unit,
    onAppearance: () -> Unit,
    onAdvanced: () -> Unit,
    onDebugBundle: () -> Unit,
    themeSettings: CodecksThemeSettings = CodecksThemeSettings(),
    onThemeModeChange: (CodecksThemeMode) -> Unit = {},
    onThemeAccentChange: (CodecksAccent) -> Unit = {},
    onThemeSurfaceStyleChange: (CodecksSurfaceStyle) -> Unit = {},
    onThemeBorderStyleChange: (CodecksBorderStyle) -> Unit = {},
    onThemeShapeStyleChange: (CodecksShapeStyle) -> Unit = {},
    onDeckStyleChange: (CodecksDeckStyle) -> Unit = {},
    onIconPackChange: (CodecksIconPack) -> Unit = {},
    trackpadSettings: TrackpadSettings = TrackpadSettings(),
    onTrackpadSettingsChange: ((TrackpadSettings) -> TrackpadSettings) -> Unit = {},
    modifier: Modifier = Modifier,
    localOnlyV1: Boolean = false,
    debugBundleEnabled: Boolean = false,
    developerOptionsEnabled: Boolean = false,
    appVersionLabel: String = "Version",
    featureFlags: Map<FeatureFlag, Boolean> = emptyMap(),
    onFeatureFlagChange: (FeatureFlag, Boolean) -> Unit = { _, _ -> },
    onResetFeatureFlags: () -> Unit = {},
) {
    var showResetFlagsDialog by rememberSaveable { mutableStateOf(false) }
    var trackpadFineTuneOpen by rememberSaveable { mutableStateOf(false) }
    var macConnectionOpen by rememberSaveable { mutableStateOf(!connectionReady) }
    val hidHealth = hidState.hidHealth(bluetoothPermissionGranted)
    val readiness = codecksReadiness(connectionHealth, hidHealth, aiProviderReady)
    LaunchedEffect(connectionReady) {
        if (!connectionReady) macConnectionOpen = true
    }
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
                item { SettingsHero(readiness = readiness) }
                item { SectionLabel("Connections") }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Link,
                        title = "Mac actions",
                        summary = "Deck, clipboard, and Rules over a secure connection. ${connectionHealth.detail}",
                        value = connectionHealth.statusLabel(),
                        onClick = { macConnectionOpen = !macConnectionOpen },
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Mouse,
                        title = "Mac input",
                        summary = "Trackpad and Text over Bluetooth. ${hidHealth.detail}",
                        value = hidHealth.statusLabel(),
                        onClick = onBluetooth,
                    )
                }
                if (macConnectionOpen && connectionState != null) {
                    item {
                        MacConnectionSettingsPanel(
                            state = connectionState,
                            onHostChange = onConnectionHostChange,
                            onPortChange = onConnectionPortChange,
                            onUserChange = onConnectionUserChange,
                            onPasswordChange = onConnectionPasswordChange,
                            onSelectHost = onConnectionSelectHost,
                            onScan = onConnectionScan,
                            onScanLocalNetwork = onConnectionScanLocalNetwork,
                            onVerifyHostKey = onConnectionVerifyHostKey,
                            onConfirmHostKey = onConnectionConfirmHostKey,
                            onAuthorize = onConnectionAuthorize,
                            onRotateKey = onConnectionRotateKey,
                            onResetTrust = onConnectionResetTrust,
                            onRemoveTarget = onConnectionRemoveTarget,
                            onSavePassword = onConnectionSavePassword,
                            onUseSavedPassword = onConnectionUseSavedPassword,
                            onTest = onConnectionTest,
                            onOpenMacHelper = onOpenMacHelper,
                        )
                    }
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Fullscreen,
                        title = "App fullscreen",
                        summary = "Hide system bars and bottom navigation across Deck, Trackpad, Rules, AI, and Settings. Back exits fullscreen.",
                        value = if (fullscreen) "On" else "Off",
                        onClick = onFullscreen,
                    )
                }
                item { SectionLabel("Local data") }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.CheckCircle,
                        title = "Local-only launch mode",
                        summary = "No Codecks login, billing, server account, or public database is used in this version",
                        value = "On",
                        onClick = null,
                        showChevron = false,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "Privacy policy and data safety",
                        summary = if (localOnlyV1) {
                            "Decks, Macs, AI keys, clipboard settings, and notification preferences stay on this phone"
                        } else {
                            "Review what Codecks stores locally, what can leave the device, and Play Data Safety disclosures"
                        },
                        showChevron = false,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.FileDownload,
                        title = "Export local backup",
                        summary = "Save Deck and Rules as JSON; API keys, SSH keys, and connection secrets are excluded",
                        onClick = onExportBackup,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "Restore local backup",
                        summary = "Replace Deck and Rules from a Codecks backup file",
                        onClick = onImportBackup,
                    )
                }
                item { SectionLabel("Trackpad") }
                item {
                    TrackpadSettingsPanel(
                        settings = trackpadSettings,
                        onChange = onTrackpadSettingsChange,
                        fineTuneOpen = trackpadFineTuneOpen,
                        onFineTuneOpenChange = { trackpadFineTuneOpen = it },
                    )
                }
                item { SectionLabel("Deck") }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.GridView,
                        title = "Deck layout and buttons",
                        summary = "Reorder, resize, replace, test, and style the live Deck",
                        onClick = onDeck,
                    )
                }
                if (featureFlags.isOn(FeatureFlag.Keyboard)) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.Keyboard,
                            title = "Text to Mac",
                            summary = "Type short text over Bluetooth or paste long/unicode text through the Mac clipboard",
                            onClick = onKeyboard,
                        )
                    }
                }
                if (featureFlags.isOn(FeatureFlag.Clipboard)) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.ContentPaste,
                            title = "Clipboard bridge",
                            summary = "Manually send/pull clipboard text with optional visible auto sync",
                            onClick = onClipboard,
                        )
                    }
                }
                if (featureFlags.isOn(FeatureFlag.SmartDeck)) {
                    item { SectionLabel("Notification privacy") }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notification access",
                            summary = contextFeatureStatus.summary,
                            value = contextFeatureStatus.label,
                            onClick = onNotificationAccess,
                        )
                    }
                    item {
                        NotificationPrivacyPanel(
                            settings = notificationPrivacySettings,
                            onChange = onNotificationPrivacyChange,
                        )
                    }
                }
                if (featureFlags.isOn(FeatureFlag.Clipboard)) {
                    item { SectionLabel("Clipboard") }
                    item {
                        ClipboardSettingsPanel(
                            settings = clipboardSettings,
                            onModeChange = onClipboardModeChange,
                            onIntervalChange = onClipboardIntervalChange,
                        )
                    }
                }

                item { SectionLabel("Rules and AI") }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Psychology,
                        title = "AI Builder",
                        summary = if (aiProviderReady) "AI key saved for decks, buttons, and rules" else "Add an AI key before generating",
                        value = if (aiProviderReady) "Ready" else "Missing",
                        onClick = onAiBuilder,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Psychology,
                        title = "Rules",
                        summary = if (automationsReady) "Runnable workspace routines are ready" else "Needs Mac control channel",
                        value = if (automationsReady) "Ready" else "Setup",
                        onClick = onAutomations,
                    )
                }
                item { SectionLabel("Deck style") }
                item {
                    DeckStylePanel(
                        deckStyle = themeSettings.deckStyle,
                        onDeckStyleChange = onDeckStyleChange,
                    )
                }
                item { SectionLabel("Icon language") }
                item {
                    IconPackPanel(
                        iconPack = themeSettings.iconPack,
                        onIconPackChange = onIconPackChange,
                    )
                }
                item { SectionLabel("Support") }
                if (developerOptionsEnabled) {
                    item { SectionLabel("Developer mode") }
                    item {
                        FeatureFlagPanel(
                            featureFlags = featureFlags,
                            onFeatureFlagChange = onFeatureFlagChange,
                        )
                    }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.Settings,
                            title = "Reset developer flags",
                            summary = "Restore launch-ready defaults",
                            onClick = { showResetFlagsDialog = true },
                            showChevron = false,
                        )
                    }
                }
                item { SectionLabel("Support") }
                if (debugBundleEnabled) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.BugReport,
                            title = "Share debug report",
                            summary = "Export a redacted report for install and connection issues",
                            onClick = onDebugBundle,
                        )
                    }
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "About Codecks",
                        summary = appVersionLabel,
                        showChevron = false,
                    )
                }
        item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp)) }
    }
    if (showResetFlagsDialog) {
        AlertDialog(
            onDismissRequest = { showResetFlagsDialog = false },
            title = { Text("Reset feature flags?") },
            text = {
                Text("This restores the launch-ready default feature set and may hide experimental pages.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetFlagsDialog = false
                        onResetFeatureFlags()
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetFlagsDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MacConnectionSettingsPanel(
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
    val step = when {
        state.config.isReady -> MacPairingStep.Done
        state.host.isBlank() || state.user.isBlank() || parsedPort !in 1..65535 -> MacPairingStep.FindMac
        !trustedEndpoint -> MacPairingStep.TrustMac
        else -> MacPairingStep.Authorize
    }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
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
            MacPairingStepper(current = step)
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
            if (step == MacPairingStep.Authorize) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Mac password") },
                    supportingText = { Text("Used once; not stored unless you save it") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
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
                        onClick = onResetTrust,
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
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private enum class MacPairingStep(val label: String) {
    FindMac("Find"),
    TrustMac("Trust"),
    Authorize("Authorize"),
    Done("Done"),
}

@Composable
private fun MacPairingStepper(current: MacPairingStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        MacPairingStep.entries.forEach { step ->
            val selected = step == current
            val complete = step.ordinal < current.ordinal || current == MacPairingStep.Done
            Surface(
                color = when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    complete -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = when {
                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                    complete -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (complete && step != current) "✓ ${step.label}" else step.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun NotificationPrivacyPanel(
    settings: NotificationPrivacySettings,
    onChange: ((NotificationPrivacySettings) -> NotificationPrivacySettings) -> Unit,
) {
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification privacy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        notificationPrivacySummary(settings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingSwitch(
                label = "Show notification lane",
                checked = settings.showOnTrackpad,
                onCheckedChange = { value -> onChange { it.copy(showOnTrackpad = value) } },
            )
            SettingSwitch(
                label = "Show title and message",
                checked = settings.showContent,
                onCheckedChange = { value -> onChange { it.copy(showContent = value) } },
            )
            SettingSwitch(
                label = "Hide sensitive apps",
                checked = settings.hideSensitiveApps,
                onCheckedChange = { value -> onChange { it.copy(hideSensitiveApps = value) } },
            )
        }
    }
}

@Composable
private fun TrackpadSettingsPanel(
    settings: TrackpadSettings,
    onChange: ((TrackpadSettings) -> TrackpadSettings) -> Unit,
    fineTuneOpen: Boolean,
    onFineTuneOpenChange: (Boolean) -> Unit,
) {
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Mouse, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trackpad behavior", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everyday toggles first. Fine tuning stays tucked away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingSwitch(
                label = "OLED pointer trace",
                checked = settings.pointerTraceEnabled,
                onCheckedChange = { value -> onChange { it.copy(pointerTraceEnabled = value) } },
            )
            SettingSwitch(
                label = "Haptic ticks",
                checked = settings.hapticsEnabled,
                onCheckedChange = { value -> onChange { it.copy(hapticsEnabled = value) } },
            )
            SettingSwitch(
                label = "Scroll rail",
                checked = settings.scrollRailEnabled,
                onCheckedChange = { value -> onChange { it.copy(scrollRailEnabled = value) } },
            )
            SettingSwitch(
                label = "Slow scroll rail",
                checked = settings.precisionScrollRailEnabled,
                onCheckedChange = { value -> onChange { it.copy(precisionScrollRailEnabled = value) } },
            )
            Text("Custom gestures", style = MaterialTheme.typography.labelLarge)
            GestureActionPicker(
                label = "Two-finger double tap",
                selected = settings.twoFingerDoubleTapAction,
                onSelected = { action -> onChange { it.copy(twoFingerDoubleTapAction = action) } },
            )
            GestureActionPicker(
                label = "Three-finger double tap",
                selected = settings.threeFingerDoubleTapAction,
                onSelected = { action -> onChange { it.copy(threeFingerDoubleTapAction = action) } },
            )
            GestureActionPicker(
                label = "Three-finger hold",
                selected = settings.threeFingerHoldAction,
                onSelected = { action -> onChange { it.copy(threeFingerHoldAction = action) } },
            )
            GestureActionPicker(
                label = "Four-finger double tap",
                selected = settings.fourFingerDoubleTapAction,
                onSelected = { action -> onChange { it.copy(fourFingerDoubleTapAction = action) } },
            )
            GestureActionPicker(
                label = "Four-finger hold",
                selected = settings.fourFingerHoldAction,
                onSelected = { action -> onChange { it.copy(fourFingerHoldAction = action) } },
            )
            Text("Hand alignment", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TrackpadRailSide.entries.forEach { side ->
                    DeckFilterPill(
                        label = if (side == TrackpadRailSide.Left) "Left hand" else "Right hand",
                        selected = settings.railSide == side,
                        onClick = { onChange { it.copy(railSide = side) } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
            FineTuneToggleRow(
                open = fineTuneOpen,
                onClick = { onFineTuneOpenChange(!fineTuneOpen) },
            )
            if (fineTuneOpen) {
                SettingSlider(
                    label = "Pointer speed",
                    value = settings.pointerSpeed,
                    valueRange = 0.3f..1.35f,
                    onValueChange = { value -> onChange { it.copy(pointerSpeed = value) } },
                )
                SettingSlider(
                    label = "Acceleration",
                    value = settings.acceleration,
                    valueRange = 0.5f..1.75f,
                    onValueChange = { value -> onChange { it.copy(acceleration = value) } },
                )
                SettingSlider(
                    label = "Scroll speed",
                    value = settings.scrollSpeed,
                    valueRange = 0.35f..1.8f,
                    onValueChange = { value -> onChange { it.copy(scrollSpeed = value) } },
                )
                SettingSlider(
                    label = "Slow rail speed",
                    value = settings.precisionScrollSpeed,
                    valueRange = 0.1f..0.75f,
                    onValueChange = { value -> onChange { it.copy(precisionScrollSpeed = value) } },
                )
                SettingSlider(
                    label = "Slow rail acceleration",
                    value = settings.precisionScrollAcceleration,
                    valueRange = 0f..1f,
                    onValueChange = { value -> onChange { it.copy(precisionScrollAcceleration = value) } },
                )
                SettingSlider(
                    label = "Background opacity",
                    value = settings.backgroundOpacity,
                    valueRange = 0.05f..0.72f,
                    onValueChange = { value -> onChange { it.copy(backgroundOpacity = value) } },
                )
                SettingSlider(
                    label = "Double-tap window ${settings.doubleTapTimeoutMillis}ms",
                    value = settings.doubleTapTimeoutMillis.toFloat(),
                    valueRange = 350f..900f,
                    onValueChange = { value -> onChange { it.copy(doubleTapTimeoutMillis = value.toInt()) } },
                )
                SettingSlider(
                    label = "Gesture hold ${settings.multiFingerHoldMillis}ms",
                    value = settings.multiFingerHoldMillis.toFloat(),
                    valueRange = 350f..1_000f,
                    onValueChange = { value -> onChange { it.copy(multiFingerHoldMillis = value.toInt()) } },
                )
                Text("Clock style", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TrackpadClockStyle.entries.forEach { style ->
                        DeckFilterPill(
                            label = style.label,
                            selected = settings.clockStyle == style,
                            onClick = { onChange { it.copy(clockStyle = style) } },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                Text("Floating menu", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TrackpadFloatingMenuLayout.entries.forEach { layout ->
                        DeckFilterPill(
                            label = layout.label,
                            selected = settings.floatingMenuLayout == layout,
                            onClick = { onChange { it.copy(floatingMenuLayout = layout) } },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                Text("Rotation", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TrackpadRotation.entries.forEach { rotation ->
                        DeckFilterPill(
                            label = rotation.label,
                            selected = settings.rotation == rotation,
                            onClick = { onChange { it.copy(rotation = rotation) } },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureActionPicker(
    label: String,
    selected: TrackpadGestureAction,
    onSelected: (TrackpadGestureAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            TrackpadGestureAction.entries.forEach { action ->
                DeckFilterPill(
                    label = action.label,
                    selected = selected == action,
                    onClick = { onSelected(action) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun FineTuneToggleRow(
    open: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fine tuning", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pointer speed, acceleration, scroll, clock, menu, rotation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (open) "Hide" else "Show",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ClipboardSettingsPanel(
    settings: ClipboardSyncSettings,
    onModeChange: (ClipboardSyncMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clipboard sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        settings.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(settings.valueLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClipboardSyncMode.entries.forEach { mode ->
                    DeckFilterPill(
                        label = mode.label,
                        selected = settings.mode == mode,
                        onClick = { onModeChange(mode) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
            SettingSlider(
                label = "Sync every ${settings.intervalMinutes} min",
                value = settings.intervalMinutes.toFloat(),
                valueRange = 1f..60f,
                onValueChange = { value -> onIntervalChange(value.toInt().coerceIn(1, 60)) },
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DeckStylePanel(
    deckStyle: CodecksDeckStyle,
    onDeckStyleChange: (CodecksDeckStyle) -> Unit,
) {
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deck style", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick the deck personality: classic green, neon, candy, glass, mono, or compact tiles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    CodecksDeckStyle.entries,
                    key = CodecksDeckStyle::name,
                ) { style ->
                    DeckStylePreviewCard(
                        style = style,
                        selected = deckStyle == style,
                        onClick = { onDeckStyleChange(style) },
                    )
                }
            }
            Text(
                text = deckStyle.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeckStylePreviewCard(
    style: CodecksDeckStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    CodecksPanel(
        selected = selected,
        modifier = Modifier
            .width(224.dp)
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DeckControlTile(
                    label = "Deck",
                    icon = ActionIcon.Apps.imageVector(),
                    state = DeckComponentState.Selected,
                    showLabel = false,
                    deckStyle = style,
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(60.dp),
                )
                DeckControlTile(
                    label = "Trackpad",
                    icon = ActionIcon.Mouse.imageVector(),
                    showLabel = false,
                    deckStyle = style,
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(60.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DeckControlTile(
                    label = "AI",
                    icon = ActionIcon.Control.imageVector(),
                    state = DeckComponentState.Running,
                    showLabel = false,
                    deckStyle = style,
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(60.dp),
                )
                DeckControlTile(
                    label = "Keys",
                    icon = ActionIcon.Keyboard.imageVector(),
                    danger = style == CodecksDeckStyle.NothingMonoDeck,
                    showLabel = false,
                    deckStyle = style,
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(60.dp),
                )
            }
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun IconPackPanel(
    iconPack: CodecksIconPack,
    onIconPackChange: (CodecksIconPack) -> Unit,
) {
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Text("Icon pack", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CodecksIconPack.entries, key = CodecksIconPack::name) { pack ->
                    CodecksPanel(
                        selected = iconPack == pack,
                        modifier = Modifier
                            .width(164.dp)
                            .clickable { onIconPackChange(pack) },
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(ActionIcon.Finder.imageVector(pack), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Icon(ActionIcon.Terminal.imageVector(pack), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Icon(ActionIcon.Control.imageVector(pack), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            }
                            Text(pack.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            Text(
                iconPack.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ThemeModePanel(
    themeSettings: CodecksThemeSettings,
    onThemeModeChange: (CodecksThemeMode) -> Unit,
    onAccentChange: (CodecksAccent) -> Unit = {},
    onSurfaceStyleChange: (CodecksSurfaceStyle) -> Unit = {},
    onBorderStyleChange: (CodecksBorderStyle) -> Unit = {},
    onShapeStyleChange: (CodecksShapeStyle) -> Unit = {},
    showMode: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (showMode) {
            Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CodecksThemeMode.entries, key = CodecksThemeMode::name) { mode ->
                    DeckFilterPill(
                        label = mode.label,
                        selected = themeSettings.mode == mode,
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Text(
                text = themeSettings.mode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            Text(
                "OLED black stays as the base; color, surface energy, borders, and shape remain customizable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CodecksAccent.entries, key = CodecksAccent::name) { accent ->
                DeckFilterPill(
                    label = accent.label,
                    selected = themeSettings.accent == accent,
                    onClick = { onAccentChange(accent) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Text("Surfaces", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CodecksSurfaceStyle.entries, key = CodecksSurfaceStyle::name) { style ->
                DeckFilterPill(
                    label = style.label,
                    selected = themeSettings.surfaceStyle == style,
                    onClick = { onSurfaceStyleChange(style) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Text(themeSettings.surfaceStyle.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Borders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CodecksBorderStyle.entries, key = CodecksBorderStyle::name) { style ->
                DeckFilterPill(
                    label = style.label,
                    selected = themeSettings.borderStyle == style,
                    onClick = { onBorderStyleChange(style) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Text(themeSettings.borderStyle.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Shape", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CodecksShapeStyle.entries, key = CodecksShapeStyle::name) { style ->
                DeckFilterPill(
                    label = style.label,
                    selected = themeSettings.shapeStyle == style,
                    onClick = { onShapeStyleChange(style) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Text(themeSettings.shapeStyle.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsHero(
    readiness: io.codecks.ui.connection.CodecksReadiness,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(readiness.title, style = MaterialTheme.typography.titleLarge)
        Text(
            readiness.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupChecklist(
    connectionReady: Boolean,
    connectionHealth: ConnectionHealth,
    hidState: HidState,
    bluetoothPermissionGranted: Boolean,
    notificationAccessReady: Boolean,
    aiProviderReady: Boolean,
    automationsReady: Boolean,
    featureFlags: Map<FeatureFlag, Boolean>,
    onConnection: () -> Unit,
    onBluetooth: () -> Unit,
    onNotificationAccess: () -> Unit,
    onAiBuilder: () -> Unit,
    onAutomations: () -> Unit,
) {
    val macReady = connectionReady && connectionHealth.isReady
    val hidHealth = hidState.hidHealth(bluetoothPermissionGranted)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth()) {
        SetupRow(
            title = "Mac control channel",
            summary = connectionHealth.detail,
            ready = macReady,
            statusLabel = connectionHealth.statusLabel(),
            onClick = onConnection,
        )
        SetupRow(
            title = "Trackpad Mac",
            summary = hidHealth.detail,
            ready = hidHealth.canSendInput,
            statusLabel = hidHealth.statusLabel(),
            onClick = onBluetooth,
        )
        if (featureFlags.isOn(FeatureFlag.SmartDeck)) {
            SetupRow(
                title = "Notification access",
                summary = if (notificationAccessReady) {
                    "Trackpad can show approved notification sources"
                } else {
                    "Optional: enable Android notification access"
                },
                ready = notificationAccessReady,
                statusLabel = if (notificationAccessReady) "Ready" else "Optional",
                required = false,
                onClick = onNotificationAccess,
            )
        }
        if (featureFlags.isOn(FeatureFlag.Ai)) {
            SetupRow(
                title = "AI Builder",
                summary = if (aiProviderReady) {
                    "AI key saved"
                } else {
                    "Save an AI key"
                },
                ready = aiProviderReady,
                statusLabel = if (aiProviderReady) "Ready" else "Optional",
                required = false,
                onClick = onAiBuilder,
            )
        }
        if (featureFlags.isOn(FeatureFlag.Automations)) {
            SetupRow(
                title = "Rules",
                summary = if (automationsReady) "Ready to run" else "Needs Mac control channel",
                ready = automationsReady,
                onClick = onAutomations,
            )
        }
    }
}

@Composable
private fun SetupRow(
    title: String,
    summary: String,
    ready: Boolean,
    statusLabel: String = if (ready) "Ready" else "Setup needed",
    required: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = {
            Icon(
                imageVector = if (ready) Icons.Outlined.CheckCircle else if (required) Icons.Outlined.Settings else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (ready || required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                statusLabel,
                color = if (ready || required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FeatureFlagPanel(
    featureFlags: Map<FeatureFlag, Boolean>,
    onFeatureFlagChange: (FeatureFlag, Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val labsEnabled = featureFlags.isOn(FeatureFlag.Labs)
        buildList {
            add(FlagSpec(FeatureFlag.Deck, "Deck", "Home deck and scriptable controls", Icons.Outlined.GridView))
            add(FlagSpec(FeatureFlag.Trackpad, "Trackpad", "Full-screen pointer, gestures, scroll zone", Icons.Outlined.Mouse))
            add(FlagSpec(FeatureFlag.Ai, "AI Builder", "AI keys, decks, buttons, rules", Icons.Outlined.AutoAwesome))
            add(FlagSpec(FeatureFlag.Automations, "Rules", "Runnable workspace routines", Icons.Outlined.Psychology))
            add(FlagSpec(FeatureFlag.Keyboard, "Keyboard controls", "Keyboard surface inside Trackpad", Icons.Outlined.Keyboard))
            add(FlagSpec(FeatureFlag.Clipboard, "Clipboard controls", "Clipboard surface and sync settings", Icons.Outlined.ContentPaste))
            add(FlagSpec(FeatureFlag.Labs, "Labs", "Experimental inputs stay hidden unless enabled", Icons.Outlined.Terminal))
            if (labsEnabled) {
                add(FlagSpec(FeatureFlag.SmartSuggestions, "Smart suggestions", "Local deterministic suggestions; no AI ranking", Icons.Outlined.AutoAwesome))
                add(FlagSpec(FeatureFlag.SmartDeck, "Smart Deck", "Temporary Deck suggestion row", Icons.Outlined.GridView))
                add(FlagSpec(FeatureFlag.SmartKeyboard, "Smart Keyboard", "Future keyboard suggestions", Icons.Outlined.Keyboard))
                add(FlagSpec(FeatureFlag.SmartClipboard, "Smart Clipboard", "Future clipboard classification", Icons.Outlined.ContentPaste))
                add(FlagSpec(FeatureFlag.SmartRules, "Smart Rules", "Future rule drafts", Icons.Outlined.Psychology))
                add(FlagSpec(FeatureFlag.SmartSettings, "Smart Settings", "Future settings recommendations", Icons.Outlined.Settings))
                add(FlagSpec(FeatureFlag.SmartTrackpadSuggest, "Smart Trackpad suggest", "Future trackpad destination hints", Icons.Outlined.Mouse))
                add(FlagSpec(FeatureFlag.SmartTrackpadSnap, "Smart Trackpad snap", "Future optional pointer snap", Icons.Outlined.Mouse))
                add(FlagSpec(FeatureFlag.SmartOcr, "Smart OCR", "Future local OCR fallback", Icons.Outlined.Search))
                add(FlagSpec(FeatureFlag.LabAirMouse, "Labs: Air mouse", "Tilt phone to move pointer", Icons.Outlined.Mouse))
                add(FlagSpec(FeatureFlag.LabAirTouch, "Labs: S Pen air touch", "Experimental fake-monitor calibration", Icons.Outlined.Mouse))
                add(FlagSpec(FeatureFlag.LabBackTap, "Labs: back tap", "Device back tap can click", Icons.Outlined.Mouse))
                add(FlagSpec(FeatureFlag.LabVolumeKeys, "Labs: volume keys", "Use volume keys for scroll", Icons.Outlined.Mouse))
            }
        }.forEach { spec ->
            FeatureFlagRow(
                spec = spec,
                checked = featureFlags.isOn(spec.flag),
                onCheckedChange = { onFeatureFlagChange(spec.flag, it) },
            )
        }
    }
}

private fun notificationPrivacySummary(settings: NotificationPrivacySettings): String = when {
    !settings.showOnTrackpad -> "Phone notifications stay off the Trackpad background"
    settings.allowedPackages.isNotEmpty() && settings.showContent -> "Only approved apps can show title and message text"
    settings.allowedPackages.isNotEmpty() -> "Only approved apps can appear; content stays hidden"
    settings.showContent -> "Trackpad can show notification title and message text"
    settings.hideSensitiveApps -> "Private mode: app names only; sensitive apps hidden"
    else -> "Private mode: app names only"
}

private fun bluetoothSummary(state: HidState, permissionGranted: Boolean): String =
    state.hidHealth(permissionGranted).detail

private fun ClipboardSyncSettings.summary(): String = when (mode) {
    ClipboardSyncMode.Off -> "Automatic sync is off"
    ClipboardSyncMode.PhoneToMac -> "Phone to Mac every $intervalMinutes min"
    ClipboardSyncMode.MacToPhone -> "Mac to phone every $intervalMinutes min"
    ClipboardSyncMode.Bidirectional -> "Two-way sync every $intervalMinutes min"
}

private fun ClipboardSyncSettings.valueLabel(): String =
    if (mode == ClipboardSyncMode.Off) "Off" else "${intervalMinutes}m"

private val ClipboardSyncMode.label: String
    get() = when (this) {
        ClipboardSyncMode.Off -> "Off"
        ClipboardSyncMode.PhoneToMac -> "Phone"
        ClipboardSyncMode.MacToPhone -> "Mac"
        ClipboardSyncMode.Bidirectional -> "Both"
    }

@Composable
private fun FeatureFlagRow(
    spec: FlagSpec,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(spec.title) },
        supportingContent = { Text(spec.summary) },
        leadingContent = {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

private data class FlagSpec(
    val flag: FeatureFlag,
    val title: String,
    val summary: String,
    val icon: ImageVector,
)

private fun Map<FeatureFlag, Boolean>.isOn(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 24.dp))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    value: String? = null,
    showChevron: Boolean = true,
) {
    val baseModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    CodecksPanel(
        modifier = if (onClick == null) baseModifier else baseModifier.clickable(onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (value != null) {
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (showChevron && onClick != null) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
