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
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.ui.theme.LocalCodecksSemanticColors

@Composable
fun CodecksPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = codecksSemanticColorTokens()
    val accent = when {
        danger -> scheme.error
        selected -> scheme.primary
        else -> scheme.outlineVariant
    }
    Surface(
        color = semantic.transparent,
        contentColor = if (danger) scheme.onErrorContainer else scheme.onSurface,
        border = BorderStroke(
            width = if (selected || danger) CodecksDesignTokens.Stroke.emphasized else CodecksDesignTokens.Stroke.hairline,
            color = accent.copy(
                alpha = if (selected || danger) CodecksDesignTokens.Opacity.emphasized else CodecksDesignTokens.Opacity.disabled,
            ),
        ),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = CodecksDesignTokens.Elevation.flat,
        shadowElevation = CodecksDesignTokens.Elevation.flat,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = when {
                        danger -> listOf(
                            scheme.error.copy(alpha = CodecksDesignTokens.Opacity.soft),
                            scheme.surfaceContainerLow.copy(alpha = CodecksDesignTokens.Opacity.nearlyOpaque),
                            semantic.canvas.copy(alpha = CodecksDesignTokens.Opacity.outline),
                        )
                        selected -> listOf(
                            scheme.primary.copy(alpha = CodecksDesignTokens.Opacity.stateLayer),
                            scheme.surfaceContainerHigh.copy(alpha = CodecksDesignTokens.Opacity.nearlyOpaque),
                            semantic.canvas.copy(alpha = CodecksDesignTokens.Opacity.outline),
                        )
                        else -> listOf(
                            scheme.primary.copy(alpha = CodecksDesignTokens.Opacity.barelyVisible),
                            scheme.surfaceContainerLow.copy(alpha = CodecksDesignTokens.Opacity.nearlyOpaque),
                            semantic.canvas.copy(alpha = CodecksDesignTokens.Opacity.medium),
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
    glowColor: Color = LocalCodecksSemanticColors.current.glow,
    canvasColor: Color = MaterialTheme.colorScheme.background,
) {
    val glowStrength = LocalCodecksSemanticColors.current.glowStrength
    val semantic = codecksSemanticColorTokens()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(CodecksDesignTokens.Surface.edgeGlowDepth)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(glowColor.copy(alpha = CodecksDesignTokens.Surface.topGlowAlpha * glowStrength), semantic.transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(CodecksDesignTokens.Surface.edgeGlowDepth)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(semantic.transparent, glowColor.copy(alpha = CodecksDesignTokens.Surface.bottomGlowAlpha * glowStrength)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(CodecksDesignTokens.Surface.sideGlowWidth)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(glowColor.copy(alpha = CodecksDesignTokens.Surface.sideGlowAlpha * glowStrength), semantic.transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(CodecksDesignTokens.Surface.sideGlowWidth)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(semantic.transparent, glowColor.copy(alpha = CodecksDesignTokens.Surface.sideGlowAlpha * glowStrength)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(semantic.transparent, canvasColor.copy(alpha = CodecksDesignTokens.Surface.centerVeilAlpha)),
                    ),
                ),
        )
    }
}
