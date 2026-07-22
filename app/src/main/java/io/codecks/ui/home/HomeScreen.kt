package io.codecks.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.domain.ActionKind
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckTemplate
import io.codecks.ui.designsystem.DeckComponentState
import io.codecks.ui.designsystem.DeckControlTile
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.ConnectionHealthKind
import io.codecks.ui.connection.isReady
import io.codecks.ui.connection.simpleConnectionHealth
import io.codecks.ui.icons.deckImageVector
import io.codecks.ui.icons.imageVector
import io.codecks.ui.theme.CodecksDeckStyle

@Composable
fun HomeScreen(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onAction: (DeckAction) -> Unit,
    connectionHealth: ConnectionHealth = simpleConnectionHealth(state.connectionReady),
    onOpenSettings: () -> Unit = {},
    onOpenConnection: () -> Unit = {},
    onEditDeck: () -> Unit = {},
    onOpenPalette: () -> Unit = {},
    onEditSlot: (Int) -> Unit = { onEditDeck() },
    visibleSlotIndices: List<Int> = if (state.deckLayout.slots.isEmpty()) {
        state.actions.indices.toList()
    } else {
        state.deckLayout.slots.indices.toList()
    },
    onTestAction: (DeckAction) -> Unit = {},
    onDuplicateAction: (DeckAction) -> Unit = {},
    onRemoveAction: (DeckAction) -> Unit = {},
    onOpenRunLog: (String?) -> Unit = {},
    smartSuggestions: List<SmartDeckSuggestionUi> = emptyList(),
    onRunSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onPinSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onHideSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onExplainSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onNeverSmartSuggestionForApp: (SmartDeckSuggestionUi) -> Unit = {},
    onRemoveSlot: (Int) -> Unit = { slot ->
        state.deckLayout.slots.getOrNull(slot)?.action?.let(onRemoveAction)
    },
    focusedActionId: String? = null,
    deckStyle: CodecksDeckStyle = CodecksDeckStyle.StreamDeckPro,
    modifier: Modifier = Modifier,
) {
    val renderLayout = remember(state.deckLayout, state.actions) {
        state.deckLayout.takeIf { it.slots.isNotEmpty() } ?: DeckLayout.fromActions(state.actions)
    }
    val deckActionSlots = remember(renderLayout, visibleSlotIndices) {
        buildHomeDeckSlots(renderLayout, visibleSlotIndices)
    }
        .filterNot { it.action.id in bottomNavShortcutIds }
    val runningActionId = (state.actionStatus as? ActionStatus.Running)?.actionId
    val currentResult = state.actionStatus.takeIf { it !is ActionStatus.Idle }
    var optionsSlot by remember { mutableStateOf<HomeDeckSlot?>(null) }
    CodecksKeybedDeck(
        activeDeckLabel = activeTemplateTitle(state.activeTemplateId, state.deckTemplates),
        activeApp = state.activeMacApp,
        connectionHealth = connectionHealth,
        slots = deckActionSlots,
        runningActionId = runningActionId,
        currentResult = currentResult,
        focusedActionId = focusedActionId,
        onAction = onAction,
        onEditSlot = onEditSlot,
        onOpenSettings = onOpenSettings,
        onOpenConnection = onOpenConnection,
        onEditDeck = onEditDeck,
        onOpenPalette = onOpenPalette,
        smartSuggestions = smartSuggestions,
        onRunSmartSuggestion = onRunSmartSuggestion,
        onPinSmartSuggestion = onPinSmartSuggestion,
        onHideSmartSuggestion = onHideSmartSuggestion,
        onExplainSmartSuggestion = onExplainSmartSuggestion,
        onNeverSmartSuggestionForApp = onNeverSmartSuggestionForApp,
        onOpenOptions = { slot ->
            if (shouldShowActionOptions(slot.action, locked = false)) optionsSlot = slot
        },
        deckStyle = deckStyle,
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    )
    optionsSlot?.let { slot ->
        val action = slot.action
        ActionOptionsDialog(
            action = action,
            onDismiss = { optionsSlot = null },
            onRun = {
                optionsSlot = null
                onAction(action)
            },
            onTest = {
                optionsSlot = null
                onTestAction(action)
            },
            onEdit = {
                optionsSlot = null
                onEditSlot(slot.slot)
            },
            onDuplicate = {
                optionsSlot = null
                onDuplicateAction(action)
            },
            onDelete = {
                optionsSlot = null
                onRemoveSlot(slot.slot)
            },
            onViewLog = {
                optionsSlot = null
                onOpenRunLog(action.id)
            },
        )
    }
}

