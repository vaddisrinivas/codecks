package io.codecks.ui.mouse

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.codecks.HidCommand
import io.codecks.data.context.NotificationPreview
import kotlin.math.abs
import kotlinx.coroutines.delay
import io.codecks.core.trackpad.TrackpadClockStyle
import io.codecks.core.trackpad.TrackpadRailSide
import io.codecks.core.trackpad.TrackpadRotation
import io.codecks.core.trackpad.TrackpadGestureSample

@Composable
internal fun Trackpad(
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
    precisionScrollRailEnabled: Boolean,
    precisionScrollSpeed: Float,
    precisionScrollAcceleration: Float,
    twoFingerDoubleTapCommand: HidCommand?,
    threeFingerDoubleTapCommand: HidCommand?,
    threeFingerHoldCommand: HidCommand?,
    fourFingerDoubleTapCommand: HidCommand?,
    fourFingerHoldCommand: HidCommand?,
    multiFingerHoldMillis: Int,
    railSide: TrackpadRailSide,
    rotation: TrackpadRotation,
    hapticsEnabled: Boolean,
    doubleTapTimeoutMillis: Int,
    tapMovementThresholdPx: Float,
    backgroundOpacity: Float,
    clockStyle: TrackpadClockStyle,
    phoneNotifications: List<NotificationPreview>,
    laptopNotifications: List<NotificationPreview>,
    phoneNotificationAccessReady: Boolean,
    phoneNotificationLaneEnabled: Boolean,
    quietModeEnabled: Boolean,
    idleBlankTimeoutMillis: Int,
    controlsOpen: Boolean,
    sessionPinned: Boolean,
    onDoubleTap: () -> Unit,
    onOpenDeckGesture: () -> Unit,
    onOpenControlsGesture: () -> Unit = {},
    onTapCorrection: () -> Unit = {},
    onGestureDiagnostic: (TrackpadGestureSample) -> Unit = {},
    sensitivity: Float,
    acceleration: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tracePoints = remember { mutableStateListOf<PointerTracePoint>() }
    val traceColor = MaterialTheme.colorScheme.primary
    val stylusTraceColor = MaterialTheme.colorScheme.tertiary
    var lastActivityMillis by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    var idleBlanked by remember { mutableStateOf(false) }
    var latestTapSample by remember { mutableStateOf<TrackpadGestureSample?>(null) }
    var latestTapFeedbackVisible by remember { mutableStateOf(false) }
    fun recordTrackpadActivity() {
        lastActivityMillis = SystemClock.uptimeMillis()
        if (idleBlanked) idleBlanked = false
    }
    LaunchedEffect(idleBlankTimeoutMillis, lastActivityMillis) {
        val timeoutMillis = idleBlankTimeoutMillis.coerceIn(30_000, 600_000).toLong()
        idleBlanked = false
        delay(timeoutMillis)
        idleBlanked = true
    }
    LaunchedEffect(tracePoints.size) {
        while (tracePoints.isNotEmpty()) {
            delay(80L)
            val now = SystemClock.uptimeMillis()
            tracePoints.removeAll { now - it.timestampMillis > TRACE_TTL_MS }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (enabled) 0.82f else 0.48f),
            border = BorderStroke(
                1.dp,
                if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                },
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .testTag(TrackpadTestTag)
        ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge)) {
            RawTrackpadTouchLayer(
                enabled = enabled,
                sensitivity = sensitivity,
                acceleration = acceleration,
                dragLockEnabled = dragLockEnabled,
                scrollRailEnabled = scrollRailEnabled,
                precisionScrollRailEnabled = precisionScrollRailEnabled,
                precisionScrollSpeed = precisionScrollSpeed,
                precisionScrollAcceleration = precisionScrollAcceleration,
                twoFingerDoubleTapCommand = twoFingerDoubleTapCommand,
                threeFingerDoubleTapCommand = threeFingerDoubleTapCommand,
                threeFingerHoldCommand = threeFingerHoldCommand,
                fourFingerDoubleTapCommand = fourFingerDoubleTapCommand,
                fourFingerHoldCommand = fourFingerHoldCommand,
                multiFingerHoldMillis = multiFingerHoldMillis,
                railSide = railSide,
                onDoubleTap = onDoubleTap,
                onActivity = ::recordTrackpadActivity,
                rotation = rotation,
                hapticsEnabled = hapticsEnabled,
                doubleTapTimeoutMillis = doubleTapTimeoutMillis,
                tapMovementThresholdPx = tapMovementThresholdPx,
                onMove = onMove,
                onLeftClick = onLeftClick,
                onRightClick = onRightClick,
                onScroll = onScroll,
                onCommand = onCommand,
                onPress = onPress,
                onReleaseButtons = onReleaseButtons,
                onGestureSample = { sample ->
                    onGestureDiagnostic(sample)
                    if (sample.classification == "left_click" ||
                        sample.classification == "right_click" ||
                        sample.classification.startsWith("tap_waiting:") ||
                        sample.classification.startsWith("double_tap:") ||
                        sample.classification.startsWith("hold:") ||
                        sample.classification.startsWith("scroll:")
                    ) {
                        latestTapSample = sample
                        latestTapFeedbackVisible = true
                    }
                },
                onTelemetry = {},
                onOpenDeckGesture = onOpenDeckGesture,
                onOpenControlsGesture = onOpenControlsGesture,
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
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(1f),
            )
            if (scrollRailEnabled) {
                TrackpadRailMarker(label = "SCROLL", side = railSide, strong = true)
            }
            if (precisionScrollRailEnabled) {
                TrackpadRailMarker(label = "SLOW", side = railSide.opposite(), strong = false)
            }
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
            if (!controlsOpen && !idleBlanked) {
                TrackpadSurfaceDecoration(modifier = Modifier.matchParentSize())
            }
            if (!controlsOpen && (tracePoints.isEmpty() || !enabled || dragLockEnabled)) {
                TrackpadCenterHint(
                    enabled = enabled,
                    dragLockEnabled = dragLockEnabled,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (controlsOpen) {
                TrackpadGuardHint(
                    sessionPinned = sessionPinned,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 106.dp),
                )
            }
            if (latestTapFeedbackVisible && !controlsOpen) {
                LaunchedEffect(latestTapSample) {
                    delay(3_200L)
                    latestTapFeedbackVisible = false
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp, start = 18.dp, end = 18.dp)
                        .widthIn(max = 420.dp)
                        .zIndex(2f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = latestTapSample?.feedbackLabel() ?: "Gesture recognized",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (latestTapSample?.classification in setOf("left_click", "right_click")) {
                            TextButton(
                                onClick = {
                                    onTapCorrection()
                                    latestTapFeedbackVisible = false
                                },
                            ) {
                                Text("Wrong click")
                            }
                        }
                    }
                }
            }
            if (idleBlanked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.96f)),
                )
            }
        }
        }
    }
}

