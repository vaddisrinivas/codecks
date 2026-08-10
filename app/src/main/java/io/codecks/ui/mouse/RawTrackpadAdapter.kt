package io.codecks.ui.mouse

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import io.codecks.HidCommand
import kotlin.math.abs
import kotlinx.coroutines.delay
import io.codecks.core.trackpad.TrackpadRailSide
import io.codecks.core.trackpad.TrackpadRotation
import io.codecks.core.trackpad.TrackpadGestureEngine
import io.codecks.core.trackpad.TrackpadMultiTapDetector
import io.codecks.core.trackpad.TrackpadPrecisionScrollAccumulator
import io.codecks.core.trackpad.TrackpadGestureEvent
import io.codecks.core.trackpad.TrackpadGestureSample
import io.codecks.core.trackpad.TrackpadMotionMode
import io.codecks.core.trackpad.isTrackpadScrollZone
import io.codecks.core.trackpad.shouldArmTapDrag
import io.codecks.core.trackpad.trackpadPointerGain
import io.codecks.core.trackpad.shouldTriggerTrackpadHold

@Composable
internal fun RawTrackpadTouchLayer(
    enabled: Boolean,
    sensitivity: Float,
    acceleration: Float,
    dragLockEnabled: Boolean,
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
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Float, Float) -> Unit,
    onCommand: (HidCommand) -> Unit,
    onPress: (Int) -> Unit,
    onReleaseButtons: () -> Unit,
    onDoubleTap: () -> Unit,
    onGestureSample: (TrackpadGestureSample) -> Unit,
    onTelemetry: (TrackpadTelemetrySample) -> Unit,
    onOpenDeckGesture: () -> Unit,
    onOpenControlsGesture: () -> Unit = {},
    onActivity: () -> Unit,
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
            view.scrollRailEnabled = scrollRailEnabled
            view.precisionScrollRailEnabled = precisionScrollRailEnabled
            view.precisionScrollSpeed = precisionScrollSpeed
            view.precisionScrollAcceleration = precisionScrollAcceleration
            view.twoFingerDoubleTapCommand = twoFingerDoubleTapCommand
            view.threeFingerDoubleTapCommand = threeFingerDoubleTapCommand
            view.threeFingerHoldCommand = threeFingerHoldCommand
            view.fourFingerDoubleTapCommand = fourFingerDoubleTapCommand
            view.fourFingerHoldCommand = fourFingerHoldCommand
            view.multiFingerHoldMillis = multiFingerHoldMillis.coerceIn(350, 1_000)
            view.railSide = railSide
            view.rotation = rotation
            view.hapticsEnabled = hapticsEnabled
            view.doubleTapTimeoutMillis = doubleTapTimeoutMillis.coerceIn(350, 900)
            view.tapMovementThresholdPx = tapMovementThresholdPx
            view.onMove = onMove
            view.onLeftClick = onLeftClick
            view.onRightClick = onRightClick
            view.onScroll = onScroll
            view.onCommand = onCommand
            view.onPress = onPress
            view.onReleaseButtons = onReleaseButtons
            view.onDoubleTap = onDoubleTap
            view.onGestureSample = onGestureSample
            view.onTelemetry = onTelemetry
            view.onOpenDeckGesture = onOpenDeckGesture
            view.onOpenControlsGesture = onOpenControlsGesture
            view.onActivity = onActivity
            view.stylusEnabled = stylusEnabled
            view.onTrace = onTrace
        },
    )
}

internal data class PointerTracePoint(
    val position: Offset,
    val timestampMillis: Long,
    val isStylus: Boolean,
)

