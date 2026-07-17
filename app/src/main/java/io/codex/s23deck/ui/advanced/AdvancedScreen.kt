package io.codex.s23deck.ui.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckSectionLabel
import java.util.Locale

data class AdvancedDiagnosticsState(
    val connectionReady: Boolean = false,
    val host: String = "Not configured",
    val user: String = "",
    val port: Int = 22,
    val keyReady: Boolean = false,
    val hostKeyPinned: Boolean = false,
    val actionCount: Int = 0,
    val safeActionCount: Int = 0,
    val lastResult: String = "No diagnostics run",
    val isRunning: Boolean = false,
    val pointerSensitivity: Float = 1f,
    val naturalScroll: Boolean = true,
)

@Composable
fun AdvancedScreen(
    state: AdvancedDiagnosticsState,
    actions: List<DeckAction>,
    contentPadding: PaddingValues,
    onOpenConnection: () -> Unit,
    onTestConnection: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onRunSafeActions: () -> Unit,
    onRunAction: (DeckAction) -> Unit,
    onOpenLink: (String) -> Unit,
    onRunShell: (String) -> Unit,
    onPointerSensitivityChange: (Float) -> Unit,
    onNaturalScrollChange: (Boolean) -> Unit,
    onOpenMouse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var linkText by rememberSaveable { mutableStateOf("") }
    var shellText by rememberSaveable { mutableStateOf("") }
    val filteredActions = actions.filter { action ->
        query.isBlank() || listOf(action.label, action.description, action.id)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(contentPadding),
    ) {
        if (maxWidth >= 700.dp) {
            Row(modifier = Modifier.fillMaxSize()) {
                DiagnosticsPane(
                    state = state,
                    onOpenConnection = onOpenConnection,
                    onTestConnection = onTestConnection,
                    onRunDiagnostics = onRunDiagnostics,
                    onRunSafeActions = onRunSafeActions,
                    onPointerSensitivityChange = onPointerSensitivityChange,
                    onNaturalScrollChange = onNaturalScrollChange,
                    onOpenMouse = onOpenMouse,
                    modifier = Modifier.width(380.dp).fillMaxHeight(),
                )
                HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                ToolsPane(
                    state = state,
                    actions = filteredActions,
                    query = query,
                    onQueryChange = { query = it },
                    linkText = linkText,
                    onLinkTextChange = { linkText = it },
                    shellText = shellText,
                    onShellTextChange = { shellText = it },
                    onRunAction = onRunAction,
                    onOpenLink = onOpenLink,
                    onRunShell = onRunShell,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                diagnosticsItems(
                    state = state,
                    onOpenConnection = onOpenConnection,
                    onTestConnection = onTestConnection,
                    onRunDiagnostics = onRunDiagnostics,
                    onRunSafeActions = onRunSafeActions,
                    onPointerSensitivityChange = onPointerSensitivityChange,
                    onNaturalScrollChange = onNaturalScrollChange,
                    onOpenMouse = onOpenMouse,
                )
                toolItems(
                    state = state,
                    actions = filteredActions,
                    query = query,
                    onQueryChange = { query = it },
                    linkText = linkText,
                    onLinkTextChange = { linkText = it },
                    shellText = shellText,
                    onShellTextChange = { shellText = it },
                    onRunAction = onRunAction,
                    onOpenLink = onOpenLink,
                    onRunShell = onRunShell,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsPane(
    state: AdvancedDiagnosticsState,
    onOpenConnection: () -> Unit,
    onTestConnection: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onRunSafeActions: () -> Unit,
    onPointerSensitivityChange: (Float) -> Unit,
    onNaturalScrollChange: (Boolean) -> Unit,
    onOpenMouse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        diagnosticsItems(
            state,
            onOpenConnection,
            onTestConnection,
            onRunDiagnostics,
            onRunSafeActions,
            onPointerSensitivityChange,
            onNaturalScrollChange,
            onOpenMouse,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.diagnosticsItems(
    state: AdvancedDiagnosticsState,
    onOpenConnection: () -> Unit,
    onTestConnection: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onRunSafeActions: () -> Unit,
    onPointerSensitivityChange: (Float) -> Unit,
    onNaturalScrollChange: (Boolean) -> Unit,
    onOpenMouse: () -> Unit,
) {
    item { SectionTitle("Diagnostics") }
    item {
        ListItem(
            headlineContent = { Text(if (state.connectionReady) "Mac ready" else "Mac setup required") },
            supportingContent = {
                Text(
                    if (state.connectionReady) "${state.user}@${state.host}:${state.port}" else state.host,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Icon(
                    if (state.connectionReady) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (state.connectionReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
            trailingContent = {
                Text(if (state.connectionReady) "Ready" else "Setup", color = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable(onClick = onOpenConnection).heightIn(min = 72.dp),
        )
    }
    item { InsetDivider() }
    item {
        StatusRow(
            icon = Icons.Outlined.Security,
            title = "Connection identity",
            summary = if (state.keyReady) "Phone key generated" else "Phone key missing",
            value = if (state.keyReady) "Ready" else "Check",
        )
    }
    item {
        StatusRow(
            icon = Icons.Outlined.Memory,
            title = "Host verification",
            summary = if (state.hostKeyPinned) "Host key pinned" else "Host key not pinned",
            value = if (state.hostKeyPinned) "Pinned" else "Check",
        )
    }
    item {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DeckActionButton(
                    label = "Test",
                    onClick = onTestConnection,
                    enabled = !state.isRunning,
                    icon = Icons.Outlined.Refresh,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
                DeckActionButton(
                    label = if (state.isRunning) "Running" else "Full check",
                    onClick = onRunDiagnostics,
                    enabled = !state.isRunning,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
            }
            Text(
                text = state.lastResult,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    item { InsetDivider(start = 16.dp, end = 16.dp) }
    item { SectionTitle("Controls") }
    item {
        ListItem(
            headlineContent = { Text("Registered actions") },
            supportingContent = { Text("${state.actionCount} total, ${state.safeActionCount} safe") },
            leadingContent = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
            trailingContent = {
                DeckActionButton(
                    label = "Run safe",
                    onClick = onRunSafeActions,
                    enabled = state.connectionReady && !state.isRunning,
                    modifier = Modifier.widthIn(min = 132.dp, max = 172.dp).heightIn(min = 48.dp),
                )
            },
        )
    }
    item {
        ListItem(
            headlineContent = { Text("Pointer sensitivity") },
            supportingContent = { Text(String.format(Locale.US, "%.2fx", state.pointerSensitivity)) },
            leadingContent = { Icon(Icons.Outlined.Mouse, contentDescription = null) },
            trailingContent = {
                Row {
                    DeckActionButton(
                        label = "-",
                        onClick = { onPointerSensitivityChange((state.pointerSensitivity - 0.25f).coerceAtLeast(0.5f)) },
                        modifier = Modifier.width(56.dp).heightIn(min = 48.dp),
                    )
                    DeckActionButton(
                        label = "+",
                        onClick = { onPointerSensitivityChange((state.pointerSensitivity + 0.25f).coerceAtMost(3f)) },
                        modifier = Modifier.padding(start = 8.dp).width(56.dp).heightIn(min = 48.dp),
                    )
                }
            },
            modifier = Modifier.clickable(onClick = onOpenMouse),
        )
    }
    item {
        ListItem(
            headlineContent = { Text("Natural scroll") },
            supportingContent = { Text("Match macOS scrolling direction") },
            leadingContent = { Icon(Icons.Outlined.Mouse, contentDescription = null) },
            trailingContent = {
                Switch(checked = state.naturalScroll, onCheckedChange = onNaturalScrollChange)
            },
        )
    }
}

@Composable
private fun ToolsPane(
    state: AdvancedDiagnosticsState,
    actions: List<DeckAction>,
    query: String,
    onQueryChange: (String) -> Unit,
    linkText: String,
    onLinkTextChange: (String) -> Unit,
    shellText: String,
    onShellTextChange: (String) -> Unit,
    onRunAction: (DeckAction) -> Unit,
    onOpenLink: (String) -> Unit,
    onRunShell: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        toolItems(
            state,
            actions,
            query,
            onQueryChange,
            linkText,
            onLinkTextChange,
            shellText,
            onShellTextChange,
            onRunAction,
            onOpenLink,
            onRunShell,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.toolItems(
    state: AdvancedDiagnosticsState,
    actions: List<DeckAction>,
    query: String,
    onQueryChange: (String) -> Unit,
    linkText: String,
    onLinkTextChange: (String) -> Unit,
    shellText: String,
    onShellTextChange: (String) -> Unit,
    onRunAction: (DeckAction) -> Unit,
    onOpenLink: (String) -> Unit,
    onRunShell: (String) -> Unit,
) {
    item { SectionTitle("Action registry") }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text("Search actions") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    itemsIndexed(actions, key = { _, action -> action.id }) { index, action ->
        ListItem(
            headlineContent = { Text(action.label) },
            supportingContent = {
                Text(action.description.ifBlank { action.id }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
            trailingContent = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
            modifier = Modifier.clickable(enabled = state.connectionReady && !state.isRunning) {
                onRunAction(action)
            }.heightIn(min = 64.dp),
        )
        if (index < actions.lastIndex) InsetDivider()
    }
    item { InsetDivider(start = 16.dp, end = 16.dp) }
    item { SectionTitle("Open link") }
    item {
        CommandField(
            value = linkText,
            onValueChange = onLinkTextChange,
            label = "https://",
            icon = Icons.Outlined.Link,
            primaryLabel = "Open",
            primaryEnabled = linkText.isNotBlank() && state.connectionReady && !state.isRunning,
            onPrimary = { onOpenLink(linkText.trim()) },
        )
    }
    item { SectionTitle("Shell") }
    item {
        CommandField(
            value = shellText,
            onValueChange = onShellTextChange,
            label = "Command",
            icon = Icons.Outlined.Terminal,
            primaryLabel = "Run",
            primaryEnabled = shellText.isNotBlank() && state.connectionReady && !state.isRunning,
            onPrimary = { onRunShell(shellText) },
        )
    }
}

@Composable
private fun CommandField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    secondaryEnabled: Boolean = false,
    onSecondary: () -> Unit = {},
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (secondaryLabel != null) {
                DeckActionButton(
                    label = secondaryLabel,
                    onClick = onSecondary,
                    enabled = secondaryEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
            }
            DeckActionButton(
                label = primaryLabel,
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun InsetDivider(start: androidx.compose.ui.unit.Dp = 72.dp, end: androidx.compose.ui.unit.Dp = 16.dp) {
    HorizontalDivider(modifier = Modifier.padding(start = start, end = end))
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    value: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun SectionTitle(text: String) {
    DeckSectionLabel(text)
}
