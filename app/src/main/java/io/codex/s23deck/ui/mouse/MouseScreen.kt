package io.codex.s23deck.ui.mouse

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
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
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Swipe
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.codex.s23deck.HidCommand
import io.codex.s23deck.HidState
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.data.notifications.NotificationPreview
import io.codex.s23deck.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codex.s23deck.ui.designsystem.CodecksPanel
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.home.CustomActionRow
import io.codex.s23deck.ui.keyboard.HidHostHeader
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import io.codex.s23deck.core.trackpad.TrackpadClockStyle
import io.codex.s23deck.core.trackpad.TrackpadFloatingMenuLayout
import io.codex.s23deck.core.trackpad.TrackpadRailSide
import io.codex.s23deck.core.trackpad.TrackpadRotation
import io.codex.s23deck.core.trackpad.TrackpadSettings
import io.codex.s23deck.core.trackpad.TrackpadGestureEngine
import io.codex.s23deck.core.trackpad.TrackpadGestureEvent
import io.codex.s23deck.core.trackpad.TrackpadMotionMode
import io.codex.s23deck.core.trackpad.isTrackpadScrollZone
import io.codex.s23deck.core.trackpad.trackpadPointerGain
import java.time.format.DateTimeFormatter

@Composable
fun MouseScreen(
    state: HidState,
    settings: TrackpadSettings = TrackpadSettings(),
    onSettingsChange: ((TrackpadSettings) -> TrackpadSettings) -> Unit = {},
    contentPadding: PaddingValues,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onRefreshHosts: () -> Unit,
    onConnect: (String) -> Unit,
    onMove: (Float, Float) -> Unit,
    onScroll: (Int) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onMiddleClick: () -> Unit = {},
    onPress: (Int) -> Unit = {},
    onReleaseButtons: () -> Unit = {},
    onHorizontalScroll: (Int) -> Unit = {},
    onCommand: (HidCommand) -> Unit = {},
    customActions: List<DeckAction> = emptyList(),
    dynamicActions: List<DeckAction> = emptyList(),
    customActionsReady: Boolean = true,
    onCustomAction: (DeckAction) -> Unit = {},
    selectedActionId: String? = null,
    phoneNotifications: List<NotificationPreview> = emptyList(),
    laptopNotifications: List<NotificationPreview> = emptyList(),
    phoneNotificationAccessReady: Boolean = false,
    phoneNotificationLaneEnabled: Boolean = true,
    labsEnabled: Boolean = false,
    airMouseEnabled: Boolean = false,
    airTouchEnabled: Boolean = false,
    backTapAvailable: Boolean = false,
    volumeKeysAvailable: Boolean = false,
    airTouchCursor: Offset = Offset.Zero,
    airTouchConfirmSignal: Int = 0,
    onAirTouchActiveChange: (Boolean) -> Unit = {},
    onAirTouchDelta: (Float, Float) -> Unit = { _, _ -> },
    onAirTouchRecenter: () -> Unit = {},
    onAirTouchSampleConfirmed: (target: Offset, observed: Offset) -> Unit = { _, _ -> },
    onOpenBluetoothSettings: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onExitTrackpad: () -> Unit = {},
    sessionPinned: Boolean = false,
    onToggleSessionPin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sensitivity = settings.pointerSpeed
    val acceleration = settings.acceleration
    val naturalScroll = settings.naturalScroll
    val scrollScale = settings.scrollSpeed
    val dragLockEnabled = settings.dragLockEnabled
    val traceEnabled = settings.pointerTraceEnabled
    val scrollRailEnabled = settings.scrollRailEnabled
    val verticalRailDirection = if (settings.scrollRailInverted) ScrollRailDirection.Inverted else ScrollRailDirection.Direct
    val railSide = settings.railSide
    val rotation = settings.rotation
    val hapticsEnabled = settings.hapticsEnabled
    val backgroundOpacity = settings.backgroundOpacity
    val clockStyle = settings.clockStyle
    val floatingMenuLayout = settings.floatingMenuLayout
    val doubleTapTimeoutMillis = settings.doubleTapTimeoutMillis
    val sPenPrecisionEnabled = settings.sPenPrecisionEnabled
    val backTapEnabled = settings.backTapEnabled && backTapAvailable
    val volumeKeysEnabled = settings.volumeKeysEnabled && volumeKeysAvailable
    var inputMode by rememberSaveable { mutableStateOf(MouseInputMode.Trackpad) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }
    var quickTray by rememberSaveable { mutableStateOf<TrackpadQuickTray?>(null) }
    var keyboardTrayOpen by rememberSaveable { mutableStateOf(false) }
    var gyroSensitivity by rememberSaveable { mutableFloatStateOf(0.85f) }
    var gyroCalibration by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }
    var latestGyroSample by remember { mutableStateOf(Offset.Zero) }
    val scrollSign = if (naturalScroll) -1 else 1
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lifecycleOwner = LocalLifecycleOwner.current
    val gyroAvailable = remember(context) { hasGyroscope(context) }
    val sPenRemoteSdkAvailable = remember { hasSpenRemoteSdk() }
    val airTouchPoints = remember { mutableStateListOf<AirTouchPoint>() }
    fun confirmAirTouchPoint() {
        val target = AIR_TOUCH_TARGETS[airTouchPoints.size % AIR_TOUCH_TARGETS.size]
        airTouchPoints += AirTouchPoint(
            index = airTouchPoints.size + 1,
            target = target,
            cursor = airTouchCursor,
        )
        onAirTouchSampleConfirmed(target.position, airTouchCursor)
    }

    LaunchedEffect(labsEnabled, airMouseEnabled, airTouchEnabled, inputMode) {
        if (!labsEnabled ||
            (inputMode == MouseInputMode.AirMouse && !airMouseEnabled) ||
            (inputMode == MouseInputMode.AirTouch && !airTouchEnabled)
        ) {
            inputMode = MouseInputMode.Trackpad
        }
    }
    LaunchedEffect(inputMode) {
        onAirTouchActiveChange(inputMode == MouseInputMode.AirTouch)
    }
    LaunchedEffect(dynamicActions, quickTray) {
        if (quickTray == TrackpadQuickTray.Dynamic && dynamicActions.isEmpty()) {
            quickTray = null
        }
    }
    DisposableEffect(lifecycleOwner.lifecycle, inputMode, onReleaseButtons) {
        val observer = LifecycleEventObserver { _, event ->
            if (inputMode == MouseInputMode.Trackpad &&
                (event == Lifecycle.Event.ON_PAUSE ||
                    event == Lifecycle.Event.ON_STOP ||
                    event == Lifecycle.Event.ON_DESTROY)
            ) {
                onReleaseButtons()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (inputMode == MouseInputMode.Trackpad) onReleaseButtons()
        }
    }
    LaunchedEffect(airTouchConfirmSignal) {
        if (airTouchConfirmSignal > 0 && inputMode == MouseInputMode.AirTouch) {
            confirmAirTouchPoint()
        }
    }

    AirMouseEffect(
        enabled = inputMode == MouseInputMode.AirMouse && state.isConnected,
        context = context,
        lifecycle = lifecycleOwner.lifecycle,
        calibration = gyroCalibration,
        sensitivity = gyroSensitivity,
        onSample = { latestGyroSample = it },
        onMove = onMove,
    )
    BackTapEffect(
        enabled = backTapEnabled && state.isConnected,
        context = context,
        lifecycle = lifecycleOwner.lifecycle,
        onBackTap = onLeftClick,
    )
    BackHandler(enabled = inputMode == MouseInputMode.Trackpad) {
        if (quickTray != null) {
            quickTray = null
        } else if (!controlsOpen) {
            controlsOpen = true
        } else {
            controlsOpen = true
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(if (inputMode == MouseInputMode.Trackpad) PaddingValues(0.dp) else contentPadding),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.matchParentSize())
        val surfacePadding = if (inputMode == MouseInputMode.Trackpad) 0.dp else 16.dp
        Box(modifier = Modifier.fillMaxSize().padding(surfacePadding)) {
            when (inputMode) {
                MouseInputMode.Trackpad -> Trackpad(
                    onMove = onMove,
                    onLeftClick = onLeftClick,
                    onRightClick = onRightClick,
                    onScroll = { x, y ->
                        onHorizontalScroll((x * scrollSign * scrollScale).roundToInt())
                        onScroll((y * scrollSign * scrollScale).roundToInt())
                    },
                    onRailScroll = { amount -> onScroll((amount * verticalRailDirection.sign * scrollScale).roundToInt()) },
                    onCommand = onCommand,
                    onPress = onPress,
                    onReleaseButtons = onReleaseButtons,
                    dragLockEnabled = dragLockEnabled,
                    traceEnabled = traceEnabled,
                    stylusEnabled = sPenPrecisionEnabled,
                    scrollRailEnabled = scrollRailEnabled,
                    railSide = railSide,
                    rotation = rotation,
                    hapticsEnabled = hapticsEnabled,
                    doubleTapTimeoutMillis = doubleTapTimeoutMillis,
                    backgroundOpacity = backgroundOpacity,
                    clockStyle = clockStyle,
                    phoneNotifications = phoneNotifications,
                    laptopNotifications = laptopNotifications,
                    phoneNotificationAccessReady = phoneNotificationAccessReady,
                    phoneNotificationLaneEnabled = phoneNotificationLaneEnabled,
                    onDragLockChange = { enabled ->
                        onSettingsChange { it.copy(dragLockEnabled = enabled) }
                        if (enabled) onPress(1) else onReleaseButtons()
                    },
                    controlsOpen = controlsOpen,
                    sessionPinned = sessionPinned,
                    onDoubleTap = {
                        if (controlsOpen) {
                            controlsOpen = false
                            quickTray = null
                        }
                    },
                    sensitivity = sensitivity,
                    acceleration = acceleration,
                    enabled = state.isConnected,
                    modifier = Modifier.fillMaxSize(),
                )
                MouseInputMode.AirTouch -> AirTouchSurface(
                        enabled = state.isConnected,
                        cursor = airTouchCursor,
                        calibrationPoints = airTouchPoints,
                        remoteSdkAvailable = sPenRemoteSdkAvailable,
                        onConfirmPoint = ::confirmAirTouchPoint,
                        onClearPoints = { airTouchPoints.clear() },
                        onRecenter = onAirTouchRecenter,
                        onMove = onAirTouchDelta,
                        onLeftClick = onLeftClick,
                        onRightClick = onRightClick,
                    modifier = Modifier.fillMaxSize(),
                )
                MouseInputMode.AirMouse -> AirMouseSurface(
                        enabled = state.isConnected,
                        latestGyroSample = latestGyroSample,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (controlsOpen && inputMode == MouseInputMode.Trackpad) {
                TrackpadFloatingMenu(
                    layout = floatingMenuLayout,
                    selectedTray = quickTray,
                    dynamicEnabled = dynamicActions.isNotEmpty(),
                    onCustom = { quickTray = if (quickTray == TrackpadQuickTray.Custom) null else TrackpadQuickTray.Custom },
                    onDynamic = { quickTray = if (quickTray == TrackpadQuickTray.Dynamic) null else TrackpadQuickTray.Dynamic },
                    onSettings = { quickTray = if (quickTray == TrackpadQuickTray.Settings) null else TrackpadQuickTray.Settings },
                    sessionPinned = sessionPinned,
                    onToggleSessionPin = onToggleSessionPin,
                    onExit = onExitTrackpad,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            start = 18.dp,
                            end = if (railSide == TrackpadRailSide.Right) 42.dp else 18.dp,
                            bottom = 24.dp,
                        ),
                )
                quickTray?.let { tray ->
                    val trayModifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            start = 18.dp,
                            end = if (railSide == TrackpadRailSide.Right) 42.dp else 18.dp,
                            bottom = if (floatingMenuLayout == TrackpadFloatingMenuLayout.Vertical) 232.dp else 100.dp,
                        )
                        .fillMaxWidth()
                        .fillMaxHeight(0.66f)
                        .widthIn(max = 460.dp)
                    if (tray == TrackpadQuickTray.Settings) {
                        TrackpadSettingsTray(
                            pointerSpeed = sensitivity,
                            acceleration = acceleration,
                            scrollSpeed = scrollScale,
                            naturalScroll = naturalScroll,
                            scrollRailEnabled = scrollRailEnabled,
                            traceEnabled = traceEnabled,
                            hapticsEnabled = hapticsEnabled,
                            doubleTapTimeoutMillis = doubleTapTimeoutMillis,
                            phoneNotificationAccessReady = phoneNotificationAccessReady,
                            phoneNotificationLaneEnabled = phoneNotificationLaneEnabled,
                            onPointerSpeedChange = { value -> onSettingsChange { it.copy(pointerSpeed = value) } },
                            onAccelerationChange = { value -> onSettingsChange { it.copy(acceleration = value) } },
                            onScrollSpeedChange = { value -> onSettingsChange { it.copy(scrollSpeed = value) } },
                            onNaturalScrollChange = { value -> onSettingsChange { it.copy(naturalScroll = value) } },
                            onScrollRailEnabledChange = { value -> onSettingsChange { it.copy(scrollRailEnabled = value) } },
                            onTraceEnabledChange = { value -> onSettingsChange { it.copy(pointerTraceEnabled = value) } },
                            onHapticsEnabledChange = { value -> onSettingsChange { it.copy(hapticsEnabled = value) } },
                            onDoubleTapTimeoutChange = { value -> onSettingsChange { it.copy(doubleTapTimeoutMillis = value) } },
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            modifier = trayModifier,
                        )
                    } else {
                        TrackpadQuickTrayPanel(
                            tray = tray,
                            customActions = customActions,
                            dynamicActions = dynamicActions,
                            customActionsReady = customActionsReady,
                            onCustomAction = onCustomAction,
                            selectedActionId = selectedActionId,
                            modifier = trayModifier,
                        )
                    }
                }
            } else if (controlsOpen) {
                CodecksPanel(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .fillMaxHeight(if (landscape) 0.92f else 0.62f),
                ) {
                    MouseControls(
                        state = state,
                        permissionGranted = permissionGranted,
                        onRequestPermission = onRequestPermission,
                        onStart = onStart,
                        onRefreshHosts = onRefreshHosts,
                        onConnect = onConnect,
                        onScroll = { amount -> onScroll(amount * scrollSign) },
                        onHorizontalScroll = onHorizontalScroll,
                        onLeftClick = onLeftClick,
                        onRightClick = onRightClick,
                        onMiddleClick = onMiddleClick,
                        onCommand = onCommand,
                        onPress = onPress,
                        onReleaseButtons = onReleaseButtons,
                        sensitivity = sensitivity,
                        onSensitivityChange = { value -> onSettingsChange { it.copy(pointerSpeed = value) } },
                        inputMode = inputMode,
                        onInputModeChange = {
                            inputMode = it
                            if (it != MouseInputMode.Trackpad && dragLockEnabled) {
                                onSettingsChange { settings -> settings.copy(dragLockEnabled = false) }
                                onReleaseButtons()
                            }
                        },
                        naturalScroll = naturalScroll,
                        onNaturalScrollChange = { value -> onSettingsChange { it.copy(naturalScroll = value) } },
                        scrollSpeed = scrollScale,
                        onScrollSpeedChange = { value -> onSettingsChange { it.copy(scrollSpeed = value) } },
                        scrollRailEnabled = scrollRailEnabled,
                        onScrollRailEnabledChange = { value -> onSettingsChange { it.copy(scrollRailEnabled = value) } },
                        verticalRailDirection = verticalRailDirection,
                        onVerticalRailDirectionChange = { value ->
                            onSettingsChange { it.copy(scrollRailInverted = value == ScrollRailDirection.Inverted) }
                        },
                        dragLockEnabled = dragLockEnabled,
                        onDragLockChange = { enabled ->
                            onSettingsChange { it.copy(dragLockEnabled = enabled) }
                            if (enabled) onPress(1) else onReleaseButtons()
                        },
                        traceEnabled = traceEnabled,
                        onTraceEnabledChange = { value -> onSettingsChange { it.copy(pointerTraceEnabled = value) } },
                        sPenPrecisionEnabled = sPenPrecisionEnabled,
                        onSPenPrecisionEnabledChange = { value -> onSettingsChange { it.copy(sPenPrecisionEnabled = value) } },
                        backTapEnabled = backTapEnabled,
                        onBackTapEnabledChange = { value -> onSettingsChange { it.copy(backTapEnabled = value) } },
                        volumeKeysEnabled = volumeKeysEnabled,
                        onVolumeKeysEnabledChange = { value -> onSettingsChange { it.copy(volumeKeysEnabled = value) } },
                        airMouseEnabled = airMouseEnabled,
                        airTouchEnabled = airTouchEnabled,
                        backTapAvailable = backTapAvailable,
                        volumeKeysAvailable = volumeKeysAvailable,
                        gyroAvailable = gyroAvailable,
                        gyroSensitivity = gyroSensitivity,
                        onGyroSensitivityChange = { gyroSensitivity = it },
                        gyroStatus = latestGyroSample,
                        onGyroCalibrate = { gyroCalibration = latestGyroSample },
                        showHostHeader = false,
                        customActions = customActions,
                        customActionsReady = customActionsReady,
                        onCustomAction = onCustomAction,
                        selectedActionId = selectedActionId,
                        modifier = Modifier.fillMaxWidth().padding(14.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
            if (!controlsOpen && inputMode == MouseInputMode.Trackpad) {
                TrackpadBottomBar(
                    connected = state.isConnected,
                    dragLockEnabled = dragLockEnabled,
                    onKeyboard = { keyboardTrayOpen = true },
                    onDragLock = {
                        val enabled = !dragLockEnabled
                        onSettingsChange { it.copy(dragLockEnabled = enabled) }
                        if (enabled) onPress(1) else onReleaseButtons()
                    },
                    onMore = {
                        if (state.isConnected) {
                            controlsOpen = true
                            quickTray = null
                        } else {
                            onOpenBluetoothSettings()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                )
            }
        }
    }
    if (keyboardTrayOpen) {
        AlertDialog(
            onDismissRequest = { keyboardTrayOpen = false },
            title = { Text("Keyboard shortcuts") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    KeyboardCommandGrid(enabled = state.isConnected, onCommand = onCommand)
                }
            },
            confirmButton = {
                TextButton(onClick = { keyboardTrayOpen = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun TrackpadBottomBar(
    connected: Boolean,
    dragLockEnabled: Boolean,
    onKeyboard: () -> Unit,
    onDragLock: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            TrackpadBottomAction(
                label = "Keyboard",
                icon = Icons.Outlined.Keyboard,
                enabled = connected,
                onClick = onKeyboard,
                modifier = Modifier.weight(1f),
            )
            TrackpadBottomAction(
                label = if (dragLockEnabled) "Dragging" else "Drag lock",
                icon = Icons.Outlined.Lock,
                selected = dragLockEnabled,
                enabled = connected,
                onClick = onDragLock,
                modifier = Modifier.weight(1f),
            )
            TrackpadBottomAction(
                label = if (connected) "More" else "Setup",
                icon = if (connected) Icons.Outlined.Tune else Icons.Outlined.Bluetooth,
                selected = !connected,
                onClick = onMore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrackpadBottomAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.large,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun MouseControls(
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
                title = "Trackpad targets",
                disconnectedTitle = "Trackpad ready",
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
private fun TrackpadFloatingMenu(
    layout: TrackpadFloatingMenuLayout,
    selectedTray: TrackpadQuickTray?,
    dynamicEnabled: Boolean,
    onCustom: () -> Unit,
    onDynamic: () -> Unit,
    onSettings: () -> Unit,
    sessionPinned: Boolean,
    onToggleSessionPin: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        add(TrackpadQuickTray.Custom to Icons.Outlined.GridView)
        if (dynamicEnabled) add(TrackpadQuickTray.Dynamic to Icons.Outlined.AutoAwesome)
        add(TrackpadQuickTray.Settings to Icons.Outlined.Settings)
    }
    CodecksPanel(
        modifier = modifier,
    ) {
        val itemModifier = Modifier.size(56.dp)
        if (layout == TrackpadFloatingMenuLayout.Horizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(6.dp)) {
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
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(6.dp)) {
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
            }
        }
    }
}

@Composable
private fun TrackpadMenuIcon(
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
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrackpadQuickTrayPanel(
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
private fun TrackpadSettingsTray(
    pointerSpeed: Float,
    acceleration: Float,
    scrollSpeed: Float,
    naturalScroll: Boolean,
    scrollRailEnabled: Boolean,
    traceEnabled: Boolean,
    hapticsEnabled: Boolean,
    doubleTapTimeoutMillis: Int,
    phoneNotificationAccessReady: Boolean,
    phoneNotificationLaneEnabled: Boolean,
    onPointerSpeedChange: (Float) -> Unit,
    onAccelerationChange: (Float) -> Unit,
    onScrollSpeedChange: (Float) -> Unit,
    onNaturalScrollChange: (Boolean) -> Unit,
    onScrollRailEnabledChange: (Boolean) -> Unit,
    onTraceEnabledChange: (Boolean) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onDoubleTapTimeoutChange: (Int) -> Unit,
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
            SettingsSwitchRow("Scroll rail", scrollRailEnabled, onScrollRailEnabledChange, enabled = true)
            SettingsSwitchRow("Natural scroll", naturalScroll, onNaturalScrollChange, enabled = true)
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

@Composable
private fun Trackpad(
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Float, Float) -> Unit,
    onRailScroll: (Int) -> Unit,
    onCommand: (HidCommand) -> Unit,
    onPress: (Int) -> Unit,
    onReleaseButtons: () -> Unit,
    dragLockEnabled: Boolean,
    onDragLockChange: (Boolean) -> Unit,
    traceEnabled: Boolean,
    stylusEnabled: Boolean,
    scrollRailEnabled: Boolean,
    railSide: TrackpadRailSide,
    rotation: TrackpadRotation,
    hapticsEnabled: Boolean,
    doubleTapTimeoutMillis: Int,
    backgroundOpacity: Float,
    clockStyle: TrackpadClockStyle,
    phoneNotifications: List<NotificationPreview>,
    laptopNotifications: List<NotificationPreview>,
    phoneNotificationAccessReady: Boolean,
    phoneNotificationLaneEnabled: Boolean,
    controlsOpen: Boolean,
    sessionPinned: Boolean,
    onDoubleTap: () -> Unit,
    sensitivity: Float,
    acceleration: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tracePoints = remember { mutableStateListOf<PointerTracePoint>() }
    val traceColor = MaterialTheme.colorScheme.primary
    val stylusTraceColor = MaterialTheme.colorScheme.tertiary
    LaunchedEffect(tracePoints.size) {
        while (tracePoints.isNotEmpty()) {
            delay(80L)
            val now = SystemClock.uptimeMillis()
            tracePoints.removeAll { now - it.timestampMillis > TRACE_TTL_MS }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .testTag(TrackpadTestTag)
        ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge)) {
            TrackpadSurfaceDecoration(modifier = Modifier.matchParentSize())
            if (!controlsOpen) {
                TrackpadCenterHint(
                    enabled = enabled,
                    dragLockEnabled = dragLockEnabled,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            RawTrackpadTouchLayer(
                enabled = enabled,
                sensitivity = sensitivity,
                acceleration = acceleration,
                dragLockEnabled = dragLockEnabled,
                railSide = railSide,
                onDoubleTap = { if (controlsOpen) onDoubleTap() },
                rotation = rotation,
                hapticsEnabled = hapticsEnabled,
                doubleTapTimeoutMillis = doubleTapTimeoutMillis,
                onMove = onMove,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
                onScroll = onScroll,
                onCommand = onCommand,
                onPress = onPress,
                onReleaseButtons = onReleaseButtons,
                stylusEnabled = stylusEnabled,
                onTrace = { point ->
                    if (traceEnabled) {
                        val now = SystemClock.uptimeMillis()
                        tracePoints.removeAll { now - it.timestampMillis > TRACE_TTL_MS }
                        val farEnough = tracePoints.lastOrNull()?.let {
                            (it.position - point.position).getDistance() >= TRACE_MIN_DISTANCE_PX
                        } ?: true
                        if (farEnough) {
                            tracePoints += point
                            while (tracePoints.size > TRACE_MAX_POINTS) tracePoints.removeAt(0)
                        }
                    } else if (tracePoints.isNotEmpty()) {
                        tracePoints.clear()
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                val now = SystemClock.uptimeMillis()
                tracePoints.zipWithNext().forEach { (start, end) ->
                    val age = now - end.timestampMillis
                    val alpha = (1f - (age.toFloat() / TRACE_TTL_MS)).coerceIn(0f, 0.64f)
                    val color = if (end.isStylus) stylusTraceColor else traceColor
                    drawLine(
                        color = color.copy(alpha = alpha),
                        start = start.position,
                        end = end.position,
                        strokeWidth = if (end.isStylus) 4.5f else 6.5f,
                        cap = StrokeCap.Round,
                    )
                }
                tracePoints.lastOrNull()?.let { last ->
                    val age = now - last.timestampMillis
                    val alpha = (1f - (age.toFloat() / TRACE_TTL_MS)).coerceIn(0f, 0.52f)
                    val color = if (last.isStylus) stylusTraceColor else traceColor
                    drawCircle(color.copy(alpha = alpha), radius = if (last.isStylus) 7f else 9f, center = last.position)
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                contentColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.36f else 0.18f)),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(if (railSide == TrackpadRailSide.Right) Alignment.TopEnd else Alignment.TopStart)
                    .padding(
                        start = if (railSide == TrackpadRailSide.Left) 64.dp else 16.dp,
                        top = 16.dp,
                        end = if (railSide == TrackpadRailSide.Right) 64.dp else 16.dp,
                    ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.Swipe, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (enabled) "Trackpad ready" else "Setup needed",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (controlsOpen) {
                TrackpadGuardHint(
                    sessionPinned = sessionPinned,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 106.dp),
                )
            }
            if (scrollRailEnabled) {
                ScrollRail(
                    orientation = ScrollRailOrientation.Vertical,
                    enabled = enabled,
                    hapticsEnabled = hapticsEnabled,
                    onStep = onRailScroll,
                    modifier = Modifier
                        .align(if (railSide == TrackpadRailSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(
                            start = if (railSide == TrackpadRailSide.Left) 6.dp else 0.dp,
                            end = if (railSide == TrackpadRailSide.Right) 6.dp else 0.dp,
                        )
                        .fillMaxHeight(0.42f)
                        .widthIn(min = 14.dp, max = 16.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun TrackpadCenterHint(
    enabled: Boolean,
    dragLockEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (dragLockEnabled) Icons.Outlined.Lock else Icons.Outlined.Mouse,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            text = when {
                !enabled -> "Pair Bluetooth HID"
                dragLockEnabled -> "Drag lock active"
                else -> "Move · tap · scroll"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (enabled) {
                "One finger moves. Two fingers scroll. Tap with two fingers for right click."
            } else {
                "Complete Trackpad setup to control your Mac pointer."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TrackpadGuardHint(sessionPinned: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Text(
            text = if (sessionPinned) {
                "Session locked · Exit or Android unpin gesture releases Trackpad"
            } else {
                "Back opens controls · Tap Lock to protect against Home gestures"
            },
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TrackpadSurfaceDecoration(
    modifier: Modifier = Modifier,
) {
    val laneColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
    val cornerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
    Canvas(modifier = modifier) {
        val edge = 22.dp.toPx()
        val short = 34.dp.toPx()
        val stroke = 1.dp.toPx()
        listOf(size.height / 3f, size.height * 2f / 3f).forEach { y ->
            drawLine(laneColor, Offset(edge, y), Offset(size.width - edge, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        listOf(size.width / 3f, size.width * 2f / 3f).forEach { x ->
            drawLine(laneColor.copy(alpha = 0.025f), Offset(x, edge), Offset(x, size.height - edge), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawLine(cornerColor, Offset(edge, edge), Offset(edge + short, edge), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(edge, edge), Offset(edge, edge + short), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(size.width - edge, edge), Offset(size.width - edge - short, edge), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(size.width - edge, edge), Offset(size.width - edge, edge + short), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(edge, size.height - edge), Offset(edge + short, size.height - edge), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(edge, size.height - edge), Offset(edge, size.height - edge - short), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(size.width - edge, size.height - edge), Offset(size.width - edge - short, size.height - edge), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(cornerColor, Offset(size.width - edge, size.height - edge), Offset(size.width - edge, size.height - edge - short), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun TrackpadBackground(
    phoneNotifications: List<NotificationPreview>,
    laptopNotifications: List<NotificationPreview>,
    clockText: String,
    dayText: String,
    dateText: String,
    clockStyle: TrackpadClockStyle,
    landscape: Boolean,
    controlsOpen: Boolean,
    railSide: TrackpadRailSide,
    opacity: Float,
    phoneNotificationAccessReady: Boolean,
    phoneNotificationLaneEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = opacity.coerceIn(0.24f, 0.78f)
    val phoneEmptyText = when {
        !phoneNotificationAccessReady -> "Enable notification access"
        else -> "Waiting for notifications"
    }
    if (landscape) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = modifier,
        ) {
            if (phoneNotificationLaneEnabled) {
                NotificationLane(
                    title = "Phone",
                    notifications = phoneNotifications,
                    emptyText = phoneEmptyText,
                    alpha = alpha,
                    modifier = Modifier.fillMaxHeight().weight(1f),
                )
            }
            NotificationLane(
                title = "Mac",
                notifications = laptopNotifications,
                emptyText = "Recent Mac actions appear here",
                alpha = alpha,
                modifier = Modifier.fillMaxHeight().weight(1f),
            )
            ClockLane(
                clockText = clockText,
                dayText = dayText,
                dateText = dateText,
                style = clockStyle,
                alpha = alpha,
                center = true,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(
                        start = if (controlsOpen && railSide == TrackpadRailSide.Left) 72.dp else 0.dp,
                        end = if (controlsOpen && railSide == TrackpadRailSide.Right) 72.dp else 0.dp,
                    ),
            )
        }
        return
    }
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier,
    ) {
        if (phoneNotificationLaneEnabled) {
            NotificationLane(
                title = "Phone",
                notifications = phoneNotifications,
                emptyText = phoneEmptyText,
                alpha = alpha,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        NotificationLane(
            title = "Mac",
            notifications = laptopNotifications,
            emptyText = "Recent Mac actions appear here",
            alpha = alpha,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        ClockLane(
            clockText = clockText,
            dayText = dayText,
            dateText = dateText,
            style = clockStyle,
            alpha = alpha,
            center = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    bottom = if (controlsOpen) 96.dp else 0.dp,
                    end = if (controlsOpen && railSide == TrackpadRailSide.Right) 84.dp else 0.dp,
                    start = if (controlsOpen && railSide == TrackpadRailSide.Left) 84.dp else 0.dp,
                ),
        )
    }
}

@Composable
private fun NotificationLane(
    title: String,
    notifications: List<NotificationPreview>,
    emptyText: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = textColor.copy(alpha = (alpha + 0.18f).coerceAtMost(0.9f)),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val preview = notifications.take(3)
        if (preview.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = alpha),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            ) {
                preview.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
                        contentColor = textColor,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(0.92f),
                    ) {
                        Text(
                            text = buildString {
                                append(item.source)
                                if (item.title.isNotBlank()) append(" · ").append(item.title)
                                if (item.text.isNotBlank()) append(" — ").append(item.text)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockLane(
    clockText: String,
    dayText: String,
    dateText: String,
    style: TrackpadClockStyle,
    alpha: Float,
    center: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = (alpha + 0.18f).coerceAtMost(0.9f))
    val horizontal = style == TrackpadClockStyle.Compact
    if (horizontal) {
        Row(
            horizontalArrangement = if (center) Arrangement.Center else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            Text(clockText, style = MaterialTheme.typography.displayMedium, color = color, maxLines = 1, textAlign = TextAlign.Center)
            Text(
                "$dayText · $dateText",
                style = MaterialTheme.typography.titleMedium,
                color = color.copy(alpha = alpha),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            horizontalAlignment = if (center || style == TrackpadClockStyle.Focus) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = if (center) Arrangement.Center else Arrangement.Bottom,
            modifier = modifier,
        ) {
            Text(
                text = clockText,
                style = if (style == TrackpadClockStyle.Focus) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                color = color,
                textAlign = if (center || style == TrackpadClockStyle.Focus) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "$dayText · $dateText",
                style = MaterialTheme.typography.titleMedium,
                color = color.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (center || style == TrackpadClockStyle.Focus) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScrollRail(
    orientation: ScrollRailOrientation,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeOffset by remember { mutableStateOf<Offset?>(null) }
    var activeStep by remember { mutableIntStateOf(0) }
    var lastHapticStep by remember { mutableIntStateOf(0) }
    var railSize by remember { mutableStateOf(IntSize.Zero) }
    val haptics = LocalHapticFeedback.current
    val railColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
    val centerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    val activeColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(enabled, activeStep) {
        while (enabled && activeStep != 0) {
            onStep(activeStep)
            delay(42L)
        }
    }

    Surface(
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
            activeOffset != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.46f)
        },
        contentColor = if (activeOffset != null) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (activeOffset != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .onSizeChanged { size ->
                railSize = size
                activeStep = activeOffset?.let { railStep(it, size, orientation) } ?: 0
            }
            .pointerInput(enabled, orientation) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    activeOffset = down.position
                    activeStep = railStep(down.position, railSize, orientation)
                    lastHapticStep = activeStep
                    down.consume()
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    do {
                        val event = awaitPointerEvent()
                        val active = event.changes.firstOrNull { it.pressed }
                        if (active != null) {
                            activeOffset = active.position
                            val nextStep = railStep(active.position, railSize, orientation)
                            if (nextStep != activeStep && nextStep != 0 && nextStep != lastHapticStep) {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticStep = nextStep
                            }
                            activeStep = nextStep
                            active.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    activeOffset = null
                    activeStep = 0
                    lastHapticStep = 0
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            if (orientation == ScrollRailOrientation.Vertical) {
                drawLine(
                    color = railColor,
                    start = Offset(center.x, 4f),
                    end = Offset(center.x, size.height - 4f),
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round,
                )
            } else {
                drawLine(
                    color = railColor,
                    start = Offset(4f, center.y),
                    end = Offset(size.width - 4f, center.y),
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(color = centerColor, radius = 3.5f, center = center)
            activeOffset?.let { offset ->
                drawCircle(
                    color = activeColor,
                    radius = 5.5f,
                    center = Offset(
                        x = offset.x.coerceIn(0f, size.width),
                        y = offset.y.coerceIn(0f, size.height),
                    ),
                )
            }
        }
    }
}

private fun railStep(
    offset: Offset,
    railSize: IntSize,
    orientation: ScrollRailOrientation,
): Int {
    if (railSize.width <= 0 || railSize.height <= 0) return 0
    val axisSize = if (orientation == ScrollRailOrientation.Vertical) railSize.height.toFloat() else railSize.width.toFloat()
    val axis = if (orientation == ScrollRailOrientation.Vertical) offset.y else offset.x
    val centered = ((axis - (axisSize / 2f)) / (axisSize / 2f)).coerceIn(-1f, 1f)
    val distance = abs(centered)
    if (distance < 0.16f) return 0
    val magnitude = when {
        distance > 0.72f -> 6
        distance > 0.42f -> 4
        else -> 2
    }
    return if (centered < 0f) -magnitude else magnitude
}

@Composable
private fun RawTrackpadTouchLayer(
    enabled: Boolean,
    sensitivity: Float,
    acceleration: Float,
    dragLockEnabled: Boolean,
    railSide: TrackpadRailSide,
    rotation: TrackpadRotation,
    hapticsEnabled: Boolean,
    doubleTapTimeoutMillis: Int,
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Float, Float) -> Unit,
    onCommand: (HidCommand) -> Unit,
    onPress: (Int) -> Unit,
    onReleaseButtons: () -> Unit,
    onDoubleTap: () -> Unit,
    stylusEnabled: Boolean,
    onTrace: (PointerTracePoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            RawTrackpadView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        },
        modifier = modifier,
        update = { view ->
            view.enabledForInput = enabled
            view.sensitivity = sensitivity
            view.acceleration = acceleration
            view.dragLockEnabled = dragLockEnabled
            view.railSide = railSide
            view.rotation = rotation
            view.hapticsEnabled = hapticsEnabled
            view.doubleTapTimeoutMillis = doubleTapTimeoutMillis.coerceIn(350, 900)
            view.onMove = onMove
            view.onLeftClick = onLeftClick
            view.onRightClick = onRightClick
            view.onScroll = onScroll
            view.onCommand = onCommand
            view.onPress = onPress
            view.onReleaseButtons = onReleaseButtons
            view.onDoubleTap = onDoubleTap
            view.stylusEnabled = stylusEnabled
            view.onTrace = onTrace
        },
    )
}

private data class PointerTracePoint(
    val position: Offset,
    val timestampMillis: Long,
    val isStylus: Boolean,
)

private class RawTrackpadView(context: Context) : View(context) {
    var enabledForInput: Boolean = true
    var sensitivity: Float = 1f
    var acceleration: Float = 1f
    var dragLockEnabled: Boolean = false
    var railSide: TrackpadRailSide = TrackpadRailSide.Right
    var rotation: TrackpadRotation = TrackpadRotation.Deg0
    var hapticsEnabled: Boolean = true
    var doubleTapTimeoutMillis: Int = 520
    var onMove: (Float, Float) -> Unit = { _, _ -> }
    var onLeftClick: () -> Unit = {}
    var onRightClick: () -> Unit = {}
    var onScroll: (Float, Float) -> Unit = { _, _ -> }
    var onCommand: (HidCommand) -> Unit = {}
    var onPress: (Int) -> Unit = {}
    var onReleaseButtons: () -> Unit = {}
    var onDoubleTap: () -> Unit = {}
    var stylusEnabled: Boolean = true
    var onTrace: (PointerTracePoint) -> Unit = {}

    private val activePointers = linkedMapOf<Int, Offset>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var maxPointers = 0
    private var totalPan = Offset.Zero
    private var lastCentroid: Offset? = null
    private var lastPointerDelta = Offset.Zero
    private var pointerVelocity = Offset.Zero
    private var lastPointerMoveTimeMs = 0L
    private var lastHoverPosition: Offset? = null
    private var leftButtonHeld = false
    private var lastTapUpTimeMs = 0L
    private var tapDragArmedUntil = 0L
    private var scrollZonePointerId: Int? = null
    private var lastScrollZoneY = 0f
    private var lastScrollZoneHapticY = 0f
    private val gestureEngine = TrackpadGestureEngine()
    private val longPressRunnable = Runnable {
        if (!dragLockEnabled &&
            enabledForInput &&
            maxPointers == 1 &&
            activePointers.size == 1 &&
            totalPan.getDistanceSquared() <= touchSlop * touchSlop
        ) {
            startLeftDrag("DragStart")
        }
    }

    init {
        isClickable = true
        isLongClickable = true
        isFocusable = true
        contentDescription = "Trackpad. Swipe to move the Mac pointer. Activate for left click. Long press for right click."
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (enabledForInput) onLeftClick()
        return enabledForInput
    }

    override fun performLongClick(): Boolean {
        super.performLongClick()
        if (enabledForInput) onRightClick()
        return enabledForInput
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledForInput) {
            resetGesture()
            return false
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                addOrUpdatePointer(event, event.actionIndex)
                maxPointers = maxOf(maxPointers, activePointers.size)
                lastCentroid = centroid()
                if (isInScrollZone(event.x)) {
                    scrollZonePointerId = event.getPointerId(event.actionIndex)
                    lastScrollZoneY = event.y
                    lastScrollZoneHapticY = event.y
                    if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } else if (!dragLockEnabled && event.eventTime <= tapDragArmedUntil) {
                    tapDragArmedUntil = 0L
                    startLeftDrag("TapDragStart")
                } else {
                    postDelayed(longPressRunnable, DRAG_HOLD_TIMEOUT_MS)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPressRunnable)
                tapDragArmedUntil = 0L
                scrollZonePointerId = null
                addOrUpdatePointer(event, event.actionIndex)
                updateAllPointers(event)
                maxPointers = maxOf(maxPointers, activePointers.size, event.pointerCount)
                lastCentroid = centroid()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val scrollPointerId = scrollZonePointerId
                if (scrollPointerId != null && event.pointerCount == 1) {
                    val index = event.findPointerIndex(scrollPointerId)
                    if (index >= 0) {
                        val y = event.getY(index)
                        val deltaY = y - lastScrollZoneY
                        val position = Offset(event.getX(index), y)
                        onTrace(PointerTracePoint(position, event.eventTime, event.isStylusEvent()))
                        if (abs(deltaY) >= SCROLL_ZONE_DEADZONE_PX) {
                            val rotatedScroll = Offset(0f, deltaY / SCROLL_ZONE_DIVISOR).rotated(rotation)
                            onScroll(rotatedScroll.x, rotatedScroll.y)
                            lastScrollZoneY = y
                            if (abs(y - lastScrollZoneHapticY) >= SCROLL_ZONE_HAPTIC_STEP_PX) {
                                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                lastScrollZoneHapticY = y
                            }
                        }
                        return true
                    }
                }
                updateAllPointers(event)
                maxPointers = maxOf(maxPointers, activePointers.size, event.pointerCount)
                val nextCentroid = centroid()
                val previousCentroid = lastCentroid
                if (nextCentroid != null && previousCentroid != null) {
                    val delta = nextCentroid - previousCentroid
                    val stylus = stylusEnabled && event.isStylusEvent()
                    onTrace(PointerTracePoint(nextCentroid, event.eventTime, stylus))
                    totalPan += delta
                    if (!leftButtonHeld && maxPointers == 1 && totalPan.getDistanceSquared() > touchSlop * touchSlop) {
                        removeCallbacks(longPressRunnable)
                    }
                    when (gestureEngine.motionFor(maxOf(activePointers.size, event.pointerCount))) {
                        TrackpadMotionMode.Pointer -> {
                            val smoothed = smartPointerDelta(delta, event.eventTime, leftButtonHeld, stylus)
                            if (smoothed != Offset.Zero) {
                                val rotated = smoothed.rotated(rotation)
                                onMove(rotated.x * sensitivity, rotated.y * sensitivity)
                            }
                        }
                        TrackpadMotionMode.Scroll -> {
                            val rotated = Offset(delta.x / 10f, delta.y / 10f).rotated(rotation)
                            onScroll(rotated.x, rotated.y)
                        }
                        TrackpadMotionMode.ReservedGesture -> Unit
                    }
                }
                lastCentroid = nextCentroid
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (leftButtonHeld) {
                    if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onReleaseButtons()
                    resetGesture()
                    return true
                }
                maxPointers = maxOf(maxPointers, activePointers.size, event.pointerCount)
                updateAllPointers(event, skipActionIndex = event.actionIndex)
                activePointers.remove(event.getPointerId(event.actionIndex))
                lastCentroid = centroid()
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                maxPointers = maxOf(maxPointers, activePointers.size, event.pointerCount)
                updateAllPointers(event, skipActionIndex = event.actionIndex)
                if (leftButtonHeld) {
                    if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onReleaseButtons()
                    resetGesture()
                    return true
                }
                finishGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (leftButtonHeld) onReleaseButtons()
                resetGesture()
                return true
            }
        }
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!enabledForInput || !stylusEnabled || !event.isStylusEvent()) {
            lastHoverPosition = null
            return false
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        val position = Offset(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                lastHoverPosition = position
                onTrace(PointerTracePoint(position, event.eventTime, isStylus = true))
                return true
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                val previous = lastHoverPosition
                if (previous != null) {
                    val delta = position - previous
                    onTrace(PointerTracePoint(position, event.eventTime, isStylus = true))
                    val smoothed = smartPointerDelta(delta, event.eventTime, dragging = false, stylus = true)
                    if (smoothed != Offset.Zero) {
                        val rotated = smoothed.rotated(rotation)
                        onMove(
                            rotated.x * sensitivity * STYLUS_HOVER_GAIN,
                            rotated.y * sensitivity * STYLUS_HOVER_GAIN,
                        )
                    }
                }
                lastHoverPosition = position
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                lastHoverPosition = null
                pointerVelocity = Offset.Zero
                lastPointerDelta = Offset.Zero
                lastPointerMoveTimeMs = 0L
                return true
            }
        }
        return true
    }

    private fun addOrUpdatePointer(event: MotionEvent, index: Int) {
        activePointers[event.getPointerId(index)] = Offset(event.getX(index), event.getY(index))
    }

    private fun updateAllPointers(event: MotionEvent, skipActionIndex: Int? = null) {
        for (index in 0 until event.pointerCount) {
            if (index == skipActionIndex) continue
            addOrUpdatePointer(event, index)
        }
    }

    private fun centroid(): Offset? {
        if (activePointers.isEmpty()) return null
        val sum = activePointers.values.reduce { acc, offset -> acc + offset }
        return sum / activePointers.size.toFloat()
    }

    private fun finishGesture() {
        val pointerCount = maxPointers
        val pan = totalPan
        when (val gesture = gestureEngine.gestureFor(pointerCount, pan, dragLockEnabled)) {
            TrackpadGestureEvent.LeftClick -> {
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (SystemClock.uptimeMillis() - lastTapUpTimeMs <= doubleTapTimeoutMillis.coerceIn(350, 900)) {
                    lastTapUpTimeMs = 0L
                    onDoubleTap()
                    resetGesture()
                    return
                }
                lastTapUpTimeMs = SystemClock.uptimeMillis()
                tapDragArmedUntil = SystemClock.uptimeMillis() + TAP_DRAG_ARM_MS
                onLeftClick()
            }
            TrackpadGestureEvent.RightClick -> {
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onRightClick()
            }
            is TrackpadGestureEvent.Command -> {
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCommand(gesture.command)
            }
            TrackpadGestureEvent.None -> Unit
        }
        resetGesture()
    }

    private fun resetGesture() {
        removeCallbacks(longPressRunnable)
        activePointers.clear()
        maxPointers = 0
        totalPan = Offset.Zero
        lastCentroid = null
        lastPointerDelta = Offset.Zero
        pointerVelocity = Offset.Zero
        lastPointerMoveTimeMs = 0L
                lastHoverPosition = null
                leftButtonHeld = false
        scrollZonePointerId = null
        lastScrollZoneY = 0f
        lastScrollZoneHapticY = 0f
    }

    private fun startLeftDrag(@Suppress("UNUSED_PARAMETER") label: String) {
        if (leftButtonHeld) return
        leftButtonHeld = true
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onPress(1)
    }

    private fun isInScrollZone(x: Float): Boolean =
        isTrackpadScrollZone(
            x = x,
            width = width,
            railSide = railSide,
            widthFraction = SCROLL_ZONE_WIDTH_FRACTION,
            minWidthPx = SCROLL_ZONE_MIN_WIDTH_PX,
        )

    private fun smartPointerDelta(delta: Offset, eventTimeMs: Long, dragging: Boolean, stylus: Boolean): Offset {
        val filtered = Offset(
            x = if (abs(delta.x) < POINTER_DEADZONE_PX) 0f else delta.x,
            y = if (abs(delta.y) < POINTER_DEADZONE_PX) 0f else delta.y,
        )
        val elapsedMs = if (lastPointerMoveTimeMs == 0L) {
            16f
        } else {
            (eventTimeMs - lastPointerMoveTimeMs).coerceIn(8L, 40L).toFloat()
        }
        lastPointerMoveTimeMs = eventTimeMs
        val normalizedVelocity = filtered * (16f / elapsedMs)
        pointerVelocity = Offset(
            x = (pointerVelocity.x * 0.72f) + (normalizedVelocity.x * 0.28f),
            y = (pointerVelocity.y * 0.72f) + (normalizedVelocity.y * 0.28f),
        )
        val speed = pointerVelocity.getDistance()
        val gain = trackpadPointerGain(
            speed = speed,
            dragging = dragging,
            stylus = stylus,
            acceleration = acceleration,
        )
        val lead = if (!dragging && !stylus && speed > 3.0f) {
            Offset(
                x = pointerVelocity.x.coerceIn(-6f, 6f) * 0.08f,
                y = pointerVelocity.y.coerceIn(-6f, 6f) * 0.08f,
            )
        } else {
            Offset.Zero
        }
        val predicted = (filtered * gain) + lead
        val smoothed = Offset(
            x = (predicted.x * 0.76f) + (lastPointerDelta.x * 0.24f),
            y = (predicted.y * 0.76f) + (lastPointerDelta.y * 0.24f),
        )
        lastPointerDelta = smoothed
        return Offset(
            x = if (abs(smoothed.x) < POINTER_DEADZONE_PX) 0f else smoothed.x,
            y = if (abs(smoothed.y) < POINTER_DEADZONE_PX) 0f else smoothed.y,
        )
    }

    private companion object {
        const val TAP_DRAG_ARM_MS = 700L
        const val DRAG_HOLD_TIMEOUT_MS = 220L
        const val POINTER_DEADZONE_PX = 0.35f
        const val STYLUS_HOVER_GAIN = 0.72f
        const val SCROLL_ZONE_WIDTH_FRACTION = 0.12f
        const val SCROLL_ZONE_MIN_WIDTH_PX = 72f
        const val SCROLL_ZONE_DEADZONE_PX = 1.5f
        const val SCROLL_ZONE_DIVISOR = 5.5f
        const val SCROLL_ZONE_HAPTIC_STEP_PX = 64f
    }
}

private fun MotionEvent.isStylusEvent(): Boolean {
    for (index in 0 until pointerCount) {
        val toolType = getToolType(index)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            return true
        }
    }
    return false
}

private fun Offset.rotated(rotation: TrackpadRotation): Offset =
    when (rotation) {
        TrackpadRotation.Deg0 -> this
        TrackpadRotation.Deg90 -> Offset(-y, x)
        TrackpadRotation.Deg180 -> Offset(-x, -y)
        TrackpadRotation.Deg270 -> Offset(y, -x)
    }

@Composable
private fun AirTouchSurface(
    enabled: Boolean,
    cursor: Offset,
    calibrationPoints: List<AirTouchPoint>,
    remoteSdkAvailable: Boolean,
    onConfirmPoint: () -> Unit,
    onClearPoints: () -> Unit,
    onRecenter: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val pointColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.secondary
    val cursorColor = MaterialTheme.colorScheme.tertiary
    val nextTarget = AIR_TOUCH_TARGETS[calibrationPoints.size % AIR_TOUCH_TARGETS.size]
    Box(
        modifier = modifier
            .heightIn(min = 280.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.background),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.matchParentSize())
        Canvas(modifier = Modifier.matchParentSize().padding(22.dp)) {
            val left = size.width * 0.08f
            val top = size.height * 0.12f
            val right = size.width * 0.92f
            val bottom = size.height * 0.76f
            drawLine(outlineColor, Offset(left, top), Offset(right, top), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(outlineColor, Offset(right, top), Offset(right, bottom), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(outlineColor, Offset(right, bottom), Offset(left, bottom), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(outlineColor, Offset(left, bottom), Offset(left, top), strokeWidth = 4f, cap = StrokeCap.Round)
            val nextTargetPoint = nextTarget.toAirTouchCanvasPoint(left, top, right, bottom)
            drawCircle(targetColor.copy(alpha = 0.22f), radius = 21f, center = nextTargetPoint)
            drawCircle(targetColor, radius = 8f, center = nextTargetPoint)
            calibrationPoints.forEach { point ->
                val targetMapped = point.target.toAirTouchCanvasPoint(left, top, right, bottom)
                val observedMapped = point.cursor.toAirTouchCanvasPoint(left, top, right, bottom)
                drawLine(
                    color = pointColor.copy(alpha = 0.28f),
                    start = targetMapped,
                    end = observedMapped,
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round,
                )
                drawCircle(pointColor.copy(alpha = 0.18f), radius = 13f, center = observedMapped)
                drawCircle(pointColor, radius = 5f, center = observedMapped)
            }
            val cursorPoint = cursor.toAirTouchCanvasPoint(left, top, right, bottom)
            drawLine(
                cursorColor,
                Offset(cursorPoint.x - 14f, cursorPoint.y),
                Offset(cursorPoint.x + 14f, cursorPoint.y),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawLine(
                cursorColor,
                Offset(cursorPoint.x, cursorPoint.y - 14f),
                Offset(cursorPoint.x, cursorPoint.y + 14f),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
        CodecksPanel(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Air Touch", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Samples ${calibrationPoints.size}  Next ${nextTarget.label}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    if (remoteSdkAvailable) "Raw SDK ready" else "S Pen button confirms",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DeckActionButton(
                    label = "Fallback confirm",
                    onClick = onConfirmPoint,
                    enabled = enabled,
                    modifier = Modifier.weight(1.35f).height(52.dp),
                )
                DeckActionButton(
                    label = "Recenter",
                    onClick = onRecenter,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                DeckActionButton(
                    label = "Clear",
                    onClick = onClearPoints,
                    enabled = calibrationPoints.isNotEmpty(),
                    modifier = Modifier.weight(0.82f).height(52.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DeckActionButton(
                    label = "Click",
                    onClick = onLeftClick,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                DeckActionButton(
                    label = "Right click",
                    onClick = onRightClick,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DeckActionButton(label = "Left", onClick = { onMove(-AIR_TOUCH_NUDGE, 0f) }, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp))
                DeckActionButton(label = "Up", onClick = { onMove(0f, -AIR_TOUCH_NUDGE) }, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp))
                DeckActionButton(label = "Down", onClick = { onMove(0f, AIR_TOUCH_NUDGE) }, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp))
                DeckActionButton(label = "Right", onClick = { onMove(AIR_TOUCH_NUDGE, 0f) }, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp))
            }
        }
    }
}

private fun Offset.toAirTouchCanvasPoint(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Offset {
    val normalizedX = ((x / AIR_TOUCH_CURSOR_RANGE) + 0.5f).coerceIn(0f, 1f)
    val normalizedY = ((y / AIR_TOUCH_CURSOR_RANGE) + 0.5f).coerceIn(0f, 1f)
    return Offset(
        x = left + ((right - left) * normalizedX),
        y = top + ((bottom - top) * normalizedY),
    )
}

@Composable
private fun AirMouseSurface(
    enabled: Boolean,
    latestGyroSample: Offset,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 280.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.background),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.matchParentSize())
        Icon(
            Icons.Outlined.ScreenRotation,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            if (enabled) "Tilt phone to move pointer" else "Connect Bluetooth to use air mouse",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center).offset { IntOffset(0, 32) },
        )
        Text(
            "x ${"%.2f".format(latestGyroSample.x)}  y ${"%.2f".format(latestGyroSample.y)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun AirMouseEffect(
    enabled: Boolean,
    context: Context,
    lifecycle: Lifecycle,
    calibration: Offset,
    sensitivity: Float,
    onSample: (Offset) -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    DisposableEffect(enabled, context, lifecycle, calibration, sensitivity) {
        if (!enabled) {
            onDispose {}
        } else {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            if (gyroscope == null) {
                onDispose {}
            } else {
                val listener = object : SensorEventListener {
                    private var filtered = Offset.Zero

                    override fun onSensorChanged(event: SensorEvent) {
                        val sample = Offset(event.values[1], event.values[0])
                        onSample(sample)
                        val adjusted = sample - calibration
                        filtered = Offset(
                            x = (filtered.x * 0.82f) + (adjusted.x * 0.18f),
                            y = (filtered.y * 0.82f) + (adjusted.y * 0.18f),
                        )
                        val dx = (filtered.x * 12f * sensitivity).coerceIn(-14f, 14f)
                        val dy = (filtered.y * 12f * sensitivity).coerceIn(-14f, 14f)
                        if (kotlin.math.abs(dx) > 0.18f || kotlin.math.abs(dy) > 0.18f) {
                            onMove(dx, dy)
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> manager.registerListener(
                            listener,
                            gyroscope,
                            SensorManager.SENSOR_DELAY_GAME,
                        )
                        Lifecycle.Event.ON_PAUSE,
                        Lifecycle.Event.ON_STOP -> manager.unregisterListener(listener)
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
                }
                onDispose {
                    lifecycle.removeObserver(observer)
                    manager.unregisterListener(listener)
                }
            }
        }
    }
}

@Composable
private fun BackTapEffect(
    enabled: Boolean,
    context: Context,
    lifecycle: Lifecycle,
    onBackTap: () -> Unit,
) {
    DisposableEffect(enabled, context, lifecycle, onBackTap) {
        if (!enabled) {
            onDispose {}
        } else {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer == null) {
                onDispose {}
            } else {
                val listener = object : SensorEventListener {
                    private val gravity = FloatArray(3)
                    private var lastLinearZ = 0f
                    private var tapCount = 0
                    private var lastCandidateAt = 0L
                    private var lastTriggerAt = 0L

                    override fun onSensorChanged(event: SensorEvent) {
                        for (index in 0..2) {
                            gravity[index] = (gravity[index] * 0.86f) + (event.values[index] * 0.14f)
                        }
                        val linearZ = event.values[2] - gravity[2]
                        val jerk = abs(linearZ - lastLinearZ)
                        lastLinearZ = linearZ
                        val now = SystemClock.uptimeMillis()
                        if (now - lastCandidateAt > 720L) tapCount = 0
                        if (abs(linearZ) > 10.5f && jerk > 4.0f && now - lastCandidateAt > 90L) {
                            if (now - lastTriggerAt < 760L) return
                            tapCount = if (now - lastCandidateAt < 520L) tapCount + 1 else 1
                            lastCandidateAt = now
                            if (tapCount >= 2) {
                                tapCount = 0
                                lastTriggerAt = now
                                onBackTap()
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> manager.registerListener(
                            listener,
                            accelerometer,
                            SensorManager.SENSOR_DELAY_GAME,
                        )
                        Lifecycle.Event.ON_PAUSE,
                        Lifecycle.Event.ON_STOP -> manager.unregisterListener(listener)
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
                }
                onDispose {
                    lifecycle.removeObserver(observer)
                    manager.unregisterListener(listener)
                }
            }
        }
    }
}

private fun hasGyroscope(context: Context): Boolean {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    return manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
}

private const val TRACE_TTL_MS = 620L
private const val TRACE_MAX_POINTS = 42
private const val TRACE_MIN_DISTANCE_PX = 4f
private val TrackpadClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val TrackpadDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")
private val TrackpadDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private const val AIR_TOUCH_CURSOR_RANGE = 1000f
private const val AIR_TOUCH_NUDGE = 28f

private enum class TrackpadQuickTray {
    Custom,
    Dynamic,
    Settings,
}

private enum class MouseInputMode(val label: String) {
    Trackpad("Trackpad"),
    AirMouse("Air"),
    AirTouch("Air Touch"),
}

private data class AirTouchTarget(
    val label: String,
    val position: Offset,
)

private data class AirTouchPoint(
    val index: Int,
    val target: AirTouchTarget,
    val cursor: Offset,
)

private enum class ScrollRailDirection(val label: String, val sign: Int) {
    Direct("Direct", 1),
    Inverted("Invert", -1),
}

private enum class ScrollRailOrientation {
    Vertical,
    Horizontal,
}

private val AIR_TOUCH_TARGETS = listOf(
    AirTouchTarget("top left", Offset(-500f, -500f)),
    AirTouchTarget("top right", Offset(500f, -500f)),
    AirTouchTarget("bottom right", Offset(500f, 500f)),
    AirTouchTarget("bottom left", Offset(-500f, 500f)),
    AirTouchTarget("center", Offset.Zero),
    AirTouchTarget("top edge", Offset(0f, -500f)),
    AirTouchTarget("right edge", Offset(500f, 0f)),
    AirTouchTarget("bottom edge", Offset(0f, 500f)),
    AirTouchTarget("left edge", Offset(-500f, 0f)),
    AirTouchTarget("upper left", Offset(-250f, -250f)),
    AirTouchTarget("upper right", Offset(250f, -250f)),
    AirTouchTarget("lower right", Offset(250f, 250f)),
    AirTouchTarget("lower left", Offset(-250f, 250f)),
)

private fun AirTouchTarget.toAirTouchCanvasPoint(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Offset = position.toAirTouchCanvasPoint(left, top, right, bottom)

private fun hasSpenRemoteSdk(): Boolean = runCatching {
    Class.forName("com.samsung.android.sdk.penremote.SpenRemote")
}.isSuccess

internal const val TrackpadTestTag = "mouse-trackpad"

private val OffsetSaver = androidx.compose.runtime.saveable.Saver<Offset, List<Float>>(
    save = { listOf(it.x, it.y) },
    restore = { values -> Offset(values[0], values[1]) },
)
