package io.codex.s23deck.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.codex.s23deck.HidState
import io.codex.s23deck.data.clipboard.ClipboardSyncSettings
import io.codex.s23deck.data.notifications.NotificationPrivacySettings
import io.codex.s23deck.core.trackpad.TrackpadClockStyle
import io.codex.s23deck.core.trackpad.TrackpadFloatingMenuLayout
import io.codex.s23deck.core.trackpad.TrackpadRailSide
import io.codex.s23deck.core.trackpad.TrackpadRotation
import io.codex.s23deck.core.trackpad.TrackpadSettings
import io.codex.s23deck.domain.clipboard.ClipboardSyncMode
import io.codex.s23deck.domain.commerce.DEFAULT_FEATURE_FLAGS
import io.codex.s23deck.domain.commerce.FeatureFlag
import io.codex.s23deck.domain.ActionIcon
import io.codex.s23deck.ui.designsystem.CodecksPanel
import io.codex.s23deck.ui.designsystem.DeckComponentState
import io.codex.s23deck.ui.designsystem.DeckControlTile
import io.codex.s23deck.ui.designsystem.DeckPage
import io.codex.s23deck.ui.designsystem.DeckFilterPill
import io.codex.s23deck.ui.connection.ConnectionHealth
import io.codex.s23deck.ui.connection.canSendInput
import io.codex.s23deck.ui.connection.hidHealth
import io.codex.s23deck.ui.connection.isReady
import io.codex.s23deck.ui.connection.simpleConnectionHealth
import io.codex.s23deck.ui.connection.codecksReadiness
import io.codex.s23deck.ui.connection.statusLabel
import io.codex.s23deck.ui.icons.imageVector
import io.codex.s23deck.ui.theme.CodecksDeckStyle
import io.codex.s23deck.ui.theme.CodecksIconPack
import io.codex.s23deck.ui.theme.DeckBridgeAccent
import io.codex.s23deck.ui.theme.DeckBridgeBorderStyle
import io.codex.s23deck.ui.theme.DeckBridgeShapeStyle
import io.codex.s23deck.ui.theme.DeckBridgeSurfaceStyle
import io.codex.s23deck.ui.theme.DeckBridgeThemeMode
import io.codex.s23deck.ui.theme.DeckBridgeThemeSettings

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    connectionReady: Boolean,
    connectionHealth: ConnectionHealth = simpleConnectionHealth(connectionReady),
    hidState: HidState,
    bluetoothPermissionGranted: Boolean,
    notificationAccessReady: Boolean,
    notificationPrivacySettings: NotificationPrivacySettings = NotificationPrivacySettings(),
    clipboardSettings: ClipboardSyncSettings,
    aiProviderReady: Boolean,
    automationsReady: Boolean,
    onConnection: () -> Unit,
    onBluetooth: () -> Unit,
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
    onContextDeck: () -> Unit,
    onPremium: () -> Unit,
    onWidget: () -> Unit,
    onAppearance: () -> Unit,
    onAdvanced: () -> Unit,
    onDebugBundle: () -> Unit,
    themeSettings: DeckBridgeThemeSettings = DeckBridgeThemeSettings(),
    onThemeModeChange: (DeckBridgeThemeMode) -> Unit = {},
    onThemeAccentChange: (DeckBridgeAccent) -> Unit = {},
    onThemeSurfaceStyleChange: (DeckBridgeSurfaceStyle) -> Unit = {},
    onThemeBorderStyleChange: (DeckBridgeBorderStyle) -> Unit = {},
    onThemeShapeStyleChange: (DeckBridgeShapeStyle) -> Unit = {},
    onDeckStyleChange: (CodecksDeckStyle) -> Unit = {},
    onIconPackChange: (CodecksIconPack) -> Unit = {},
    trackpadSettings: TrackpadSettings = TrackpadSettings(),
    onTrackpadSettingsChange: ((TrackpadSettings) -> TrackpadSettings) -> Unit = {},
    modifier: Modifier = Modifier,
    showPremium: Boolean = false,
    localOnlyV1: Boolean = false,
    debugBundleEnabled: Boolean = false,
    developerOptionsEnabled: Boolean = false,
    appVersionLabel: String = "Version",
    featureFlags: Map<FeatureFlag, Boolean> = emptyMap(),
    onFeatureFlagChange: (FeatureFlag, Boolean) -> Unit = { _, _ -> },
    onResetFeatureFlags: () -> Unit = {},
) {
    var showResetFlagsDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.Essentials) }
    var trackpadFineTuneOpen by rememberSaveable { mutableStateOf(false) }
    val hidHealth = hidState.hidHealth(bluetoothPermissionGranted)
    val readiness = codecksReadiness(connectionHealth, hidHealth, aiProviderReady)
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        item {
            SettingsSectionPills(
                selected = selectedSection,
                onSelected = { selectedSection = it },
                developerOptionsEnabled = developerOptionsEnabled,
            )
        }
        when (selectedSection) {
            SettingsSection.Essentials -> {
                item { SettingsHero(readiness = readiness) }
                item { SectionLabel("Readiness") }
                item {
                    CodecksPanel(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        SetupChecklist(
                            connectionReady = connectionReady,
                            connectionHealth = connectionHealth,
                            hidState = hidState,
                            bluetoothPermissionGranted = bluetoothPermissionGranted,
                            notificationAccessReady = notificationAccessReady,
                            aiProviderReady = aiProviderReady,
                            automationsReady = automationsReady,
                            featureFlags = featureFlags,
                            onConnection = onConnection,
                            onBluetooth = onBluetooth,
                            onNotificationAccess = onNotificationAccess,
                            onAiBuilder = onAiBuilder,
                            onAutomations = onAutomations,
                        )
                    }
                }
                item { SectionLabel(if (localOnlyV1) "Local Data" else "Account & Data") }
                item {
                    SettingsRow(
                        icon = if (localOnlyV1) Icons.Outlined.CheckCircle else Icons.Outlined.WorkspacePremium,
                        title = if (localOnlyV1) "Local-only launch mode" else "Account and plan",
                        summary = if (localOnlyV1) {
                            "No Codecks login, billing, server account, or public database is used in this version"
                        } else {
                            "Sign in, billing, restore purchases, and account deletion"
                        },
                        value = if (localOnlyV1) "On" else if (showPremium && featureFlags.isOn(FeatureFlag.Premium)) "Open" else null,
                        onClick = if (!localOnlyV1 && showPremium && featureFlags.isOn(FeatureFlag.Premium)) onPremium else null,
                        showChevron = !localOnlyV1 && showPremium && featureFlags.isOn(FeatureFlag.Premium),
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "Privacy policy and data safety",
                        summary = if (localOnlyV1) {
                            "Decks, Mac targets, provider keys, clipboard settings, and notification preferences stay on this phone"
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
                        summary = "Save Deck and automations as JSON; API keys, SSH keys, and connection secrets are excluded",
                        onClick = onExportBackup,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "Restore local backup",
                        summary = "Replace Deck and automations from a Codecks backup file",
                        onClick = onImportBackup,
                    )
                }
                item { SectionLabel("Connections") }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Link,
                        title = "Mac control channel",
                        summary = connectionHealth.detail,
                        value = connectionHealth.statusLabel(),
                        onClick = onConnection,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Bluetooth,
                        title = "Bluetooth mouse & keyboard",
                        summary = hidHealth.detail,
                        value = hidHealth.statusLabel(),
                        onClick = onBluetooth,
                    )
                }
                if (featureFlags.isOn(FeatureFlag.Devices)) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.Workspaces,
                            title = "Targets & groups",
                            summary = "Current Mac, compatible targets, and grouped runs",
                            value = if (readiness.macCommandsReady) "Current" else connectionHealth.statusLabel(),
                            onClick = onDevices,
                        )
                    }
                }
            }

            SettingsSection.Controls -> {
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
                if (featureFlags.isOn(FeatureFlag.ContextDeck) || featureFlags.isOn(FeatureFlag.Widget)) {
                    item { SectionLabel("Notification privacy") }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notification access",
                            summary = if (notificationAccessReady) {
                                "Phone notifications can appear behind Trackpad"
                            } else {
                                "Open Android notification access; apps cannot grant this with a normal prompt"
                            },
                            value = if (notificationAccessReady) "Ready" else "Allow",
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
            }

            SettingsSection.Automation -> {
                item { SectionLabel("Create and run") }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Psychology,
                        title = "AI creator",
                        summary = if (aiProviderReady) "Provider key saved for decks, buttons, and automations" else "Add a provider key before generating",
                        value = if (aiProviderReady) "Ready" else "Missing",
                        onClick = onAiBuilder,
                    )
                }
                item {
                    SettingsRow(
                        icon = Icons.Outlined.Psychology,
                        title = "Automations",
                        summary = if (automationsReady) "Runnable workspace routines are ready" else "Needs Mac control channel",
                        value = if (automationsReady) "Ready" else "Setup",
                        onClick = onAutomations,
                    )
                }
                if (featureFlags.isOn(FeatureFlag.ContextDeck)) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "Context Deck",
                            summary = "AI-ranked live controls from Mac app, notifications, and local state",
                            value = "Preview",
                            onClick = onContextDeck,
                        )
                    }
                }
                if (featureFlags.isOn(FeatureFlag.Widget)) {
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.Widgets,
                            title = "Widget",
                            summary = "Home screen shortcuts",
                            onClick = onWidget,
                        )
                    }
                }
            }

            SettingsSection.Look -> {
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
                if (featureFlags.isOn(FeatureFlag.Appearance)) {
                    item { SectionLabel("Advanced appearance") }
                    item {
                        ThemeModePanel(
                            themeSettings = themeSettings,
                            onThemeModeChange = onThemeModeChange,
                            onAccentChange = onThemeAccentChange,
                            onSurfaceStyleChange = onThemeSurfaceStyleChange,
                            onBorderStyleChange = onThemeBorderStyleChange,
                            onShapeStyleChange = onThemeShapeStyleChange,
                        )
                    }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.ColorLens,
                            title = "Appearance",
                            summary = "Theme controls and future Deck-button appearance presets",
                            value = "Advanced",
                            onClick = onAppearance,
                        )
                    }
                }
            }

            SettingsSection.Support -> {
                if (!localOnlyV1) {
                    item { SectionLabel("Account") }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.WorkspacePremium,
                            title = "Account and plan",
                            summary = "Sign in, billing, restore purchases, and account deletion",
                            value = if (showPremium && featureFlags.isOn(FeatureFlag.Premium)) "Open" else null,
                            onClick = if (showPremium && featureFlags.isOn(FeatureFlag.Premium)) onPremium else null,
                            showChevron = showPremium && featureFlags.isOn(FeatureFlag.Premium),
                        )
                    }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Delete account",
                            summary = "Available from Account and plan; deletes server account state and local session data",
                            onClick = if (showPremium && featureFlags.isOn(FeatureFlag.Premium)) onPremium else null,
                            showChevron = showPremium && featureFlags.isOn(FeatureFlag.Premium),
                        )
                    }
                }
                if (featureFlags.isOn(FeatureFlag.Advanced)) {
                    item { SectionLabel("Diagnostics") }
                    item {
                        SettingsRow(
                            icon = Icons.Outlined.Terminal,
                            title = "Diagnostics",
                            summary = "Connection details and diagnostics",
                            onClick = onAdvanced,
                        )
                    }
                }
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
            }
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

