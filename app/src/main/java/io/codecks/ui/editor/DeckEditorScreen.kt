package io.codecks.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.codecks.domain.ActionKind
import io.codecks.domain.ActionIcon
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckComponentState
import io.codecks.ui.designsystem.DeckControlTile
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.icons.deckImageVector
import io.codecks.ui.theme.CodecksDeckStyle

@Composable
fun DeckEditorScreen(
    slots: List<DeckAction?>,
    allActions: List<DeckAction>,
    selectedSlot: Int,
    contentPadding: PaddingValues,
    onSelectSlot: (Int) -> Unit,
    onAssignAction: (slot: Int, action: DeckAction) -> Unit,
    onMoveAction: (from: Int, to: Int) -> Unit,
    onRemoveAction: (Int) -> Unit,
    onTestAction: (DeckAction) -> Unit,
    onCreateWithAi: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    hasUnsavedChanges: Boolean = false,
    deckStyle: CodecksDeckStyle = CodecksDeckStyle.StreamDeckPro,
    slotSpans: List<Int> = List(slots.size) { 1 },
    onResizeAction: (slot: Int, columnSpan: Int) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategoryName by rememberSaveable { mutableStateOf(ActionCategory.All.name) }
    var draftSelectedSlot by rememberSaveable { mutableIntStateOf(selectedSlot) }
    var pickedUpSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedSlot, slots.size) {
        draftSelectedSlot = selectedSlot.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    }
    val selectedCategory = ActionCategory.entries.firstOrNull { it.name == selectedCategoryName } ?: ActionCategory.All
    val safeSelection = draftSelectedSlot.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val selectedAction = slots.getOrNull(safeSelection)
    val trimmedQuery = query.trim()
    val filteredActions = allActions.filter { action ->
        selectedCategory.matches(action) && (
            trimmedQuery.isBlank() || action.searchTokens(selectedCategory)
                .any { it.contains(trimmedQuery, ignoreCase = true) }
            )
    }
    val selectSlot: (Int) -> Unit = {
        draftSelectedSlot = it
        onSelectSlot(it)
    }
    val moveAction: (Int, Int) -> Unit = { from, to ->
        draftSelectedSlot = to
        pickedUpSlot = null
        onMoveAction(from, to)
    }
    val pickUpSlot: (Int) -> Unit = { index ->
        if (slots.getOrNull(index) != null) {
            pickedUpSlot = index
            selectSlot(index)
        }
    }
    val selectOrDropSlot: (Int) -> Unit = { index ->
        val picked = pickedUpSlot
        if (picked != null && picked != index) {
            moveAction(picked, index)
        } else {
            pickedUpSlot = null
            selectSlot(index)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.fillMaxSize())
        if (maxWidth >= 840.dp) {
            Row(modifier = Modifier.fillMaxSize()) {
                SlotPane(
                    slots = slots,
                    slotSpans = slotSpans,
                    selectedSlot = safeSelection,
                    onSelectSlot = selectOrDropSlot,
                    onMoveAction = moveAction,
                    pickedUpSlot = pickedUpSlot,
                    onPickUpSlot = pickUpSlot,
                    deckStyle = deckStyle,
                    compact = true,
                    modifier = Modifier.widthIn(min = 300.dp, max = 360.dp).fillMaxHeight(),
                )
                HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                EditorPane(
                    slot = safeSelection,
                    slotCount = slots.size,
                    selectedAction = selectedAction,
                    selectedSpan = slotSpans.getOrElse(safeSelection) { 1 },
                    filteredActions = filteredActions,
                    query = query,
                    onQueryChange = { query = it },
                    selectedCategory = selectedCategory,
                    onCategoryChange = { selectedCategoryName = it.name },
                    onAssignAction = onAssignAction,
                    onMoveAction = moveAction,
                    onRemoveAction = onRemoveAction,
                    onTestAction = onTestAction,
                    onResizeAction = onResizeAction,
                    onCreateWithAi = onCreateWithAi,
                    onSave = onSave,
                    isSaving = isSaving,
                    hasUnsavedChanges = hasUnsavedChanges,
                    deckStyle = deckStyle,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    ActionPickerHeader(
                        query = query,
                        onQueryChange = { query = it },
                        selectedCategory = selectedCategory,
                        onCategoryChange = { selectedCategoryName = it.name },
                        onCreateWithAi = onCreateWithAi,
                        onCreateCustomButton = { label ->
                            onAssignAction(safeSelection, customDecorAction(safeSelection, label))
                        },
                    )
                }
                item {
                    EditorHeader(
                        slot = safeSelection,
                        slotCount = slots.size,
                        selectedAction = selectedAction,
                        selectedSpan = slotSpans.getOrElse(safeSelection) { 1 },
                        onMoveAction = moveAction,
                        onRemoveAction = onRemoveAction,
                        onTestAction = onTestAction,
                        onResizeAction = onResizeAction,
                        deckStyle = deckStyle,
                    )
                }
                if (filteredActions.isEmpty()) {
                    item { EmptySearchState(query = trimmedQuery, category = selectedCategory) }
                }
                itemsIndexed(filteredActions, key = { _, action -> action.id }) { index, action ->
                    ActionRow(
                        action = action,
                        selected = selectedAction?.id == action.id,
                        onClick = { onAssignAction(safeSelection, action) },
                    )
                    if (index < filteredActions.lastIndex) EditorDivider()
                }
                item { SectionTitle("Deck layout") }
                item {
                    Text(
                        text = "Long-press a tile, then tap another slot to move it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item {
                    SlotGrid(
                        slots = slots,
                        slotSpans = slotSpans,
                        selectedSlot = safeSelection,
                        onSelectSlot = selectOrDropSlot,
                        onMoveAction = moveAction,
                        pickedUpSlot = pickedUpSlot,
                        onPickUpSlot = pickUpSlot,
                        deckStyle = deckStyle,
                        modifier = Modifier.heightIn(min = 112.dp, max = 360.dp),
                    )
                }
                item {
                    SaveBar(
                        isSaving = isSaving,
                        hasUnsavedChanges = hasUnsavedChanges,
                        onSave = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotPane(
    slots: List<DeckAction?>,
    slotSpans: List<Int>,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    pickedUpSlot: Int?,
    onPickUpSlot: (Int) -> Unit,
    deckStyle: CodecksDeckStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        SectionTitle("Deck layout")
        Text(
            text = "This is the exact button order and width used by the Deck.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text = "Long-press a tile, then tap another slot to move it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (compact) {
            CompactSlotGrid(
                slots = slots,
                slotSpans = slotSpans,
                selectedSlot = selectedSlot,
                onSelectSlot = onSelectSlot,
                onMoveAction = onMoveAction,
                pickedUpSlot = pickedUpSlot,
                onPickUpSlot = onPickUpSlot,
                deckStyle = deckStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SlotGrid(
                slots = slots,
                slotSpans = slotSpans,
                selectedSlot = selectedSlot,
                onSelectSlot = onSelectSlot,
                onMoveAction = onMoveAction,
                pickedUpSlot = pickedUpSlot,
                onPickUpSlot = onPickUpSlot,
                deckStyle = deckStyle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompactSlotGrid(
    slots: List<DeckAction?>,
    slotSpans: List<Int>,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    pickedUpSlot: Int?,
    onPickUpSlot: (Int) -> Unit,
    deckStyle: CodecksDeckStyle,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        slots.chunked(3).forEachIndexed { rowIndex, rowSlots ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowSlots.forEachIndexed { columnIndex, action ->
                    val index = rowIndex * 3 + columnIndex
                    SlotTile(
                        index = index,
                        action = action,
                        columnSpan = slotSpans.getOrElse(index) { 1 },
                        selected = index == selectedSlot,
                        onClick = { onSelectSlot(index) },
                        onMoveLeft = { onMoveAction(index, index - 1) },
                        onMoveRight = { onMoveAction(index, index + 1) },
                        onPickUp = { onPickUpSlot(index) },
                        canMoveLeft = index > 0,
                        canMoveRight = index < slots.lastIndex,
                        moving = pickedUpSlot == index,
                        dropTarget = pickedUpSlot != null && pickedUpSlot != index,
                        deckStyle = deckStyle,
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                    )
                }
                repeat(3 - rowSlots.size) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SlotGrid(
    slots: List<DeckAction?>,
    slotSpans: List<Int>,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    pickedUpSlot: Int?,
    onPickUpSlot: (Int) -> Unit,
    deckStyle: CodecksDeckStyle,
    modifier: Modifier = Modifier,
    minCellSize: androidx.compose.ui.unit.Dp = 96.dp,
    minSlotHeight: androidx.compose.ui.unit.Dp = 72.dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellSize),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(slots.size) { index ->
            val action = slots[index]
            SlotTile(
                index = index,
                action = action,
                columnSpan = slotSpans.getOrElse(index) { 1 },
                selected = index == selectedSlot,
                onClick = { onSelectSlot(index) },
                onMoveLeft = { onMoveAction(index, index - 1) },
                onMoveRight = { onMoveAction(index, index + 1) },
                onPickUp = { onPickUpSlot(index) },
                canMoveLeft = index > 0,
                canMoveRight = index < slots.lastIndex,
                moving = pickedUpSlot == index,
                dropTarget = pickedUpSlot != null && pickedUpSlot != index,
                deckStyle = deckStyle,
                modifier = Modifier.heightIn(min = minSlotHeight),
            )
        }
    }
}

@Composable
private fun SlotTile(
    index: Int,
    action: DeckAction?,
    columnSpan: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onPickUp: () -> Unit,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    moving: Boolean,
    dropTarget: Boolean,
    deckStyle: CodecksDeckStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        DeckControlTile(
            label = when {
                moving -> "${index + 1}. Moving"
                dropTarget -> "${index + 1}. Drop here"
                else -> "${index + 1}. ${action?.label ?: "Tap to assign"} · ${columnSpan}×"
            },
            icon = action?.deckImageVector() ?: Icons.Outlined.Add,
            onClick = onClick,
            state = if (selected || moving || dropTarget) DeckComponentState.Selected else DeckComponentState.Idle,
            deckStyle = deckStyle,
            onLongClick = if (action != null) onPickUp else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (selected) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditorIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    label = "Move slot left",
                    enabled = action != null && canMoveLeft,
                    onClick = onMoveLeft,
                )
                Text(
                    text = if (action == null) "Empty" else "Reorder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EditorIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    label = "Move slot right",
                    enabled = action != null && canMoveRight,
                    onClick = onMoveRight,
                )
            }
        }
    }
}

@Composable
private fun EditorPane(
    slot: Int,
    slotCount: Int,
    selectedAction: DeckAction?,
    selectedSpan: Int,
    filteredActions: List<DeckAction>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: ActionCategory,
    onCategoryChange: (ActionCategory) -> Unit,
    onAssignAction: (Int, DeckAction) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    onRemoveAction: (Int) -> Unit,
    onTestAction: (DeckAction) -> Unit,
    onResizeAction: (Int, Int) -> Unit,
    onCreateWithAi: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    hasUnsavedChanges: Boolean,
    deckStyle: CodecksDeckStyle,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            EditorHeader(
                slot = slot,
                slotCount = slotCount,
                selectedAction = selectedAction,
                selectedSpan = selectedSpan,
                onMoveAction = onMoveAction,
                onRemoveAction = onRemoveAction,
                onTestAction = onTestAction,
                onResizeAction = onResizeAction,
                deckStyle = deckStyle,
            )
        }
        item {
                ActionPickerHeader(
                    query = query,
                    onQueryChange = onQueryChange,
                    selectedCategory = selectedCategory,
                    onCategoryChange = onCategoryChange,
                    onCreateWithAi = onCreateWithAi,
                    onCreateCustomButton = { label ->
                        onAssignAction(slot, customDecorAction(slot, label))
                    },
                )
        }
        if (filteredActions.isEmpty()) {
            item { EmptySearchState(query = query.trim(), category = selectedCategory) }
        }
        itemsIndexed(filteredActions, key = { _, action -> action.id }) { index, action ->
            ActionRow(
                action = action,
                selected = selectedAction?.id == action.id,
                onClick = { onAssignAction(slot, action) },
            )
            if (index < filteredActions.lastIndex) EditorDivider()
        }
        item {
            SaveBar(
                isSaving = isSaving,
                hasUnsavedChanges = hasUnsavedChanges,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun EditorHeader(
    slot: Int,
    slotCount: Int,
    selectedAction: DeckAction?,
    selectedSpan: Int,
    onMoveAction: (Int, Int) -> Unit,
    onRemoveAction: (Int) -> Unit,
    onTestAction: (DeckAction) -> Unit,
    onResizeAction: (Int, Int) -> Unit,
    deckStyle: CodecksDeckStyle,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Selected slot",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = selectedAction?.deckImageVector() ?: Icons.Outlined.Add,
                        contentDescription = null,
                        tint = if (selectedAction == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Slot ${slot + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = selectedAction?.label ?: "Choose an action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selectedAction?.description ?: "Assign from the library below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        label = "Move left",
                        enabled = slot > 0,
                        onClick = { onMoveAction(slot, slot - 1) },
                    )
                    EditorIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowForward,
                        label = "Move right",
                        enabled = slot < slotCount - 1,
                        onClick = { onMoveAction(slot, slot + 1) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Button width",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                (1..4).forEach { span ->
                    DeckFilterPill(
                        label = "${span}×",
                        selected = selectedSpan == span,
                        onClick = { onResizeAction(slot, span) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DeckActionButton(
                label = "Test",
                onClick = { selectedAction?.let(onTestAction) },
                enabled = selectedAction != null,
                icon = Icons.Outlined.PlayArrow,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            )
            DeckActionButton(
                label = "Remove",
                onClick = { onRemoveAction(slot) },
                enabled = selectedAction != null,
                icon = Icons.Outlined.DeleteOutline,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            )
        }
    }
    EditorDivider(start = 16.dp, end = 16.dp)
}

@Composable
private fun EditorStylePreview(
    deckStyle: CodecksDeckStyle,
    selectedAction: DeckAction?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "Style preview · ${deckStyle.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DeckControlTile(
                    label = selectedAction?.label ?: "Assigned",
                    icon = selectedAction?.deckImageVector() ?: Icons.Outlined.PlayArrow,
                    onClick = {},
                    state = DeckComponentState.Selected,
                    deckStyle = deckStyle,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                )
                DeckControlTile(
                    label = "Tap to assign",
                    icon = Icons.Outlined.Add,
                    onClick = {},
                    state = DeckComponentState.Idle,
                    deckStyle = deckStyle,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                )
            }
        }
    }
}

@Composable
private fun EditorDivider(
    start: androidx.compose.ui.unit.Dp = 72.dp,
    end: androidx.compose.ui.unit.Dp = 16.dp,
) {
    HorizontalDivider(modifier = Modifier.padding(start = start, end = end))
}

@Composable
private fun EditorIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun ActionPickerHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: ActionCategory,
    onCategoryChange: (ActionCategory) -> Unit,
    onCreateWithAi: () -> Unit,
    onCreateCustomButton: (String) -> Unit,
) {
    var customLabel by rememberSaveable { mutableStateOf("") }
    val trimmedCustomLabel = customLabel.trim()
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = "Buttons",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = "Make your own emoji button",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it.take(32) },
                    singleLine = true,
                    label = { Text("Emoji or label") },
                    placeholder = { Text("💀 Deploy, 🌶️ Chaos, 🧃 Break") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DeckActionButton(
                    label = "Make emoji button",
                    onClick = {
                        onCreateCustomButton(trimmedCustomLabel)
                        customLabel = ""
                    },
                    enabled = trimmedCustomLabel.isNotBlank(),
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text("Search actions, emoji, blanks") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        DeckActionButton(
            label = "Create with AI",
            onClick = onCreateWithAi,
            icon = Icons.Outlined.AutoAwesome,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(ActionCategory.entries, key = { it.name }) { category ->
                DeckFilterPill(
                    label = category.title,
                    selected = category == selectedCategory,
                    onClick = { onCategoryChange(category) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptySearchState(query: String, category: ActionCategory) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(
            text = "No actions found",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (query.isBlank()) {
                "No ${category.title.lowercase()} actions available."
            } else {
                "Try a different search or category."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ActionRow(action: DeckAction, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(action.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        supportingContent = {
            Text(action.description.ifBlank { action.id }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.18f else 0.10f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = action.deckImageVector(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
        },
        trailingContent = {
            if (selected) Text("Assigned", color = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Outlined.Add, contentDescription = null)
        },
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp),
    )
}

private fun customDecorAction(slot: Int, label: String): DeckAction {
    val cleanLabel = label.trim().ifBlank { "✨ Button" }.take(32)
    val safeIdLabel = cleanLabel
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "emoji" }
        .take(18)
    return DeckAction(
        id = "custom_decor_${slot}_${System.currentTimeMillis()}_$safeIdLabel",
        label = cleanLabel,
        kind = ActionKind.Local,
        icon = ActionIcon.Emoji,
        description = "Custom emoji/decor button",
        route = "celebrate",
        liveSafe = true,
    )
}

@Composable
private fun SaveBar(
    isSaving: Boolean,
    hasUnsavedChanges: Boolean,
    onSave: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        if (hasUnsavedChanges) {
            Text(
                text = "Unsaved changes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        DeckActionButton(
            label = when {
                isSaving -> "Saving"
                hasUnsavedChanges -> "Apply layout"
                else -> "Saved"
            },
            onClick = onSave,
            enabled = hasUnsavedChanges && !isSaving,
            icon = Icons.Outlined.Save,
            modifier = Modifier.heightIn(min = 56.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

private enum class ActionCategory(val title: String) {
    All("All"),
    App("App"),
    Mac("Mac"),
    Browser("Browser"),
    Media("Media"),
    Editing("Editing"),
    System("System"),
    Decor("Decor");

    fun matches(action: DeckAction): Boolean = when (this) {
        All -> true
        App -> action.kind == ActionKind.Local
        Mac -> action.kind == ActionKind.Ssh
        Browser -> action.id in browserIds || "browser" in action.label.lowercase()
        Media -> action.id in mediaIds || action.icon.name == "Volume" || action.icon.name == "Play"
        Editing -> action.id in editingIds
        System -> action.dangerous || action.id in systemIds
        Decor -> action.id in decorIds || action.icon.name in setOf("Party", "Sparkle", "Emoji", "Empty")
    }

    private companion object {
        val browserIds = setOf(
            "new_tab", "tab_left", "tab_right", "reload", "browser_back", "browser_forward",
            "dev_tools", "chatgpt", "github", "gmail", "calendar", "drive", "claude", "browser_deck",
        )
        val mediaIds = setOf("play_pause", "next_track", "prev_track", "mute", "vol_up", "vol_down")
        val editingIds = setOf("copy", "paste", "keyboard")
        val decorIds = setOf(
            "confetti", "sparkle", "emoji_heart", "emoji_fire", "emoji_focus",
            "emoji_coffee", "blank_spacer", "magic_blank", "blank",
        )
        val systemIds = setOf(
            "lock_mac", "sleep_display", "screensaver", "settings", "activity", "mission",
            "next_app", "prev_app", "space_left", "space_right", "show_desktop", "full_screen",
        )
    }
}

private fun DeckAction.searchTokens(category: ActionCategory): List<String> = listOf(
    id,
    label,
    description,
    kind.name,
    icon.name,
    category.title,
    if (dangerous) "dangerous" else "",
    if (liveSafe) "safe" else "",
)
