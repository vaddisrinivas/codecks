package io.codecks.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.core.design.CodecksHapticToken
import io.codecks.ui.theme.CodecksDeckStyle

@Composable
fun DeckPage(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = CodecksDesignTokens.Size.sheetMaxWidth,
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
            verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.none),
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
                start = CodecksDesignTokens.Spacing.lg,
                end = CodecksDesignTokens.Spacing.lg,
                top = CodecksDesignTokens.Spacing.xl,
                bottom = CodecksDesignTokens.Spacing.sm,
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
            .padding(horizontal = CodecksDesignTokens.Spacing.page, vertical = CodecksDesignTokens.Spacing.page),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(CodecksDesignTokens.Spacing.xl),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(CodecksDesignTokens.Size.deckIconContainer),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(CodecksDesignTokens.Size.iconMd),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

enum class DeckComponentState {
    Idle,
    Running,
    Succeeded,
    Selected,
    Failure,
    Disabled,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckControlTile(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: DeckComponentState = DeckComponentState.Idle,
    enabled: Boolean = state != DeckComponentState.Disabled,
    danger: Boolean = false,
    accentColor: Color? = null,
    showLabel: Boolean = true,
    deckStyle: CodecksDeckStyle = CodecksDeckStyle.StreamDeckPro,
    onLongClick: (() -> Unit)? = null,
) {
    if (deckStyle != CodecksDeckStyle.CodexMicroGlass || accentColor != null) {
        FlatDeckTile(
            label = label,
            icon = icon,
            state = state,
            enabled = enabled,
            danger = danger,
            accentColor = accentColor,
            showLabel = showLabel,
            deckStyle = deckStyle,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.heightIn(min = CodecksDesignTokens.Size.controlTileMinHeight),
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
        modifier = modifier.heightIn(min = CodecksDesignTokens.Size.controlTileMinHeight),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlatDeckTile(
    label: String,
    icon: ImageVector?,
    state: DeckComponentState,
    enabled: Boolean,
    danger: Boolean,
    accentColor: Color?,
    showLabel: Boolean,
    deckStyle: CodecksDeckStyle,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = codecksDeckTileVisual(state, pressed, danger, enabled, accentColor)
    val motionPolicy = LocalCodecksMotionPolicy.current
    val shape = MaterialTheme.shapes.large
    val haptics = LocalHapticFeedback.current
    var focused by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) CodecksDesignTokens.Motion.pressedScale else CodecksDesignTokens.Motion.restingScale,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = motionPolicy.duration(
                if (pressed) CodecksDesignTokens.Motion.pressMillis else CodecksDesignTokens.Motion.stateChangeMillis,
            ),
        ),
        label = "flatDeckTilePressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                shadowElevation = if (enabled) CodecksDesignTokens.Elevation.medium.toPx() else CodecksDesignTokens.Elevation.flat.toPx()
                this.shape = shape
                clip = false
            }
            .clip(shape)
            .background(colors.container)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.glow.copy(alpha = colors.glowAlpha),
                        codecksSemanticColorTokens().transparent,
                    ),
                ),
            )
            .border(
                BorderStroke(
                    if (focused) CodecksDesignTokens.Focus.ringWidth else colors.borderWidth,
                    if (focused) codecksSemanticColorTokens().focus.copy(alpha = CodecksDesignTokens.Focus.ringAlpha) else colors.border,
                ),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = (enabled || onLongClick != null) && state != DeckComponentState.Running,
                role = Role.Button,
                onClick = {
                    if (enabled) {
                        haptics.performCodecksHaptic(CodecksHapticToken.Confirm)
                        onClick()
                    }
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performCodecksHaptic(CodecksHapticToken.LongPress)
                        action()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (showLabel) CodecksDesignTokens.Spacing.sm else CodecksDesignTokens.Spacing.xxs, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().padding(horizontal = CodecksDesignTokens.Spacing.sm, vertical = CodecksDesignTokens.Spacing.sm),
        ) {
            if (icon != null) Surface(
                color = colors.iconContainer,
                contentColor = colors.icon,
                shape = MaterialTheme.shapes.medium,
                border = if (deckStyle == CodecksDeckStyle.NothingMonoDeck) {
                    BorderStroke(CodecksDesignTokens.Stroke.hairline, colors.border.copy(alpha = CodecksDesignTokens.Opacity.muted))
                } else {
                    null
                },
                modifier = Modifier.size(if (showLabel) CodecksDesignTokens.Size.deckIconContainer else CodecksDesignTokens.Size.deckIconContainerLarge),
                shadowElevation = CodecksDesignTokens.Elevation.flat,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.icon,
                        modifier = Modifier
                            .size(if (showLabel) CodecksDesignTokens.Size.deckGlyph else CodecksDesignTokens.Size.deckGlyphLarge)
                            .testTag("deck-control-icon"),
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
                modifier = Modifier.align(Alignment.TopEnd).padding(CodecksDesignTokens.Spacing.sm),
            )
        }
    }
}

