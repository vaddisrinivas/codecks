package io.codex.s23deck.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codex.s23deck.core.design.DeckBridgeDesignTokens
import io.codex.s23deck.ui.theme.CodecksDeckStyle

@Composable
fun DeckPage(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = DeckBridgeDesignTokens.Size.sheetMaxWidth,
    content: LazyListScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.fillMaxSize())
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth),
            content = content,
        )
    }
}

@Composable
fun DeckSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(
                start = DeckBridgeDesignTokens.Spacing.lg,
                end = DeckBridgeDesignTokens.Spacing.lg,
                top = DeckBridgeDesignTokens.Spacing.xl,
                bottom = DeckBridgeDesignTokens.Spacing.sm,
            )
            .semantics { heading() },
    )
}

@Composable
fun DeckEmptyState(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DeckBridgeDesignTokens.Spacing.page, vertical = DeckBridgeDesignTokens.Spacing.page),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

enum class DeckComponentState {
    Idle,
    Running,
    Selected,
    Failure,
    Disabled,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckControlTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: DeckComponentState = DeckComponentState.Idle,
    enabled: Boolean = state != DeckComponentState.Disabled,
    danger: Boolean = false,
    showLabel: Boolean = true,
    deckStyle: CodecksDeckStyle = CodecksDeckStyle.StreamDeckPro,
    onLongClick: (() -> Unit)? = null,
) {
    if (deckStyle != CodecksDeckStyle.CodexMicroGlass) {
        FlatDeckTile(
            label = label,
            icon = icon,
            state = state,
            enabled = enabled,
            danger = danger,
            showLabel = showLabel,
            deckStyle = deckStyle,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.heightIn(min = DeckBridgeDesignTokens.Size.controlTileMinHeight),
        )
        return
    }

    CkDeckKey(
        label = label,
        icon = icon,
        state = state.toDeckKeyVisualState(),
        enabled = enabled,
        dangerous = danger,
        showLabel = showLabel,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.heightIn(min = DeckBridgeDesignTokens.Size.controlTileMinHeight),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlatDeckTile(
    label: String,
    icon: ImageVector,
    state: DeckComponentState,
    enabled: Boolean,
    danger: Boolean,
    showLabel: Boolean,
    deckStyle: CodecksDeckStyle,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = flatDeckTileColors(label, deckStyle, state, pressed, danger, enabled)
    val shape = RoundedCornerShape(
        when (deckStyle) {
            CodecksDeckStyle.AuroraGlass -> 24.dp
            CodecksDeckStyle.CandyPop -> 26.dp
            CodecksDeckStyle.ArcadeNeon -> 18.dp
            CodecksDeckStyle.OneUiWidgetGrid -> 22.dp
            CodecksDeckStyle.StreamDeckPro -> 20.dp
            CodecksDeckStyle.NothingMonoDeck -> 18.dp
            CodecksDeckStyle.CodexMicroGlass -> 20.dp
        },
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = if (pressed) 70 else 140),
        label = "flatDeckTilePressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                shadowElevation = if (deckStyle in setOf(CodecksDeckStyle.AuroraGlass, CodecksDeckStyle.ArcadeNeon) && enabled) 8f else 0f
                this.shape = shape
                clip = false
            }
            .clip(shape)
            .background(colors.container)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.glow.copy(alpha = colors.glowAlpha),
                        Color.Transparent,
                    ),
                ),
            )
            .border(BorderStroke(colors.borderWidth, colors.border), shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled && state != DeckComponentState.Running,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (showLabel) 8.dp else 0.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = if (showLabel) 10.dp else 8.dp),
        ) {
            Surface(
                color = colors.iconContainer,
                contentColor = colors.icon,
                shape = RoundedCornerShape(
                    when (deckStyle) {
                        CodecksDeckStyle.StreamDeckPro,
                        CodecksDeckStyle.ArcadeNeon -> 14.dp
                        CodecksDeckStyle.CandyPop -> 18.dp
                        else -> 999.dp
                    },
                ),
                border = if (deckStyle == CodecksDeckStyle.NothingMonoDeck) {
                    BorderStroke(1.dp, colors.border.copy(alpha = 0.70f))
                } else {
                    null
                },
                modifier = Modifier.size(if (showLabel) 44.dp else 52.dp),
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.icon,
                        modifier = Modifier.size(if (showLabel) 25.dp else 30.dp),
                    )
                }
            }
            if (showLabel) {
                Text(
                    text = label,
                    color = colors.content,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state != DeckComponentState.Idle && state != DeckComponentState.Disabled) {
            DeckKeyStateMarker(
                state = state.toDeckKeyVisualState(),
                dangerous = danger,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
            )
        }
    }
}

