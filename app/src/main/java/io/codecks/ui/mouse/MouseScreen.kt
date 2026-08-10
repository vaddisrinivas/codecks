package io.codecks.ui.mouse

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.codecks.HidCommand
import io.codecks.HidState
import io.codecks.domain.DeckAction
import io.codecks.data.context.NotificationPreview
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.home.CustomActionRow
import io.codecks.ui.keyboard.HidHostHeader
import io.codecks.ui.theme.CodecksScopedTheme
import io.codecks.ui.theme.ThemeTarget
import kotlin.math.abs
import kotlin.math.roundToInt
import io.codecks.core.trackpad.TrackpadRailSide
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadGestureEngine
import io.codecks.core.trackpad.TrackpadGestureSample

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
    onTapCorrection: () -> Unit = {},
    onGestureDiagnostic: (TrackpadGestureSample) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onExitTrackpad: () -> Unit = {},
    sessionPinned: Boolean = false,
    onToggleSessionPin: () -> Unit = {},
    onOpenKeyboardSurface: () -> Unit = {},
    onOpenClipboardSurface: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sensitivity = settings.pointerSpeed
    val acceleration = settings.acceleration
    val naturalScroll = settings.naturalScroll
    val scrollScale = settings.scrollSpeed
    val dragLockEnabled = settings.dragLockEnabled
    val traceEnabled = settings.pointerTraceEnabled
    val quietModeEnabled = settings.quietModeEnabled
    val idleBlankTimeoutMillis = settings.idleBlankTimeoutMillis
    val scrollRailEnabled = settings.scrollRailEnabled
    val precisionScrollRailEnabled = settings.precisionScrollRailEnabled
    val verticalRailDirection = if (settings.scrollRailInverted) ScrollRailDirection.Inverted else ScrollRailDirection.Direct
    val railSide = settings.railSide
    val rotation = settings.rotation
    val hapticsEnabled = settings.hapticsEnabled
    val backgroundOpacity = settings.backgroundOpacity
    val clockStyle = settings.clockStyle
    val doubleTapTimeoutMillis = settings.doubleTapTimeoutMillis
    val tapMovementThresholdPx = settings.tapMovementThresholdPx
    val sPenPrecisionEnabled = settings.sPenPrecisionEnabled
    val backTapEnabled = settings.backTapEnabled && backTapAvailable
    val volumeKeysEnabled = settings.volumeKeysEnabled && volumeKeysAvailable
    var inputMode by rememberSaveable { mutableStateOf(MouseInputMode.Trackpad) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }
    var quickTray by rememberSaveable { mutableStateOf<TrackpadQuickTray?>(null) }
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
    BackHandler(enabled = inputMode == MouseInputMode.Trackpad || controlsOpen) {
        if (quickTray != null) {
            quickTray = null
        } else if (controlsOpen) {
            controlsOpen = false
        } else if (inputMode == MouseInputMode.Trackpad) {
            onExitTrackpad()
        }
    }
    CodecksScopedTheme(if (inputMode == MouseInputMode.Trackpad) ThemeTarget.Trackpad else ThemeTarget.Global) {
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
                    precisionScrollRailEnabled = precisionScrollRailEnabled,
                    precisionScrollSpeed = settings.precisionScrollSpeed,
                    precisionScrollAcceleration = settings.precisionScrollAcceleration,
                    twoFingerDoubleTapCommand = settings.twoFingerDoubleTapAction.command,
                    threeFingerDoubleTapCommand = settings.threeFingerDoubleTapAction.command,
                    threeFingerHoldCommand = settings.threeFingerHoldAction.command,
                    fourFingerDoubleTapCommand = settings.fourFingerDoubleTapAction.command,
                    fourFingerHoldCommand = settings.fourFingerHoldAction.command,
                    multiFingerHoldMillis = settings.multiFingerHoldMillis,
                    railSide = railSide,
                    rotation = rotation,
                    hapticsEnabled = hapticsEnabled,
                    doubleTapTimeoutMillis = doubleTapTimeoutMillis,
                    tapMovementThresholdPx = tapMovementThresholdPx,
                    backgroundOpacity = backgroundOpacity,
                    clockStyle = clockStyle,
                    phoneNotifications = phoneNotifications,
                    laptopNotifications = laptopNotifications,
                    phoneNotificationAccessReady = phoneNotificationAccessReady,
                    phoneNotificationLaneEnabled = phoneNotificationLaneEnabled && !quietModeEnabled,
                    quietModeEnabled = quietModeEnabled,
                    idleBlankTimeoutMillis = idleBlankTimeoutMillis,
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
                        } else {
                            onLeftClick()
                            onLeftClick()
                        }
                    },
                    onOpenDeckGesture = onExitTrackpad,
                    onOpenControlsGesture = {
                        controlsOpen = true
                        quickTray = TrackpadQuickTray.Settings
                    },
                    sensitivity = sensitivity,
                    acceleration = acceleration,
                    enabled = state.isConnected,
                    onTapCorrection = onTapCorrection,
                    onGestureDiagnostic = onGestureDiagnostic,
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
                val tray = quickTray ?: TrackpadQuickTray.Settings
                TrackpadExpandedBottomSheet(
                    selectedTray = tray,
                    dynamicEnabled = dynamicActions.isNotEmpty(),
                    sessionPinned = sessionPinned,
                    onCustom = { quickTray = TrackpadQuickTray.Custom },
                    onDynamic = { quickTray = TrackpadQuickTray.Dynamic },
                    onSettings = { quickTray = TrackpadQuickTray.Settings },
                    onToggleSessionPin = onToggleSessionPin,
                    onExit = onExitTrackpad,
                    onClose = {
                        controlsOpen = false
                        quickTray = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 18.dp,
                            end = if (railSide == TrackpadRailSide.Right) 42.dp else 18.dp,
                            bottom = 18.dp,
                        )
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f)
                        .widthIn(max = 520.dp),
                ) {
                    if (tray == TrackpadQuickTray.Settings) {
                        TrackpadSettingsTray(
                            pointerSpeed = sensitivity,
                            acceleration = acceleration,
                            scrollSpeed = scrollScale,
                            naturalScroll = naturalScroll,
                            scrollRailEnabled = scrollRailEnabled,
                            traceEnabled = traceEnabled,
                            quietModeEnabled = quietModeEnabled,
                            idleBlankTimeoutMillis = idleBlankTimeoutMillis,
                            hapticsEnabled = hapticsEnabled,
                            doubleTapTimeoutMillis = doubleTapTimeoutMillis,
                            tapMovementThresholdPx = tapMovementThresholdPx,
                            phoneNotificationAccessReady = phoneNotificationAccessReady,
                            phoneNotificationLaneEnabled = phoneNotificationLaneEnabled,
                            onPointerSpeedChange = { value -> onSettingsChange { it.copy(pointerSpeed = value) } },
                            onAccelerationChange = { value -> onSettingsChange { it.copy(acceleration = value) } },
                            onScrollSpeedChange = { value -> onSettingsChange { it.copy(scrollSpeed = value) } },
                            onNaturalScrollChange = { value -> onSettingsChange { it.copy(naturalScroll = value) } },
                            onScrollRailEnabledChange = { value -> onSettingsChange { it.copy(scrollRailEnabled = value) } },
                            onTraceEnabledChange = { value -> onSettingsChange { it.copy(pointerTraceEnabled = value) } },
                            onQuietModeEnabledChange = { value -> onSettingsChange { it.copy(quietModeEnabled = value) } },
                            onIdleBlankTimeoutChange = { value -> onSettingsChange { it.copy(idleBlankTimeoutMillis = value) } },
                            onHapticsEnabledChange = { value -> onSettingsChange { it.copy(hapticsEnabled = value) } },
                            onDoubleTapTimeoutChange = { value -> onSettingsChange { it.copy(doubleTapTimeoutMillis = value) } },
                            onTapMovementThresholdChange = { value -> onSettingsChange { it.copy(tapMovementThresholdPx = value) } },
                            onTapCorrectionReset = {
                                onSettingsChange {
                                    it.copy(
                                        tapCorrectionCount = 0,
                                        tapMovementThresholdPx = TrackpadGestureEngine.DEFAULT_TAP_MOVEMENT_THRESHOLD_PX,
                                        doubleTapTimeoutMillis = 620,
                                    )
                                }
                            },
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TrackpadQuickTrayPanel(
                            tray = tray,
                            customActions = customActions,
                            dynamicActions = dynamicActions,
                            customActionsReady = customActionsReady,
                            onCustomAction = onCustomAction,
                            selectedActionId = selectedActionId,
                            modifier = Modifier.fillMaxSize(),
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
        }
        }
    }
}
