package io.codecks.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CodecksDeckSurfaceTokens {
    val edgeGlowDepth = 132.dp
    val sideGlowWidth = 76.dp
    const val topGlowAlpha = 0.11f
    const val bottomGlowAlpha = 0.07f
    const val sideGlowAlpha = 0.055f
    const val centerVeilAlpha = 0.90f
}

@Composable
fun CodecksPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when {
        danger -> scheme.error
        selected -> scheme.primary
        else -> scheme.outlineVariant
    }
    Surface(
        color = Color.Transparent,
        contentColor = if (danger) scheme.onErrorContainer else scheme.onSurface,
        border = BorderStroke(
            width = if (selected || danger) 1.5.dp else 1.dp,
            color = accent.copy(alpha = if (selected || danger) 0.72f else 0.34f),
        ),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = when {
                        danger -> listOf(
                            scheme.error.copy(alpha = 0.16f),
                            scheme.surfaceContainerLow.copy(alpha = 0.94f),
                            Color.Black.copy(alpha = 0.44f),
                        )
                        selected -> listOf(
                            scheme.primary.copy(alpha = 0.10f),
                            scheme.surfaceContainerHigh.copy(alpha = 0.96f),
                            Color.Black.copy(alpha = 0.42f),
                        )
                        else -> listOf(
                            scheme.primary.copy(alpha = 0.045f),
                            scheme.surfaceContainerLow.copy(alpha = 0.96f),
                            Color.Black.copy(alpha = 0.46f),
                        )
                    },
                ),
            ),
        ) { content() }
    }
}

@Composable
fun CodecksDeckEdgeGlowBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    canvasColor: Color = MaterialTheme.colorScheme.background,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(CodecksDeckSurfaceTokens.edgeGlowDepth)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(glowColor.copy(alpha = CodecksDeckSurfaceTokens.topGlowAlpha), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(CodecksDeckSurfaceTokens.edgeGlowDepth)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, glowColor.copy(alpha = CodecksDeckSurfaceTokens.bottomGlowAlpha)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(CodecksDeckSurfaceTokens.sideGlowWidth)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(glowColor.copy(alpha = CodecksDeckSurfaceTokens.sideGlowAlpha), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(CodecksDeckSurfaceTokens.sideGlowWidth)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, glowColor.copy(alpha = CodecksDeckSurfaceTokens.sideGlowAlpha)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, canvasColor.copy(alpha = CodecksDeckSurfaceTokens.centerVeilAlpha)),
                    ),
                ),
        )
    }
}
