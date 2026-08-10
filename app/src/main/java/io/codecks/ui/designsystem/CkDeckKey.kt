package io.codecks.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.core.design.CodecksHapticToken
import kotlin.math.roundToInt

enum class DeckKeyVisualState {
    Idle,
    Running,
    Waiting,
    Success,
    Failure,
    ToggledOn,
    Unavailable,
    DisabledByPolicy,
    DangerousArmed,
    Editing,
    Selected,
}

val CodecksFocusRingWidthSemantics = SemanticsPropertyKey<Float>("CodecksFocusRingWidthDp")
val CodecksFocusRingColorSemantics = SemanticsPropertyKey<Long>("CodecksFocusRingArgb")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CkDeckKey(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: DeckKeyVisualState = DeckKeyVisualState.Idle,
    enabled: Boolean = state !in setOf(DeckKeyVisualState.Unavailable, DeckKeyVisualState.DisabledByPolicy),
    dangerous: Boolean = false,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val semantic = codecksSemanticColorTokens()
    val effectiveState = if (enabled) state else DeckKeyVisualState.Unavailable
    val lightColor = deckKeyStateColor(effectiveState, dangerous)
    val motionPolicy = LocalCodecksMotionPolicy.current
    val pressOffsetPx = with(density) {
        if (pressed) CodecksDesignTokens.Spacing.xs.toPx() else CodecksDesignTokens.Opacity.transparent
    }
    val faceTranslation by animateFloatAsState(
        targetValue = pressOffsetPx,
        animationSpec = tween(
            durationMillis = motionPolicy.duration(
                if (pressed) CodecksDesignTokens.Motion.pressMillis else CodecksDesignTokens.Motion.stateChangeMillis,
            ),
        ),
        label = "ckDeckKeyFaceTravel",
    )
    val runningAlpha = if (
        effectiveState == DeckKeyVisualState.Running &&
        motionPolicy.allowsContinuousMotion
    ) {
        val runningPulse = rememberInfiniteTransition(label = "ckDeckKeyRunning")
        val pulsingAlpha by runningPulse.animateFloat(
            initialValue = CodecksDesignTokens.Opacity.emphasized,
            targetValue = CodecksDesignTokens.Opacity.full,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = CodecksDesignTokens.Motion.screenTransitionMillis * 7),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ckDeckKeyRunningAlpha",
        )
        pulsingAlpha
    } else {
        CodecksDesignTokens.Opacity.full
    }
    val stateLightMultiplier = when (effectiveState) {
        DeckKeyVisualState.Running -> runningAlpha
        DeckKeyVisualState.Success -> CodecksDesignTokens.Motion.restingScale
        DeckKeyVisualState.Failure -> CodecksDesignTokens.Motion.restingScale
        DeckKeyVisualState.Waiting -> CodecksDesignTokens.Motion.restingScale
        DeckKeyVisualState.ToggledOn,
        DeckKeyVisualState.Selected -> CodecksDesignTokens.Motion.restingScale
        else -> CodecksDesignTokens.Motion.restingScale
    }
    val underglowAlpha = if (effectiveState == DeckKeyVisualState.Idle) {
        CodecksDesignTokens.Opacity.soft
    } else {
        CodecksDesignTokens.Opacity.low
    } * stateLightMultiplier
    val apertureAlpha = if (effectiveState == DeckKeyVisualState.Idle) {
        CodecksDesignTokens.Opacity.stateLayer
    } else {
        CodecksDesignTokens.Opacity.low
    } * stateLightMultiplier
    val innerBloomAlpha = if (effectiveState == DeckKeyVisualState.Idle) {
        CodecksDesignTokens.Opacity.subtle
    } else {
        CodecksDesignTokens.Opacity.selectedContainer
    } * stateLightMultiplier
    val keyShape = MaterialTheme.shapes.large
    val wellShape = MaterialTheme.shapes.medium
    val semanticState = keyStateDescription(effectiveState, dangerous)
    val manageable = onLongClick != null
    val interactive = enabled || manageable

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (!enabled && manageable) {
                    "Run unavailable. Long press for options"
                } else {
                    semanticState
                }
                role = Role.Button
                this[CodecksFocusRingWidthSemantics] =
                    if (focused) CodecksDesignTokens.Focus.ringWidth.value else 0f
                this[CodecksFocusRingColorSemantics] = if (focused) {
                    semantic.focus.copy(alpha = CodecksDesignTokens.Focus.ringAlpha)
                        .toArgb().toLong() and 0xffffffffL
                } else {
                    0L
                }
                if (!interactive) disabled()
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = interactive, interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = interactive && effectiveState != DeckKeyVisualState.Running,
                role = Role.Button,
                onClickLabel = "Run $label",
                onClick = {
                    if (enabled) {
                        haptic.performCodecksHaptic(CodecksHapticToken.Confirm)
                        onClick()
                    }
                },
                onLongClickLabel = onLongClick?.let { "Open $label options" },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptic.performCodecksHaptic(CodecksHapticToken.LongPress)
                        action()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(CodecksDesignTokens.Stroke.hairline)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            lightColor.copy(alpha = underglowAlpha.coerceIn(CodecksDesignTokens.Opacity.transparent, CodecksDesignTokens.Opacity.scrim)),
                            lightColor.copy(alpha = (underglowAlpha * CodecksDesignTokens.Opacity.low).coerceIn(CodecksDesignTokens.Opacity.transparent, CodecksDesignTokens.Opacity.low)),
                            semantic.transparent,
                        ),
                    ),
                )
                .drawBehind {
                    drawCircle(
                        color = semantic.content.copy(alpha = CodecksDesignTokens.Opacity.barelyVisible),
                        radius = size.minDimension * 0.62f,
                        center = Offset(size.width / 2f, size.height * 0.60f),
                    )
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CodecksDesignTokens.Spacing.xs, vertical = CodecksDesignTokens.Spacing.sm)
                .offset { IntOffset(0, CodecksDesignTokens.Spacing.xs.toPx().roundToInt()) }
                .clip(keyShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            semantic.content.copy(alpha = CodecksDesignTokens.Opacity.stateLayer),
                            semantic.surface.copy(alpha = CodecksDesignTokens.Opacity.nearlyOpaque),
                            semantic.canvas.copy(alpha = CodecksDesignTokens.Opacity.scrim),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(CodecksDesignTokens.Spacing.xs)
                .graphicsLayer {
                    translationY = faceTranslation
                    shadowElevation = if (pressed) CodecksDesignTokens.Elevation.flat.toPx() else CodecksDesignTokens.Elevation.medium.toPx()
                    this.shape = keyShape
                    clip = false
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(keyShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                lightColor.copy(alpha = apertureAlpha.coerceIn(CodecksDesignTokens.Opacity.transparent, CodecksDesignTokens.Opacity.muted)),
                                lightColor.copy(alpha = (apertureAlpha * CodecksDesignTokens.Opacity.disabled).coerceIn(CodecksDesignTokens.Opacity.transparent, CodecksDesignTokens.Opacity.disabled)),
                                semantic.transparent,
                            ),
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                semantic.content.copy(alpha = CodecksDesignTokens.Opacity.disabled),
                                semantic.surfaceRaised.copy(alpha = CodecksDesignTokens.Opacity.high),
                                semantic.surface.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
                                semantic.canvas.copy(alpha = CodecksDesignTokens.Opacity.disabled),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(CodecksDesignTokens.Stroke.focus, semantic.content.copy(alpha = CodecksDesignTokens.Opacity.disabled)),
                        keyShape,
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CodecksDesignTokens.Spacing.sm)
                    .clip(wellShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                lightColor.copy(alpha = innerBloomAlpha.coerceIn(CodecksDesignTokens.Opacity.transparent, CodecksDesignTokens.Opacity.medium)),
                                semantic.surface.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
                                semantic.canvas.copy(alpha = CodecksDesignTokens.Opacity.stateLayer),
                            ),
                        ),
                    )
                    .border(BorderStroke(CodecksDesignTokens.Stroke.hairline, semantic.content.copy(alpha = CodecksDesignTokens.Opacity.soft)), wellShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) semantic.content else semantic.content.copy(alpha = CodecksDesignTokens.Opacity.disabled),
                    modifier = Modifier.size(CodecksDesignTokens.Size.deckGlyph),
                )
            }

            if (showLabel) {
                Text(
                    text = label,
                    color = semantic.content.copy(alpha = if (enabled) CodecksDesignTokens.Opacity.emphasized else CodecksDesignTokens.Opacity.disabled),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = CodecksDesignTokens.Spacing.sm, vertical = CodecksDesignTokens.Spacing.sm),
                )
            }

            DeckKeyStateMarker(
                state = effectiveState,
                dangerous = dangerous,
                modifier = Modifier.align(Alignment.TopEnd).padding(CodecksDesignTokens.Spacing.sm),
            )
        }

        if (focused || effectiveState == DeckKeyVisualState.Selected || effectiveState == DeckKeyVisualState.Editing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CodecksDesignTokens.Stroke.hairline)
                    .border(
                        BorderStroke(
                            width = if (focused) CodecksDesignTokens.Focus.ringWidth else CodecksDesignTokens.Stroke.hairline,
                            color = if (focused) semantic.focus.copy(alpha = CodecksDesignTokens.Focus.ringAlpha) else lightColor.copy(alpha = CodecksDesignTokens.Opacity.high),
                        ),
                        MaterialTheme.shapes.extraLarge,
                    ),
            )
        }
    }
}

