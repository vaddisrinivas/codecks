package io.codecks.ui.runlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckEmptyState
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.home.ActionEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RunLogScreen(
    events: List<ActionEvent>,
    actions: List<DeckAction>,
    filterActionId: String?,
    contentPadding: PaddingValues,
    onClearFilter: () -> Unit,
    onRetry: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionById = remember(actions) { actions.associateBy(DeckAction::id) }
    val visibleEvents = remember(events, filterActionId) {
        if (filterActionId == null) events else events.filter { it.actionId == filterActionId }
    }
    val filterLabel = filterActionId?.let { actionById[it]?.label ?: visibleEvents.firstOrNull()?.label }

    DeckPage(contentPadding = contentPadding, modifier = modifier) {
        item {
            RunLogHeader(
                total = events.size,
                shown = visibleEvents.size,
                filterLabel = filterLabel,
                onClearFilter = onClearFilter,
                onClear = onClear,
            )
        }
        if (visibleEvents.isEmpty()) {
            item {
                DeckEmptyState(
                    title = if (filterActionId == null) "No runs yet" else "No runs for this action",
                    body = "Run a Deck button and its result will appear here.",
                    icon = Icons.Outlined.History,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(
                count = visibleEvents.size,
                key = { index -> "${visibleEvents[index].timestampMillis}-$index" },
            ) { index ->
                RunLogRow(
                    event = visibleEvents[index],
                    onRetry = { onRetry(visibleEvents[index].actionId) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun RunLogHeader(
    total: Int,
    shown: Int,
    filterLabel: String?,
    onClearFilter: () -> Unit,
    onClear: () -> Unit,
) {
    CodecksPanel(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Run Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when {
                            total == 0 -> "No actions run yet"
                            filterLabel != null -> "$shown runs for $filterLabel"
                            else -> "$total recent runs"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (total > 0) TextButton(onClick = onClear) { Text("Clear") }
            }
            if (filterLabel != null) {
                DeckActionButton(
                    label = "Show all runs",
                    onClick = onClearFilter,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun RunLogRow(
    event: ActionEvent,
    onRetry: () -> Unit,
) {
    CodecksPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(14.dp),
        ) {
            Surface(
                color = if (event.succeeded) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (event.succeeded) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(
                    imageVector = if (event.succeeded) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(event.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    event.logs.ifBlank { event.message },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                event.target?.let { target ->
                    Text(
                        target,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    RunLogTimeFormatter.format(Instant.ofEpochMilli(event.timestampMillis).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!event.succeeded) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

private val RunLogTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