private data class FlatDeckTileColors(
    val container: Brush,
    val content: Color,
    val icon: Color,
    val iconContainer: Color,
    val border: Color,
    val borderWidth: androidx.compose.ui.unit.Dp,
    val glow: Color,
    val glowAlpha: Float,
)

@Composable
private fun flatDeckTileColors(
    label: String,
    deckStyle: CodecksDeckStyle,
    state: DeckComponentState,
    pressed: Boolean,
    danger: Boolean,
    enabled: Boolean,
): FlatDeckTileColors {
    val scheme = MaterialTheme.colorScheme
    val stateGlow = deckKeyGlowColor(state, danger)
    val active = pressed || state == DeckComponentState.Selected || state == DeckComponentState.Running
    val disabledAlpha = if (enabled) 1f else 0.38f
    return when (deckStyle) {
        CodecksDeckStyle.AuroraGlass -> playfulDeckTileColors(
            accent = playfulTileAccent(deckStyle, label),
            active = active,
            danger = danger,
            enabled = enabled,
            scheme = scheme,
            glassMid = Color(0xFF131021),
            glassAlpha = 0.94f,
        )
        CodecksDeckStyle.CandyPop -> playfulDeckTileColors(
            accent = playfulTileAccent(deckStyle, label),
            active = active,
            danger = danger,
            enabled = enabled,
            scheme = scheme,
            glassMid = Color(0xFF21131B),
            glassAlpha = 0.91f,
        )
        CodecksDeckStyle.ArcadeNeon -> playfulDeckTileColors(
            accent = playfulTileAccent(deckStyle, label),
            active = active,
            danger = danger,
            enabled = enabled,
            scheme = scheme,
            glassMid = Color(0xFF071512),
            glassAlpha = 0.96f,
        )
        CodecksDeckStyle.OneUiWidgetGrid -> FlatDeckTileColors(
            container = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (active) 0.13f else 0.075f),
                    Color(0xFF111318).copy(alpha = 0.96f * disabledAlpha),
                    Color.Black.copy(alpha = 0.96f),
                ),
            ),
            content = Color.White.copy(alpha = if (enabled) 0.82f else 0.34f),
            icon = if (danger) scheme.error else Color.White.copy(alpha = if (enabled) 0.92f else 0.34f),
            iconContainer = Color.White.copy(alpha = if (active) 0.14f else 0.075f),
            border = Color.White.copy(alpha = if (active) 0.34f else 0.16f),
            borderWidth = 1.dp,
            glow = if (active || danger) stateGlow else Color.White,
            glowAlpha = if (active || danger) 0.18f else 0.045f,
        )
        CodecksDeckStyle.StreamDeckPro -> FlatDeckTileColors(
            container = Brush.verticalGradient(
                listOf(
                    if (active) stateGlow.copy(alpha = 0.22f) else Color(0xFF111713),
                    Color(0xFF080B09),
                ),
            ),
            content = Color.White.copy(alpha = if (enabled) 0.92f else 0.58f),
            icon = if (danger) scheme.error else stateGlow.copy(alpha = if (enabled) 1f else 0.58f),
            iconContainer = if (danger) scheme.error.copy(alpha = 0.18f) else stateGlow.copy(alpha = if (active) 0.24f else 0.13f),
            border = if (active) stateGlow.copy(alpha = 0.78f) else stateGlow.copy(alpha = if (enabled) 0.25f else 0.14f),
            borderWidth = if (active) 1.5.dp else 1.dp,
            glow = stateGlow,
            glowAlpha = if (active) 0.18f else 0.035f,
        )
        CodecksDeckStyle.NothingMonoDeck -> FlatDeckTileColors(
            container = Brush.verticalGradient(
                listOf(Color.Black, Color(0xFF050505)),
            ),
            content = Color.White.copy(alpha = if (enabled) 0.84f else 0.30f),
            icon = if (danger || state == DeckComponentState.Failure) Color(0xFFFF2B2B) else Color.White.copy(alpha = if (enabled) 0.94f else 0.30f),
            iconContainer = if (danger || state == DeckComponentState.Failure) Color(0xFFFF2B2B).copy(alpha = 0.16f) else Color.White.copy(alpha = if (active) 0.14f else 0.055f),
            border = if (danger || state == DeckComponentState.Failure) Color(0xFFFF2B2B).copy(alpha = 0.78f) else Color.White.copy(alpha = if (active) 0.48f else 0.22f),
            borderWidth = if (active) 2.dp else 1.dp,
            glow = if (danger || state == DeckComponentState.Failure) Color(0xFFFF2B2B) else Color.White,
            glowAlpha = if (active || danger) 0.16f else 0.035f,
        )
        CodecksDeckStyle.CodexMicroGlass -> FlatDeckTileColors(
            container = Brush.verticalGradient(listOf(Color.Black, Color.Black)),
            content = Color.White,
            icon = Color.White,
            iconContainer = Color.White.copy(alpha = 0.10f),
            border = Color.White.copy(alpha = 0.18f),
            borderWidth = 1.dp,
            glow = Color.White,
            glowAlpha = 0.08f,
        )
    }
}