@Composable
fun DeckKeyStateMarker(
    state: DeckKeyVisualState,
    dangerous: Boolean,
    modifier: Modifier = Modifier,
) {
    val markerIcon = when {
        state == DeckKeyVisualState.Success -> Icons.Outlined.CheckCircle
        state == DeckKeyVisualState.Failure -> Icons.Outlined.ErrorOutline
        state == DeckKeyVisualState.Waiting || state == DeckKeyVisualState.DangerousArmed -> Icons.Outlined.PriorityHigh
        else -> null
    }
    markerIcon ?: return
    Surface(
        color = deckKeyStateColor(state, dangerous).copy(alpha = CodecksDesignTokens.Focus.ringAlpha),
        contentColor = codecksSemanticColorTokens().canvas,
        shape = CircleShape,
        modifier = modifier.size(CodecksDesignTokens.Size.stateMarker),
        shadowElevation = CodecksDesignTokens.Elevation.flat,
    ) {
        Icon(
            imageVector = markerIcon,
            contentDescription = null,
            modifier = Modifier.padding(CodecksDesignTokens.Spacing.xxs),
        )
    }
}

@Composable
private fun deckKeyStateColor(
    state: DeckKeyVisualState,
    dangerous: Boolean,
): Color {
    val semantic = codecksSemanticColorTokens()
    return when {
        dangerous || state == DeckKeyVisualState.Failure -> semantic.danger
        state == DeckKeyVisualState.Running || state == DeckKeyVisualState.Selected || state == DeckKeyVisualState.ToggledOn -> semantic.selected
        state == DeckKeyVisualState.Waiting || state == DeckKeyVisualState.DangerousArmed -> semantic.warning
        state == DeckKeyVisualState.Success -> semantic.success
        state == DeckKeyVisualState.Unavailable || state == DeckKeyVisualState.DisabledByPolicy -> semantic.contentMuted
        else -> semantic.content
    }
}

private fun keyStateDescription(
    state: DeckKeyVisualState,
    dangerous: Boolean,
): String = when {
    dangerous && state == DeckKeyVisualState.Idle -> "Dangerous action, confirmation may be required"
    state == DeckKeyVisualState.Idle -> "Ready"
    state == DeckKeyVisualState.Running -> "Running"
    state == DeckKeyVisualState.Waiting -> "Waiting for confirmation"
    state == DeckKeyVisualState.Success -> "Succeeded"
    state == DeckKeyVisualState.Failure -> "Failed"
    state == DeckKeyVisualState.ToggledOn -> "On"
    state == DeckKeyVisualState.Unavailable -> "Unavailable"
    state == DeckKeyVisualState.DisabledByPolicy -> "Disabled by policy"
    state == DeckKeyVisualState.DangerousArmed -> "Dangerous action armed"
    state == DeckKeyVisualState.Editing -> "Editing"
    state == DeckKeyVisualState.Selected -> "Selected"
    else -> "Ready"
}
