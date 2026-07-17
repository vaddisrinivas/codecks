package io.codex.s23deck.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.LastPage
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.codex.s23deck.HidCommand
import io.codex.s23deck.HidState
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckFilterPill
import io.codex.s23deck.ui.home.CustomActionRow

private data class KeyControl(
    val label: String,
    val command: HidCommand,
    val icon: ImageVector? = null,
)

private enum class KeyboardMode(val label: String) {
    Type("Type"),
    Edit("Edit"),
    Mac("Mac"),
}

private val editControls = listOf(
    KeyControl("Copy", HidCommand.Copy, Icons.Outlined.ContentCopy),
    KeyControl("Paste", HidCommand.Paste, Icons.Outlined.ContentPaste),
    KeyControl("Cut", HidCommand.Cut, Icons.Outlined.ContentCut),
    KeyControl("Select all", HidCommand.SelectAll, Icons.Outlined.SelectAll),
    KeyControl("Undo", HidCommand.Undo, Icons.AutoMirrored.Outlined.Undo),
    KeyControl("Redo", HidCommand.Redo, Icons.AutoMirrored.Outlined.Redo),
    KeyControl("Find", HidCommand.Find, Icons.Outlined.Search),
    KeyControl("Save", HidCommand.Save, Icons.Outlined.Save),
    KeyControl("New", HidCommand.NewDocument, Icons.AutoMirrored.Outlined.NoteAdd),
    KeyControl("Open", HidCommand.OpenDocument),
    KeyControl("Close", HidCommand.CloseWindow),
)

private val typeControls = listOf(
    KeyControl("Enter", HidCommand.Enter, Icons.AutoMirrored.Outlined.KeyboardReturn),
    KeyControl("Tab", HidCommand.Tab, Icons.Outlined.Tab),
    KeyControl("Escape", HidCommand.Escape),
    KeyControl("Backspace", HidCommand.Backspace, Icons.AutoMirrored.Outlined.Backspace),
    KeyControl("Delete", HidCommand.ForwardDelete, Icons.Outlined.Delete),
)

private val textNavigationControls = listOf(
    KeyControl("Line start", HidCommand.LineStart, Icons.Outlined.FirstPage),
    KeyControl("Line end", HidCommand.LineEnd, Icons.AutoMirrored.Outlined.LastPage),
    KeyControl("Word left", HidCommand.WordLeft),
    KeyControl("Word right", HidCommand.WordRight),
)

private val macControls = listOf(
    KeyControl("Spotlight", HidCommand.Spotlight, Icons.Outlined.Search),
    KeyControl("Mission", HidCommand.MissionControl, Icons.Outlined.LaptopMac),
    KeyControl("Expose", HidCommand.AppExpose),
    KeyControl("Launchpad", HidCommand.Launchpad, Icons.Outlined.Apps),
    KeyControl("Desktop", HidCommand.ShowDesktop),
    KeyControl("Notify", HidCommand.NotificationCenter),
    KeyControl("App switch", HidCommand.AppSwitcher),
    KeyControl("Window", HidCommand.WindowSwitcher),
    KeyControl("Shot area", HidCommand.ScreenshotArea),
    KeyControl("Shot window", HidCommand.ScreenshotWindow),
    KeyControl("Space left", HidCommand.SpaceLeft),
    KeyControl("Space right", HidCommand.SpaceRight),
    KeyControl("Previous", HidCommand.MediaPrevious),
    KeyControl("Play / pause", HidCommand.MediaPlayPause, Icons.Outlined.MusicNote),
    KeyControl("Next", HidCommand.MediaNext),
    KeyControl("Volume down", HidCommand.MediaVolumeDown),
    KeyControl("Mute", HidCommand.MediaMute),
    KeyControl("Volume up", HidCommand.MediaVolumeUp),
)