private enum class SettingsSection(val label: String) {
    Essentials("Essentials"),
    Controls("Controls"),
    Automation("Automations"),
    Look("Look"),
    Support("About"),
}

@Composable
private fun SettingsSectionPills(
    selected: SettingsSection,
    onSelected: (SettingsSection) -> Unit,
    developerOptionsEnabled: Boolean,
) {
    val sections = SettingsSection.entries.map { section ->
        if (section == SettingsSection.Support && developerOptionsEnabled) {
            section to "Advanced"
        } else {
            section to section.label
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        sections.chunked(3).forEach { rowSections ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowSections.forEach { (section, label) ->
                    DeckFilterPill(
                        label = label,
                        selected = selected == section,
                        onClick = { onSelected(section) },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    )
                }
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
                        "A focused near-black control surface. Experimental themes stay out of the main product.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(CodecksDeckStyle.StreamDeckPro, CodecksDeckStyle.NothingMonoDeck),
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
    themeSettings: DeckBridgeThemeSettings,
    onThemeModeChange: (DeckBridgeThemeMode) -> Unit,
    onAccentChange: (DeckBridgeAccent) -> Unit = {},
    onSurfaceStyleChange: (DeckBridgeSurfaceStyle) -> Unit = {},
    onBorderStyleChange: (DeckBridgeBorderStyle) -> Unit = {},
    onShapeStyleChange: (DeckBridgeShapeStyle) -> Unit = {},
    showMode: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (showMode) {
            Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DeckBridgeThemeMode.entries, key = DeckBridgeThemeMode::name) { mode ->
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
            items(DeckBridgeAccent.entries, key = DeckBridgeAccent::name) { accent ->
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
            items(DeckBridgeSurfaceStyle.entries, key = DeckBridgeSurfaceStyle::name) { style ->
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
            items(DeckBridgeBorderStyle.entries, key = DeckBridgeBorderStyle::name) { style ->
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
            items(DeckBridgeShapeStyle.entries, key = DeckBridgeShapeStyle::name) { style ->
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
    readiness: io.codex.s23deck.ui.connection.CodecksReadiness,
) {
    CodecksPanel(
        selected = readiness.coreReady,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
        ) {
            Surface(
                color = if (readiness.coreReady) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (readiness.coreReady) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(
                        imageVector = if (readiness.coreReady) Icons.Outlined.CheckCircle else Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(readiness.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    readiness.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            title = "Bluetooth HID target",
            summary = hidHealth.detail,
            ready = hidHealth.canSendInput,
            statusLabel = hidHealth.statusLabel(),
            onClick = onBluetooth,
        )
        if (featureFlags.isOn(FeatureFlag.ContextDeck) || featureFlags.isOn(FeatureFlag.Widget)) {
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
                title = "AI provider",
                summary = if (aiProviderReady) {
                    "AI key saved"
                } else {
                    "Save a provider key"
                },
                ready = aiProviderReady,
                statusLabel = if (aiProviderReady) "Ready" else "Optional",
                required = false,
                onClick = onAiBuilder,
            )
        }
        if (featureFlags.isOn(FeatureFlag.Automations)) {
            SetupRow(
                title = "Automations",
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
    statusLabel: String = if (ready) "Ready" else "Fix",
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
        listOf(
            FlagSpec(FeatureFlag.Deck, "Deck", "Home deck and scriptable controls", Icons.Outlined.GridView),
            FlagSpec(FeatureFlag.Trackpad, "Trackpad", "Full-screen pointer, gestures, scroll zone", Icons.Outlined.Mouse),
            FlagSpec(FeatureFlag.Ai, "AI", "Provider keys, decks, buttons, automations", Icons.Outlined.AutoAwesome),
            FlagSpec(FeatureFlag.ContextDeck, "Context Deck", "AI-ranked live command deck and smart widget", Icons.Outlined.AutoAwesome),
            FlagSpec(FeatureFlag.Automations, "Automations", "Runnable workspace routines", Icons.Outlined.Psychology),
            FlagSpec(FeatureFlag.Keyboard, "Keyboard page", "Optional page; keyboard controls stay in Trackpad", Icons.Outlined.Keyboard),
            FlagSpec(FeatureFlag.Clipboard, "Clipboard page", "Clipboard sync settings and manual transfer", Icons.Outlined.ContentPaste),
            FlagSpec(FeatureFlag.Activity, "Activity", "Action history page", Icons.Outlined.Info),
            FlagSpec(FeatureFlag.Devices, "Devices", "Multi-device target picker", Icons.Outlined.Workspaces),
            FlagSpec(FeatureFlag.Premium, "Premium", "Billing and account surfaces", Icons.Outlined.WorkspacePremium),
            FlagSpec(FeatureFlag.Widget, "Widget", "Launcher widget setup", Icons.Outlined.Widgets),
            FlagSpec(FeatureFlag.Appearance, "Appearance", "Theme controls", Icons.Outlined.ColorLens),
            FlagSpec(FeatureFlag.Advanced, "Diagnostics", "Advanced diagnostics and shell", Icons.Outlined.Terminal),
            FlagSpec(FeatureFlag.Labs, "Labs", "Experimental inputs stay hidden unless enabled", Icons.Outlined.Terminal),
            FlagSpec(FeatureFlag.LabAirMouse, "Labs: Air mouse", "Tilt phone to move pointer", Icons.Outlined.Mouse),
            FlagSpec(FeatureFlag.LabAirTouch, "Labs: S Pen air touch", "Experimental fake-monitor calibration", Icons.Outlined.Mouse),
            FlagSpec(FeatureFlag.LabBackTap, "Labs: back tap", "Device back tap can click", Icons.Outlined.Mouse),
            FlagSpec(FeatureFlag.LabVolumeKeys, "Labs: volume keys", "Use volume keys for scroll", Icons.Outlined.Mouse),
        ).forEach { spec ->
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
