package io.codecks.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.DeckAction
import io.codecks.domain.isRunnableFromSmartSuggestion
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckTemplate
import io.codecks.ui.home.smart.SmartDeckSuggestionUi
import io.codecks.ui.designsystem.DeckComponentState
import io.codecks.ui.designsystem.DeckControlTile
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.designsystem.codecksArgbColor
import io.codecks.ui.designsystem.codecksOpaqueColor
import io.codecks.ui.designsystem.codecksSemanticColorTokens
import io.codecks.ui.icons.deckImageVector
import io.codecks.ui.icons.deckImageVectorOrNull
import io.codecks.ui.icons.imageVector

@Composable
internal fun SmartSuggestionRow(
    suggestions: List<SmartDeckSuggestionUi>,
    runPending: Boolean,
    onRun: (SmartDeckSuggestionUi) -> Unit,
    onPin: (SmartDeckSuggestionUi) -> Unit,
    onHide: (SmartDeckSuggestionUi) -> Unit,
    onWhy: (SmartDeckSuggestionUi) -> Unit,
    onSuppressForContext: (SmartDeckSuggestionUi) -> Unit,
    onNeverForAction: (SmartDeckSuggestionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val largeText = LocalDensity.current.fontScale >= 2f
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
        contentPadding = PaddingValues(vertical = CodecksDesignTokens.Spacing.xs),
        modifier = modifier.height(
            if (largeText) CodecksDesignTokens.Size.HomeDeck.suggestionRowHeightLargeText
            else CodecksDesignTokens.Size.HomeDeck.suggestionRowHeight,
        ),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .height(
                        if (largeText) CodecksDesignTokens.Size.HomeDeck.suggestionCardHeightLargeText
                        else CodecksDesignTokens.Size.HomeDeck.suggestionCardHeight,
                    )
                    .width(
                        if (largeText) CodecksDesignTokens.Size.HomeDeck.suggestionCardWidthLargeText
                        else CodecksDesignTokens.Size.HomeDeck.suggestionCardWidth,
                    )
                    .semantics { heading() },
            ) {
                Text(
                    text = "Suggested",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Local only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(suggestions.take(3), key = SmartDeckSuggestionUi::candidateId) { suggestion ->
            var menuOpen by remember { mutableStateOf(false) }
            val runnable = suggestion.action.isRunnableFromSmartSuggestion()
            val canRun = runnable && !runPending
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(CodecksDesignTokens.Stroke.hairline, MaterialTheme.colorScheme.outline.copy(alpha = CodecksDesignTokens.Opacity.low)),
                modifier = Modifier.widthIn(
                    min = if (largeText) CodecksDesignTokens.Size.HomeDeck.suggestionMinWidthLargeText else CodecksDesignTokens.Size.HomeDeck.suggestionMinWidth,
                    max = if (largeText) CodecksDesignTokens.Size.HomeDeck.suggestionMaxWidthLargeText else CodecksDesignTokens.Size.HomeDeck.suggestionMaxWidth,
                ).testTag("smart-suggestion-${suggestion.candidateId}"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xxs),
                    modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.sm),
                ) {
                    Text(
                        text = "${suggestion.confidence}: ${suggestion.action.label}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (largeText) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("smart-suggestion-title-${suggestion.candidateId}"),
                    )
                    Text(
                        text = suggestion.reason,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = if (largeText) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("smart-suggestion-reason-${suggestion.candidateId}"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onRun(suggestion) },
                            enabled = canRun,
                        ) {
                            Text(
                                when {
                                    !runnable -> "Test first"
                                    runPending -> "Running…"
                                    else -> "Run"
                                },
                            )
                        }
                        TextButton(onClick = { onPin(suggestion) }) { Text("Pin") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More suggestion actions")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Why this?") },
                                    onClick = {
                                        menuOpen = false
                                        onWhy(suggestion)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Hide for now") },
                                    onClick = {
                                        menuOpen = false
                                        onHide(suggestion)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Don’t suggest here") },
                                    onClick = {
                                        menuOpen = false
                                        onSuppressForContext(suggestion)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Never suggest this button") },
                                    onClick = {
                                        menuOpen = false
                                        onNeverForAction(suggestion)
                                    },
                                )
                            }
                        }
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
    onCreateWithAiForSlot: (Int) -> Unit,
    onTemplateSelected: (String) -> Unit,
    onRefreshContext: () -> Unit,
    onDynamicDeckChange: (Boolean) -> Unit,
    locked: Boolean,
    onLongClick: (HomeDeckSlot) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md), modifier = Modifier.fillMaxSize().padding(CodecksDesignTokens.Spacing.md)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
            modifier = Modifier.width(CodecksDesignTokens.Size.HomeDeck.landscapeTemplateWidth).fillMaxSize(),
        ) {
            item {
                Text("Dynamic", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                DeckFilterPill(
                    label = "Custom",
                    selected = state.activeTemplateId == CUSTOM_TEMPLATE_ID,
                    onClick = { onTemplateSelected(CUSTOM_TEMPLATE_ID) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                )
            }
            items(state.deckTemplates, key = DeckTemplate::id) { template ->
                DeckFilterPill(
                    label = template.title,
                    selected = state.activeTemplateId == template.id,
                    onClick = { onTemplateSelected(template.id) },
                    icon = template.icon.imageVector(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                )
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md),
            modifier = Modifier.width(CodecksDesignTokens.Size.HomeDeck.landscapeCustomWidth).fillMaxSize(),
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
            columns = GridCells.Adaptive(minSize = CodecksDesignTokens.Size.HomeDeck.adaptiveCell),
            contentPadding = PaddingValues(bottom = CodecksDesignTokens.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md),
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
                                onCreateWithAiForSlot(slot.slot)
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
    val semantic = codecksSemanticColorTokens()
    Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm), modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.lg, vertical = CodecksDesignTokens.Spacing.md)) {
        Surface(
            color = semantic.content.copy(alpha = CodecksDesignTokens.Opacity.subtle),
            contentColor = semantic.content,
            border = BorderStroke(CodecksDesignTokens.Stroke.hairline, semantic.content.copy(alpha = CodecksDesignTokens.Opacity.soft)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.lg, vertical = CodecksDesignTokens.Spacing.md),
            ) {
                Surface(
                    color = semantic.accent.copy(alpha = CodecksDesignTokens.Opacity.low),
                    contentColor = semantic.content,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(CodecksDesignTokens.Size.minTouchTarget),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = semantic.content,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xxs), modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Codecks",
                        style = MaterialTheme.typography.titleLarge,
                        color = semantic.content,
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
                        color = semantic.content.copy(alpha = CodecksDesignTokens.Opacity.muted),
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm), contentPadding = PaddingValues(end = CodecksDesignTokens.Spacing.lg)) {
            item {
                DeckFilterPill(
                    label = "Custom",
                    selected = activeTemplateId == CUSTOM_TEMPLATE_ID,
                    onClick = { onTemplateSelected(CUSTOM_TEMPLATE_ID) },
                    modifier = Modifier.heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                )
            }
            items(templates, key = DeckTemplate::id) { template ->
                DeckFilterPill(
                    label = template.title,
                    selected = activeTemplateId == template.id,
                    onClick = { onTemplateSelected(template.id) },
                    icon = template.icon.imageVector(),
                    modifier = Modifier.heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
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
    Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (viewMode == DeckViewMode.Pages) "Controls · Page ${pageIndex + 1}/$pageCount" else "Controls",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = CodecksDesignTokens.Spacing.xxs, bottom = CodecksDesignTokens.Spacing.xxs),
        )
        if (viewMode == DeckViewMode.Pages && pageCount > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm), contentPadding = PaddingValues(end = CodecksDesignTokens.Spacing.lg)) {
                items(pageCount, key = { "deck-page-$it" }) { index ->
                    DeckFilterPill(
                        label = "${index + 1}",
                        selected = index == pageIndex,
                        onClick = { onPageSelected(index) },
                        modifier = Modifier.width(CodecksDesignTokens.Size.HomeDeck.pagePillWidth).heightIn(min = CodecksDesignTokens.Size.minTouchTarget),
                    )
                }
            }
        }
    }
}

