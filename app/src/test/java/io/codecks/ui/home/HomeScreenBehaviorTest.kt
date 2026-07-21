package io.codecks.ui.home

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenBehaviorTest {
    @Test
    fun buildHomeDeckSlots_preservesOriginalVisibleSlotIndices() {
        val actions = listOf(
            deckAction("finder"),
            deckAction("add_button", ActionIcon.Add),
        )

        val slots = buildHomeDeckSlots(actions, visibleSlotIndices = listOf(2, 5))

        assertEquals(listOf(2, 5), slots.map(HomeDeckSlot::slot))
        assertEquals(listOf("finder", "add_button"), slots.map { it.action.id })
    }

    @Test
    fun buildHomeDeckSlots_fallsBackToVisibleIndexWhenNoOriginalIndexExists() {
        val actions = listOf(deckAction("finder"), deckAction("terminal"))

        val slots = buildHomeDeckSlots(actions, visibleSlotIndices = listOf(7))

        assertEquals(listOf(7, 1), slots.map(HomeDeckSlot::slot))
    }

    @Test
    fun actionOptions_areHiddenWhenLockedOrForAddSlots() {
        assertFalse(shouldShowActionOptions(deckAction("finder"), locked = true))
        assertFalse(shouldShowActionOptions(deckAction("add_button", ActionIcon.Add), locked = false))
        assertFalse(shouldShowActionOptions(deckAction("blank"), locked = false))
        assertTrue(shouldShowActionOptions(deckAction("finder"), locked = false))
    }

    private fun deckAction(
        id: String,
        icon: ActionIcon = ActionIcon.Apps,
    ): DeckAction = DeckAction(
        id = id,
        label = id,
        kind = ActionKind.Local,
        icon = icon,
    )
}
