package io.codecks.ui.home

import io.codecks.domain.ActionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusFeedbackPolicyTest {
    @Test
    fun genericDeckSuccessUsesTileOnlyFeedback() {
        val feedback = homeStatusFeedback(ActionStatus.Succeeded("finder", "Finder sent"))

        assertTrue(feedback is HomeStatusFeedback.TileOnly)
    }

    @Test
    fun deckRemoveSuccessKeepsUndoSnackbar() {
        val feedback = homeStatusFeedback(ActionStatus.Succeeded("deck_remove", "Removed Focus"))

        assertEquals(
            HomeStatusFeedback.Snackbar(
                message = "Removed Focus",
                actionLabel = "Undo",
            ),
            feedback,
        )
    }

    @Test
    fun connectMacFailureKeepsSetupShortcut() {
        val feedback = homeStatusFeedback(ActionStatus.Failed("finder", CONNECT_MAC_FIRST_MESSAGE))

        assertEquals(
            HomeStatusFeedback.Snackbar(
                message = CONNECT_MAC_FIRST_MESSAGE,
                actionLabel = "Set up",
            ),
            feedback,
        )
    }
}
