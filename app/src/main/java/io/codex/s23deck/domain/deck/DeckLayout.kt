package io.codex.s23deck.domain.deck

import io.codex.s23deck.domain.DeckAction

/**
 * The persisted, renderable Deck model.
 *
 * A slot owns its stable identity and width. The action id is deliberately not the slot id:
 * the same action may appear more than once and can move without losing slot-specific state.
 */
data class DeckSlot(
    val id: String,
    val action: DeckAction,
    val columnSpan: Int = 1,
) {
    fun normalized(columns: Int): DeckSlot = copy(columnSpan = columnSpan.coerceIn(1, columns))
}

data class DeckLayout(
    val columns: Int = DEFAULT_COLUMNS,
    val slots: List<DeckSlot> = emptyList(),
) {
    val actions: List<DeckAction> get() = slots.map(DeckSlot::action)

    fun normalized(): DeckLayout {
        val safeColumns = columns.coerceIn(1, MAX_COLUMNS)
        val usedIds = mutableSetOf<String>()
        val normalizedSlots = slots.mapIndexed { index, slot ->
            var candidate = slot.id.ifBlank { "slot-${index + 1}" }
            var suffix = 2
            while (!usedIds.add(candidate)) {
                candidate = "${slot.id.ifBlank { "slot-${index + 1}" }}-$suffix"
                suffix += 1
            }
            slot.copy(id = candidate).normalized(safeColumns)
        }
        return copy(columns = safeColumns, slots = normalizedSlots)
    }

    fun replacingAction(index: Int, action: DeckAction): DeckLayout =
        if (index !in slots.indices) this
        else copy(slots = slots.toMutableList().also { it[index] = it[index].copy(action = action) })

    fun swapping(from: Int, to: Int): DeckLayout =
        if (from !in slots.indices || to !in slots.indices) this
        else copy(slots = slots.toMutableList().also { items ->
            val item = items[from]
            items[from] = items[to]
            items[to] = item
        })

    fun resizing(index: Int, columnSpan: Int): DeckLayout =
        if (index !in slots.indices) this
        else copy(slots = slots.toMutableList().also {
            it[index] = it[index].copy(columnSpan = columnSpan.coerceIn(1, columns))
        })

    companion object {
        const val DEFAULT_COLUMNS = 4
        private const val MAX_COLUMNS = 6

        val Empty = DeckLayout()

        fun fromActions(actions: List<DeckAction>, columns: Int = DEFAULT_COLUMNS): DeckLayout =
            DeckLayout(
                columns = columns,
                slots = actions.mapIndexed { index, action ->
                    DeckSlot(id = "slot-${index + 1}", action = action)
                },
            )
    }
}

/** Packs sequential slots into rows without splitting a slot across rows. */
fun DeckLayout.rows(): List<List<DeckSlot>> {
    val layout = normalized()
    val rows = mutableListOf<MutableList<DeckSlot>>()
    var row = mutableListOf<DeckSlot>()
    var usedColumns = 0
    layout.slots.forEach { slot ->
        if (row.isNotEmpty() && usedColumns + slot.columnSpan > layout.columns) {
            rows += row
            row = mutableListOf()
            usedColumns = 0
        }
        row += slot
        usedColumns += slot.columnSpan
        if (usedColumns == layout.columns) {
            rows += row
            row = mutableListOf()
            usedColumns = 0
        }
    }
    if (row.isNotEmpty()) rows += row
    return rows
}
