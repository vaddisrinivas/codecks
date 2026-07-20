package io.codecks.core.designsystem.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CodecksWidthClass {
    Compact,
    Medium,
    Expanded,
}

enum class CodecksHeightClass {
    Compact,
    Medium,
    Expanded,
}

@Immutable
data class CodecksWindowSize(
    val width: Dp,
    val height: Dp,
    val widthClass: CodecksWidthClass,
    val heightClass: CodecksHeightClass,
) {
    val isLandscape: Boolean get() = width > height
    val isDesktopLike: Boolean get() = widthClass == CodecksWidthClass.Expanded && heightClass != CodecksHeightClass.Compact
}

@Composable
fun rememberCodecksWindowSize(): CodecksWindowSize {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.dp
    val height = configuration.screenHeightDp.dp

    return CodecksWindowSize(
        width = width,
        height = height,
        widthClass = when {
            width < 600.dp -> CodecksWidthClass.Compact
            width < 840.dp -> CodecksWidthClass.Medium
            else -> CodecksWidthClass.Expanded
        },
        heightClass = when {
            height < 480.dp -> CodecksHeightClass.Compact
            height < 900.dp -> CodecksHeightClass.Medium
            else -> CodecksHeightClass.Expanded
        },
    )
}
