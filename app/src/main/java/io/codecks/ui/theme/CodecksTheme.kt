package io.codecks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DeckLightColors = lightColorScheme(
    primary = Color(0xFF087F3F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F5D8),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF466052),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8D9),
    onSecondaryContainer = Color(0xFF0A1F13),
    tertiary = Color(0xFF146C43),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB7F1CE),
    onTertiaryContainer = Color(0xFF002112),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF171C18),
    surface = Color(0xFFF7FAF7),
    onSurface = Color(0xFF171C18),
    surfaceVariant = Color(0xFFDDE5DE),
    onSurfaceVariant = Color(0xFF414943),
    outline = Color(0xFF717A73),
    outlineVariant = Color(0xFFC1C9C2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5F1),
    surfaceContainer = Color(0xFFEBF0EC),
    surfaceContainerHigh = Color(0xFFE5EBE6),
    surfaceContainerHighest = Color(0xFFDFE5E0),
)

private val DeckDarkColors = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF002112),
    primaryContainer = Color(0xFF0C3D25),
    onPrimaryContainer = Color(0xFFA7F3C5),
    secondary = Color(0xFFAFCBB8),
    onSecondary = Color(0xFF1B3525),
    secondaryContainer = Color(0xFF263D2E),
    onSecondaryContainer = Color(0xFFCBE8D3),
    tertiary = Color(0xFF77DEA1),
    onTertiary = Color(0xFF00391C),
    tertiaryContainer = Color(0xFF07512D),
    onTertiaryContainer = Color(0xFF9FF6BE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF080A09),
    onBackground = Color(0xFFF1F5F2),
    surface = Color(0xFF080A09),
    onSurface = Color(0xFFF1F5F2),
    surfaceVariant = Color(0xFF303832),
    onSurfaceVariant = Color(0xFFADB7B0),
    outline = Color(0xFF78847C),
    outlineVariant = Color(0xFF2A332D),
    surfaceContainerLowest = Color(0xFF050706),
    surfaceContainerLow = Color(0xFF0D110F),
    surfaceContainer = Color(0xFF111713),
    surfaceContainerHigh = Color(0xFF171D19),
    surfaceContainerHighest = Color(0xFF1C241E),
)

private val DeckOledColors = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF001F0F),
    primaryContainer = Color(0xFF0A3721),
    onPrimaryContainer = Color(0xFFA7F3C5),
    secondary = Color(0xFFA9C7B3),
    onSecondary = Color(0xFF173522),
    secondaryContainer = Color(0xFF15271C),
    onSecondaryContainer = Color(0xFFC5E4CE),
    tertiary = Color(0xFF66E49A),
    onTertiary = Color(0xFF00391B),
    tertiaryContainer = Color(0xFF064B29),
    onTertiaryContainer = Color(0xFF9CF6BD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFF3F7F4),
    surface = Color.Black,
    onSurface = Color(0xFFF3F7F4),
    surfaceVariant = Color(0xFF2C342F),
    onSurfaceVariant = Color(0xFF98A39C),
    outline = Color(0xFF647068),
    outlineVariant = Color(0xFF263029),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0D0B),
    surfaceContainer = Color(0xFF0D110F),
    surfaceContainerHigh = Color(0xFF111713),
    surfaceContainerHighest = Color(0xFF171D19),
)

private val DeckTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val DeckShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun CodecksTheme(
    mode: CodecksThemeMode = CodecksThemeMode.Oled,
    settings: CodecksThemeSettings = CodecksThemeSettings(mode = mode),
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val resolvedMode = settings.mode
    val darkResolved = resolvedMode == CodecksThemeMode.Dark ||
        resolvedMode == CodecksThemeMode.Oled ||
        (resolvedMode == CodecksThemeMode.System && darkTheme)
    val colorScheme = when {
        resolvedMode == CodecksThemeMode.Oled -> DeckOledColors
        darkResolved -> DeckDarkColors
        else -> DeckLightColors
    }.applyThemeSettings(settings, darkResolved)

    CompositionLocalProvider(LocalCodecksIconPack provides settings.iconPack) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DeckTypography,
            shapes = shapesFor(settings.shapeStyle),
            content = content,
        )
    }
}

