package io.codecks.ui.mouse

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.codecks.HidCommand
import io.codecks.HidState
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.home.CustomActionRow
import io.codecks.ui.keyboard.HidHostHeader
import kotlin.math.roundToInt
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadGestureEngine

@Composable
internal fun MouseControls(
    state: HidState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onRefreshHosts: () -> Unit,
    onConnect: (String) -> Unit,
    onScroll: (Int) -> Unit,
    onHorizontalScroll: (Int) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onMiddleClick: () -> Unit,
    onCommand: (HidCommand) -> Unit,
    onPress: (Int) -> Unit,
    onReleaseButtons: () -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    inputMode: MouseInputMode,
    onInputModeChange: (MouseInputMode) -> Unit,
    naturalScroll: Boolean,
    onNaturalScrollChange: (Boolean) -> Unit,
    scrollSpeed: Float,
    onScrollSpeedChange: (Float) -> Unit,
    scrollRailEnabled: Boolean,
    onScrollRailEnabledChange: (Boolean) -> Unit,
    verticalRailDirection: ScrollRailDirection,
    onVerticalRailDirectionChange: (ScrollRailDirection) -> Unit,
    dragLockEnabled: Boolean,
    onDragLockChange: (Boolean) -> Unit,
    traceEnabled: Boolean,
    onTraceEnabledChange: (Boolean) -> Unit,
    sPenPrecisionEnabled: Boolean,
    onSPenPrecisionEnabledChange: (Boolean) -> Unit,
    backTapEnabled: Boolean,
    onBackTapEnabledChange: (Boolean) -> Unit,
    volumeKeysEnabled: Boolean,
    onVolumeKeysEnabledChange: (Boolean) -> Unit,
    airMouseEnabled: Boolean,
    airTouchEnabled: Boolean,
    backTapAvailable: Boolean,
    volumeKeysAvailable: Boolean,
    gyroAvailable: Boolean,
    gyroSensitivity: Float,
    onGyroSensitivityChange: (Float) -> Unit,
    gyroStatus: Offset,
    onGyroCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
    showHostHeader: Boolean = true,
    customActions: List<DeckAction> = emptyList(),
    customActionsReady: Boolean = true,
    onCustomAction: (DeckAction) -> Unit = {},
    selectedActionId: String? = null,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    val inputModes = remember(airMouseEnabled, airTouchEnabled) {
        buildList {
            add(MouseInputMode.Trackpad)
            if (airMouseEnabled) add(MouseInputMode.AirMouse)
            if (airTouchEnabled) add(MouseInputMode.AirTouch)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        if (showHostHeader) {
            HidHostHeader(
                title = "Choose a paired Mac",
                disconnectedTitle = "Connect a Mac",
                connectedTitle = "Trackpad connected",
                icon = Icons.Outlined.Mouse,
                state = state,
                permissionGranted = permissionGranted,
                onRequestPermission = onRequestPermission,
                onStart = onStart,
                onRefreshHosts = onRefreshHosts,
                onConnect = onConnect,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton(
                label = "Left",
                mask = 1,
                onClick = onLeftClick,
                onPress = onPress,
                onReleaseButtons = onReleaseButtons,
                enabled = state.isConnected,
                modifier = Modifier.weight(1f).height(56.dp),
            )
            HoldButton(
                label = "Right",
                mask = 2,
                onClick = onRightClick,
                onPress = onPress,
                onReleaseButtons = onReleaseButtons,
                enabled = state.isConnected,
                modifier = Modifier.weight(1f).height(56.dp),
            )
            if (!showHostHeader) {
                DeckActionButton(
                    label = "More",
                    onClick = { settingsOpen = true },
                    icon = Icons.Outlined.Tune,
                    modifier = Modifier.weight(0.9f).height(56.dp),
                )
            }
        }
        CustomActionRow(
            actions = customActions.take(if (showHostHeader) 3 else 4),
            onAction = onCustomAction,
            selectedActionId = selectedActionId,
            contentPadding = PaddingValues(end = 16.dp),
            isActionEnabled = { it.kind != ActionKind.Ssh || customActionsReady },
        )
        if (showHostHeader) {
            DeckActionButton(
                label = "Trackpad options",
                onClick = { settingsOpen = true },
                        icon = Icons.Outlined.Tune,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Trackpad options") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        inputModes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = inputMode == mode,
                                onClick = { onInputModeChange(mode) },
                                enabled = mode != MouseInputMode.AirMouse || gyroAvailable,
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = inputModes.size,
                                ),
                                icon = {},
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    DeckActionButton(
                        label = if (dragLockEnabled) "Unlock drag" else "Drag lock",
                        onClick = { onDragLockChange(!dragLockEnabled) },
                        enabled = state.isConnected && inputMode == MouseInputMode.Trackpad,
                        icon = Icons.Outlined.Lock,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                    HoldButton(
                        label = "Middle click",
                        mask = 4,
                        onClick = onMiddleClick,
                        onPress = onPress,
                        onReleaseButtons = onReleaseButtons,
                        enabled = state.isConnected,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                    DeckActionButton(
                        label = "Keyboard controls",
                        onClick = { keyboardOpen = true },
                        enabled = state.isConnected,
                        icon = Icons.Outlined.Keyboard,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Scroll rails", style = MaterialTheme.typography.labelMedium)
                            Switch(
                                checked = scrollRailEnabled,
                                onCheckedChange = onScrollRailEnabledChange,
                                enabled = inputMode == MouseInputMode.Trackpad,
                            )
                        }
                        DirectionSelector(
                            label = "Vertical rail",
                            selected = verticalRailDirection,
                            onSelected = onVerticalRailDirectionChange,
                            enabled = scrollRailEnabled && inputMode == MouseInputMode.Trackpad,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Sensitivity", style = MaterialTheme.typography.labelMedium)
                            Text(
                                when {
                                    sensitivity < 0.75f -> "Fine"
                                    sensitivity > 1.2f -> "Fast"
                                    else -> "Normal"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = sensitivity,
                            onValueChange = onSensitivityChange,
                            valueRange = 0.3f..1.35f,
                            enabled = state.isConnected && inputMode == MouseInputMode.Trackpad,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Scroll speed", style = MaterialTheme.typography.labelMedium)
                            Text(
                                when {
                                    scrollSpeed < 0.75f -> "Fine"
                                    scrollSpeed > 1.25f -> "Fast"
                                    else -> "Normal"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = scrollSpeed,
                            onValueChange = onScrollSpeedChange,
                            valueRange = 0.35f..1.8f,
                            enabled = state.isConnected && inputMode == MouseInputMode.Trackpad,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Input assist", style = MaterialTheme.typography.labelMedium)
                        SettingsSwitchRow(
                            label = "S Pen precision",
                            checked = sPenPrecisionEnabled,
                            onCheckedChange = onSPenPrecisionEnabledChange,
                            enabled = inputMode == MouseInputMode.Trackpad,
                        )
                        SettingsSwitchRow(
                            label = "Back tap click",
                            checked = backTapEnabled,
                            onCheckedChange = onBackTapEnabledChange,
                            enabled = state.isConnected && backTapAvailable,
                        )
                        SettingsSwitchRow(
                            label = "Volume keys scroll",
                            checked = volumeKeysEnabled,
                            onCheckedChange = onVolumeKeysEnabledChange,
                            enabled = state.isConnected && volumeKeysAvailable,
                        )
                    }
                    if (inputMode == MouseInputMode.AirMouse) {
                        DeckActionButton(
                            label = "Calibrate air mouse",
                            onClick = onGyroCalibrate,
                            enabled = state.isConnected && gyroAvailable,
                            icon = Icons.Outlined.Tune,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        )
                        Slider(
                            value = gyroSensitivity,
                            onValueChange = onGyroSensitivityChange,
                            valueRange = 0.25f..1.75f,
                            enabled = state.isConnected && gyroAvailable,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Pointer trace", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = traceEnabled,
                            onCheckedChange = onTraceEnabledChange,
                            enabled = inputMode == MouseInputMode.Trackpad,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Natural scroll", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = naturalScroll,
                            onCheckedChange = onNaturalScrollChange,
                            enabled = state.isConnected,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) { Text("Done") }
            },
        )
    }

    if (keyboardOpen) {
        AlertDialog(
            onDismissRequest = { keyboardOpen = false },
            title = { Text("Keyboard controls") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    KeyboardCommandGrid(
                        enabled = state.isConnected,
                        onCommand = onCommand,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { keyboardOpen = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun KeyboardCommandGrid(
    enabled: Boolean,
    onCommand: (HidCommand) -> Unit,
) {
    val controls = remember {
        listOf(
            "Enter" to HidCommand.Enter,
            "Esc" to HidCommand.Escape,
            "Tab" to HidCommand.Tab,
            "Backspace" to HidCommand.Backspace,
            "Copy" to HidCommand.Copy,
            "Paste" to HidCommand.Paste,
            "Undo" to HidCommand.Undo,
            "Find" to HidCommand.Find,
            "Spotlight" to HidCommand.Spotlight,
            "Mission" to HidCommand.MissionControl,
            "Expose" to HidCommand.AppExpose,
            "Desktop" to HidCommand.ShowDesktop,
            "Back" to HidCommand.BrowserBack,
            "Forward" to HidCommand.BrowserForward,
            "Space left" to HidCommand.SpaceLeft,
            "Space right" to HidCommand.SpaceRight,
            "App switch" to HidCommand.AppSwitcher,
            "Window" to HidCommand.WindowSwitcher,
        )
    }
    controls.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (label, command) ->
                DeckActionButton(
                    label = label,
                    onClick = { onCommand(command) },
                    enabled = enabled,
                    icon = when (command) {
                        HidCommand.Spotlight -> Icons.Outlined.Search
                        HidCommand.MissionControl -> Icons.Outlined.LaptopMac
                        else -> null
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
            }
            repeat(2 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DirectionSelector(
    label: String,
    selected: ScrollRailDirection,
    onSelected: (ScrollRailDirection) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ScrollRailDirection.entries.forEachIndexed { index, direction ->
                SegmentedButton(
                    selected = selected == direction,
                    onClick = { onSelected(direction) },
                    enabled = enabled,
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ScrollRailDirection.entries.size,
                    ),
                    icon = {},
                    label = { Text(direction.label) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun HoldButton(
    label: String,
    mask: Int,
    onClick: () -> Unit,
    onPress: (Int) -> Unit,
    onReleaseButtons: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        pressed -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        pressed -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.pointerInput(enabled, mask) {
            if (enabled) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPress(mask)
                        val released = tryAwaitRelease()
                        pressed = false
                        onReleaseButtons()
                        if (released) onClick()
                    },
                )
            }
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun TrackpadExpandedBottomSheet(
    selectedTray: TrackpadQuickTray,
    dynamicEnabled: Boolean,
    onCustom: () -> Unit,
    onDynamic: () -> Unit,
    onSettings: () -> Unit,
    sessionPinned: Boolean,
    onToggleSessionPin: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val items = buildList {
        add(TrackpadQuickTray.Custom to Icons.Outlined.GridView)
        if (dynamicEnabled) add(TrackpadQuickTray.Dynamic to Icons.Outlined.AutoAwesome)
        add(TrackpadQuickTray.Settings to Icons.Outlined.Settings)
    }
    CodecksPanel(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeOverlayTouches(),
            )
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(6.dp),
                ) {
                    val itemModifier = Modifier.size(48.dp)
                    items.forEach { (tray, icon) ->
                        TrackpadMenuIcon(
                            icon = icon,
                            selected = selectedTray == tray,
                            onClick = when (tray) {
                                TrackpadQuickTray.Custom -> onCustom
                                TrackpadQuickTray.Dynamic -> onDynamic
                                TrackpadQuickTray.Settings -> onSettings
                            },
                            contentDescription = when (tray) {
                                TrackpadQuickTray.Custom -> "Custom actions"
                                TrackpadQuickTray.Dynamic -> "Dynamic actions"
                                TrackpadQuickTray.Settings -> "Trackpad settings"
                            },
                            modifier = itemModifier,
                        )
                    }
                    TrackpadMenuIcon(
                        icon = Icons.Outlined.Lock,
                        selected = sessionPinned,
                        onClick = onToggleSessionPin,
                        contentDescription = if (sessionPinned) "Unlock Trackpad session" else "Lock Trackpad session",
                        modifier = itemModifier,
                    )
                    TrackpadMenuIcon(
                        icon = Icons.Outlined.Home,
                        selected = false,
                        onClick = onExit,
                        contentDescription = "Exit Trackpad",
                        modifier = itemModifier,
                    )
                    TextButton(onClick = onClose) { Text("Done") }
                }
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    content()
                }
            }
        }
    }
}

private fun Modifier.consumeOverlayTouches(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
internal fun TrackpadMenuIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TrackpadQuickTrayPanel(
    tray: TrackpadQuickTray,
    customActions: List<DeckAction>,
    dynamicActions: List<DeckAction>,
    customActionsReady: Boolean,
    onCustomAction: (DeckAction) -> Unit,
    selectedActionId: String?,
    modifier: Modifier = Modifier,
) {
    val actions = when (tray) {
        TrackpadQuickTray.Custom -> customActions
        TrackpadQuickTray.Dynamic -> dynamicActions
        TrackpadQuickTray.Settings -> emptyList()
    }
    CodecksPanel(
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (tray == TrackpadQuickTray.Custom) "Custom" else "Dynamic",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actions.isEmpty()) {
                Text(
                    text = if (tray == TrackpadQuickTray.Custom) "Add deck buttons first" else "No dynamic buttons here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CustomActionRow(
                    actions = actions.take(6),
                    onAction = onCustomAction,
                    selectedActionId = selectedActionId,
                    contentPadding = PaddingValues(end = 4.dp),
                    isActionEnabled = { it.kind != ActionKind.Ssh || customActionsReady },
                )
            }
        }
    }
}

@Composable
internal fun TrackpadSettingsTray(
    pointerSpeed: Float,
    acceleration: Float,
    scrollSpeed: Float,
    naturalScroll: Boolean,
    scrollRailEnabled: Boolean,
    traceEnabled: Boolean,
    quietModeEnabled: Boolean,
    idleBlankTimeoutMillis: Int,
    hapticsEnabled: Boolean,
    doubleTapTimeoutMillis: Int,
    tapMovementThresholdPx: Float,
    phoneNotificationAccessReady: Boolean,
    phoneNotificationLaneEnabled: Boolean,
    onPointerSpeedChange: (Float) -> Unit,
    onAccelerationChange: (Float) -> Unit,
    onScrollSpeedChange: (Float) -> Unit,
    onNaturalScrollChange: (Boolean) -> Unit,
    onScrollRailEnabledChange: (Boolean) -> Unit,
    onTraceEnabledChange: (Boolean) -> Unit,
    onQuietModeEnabledChange: (Boolean) -> Unit,
    onIdleBlankTimeoutChange: (Int) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onDoubleTapTimeoutChange: (Int) -> Unit,
    onTapMovementThresholdChange: (Float) -> Unit,
    onTapCorrectionReset: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CodecksPanel(
        selected = true,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Trackpad settings", style = MaterialTheme.typography.titleSmall)
            if (phoneNotificationLaneEnabled && !phoneNotificationAccessReady) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null)
                            Column {
                                Text("Phone notifications", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Allow Android notification access",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                                )
                            }
                        }
                        TextButton(onClick = onOpenNotificationSettings) {
                            Text("Allow")
                        }
                    }
                }
            }
            TrackpadSliderSetting(
                label = "Pointer",
                valueLabel = when {
                    pointerSpeed < 0.75f -> "Fine"
                    pointerSpeed > 1.1f -> "Fast"
                    else -> "Normal"
                },
                value = pointerSpeed,
                valueRange = 0.3f..1.35f,
                onValueChange = onPointerSpeedChange,
            )
            TrackpadSliderSetting(
                label = "Acceleration",
                valueLabel = "%.1fx".format(acceleration),
                value = acceleration,
                valueRange = 0.5f..1.75f,
                onValueChange = onAccelerationChange,
            )
            TrackpadSliderSetting(
                label = "Scroll",
                valueLabel = "%.1fx".format(scrollSpeed),
                value = scrollSpeed,
                valueRange = 0.35f..1.8f,
                onValueChange = onScrollSpeedChange,
            )
            TrackpadSliderSetting(
                label = "Double tap",
                valueLabel = "${doubleTapTimeoutMillis}ms",
                value = doubleTapTimeoutMillis.toFloat(),
                valueRange = 350f..900f,
                onValueChange = { onDoubleTapTimeoutChange(it.roundToInt()) },
            )
            TrackpadSliderSetting(
                label = "Tap guard",
                valueLabel = "${tapMovementThresholdPx.roundToInt()}px",
                value = tapMovementThresholdPx,
                valueRange = TrackpadGestureEngine.MIN_TAP_MOVEMENT_THRESHOLD_PX..TrackpadGestureEngine.MAX_TAP_MOVEMENT_THRESHOLD_PX,
                onValueChange = onTapMovementThresholdChange,
            )
            TextButton(onClick = onTapCorrectionReset) {
                Text("Reset tap learning")
            }
            SettingsSwitchRow("Scroll rail", scrollRailEnabled, onScrollRailEnabledChange, enabled = true)
            SettingsSwitchRow("Natural scroll", naturalScroll, onNaturalScrollChange, enabled = true)
            SettingsSwitchRow("Quiet while using Trackpad", quietModeEnabled, onQuietModeEnabledChange, enabled = true)
            Text(
                "Quiet hides Codecks notification lanes while you use Trackpad. Pin app blocks accidental Home/recents gestures.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrackpadSliderSetting(
                label = "Screen blanks after idle",
                valueLabel = "${idleBlankTimeoutMillis / 1000}s",
                value = idleBlankTimeoutMillis.toFloat(),
                valueRange = 30_000f..600_000f,
                onValueChange = { onIdleBlankTimeoutChange((it / 5_000f).roundToInt() * 5_000) },
            )
            SettingsSwitchRow("Haptics", hapticsEnabled, onHapticsEnabledChange, enabled = true)
            SettingsSwitchRow("Pointer trace", traceEnabled, onTraceEnabledChange, enabled = true)
        }
    }
}

@Composable
private fun TrackpadSliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}