@Composable
fun KeyboardScreen(
    state: HidState,
    text: String,
    contentPadding: PaddingValues,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onRefreshHosts: () -> Unit,
    onConnect: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onTypeText: () -> Unit,
    onClearText: () -> Unit,
    onCommand: (HidCommand) -> Unit,
    customActions: List<DeckAction> = emptyList(),
    onCustomAction: (DeckAction) -> Unit = {},
    selectedActionId: String? = null,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(KeyboardMode.Type) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    HidHostHeader(
                        title = "Keyboard targets",
                        disconnectedTitle = "Keyboard ready",
                        connectedTitle = "Keyboard connected",
                        icon = Icons.Outlined.Keyboard,
                        state = state,
                        permissionGranted = permissionGranted,
                        onRequestPermission = onRequestPermission,
                        onStart = onStart,
                        onRefreshHosts = onRefreshHosts,
                        onConnect = onConnect,
                    )
                    Composer(text, state.isConnected, onTextChange, onTypeText, onClearText)
                    CustomActionRow(
                        actions = customActions.take(4),
                        onAction = onCustomAction,
                        selectedActionId = selectedActionId,
                        contentPadding = PaddingValues(end = 16.dp),
                    )
                }
                KeyboardQuickPanel(
                    enabled = state.isConnected,
                    onCommand = onCommand,
                    onMore = { controlsOpen = true },
                    modifier = Modifier.widthIn(min = 300.dp, max = 360.dp),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                HidHostHeader(
                    title = "Keyboard targets",
                    disconnectedTitle = "Keyboard ready",
                    connectedTitle = "Keyboard connected",
                    icon = Icons.Outlined.Keyboard,
                    state = state,
                    permissionGranted = permissionGranted,
                    onRequestPermission = onRequestPermission,
                    onStart = onStart,
                    onRefreshHosts = onRefreshHosts,
                    onConnect = onConnect,
                )
                Composer(text, state.isConnected, onTextChange, onTypeText, onClearText)
                CustomActionRow(
                    actions = customActions.take(8),
                    onAction = onCustomAction,
                    selectedActionId = selectedActionId,
                    contentPadding = PaddingValues(end = 16.dp),
                )
                KeyboardQuickPanel(state.isConnected, onCommand, onMore = { controlsOpen = true })
            }
        }
    }
    if (controlsOpen) {
        AlertDialog(
            onDismissRequest = { controlsOpen = false },
            title = { Text("Keyboard controls") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    ModeSelector(mode, onModeChange = { mode = it })
                    KeyboardCommandContent(mode, state.isConnected, 2, onCommand)
                }
            },
            confirmButton = {
                TextButton(onClick = { controlsOpen = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun KeyboardQuickPanel(
    enabled: Boolean,
    onCommand: (HidCommand) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        listOf(
            listOf(
                KeyControl("Enter", HidCommand.Enter, Icons.AutoMirrored.Outlined.KeyboardReturn),
                KeyControl("Esc", HidCommand.Escape),
            ),
            listOf(
                KeyControl("Tab", HidCommand.Tab, Icons.Outlined.Tab),
                KeyControl("Backspace", HidCommand.Backspace, Icons.AutoMirrored.Outlined.Backspace),
            ),
        ).forEach { rowControls ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowControls.forEach { control ->
                    DeckActionButton(
                        label = control.label,
                        onClick = { onCommand(control.command) },
                        enabled = enabled,
                        icon = control.icon,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                KeyControl("Mission", HidCommand.MissionControl, Icons.Outlined.LaptopMac),
                KeyControl("Spotlight", HidCommand.Spotlight, Icons.Outlined.Search),
            ).forEach { control ->
                DeckActionButton(
                    label = control.label,
                    onClick = { onCommand(control.command) },
                    enabled = enabled,
                    icon = control.icon,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
            }
        }
        DeckActionButton(
            label = "More controls",
            onClick = onMore,
            icon = Icons.Outlined.Keyboard,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
    }
}

@Composable
private fun ModeSelector(
    selected: KeyboardMode,
    onModeChange: (KeyboardMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        KeyboardMode.entries.forEach { mode ->
            DeckFilterPill(
                label = mode.label,
                selected = selected == mode,
                onClick = { onModeChange(mode) },
            )
        }
    }
}

@Composable
private fun KeyboardCommandContent(
    mode: KeyboardMode,
    enabled: Boolean,
    columns: Int,
    onCommand: (HidCommand) -> Unit,
) {
    when (mode) {
        KeyboardMode.Type -> CommandSection("Type keys", typeControls, columns, enabled, onCommand)
        KeyboardMode.Edit -> {
            CommandSection("Edit", editControls, columns, enabled, onCommand)
            CommandSection("Text navigation", textNavigationControls, columns, enabled, onCommand)
        }
        KeyboardMode.Mac -> CommandSection("Mac", macControls, columns, enabled, onCommand)
    }
}

@Composable
private fun Composer(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onTypeText: () -> Unit,
    onClearText: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Text to type on Mac") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckActionButton(
                    label = "Type",
                    onClick = onTypeText,
                    enabled = enabled && text.isNotBlank(),
                    icon = Icons.AutoMirrored.Outlined.Send,
                    modifier = Modifier.weight(1f).height(56.dp),
                )
                DeckActionButton(
                    label = "Clear",
                    onClick = onClearText,
                    enabled = text.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(56.dp),
                )
            }
        }
    }
}

@Composable
private fun CommandSection(
    title: String,
    controls: List<KeyControl>,
    columns: Int,
    enabled: Boolean,
    onCommand: (HidCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        controls.chunked(columns).forEach { rowControls ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowControls.forEach { control ->
                    DeckActionButton(
                        label = control.label,
                        onClick = { onCommand(control.command) },
                        enabled = enabled,
                        icon = control.icon,
                        modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    )
                }
                repeat(columns - rowControls.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
