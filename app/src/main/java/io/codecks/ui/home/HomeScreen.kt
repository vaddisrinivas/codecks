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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.domain.ActionStatus
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.domain.DeckAction
import io.codecks.domain.deck.DeckLayout
import io.codecks.ui.home.smart.SmartDeckSuggestionUi
import io.codecks.ui.designsystem.DeckComponentState
import io.codecks.ui.designsystem.DeckControlTile
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.ConnectionHealthKind
import io.codecks.ui.connection.isReady
import io.codecks.ui.connection.simpleConnectionHealth
import io.codecks.ui.icons.deckImageVectorOrNull
import io.codecks.ui.icons.imageVector
import io.codecks.ui.theme.CodecksDeckStyle
import io.codecks.ui.theme.CodecksScopedTheme
import io.codecks.ui.theme.ThemeTarget
import io.codecks.ui.theme.LocalCodecksSemanticColors

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
    onCreateWithAiForSlot: (Int) -> Unit = {},
    visibleSlotIndices: List<Int> = if (state.deckLayout.slots.isEmpty()) {
        state.actions.indices.toList()
    } else {
        state.deckLayout.slots.indices.toList()
    },
    onTestAction: (DeckAction) -> Unit = {},
    onDuplicateAction: (DeckAction) -> Unit = {},
    onRemoveAction: (DeckAction) -> Unit = {},
    onAssignSlot: (Int, DeckAction) -> Unit = { _, _ -> },
    onMoveSlot: (Int, Int) -> Unit = { _, _ -> },
    onResizeSlot: (Int, Int) -> Unit = { _, _ -> },
    onForgetAction: (DeckAction) -> Unit = {},
    onOpenRunLog: (String?) -> Unit = {},
    smartSuggestions: List<SmartDeckSuggestionUi> = emptyList(),
    smartRunPending: Boolean = false,
    onRunSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onPinSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onHideSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onExplainSmartSuggestion: (SmartDeckSuggestionUi) -> Unit = {},
    onSuppressSmartSuggestionForContext: (SmartDeckSuggestionUi) -> Unit = {},
    onNeverSmartSuggestionForAction: (SmartDeckSuggestionUi) -> Unit = {},
    onPlacePendingDeckPlacement: (List<Int>) -> Unit = {},
    onCancelPendingDeckPlacement: () -> Unit = {},
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
    var addToSlot by rememberSaveable { mutableIntStateOf(-1) }
    var reassignSlot by rememberSaveable { mutableIntStateOf(-1) }
    var resizingSlot by rememberSaveable { mutableIntStateOf(-1) }
    var movingFromSlot by rememberSaveable { mutableIntStateOf(-1) }
    var customizationMode by rememberSaveable { mutableStateOf(false) }
    var pendingForgetAction by remember { mutableStateOf<DeckAction?>(null) }
    var selectedPlacementSlots by remember { mutableStateOf<List<Int>>(emptyList()) }
    val availableActions = remember(state.allActions) {
        state.allActions.filterNot { it.id in bottomNavShortcutIds || it.id in OPEN_SLOT_IDS }
    }
    LaunchedEffect(state.pendingDeckPlacement) {
        selectedPlacementSlots = emptyList()
    }

    state.pendingDeckPlacement?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                selectedPlacementSlots = emptyList()
                onCancelPendingDeckPlacement()
            },
            title = { Text("Place generated buttons") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose ${pending.actions.size} slot(s) for ${pending.actions.size} generated button(s).")
                    val selectedActionLabels = if (selectedPlacementSlots.isEmpty()) {
                        "No slots selected"
                    } else {
                        selectedPlacementSlots.joinToString { (it + 1).toString() }
                    }
                    Text("Selected slots: $selectedActionLabels")
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        items(
                            count = state.actions.size,
                            key = { slot -> "placement-slot-$slot" },
                        ) { slot ->
                            val action = state.actions[slot]
                            val selected = selectedPlacementSlots.contains(slot)
                            val canSelectMore = selectedPlacementSlots.size < pending.actions.size || selected
                            Surface(
                                onClick = {
                                    val updated = if (selected) {
                                        selectedPlacementSlots.filterNot { it == slot }
                                    } else if (canSelectMore) {
                                        selectedPlacementSlots + slot
                                    } else {
                                        selectedPlacementSlots
                                    }
                                    selectedPlacementSlots = updated
                                },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.24f else 0.08f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Slot ${slot + 1} · ${action.label}", maxLines = 1)
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { checked ->
                                            val updated = if (checked) {
                                                if (selectedPlacementSlots.size < pending.actions.size) {
                                                    selectedPlacementSlots + slot
                                                } else {
                                                    selectedPlacementSlots
                                                }
                                            } else {
                                                selectedPlacementSlots.filterNot { it == slot }
                                            }
                                            selectedPlacementSlots = updated
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPlacePendingDeckPlacement(selectedPlacementSlots)
                        selectedPlacementSlots = emptyList()
                    },
                    enabled = selectedPlacementSlots.size == pending.actions.size,
                ) {
                    Text("Place on Deck")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedPlacementSlots = emptyList()
                        onCancelPendingDeckPlacement()
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    CodecksScopedTheme(ThemeTarget.Deck) {
        CodecksKeybedDeck(
        activeDeckLabel = when {
            movingFromSlot >= 0 -> deckActionSlots
                .firstOrNull { it.slot == movingFromSlot }
                ?.action
                ?.let { "Move ${it.label} · tap destination" }
                ?: "Choose destination"
            customizationMode -> "Customize · tap a button to move"
            else -> activeTemplateTitle(state.activeTemplateId, state.deckTemplates)
        },
        activeApp = state.activeMacApp,
        connectionHealth = connectionHealth,
        slots = deckActionSlots,
        runningActionId = runningActionId,
        currentResult = currentResult,
        focusedActionId = focusedActionId,
        onAction = onAction,
        onOpenEmptySlot = { slot -> addToSlot = slot },
        onOpenSettings = onOpenSettings,
        onOpenConnection = onOpenConnection,
        customizationMode = customizationMode,
        onCustomizationModeChange = { enabled ->
            customizationMode = enabled
            if (!enabled) movingFromSlot = -1
        },
        onOpenPalette = onOpenPalette,
        smartSuggestions = smartSuggestions,
        smartRunPending = smartRunPending,
        onRunSmartSuggestion = onRunSmartSuggestion,
        onPinSmartSuggestion = onPinSmartSuggestion,
        onHideSmartSuggestion = onHideSmartSuggestion,
        onExplainSmartSuggestion = onExplainSmartSuggestion,
        onSuppressSmartSuggestionForContext = onSuppressSmartSuggestionForContext,
        onNeverSmartSuggestionForAction = onNeverSmartSuggestionForAction,
        onOpenOptions = { slot ->
            if (shouldShowActionOptions(slot.action, locked = false)) optionsSlot = slot
        },
        movingFromSlot = movingFromSlot.takeIf { it >= 0 },
        onMoveTarget = { target ->
            val from = movingFromSlot
            if (from < 0) {
                movingFromSlot = target.slot
            } else {
                movingFromSlot = -1
                if (from != target.slot) onMoveSlot(from, target.slot)
            }
        },
        deckStyle = deckStyle,
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
    }
    if (addToSlot >= 0) {
        val slot = addToSlot
        AddToSlotDialog(
            slot = slot,
            onDismiss = { addToSlot = -1 },
            onChooseFromCatalog = {
                addToSlot = -1
                reassignSlot = slot
            },
            onCreateWithAi = {
                addToSlot = -1
                onCreateWithAiForSlot(slot)
            },
        )
    }
    optionsSlot?.let { slot ->
        val action = slot.action
        ActionOptionsDialog(
            action = action,
            canForget = action.isCatalogForgettable(),
            onDismiss = { optionsSlot = null },
            onRun = {
                optionsSlot = null
                onAction(action)
            },
            onTest = {
                optionsSlot = null
                onTestAction(action)
            },
            onReassign = {
                optionsSlot = null
                reassignSlot = slot.slot
            },
            onMove = {
                optionsSlot = null
                movingFromSlot = slot.slot
            },
            onResize = {
                optionsSlot = null
                resizingSlot = slot.slot
            },
            onDuplicate = {
                optionsSlot = null
                onDuplicateAction(action)
            },
            onRemoveFromDeck = {
                optionsSlot = null
                onRemoveSlot(slot.slot)
            },
            onForget = {
                optionsSlot = null
                pendingForgetAction = action
            },
            onViewLog = {
                optionsSlot = null
                onOpenRunLog(action.id)
            },
        )
    }
    if (resizingSlot >= 0) {
        val slot = resizingSlot
        ResizeActionDialog(
            slot = slot,
            currentSpan = renderLayout.slots.getOrNull(slot)?.columnSpan ?: 1,
            maxSpan = renderLayout.columns,
            onDismiss = { resizingSlot = -1 },
            onResize = { columnSpan ->
                resizingSlot = -1
                onResizeSlot(slot, columnSpan)
            },
        )
    }
    if (reassignSlot >= 0) {
        val slot = reassignSlot
        ReassignActionDialog(
            slot = slot,
            currentAction = renderLayout.slots.getOrNull(slot)?.action,
            actions = availableActions,
            onDismiss = { reassignSlot = -1 },
            onAssign = { action ->
                reassignSlot = -1
                onAssignSlot(slot, action)
            },
            onForget = { action ->
                reassignSlot = -1
                pendingForgetAction = action
            },
        )
    }
    pendingForgetAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingForgetAction = null },
            title = { Text("Forget ${action.label}?") },
            text = {
                Text("This removes it from the catalog and every deck slot. You can undo from the confirmation message.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingForgetAction = null
                        onForgetAction(action)
                    },
                ) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingForgetAction = null }) {
                    Text("Cancel")
                }
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
    onOpenEmptySlot: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConnection: () -> Unit,
    customizationMode: Boolean,
    onCustomizationModeChange: (Boolean) -> Unit,
    onOpenPalette: () -> Unit,
    smartSuggestions: List<SmartDeckSuggestionUi>,
    smartRunPending: Boolean,
    onRunSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onPinSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onHideSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onExplainSmartSuggestion: (SmartDeckSuggestionUi) -> Unit,
    onSuppressSmartSuggestionForContext: (SmartDeckSuggestionUi) -> Unit,
    onNeverSmartSuggestionForAction: (SmartDeckSuggestionUi) -> Unit,
    onOpenOptions: (HomeDeckSlot) -> Unit,
    movingFromSlot: Int?,
    onMoveTarget: (HomeDeckSlot) -> Unit,
    deckStyle: CodecksDeckStyle,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale >= 2f
    val connectionReady = connectionHealth.isReady
    val deckCanvasColor = MaterialTheme.colorScheme.background
    val deckTextColor = MaterialTheme.colorScheme.onBackground
    val connectionTone = connectionToneColor(connectionHealth.kind)
    var deckMenuExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(deckCanvasColor)
    ) {
        val deckColumns = if (largeText && maxWidth < 600.dp) 2 else 4
        val rows = remember(slots, deckColumns) { packHomeDeckRows(slots, columns = deckColumns) }
        val framePadding = when {
            maxWidth >= 900.dp -> 14.dp
            maxWidth >= 600.dp -> 10.dp
            else -> 4.dp
        }
        val headerHeight = when {
            largeText -> 144.dp
            maxHeight < 640.dp -> 36.dp
            else -> 42.dp
        }
        val suggestionHeight = if (smartSuggestions.isNotEmpty()) {
            if (largeText) CodecksDesignTokens.Size.HomeDeck.suggestionRowHeightLargeText else 104.dp
        } else 0.dp
        val gapX = when {
            maxWidth >= 900.dp -> 12.dp
            maxWidth >= 600.dp -> 10.dp
            else -> 6.dp
        }
        val usableWidth = (maxWidth - framePadding * 2f).coerceAtLeast(280.dp)
        val usableHeight = (maxHeight - framePadding * 2f - headerHeight - suggestionHeight - 8.dp).coerceAtLeast(280.dp)
        val keyWidth = ((usableWidth - gapX * (deckColumns - 1).toFloat()) / deckColumns.toFloat()).coerceAtLeast(68.dp)
        val gapY = when {
            maxHeight >= 900.dp -> 12.dp
            maxHeight >= 640.dp -> 8.dp
            else -> 5.dp
        }
        val rowCount = rows.size.coerceAtLeast(1)
        val keyHeight = ((usableHeight - gapY * (rowCount - 1).toFloat()) / rowCount.toFloat()).coerceAtLeast(52.dp)
        val keybedWidth = keyWidth * deckColumns.toFloat() + gapX * (deckColumns - 1).toFloat()
        val showKeyLabels = largeText || keyHeight >= 76.dp
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
            val headerModifier = Modifier
                .width(keybedWidth)
                .height(headerHeight)
            if (largeText) Column(
                verticalArrangement = Arrangement.Center,
                modifier = headerModifier,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = deckTextColor)
                    }
                    IconButton(onClick = onOpenPalette) {
                        Icon(Icons.Outlined.Search, contentDescription = "Command palette", tint = deckTextColor)
                    }
                    Text(
                        text = activeDeckLabel,
                        color = deckTextColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f).testTag("home-deck-title"),
                    )
                    Box {
                        IconButton(onClick = { deckMenuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Deck options", tint = deckTextColor)
                        }
                        DropdownMenu(
                            expanded = deckMenuExpanded,
                            onDismissRequest = { deckMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (customizationMode) "Done customizing" else "Customize on Deck") },
                                onClick = {
                                    deckMenuExpanded = false
                                    onCustomizationModeChange(!customizationMode)
                                },
                            )
                        }
                    }
                }
                Surface(
                    onClick = onOpenConnection,
                    color = connectionTone.copy(alpha = if (connectionReady) 0.18f else 0.12f),
                    contentColor = connectionTone,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, connectionTone.copy(alpha = 0.42f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CodecksDesignTokens.Size.minTouchTarget)
                        .testTag("home-connection-status"),
                ) {
                    Text(
                        text = "$statusText • ${connectionHealth.deckLabel()}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("home-deck-subtitle"),
                    )
                }
            } else Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = headerModifier,
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
                Box {
                    IconButton(onClick = { deckMenuExpanded = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Deck options",
                            tint = deckTextColor.copy(alpha = 0.82f),
                        )
                    }
                    DropdownMenu(
                        expanded = deckMenuExpanded,
                        onDismissRequest = { deckMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (customizationMode) "Done customizing" else "Customize on Deck") },
                            onClick = {
                                deckMenuExpanded = false
                                onCustomizationModeChange(!customizationMode)
                            },
                        )
                    }
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
                        Text(
                            text = connectionHealth.deckLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.testTag("home-connection-status"),
                        )
                    }
                }
            }
            SmartSuggestionRow(
                suggestions = smartSuggestions,
                runPending = smartRunPending,
                onRun = onRunSmartSuggestion,
                onPin = onPinSmartSuggestion,
                onHide = onHideSmartSuggestion,
                onWhy = onExplainSmartSuggestion,
                onSuppressForContext = onSuppressSmartSuggestionForContext,
                onNeverForAction = onNeverSmartSuggestionForAction,
                modifier = Modifier.width(keybedWidth),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(gapY),
                modifier = Modifier
                    .width(keybedWidth)
                    .then(if (largeText) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier),
            ) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gapX)) {
                        row.forEach { slot ->
                            val action = slot.action
                            val openSlot = action.id in OPEN_SLOT_IDS
                            val running = runningActionId == action.id
                            val moving = movingFromSlot != null
                            val selected = movingFromSlot == slot.slot || focusedActionId == action.id
                            val enabled = openSlot || isDeckActionEnabled(action, connectionReady)
                            DeckControlTile(
                                label = if (openSlot) "Tap to assign" else action.label,
                                icon = action.deckImageVectorOrNull(),
                                state = when {
                                    running -> DeckComponentState.Running
                                    selected -> DeckComponentState.Selected
                                    !openSlot && currentResult is ActionStatus.Succeeded && currentResult.actionId == action.id -> DeckComponentState.Succeeded
                                    !openSlot && currentResult is ActionStatus.Failed && currentResult.actionId == action.id -> DeckComponentState.Failure
                                    !enabled -> DeckComponentState.Disabled
                                    else -> DeckComponentState.Idle
                                },
                                enabled = enabled || moving,
                                danger = action.dangerous,
                                accentColor = action.deckAccentColor(),
                                showLabel = showKeyLabels,
                                deckStyle = deckStyle,
                                onClick = {
                                    when {
                                        moving -> onMoveTarget(slot)
                                        openSlot -> onOpenEmptySlot(slot.slot)
                                        customizationMode -> onMoveTarget(slot)
                                        else -> onAction(action)
                                    }
                                },
                                onLongClick = if (openSlot) null else ({ onOpenOptions(slot) }),
                                modifier = Modifier
                                    .width(keyWidth * slot.columnSpan.toFloat() + gapX * (slot.columnSpan - 1).toFloat())
                                    .then(
                                        if (largeText) Modifier.heightIn(min = 112.dp)
                                        else Modifier.heightIn(min = keyHeight, max = keyHeight),
                                    )
                                    .testTag("deck-action-${action.id}"),
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
        ConnectionHealthKind.Ready -> LocalCodecksSemanticColors.current.success
        ConnectionHealthKind.Scanning,
        ConnectionHealthKind.Verifying,
        ConnectionHealthKind.Connecting,
        ConnectionHealthKind.Testing -> LocalCodecksSemanticColors.current.warning
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
