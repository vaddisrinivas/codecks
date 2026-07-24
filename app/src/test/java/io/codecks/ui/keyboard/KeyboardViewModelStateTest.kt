package io.codecks.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardViewModelStateTest {
    @Test
    fun successfulSendClearsDraftAndKeepsSentTextInRecents() {
        val state = KeyboardUiState(
            text = "ship it",
            recentSends = listOf("older"),
        )

        val result = state.afterSuccessfulSend(
            sentText = "ship it",
            message = "Typed 7 chars over Bluetooth · Enter sent",
        )

        assertEquals("", result.text)
        assertEquals("Typed 7 chars over Bluetooth · Enter sent", result.status)
        assertEquals(listOf("ship it", "older"), result.recentSends)
    }
}
