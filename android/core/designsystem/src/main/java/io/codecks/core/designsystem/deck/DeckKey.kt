package io.codecks.core.designsystem.deck

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.codecks.core.designsystem.CodecksAppTheme
import io.codecks.core.designsystem.CodecksTokens

enum class DeckKeySize(val gridSpan: Int) {
    OneU(1),
    ThreeU(3),
}

enum class DeckKeyState {
    Idle,
    Running,
    Success,
    Danger,
    Disabled,
}

@Immutable
data class CodecksDeckKeyColors(
    val glow: Color,
    val stroke: Color,
    val content: Color,
    val top: Color,
    val bottom: Color,
)

typealias CodecksDeckKeySize = DeckKeySize
typealias CodecksDeckKeyState = DeckKeyState

@Composable
fun LuminousDeckKey(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    size: DeckKeySize = DeckKeySize.OneU,
    state: DeckKeyState = DeckKeyState.Idle,
    minHeight: Dp = when (size) {
        DeckKeySize.OneU -> 72.dp
        DeckKeySize.ThreeU -> 88.dp
    },
    onClick: (() -> Unit)? = null,
) {
    val enabled = state != DeckKeyState.Disabled && onClick != null
    val colors = deckKeyColors(state)
    val shape = CodecksTokens.radii.key
    val interactionSource = remember { MutableInteractionSource() }
    val pulse = runningPulse(state)

    Surface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = maxOf(48.dp, minHeight))
            .semantics {
                role = Role.Button
                contentDescription = listOfNotNull(title, subtitle, state.name).joinToString(", ")
                if (!enabled) disabled()
            }
            .clip(shape)
            .drawBehind {
                val glowRadius = this.size.minDimension * (0.58f + pulse * 0.14f)
                drawCircle(
                    color = colors.glow.copy(alpha = 0.30f + pulse * 0.22f),
                    radius = glowRadius,
                    center = Offset(this.size.width * 0.50f, this.size.height * 0.16f),
                )
            }
            .border(BorderStroke(1.dp, colors.stroke.copy(alpha = 0.72f)), shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick?.invoke() },
            ),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = when (state) {
            DeckKeyState.Idle,
            DeckKeyState.Disabled -> CodecksTokens.elevation.keyIdle
            DeckKeyState.Running,
            DeckKeyState.Success -> CodecksTokens.elevation.keyActive
            DeckKeyState.Danger -> CodecksTokens.elevation.keyDanger
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .alpha(if (state == DeckKeyState.Disabled) 0.48f else 1f),
        ) {
            KeyGlyph(
                state = state,
                tint = colors.glow,
                modifier = Modifier.align(Alignment.TopStart),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = colors.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (size == DeckKeySize.ThreeU) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = colors.content.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun CodecksDeckKey(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    size: DeckKeySize = DeckKeySize.OneU,
    state: DeckKeyState = DeckKeyState.Idle,
    minHeight: Dp = when (size) {
        DeckKeySize.OneU -> 72.dp
        DeckKeySize.ThreeU -> 88.dp
    },
    onClick: (() -> Unit)? = null,
) {
    LuminousDeckKey(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        size = size,
        state = state,
        minHeight = minHeight,
        onClick = onClick,
    )
}

@Composable
private fun runningPulse(state: DeckKeyState): Float {
    if (state != DeckKeyState.Running) return 0f
    val transition = rememberInfiniteTransition(label = "deck-key-running")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "deck-key-pulse",
    )
    return pulse
}

@Composable
private fun deckKeyColors(state: DeckKeyState): CodecksDeckKeyColors {
    val tokens = CodecksTokens.colors
    val glow = when (state) {
        DeckKeyState.Idle -> tokens.cyan
        DeckKeyState.Running -> tokens.blue
        DeckKeyState.Success -> tokens.green
        DeckKeyState.Danger -> tokens.red
        DeckKeyState.Disabled -> tokens.disabled
    }

    return CodecksDeckKeyColors(
        glow = glow,
        stroke = when (state) {
            DeckKeyState.Disabled -> tokens.keyStroke.copy(alpha = 0.45f)
            else -> glow.copy(alpha = 0.86f)
        },
        content = if (state == DeckKeyState.Disabled) tokens.disabled else tokens.textPrimary,
        top = when (state) {
            DeckKeyState.Disabled -> Color(0xFF0A0D0F)
            else -> tokens.keyTop
        },
        bottom = when (state) {
            DeckKeyState.Danger -> Color(0xFF150208)
            DeckKeyState.Success -> Color(0xFF03130A)
            DeckKeyState.Running -> Color(0xFF020B17)
            else -> tokens.keyBottom
        },
    )
}

@Composable
private fun KeyGlyph(
    state: DeckKeyState,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (state) {
        DeckKeyState.Idle -> Icons.Rounded.PowerSettingsNew
        DeckKeyState.Running -> Icons.Rounded.Sync
        DeckKeyState.Success -> Icons.Rounded.CheckCircle
        DeckKeyState.Danger -> Icons.Rounded.Error
        DeckKeyState.Disabled -> Icons.Rounded.PlayArrow
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint.copy(alpha = if (state == DeckKeyState.Disabled) 0.48f else 0.96f),
        modifier = modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CodecksDeckKeyPreview() {
    CodecksAppTheme {
        Column(
            modifier = Modifier
                .background(CodecksTokens.colors.oledBlack)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LuminousDeckKey(title = "Run", subtitle = "Idle", state = DeckKeyState.Idle, onClick = {})
            LuminousDeckKey(title = "Sync", subtitle = "Running", state = DeckKeyState.Running, onClick = {})
            LuminousDeckKey(title = "Deploy", subtitle = "Success", state = DeckKeyState.Success, onClick = {})
            LuminousDeckKey(title = "Stop", subtitle = "Danger", state = DeckKeyState.Danger, onClick = {})
            LuminousDeckKey(title = "Disabled", subtitle = "Offline", state = DeckKeyState.Disabled)
        }
    }
}