private fun ColorScheme.applyThemeSettings(
    settings: CodecksThemeSettings,
    dark: Boolean,
): ColorScheme {
    val accent = settings.accent.color(dark)
    val onAccent = if (dark) Color(0xFF06111F) else Color.White
    val accentContainer = settings.accent.container(dark)
    val onAccentContainer = settings.accent.onContainer(dark)
    val contrast = when (settings.surfaceStyle) {
        CodecksSurfaceStyle.Balanced -> 0
        CodecksSurfaceStyle.Crisp -> 1
        CodecksSurfaceStyle.Colorful -> 2
    }
    val borderBoost = when (settings.borderStyle) {
        CodecksBorderStyle.Subtle -> 0
        CodecksBorderStyle.Visible -> 1
        CodecksBorderStyle.Strong -> 2
    }
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = if (settings.surfaceStyle == CodecksSurfaceStyle.Colorful) settings.accent.secondary(dark) else secondary,
        secondaryContainer = if (settings.surfaceStyle == CodecksSurfaceStyle.Colorful) settings.accent.secondaryContainer(dark) else secondaryContainer,
        outline = outline.withContrast(borderBoost, dark),
        outlineVariant = outlineVariant.withContrast(borderBoost, dark),
        surfaceContainerLow = surfaceContainerLow.withSurfaceContrast(contrast, dark),
        surfaceContainer = surfaceContainer.withSurfaceContrast(contrast, dark),
        surfaceContainerHigh = surfaceContainerHigh.withSurfaceContrast(contrast + 1, dark),
        surfaceContainerHighest = surfaceContainerHighest.withSurfaceContrast(contrast + 1, dark),
    )
}

private fun CodecksAccent.color(dark: Boolean): Color = when (this) {
    CodecksAccent.Blue -> if (dark) Color(0xFF8FCAFF) else Color(0xFF1769E0)
    CodecksAccent.Cyan -> if (dark) Color(0xFF73D7F4) else Color(0xFF006A7D)
    CodecksAccent.Green -> if (dark) Color(0xFF3DDC84) else Color(0xFF087F3F)
    CodecksAccent.Violet -> if (dark) Color(0xFFD1BCFF) else Color(0xFF6D4FD8)
    CodecksAccent.Amber -> if (dark) Color(0xFFFFCA73) else Color(0xFF8A5A00)
    CodecksAccent.Rose -> if (dark) Color(0xFFFF8FD8) else Color(0xFFA9006E)
    CodecksAccent.Coral -> if (dark) Color(0xFFFFA38B) else Color(0xFFA33A22)
    CodecksAccent.Lime -> if (dark) Color(0xFFB8F45A) else Color(0xFF4D7200)
}

private fun CodecksAccent.container(dark: Boolean): Color = when (this) {
    CodecksAccent.Blue -> if (dark) Color(0xFF0A3A68) else Color(0xFFD8E9FF)
    CodecksAccent.Cyan -> if (dark) Color(0xFF003F4C) else Color(0xFFBDEFFF)
    CodecksAccent.Green -> if (dark) Color(0xFF0A3721) else Color(0xFFC8F5D8)
    CodecksAccent.Violet -> if (dark) Color(0xFF443184) else Color(0xFFE9DDFF)
    CodecksAccent.Amber -> if (dark) Color(0xFF533B00) else Color(0xFFFFDEA3)
    CodecksAccent.Rose -> if (dark) Color(0xFF651246) else Color(0xFFFFD8EA)
    CodecksAccent.Coral -> if (dark) Color(0xFF652416) else Color(0xFFFFDBD1)
    CodecksAccent.Lime -> if (dark) Color(0xFF334C00) else Color(0xFFE0F9A4)
}