private fun TrackpadGestureSample.feedbackLabel(): String = when {
    classification == "left_click" -> "Left click"
    classification == "right_click" -> "Two-finger right click"
    classification.startsWith("tap_waiting:") ->
        "${classification.substringAfter(':')}-finger tap · tap again"
    classification.startsWith("scroll:") ->
        if (classification.endsWith("slow")) "Slow scroll" else "Fast scroll"
    classification.startsWith("double_tap:") ->
        "Double tap · ${classification.substringAfter(':').gestureCommandLabel()}"
    classification.startsWith("hold:") ->
        "Hold · ${classification.substringAfter(':').gestureCommandLabel()}"
    else -> diagnosticSummary()
}

private fun String.gestureCommandLabel(): String = when (this) {
    "Deck" -> "Deck"
    HidCommand.WindowSwitcher.name -> "same-app window"
    HidCommand.AppSwitcher.name -> "switch app"
    HidCommand.MissionControl.name -> "Mission Control"
    HidCommand.ShowDesktop.name -> "show desktop"
    HidCommand.Spotlight.name -> "Spotlight"
    HidCommand.MediaPlayPause.name -> "play / pause"
    HidCommand.ScreenshotArea.name -> "screenshot"
    else -> replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
}

internal data class TrackpadTelemetrySample(
    val timestampMillis: Long,
    val pointerCount: Int,
    val deltaPx: Float,
    val dispatchMillis: Long,
)

@Composable
private fun TrackpadCenterHint(
    enabled: Boolean,
    dragLockEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 28.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.08f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (dragLockEnabled) Icons.Outlined.Lock else Icons.Outlined.Mouse,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(
            text = when {
                !enabled -> "Connect a Mac"
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
                "Choose a paired Mac above, then use this surface as your Mac pointer."
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
                "Back returns to Deck"
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
private fun BoxScope.TrackpadRailMarker(
    label: String,
    side: TrackpadRailSide,
    strong: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (strong) 0.14f else 0.08f),
        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = if (strong) 0.78f else 0.62f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .align(if (side == TrackpadRailSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 5.dp)
            .width(22.dp)
            .height(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.rotate(-90f))
        }
    }
}

internal fun TrackpadRailSide.opposite(): TrackpadRailSide =
    if (this == TrackpadRailSide.Left) TrackpadRailSide.Right else TrackpadRailSide.Left
