package io.codecks.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test
    fun `keyboard route is task route not drawer destination`() {
        assertEquals(AppRoute.Keyboard, AppRoute.fromId("keyboard"))
        assertTrue(AppRoute.Keyboard in AppRoute.all)
        assertFalse(AppRoute.Keyboard in AppRoute.topLevel)
    }

    @Test
    fun `keyboard return target preserves origin but never keyboard itself`() {
        assertEquals(AppRoute.Trackpad, AppRoute.keyboardReturnTarget("trackpad"))
        assertEquals(AppRoute.Deck, AppRoute.keyboardReturnTarget("deck"))
        assertEquals(AppRoute.Deck, AppRoute.keyboardReturnTarget("keyboard"))
        assertEquals(AppRoute.Deck, AppRoute.keyboardReturnTarget("missing"))
    }

    @Test
    fun `unknown route falls back to deck`() {
        assertEquals(AppRoute.Deck, AppRoute.fromId("missing"))
    }
}
