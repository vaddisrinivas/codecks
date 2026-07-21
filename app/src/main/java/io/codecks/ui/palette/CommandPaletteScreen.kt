package io.codecks.ui.palette

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.domain.DeckAction
import io.codecks.ui.automations.AutomationItem
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckEmptyState
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.icons.deckImageVector

@Composable
fun CommandPaletteScreen(
    actions: List<DeckAction>,
    automations: List<AutomationItem>,
    runningActionId: String?,
    contentPadding: PaddingValues,
    onRunAction: (DeckAction) -> Unit,
    onRunAutomation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val commands = remember(actions, automations, query) {
        buildList {
            actions
                .filterNot { it.id in setOf("blank", "add_button") }
                .forEach { action ->
                    add(PaletteCommand.Action(action))
                }
            automations.forEach { automation ->
                add(PaletteCommand.Automation(automation))
            }
        }.filter { it.matches(query) }
            .sortedWith(compareBy<PaletteCommand> { it.group }.thenBy { it.title.lowercase() })
            .take(80)
    }

    DeckPage(contentPadding = contentPadding, modifier = modifier) {
        item {
            CodecksPanel(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
                    Text("Command Palette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text("Search actions and rules") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (commands.isEmpty()) {
            item {
                DeckEmptyState(
                    title = "No commands found",
                    body = "Try another action, app, or automation name.",
                    icon = Icons.Outlined.Search,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(
                count = commands.size,
                key = { index -> commands[index].id },
            ) { index ->
                val command = commands[index]
                PaletteCommandRow(
                    command = command,
                    running = runningActionId == command.id,
                    onClick = {
                        when (command) {
                            is PaletteCommand.Action -> onRunAction(command.action)
                            is PaletteCommand.Automation -> onRunAutomation(command.automation.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PaletteCommandRow(
    command: PaletteCommand,
    running: Boolean,
    onClick: () -> Unit,
) {
    CodecksPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Surface(
            onClick = onClick,
            enabled = !running,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp).heightIn(min = 48.dp),
            ) {
                Icon(command.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(command.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (running) "Running…" else command.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(command.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private sealed interface PaletteCommand {
    val id: String
    val title: String
    val subtitle: String
    val group: String

    fun matches(query: String): Boolean {
        val normalized = query.trim().lowercase()
        return normalized.isBlank() ||
            title.lowercase().contains(normalized) ||
            subtitle.lowercase().contains(normalized) ||
            group.lowercase().contains(normalized)
    }

    data class Action(val action: DeckAction) : PaletteCommand {
        override val id: String = action.id
        override val title: String = action.label
        override val subtitle: String = action.description.ifBlank { action.kind.name.lowercase() }
        override val group: String = "Deck"
    }

    data class Automation(val automation: AutomationItem) : PaletteCommand {
        override val id: String = automation.id
        override val title: String = automation.label
        override val subtitle: String = automation.description.ifBlank { automation.triggerLabel }
        override val group: String = "Rule"
    }
}

@Composable
private fun PaletteCommand.icon() = when (this) {
    is PaletteCommand.Action -> action.deckImageVector()
    is PaletteCommand.Automation -> Icons.Outlined.AutoAwesome
}
