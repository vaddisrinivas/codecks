package io.codex.s23deck.ui.designsystem

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val DeckButtonStyleSchemaVersion = 1

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

@Immutable
data class DeckButtonStyle(
    val id: String = "luminous-glass",
    val schemaVersion: Int = DeckButtonStyleSchemaVersion,
    val name: String = "Luminous Glass",
    val material: DeckButtonMaterialTokens = DeckButtonMaterialTokens(),
    val geometry: DeckButtonGeometryTokens = DeckButtonGeometryTokens(),
    val light: DeckButtonLightTokens = DeckButtonLightTokens(),
    val motion: DeckButtonMotionTokens = DeckButtonMotionTokens(),
) {
    fun clamped(): DeckButtonStyle = copy(
        schemaVersion = DeckButtonStyleSchemaVersion,
        material = material.clamped(),
        geometry = geometry.clamped(),
        light = light.clamped(),
        motion = motion.clamped(),
    )
}

@Immutable
data class DeckButtonMaterialTokens(
    val glassTint: Color = Color(0xFF101317),
    val glassFaceAlpha: Float = 0.86f,
    val frostDiffusion: Float = 0.78f,
    val concaveWellTint: Color = Color(0xFF1A1F25),
    val sideWallTint: Color = Color(0xFF050608),
    val rimHighlightAlpha: Float = 0.36f,
    val bottomOcclusionAlpha: Float = 0.34f,
    val glyph: Color = Color(0xFFF6F7F8),
) {
    fun clamped(): DeckButtonMaterialTokens = copy(
        glassFaceAlpha = glassFaceAlpha.coerceIn(0.45f, 0.92f),
        frostDiffusion = frostDiffusion.coerceIn(0f, 1f),
        rimHighlightAlpha = rimHighlightAlpha.coerceIn(0f, 0.85f),
        bottomOcclusionAlpha = bottomOcclusionAlpha.coerceIn(0f, 0.55f),
    )
}

@Immutable
data class DeckButtonGeometryTokens(
    val minKeySize: Dp = 64.dp,
    val maxKeySize: Dp = 104.dp,
    val cornerRadius: Dp = 20.dp,
    val concaveWellInset: Dp = 9.dp,
    val sideDepth: Dp = 7.dp,
    val rimWidth: Dp = 2.dp,
    val glyphSize: Dp = 25.dp,
) {
    fun clamped(): DeckButtonGeometryTokens = copy(
        minKeySize = minKeySize.coerceIn(48.dp, 80.dp),
        maxKeySize = maxKeySize.coerceIn(80.dp, 128.dp),
        cornerRadius = cornerRadius.coerceIn(12.dp, 24.dp),
        concaveWellInset = concaveWellInset.coerceIn(6.dp, 16.dp),
        sideDepth = sideDepth.coerceIn(2.dp, 8.dp),
        rimWidth = rimWidth.coerceIn(1.dp, 4.dp),
        glyphSize = glyphSize.coerceIn(22.dp, 30.dp),
    )
}

@Immutable
data class DeckButtonLightTokens(
    val idleUnderglowAlpha: Float = 0.16f,
    val idleApertureAlpha: Float = 0.10f,
    val idleInnerTransmissionAlpha: Float = 0.09f,
    val activeUnderglowAlpha: Float = 0.34f,
    val activeApertureAlpha: Float = 0.28f,
    val activeInnerBloomAlpha: Float = 0.18f,
    val glowIntensity: Float = 0.88f,
) {
    fun clamped(): DeckButtonLightTokens = copy(
        idleUnderglowAlpha = idleUnderglowAlpha.coerceIn(0f, 0.22f),
        idleApertureAlpha = idleApertureAlpha.coerceIn(0f, 0.28f),
        idleInnerTransmissionAlpha = idleInnerTransmissionAlpha.coerceIn(0f, 0.24f),
        activeUnderglowAlpha = activeUnderglowAlpha.coerceIn(0f, 0.54f),
        activeApertureAlpha = activeApertureAlpha.coerceIn(0f, 0.64f),
        activeInnerBloomAlpha = activeInnerBloomAlpha.coerceIn(0f, 0.38f),
        glowIntensity = glowIntensity.coerceIn(0f, 1.35f),
    )
}

@Immutable
data class DeckButtonMotionTokens(
    val pressTravel: Dp = 3.dp,
    val motionIntensity: Float = 1f,
) {
    fun clamped(): DeckButtonMotionTokens = copy(
        pressTravel = pressTravel.coerceIn(1.dp, 4.dp),
        motionIntensity = motionIntensity.coerceIn(0f, 1f),
    )
}