private fun playfulDeckTileColors(
    accent: Color,
    active: Boolean,
    danger: Boolean,
    enabled: Boolean,
    scheme: androidx.compose.material3.ColorScheme,
    glassMid: Color,
    glassAlpha: Float,
): FlatDeckTileColors {
    val tone = if (danger) scheme.error else accent
    val enabledAlpha = if (enabled) 1f else 0.34f
    return FlatDeckTileColors(
        container = Brush.verticalGradient(
            listOf(
                tone.copy(alpha = if (active) 0.42f else 0.24f),
                glassMid.copy(alpha = glassAlpha),
                Color.Black.copy(alpha = 0.98f),
            ),
        ),
        content = Color.White.copy(alpha = if (enabled) 0.92f else 0.34f),
        icon = tone.copy(alpha = if (enabled) 1f else enabledAlpha),
        iconContainer = tone.copy(alpha = if (active) 0.34f else 0.20f),
        border = tone.copy(alpha = if (active) 0.88f else 0.48f),
        borderWidth = if (active) 2.dp else 1.dp,
        glow = tone,
        glowAlpha = if (active) 0.34f else 0.16f,
    )
}

private fun playfulTileAccent(deckStyle: CodecksDeckStyle, label: String): Color {
    val palette = when (deckStyle) {
        CodecksDeckStyle.AuroraGlass -> listOf(
            Color(0xFF54E5FF),
            Color(0xFFA98BFF),
            Color(0xFFFF72CE),
            Color(0xFF6F9CFF),
        )
        CodecksDeckStyle.CandyPop -> listOf(
            Color(0xFFFF8B72),
            Color(0xFFFFC45E),
            Color(0xFF73F0C0),
            Color(0xFFCBA6FF),
        )
        CodecksDeckStyle.ArcadeNeon -> listOf(
            Color(0xFFB9FF39),
            Color(0xFF31E6FF),
            Color(0xFFFF4FD8),
            Color(0xFFFF9D2E),
        )
        else -> listOf(Color.White)
    }
    val stableIndex = label.fold(0) { hash, char -> ((hash * 31) + char.code) and Int.MAX_VALUE }
    return palette[stableIndex % palette.size]
}

private fun DeckComponentState.toDeckKeyVisualState(): DeckKeyVisualState = when (this) {
    DeckComponentState.Idle -> DeckKeyVisualState.Idle
    DeckComponentState.Running -> DeckKeyVisualState.Running
    DeckComponentState.Selected -> DeckKeyVisualState.Selected
    DeckComponentState.Failure -> DeckKeyVisualState.Failure
    DeckComponentState.Disabled -> DeckKeyVisualState.Unavailable
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckSettingsRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = contentColor)
        },
        trailingContent = {
            when {
                trailingContent != null -> trailingContent()
                value != null -> Text(value, color = contentColor)
                showChevron -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        },
        modifier = modifier.combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
    )
}

