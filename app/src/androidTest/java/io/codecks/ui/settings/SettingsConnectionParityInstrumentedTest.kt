package io.codecks.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.codecks.MainActivity
import org.junit.Rule
import org.junit.Test

class SettingsConnectionParityInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsRoute_exposesActiveMacSetupFlow() {
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithTag("more-destination-settings").performClick()

        rule.onNodeWithText("Setup").assertIsDisplayed()
        val settingsList = rule.onNode(hasScrollAction())
        settingsList.performScrollToNode(hasText("Mac input"))
        rule.onNodeWithText("Mac input").assertIsDisplayed()

        // A clean managed device is disconnected, so this panel is already open.
        // Scroll the lazy Settings semantics; clicking Mac actions would close it.
        settingsList.performScrollToNode(hasText("Connect a Mac"))
        rule.onNodeWithText("Connect a Mac").assertIsDisplayed()
        settingsList.performScrollToNode(hasText("Find Macs"))
        rule.onNodeWithText("Find Macs").assertIsDisplayed()
    }
}
