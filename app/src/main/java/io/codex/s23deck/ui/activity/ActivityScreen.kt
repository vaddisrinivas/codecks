package io.codex.s23deck.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codex.s23deck.ui.designsystem.DeckFilterPill
import io.codex.s23deck.ui.designsystem.DeckEmptyState
import io.codex.s23deck.ui.designsystem.DeckActionButton
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    contentPadding: PaddingValues,
    onRetry: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf<ActivityResult?>(null) }
    val visibleItems = remember(state.items, filter) {
        state.items.filter { filter == null || it.result == filter }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val wide = maxWidth >= 600.dp
        Column(modifier = Modifier.fillMaxSize()) {
            ActivityHeader(
                itemCount = state.items.size,
                visibleCount = visibleItems.size,
                filter = filter,
                clearing = state.clearing,
                onClear = onClear,
            )
            if (state.items.isNotEmpty()) {
                ActivityFilters(selected = filter, onSelect = { filter = it })
            }
            if (visibleItems.isEmpty()) {
                EmptyActivity(filtered = state.items.isNotEmpty())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = if (wide) 32.dp else 16.dp,
                        end = if (wide) 32.dp else 16.dp,
                        bottom = 24.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleItems, key = ActivityItem::id) { item ->
                        ActivityRow(item = item, wide = wide, onRetry = { onRetry(item.actionId) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityHeader(
    itemCount: Int,
    visibleCount: Int,
    filter: ActivityResult?,
    clearing: Boolean,
    onClear: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recent activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = activitySummary(itemCount, visibleCount, filter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (itemCount > 0) {
                IconButton(
                    onClick = onClear,
                    enabled = !clearing,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear activity")
                }
            }
        }
        if (itemCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = filter?.icon() ?: Icons.Outlined.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = filter?.label() ?: "All actions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$visibleCount shown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun activitySummary(itemCount: Int, visibleCount: Int, filter: ActivityResult?): String = when {
    itemCount == 0 -> "No actions run yet"
    filter == null -> "$itemCount total"
    else -> "$visibleCount of $itemCount total"
}

@Composable
private fun ActivityFilters(
    selected: ActivityResult?,
    onSelect: (ActivityResult?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DeckFilterPill(
                label = "All",
                selected = selected == null,
                onClick = { onSelect(null) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        items(ActivityResult.entries, key = ActivityResult::name) { result ->
            DeckFilterPill(
                label = result.label(),
                selected = selected == result,
                onClick = { onSelect(if (selected == result) null else result) },
                icon = if (selected == result) result.icon() else null,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun ActivityRow(
    item: ActivityItem,
    wide: Boolean,
    onRetry: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Surface(
            color = item.result.containerColor(),
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (item.result == ActivityResult.Running) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(
                        imageVector = item.result.icon(),
                        contentDescription = item.result.label(),
                        tint = item.result.contentColor(),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.actionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (wide) {
                    Text(
                        text = item.occurredAt.formattedTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.message.isNotBlank()) {
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!wide) {
                Text(
                    text = item.occurredAt.formattedTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.result != ActivityResult.Running) {
            DeckActionButton(
                label = if (wide) "Run again" else "Run",
                onClick = onRetry,
                icon = Icons.Outlined.Refresh,
                modifier = Modifier.widthIn(min = 96.dp, max = if (wide) 148.dp else 112.dp).heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun EmptyActivity(filtered: Boolean) {
    DeckEmptyState(
        title = if (filtered) "No matching activity" else "No activity yet",
        body = if (filtered) "Try another filter." else "Actions you run will appear here.",
        icon = Icons.Outlined.History,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private val timeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun java.time.Instant.formattedTime(): String =
    timeFormatter.format(atZone(ZoneId.systemDefault()))

private fun ActivityResult.label(): String = when (this) {
    ActivityResult.Running -> "Running"
    ActivityResult.Succeeded -> "Succeeded"
    ActivityResult.Failed -> "Failed"
}

private fun ActivityResult.icon(): ImageVector = when (this) {
    ActivityResult.Running -> Icons.Outlined.Refresh
    ActivityResult.Succeeded -> Icons.Outlined.Check
    ActivityResult.Failed -> Icons.Outlined.Close
}

@Composable
private fun ActivityResult.containerColor(): Color = when (this) {
    ActivityResult.Running -> MaterialTheme.colorScheme.secondaryContainer
    ActivityResult.Succeeded -> MaterialTheme.colorScheme.primaryContainer
    ActivityResult.Failed -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun ActivityResult.contentColor(): Color = when (this) {
    ActivityResult.Running -> MaterialTheme.colorScheme.onSecondaryContainer
    ActivityResult.Succeeded -> MaterialTheme.colorScheme.onPrimaryContainer
    ActivityResult.Failed -> MaterialTheme.colorScheme.onErrorContainer
}