object DeckButtonStyles {
    val LuminousGlass = DeckButtonStyle()
}

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
    style: DeckButtonStyle = DeckButtonStyles.LuminousGlass,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val resolved = style.clamped()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val effectiveState = if (enabled) state else DeckKeyVisualState.Unavailable
    val lightColor = deckKeyStateColor(effectiveState, dangerous)
    val pressOffsetPx = with(density) {
        if (pressed) resolved.motion.pressTravel.toPx() * resolved.motion.motionIntensity else 0f
    }
    val faceTranslation by animateFloatAsState(
        targetValue = pressOffsetPx,
        animationSpec = tween(durationMillis = if (pressed) 80 else 160),
        label = "ckDeckKeyFaceTravel",
    )
    val runningAlpha = if (effectiveState == DeckKeyVisualState.Running && resolved.motion.motionIntensity > 0.01f) {
        val runningPulse = rememberInfiniteTransition(label = "ckDeckKeyRunning")
        val pulsingAlpha by runningPulse.animateFloat(
            initialValue = 0.72f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ckDeckKeyRunningAlpha",
        )
        pulsingAlpha
    } else {
        1f
    }
    val stateLightMultiplier = when (effectiveState) {
        DeckKeyVisualState.Running -> runningAlpha
        DeckKeyVisualState.Success -> 1.16f
        DeckKeyVisualState.Failure -> 1.12f
        DeckKeyVisualState.Waiting -> 1.04f
        DeckKeyVisualState.ToggledOn,
        DeckKeyVisualState.Selected -> 1.02f
        else -> 1f
    }
    val underglowAlpha = if (effectiveState == DeckKeyVisualState.Idle) {
        resolved.light.idleUnderglowAlpha
    } else {
        resolved.light.activeUnderglowAlpha
    } * resolved.light.glowIntensity * stateLightMultiplier
    val apertureAlpha = if (effectiveState == DeckKeyVisualState.Idle) {
        resolved.light.idleApertureAlpha
    } else {
        resolved.light.activeApertureAlpha
    } * resolved.light.glowIntensity * stateLightMultiplier
    val innerBloomAlpha = if (effectiveState == DeckKeyVisualState.Idle) {
        resolved.light.idleInnerTransmissionAlpha
    } else {
        resolved.light.activeInnerBloomAlpha
    } * resolved.light.glowIntensity * stateLightMultiplier
    val keyShape = RoundedCornerShape(resolved.geometry.cornerRadius)
    val wellShape = RoundedCornerShape((resolved.geometry.cornerRadius - 4.dp).coerceAtLeast(10.dp))
    val semanticState = keyStateDescription(effectiveState, dangerous)

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = semanticState
                role = Role.Button
                if (!enabled) disabled()
            }
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled && effectiveState != DeckKeyVisualState.Running,
                role = Role.Button,
                onClickLabel = "Run $label",
                onClick = onClick,
                onLongClickLabel = onLongClick?.let { "Open $label options" },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(RoundedCornerShape(resolved.geometry.cornerRadius + 8.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            lightColor.copy(alpha = underglowAlpha.coerceIn(0f, 0.60f)),
                            lightColor.copy(alpha = (underglowAlpha * 0.28f).coerceIn(0f, 0.24f)),
                            Color.Transparent,
                        ),
                    ),
                )
                .drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = size.minDimension * 0.62f,
                        center = Offset(size.width / 2f, size.height * 0.60f),
                    )
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .offset { IntOffset(0, (resolved.geometry.sideDepth.toPx() * 0.42f).roundToInt()) }
                .clip(keyShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            resolved.material.sideWallTint.copy(alpha = 0.96f),
                            Color.Black.copy(alpha = resolved.material.bottomOcclusionAlpha + 0.28f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .graphicsLayer {
                    translationY = faceTranslation
                    shadowElevation = if (pressed) 1f else 7f
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
                                lightColor.copy(alpha = apertureAlpha.coerceIn(0f, 0.70f)),
                                lightColor.copy(alpha = (apertureAlpha * 0.36f).coerceIn(0f, 0.34f)),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = resolved.material.rimHighlightAlpha),
                                resolved.material.glassTint.copy(alpha = resolved.material.glassFaceAlpha),
                                resolved.material.concaveWellTint.copy(alpha = resolved.material.frostDiffusion),
                                Color.Black.copy(alpha = resolved.material.bottomOcclusionAlpha),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(resolved.geometry.rimWidth, Color.White.copy(alpha = resolved.material.rimHighlightAlpha)),
                        keyShape,
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(resolved.geometry.concaveWellInset)
                    .clip(wellShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                lightColor.copy(alpha = innerBloomAlpha.coerceIn(0f, 0.48f)),
                                resolved.material.concaveWellTint.copy(alpha = 0.72f),
                                Color.Black.copy(alpha = 0.10f),
                            ),
                        ),
                    )
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), wellShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) resolved.material.glyph else resolved.material.glyph.copy(alpha = 0.34f),
                    modifier = Modifier.size(resolved.geometry.glyphSize),
                )
            }

            if (showLabel) {
                Text(
                    text = label,
                    color = resolved.material.glyph.copy(alpha = if (enabled) 0.72f else 0.34f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                )
            }

            DeckKeyStateMarker(
                state = effectiveState,
                dangerous = dangerous,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
            )
        }

        if (focused || effectiveState == DeckKeyVisualState.Selected || effectiveState == DeckKeyVisualState.Editing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .border(
                        BorderStroke(
                            width = if (focused) 2.dp else 1.dp,
                            color = if (focused) Color.White else lightColor.copy(alpha = 0.82f),
                        ),
                        RoundedCornerShape(resolved.geometry.cornerRadius + 4.dp),
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
        color = deckKeyStateColor(state, dangerous).copy(alpha = 0.92f),
        contentColor = Color(0xFF0A0A0A),
        shape = CircleShape,
        modifier = modifier.size(18.dp),
        shadowElevation = 0.dp,
    ) {
        Icon(
            imageVector = markerIcon,
            contentDescription = null,
            modifier = Modifier.padding(3.dp),
        )
    }
}

private fun deckKeyStateColor(
    state: DeckKeyVisualState,
    dangerous: Boolean,
): Color = when {
    dangerous || state == DeckKeyVisualState.Failure -> Color(0xFFFF7373)
    state == DeckKeyVisualState.Running || state == DeckKeyVisualState.Selected || state == DeckKeyVisualState.ToggledOn -> Color(0xFF9CD5FE)
    state == DeckKeyVisualState.Waiting || state == DeckKeyVisualState.DangerousArmed -> Color(0xFFFFD0B8)
    state == DeckKeyVisualState.Success -> Color(0xFF9BF396)
    state == DeckKeyVisualState.Unavailable || state == DeckKeyVisualState.DisabledByPolicy -> Color(0xFFC8CDD0)
    else -> Color.White
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
