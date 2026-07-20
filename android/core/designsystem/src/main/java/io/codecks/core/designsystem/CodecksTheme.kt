package io.codecks.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CodecksOledColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF64F4FF),
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF004F59),
    onPrimaryContainer = Color(0xFFB5F8FF),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFF563900),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = Color(0xFF7CFFB2),
    onTertiary = Color(0xFF002111),
    tertiaryContainer = Color(0xFF004B27),
    onTertiaryContainer = Color(0xFF9BFFC7),
    error = Color(0xFFFF5D7A),
    onError = Color(0xFF3F0012),
    errorContainer = Color(0xFF710026),
    onErrorContainer = Color(0xFFFFD9DF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8F8FA),
    surface = Color(0xFF050708),
    onSurface = Color(0xFFE8F8FA),
    surfaceVariant = Color(0xFF172125),
    onSurfaceVariant = Color(0xFFB8C8CC),
    outline = Color(0xFF4E6268),
    outlineVariant = Color(0xFF243238),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE8F8FA),
    inverseOnSurface = Color(0xFF101416),
    inversePrimary = Color(0xFF006973),
)

@Immutable
data class CodecksColors(
    val oledBlack: Color = Color(0xFF000000),
    val panel: Color = Color(0xFF050708),
    val panelHigh: Color = Color(0xFF0C1114),
    val keyTop: Color = Color(0xFF11191D),
    val keyBottom: Color = Color(0xFF06090B),
    val keyStroke: Color = Color(0xFF2C3E45),
    val cyan: Color = Color(0xFF64F4FF),
    val blue: Color = Color(0xFF5A8CFF),
    val green: Color = Color(0xFF7CFFB2),
    val amber: Color = Color(0xFFFFD166),
    val red: Color = Color(0xFFFF5D7A),
    val textPrimary: Color = Color(0xFFE8F8FA),
    val textSecondary: Color = Color(0xFF99ADB3),
    val disabled: Color = Color(0xFF536267),
)

@Immutable
data class CodecksSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

@Immutable
data class CodecksRadii(
    val key: CornerBasedShape = RoundedCornerShape(8.dp),
    val panel: CornerBasedShape = RoundedCornerShape(8.dp),
    val control: CornerBasedShape = RoundedCornerShape(6.dp),
)

@Immutable
data class CodecksElevation(
    val keyIdle: Dp = 1.dp,
    val keyActive: Dp = 6.dp,
    val keyDanger: Dp = 8.dp,
)

object CodecksTokens {
    val colors: CodecksColors
        @Composable get() = LocalCodecksColors.current

    val spacing: CodecksSpacing
        @Composable get() = LocalCodecksSpacing.current

    val radii: CodecksRadii
        @Composable get() = LocalCodecksRadii.current

    val elevation: CodecksElevation
        @Composable get() = LocalCodecksElevation.current
}

private val LocalCodecksColors = staticCompositionLocalOf { CodecksColors() }
private val LocalCodecksSpacing = staticCompositionLocalOf { CodecksSpacing() }
private val LocalCodecksRadii = staticCompositionLocalOf { CodecksRadii() }
private val LocalCodecksElevation = staticCompositionLocalOf { CodecksElevation() }

@Composable
fun CodecksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = CodecksColors()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalCodecksColors provides colors,
        LocalCodecksSpacing provides CodecksSpacing(),
        LocalCodecksRadii provides CodecksRadii(),
        LocalCodecksElevation provides CodecksElevation(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) CodecksOledColorScheme else CodecksOledColorScheme,
            typography = Typography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(8.dp),
                extraLarge = RoundedCornerShape(8.dp),
            ),
            content = content,
        )
    }
}

@Composable
fun CodecksAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CodecksTheme(darkTheme = darkTheme, content = content)
}