@Composable
private fun CodecksKeybedDeck(
    activeDeckLabel: String,
    activeApp: String?,
    connectionHealth: ConnectionHealth,
    slots: List<HomeDeckSlot>,
    runningActionId: String?,
    currentResult: ActionStatus?,
    focusedActionId: String?,
    onAction: (DeckAction) -> Unit,
    onEditSlot: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConnection: () -> Unit,
    onEditDeck: () -> Unit,
    onOpenPalette: () -> Unit,
    smartSuggestions: List<SmartDeckSuggestionUi>,
    onRunSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onPinSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onHideSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onExplainSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onNeverSmartSuggestionForApp: (SmartDeckSuggestionUi) -> Unit,
    onOpenOptions: (HomeDeckSlot) -> Unit,
    deckStyle: CodecksDeckStyle,
    modifier: Modifier = Modifier,
) {
    val rows = remember(slots) { packHomeDeckRows(slots, columns = 4) }
    val connectionReady = connectionHealth.isReady
    val deckCanvasColor = MaterialTheme.colorScheme.background
    val deckTextColor = MaterialTheme.colorScheme.onBackground
    val connectionTone = connectionToneColor(connectionHealth.kind)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(deckCanvasColor)
    ) {
        val framePadding = when {
            maxWidth >= 900.dp -> 14.dp
            maxWidth >= 600.dp -> 10.dp
            else -> 4.dp
        }
        val headerHeight = if (maxHeight < 640.dp) 36.dp else 42.dp
        val suggestionHeight = if (smartSuggestions.isNotEmpty()) 104.dp else 0.dp
        val gapX = when {
            maxWidth >= 900.dp -> 12.dp
            maxWidth >= 600.dp -> 10.dp
            else -> 6.dp
        }
        val usableWidth = (maxWidth - framePadding * 2f).coerceAtLeast(280.dp)
        val usableHeight = (maxHeight - framePadding * 2f - headerHeight - suggestionHeight - 8.dp).coerceAtLeast(280.dp)
        val keyWidth = ((usableWidth - gapX * 3f) / 4f).coerceAtLeast(68.dp)
        val gapY = when {
            maxHeight >= 900.dp -> 12.dp
            maxHeight >= 640.dp -> 8.dp
            else -> 5.dp
        }
        val rowCount = rows.size.coerceAtLeast(1)
        val keyHeight = ((usableHeight - gapY * (rowCount - 1).toFloat()) / rowCount.toFloat()).coerceAtLeast(52.dp)
        val keybedWidth = keyWidth * 4f + gapX * 3f
        val showKeyLabels = keyHeight >= 76.dp
        val statusText = when {
            !connectionReady -> connectionHealth.title
            activeApp.isNullOrBlank() -> "Mac connected"
            else -> activeApp
        }

        CodecksDeckEdgeGlowBackground(glowColor = MaterialTheme.colorScheme.primary, canvasColor = deckCanvasColor)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(framePadding),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(keybedWidth)
                    .height(headerHeight),
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = deckTextColor)
                }
                IconButton(onClick = onOpenPalette) {
                    Icon(Icons.Outlined.Search, contentDescription = "Command palette", tint = deckTextColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeDeckLabel,
                        color = deckTextColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText,
                        color = deckTextColor.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onEditDeck) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit deck", tint = deckTextColor.copy(alpha = 0.82f))
                }
                Surface(
                    onClick = onOpenConnection,
                    color = connectionTone.copy(alpha = if (connectionReady) 0.18f else 0.12f),
                    contentColor = connectionTone,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, connectionTone.copy(alpha = 0.42f)),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Icon(
                            imageVector = if (connectionReady) Icons.Outlined.CheckCircle else Icons.Outlined.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(text = connectionHealth.deckLabel(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            SmartSuggestionRow(
                suggestions = smartSuggestions,
                onRun = onRunSmartSuggestion,
                onPin = onPinSmartSuggestion,
                onHide = onHideSmartSuggestion,
                onWhy = onExplainSmartSuggestion,
                onNeverForApp = onNeverSmartSuggestionForApp,
                modifier = Modifier.width(keybedWidth),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(gapY),
                modifier = Modifier.width(keybedWidth),
            ) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gapX)) {
                        row.forEach { slot ->
                            val action = slot.action
                            val openSlot = action.id == "add_button" || action.id == "blank"
                            val running = runningActionId == action.id
                            val selected = focusedActionId == action.id
                            val enabled = openSlot || isDeckActionEnabled(action, connectionReady)
                            DeckControlTile(
                                label = if (openSlot) "Tap to assign" else action.label,
                                icon = action.deckImageVector(),
                                state = when {
                                    running -> DeckComponentState.Running
                                    selected -> DeckComponentState.Selected
                                    !openSlot && currentResult is ActionStatus.Succeeded && currentResult.actionId == action.id -> DeckComponentState.Succeeded
                                    !openSlot && currentResult is ActionStatus.Failed && currentResult.actionId == action.id -> DeckComponentState.Failure
                                    !enabled -> DeckComponentState.Disabled
                                    else -> DeckComponentState.Idle
                                },
                                enabled = enabled,
                                danger = action.dangerous,
                                showLabel = showKeyLabels,
                                deckStyle = deckStyle,
                                onClick = {
                                    if (openSlot) onEditSlot(slot.slot) else onAction(action)
                                },
                                onLongClick = if (openSlot) null else ({ onOpenOptions(slot) }),
                                modifier = Modifier
                                    .width(keyWidth * slot.columnSpan.toFloat() + gapX * (slot.columnSpan - 1).toFloat())
                                    .heightIn(min = keyHeight, max = keyHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun connectionToneColor(kind: ConnectionHealthKind): Color =
    when (kind) {
        ConnectionHealthKind.Ready -> MaterialTheme.colorScheme.tertiary
        ConnectionHealthKind.Scanning,
        ConnectionHealthKind.Verifying,
        ConnectionHealthKind.Connecting,
        ConnectionHealthKind.Testing -> MaterialTheme.colorScheme.primary
        ConnectionHealthKind.NeedsFingerprint,
        ConnectionHealthKind.NeedsKey,
        ConnectionHealthKind.NotConfigured -> MaterialTheme.colorScheme.secondary
        ConnectionHealthKind.AuthFailed,
        ConnectionHealthKind.FingerprintMismatch,
        ConnectionHealthKind.Offline -> MaterialTheme.colorScheme.error
    }

private fun ConnectionHealth.deckLabel(): String =
    when (kind) {
        ConnectionHealthKind.Ready -> "Ready"
        ConnectionHealthKind.Scanning -> "Scan"
        ConnectionHealthKind.Verifying -> "Verify"
        ConnectionHealthKind.Connecting -> "Pair"
        ConnectionHealthKind.Testing -> "Test"
        ConnectionHealthKind.NeedsFingerprint -> "Setup"
        ConnectionHealthKind.NeedsKey -> "Setup"
        ConnectionHealthKind.AuthFailed -> "Auth"
        ConnectionHealthKind.FingerprintMismatch -> "Trust"
        ConnectionHealthKind.Offline -> "Retry"
        ConnectionHealthKind.NotConfigured -> "Setup"
    }

@Composable
private fun SmartSuggestionRow(
    suggestions: List<SmartDeckSuggestionUi>,
    onRun: (SmartDeckSuggestionUi) -> Unit,
    onPin: (SmartDeckSuggestionUi) -> Unit,
    onHide: (SmartDeckSuggestionUi) -> Unit,
    onWhy: (SmartDeckSuggestionUi) -> Unit,
    onNeverForApp: (SmartDeckSuggestionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = modifier.height(104.dp),
    ) {
        items(suggestions, key = SmartDeckSuggestionUi::candidateId) { suggestion ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "${suggestion.confidence}: ${suggestion.action.label}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = suggestion.reason,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onRun(suggestion) }) { Text("Run") }
                        TextButton(onClick = { onPin(suggestion) }) { Text("Pin") }
                        TextButton(onClick = { onHide(suggestion) }) { Text("Hide") }
                        TextButton(onClick = { onWhy(suggestion) }) { Text("Why") }
                        TextButton(onClick = { onNeverForApp(suggestion) }) { Text("Never") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeDeckLayout(
    state: HomeUiState,
    customActionSlots: List<HomeDeckSlot>,
    gridActionSlots: List<HomeDeckSlot>,
    runningActionId: String?,
    focusedActionId: String?,
    onAction: (DeckAction) -> Unit,
    onEditSlot: (Int) -> Unit,
    onTemplateSelected: (String) -> Unit,
    onRefreshContext: () -> Unit,
    onDynamicDeckChange: (Boolean) -> Unit,
    locked: Boolean,
    onLongClick: (HomeDeckSlot) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.width(204.dp).fillMaxSize(),
        ) {
            item {
                Text("Dynamic", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                DeckFilterPill(
                    label = "Custom",
                    selected = state.activeTemplateId == CUSTOM_TEMPLATE_ID,
                    onClick = { onTemplateSelected(CUSTOM_TEMPLATE_ID) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
            items(state.deckTemplates, key = DeckTemplate::id) { template ->
                DeckFilterPill(
                    label = template.title,
                    selected = state.activeTemplateId == template.id,
                    onClick = { onTemplateSelected(template.id) },
                    icon = template.icon.imageVector(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.dynamicDeckEnabled,
                        onCheckedChange = onDynamicDeckChange,
                        enabled = state.connectionReady,
                    )
                }
            }
            item {
                DeckFilterPill(
                    label = state.activeMacApp ?: "Refresh app",
                    selected = false,
                    onClick = onRefreshContext,
                    icon = Icons.Outlined.Refresh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.width(184.dp).fillMaxSize(),
        ) {
            item { Text("Custom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(customActionSlots, key = { "landscape-custom-${it.slot}-${it.action.id}" }) { slot ->
                val action = slot.action
                ActionCard(
                    action = action,
                    running = runningActionId == action.id,
                    selected = action.id == focusedActionId,
                    enabled = isDeckActionEnabled(action, state.connectionReady),
                    onClick = { onAction(action) },
                    onLongClick = { if (shouldShowActionOptions(action, locked)) onLongClick(slot) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 116.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text("Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(gridActionSlots, key = { "landscape-control-${it.slot}-${it.action.id}" }) { slot ->
                val action = slot.action
                ActionCard(
                    action = action,
                    running = runningActionId == action.id,
                    selected = action.id == focusedActionId,
                    enabled = isDeckActionEnabled(action, state.connectionReady),
                    onClick = {
                        if (action.id == "add_button" || action.id == "blank") {
                            onEditSlot(slot.slot)
                        } else {
                            onAction(action)
                        }
                    },
                    onLongClick = { if (shouldShowActionOptions(action, locked)) onLongClick(slot) },
                )
            }
        }
    }
}

@Composable
private fun DeckHero(
    activeApp: String?,
    activeTemplateId: String,
    templates: List<DeckTemplate>,
    dynamicDeckEnabled: Boolean,
    connectionReady: Boolean,
    locked: Boolean,
    viewMode: DeckViewMode,
    onTemplateSelected: (String) -> Unit,
    onRefreshContext: () -> Unit,
    onDynamicDeckChange: (Boolean) -> Unit,
    onLockChange: (Boolean) -> Unit,
    onViewModeChange: (DeckViewMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            color = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Codecks",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            !connectionReady -> "${activeTemplateTitle(activeTemplateId, templates)} • Mac not connected"
                            activeApp.isNullOrBlank() -> "${activeTemplateTitle(activeTemplateId, templates)} • Ready"
                            else -> "${activeTemplateTitle(activeTemplateId, templates)} • $activeApp"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRefreshContext, enabled = connectionReady) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh active Mac app")
                }
                IconButton(onClick = { onLockChange(!locked) }) {
                    Icon(Icons.Outlined.Lock, contentDescription = if (locked) "Unlock deck editing" else "Lock deck editing")
                }
            }
        }
        if (!connectionReady) {
            DeckConnectionHint()
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            item {
                DeckFilterPill(
                    label = "Custom",
                    selected = activeTemplateId == CUSTOM_TEMPLATE_ID,
                    onClick = { onTemplateSelected(CUSTOM_TEMPLATE_ID) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            items(templates, key = DeckTemplate::id) { template ->
                DeckFilterPill(
                    label = template.title,
                    selected = activeTemplateId == template.id,
                    onClick = { onTemplateSelected(template.id) },
                    icon = template.icon.imageVector(),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun DeckGridHeader(
    viewMode: DeckViewMode,
    pageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (viewMode == DeckViewMode.Pages) "Controls · Page ${pageIndex + 1}/$pageCount" else "Controls",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
        if (viewMode == DeckViewMode.Pages && pageCount > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                items(pageCount, key = { "deck-page-$it" }) { index ->
                    DeckFilterPill(
                        label = "${index + 1}",
                        selected = index == pageIndex,
                        onClick = { onPageSelected(index) },
                        modifier = Modifier.width(56.dp).heightIn(min = 44.dp),
                    )
                }
            }
        }
    }
}

private fun activeTemplateTitle(activeTemplateId: String, templates: List<DeckTemplate>): String =
    if (activeTemplateId == CUSTOM_TEMPLATE_ID) {
        "Deck"
    } else {
        templates.firstOrNull { it.id == activeTemplateId }?.title?.let { "$it Deck" } ?: "Deck"
    }

@Composable
fun CustomActionRow(
    actions: List<DeckAction>,
    onAction: (DeckAction) -> Unit,
    modifier: Modifier = Modifier,
    selectedActionId: String? = null,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 32.dp),
    isActionEnabled: (DeckAction) -> Boolean = { true },
) {
    if (actions.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
        modifier = modifier.fillMaxWidth(),
    ) {
        items(actions, key = { "custom-row-${it.id}" }) { action ->
            val selected = action.id == selectedActionId
            val enabled = isActionEnabled(action)
            DeckControlTile(
                label = action.label,
                icon = action.icon.imageVector(),
                state = if (selected) DeckComponentState.Selected else DeckComponentState.Idle,
                enabled = enabled,
                danger = action.dangerous,
                onClick = { onAction(action) },
                modifier = Modifier.size(width = 112.dp, height = 96.dp),
            )
        }
    }
}

@Composable
private fun ActionCard(
    action: DeckAction,
    running: Boolean,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    height: androidx.compose.ui.unit.Dp = 108.dp,
) {
    Box {
        DeckControlTile(
            label = action.label,
            icon = action.icon.imageVector(),
            state = when {
                running -> DeckComponentState.Running
                selected -> DeckComponentState.Selected
                !enabled -> DeckComponentState.Disabled
                else -> DeckComponentState.Idle
            },
            enabled = enabled,
            danger = action.dangerous,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}

private enum class DeckViewMode(val label: String) {
    Scroll("Scroll"),
    Pages("Pages"),
}

internal data class HomeDeckSlot(
    val slot: Int,
    val action: DeckAction,
    val id: String = "slot-${slot + 1}",
    val columnSpan: Int = 1,
)

data class SmartDeckSuggestionUi(
    val candidateId: String,
    val action: DeckAction,
    val reason: String,
    val confidence: String,
)

private val bottomNavShortcutIds = setOf("trackpad", "keyboard", "clipboard", "automations", "settings_shortcut")

internal fun buildHomeDeckSlots(
    layout: DeckLayout,
    visibleSlotIndices: List<Int>,
): List<HomeDeckSlot> =
    visibleSlotIndices.mapNotNull { index ->
        layout.slots.getOrNull(index)?.let { slot ->
            HomeDeckSlot(
                slot = index,
                action = slot.action,
                id = slot.id,
                columnSpan = slot.columnSpan.coerceIn(1, layout.columns),
            )
        }
    }

internal fun buildHomeDeckSlots(
    actions: List<DeckAction>,
    visibleSlotIndices: List<Int>,
): List<HomeDeckSlot> =
    actions.mapIndexed { index, action ->
        HomeDeckSlot(
            slot = visibleSlotIndices.getOrNull(index) ?: index,
            action = action,
        )
    }

internal fun packHomeDeckRows(slots: List<HomeDeckSlot>, columns: Int): List<List<HomeDeckSlot>> {
    val safeColumns = columns.coerceAtLeast(1)
    val rows = mutableListOf<MutableList<HomeDeckSlot>>()
    var row = mutableListOf<HomeDeckSlot>()
    var usedColumns = 0
    slots.forEach { original ->
        val slot = original.copy(columnSpan = original.columnSpan.coerceIn(1, safeColumns))
        if (row.isNotEmpty() && usedColumns + slot.columnSpan > safeColumns) {
            rows += row
            row = mutableListOf()
            usedColumns = 0
        }
        row += slot
        usedColumns += slot.columnSpan
        if (usedColumns == safeColumns) {
            rows += row
            row = mutableListOf()
            usedColumns = 0
        }
    }
    if (row.isNotEmpty()) rows += row
    return rows
}

internal fun shouldShowActionOptions(action: DeckAction, locked: Boolean): Boolean =
    !locked && action.id !in setOf("add_button", "blank")

private fun isDeckActionEnabled(action: DeckAction, connectionReady: Boolean): Boolean =
    action.kind != ActionKind.Ssh ||
        action.id in setOf("add_button", "blank") ||
        (connectionReady && (!action.requiresTest || action.liveSafe))

@Composable
private fun DeckConnectionHint() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Outlined.Computer, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mac controls are locked", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Setup and local buttons still work. Connect your Mac to unlock Mac buttons.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActionOptionsDialog(
    action: DeckAction,
    onDismiss: () -> Unit,
    onRun: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onViewLog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.label) },
        text = {
            Text(
                action.description.ifBlank { "Deck control" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onRun) { Text("Run") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onViewLog) { Text("Log") }
                TextButton(onClick = onTest) { Text("Test") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDuplicate) { Text("Duplicate") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        },
    )
}
