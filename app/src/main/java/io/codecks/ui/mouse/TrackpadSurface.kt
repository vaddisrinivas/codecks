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
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.ui.designsystem.codecksSemanticColorTokens
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
    Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md), modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (enabled) CodecksDesignTokens.Opacity.high else CodecksDesignTokens.Opacity.medium),
            border = BorderStroke(
                CodecksDesignTokens.Stroke.hairline,
                if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = CodecksDesignTokens.Opacity.low)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = CodecksDesignTokens.Opacity.selectedContainer)
                },
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = CodecksDesignTokens.Size.pointerSurfaceMinHeight)
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
                    val alpha = (CodecksDesignTokens.Opacity.full - (age.toFloat() / TRACE_TTL_MS)).coerceIn(CodecksDesignTokens.Opacity.transparent, CodecksDesignTokens.TrackpadPaint.traceMaxAlpha)
                    val color = if (end.isStylus) stylusTraceColor else traceColor
                    drawLine(
                        color = color.copy(alpha = alpha),
                        start = start.position,
                        end = end.position,
                        strokeWidth = if (end.isStylus) CodecksDesignTokens.TrackpadPaint.stylusStrokePx else CodecksDesignTokens.TrackpadPaint.touchStrokePx,
                        cap = StrokeCap.Round,
                    )
                }
                tracePoints.lastOrNull()?.let { last ->
                    val age = now - last.timestampMillis
                    val alpha = (CodecksDesignTokens.Opacity.full - (age.toFloat() / TRACE_TTL_MS)).coerceIn(CodecksDesignTokens.Opacity.transparent, CodecksDesignTokens.TrackpadPaint.traceHeadMaxAlpha)
                    val color = if (last.isStylus) stylusTraceColor else traceColor
                    drawCircle(color.copy(alpha = alpha), radius = if (last.isStylus) CodecksDesignTokens.TrackpadPaint.stylusRadiusPx else CodecksDesignTokens.TrackpadPaint.touchRadiusPx, center = last.position)
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
                        .padding(start = CodecksDesignTokens.Spacing.lg, end = CodecksDesignTokens.Spacing.lg, bottom = CodecksDesignTokens.Size.Trackpad.guardBottom),
                )
            }
            if (latestTapFeedbackVisible && !controlsOpen) {
                LaunchedEffect(latestTapSample) {
                    delay(3_200L)
                    latestTapFeedbackVisible = false
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = CodecksDesignTokens.Opacity.nearlyOpaque),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(CodecksDesignTokens.Stroke.hairline, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = CodecksDesignTokens.Size.Trackpad.feedbackTop, start = CodecksDesignTokens.Spacing.xl, end = CodecksDesignTokens.Spacing.xl)
                        .widthIn(max = CodecksDesignTokens.Size.Trackpad.feedbackMaxWidth)
                        .zIndex(2f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.sm),
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
                        .background(codecksSemanticColorTokens().canvas.copy(alpha = CodecksDesignTokens.Opacity.nearlyOpaque)),
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
        verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
        modifier = modifier.padding(horizontal = CodecksDesignTokens.Size.Trackpad.centerHorizontal),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.08f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(CodecksDesignTokens.Size.Trackpad.centerIconContainer),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (dragLockEnabled) Icons.Outlined.Lock else Icons.Outlined.Mouse,
                    contentDescription = null,
                    modifier = Modifier.size(CodecksDesignTokens.Size.Trackpad.centerIcon),
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(CodecksDesignTokens.Stroke.hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = CodecksDesignTokens.Opacity.medium)),
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
            modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.sm),
        )
    }
}

@Composable
private fun TrackpadSurfaceDecoration(
    modifier: Modifier = Modifier,
) {
    val laneColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CodecksDesignTokens.TrackpadPaint.laneAlpha)
    val cornerColor = MaterialTheme.colorScheme.primary.copy(alpha = CodecksDesignTokens.TrackpadPaint.cornerAlpha)
    Canvas(modifier = modifier) {
        val edge = CodecksDesignTokens.Size.Trackpad.decorationEdge.toPx()
        val short = CodecksDesignTokens.Size.Trackpad.decorationShort.toPx()
        val stroke = CodecksDesignTokens.Stroke.hairline.toPx()
        listOf(size.height / 3f, size.height * 2f / 3f).forEach { y ->
            drawLine(laneColor, Offset(edge, y), Offset(size.width - edge, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        listOf(size.width / 3f, size.width * 2f / 3f).forEach { x ->
            drawLine(laneColor.copy(alpha = CodecksDesignTokens.TrackpadPaint.laneFineAlpha), Offset(x, edge), Offset(x, size.height - edge), strokeWidth = stroke, cap = StrokeCap.Round)
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
            horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xl),
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
                        start = if (controlsOpen && railSide == TrackpadRailSide.Left) CodecksDesignTokens.Size.Trackpad.controlInset else CodecksDesignTokens.Spacing.none,
                        end = if (controlsOpen && railSide == TrackpadRailSide.Right) CodecksDesignTokens.Size.Trackpad.controlInset else CodecksDesignTokens.Spacing.none,
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
                    bottom = if (controlsOpen) CodecksDesignTokens.Size.Trackpad.bottomControlInset else CodecksDesignTokens.Spacing.none,
                    end = if (controlsOpen && railSide == TrackpadRailSide.Right) CodecksDesignTokens.Size.Trackpad.sideControlInset else CodecksDesignTokens.Spacing.none,
                    start = if (controlsOpen && railSide == TrackpadRailSide.Left) CodecksDesignTokens.Size.Trackpad.sideControlInset else CodecksDesignTokens.Spacing.none,
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
        modifier = modifier.padding(horizontal = CodecksDesignTokens.Spacing.xxl, vertical = CodecksDesignTokens.Spacing.md),
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
                verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = CodecksDesignTokens.Spacing.md).fillMaxWidth(),
            ) {
                preview.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = CodecksDesignTokens.Opacity.subtle),
                        contentColor = textColor,
                        border = BorderStroke(CodecksDesignTokens.Stroke.hairline, MaterialTheme.colorScheme.primary.copy(alpha = CodecksDesignTokens.Opacity.selectedContainer)),
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
                            modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.sm),
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
    val railColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.disabled)
    val centerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.scrim)
    val activeColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(enabled, activeStep) {
        while (enabled && activeStep != 0) {
            onStep(activeStep)
            delay(42L)
        }
    }

    Surface(
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.low)
            activeOffset != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = CodecksDesignTokens.Opacity.scrim)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CodecksDesignTokens.Opacity.medium)
        },
        contentColor = if (activeOffset != null) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = CodecksDesignTokens.Stroke.hairline,
            color = if (activeOffset != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
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
        Canvas(modifier = Modifier.fillMaxSize().padding(CodecksDesignTokens.Spacing.xs)) {
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
            .padding(horizontal = CodecksDesignTokens.Spacing.xs)
            .width(CodecksDesignTokens.Size.Trackpad.railWidth)
            .height(CodecksDesignTokens.Size.Trackpad.railHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.rotate(-90f))
        }
    }
}

internal fun TrackpadRailSide.opposite(): TrackpadRailSide =
    if (this == TrackpadRailSide.Left) TrackpadRailSide.Right else TrackpadRailSide.Left