@Composable
fun DeckStatusIndicator(
    state: DeckComponentState,
    modifier: Modifier = Modifier,
    label: String = state.defaultLabel(),
) {
    val colors = deckComponentColors(state = state, pressed = false, danger = false)
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (state == DeckComponentState.Running) {
                CircularProgressIndicator(
                    color = colors.content,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = state.statusIcon(),
                    contentDescription = null,
                    tint = colors.icon,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun DeckFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = when {
            !enabled -> scheme.surfaceVariant.copy(alpha = 0.30f)
            selected -> scheme.primary.copy(alpha = 0.18f)
            else -> scheme.surfaceContainerLow.copy(alpha = 0.76f)
        },
        contentColor = if (enabled) {
            if (selected) scheme.onSurface else scheme.onSurfaceVariant
        } else {
            scheme.onSurface.copy(alpha = 0.38f)
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !enabled -> scheme.outlineVariant.copy(alpha = 0.30f)
                selected -> scheme.primary.copy(alpha = 0.72f)
                else -> scheme.outlineVariant.copy(alpha = 0.42f)
            },
        ),
        modifier = modifier
            .heightIn(min = DeckBridgeDesignTokens.Size.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled && selected) scheme.primary else LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DeckActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = selected || pressed
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = when {
            !enabled -> scheme.surfaceVariant.copy(alpha = 0.30f)
            active -> scheme.primary.copy(alpha = 0.18f)
            else -> scheme.surfaceContainerLow.copy(alpha = 0.78f)
        },
        contentColor = if (enabled) {
            scheme.onSurface
        } else {
            scheme.onSurface.copy(alpha = 0.38f)
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !enabled -> scheme.outlineVariant.copy(alpha = 0.30f)
                active -> scheme.primary.copy(alpha = 0.72f)
                else -> scheme.outlineVariant.copy(alpha = 0.42f)
            },
        ),
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) scheme.primary else LocalContentColor.current,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (icon == null) Modifier else Modifier.padding(start = 8.dp),
            )
        }
    }
}

private data class DeckComponentColors(
    val container: Color,
    val content: Color,
    val icon: Color,
    val iconContainer: Color,
)

@Composable
private fun deckComponentColors(
    state: DeckComponentState,
    pressed: Boolean,
    danger: Boolean,
): DeckComponentColors {
    val scheme = MaterialTheme.colorScheme
    return when {
        state == DeckComponentState.Disabled -> DeckComponentColors(
            container = scheme.surfaceVariant.copy(alpha = 0.38f),
            content = scheme.onSurface.copy(alpha = 0.38f),
            icon = scheme.onSurface.copy(alpha = 0.38f),
            iconContainer = scheme.surfaceVariant.copy(alpha = 0.28f),
        )
        state == DeckComponentState.Failure -> DeckComponentColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            icon = scheme.error,
            iconContainer = scheme.onErrorContainer.copy(alpha = 0.12f),
        )
        state == DeckComponentState.Running -> DeckComponentColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            icon = scheme.onPrimaryContainer,
            iconContainer = scheme.onPrimaryContainer.copy(alpha = 0.12f),
        )
        state == DeckComponentState.Selected -> DeckComponentColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            icon = scheme.primary,
            iconContainer = scheme.onPrimaryContainer.copy(alpha = 0.10f),
        )
        pressed -> DeckComponentColors(
            container = scheme.primaryContainer.copy(alpha = 0.74f),
            content = scheme.onPrimaryContainer,
            icon = if (danger) scheme.error else scheme.onPrimaryContainer,
            iconContainer = if (danger) scheme.errorContainer else scheme.primaryContainer,
        )
        else -> DeckComponentColors(
            container = scheme.surfaceContainerHigh.copy(alpha = 0.78f),
            content = scheme.onSurface,
            icon = if (danger) scheme.error else scheme.onSurface,
            iconContainer = if (danger) scheme.errorContainer.copy(alpha = 0.56f) else Color.White.copy(alpha = 0.22f),
        )
    }
}

@Composable
private fun deckKeyGlowColor(
    state: DeckComponentState,
    danger: Boolean,
): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        danger || state == DeckComponentState.Failure -> scheme.error
        state == DeckComponentState.Running -> scheme.tertiary
        state == DeckComponentState.Selected -> scheme.primary
        else -> scheme.primary
    }
}

private fun DeckComponentState.defaultLabel(): String = when (this) {
    DeckComponentState.Idle -> "Idle"
    DeckComponentState.Running -> "Running"
    DeckComponentState.Selected -> "Selected"
    DeckComponentState.Failure -> "Failed"
    DeckComponentState.Disabled -> "Disabled"
}

private fun DeckComponentState.statusIcon(): ImageVector = when (this) {
    DeckComponentState.Idle -> Icons.Outlined.RadioButtonUnchecked
    DeckComponentState.Running -> Icons.Outlined.RadioButtonUnchecked
    DeckComponentState.Selected -> Icons.Outlined.CheckCircle
    DeckComponentState.Failure -> Icons.Outlined.ErrorOutline
    DeckComponentState.Disabled -> Icons.Outlined.Block
}
