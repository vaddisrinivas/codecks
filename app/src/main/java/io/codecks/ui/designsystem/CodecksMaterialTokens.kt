package io.codecks.ui.designsystem

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.core.design.CodecksHapticToken
import io.codecks.ui.theme.LocalCodecksSemanticColors

/** Semantic colors only. Theme/preset code remains the sole owner of raw palettes. */
@Immutable
data class CodecksSemanticColorTokens(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val content: Color,
    val contentMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val connected: Color,
    val selected: Color,
    val focus: Color,
    val outline: Color,
    val scrim: Color,
    val transparent: Color,
)

@Composable
fun codecksSemanticColorTokens(): CodecksSemanticColorTokens {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalCodecksSemanticColors.current
    return CodecksSemanticColorTokens(
        canvas = scheme.background,
        surface = scheme.surfaceContainerLow,
        surfaceRaised = scheme.surfaceContainerHigh,
        content = scheme.onSurface,
        contentMuted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        success = semantic.success,
        warning = semantic.warning,
        danger = scheme.error,
        connected = semantic.success,
        selected = scheme.primary,
        focus = scheme.secondary,
        outline = scheme.outlineVariant,
        scrim = scheme.scrim,
        transparent = Color.Transparent,
    )
}

/** Converts a user-authored RGB value; raw ARGB ownership stays inside the design system. */
fun codecksOpaqueColor(rgb: Int): Color = Color(rgb or (255 shl 24))
fun codecksArgbColor(argb: Long): Color = Color(argb.toInt())

@Immutable
data class CodecksMotionPolicy(val reducedMotion: Boolean) {
    fun duration(normalMillis: Int): Int = CodecksDesignTokens.Motion.duration(normalMillis, reducedMotion)
    val allowsContinuousMotion: Boolean get() = !reducedMotion
}

val LocalCodecksMotionPolicy = staticCompositionLocalOf { CodecksMotionPolicy(reducedMotion = false) }

/** Observes Android's animator scale so reduced-motion changes apply without process recreation. */
@Composable
fun rememberCodecksMotionPolicy(): CodecksMotionPolicy {
    val context = LocalContext.current
    fun readReducedMotion(): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrElse { !ValueAnimator.areAnimatorsEnabled() }
    var reducedMotion by remember(context) { mutableStateOf(readReducedMotion()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = readReducedMotion()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return CodecksMotionPolicy(reducedMotion)
}

fun HapticFeedback.performCodecksHaptic(token: CodecksHapticToken) {
    performHapticFeedback(
        when (token) {
            CodecksHapticToken.Confirm -> HapticFeedbackType.TextHandleMove
            CodecksHapticToken.Reject,
            CodecksHapticToken.Boundary,
            CodecksHapticToken.LongPress,
            -> HapticFeedbackType.LongPress
        },
    )
}

val CodecksMaterialTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

val CodecksMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(CodecksDesignTokens.Shape.extraSmall),
    small = RoundedCornerShape(CodecksDesignTokens.Shape.small),
    medium = RoundedCornerShape(CodecksDesignTokens.Shape.medium),
    large = RoundedCornerShape(CodecksDesignTokens.Shape.large),
    extraLarge = RoundedCornerShape(CodecksDesignTokens.Shape.extraLarge),
)

/** Single semantic owner for every Deck tile treatment. Deck styles may choose layout, never palettes. */
@Immutable
data class CodecksDeckTileVisual(
    val container: Brush,
    val content: Color,
    val icon: Color,
    val iconContainer: Color,
    val border: Color,
    val borderWidth: Dp,
    val glow: Color,
    val glowAlpha: Float,
)

@Composable
fun codecksDeckTileVisual(
    state: DeckComponentState,
    pressed: Boolean,
    dangerous: Boolean,
    enabled: Boolean,
    requestedAccent: Color?,
): CodecksDeckTileVisual {
    val semantic = codecksSemanticColorTokens()
    val active = pressed || state == DeckComponentState.Selected ||
        state == DeckComponentState.Running || state == DeckComponentState.Succeeded
    val tone = when {
        dangerous || state == DeckComponentState.Failure -> semantic.danger
        state == DeckComponentState.Succeeded -> semantic.success
        requestedAccent != null -> requestedAccent
        else -> semantic.accent
    }
    val enabledAlpha = if (enabled) CodecksDesignTokens.Opacity.full else CodecksDesignTokens.Opacity.disabled
    return CodecksDeckTileVisual(
        container = Brush.verticalGradient(
            listOf(
                tone.copy(alpha = if (active) CodecksDesignTokens.Opacity.medium else CodecksDesignTokens.Opacity.low),
                semantic.surfaceRaised.copy(alpha = CodecksDesignTokens.Opacity.nearlyOpaque),
                semantic.canvas,
            ),
        ),
        content = semantic.content.copy(alpha = enabledAlpha),
        icon = tone.copy(alpha = enabledAlpha),
        iconContainer = tone.copy(
            alpha = if (active) CodecksDesignTokens.Opacity.low else CodecksDesignTokens.Opacity.selectedContainer,
        ),
        border = tone.copy(
            alpha = if (active) CodecksDesignTokens.Opacity.emphasized else CodecksDesignTokens.Opacity.outline,
        ),
        borderWidth = if (active) CodecksDesignTokens.Stroke.focus else CodecksDesignTokens.Stroke.hairline,
        glow = tone,
        glowAlpha = if (active) CodecksDesignTokens.Opacity.selectedContainer else CodecksDesignTokens.Opacity.barelyVisible,
    )
}