internal class RawTrackpadView(context: Context) : View(context) {
    var enabledForInput: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                cancelTapDragSequence()
                cancelPendingMultiTap(invokeSingle = false)
                if (leftButtonHeld) onReleaseButtons()
                resetGesture()
            }
        }
    var sensitivity: Float = 1f
    var acceleration: Float = 1f
    var dragLockEnabled: Boolean = false
    var scrollRailEnabled: Boolean = true
    var precisionScrollRailEnabled: Boolean = true
    var precisionScrollSpeed: Float = 0.28f
    var precisionScrollAcceleration: Float = 0.25f
    var twoFingerDoubleTapCommand: HidCommand? = HidCommand.WindowSwitcher
    var threeFingerDoubleTapCommand: HidCommand? = HidCommand.AppSwitcher
    var threeFingerHoldCommand: HidCommand? = HidCommand.WindowSwitcher
    var fourFingerDoubleTapCommand: HidCommand? = HidCommand.MissionControl
    var fourFingerHoldCommand: HidCommand? = HidCommand.ShowDesktop
    var multiFingerHoldMillis: Int = 520
    var railSide: TrackpadRailSide = TrackpadRailSide.Right
    var rotation: TrackpadRotation = TrackpadRotation.Deg0
    var hapticsEnabled: Boolean = true
    var doubleTapTimeoutMillis: Int = 520
    var tapMovementThresholdPx: Float = TrackpadGestureEngine.DEFAULT_TAP_MOVEMENT_THRESHOLD_PX
    var onMove: (Float, Float) -> Unit = { _, _ -> }
    var onLeftClick: () -> Unit = {}
    var onRightClick: () -> Unit = {}
    var onScroll: (Float, Float) -> Unit = { _, _ -> }
    var onCommand: (HidCommand) -> Unit = {}
    var onPress: (Int) -> Unit = {}
    var onReleaseButtons: () -> Unit = {}
    var onDoubleTap: () -> Unit = {}
    var onGestureSample: (TrackpadGestureSample) -> Unit = {}
    var onTelemetry: (TrackpadTelemetrySample) -> Unit = {}
    var onOpenDeckGesture: () -> Unit = {}
    var onOpenControlsGesture: () -> Unit = {}
    var onActivity: () -> Unit = {}
    var stylusEnabled: Boolean = true
    var onTrace: (PointerTracePoint) -> Unit = {}

    private val activePointers = linkedMapOf<Int, Offset>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var maxPointers = 0
    private var totalPan = Offset.Zero
    private var gestureStartedAtMs = 0L
    private var lastCentroid: Offset? = null
    private var lastPointerDelta = Offset.Zero
    private var pointerVelocity = Offset.Zero
    private var lastPointerMoveTimeMs = 0L
    private var lastHoverPosition: Offset? = null
    private var leftButtonHeld = false
    private var lastTapUpTimeMs = 0L
    private var pendingTapUpTimeMs = 0L
    private var tapDragArmedUntil = 0L
    private var tapDragCandidate = false
    private var tapDragFirstClickDispatched = false
    private var scrollZonePointerId: Int? = null
    private var scrollZoneMode: TrackpadScrollZone? = null
    private var lastScrollZoneY = 0f
    private var lastScrollZoneHapticY = 0f
    private var scrollZoneFeedbackSent = false
    private val standardScrollAccumulator = TrackpadPrecisionScrollAccumulator()
    private val precisionScrollAccumulator = TrackpadPrecisionScrollAccumulator()
    private val gestureEngine = TrackpadGestureEngine()
    private val multiTapDetector = TrackpadMultiTapDetector()
    private var pendingMultiTapPointerCount = 0
    private var pendingMultiTapSingle: (() -> Unit)? = null
    private var pendingMultiTapSample: TrackpadGestureSample? = null
    private val pendingMultiTapRunnable = Runnable {
        if (hapticsEnabled && pendingMultiTapSingle != null) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        pendingMultiTapSingle?.invoke()
        pendingMultiTapSample?.let(onGestureSample)
        pendingMultiTapSingle = null
        pendingMultiTapSample = null
        pendingMultiTapPointerCount = 0
        multiTapDetector.reset()
    }
    private var pendingHoldPointerCount = 0
    private var multiFingerHoldTriggered = false
    private val multiFingerHoldRunnable = Runnable {
        val pointerCount = pendingHoldPointerCount
        val command = when (pointerCount) {
            3 -> threeFingerHoldCommand
            4 -> HidCommand.AppSwitcher
            5 -> HidCommand.AppSwitcher
            else -> null
        }
        if (!multiFingerHoldTriggered && enabledForInput && shouldTriggerTrackpadHold(
                pointerCount = pointerCount,
                activePointerCount = activePointers.size,
                panDistanceSquared = totalPan.getDistanceSquared(),
                movementThresholdPx = tapMovementThresholdPx,
                hasCommand = command != null,
            )
        ) {
            requireNotNull(command)
            multiFingerHoldTriggered = true
            cancelPendingMultiTap(invokeSingle = false)
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (pointerCount == 4) {
                onOpenDeckGesture()
            } else if (pointerCount == 5) {
                onOpenControlsGesture()
            } else {
                onCommand(command)
            }
            onGestureSample(
                TrackpadGestureSample(
                    maxPointers = pointerCount,
                    panDistancePx = totalPan.getDistance(),
                    durationMillis = SystemClock.uptimeMillis() - gestureStartedAtMs,
                    classification = when (pointerCount) {
                        4 -> "hold:Deck"
                        5 -> "hold:Controls"
                        else -> "hold:${command.name}"
                    },
                ),
            )
        }
    }
    private val pendingSingleTapRunnable = Runnable {
        if (enabledForInput && pendingTapUpTimeMs != 0L) {
            performClick()
        }
        pendingTapUpTimeMs = 0L
    }
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
        contentDescription =
            "Trackpad. One finger moves the Mac pointer. Tap for left click. " +
                "Tap again and drag, or hold and drag, to draw or move. Two-finger tap for right click."
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
            cancelTapDragSequence()
            cancelPendingMultiTap(invokeSingle = false)
            if (leftButtonHeld) onReleaseButtons()
            resetGesture()
            return false
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        onActivity()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                gestureStartedAtMs = event.eventTime
                addOrUpdatePointer(event, event.actionIndex)
                maxPointers = maxOf(maxPointers, activePointers.size)
                emitTelemetry(event.eventTime, Offset.Zero, 0L)
                lastCentroid = centroid()
                val zone = scrollZoneAt(event.x)
                if (zone != null) {
                    scrollZonePointerId = event.getPointerId(event.actionIndex)
                    scrollZoneMode = zone
                    lastScrollZoneY = event.y
                    lastScrollZoneHapticY = event.y
                    if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } else if (shouldArmTapDrag(
                        tapDragArmedUntilMs = tapDragArmedUntil,
                        eventTimeMs = event.eventTime,
                        dragLockEnabled = dragLockEnabled,
                    )
                ) {
                    // A second touch chooses a drag-or-double-click gesture.
                    // Do not let the delayed first click escape while it is held.
                    val firstClickStillPending = pendingTapUpTimeMs != 0L
                    cancelPendingTap()
                    tapDragCandidate = true
                    tapDragFirstClickDispatched = !firstClickStillPending
                    postDelayed(longPressRunnable, DRAG_HOLD_TIMEOUT_MS)
                } else {
                    postDelayed(longPressRunnable, DRAG_HOLD_TIMEOUT_MS)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelPendingTap()
                removeCallbacks(longPressRunnable)
                tapDragArmedUntil = 0L
                tapDragCandidate = false
                scrollZonePointerId = null
                scrollZoneMode = null
                addOrUpdatePointer(event, event.actionIndex)
                updateAllPointers(event)
                maxPointers = maxOf(maxPointers, activePointers.size, event.pointerCount)
                emitTelemetry(event.eventTime, Offset.Zero, 0L)
                lastCentroid = centroid()
                scheduleMultiFingerHold(event.pointerCount)
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
                            val scrollDelta = when (scrollZoneMode) {
                                TrackpadScrollZone.Precision -> precisionScrollDelta(deltaY)
                                TrackpadScrollZone.Standard -> standardScrollAccumulator.add(
                                    delta = deltaY,
                                    speed = 1f,
                                    acceleration = 0f,
                                    divisor = SCROLL_ZONE_DIVISOR,
                                )
                                null -> 0f
                            }
                            if (scrollDelta != 0f) {
                                val rotatedScroll = Offset(0f, scrollDelta).rotated(rotation)
                                val dispatchStart = SystemClock.uptimeMillis()
                                onScroll(rotatedScroll.x, rotatedScroll.y)
                                emitTelemetry(event.eventTime, Offset(0f, scrollDelta), SystemClock.uptimeMillis() - dispatchStart)
                                if (!scrollZoneFeedbackSent) {
                                    scrollZoneFeedbackSent = true
                                    onGestureSample(
                                        TrackpadGestureSample(
                                            maxPointers = 1,
                                            panDistancePx = abs(deltaY),
                                            durationMillis = event.eventTime - gestureStartedAtMs,
                                            classification = if (scrollZoneMode == TrackpadScrollZone.Precision) {
                                                "scroll:slow"
                                            } else {
                                                "scroll:fast"
                                            },
                                        ),
                                    )
                                }
                            }
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
                    if (tapDragCandidate &&
                        maxPointers == 1 &&
                        totalPan.getDistanceSquared() > touchSlop * touchSlop
                    ) {
                        tapDragCandidate = false
                        tapDragArmedUntil = 0L
                        startLeftDrag("TapDragMoveStart")
                    }
                    if (totalPan.getDistanceSquared() > tapMovementThresholdPx * tapMovementThresholdPx) {
                        removeCallbacks(multiFingerHoldRunnable)
                        pendingHoldPointerCount = 0
                    }
                    if (!leftButtonHeld &&
                        maxPointers == 1 &&
                        totalPan.getDistanceSquared() > tapMovementThresholdPx * tapMovementThresholdPx
                    ) {
                        cancelPendingTap()
                        removeCallbacks(longPressRunnable)
                    }
                    when (gestureEngine.motionFor(maxOf(activePointers.size, event.pointerCount))) {
                        TrackpadMotionMode.Pointer -> {
                            val smoothed = smartPointerDelta(delta, event.eventTime, leftButtonHeld, stylus)
                            if (smoothed != Offset.Zero) {
                                val rotated = smoothed.rotated(rotation)
                                val dispatchStart = SystemClock.uptimeMillis()
                                onMove(rotated.x * sensitivity, rotated.y * sensitivity)
                                emitTelemetry(event.eventTime, rotated, SystemClock.uptimeMillis() - dispatchStart)
                            }
                        }
                        TrackpadMotionMode.Scroll -> {
                            if (!gestureEngine.shouldReserveTwoFingerBrowserSwipe(totalPan)) {
                                val rotated = Offset(delta.x / 10f, delta.y / 10f).rotated(rotation)
                                val dispatchStart = SystemClock.uptimeMillis()
                                onScroll(rotated.x, rotated.y)
                                emitTelemetry(event.eventTime, rotated, SystemClock.uptimeMillis() - dispatchStart)
                            }
                        }
                        TrackpadMotionMode.ReservedGesture -> Unit
                    }
                }
                lastCentroid = nextCentroid
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                removeCallbacks(multiFingerHoldRunnable)
                pendingHoldPointerCount = 0
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
                removeCallbacks(multiFingerHoldRunnable)
                pendingHoldPointerCount = 0
                maxPointers = maxOf(maxPointers, activePointers.size, event.pointerCount)
                updateAllPointers(event, skipActionIndex = event.actionIndex)
                if (scrollZonePointerId != null) {
                    resetGesture()
                    return true
                }
                if (leftButtonHeld) {
                    if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onReleaseButtons()
                    resetGesture()
                    return true
                }
                if (multiFingerHoldTriggered) {
                    resetGesture()
                    return true
                }
                finishGesture(event.eventTime)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTapDragSequence()
                removeCallbacks(multiFingerHoldRunnable)
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
        onActivity()
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

    private fun finishGesture(finishedAtMillis: Long) {
        val pointerCount = maxPointers
        val pan = totalPan
        val decision = gestureEngine.classifyGesture(
            maxPointers = pointerCount,
            totalPan = pan,
            durationMillis = finishedAtMillis - gestureStartedAtMs,
            dragLockEnabled = dragLockEnabled,
            tapMovementThresholdPx = tapMovementThresholdPx,
        )
        val tapLike = pan.getDistanceSquared() <= tapMovementThresholdPx * tapMovementThresholdPx
        val durationMillis = decision.sample.durationMillis
        val holdCommand = when (pointerCount) {
            3 -> threeFingerHoldCommand
            4 -> HidCommand.AppSwitcher
            5 -> HidCommand.AppSwitcher
            else -> null
        }
        if (tapLike && holdCommand != null && durationMillis >= multiFingerHoldMillis.coerceIn(350, 1_000)) {
            cancelPendingMultiTap(invokeSingle = false)
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (pointerCount == 4) {
                onOpenDeckGesture()
                onGestureSample(decision.sample.copy(classification = "hold:Deck"))
            } else if (pointerCount == 5) {
                onOpenControlsGesture()
                onGestureSample(decision.sample.copy(classification = "hold:Controls"))
            } else {
                onCommand(holdCommand)
                onGestureSample(decision.sample.copy(classification = "hold:${holdCommand.name}"))
            }
            resetGesture()
            return
        }
        val doubleTapCommand = when {
            pointerCount == 2 && decision.event == TrackpadGestureEvent.RightClick -> twoFingerDoubleTapCommand
            pointerCount == 3 && tapLike -> threeFingerDoubleTapCommand
            pointerCount == 4 && tapLike -> fourFingerDoubleTapCommand
            else -> null
        }
        if (doubleTapCommand != null) {
            val singleAction: () -> Unit = when (val gesture = decision.event) {
                TrackpadGestureEvent.RightClick -> onRightClick
                is TrackpadGestureEvent.Command -> ({ onCommand(gesture.command) })
                else -> ({})
            }
            handleMultiTapCandidate(
                pointerCount = pointerCount,
                command = doubleTapCommand,
                singleAction = singleAction,
                sample = decision.sample,
                timestampMillis = finishedAtMillis,
            )
            resetGesture()
            return
        }
        onGestureSample(decision.sample)
        when (val gesture = decision.event) {
            TrackpadGestureEvent.LeftClick -> {
                if (tapDragCandidate) {
                    val firstClickWasAlreadyDispatched = tapDragFirstClickDispatched
                    cancelPendingTap()
                    lastTapUpTimeMs = 0L
                    tapDragArmedUntil = 0L
                    if (firstClickWasAlreadyDispatched) {
                        performClick()
                    } else {
                        onDoubleTap()
                    }
                    resetGesture()
                    return
                }
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val now = SystemClock.uptimeMillis()
                if (pendingTapUpTimeMs != 0L && now - pendingTapUpTimeMs <= doubleTapTimeoutMillis.coerceIn(350, 900)) {
                    removeCallbacks(pendingSingleTapRunnable)
                    pendingTapUpTimeMs = 0L
                    lastTapUpTimeMs = 0L
                    onDoubleTap()
                    resetGesture()
                    return
                }
                lastTapUpTimeMs = now
                pendingTapUpTimeMs = now
                tapDragArmedUntil = now + TAP_DRAG_ARM_MS
                removeCallbacks(pendingSingleTapRunnable)
                postDelayed(pendingSingleTapRunnable, SINGLE_TAP_DELAY_MS)
            }
            TrackpadGestureEvent.RightClick -> {
                cancelPendingTap()
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onRightClick()
            }
            is TrackpadGestureEvent.Command -> {
                cancelPendingTap()
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCommand(gesture.command)
            }
            TrackpadGestureEvent.None -> Unit
        }
        resetGesture()
    }

    private fun emitTelemetry(eventTimeMillis: Long, delta: Offset, dispatchMillis: Long) {
        onTelemetry(
            TrackpadTelemetrySample(
                timestampMillis = eventTimeMillis,
                pointerCount = activePointers.size.coerceAtLeast(maxPointers),
                deltaPx = delta.getDistance(),
                dispatchMillis = dispatchMillis,
            ),
        )
    }

    private fun resetGesture() {
        removeCallbacks(longPressRunnable)
        removeCallbacks(multiFingerHoldRunnable)
        activePointers.clear()
        maxPointers = 0
        totalPan = Offset.Zero
        gestureStartedAtMs = 0L
        lastCentroid = null
        lastPointerDelta = Offset.Zero
        pointerVelocity = Offset.Zero
        lastPointerMoveTimeMs = 0L
        lastHoverPosition = null
        leftButtonHeld = false
        tapDragCandidate = false
        tapDragFirstClickDispatched = false
        pendingHoldPointerCount = 0
        multiFingerHoldTriggered = false
        scrollZonePointerId = null
        scrollZoneMode = null
        lastScrollZoneY = 0f
        lastScrollZoneHapticY = 0f
        scrollZoneFeedbackSent = false
        standardScrollAccumulator.reset()
        precisionScrollAccumulator.reset()
    }

    private fun startLeftDrag(@Suppress("UNUSED_PARAMETER") label: String) {
        if (leftButtonHeld) return
        cancelPendingTap()
        tapDragCandidate = false
        tapDragFirstClickDispatched = false
        tapDragArmedUntil = 0L
        leftButtonHeld = true
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onPress(1)
    }

    private fun scrollZoneAt(x: Float): TrackpadScrollZone? {
        val standard = scrollRailEnabled && isTrackpadScrollZone(
            x = x,
            width = width,
            railSide = railSide,
            widthFraction = SCROLL_ZONE_WIDTH_FRACTION,
            minWidthPx = SCROLL_ZONE_MIN_WIDTH_PX,
        )
        if (standard) return TrackpadScrollZone.Standard
        val precision = precisionScrollRailEnabled && isTrackpadScrollZone(
            x = x,
            width = width,
            railSide = railSide.opposite(),
            widthFraction = SCROLL_ZONE_WIDTH_FRACTION,
            minWidthPx = SCROLL_ZONE_MIN_WIDTH_PX,
        )
        return if (precision) TrackpadScrollZone.Precision else null
    }

    private fun precisionScrollDelta(deltaY: Float): Float {
        return precisionScrollAccumulator.add(
            delta = deltaY,
            speed = precisionScrollSpeed,
            acceleration = precisionScrollAcceleration,
            divisor = SCROLL_ZONE_DIVISOR,
        )
    }

    private fun handleMultiTapCandidate(
        pointerCount: Int,
        command: HidCommand,
        singleAction: () -> Unit,
        sample: TrackpadGestureSample,
        timestampMillis: Long,
    ) {
        if (pendingMultiTapPointerCount != 0 && pendingMultiTapPointerCount != pointerCount) {
            cancelPendingMultiTap(invokeSingle = true)
        }
        if (multiTapDetector.register(pointerCount, timestampMillis, doubleTapTimeoutMillis)) {
            removeCallbacks(pendingMultiTapRunnable)
            pendingMultiTapSingle = null
            pendingMultiTapSample = null
            pendingMultiTapPointerCount = 0
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onCommand(command)
            onGestureSample(sample.copy(classification = "double_tap:${command.name}"))
        } else {
            pendingMultiTapPointerCount = pointerCount
            pendingMultiTapSingle = singleAction
            pendingMultiTapSample = sample
            removeCallbacks(pendingMultiTapRunnable)
            postDelayed(pendingMultiTapRunnable, doubleTapTimeoutMillis.toLong())
            onGestureSample(sample.copy(classification = "tap_waiting:$pointerCount"))
        }
    }

    private fun scheduleMultiFingerHold(pointerCount: Int) {
        removeCallbacks(multiFingerHoldRunnable)
        pendingHoldPointerCount = when (pointerCount) {
            3 -> if (threeFingerHoldCommand != null) 3 else 0
            4 -> 4
            5 -> 5
            else -> 0
        }
        if (pendingHoldPointerCount != 0) {
            postDelayed(multiFingerHoldRunnable, multiFingerHoldMillis.coerceIn(350, 1_000).toLong())
        }
    }

    private fun cancelPendingMultiTap(invokeSingle: Boolean) {
        removeCallbacks(pendingMultiTapRunnable)
        if (invokeSingle) pendingMultiTapSingle?.invoke()
        pendingMultiTapSingle = null
        pendingMultiTapSample = null
        pendingMultiTapPointerCount = 0
        multiTapDetector.reset()
    }

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
        const val SINGLE_TAP_DELAY_MS = 140L
        const val DRAG_HOLD_TIMEOUT_MS = 220L
        const val POINTER_DEADZONE_PX = 0.35f
        const val STYLUS_HOVER_GAIN = 0.72f
        const val SCROLL_ZONE_WIDTH_FRACTION = 0.12f
        const val SCROLL_ZONE_MIN_WIDTH_PX = 72f
        const val SCROLL_ZONE_DEADZONE_PX = 1.5f
        const val SCROLL_ZONE_DIVISOR = 5.5f
        const val SCROLL_ZONE_HAPTIC_STEP_PX = 64f
    }

    private fun cancelPendingTap() {
        removeCallbacks(pendingSingleTapRunnable)
        pendingTapUpTimeMs = 0L
    }

    private fun cancelTapDragSequence() {
        cancelPendingTap()
        lastTapUpTimeMs = 0L
        tapDragCandidate = false
        tapDragFirstClickDispatched = false
        tapDragArmedUntil = 0L
    }
}

private enum class TrackpadScrollZone {
    Standard,
    Precision,
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
