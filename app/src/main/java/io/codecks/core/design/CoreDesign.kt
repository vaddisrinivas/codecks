package io.codecks.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.codecks.core.actions.ButtonState

object CodecksDesignTokens {
    object Spacing {
        val none = 0.dp
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val xxxl = 32.dp
        val page = 16.dp
    }

    object Size {
        val minTouchTarget = 48.dp
        val compactTouchTarget = 48.dp
        val controlTileMinHeight = 88.dp
        val iconSm = 20.dp
        val iconXs = 16.dp
        val iconMd = 24.dp
        val iconLg = 40.dp
        val pointerSurfaceMinHeight = 220.dp
        val deckIconContainer = 44.dp
        val deckIconContainerLarge = 52.dp
        val stateMarker = 18.dp
        val deckGlyph = 25.dp
        val deckGlyphLarge = 30.dp
        val actionMinHeight = 56.dp
        val sheetMaxWidth = 720.dp

        object HomeDeck {
            val suggestionRowHeight = 104.dp
            val suggestionCardHeight = 96.dp
            val suggestionCardWidth = 92.dp
            val suggestionMinWidth = 220.dp
            val suggestionMaxWidth = 280.dp
            val landscapeTemplateWidth = 204.dp
            val landscapeCustomWidth = 184.dp
            val adaptiveCell = 116.dp
            val featuredCardWidth = 112.dp
            val pagePillWidth = 56.dp
            val carouselHeight = 108.dp
            val dialogListMaxHeight = 440.dp
            val compactDialogListMaxHeight = 360.dp
        }

        object Trackpad {
            val feedbackTop = 72.dp
            val feedbackMaxWidth = 420.dp
            val guardBottom = 106.dp
            val centerHorizontal = 28.dp
            val centerIconContainer = 52.dp
            val centerIcon = 26.dp
            val decorationEdge = 22.dp
            val decorationShort = 34.dp
            val controlInset = 72.dp
            val bottomControlInset = 96.dp
            val sideControlInset = 84.dp
            val railPadding = 5.dp
            val railWidth = 22.dp
            val railHeight = 72.dp
        }
    }

    object Stroke {
        val hairline = 1.dp
        val emphasized = 1.5.dp
        val focus = 2.dp
    }

    object Shape {
        val extraSmall = 6.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val extraLarge = 24.dp
        val pill = 999.dp
    }

    object Elevation {
        val flat = 0.dp
        val low = 2.dp
        val medium = 6.dp
        val high = 12.dp
    }

    object Grid {
        val compactGap = 8.dp
        val standardGap = 12.dp
        val relaxedGap = 16.dp
        val minimumCell = 88.dp
    }

    object Opacity {
        const val transparent = 0f
        const val barelyVisible = 0.04f
        const val subtle = 0.08f
        const val stateLayer = 0.12f
        const val soft = 0.16f
        const val selectedContainer = 0.18f
        const val low = 0.24f
        const val disabled = 0.38f
        const val medium = 0.48f
        const val scrim = 0.56f
        const val muted = 0.68f
        const val outline = 0.42f
        const val emphasized = 0.72f
        const val high = 0.82f
        const val nearlyOpaque = 0.96f
        const val full = 1f
    }

    object Focus {
        val ringWidth = 2.dp
        const val ringAlpha = 0.92f
    }

    object StateLayer {
        const val hover = 0.08f
        const val focus = 0.12f
        const val pressed = 0.14f
        const val dragged = 0.18f
    }

    object Surface {
        val edgeGlowDepth = 132.dp
        val sideGlowWidth = 76.dp
        const val topGlowAlpha = 0.11f
        const val bottomGlowAlpha = 0.07f
        const val sideGlowAlpha = 0.055f
        const val centerVeilAlpha = 0.90f
    }

    object TrackpadPaint {
        const val traceMaxAlpha = 0.64f
        const val traceHeadMaxAlpha = 0.52f
        const val stylusStrokePx = 4.5f
        const val touchStrokePx = 6.5f
        const val stylusRadiusPx = 7f
        const val touchRadiusPx = 9f
        const val laneAlpha = 0.04f
        const val laneFineAlpha = 0.025f
        const val cornerAlpha = 0.12f
    }

    object Motion {
        const val instantMillis = 0
        const val pressMillis = 80
        const val stateChangeMillis = 160
        const val screenTransitionMillis = 220
        const val longPressMillis = 420
        const val pressedScale = 0.975f
        const val restingScale = 1f

        fun duration(normalMillis: Int, reducedMotion: Boolean): Int =
            if (reducedMotion) instantMillis else normalMillis.coerceAtLeast(instantMillis)
    }
}

enum class CodecksHapticToken {
    Confirm,
    Reject,
    Boundary,
    LongPress,
}

@Composable
fun CodecksStateChip(
    label: String,
    state: ButtonState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val container = when (state) {
        ButtonState.Running,
        ButtonState.Pressed -> colorScheme.primaryContainer
        ButtonState.Succeeded -> colorScheme.tertiaryContainer
        ButtonState.Failed -> colorScheme.errorContainer
        ButtonState.Disabled -> colorScheme.surfaceVariant
        ButtonState.Idle -> colorScheme.surfaceContainerLow
    }
    val content = when (state) {
        ButtonState.Failed -> colorScheme.onErrorContainer
        ButtonState.Disabled -> colorScheme.onSurfaceVariant
        else -> colorScheme.onSurface
    }
    Surface(
        color = container,
        contentColor = content,
        border = BorderStroke(CodecksDesignTokens.Stroke.hairline, colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = CodecksDesignTokens.Spacing.md,
                vertical = CodecksDesignTokens.Spacing.sm,
            ),
        )
    }
}

@Composable
fun CodecksListRow(
    icon: ImageVector,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(CodecksDesignTokens.Size.iconMd)) },
        trailingContent = value?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        modifier = modifier.heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
    )
}

@Composable
fun CodecksIconLabel(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(CodecksDesignTokens.Size.iconSm))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
