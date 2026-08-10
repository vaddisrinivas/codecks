package io.codecks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.codecks.HidState
import io.codecks.R
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.context.NotificationPrivacySettings
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.launcher.LauncherIcon
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.canSendInput
import io.codecks.ui.connection.hidHealth
import io.codecks.ui.connection.isReady
import io.codecks.ui.connection.statusLabel
import io.codecks.ui.connection.toUnifiedConnectionPresentation
import io.codecks.ui.icons.imageVector
import io.codecks.ui.theme.CodecksAccent
import io.codecks.ui.theme.CodecksBorderStyle
import io.codecks.ui.theme.CodecksShapeStyle
import io.codecks.ui.theme.CodecksSurfaceStyle
import io.codecks.ui.theme.CodecksThemeMode
import io.codecks.ui.theme.CodecksThemeSettings

@Composable
internal fun LauncherIconPanel(
    launcherIcon: LauncherIcon,
    onLauncherIconChange: (LauncherIcon) -> Unit,
) {
    CodecksPanel(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Text("App icon", style = MaterialTheme.typography.titleMedium)
            Text(
                "Changes the launcher, live widget, and next-session notification icon. The Android splash and widget-picker preview keep the original robot face.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                items(LauncherIcon.entries, key = LauncherIcon::persistedValue) { icon ->
                    val preview = when (icon) {
                        LauncherIcon.RobotFace -> R.drawable.ic_launcher
                        LauncherIcon.RobotGrid -> R.mipmap.ic_launcher_robot_grid
                        LauncherIcon.PointerGrid -> R.mipmap.ic_launcher_pointer_grid
                        LauncherIcon.MinimalGreen -> R.mipmap.ic_launcher_minimal_green
                    }
                    CodecksPanel(
                        selected = launcherIcon == icon,
                        modifier = Modifier
                            .width(168.dp)
                            .heightIn(min = 132.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${icon.label}. ${icon.description}"
                            }
                            .selectable(
                                selected = launcherIcon == icon,
                                onClick = { onLauncherIconChange(icon) },
                                role = Role.RadioButton,
                            ),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Image(
                                painter = painterResource(preview),
                                contentDescription = null,
                                modifier = Modifier.size(58.dp),
                            )
                            Text(icon.label, style = MaterialTheme.typography.labelLarge)
                            Text(
                                icon.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
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
internal fun SettingsHero(
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
internal fun SetupChecklist(
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
    val macPresentation = connectionHealth.toUnifiedConnectionPresentation()
    val hidPresentation = hidHealth.toUnifiedConnectionPresentation()
    Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth()) {
        SetupRow(
            title = "Mac control channel",
            summary = "${macPresentation.detail} Support code ${macPresentation.supportCode}.",
            ready = macReady,
            statusLabel = connectionHealth.statusLabel(),
            onClick = onConnection,
        )
        SetupRow(
            title = "Trackpad Mac",
            summary = "${hidPresentation.detail} Support code ${hidPresentation.supportCode}.",
            ready = hidHealth.canSendInput,
            statusLabel = hidHealth.statusLabel(),
            onClick = onBluetooth,
        )
        if (io.codecks.BuildConfig.OPTIONAL_CONTEXT_SURFACES_ENABLED) {
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
internal fun FeatureFlagPanel(
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
            add(FlagSpec(FeatureFlag.ReactiveTrackpad, "Reactive Trackpad", "Temporary app-aware controls above Trackpad", Icons.Outlined.AutoAwesome))
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

internal fun notificationPrivacySummary(settings: NotificationPrivacySettings): String = when {
    !settings.showOnTrackpad -> "Phone notifications stay off the Trackpad background"
    settings.allowedPackages.isNotEmpty() && settings.showContent -> "Only approved apps can show title and message text"
    settings.allowedPackages.isNotEmpty() -> "Only approved apps can appear; content stays hidden"
    settings.showContent -> "Trackpad can show notification title and message text"
    settings.hideSensitiveApps -> "Private mode: app names only; sensitive apps hidden"
    else -> "Private mode: app names only"
}

private fun bluetoothSummary(state: HidState, permissionGranted: Boolean): String =
    state.hidHealth(permissionGranted).toUnifiedConnectionPresentation().let {
        "${it.detail} Support code ${it.supportCode}."
    }

internal fun ClipboardSyncSettings.summary(): String = when (mode) {
    ClipboardSyncMode.Off -> "Automatic sync is off"
    ClipboardSyncMode.PhoneToMac -> "Phone to Mac every $intervalMinutes min"
    ClipboardSyncMode.MacToPhone -> "Mac to phone every $intervalMinutes min"
    ClipboardSyncMode.Bidirectional -> "Two-way sync every $intervalMinutes min"
}

internal fun ClipboardSyncSettings.valueLabel(): String =
    if (mode == ClipboardSyncMode.Off) "Off" else "${intervalMinutes}m"

internal val ClipboardSyncMode.label: String
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

internal fun Map<FeatureFlag, Boolean>.isOn(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 24.dp))
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsRow(
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
