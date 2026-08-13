package io.codecks.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.DeckActionButton

@Composable
internal fun BlankButtonComposer(
    onCreate: (String, String, ActionIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var colorHex by rememberSaveable { mutableStateOf(DeckBlankColors.first()) }
    var iconName by rememberSaveable { mutableStateOf(ActionIcon.Empty.name) }
    val icon = ActionIcon.entries.firstOrNull { it.name == iconName } ?: ActionIcon.Empty
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(12.dp)) {
            Text("Make an empty colored button", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(32) },
                singleLine = true,
                label = { Text("Label (optional)") },
                placeholder = { Text("Build zone, Focus, blank spacer") },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.fillMaxWidth().selectableGroup(),
            ) {
                items(DeckBlankColors, key = { it }) { choice ->
                    ColorSwatch(choice, colorHex == choice) { colorHex = choice }
                }
            }
            OutlinedTextField(
                value = colorHex,
                onValueChange = { candidate -> colorHex = candidate.trim().take(7) },
                singleLine = true,
                label = { Text("Custom color (#RRGGBB)") },
                isError = colorHex.toComposeColorOrNull() == null,
                supportingText = if (colorHex.toComposeColorOrNull() == null) {
                    { Text("Enter six hexadecimal color digits.") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            DeckIconPicker(icon, { iconName = it.name })
            DeckActionButton(
                label = "Make colored blank",
                onClick = {
                    onCreate(label.trim(), colorHex, icon)
                    label = ""
                },
                enabled = colorHex.toComposeColorOrNull() != null,
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
        }
    }
}

@Composable
internal fun ColorSwatch(colorHex: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = colorHex.toComposeColorOrNull() ?: MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
        ),
        modifier = Modifier
            .size(CodecksDesignTokens.Size.minTouchTarget)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = colorSwatchName(colorHex)
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .testTag("blank-color-$colorHex")
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {}
}

private fun colorSwatchName(colorHex: String): String = when (colorHex.uppercase()) {
    "#7CFFC4" -> "Mint"
    "#8EA1FF" -> "Periwinkle"
    "#FF7AA8" -> "Pink"
    "#FFD166" -> "Gold"
    "#FFFFFF" -> "White"
    "#A855F7" -> "Purple"
    "#22D3EE" -> "Cyan"
    "#F97316" -> "Orange"
    else -> "Custom color"
}

internal fun customDecorAction(slot: Int, label: String, colorHex: String, icon: ActionIcon): DeckAction {
    val cleanLabel = label.ifBlank { "Blank" }.take(32)
    val safeIdLabel = cleanLabel.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        .trim('_').ifBlank { "blank" }.take(18)
    return DeckAction(
        id = "custom_blank_${slot}_${System.currentTimeMillis()}_$safeIdLabel",
        label = cleanLabel,
        kind = ActionKind.Local,
        icon = icon,
        description = "Empty colored deck spacer",
        route = "decor",
        liveSafe = true,
        colorHex = colorHex,
    )
}

private val DeckBlankColors = listOf(
    "#7CFFC4", "#8EA1FF", "#FF7AA8", "#FFD166",
    "#FFFFFF", "#A855F7", "#22D3EE", "#F97316",
)
