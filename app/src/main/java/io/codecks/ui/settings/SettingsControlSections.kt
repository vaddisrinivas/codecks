package io.codecks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.context.NotificationPrivacySettings
import io.codecks.core.trackpad.TrackpadClockStyle
import io.codecks.core.trackpad.TrackpadFloatingMenuLayout
import io.codecks.core.trackpad.TrackpadGestureAction
import io.codecks.core.trackpad.TrackpadRailSide
import io.codecks.core.trackpad.TrackpadRotation
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.ActionIcon
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckComponentState
import io.codecks.ui.designsystem.DeckControlTile
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.connection.SetupStep
import io.codecks.ui.icons.imageVector
import io.codecks.ui.theme.CodecksDeckStyle
import io.codecks.ui.theme.CodecksIconPack

@Composable
internal fun MacPairingStepper(
    current: MacPairingStep,
    snapshot: io.codecks.ui.connection.SetupSnapshot,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        MacPairingStep.entries.forEach { step ->
            val selected = step == current
            val complete = when (step) {
                MacPairingStep.FindMac -> snapshot.isPassed(SetupStep.FindMac)
                MacPairingStep.TrustMac -> snapshot.isPassed(SetupStep.TrustMac)
                MacPairingStep.Authorize -> snapshot.isPassed(SetupStep.Authorize)
                MacPairingStep.Done -> snapshot.isPassed(SetupStep.VerifyControls)
            }
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
internal fun NotificationPrivacyPanel(
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
internal fun TrackpadSettingsPanel(
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
                label = "Lockscreen Trackpad",
                checked = settings.lockscreenTrackpadEnabled,
                onCheckedChange = { value -> onChange { it.copy(lockscreenTrackpadEnabled = value) } },
            )
            Text(
                "Only works after Trackpad is already connected and this toggle is on. While locked, Codecks never reconnects HID or exposes keyboard, deck, settings, or SSH.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun ClipboardSettingsPanel(
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
internal fun DeckStylePanel(
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
internal fun IconPackPanel(
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
