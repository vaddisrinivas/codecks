package io.codecks.ui.mouse.lockscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import io.codecks.core.trackpad.LockscreenDecision
import io.codecks.ui.mouse.RawTrackpadTouchLayer
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

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lockscreen Trackpad", style = MaterialTheme.typography.headlineSmall)
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
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
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
                        onActivity = {},
                        stylusEnabled = settings.sPenPrecisionEnabled,
                        onTrace = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { onClick(LEFT_BUTTON) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Left")
                    }
                    Button(
                        onClick = { onClick(RIGHT_BUTTON) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Right")
                    }
                    Button(
                        onClick = { onClick(MIDDLE_BUTTON) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Middle")
                    }
                }
            } else {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            "Unlock to connect",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            "Codecks does not start or reconnect Bluetooth HID from the lockscreen path.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Close")
                }
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Unlock for full Codecks")
                }
            }
        }
    }
}
