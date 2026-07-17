package io.codex.s23deck.domain.deck

import io.codex.s23deck.domain.ActionIcon
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.DeckAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckLayoutTest {
    @Test
    fun normalizedPreservesDuplicateActionsButRepairsSlotIdentityAndWidths() {
        val action = action("same")
        val layout = DeckLayout(
            columns = 4,
            slots = listOf(DeckSlot("slot", action, 0), DeckSlot("slot", action, 9)),
        ).normalized()

        assertEquals(listOf("same", "same"), layout.actions.map { it.id })
        assertNotEquals(layout.slots[0].id, layout.slots[1].id)
        assertEquals(listOf(1, 4), layout.slots.map { it.columnSpan })
    }

    @Test
    fun swappingMovesWholeSlotIncludingWidth() {
        val layout = DeckLayout(
            slots = listOf(
                DeckSlot("wide", action("trackpad"), 3),
                DeckSlot("small", action("automation"), 1),
            ),
        )

        val moved = layout.swapping(0, 1)

        assertEquals(listOf("small", "wide"), moved.slots.map { it.id })
        assertEquals(listOf(1, 3), moved.slots.map { it.columnSpan })
    }

    @Test
    fun rowsNeverSplitWideButtons() {
        val layout = DeckLayout(
            columns = 4,
            slots = listOf(
                DeckSlot("a", action("a"), 3),
                DeckSlot("b", action("b"), 2),
                DeckSlot("c", action("c"), 2),
            ),
        )

        val rows = layout.rows()

        assertEquals(listOf(listOf("a"), listOf("b", "c")), rows.map { row -> row.map { it.id } })
        assertTrue(rows.all { row -> row.sumOf { it.columnSpan } <= layout.columns })
    }

    private fun action(id: String) = DeckAction(id, id, ActionKind.Local, ActionIcon.Apps)
}