private fun CodecksAccent.onContainer(dark: Boolean): Color = when (this) {
    CodecksAccent.Blue -> if (dark) Color(0xFFD8E9FF) else Color(0xFF001B3D)
    CodecksAccent.Cyan -> if (dark) Color(0xFFBDEFFF) else Color(0xFF001F27)
    CodecksAccent.Green -> if (dark) Color(0xFFA7F3C5) else Color(0xFF00210D)
    CodecksAccent.Violet -> if (dark) Color(0xFFE9DDFF) else Color(0xFF21005D)
    CodecksAccent.Amber -> if (dark) Color(0xFFFFDEA3) else Color(0xFF2A1800)
    CodecksAccent.Rose -> if (dark) Color(0xFFFFD8EA) else Color(0xFF3E0026)
    CodecksAccent.Coral -> if (dark) Color(0xFFFFDBD1) else Color(0xFF3D0900)
    CodecksAccent.Lime -> if (dark) Color(0xFFE0F9A4) else Color(0xFF142000)
}

private fun CodecksAccent.secondary(dark: Boolean): Color = when (this) {
    CodecksAccent.Blue -> if (dark) Color(0xFFB9C7DD) else Color(0xFF526070)
    CodecksAccent.Cyan -> if (dark) Color(0xFFB0CCD3) else Color(0xFF486269)
    CodecksAccent.Green -> if (dark) Color(0xFFB8CCB8) else Color(0xFF526350)
    CodecksAccent.Violet -> if (dark) Color(0xFFC9C0DE) else Color(0xFF625B71)
    CodecksAccent.Amber -> if (dark) Color(0xFFD6C3A3) else Color(0xFF6D5D3F)
    CodecksAccent.Rose -> if (dark) Color(0xFFE4BBD2) else Color(0xFF775667)
    CodecksAccent.Coral -> if (dark) Color(0xFFE4BDB4) else Color(0xFF78574F)
    CodecksAccent.Lime -> if (dark) Color(0xFFC4D0A5) else Color(0xFF5E664B)
}

private fun CodecksAccent.secondaryContainer(dark: Boolean): Color = when (this) {
    CodecksAccent.Blue -> if (dark) Color(0xFF344354) else Color(0xFFD6E4F7)
    CodecksAccent.Cyan -> if (dark) Color(0xFF304850) else Color(0xFFCCE8EF)
    CodecksAccent.Green -> if (dark) Color(0xFF374A36) else Color(0xFFD4E8D1)
    CodecksAccent.Violet -> if (dark) Color(0xFF4A4458) else Color(0xFFE8DEF8)
    CodecksAccent.Amber -> if (dark) Color(0xFF51452D) else Color(0xFFF7E2B7)
    CodecksAccent.Rose -> if (dark) Color(0xFF57404C) else Color(0xFFFFD8EA)
    CodecksAccent.Coral -> if (dark) Color(0xFF59413B) else Color(0xFFFFDBD1)
    CodecksAccent.Lime -> if (dark) Color(0xFF454B35) else Color(0xFFE5EBCF)
}

private fun Color.withSurfaceContrast(level: Int, dark: Boolean): Color {
    val delta = (level.coerceIn(0, 3) * 0.035f)
    return if (dark) lighten(delta) else darken(delta)
}

private fun Color.withContrast(level: Int, dark: Boolean): Color {
    val delta = (level.coerceIn(0, 2) * 0.09f)
    return if (dark) lighten(delta) else darken(delta)
}

private fun Color.lighten(amount: Float): Color =
    copy(
        red = red + (1f - red) * amount,
        green = green + (1f - green) * amount,
        blue = blue + (1f - blue) * amount,
    )

private fun Color.darken(amount: Float): Color =
    copy(red = red * (1f - amount), green = green * (1f - amount), blue = blue * (1f - amount))

private fun shapesFor(style: CodecksShapeStyle): Shapes = when (style) {
    CodecksShapeStyle.Native -> DeckShapes
    CodecksShapeStyle.Compact -> Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    )
    CodecksShapeStyle.Soft -> Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )
}