private fun DeckComponentState.toDeckKeyVisualState(): DeckKeyVisualState = when (this) {
    DeckComponentState.Idle -> DeckKeyVisualState.Idle
    DeckComponentState.Running -> DeckKeyVisualState.Running
    DeckComponentState.Succeeded -> DeckKeyVisualState.Success
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
        MaterialTheme.colorScheme.onSurface.copy(alpha = CodecksDesignTokens.Opacity.disabled)
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
            horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.xs),
        ) {
            if (state == DeckComponentState.Running) {
                CircularProgressIndicator(
                    color = colors.content,
                    strokeWidth = CodecksDesignTokens.Stroke.focus,
                    modifier = Modifier.size(CodecksDesignTokens.Size.iconXs),
                )
            } else {
                Icon(
                    imageVector = state.statusIcon(),
                    contentDescription = null,
                    tint = colors.icon,
                    modifier = Modifier.size(CodecksDesignTokens.Size.iconXs),
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
            !enabled -> scheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.disabled)
            selected -> scheme.primary.copy(alpha = CodecksDesignTokens.Opacity.selectedContainer)
            else -> scheme.surfaceContainerLow.copy(alpha = CodecksDesignTokens.Opacity.emphasized)
        },
        contentColor = if (enabled) {
            if (selected) scheme.onSurface else scheme.onSurfaceVariant
        } else {
            scheme.onSurface.copy(alpha = CodecksDesignTokens.Opacity.disabled)
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = CodecksDesignTokens.Stroke.hairline,
            color = when {
                !enabled -> scheme.outlineVariant.copy(alpha = CodecksDesignTokens.Opacity.disabled)
                selected -> scheme.primary.copy(alpha = CodecksDesignTokens.Opacity.emphasized)
                else -> scheme.outlineVariant.copy(alpha = CodecksDesignTokens.Opacity.outline)
            },
        ),
        modifier = modifier
            .heightIn(min = CodecksDesignTokens.Size.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.lg, vertical = CodecksDesignTokens.Spacing.sm),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled && selected) scheme.primary else LocalContentColor.current,
                    modifier = Modifier.size(CodecksDesignTokens.Size.stateMarker),
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
            !enabled -> scheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.disabled)
            active -> scheme.primary.copy(alpha = CodecksDesignTokens.Opacity.selectedContainer)
            else -> scheme.surfaceContainerLow.copy(alpha = CodecksDesignTokens.Opacity.emphasized)
        },
        contentColor = if (enabled) {
            scheme.onSurface
        } else {
            scheme.onSurface.copy(alpha = CodecksDesignTokens.Opacity.disabled)
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = CodecksDesignTokens.Stroke.hairline,
            color = when {
                !enabled -> scheme.outlineVariant.copy(alpha = CodecksDesignTokens.Opacity.disabled)
                active -> scheme.primary.copy(alpha = CodecksDesignTokens.Opacity.emphasized)
                else -> scheme.outlineVariant.copy(alpha = CodecksDesignTokens.Opacity.outline)
            },
        ),
        modifier = modifier
            .heightIn(min = CodecksDesignTokens.Size.actionMinHeight)
            .semantics {
                if (!enabled) {
                    disabled()
                    stateDescription = "Disabled"
                }
            }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.sm),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) scheme.primary else LocalContentColor.current,
                    modifier = Modifier.size(CodecksDesignTokens.Size.iconSm),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (icon == null) Modifier else Modifier.padding(start = CodecksDesignTokens.Spacing.sm),
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
    val semantic = codecksSemanticColorTokens()
    return when {
        state == DeckComponentState.Disabled -> DeckComponentColors(
            container = scheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.disabled),
            content = scheme.onSurface.copy(alpha = CodecksDesignTokens.Opacity.disabled),
            icon = scheme.onSurface.copy(alpha = CodecksDesignTokens.Opacity.disabled),
            iconContainer = scheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.low),
        )
        state == DeckComponentState.Failure -> DeckComponentColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            icon = scheme.error,
            iconContainer = scheme.onErrorContainer.copy(alpha = CodecksDesignTokens.Opacity.stateLayer),
        )
        state == DeckComponentState.Succeeded -> DeckComponentColors(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            icon = scheme.tertiary,
            iconContainer = scheme.onTertiaryContainer.copy(alpha = CodecksDesignTokens.Opacity.stateLayer),
        )
        state == DeckComponentState.Running -> DeckComponentColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            icon = scheme.onPrimaryContainer,
            iconContainer = scheme.onPrimaryContainer.copy(alpha = CodecksDesignTokens.Opacity.stateLayer),
        )
        state == DeckComponentState.Selected -> DeckComponentColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            icon = scheme.primary,
            iconContainer = scheme.onPrimaryContainer.copy(alpha = CodecksDesignTokens.Opacity.subtle),
        )
        pressed -> DeckComponentColors(
            container = scheme.primaryContainer.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
            content = scheme.onPrimaryContainer,
            icon = if (danger) scheme.error else scheme.onPrimaryContainer,
            iconContainer = if (danger) scheme.errorContainer else scheme.primaryContainer,
        )
        else -> DeckComponentColors(
            container = scheme.surfaceContainerHigh.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
            content = scheme.onSurface,
            icon = if (danger) scheme.error else scheme.onSurface,
            iconContainer = if (danger) scheme.errorContainer.copy(alpha = CodecksDesignTokens.Opacity.scrim) else semantic.content.copy(alpha = CodecksDesignTokens.Opacity.low),
        )
    }
}

private fun DeckComponentState.defaultLabel(): String = when (this) {
    DeckComponentState.Idle -> "Idle"
    DeckComponentState.Running -> "Running"
    DeckComponentState.Succeeded -> "Succeeded"
    DeckComponentState.Selected -> "Selected"
    DeckComponentState.Failure -> "Failed"
    DeckComponentState.Disabled -> "Disabled"
}

private fun DeckComponentState.statusIcon(): ImageVector = when (this) {
    DeckComponentState.Idle -> Icons.Outlined.RadioButtonUnchecked
    DeckComponentState.Running -> Icons.Outlined.RadioButtonUnchecked
    DeckComponentState.Succeeded -> Icons.Outlined.CheckCircle
    DeckComponentState.Selected -> Icons.Outlined.CheckCircle
    DeckComponentState.Failure -> Icons.Outlined.ErrorOutline
    DeckComponentState.Disabled -> Icons.Outlined.Block
}