internal fun activeTemplateTitle(activeTemplateId: String, templates: List<DeckTemplate>): String =
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
    contentPadding: PaddingValues = PaddingValues(start = CodecksDesignTokens.Spacing.lg, end = CodecksDesignTokens.Spacing.xxxl),
    isActionEnabled: (DeckAction) -> Boolean = { true },
) {
    if (actions.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
        contentPadding = contentPadding,
        modifier = modifier.fillMaxWidth(),
    ) {
        items(actions, key = { "custom-row-${it.id}" }) { action ->
            val selected = action.id == selectedActionId
            val enabled = isActionEnabled(action)
            DeckControlTile(
                label = action.label,
                icon = action.deckImageVectorOrNull(),
                state = if (selected) DeckComponentState.Selected else DeckComponentState.Idle,
                enabled = enabled,
                danger = action.dangerous,
                accentColor = action.deckAccentColor(),
                onClick = { onAction(action) },
                modifier = Modifier.size(width = CodecksDesignTokens.Size.HomeDeck.featuredCardWidth, height = CodecksDesignTokens.Size.HomeDeck.suggestionCardHeight),
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
    height: androidx.compose.ui.unit.Dp = CodecksDesignTokens.Size.HomeDeck.carouselHeight,
) {
    Box {
        DeckControlTile(
            label = action.label,
            icon = action.deckImageVectorOrNull(),
            state = when {
                running -> DeckComponentState.Running
                selected -> DeckComponentState.Selected
                !enabled -> DeckComponentState.Disabled
                else -> DeckComponentState.Idle
            },
            enabled = enabled,
            danger = action.dangerous,
            accentColor = action.deckAccentColor(),
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

internal val bottomNavShortcutIds = setOf("trackpad", "keyboard", "clipboard", "automations", "settings_shortcut")
internal val OPEN_SLOT_IDS = setOf("add_button", "blank")

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

internal fun isDeckActionEnabled(action: DeckAction, connectionReady: Boolean): Boolean =
    action.route == "decor" ||
        action.kind != ActionKind.Ssh ||
        action.id in OPEN_SLOT_IDS ||
        (connectionReady && (!action.requiresTest || action.liveSafe))

internal fun DeckAction.isCatalogForgettable(): Boolean =
    commandOrigin != CommandOrigin.Bundled || id.startsWith("artifact_") || id.startsWith("ai_") || id.startsWith("custom_")

internal fun DeckAction.deckAccentColor(): Color? = colorHex?.toComposeColorOrNull()

private fun String.toComposeColorOrNull(): Color? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    val value = normalized.toLongOrNull(16) ?: return null
    return if (normalized.length == 6) {
        codecksOpaqueColor(value.toInt())
    } else {
        codecksArgbColor(value)
    }
}

@Composable
private fun DeckConnectionHint() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.md),
        ) {
            Icon(Icons.Outlined.Computer, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xxs)) {
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
internal fun ActionOptionsDialog(
    action: DeckAction,
    canForget: Boolean,
    onDismiss: () -> Unit,
    onRun: () -> Unit,
    onTest: () -> Unit,
    onReassign: () -> Unit,
    onMove: () -> Unit,
    onResize: () -> Unit,
    onDuplicate: () -> Unit,
    onRemoveFromDeck: () -> Unit,
    onForget: () -> Unit,
    onViewLog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.label) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm),
                modifier = Modifier.heightIn(max = CodecksDesignTokens.Size.HomeDeck.dialogListMaxHeight),
            ) {
                item {
                    Text(
                        action.description.ifBlank { "Deck button" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { DialogActionButton("Run", onRun) }
                item { DialogActionButton("Reassign this slot", onReassign) }
                item { DialogActionButton("Move this button", onMove) }
                item { DialogActionButton("Resize this button", onResize) }
                item { DialogActionButton("Duplicate into empty slot", onDuplicate) }
                item { DialogActionButton("Test", onTest) }
                item { DialogActionButton("Run log", onViewLog) }
                item { HorizontalDivider() }
                item { DialogActionButton("Remove from deck", onRemoveFromDeck) }
                if (canForget) {
                    item { DialogActionButton("Forget from catalog", onForget) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DialogActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.scrim),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.md),
        )
    }
}

@Composable
internal fun ResizeActionDialog(
    slot: Int,
    currentSpan: Int,
    maxSpan: Int,
    onDismiss: () -> Unit,
    onResize: (Int) -> Unit,
) {
    val choices = remember(maxSpan) {
        listOf(1, 2, 4).filter { it <= maxSpan.coerceAtLeast(1) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resize slot ${slot + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm)) {
                choices.forEach { span ->
                    DialogActionButton(
                        label = when (span) {
                            1 -> "Single"
                            maxSpan -> "Full row"
                            else -> "$span columns"
                        } + if (span == currentSpan) " · Current" else "",
                        onClick = { onResize(span) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AddToSlotDialog(
    slot: Int,
    onDismiss: () -> Unit,
    onChooseFromCatalog: () -> Unit,
    onCreateWithAi: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to slot ${slot + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.sm)) {
                Text(
                    "Choose an existing button or build a new one with AI.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DialogActionButton("Choose from catalog", onChooseFromCatalog)
                DialogActionButton("Create with AI", onCreateWithAi)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ReassignActionDialog(
    slot: Int,
    currentAction: DeckAction?,
    actions: List<DeckAction>,
    onDismiss: () -> Unit,
    onAssign: (DeckAction) -> Unit,
    onForget: (DeckAction) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(actions, query) {
        val needle = query.trim()
        actions
            .filter { action ->
                needle.isBlank() ||
                    action.label.contains(needle, ignoreCase = true) ||
                    action.description.contains(needle, ignoreCase = true) ||
                    action.id.contains(needle, ignoreCase = true)
            }
            .take(30)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reassign slot ${slot + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md)) {
                currentAction?.let {
                    Text(
                        "Current: ${it.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Find button") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.xs),
                    modifier = Modifier.heightIn(max = CodecksDesignTokens.Size.HomeDeck.compactDialogListMaxHeight),
                ) {
                    items(filtered, key = DeckAction::id) { action ->
                        Surface(
                            onClick = { onAssign(action) },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.scrim),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(CodecksDesignTokens.Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = CodecksDesignTokens.Spacing.md, vertical = CodecksDesignTokens.Spacing.sm),
                            ) {
                                Icon(action.deckImageVector(), contentDescription = null, modifier = Modifier.size(CodecksDesignTokens.Size.iconSm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (action.description.isNotBlank()) {
                                        Text(
                                            action.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CodecksDesignTokens.Opacity.emphasized),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (action.isCatalogForgettable()) {
                                    TextButton(onClick = { onForget(action) }) { Text("Forget") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
