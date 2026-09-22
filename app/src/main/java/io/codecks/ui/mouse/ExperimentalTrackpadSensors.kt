package io.codecks.ui.mouse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckActionButton
import kotlin.math.abs
import java.time.format.DateTimeFormatter

@Composable
internal fun AirTouchSurface(
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
internal fun AirMouseSurface(
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
internal fun AirMouseEffect(
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
internal fun BackTapEffect(
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

internal fun hasGyroscope(context: Context): Boolean {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    return manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
}

internal const val TRACE_TTL_MS = 620L
internal const val TRACE_MAX_POINTS = 42
internal const val TRACE_MIN_DISTANCE_PX = 4f
private val TrackpadClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val TrackpadDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")
private val TrackpadDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private const val AIR_TOUCH_CURSOR_RANGE = 1000f
private const val AIR_TOUCH_NUDGE = 28f

internal data class AirTouchTarget(
    val label: String,
    val position: Offset,
)

internal data class AirTouchPoint(
    val index: Int,
    val target: AirTouchTarget,
    val cursor: Offset,
)

internal val AIR_TOUCH_TARGETS = listOf(
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

internal fun hasSpenRemoteSdk(): Boolean = runCatching {
    Class.forName("com.samsung.android.sdk.penremote.SpenRemote")
}.isSuccess

internal const val TrackpadTestTag = "mouse-trackpad"

internal val OffsetSaver = androidx.compose.runtime.saveable.Saver<Offset, List<Float>>(
    save = { listOf(it.x, it.y) },
    restore = { values -> Offset(values[0], values[1]) },
)
