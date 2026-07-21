package io.codecks.core.designsystem.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlinx.coroutines.delay

enum class FancyDeckEffectKind {
    None,
    ConfettiBurst,
    EmojiRain,
    SparkTrail,
    ShockwavePulse,
    FireworkGrid,
    NeonSweep,
    DangerPulse,
    CalmGlow,
}

@Immutable
data class FancyDeckVisualTheme(
    val id: String,
    val name: String,
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val content: Color = Color(0xFFE8F8FA),
)

object FancyDeckThemes {
    val AuroraPixel = FancyDeckVisualTheme(
        id = "aurora-pixel",
        name = "Aurora Pixel",
        top = Color(0xFF081824),
        bottom = Color(0xFF010305),
        accent = Color(0xFF64F4FF),
    )

    val TerminalNeon = FancyDeckVisualTheme(
        id = "terminal-neon",
        name = "Terminal Neon",
        top = Color(0xFF06120A),
        bottom = Color(0xFF000000),
        accent = Color(0xFF7CFFB2),
    )

    val EmojiCarnival = FancyDeckVisualTheme(
        id = "emoji-carnival",
        name = "Emoji Carnival",
        top = Color(0xFF32111E),
        bottom = Color(0xFF080208),
        accent = Color(0xFFFF5D7A),
    )

    val StudioConsole = FancyDeckVisualTheme(
        id = "studio-console",
        name = "Studio Console",
        top = Color(0xFF221705),
        bottom = Color(0xFF080500),
        accent = Color(0xFFFFD166),
    )

    val all = listOf(AuroraPixel, TerminalNeon, EmojiCarnival, StudioConsole)

    fun byRole(role: String): FancyDeckVisualTheme = when (role) {
        "success" -> TerminalNeon
        "danger" -> EmojiCarnival.copy(accent = Color(0xFFFF5D7A))
        "voice" -> StudioConsole
        "arcade" -> EmojiCarnival
        else -> AuroraPixel
    }
}

@Composable
fun FancyDeckKey(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    emoji: String? = null,
    state: DeckKeyState = DeckKeyState.Idle,
    effect: FancyDeckEffectKind = FancyDeckEffectKind.ConfettiBurst,
    visualTheme: FancyDeckVisualTheme = FancyDeckThemes.AuroraPixel,
    minHeight: Dp = 96.dp,
    onClick: (() -> Unit)? = null,
) {
    val enabled = state != DeckKeyState.Disabled && onClick != null
    val shape = CodecksTokens.radii.key
    val interactionSource = remember { MutableInteractionSource() }
    var effectNonce by remember { mutableIntStateOf(0) }
    var showEffect by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (showEffect) 1.04f else 1f,
        animationSpec = androidx.compose.animation.core.tween(420, easing = FastOutSlowInEasing),
        label = "fancy-key-scale",
    )

    LaunchedEffect(effectNonce) {
        if (effectNonce > 0) {
            showEffect = true
            delay(950)
            showEffect = false
        }
    }

    Surface(
        modifier = modifier
            .sizeIn(minWidth = 64.dp, minHeight = minHeight)
            .semantics {
                role = Role.Button
                contentDescription = listOfNotNull(title, subtitle, emoji, state.name).joinToString(", ")
                if (!enabled) disabled()
            }
            .clip(shape)
            .drawBehind {
                drawCircle(
                    color = visualTheme.accent.copy(alpha = if (showEffect) 0.46f else 0.22f),
                    radius = size.minDimension * if (showEffect) 0.92f else 0.62f,
                    center = Offset(size.width * 0.5f, size.height * 0.12f),
                )
            }
            .border(BorderStroke(1.dp, visualTheme.accent.copy(alpha = 0.78f)), shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            ) {
                effectNonce += 1
                onClick?.invoke()
            },
        shape = shape,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(visualTheme.top, visualTheme.bottom)))
                .padding(10.dp),
        ) {
            Text(
                text = emoji ?: "◇",
                modifier = Modifier.align(Alignment.TopStart),
                color = visualTheme.accent,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(top = 42.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = visualTheme.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = visualTheme.content.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedVisibility(
                visible = showEffect,
                enter = scaleIn(initialScale = pressScale),
                exit = fadeOut(),
            ) {
                FancyDeckEffectOverlay(
                    effect = effect,
                    accent = visualTheme.accent,
                    emoji = emoji ?: "✨",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun FancyDeckEffectOverlay(
    effect: FancyDeckEffectKind,
    accent: Color,
    emoji: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val particles = when (effect) {
                FancyDeckEffectKind.None -> 0
                FancyDeckEffectKind.CalmGlow -> 5
                FancyDeckEffectKind.DangerPulse -> 8
                else -> 18
            }
            repeat(particles) { index ->
                val x = size.width * ((index * 37 % 100) / 100f)
                val y = size.height * ((index * 53 % 100) / 100f)
                val radius = 3.dp.toPx() + (index % 4) * 1.4.dp.toPx()
                val color = when (index % 4) {
                    0 -> accent
                    1 -> Color(0xFFFFD166)
                    2 -> Color(0xFF7CFFB2)
                    else -> Color(0xFFFF5D7A)
                }
                rotate(degrees = (index * 23).toFloat(), pivot = Offset(x, y)) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.74f),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(radius * 2.8f, radius),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius / 2f),
                    )
                }
            }
        }
        if (effect == FancyDeckEffectKind.EmojiRain || effect == FancyDeckEffectKind.FireworkGrid) {
            repeat(6) { index ->
                Text(
                    text = emoji,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (index * 18).dp, y = ((index * 11) % 58).dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FancyDeckKeyPreview() {
    CodecksAppTheme {
        Column(
            modifier = Modifier
                .background(CodecksTokens.colors.oledBlack)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FancyDeckKey(title = "Confetti", subtitle = "tap joy", emoji = "🎉", onClick = {})
            FancyDeckKey(title = "Voice", subtitle = "planned", emoji = "🎙", visualTheme = FancyDeckThemes.StudioConsole, onClick = {})
            FancyDeckKey(title = "Stop", subtitle = "guarded", emoji = "⛔", visualTheme = FancyDeckThemes.EmojiCarnival, effect = FancyDeckEffectKind.DangerPulse, onClick = {})
        }
    }
}

