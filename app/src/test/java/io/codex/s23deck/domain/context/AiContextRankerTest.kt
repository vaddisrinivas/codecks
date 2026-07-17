package io.codex.s23deck.domain.context

import io.codex.s23deck.domain.ActionIcon
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.DeckAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextRankerTest {
    @Test
    fun chromeContextRanksBrowserControlsFirst() {
        val ranked = AiContextRanker.rank(
            snapshot = UserContextSnapshot(
                activeMacApp = "Google Chrome",
                macConnected = true,
                notificationSources = listOf("Gmail"),
                hourOfDay = 14,
            ),
            actions = listOf(
                action("finder", "Finder", "Open Finder"),
                action("browser_reload", "Reload Tab", "Refresh browser tab"),
                action("lock", "Lock Mac", "Lock screen", dangerous = true),
            ),
        )

        assertEquals("browser_reload", ranked.first().action.id)
        assertTrue(ranked.first().reason.contains("browser"))
    }

    @Test
    fun dangerousActionsArePenalized() {
        val ranked = AiContextRanker.rank(
            snapshot = UserContextSnapshot(
                activeMacApp = "Terminal",
                macConnected = true,
                notificationSources = emptyList(),
                hourOfDay = 10,
            ),
            actions = listOf(
                action("safe_terminal", "Terminal", "Open terminal workflow"),
                action("danger_terminal", "Nuke Terminal", "Terminal destructive", dangerous = true),
            ),
        )

        assertEquals("safe_terminal", ranked.first().action.id)
    }

    private fun action(
        id: String,
        label: String,
        description: String,
        dangerous: Boolean = false,
    ) = DeckAction(
        id = id,
        label = label,
        kind = ActionKind.Ssh,
        icon = ActionIcon.Control,
        description = description,
        dangerous = dangerous,
    )
}
