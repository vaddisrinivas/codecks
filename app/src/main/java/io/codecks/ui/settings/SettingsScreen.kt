package io.codecks.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import io.codecks.HidState
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.context.ContextFeatureStatus
import io.codecks.data.context.NotificationPrivacySettings
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.backup.RestorePlan
import io.codecks.launcher.LauncherIcon
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.ConnectionUiState
import io.codecks.ui.connection.hidHealth
import io.codecks.ui.connection.simpleConnectionHealth
import io.codecks.ui.connection.toUnifiedConnectionPresentation
import io.codecks.ui.connection.codecksReadiness
import io.codecks.ui.connection.BluetoothPermissionState
import io.codecks.ui.connection.evaluateRuntimeSetupCompletion
import io.codecks.ui.connection.HidTerminalReceipt
import io.codecks.ui.connection.statusLabel
import io.codecks.ui.theme.CodecksDeckStyle
import io.codecks.ui.theme.CodecksIconPack
import io.codecks.ui.theme.CodecksAccent
import io.codecks.ui.theme.CodecksBorderStyle
import io.codecks.ui.theme.CodecksShapeStyle
import io.codecks.ui.theme.CodecksSurfaceStyle
import io.codecks.ui.theme.CodecksThemeMode
import io.codecks.ui.theme.CodecksThemeSettings
import io.codecks.ui.theme.ThemeStudioPanel

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    connectionReady: Boolean,
    connectionHealth: ConnectionHealth = simpleConnectionHealth(connectionReady),
    hidState: HidState,
    bluetoothPermissionGranted: Boolean,
    bluetoothPermissionPermanentlyDenied: Boolean = false,
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
    hidTerminalReceipt: HidTerminalReceipt? = null,
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
    onReactiveHelperPairingImport: (String) -> Unit = {},
    onOpenMacHelper: () -> Unit = {},
    codecksHelperState: CodecksHelperUiState = CodecksHelperUiState(),
    onCodecksHelperConnect: () -> Unit = {},
    onCodecksHelperSearch: (String) -> Unit = {},
    onNotificationAccess: () -> Unit,
    onNotificationPrivacyChange: ((NotificationPrivacySettings) -> NotificationPrivacySettings) -> Unit = {},
    onAutomations: () -> Unit,
    onDevices: () -> Unit,
    onDeck: () -> Unit,
    onKeyboard: () -> Unit = {},
    onClipboard: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    pendingBackupRecovery: Boolean = false,
    corruptBackupRecovery: Boolean = false,
    onRecoverBackup: () -> Unit = {},
    restorePlan: RestorePlan? = null,
    onCancelRestore: () -> Unit = {},
    onConfirmRestore: (String) -> Unit = {},
    onClipboardModeChange: (ClipboardSyncMode) -> Unit = {},
    onClipboardIntervalChange: (Int) -> Unit = {},
    onAiBuilder: () -> Unit,
    onAppearance: () -> Unit,
    onAdvanced: () -> Unit,
    onDebugBundle: () -> Unit,
    supportBundleState: SupportBundleUiState = SupportBundleUiState.Idle,
    onGenerateSupportBundle: () -> Unit = {},
    onCancelSupportBundle: () -> Unit = {},
    onRetrySupportBundleShare: () -> Unit = {},
    onDeletePendingSupportBundle: () -> Unit = {},
    onCloseSupportBundleRetaining: () -> Unit = {},
    themeSettings: CodecksThemeSettings = CodecksThemeSettings(),
    onThemeModeChange: (CodecksThemeMode) -> Unit = {},
    onThemeAccentChange: (CodecksAccent) -> Unit = {},
    onThemeSurfaceStyleChange: (CodecksSurfaceStyle) -> Unit = {},
    onThemeBorderStyleChange: (CodecksBorderStyle) -> Unit = {},
    onThemeShapeStyleChange: (CodecksShapeStyle) -> Unit = {},
    onDeckStyleChange: (CodecksDeckStyle) -> Unit = {},
    onIconPackChange: (CodecksIconPack) -> Unit = {},
    launcherIcon: LauncherIcon = LauncherIcon.RobotFace,
    onLauncherIconChange: (LauncherIcon) -> Unit = {},
    trackpadSettings: TrackpadSettings = TrackpadSettings(),
    onTrackpadSettingsChange: ((TrackpadSettings) -> TrackpadSettings) -> Unit = {},
    modifier: Modifier = Modifier,
    localOnlyV1: Boolean = false,
    debugBundleEnabled: Boolean = false,
    developerOptionsEnabled: Boolean = false,
    appVersionLabel: String = "Version",
    updateState: UpdateSettingsState = UpdateSettingsState.Idle,
    onCheckForUpdate: () -> Unit = {},
    onOpenUpdateRelease: () -> Unit = {},
    featureFlags: Map<FeatureFlag, Boolean> = emptyMap(),
    onFeatureFlagChange: (FeatureFlag, Boolean) -> Unit = { _, _ -> },
    onResetFeatureFlags: () -> Unit = {},
    onClearSmartHistory: () -> Unit = {},
) {
    var showResetFlagsDialog by rememberSaveable { mutableStateOf(false) }
    var trackpadFineTuneOpen by rememberSaveable { mutableStateOf(false) }
    var macConnectionOpen by rememberSaveable { mutableStateOf(!connectionReady) }
    val hidHealth = hidState.hidHealth(bluetoothPermissionGranted)
    val macConnectionPresentation = connectionHealth.toUnifiedConnectionPresentation()
    val hidConnectionPresentation = hidHealth.toUnifiedConnectionPresentation()
    val setupCompletion = evaluateRuntimeSetupCompletion(
        state = connectionState,
        hidState = hidState,
        permissionState = when {
            bluetoothPermissionGranted -> BluetoothPermissionState.Granted
            bluetoothPermissionPermanentlyDenied -> BluetoothPermissionState.PermanentlyDenied
            else -> BluetoothPermissionState.Denied
        },
        hidReceipt = hidTerminalReceipt,
    )
    val readiness = codecksReadiness(connectionHealth, hidHealth, aiProviderReady, setupCompletion)
    LaunchedEffect(connectionReady) {
        if (!connectionReady) macConnectionOpen = true
    }
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
                item { SettingsHero(readiness = readiness) }
                item { SectionLabel("Setup") }
                item {
                    SetupChecklist(
                        connectionReady = connectionReady,
                        connectionHealth = connectionHealth,
                        hidState = hidState,
                        bluetoothPermissionGranted = bluetoothPermissionGranted,
                        notificationAccessReady = notificationAccessReady,
                        aiProviderReady = aiProviderReady,
                        automationsReady = automationsReady,
                        featureFlags = featureFlags,
                        onConnection = { macConnectionOpen = !macConnectionOpen },
                        onBluetooth = onBluetooth,
                        onNotificationAccess = onNotificationAccess,
                        onAiBuilder = onAiBuilder,
                        onAutomations = onAutomations,
                    )
                }
                item { SectionLabel("Mac") }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Link,
                        title = "Mac actions",
                        summary = "Deck, clipboard, and Rules over a secure connection. ${macConnectionPresentation.detail} Support code ${macConnectionPresentation.supportCode}.",
                        value = connectionHealth.statusLabel(),
                        onClick = { macConnectionOpen = !macConnectionOpen },
                    )
                }
                item {
                    CodecksHelperPanel(
                        state = codecksHelperState,
                        onConnect = onCodecksHelperConnect,
                        onOpenSetup = { macConnectionOpen = true },
                        onSearch = onCodecksHelperSearch,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Mouse,
                        title = "Mac input",
                        summary = "Trackpad and Text over Bluetooth. ${hidConnectionPresentation.detail} Support code ${hidConnectionPresentation.supportCode}.",
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
                            onReactiveHelperPairingImport = onReactiveHelperPairingImport,
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
                item { SectionLabel("Data and privacy") }
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
                        summary = "Save a checked archive. Credential stores are excluded; export is blocked if Deck or Rules contain secret-shaped text.",
                        onClick = onExportBackup,
                    )
                }
                if (pendingBackupRecovery) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.ErrorOutline,
                            title = if (corruptBackupRecovery) {
                                "Resolve corrupt backup recovery"
                            } else {
                                "Finish backup recovery"
                            },
                            summary = if (corruptBackupRecovery) {
                                "Recovery data is unreadable. Review and quarantine it before importing another backup."
                            } else {
                                "Restore the preserved prior Deck and Rules before importing another backup."
                            },
                            value = "Required",
                            onClick = onRecoverBackup,
                        )
                    }
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "Restore local backup",
                        summary = "Preview changes before replacing Deck and Rules from a Codecks backup",
                        onClick = onImportBackup,
                    )
                }
                item { SectionLabel("Control surfaces") }
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
                if (contextFeatureStatus.componentEnabled) {
                    item { SectionLabel("Notifications") }
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
                if (featureFlags.isOn(FeatureFlag.SmartSuggestions) || featureFlags.isOn(FeatureFlag.SmartDeck)) {
                    item { SectionLabel("Smart suggestions") }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "Clear smart history",
                            summary = "Forget local suggestion learning. Deck buttons and Mac setup stay untouched.",
                            onClick = onClearSmartHistory,
                            showChevron = false,
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

                item { SectionLabel("Build") }
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
                item { SectionLabel("Appearance") }
                item { ThemeStudioPanel(settings = themeSettings) }
                item {
                    DeckStylePanel(
                        deckStyle = themeSettings.deckStyle,
                        onDeckStyleChange = onDeckStyleChange,
                    )
                }
                item { SectionLabel("Icons") }
                item {
                    LauncherIconPanel(
                        launcherIcon = launcherIcon,
                        onLauncherIconChange = onLauncherIconChange,
                    )
                }
                item {
                    IconPackPanel(
                        iconPack = themeSettings.iconPack,
                        onIconPackChange = onIconPackChange,
                    )
                }
                if (developerOptionsEnabled) {
                    item { SectionLabel("Advanced") }
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
                item {
                    UpdateSettingsPanel(
                        state = updateState,
                        onCheck = onCheckForUpdate,
                        onOpenRelease = onOpenUpdateRelease,
                    )
                }
                if (debugBundleEnabled) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.BugReport,
                            title = if (supportBundleState is SupportBundleUiState.PendingRetained) {
                                "Pending support bundle"
                            } else {
                                "Create support bundle"
                            },
                            summary = if (supportBundleState is SupportBundleUiState.PendingRetained) {
                                "Retained on this device. Tap to retry sharing; delete from the dialog."
                            } else {
                                "Preview exactly what is included, then use Android’s share picker"
                            },
                            onClick = if (supportBundleState is SupportBundleUiState.PendingRetained) {
                                onRetrySupportBundleShare
                            } else {
                                onDebugBundle
                            },
                        )
                    }
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "About Codecks",
                        summary = "$appVersionLabel · ${launcherIcon.label} icon",
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
    SupportBundleDialog(
        state = supportBundleState,
        onGenerate = onGenerateSupportBundle,
        onCancel = onCancelSupportBundle,
        onRetryShare = onRetrySupportBundleShare,
        onDeletePending = onDeletePendingSupportBundle,
        onCloseRetaining = onCloseSupportBundleRetaining,
    )
    restorePlan?.let { plan ->
        BackupRestoreDialog(
            plan = plan,
            onCancel = onCancelRestore,
            onConfirm = onConfirmRestore,
        )
    }
}
