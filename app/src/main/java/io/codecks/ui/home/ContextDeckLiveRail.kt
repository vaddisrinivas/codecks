package io.codecks.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.domain.contextdeck.AnalogControl
import io.codecks.domain.contextdeck.AnalogControlKind
import io.codecks.domain.contextdeck.LiveSignal
import io.codecks.domain.contextdeck.LiveSignalValue
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.ui.theme.LocalCodecksSemanticColors

@Composable
internal fun ContextDeckLiveRail(
    signals: List<LiveSignal>,
    controls: List<AnalogControl>,
    statusMessage: String?,
    onCommit: (AnalogControlKind, Int) -> Unit,
    modifierLayerAvailable: Boolean,
    modifierLayerActive: Boolean,
    onModifierLayerPressed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!modifierLayerAvailable && signals.isEmpty() && controls.isEmpty() && statusMessage == null) return
    Column(
        verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xxs),
        modifier = modifier.testTag("context-deck-live-rail"),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xs), contentPadding = PaddingValues(end = CodecksDesignTokens.Spacing.md)) {
            if (modifierLayerAvailable) item("context-modifier") {
                ModifierLayerButton(modifierLayerActive, onModifierLayerPressed)
            }
            items(signals, key = { "live-${it.id}" }) { signal ->
                val active = signal.value == LiveSignalValue.Active && signal.status == ObservationStatus.Fresh
                Surface(
                    color = when {
                        active -> LocalCodecksSemanticColors.current.success.copy(alpha = 0.16f)
                        signal.value == LiveSignalValue.Unknown -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(CodecksDesignTokens.Stroke.hairline, if (active) LocalCodecksSemanticColors.current.success.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                    modifier = Modifier.heightIn(min = CodecksDesignTokens.Size.minTouchTarget).testTag("live-${signal.id.name.lowercase()}"),
                ) {
                    Text("${signal.id.name.lowercase().replaceFirstChar(Char::uppercase)} · ${signal.value.label()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.sm, vertical = CodecksDesignTokens.Spacing.xs))
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm), contentPadding = PaddingValues(end = CodecksDesignTokens.Spacing.md)) {
            items(controls, key = { "analog-${it.kind}" }) { AnalogDeckControl(it, onCommit) }
            statusMessage?.let { message -> item("context-status") {
                Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2, modifier = Modifier.widthIn(max = 240.dp).padding(CodecksDesignTokens.Spacing.sm))
            } }
        }
    }
}

@Composable
private fun ModifierLayerButton(active: Boolean, onPressedChange: (Boolean) -> Unit) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CodecksDesignTokens.Stroke.hairline, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        modifier = Modifier.heightIn(min = CodecksDesignTokens.Size.minTouchTarget).pointerInput(Unit) {
            detectTapGestures(onPress = { onPressedChange(true); tryAwaitRelease(); onPressedChange(false) })
        }.semantics {
            role = Role.Button
            stateDescription = if (active) "Secondary layer shown" else "Base layer shown"
            onClick("Toggle secondary layer") { onPressedChange(!active); true }
        }.testTag("context-modifier-layer"),
    ) {
        Text(if (active) "FN · Layer 2" else "Hold FN", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.xs))
    }
}

@Composable
private fun AnalogDeckControl(control: AnalogControl, onCommit: (AnalogControlKind, Int) -> Unit) {
    var preview by remember(control.kind, control.valuePercent) { mutableFloatStateOf((control.valuePercent ?: 0).toFloat()) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f), shape = MaterialTheme.shapes.large, modifier = Modifier.width(CodecksDesignTokens.Size.HomeDeck.featuredCardWidth)) {
        Column(modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.sm)) {
            Text("${control.kind.label()} · ${control.valuePercent?.let { "$it%" } ?: "Unavailable"}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(
                value = preview,
                onValueChange = { preview = it },
                onValueChangeFinished = { if (control.enabled) onCommit(control.kind, preview.toInt().coerceIn(0, 100)) },
                enabled = control.enabled,
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.testTag("analog-${control.kind.name.lowercase()}"),
            )
        }
    }
}

private fun LiveSignalValue.label() = when (this) {
    LiveSignalValue.Active -> "On"
    LiveSignalValue.Inactive -> "Off"
    LiveSignalValue.Unknown -> "Unavailable"
}

private fun AnalogControlKind.label() = when (this) {
    AnalogControlKind.Volume -> "Volume"
    AnalogControlKind.Brightness -> "Brightness"
    AnalogControlKind.Timeline -> "Timeline"
}
