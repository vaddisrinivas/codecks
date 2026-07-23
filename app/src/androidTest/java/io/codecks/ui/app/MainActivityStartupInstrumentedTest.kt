package io.codecks.ui.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.codecks.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivityStartupInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deckStartupRendersVisibleControls() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Finder").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Finder").assertIsDisplayed()
        composeRule.onNodeWithText("Setup").assertIsDisplayed()
    }
}
