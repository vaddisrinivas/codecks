package io.codecks.ui.mouse.lockscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.core.trackpad.LockscreenDecision
import io.codecks.ui.mouse.RawTrackpadTouchLayer
import io.codecks.ui.app.accessibilityTraversalOrder
import kotlin.math.roundToInt

private const val LEFT_BUTTON = 1
private const val RIGHT_BUTTON = 2
private const val MIDDLE_BUTTON = 4

@Composable
fun LockscreenTrackpadScreen(
    state: LockscreenTrackpadUiState,
    onMove: (Float, Float) -> Unit,
    onScroll: (vertical: Int, horizontal: Int) -> Unit,
    onClick: (Int) -> Unit,
    onPress: (Int) -> Unit,
    onReleaseButtons: () -> Unit,
    onUnlock: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val canUsePointer = state.decision == LockscreenDecision.AllowRestrictedPointer
    val scrollSign = if (settings.naturalScroll) -1 else 1
    val largeText = LocalDensity.current.fontScale >= 2f

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.lg),
            modifier = Modifier
                .fillMaxSize()
                .then(if (largeText) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(CodecksDesignTokens.Spacing.page),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
                modifier = Modifier.accessibilityTraversalOrder(0f),
            ) {
                Text("Lockscreen Trackpad", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(
                    if (canUsePointer) {
                        "Pointer-only mode. Codecks keeps keyboard, deck, settings, and SSH locked away."
                    } else {
                        "Unlock for full Codecks or to reconnect before using Trackpad."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (canUsePointer) {
                Surface(
                    tonalElevation = CodecksDesignTokens.Elevation.low,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .accessibilityTraversalOrder(1f)
                        .then(if (largeText) Modifier.heightIn(min = CodecksDesignTokens.Size.pointerSurfaceMinHeight) else Modifier.weight(1f))
                        .heightIn(min = CodecksDesignTokens.Size.pointerSurfaceMinHeight),
                ) {
                    RawTrackpadTouchLayer(
                        enabled = true,
                        sensitivity = settings.pointerSpeed,
                        acceleration = settings.acceleration,
                        dragLockEnabled = false,
                        scrollRailEnabled = settings.scrollRailEnabled,
                        precisionScrollRailEnabled = settings.precisionScrollRailEnabled,
                        precisionScrollSpeed = settings.precisionScrollSpeed,
                        precisionScrollAcceleration = settings.precisionScrollAcceleration,
                        twoFingerDoubleTapCommand = null,
                        threeFingerDoubleTapCommand = null,
                        threeFingerHoldCommand = null,
                        fourFingerDoubleTapCommand = null,
                        fourFingerHoldCommand = null,
                        multiFingerHoldMillis = settings.multiFingerHoldMillis,
                        railSide = settings.railSide,
                        rotation = settings.rotation,
                        hapticsEnabled = settings.hapticsEnabled,
                        doubleTapTimeoutMillis = settings.doubleTapTimeoutMillis,
                        tapMovementThresholdPx = settings.tapMovementThresholdPx,
                        onMove = onMove,
                        onLeftClick = { onClick(LEFT_BUTTON) },
                        onRightClick = { onClick(RIGHT_BUTTON) },
                        onScroll = { horizontal, vertical ->
                            onScroll(
                                (vertical * scrollSign * settings.scrollSpeed).roundToInt(),
                                (horizontal * scrollSign * settings.scrollSpeed).roundToInt(),
                            )
                        },
                        onCommand = {},
                        onPress = onPress,
                        onReleaseButtons = onReleaseButtons,
                        onDoubleTap = { onClick(LEFT_BUTTON); onClick(LEFT_BUTTON) },
                        onGestureSample = {},
                        onTelemetry = {},
                        onOpenDeckGesture = {},
                        exposeDeckAccessibilityAction = false,
                        exposeControlsAccessibilityAction = false,
                        onActivity = {},
                        stylusEnabled = settings.sPenPrecisionEnabled,
                        onTrace = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (largeText) Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap)) {
                    Button(
                        onClick = { onClick(LEFT_BUTTON) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(2f),
                    ) {
                        Text("Left")
                    }
                    Button(
                        onClick = { onClick(RIGHT_BUTTON) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(3f),
                    ) {
                        Text("Right")
                    }
                    Button(
                        onClick = { onClick(MIDDLE_BUTTON) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(4f),
                    ) {
                        Text("Middle")
                    }
                } else Row(
                    horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = { onClick(LEFT_BUTTON) }, modifier = Modifier.weight(1f).heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(2f)) { Text("Left") }
                    Button(onClick = { onClick(RIGHT_BUTTON) }, modifier = Modifier.weight(1f).heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(3f)) { Text("Right") }
                    Button(onClick = { onClick(MIDDLE_BUTTON) }, modifier = Modifier.weight(1f).heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(4f)) { Text("Middle") }
                }
            } else {
                Surface(
                    tonalElevation = CodecksDesignTokens.Elevation.low,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (largeText) Modifier.heightIn(min = CodecksDesignTokens.Size.pointerSurfaceMinHeight) else Modifier.weight(1f)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CodecksDesignTokens.Spacing.xxl),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(CodecksDesignTokens.Size.iconLg),
                        )
                        Text(
                            "Unlock to connect",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = CodecksDesignTokens.Spacing.md),
                        )
                        Text(
                            "Codecks does not start or reconnect Bluetooth HID from the lockscreen path.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CodecksDesignTokens.Spacing.sm),
                        )
                    }
                }
            }

            if (largeText) Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap)) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(5f),
                ) {
                    Text("Close")
                }
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(6f),
                ) {
                    Text("Unlock for full Codecks")
                }
            } else Row(
                horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Grid.standardGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(5f)) { Text("Close") }
                Button(onClick = onUnlock, modifier = Modifier.weight(1f).heightIn(min = CodecksDesignTokens.Size.minTouchTarget).accessibilityTraversalOrder(6f)) { Text("Unlock for full Codecks") }
            }
        }
    }
}
