package io.codecks

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import org.junit.Assert.assertEquals
import org.junit.Test

class DangerousActionConfirmationCopyTest {
    @Test
    fun `uses reviewed confirmation copy when supplied`() {
        val copy = dangerousActionConfirmationCopy(
            action(
                confirmationTitle = "Lock Desk Mac?",
                confirmationBody = "This ends the active session on Desk Mac.",
            ),
        )

        assertEquals("Lock Desk Mac?", copy.title)
        assertEquals("This ends the active session on Desk Mac.", copy.body)
        assertEquals("Run Lock Mac", copy.confirmLabel)
    }

    @Test
    fun `falls back to explicit button confirmation copy`() {
        val copy = dangerousActionConfirmationCopy(action(description = "Locks the selected Mac."))

        assertEquals("Run Lock Mac?", copy.title)
        assertEquals("Locks the selected Mac.", copy.body)
        assertEquals("Run Lock Mac", copy.confirmLabel)
    }

    @Test
    fun `prefers risk reason over generic description`() {
        val copy = dangerousActionConfirmationCopy(
            action(
                description = "Runs the button.",
                riskReason = "This closes the active session.",
            ),
        )

        assertEquals("This closes the active session.", copy.body)
    }

    private fun action(
        description: String = "",
        confirmationTitle: String? = null,
        confirmationBody: String? = null,
        riskReason: String? = null,
    ) = DeckAction(
        id = "lock_mac",
        label = "Lock Mac",
        kind = ActionKind.Local,
        icon = ActionIcon.Lock,
        description = description,
        dangerous = true,
        confirmationTitle = confirmationTitle,
        confirmationBody = confirmationBody,
        riskReason = riskReason,
    )
}
