package io.codecks.ui.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.MainActivity
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.navigation.HomeRoute
import io.codecks.ui.theme.CodecksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodecksNavigationDrawerInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun compactAllOpensDrawerAndGroupedNavigationSelectsSettings() {
        rule.onNodeWithTag("navigation-all").performClick()
        rule.onNodeWithText("Quick Deck").assertIsDisplayed()
        rule.onNodeWithText("App navigation").assertIsDisplayed()
        rule.onNodeWithText("Control").assertIsDisplayed()
        rule.onNodeWithText("Build").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Manage").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("drawer-destination-settings").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Setup").assertIsDisplayed()
        rule.onNodeWithTag("navigation-all").performClick()
        rule.onNodeWithTag("drawer-destination-settings").assertIsSelected()
    }

    @Test
    fun openingDrawerDoesNotExecuteQuickDeckActionAndClickCallsCallbackOnce() {
        var calls = 0
        val action = DeckAction("instrumented", "Instrumented action", ActionKind.Local, ActionIcon.Play)
        rule.activity.setContent {
            CodecksTheme {
                CodecksNavigationDrawerContent(
                    currentRoute = HomeRoute,
                    destinations = RouteRegistry.primaryDestinations(),
                    actions = listOf(action),
                    selectedActionId = null,
                    actionRunning = false,
                    onDestinationSelected = {},
                    onAction = { calls += 1 },
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("quick-deck-action-instrumented").assertIsDisplayed()
        assertEquals(0, calls)

        rule.onNodeWithTag("quick-deck-action-instrumented").performClick()
        rule.waitForIdle()
        assertEquals(1, calls)
    }

    @Test
    fun runningQuickDeckActionIsDisabledButSelected() {
        val action = DeckAction("running", "Running action", ActionKind.Local, ActionIcon.Play)
        rule.activity.setContent {
            CodecksTheme {
                CodecksNavigationDrawerContent(
                    currentRoute = HomeRoute,
                    destinations = RouteRegistry.primaryDestinations(),
                    actions = listOf(action),
                    selectedActionId = action.id,
                    actionRunning = true,
                    onDestinationSelected = {},
                    onAction = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag("quick-deck-action-running")
            .assertIsNotEnabled()
            .assertIsSelected()
    }

    @Test
    fun largeTextQuickDeckTargetRemainsAccessible() {
        val action = DeckAction("large-text", "Large text action", ActionKind.Local, ActionIcon.Play)
        rule.activity.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                CodecksTheme {
                    CodecksNavigationDrawerContent(
                        currentRoute = HomeRoute,
                        destinations = RouteRegistry.primaryDestinations(),
                        actions = listOf(action),
                        selectedActionId = null,
                        actionRunning = false,
                        onDestinationSelected = {},
                        onAction = {},
                        onDismiss = {},
                    )
                }
            }
        }
        val bounds = rule.onNodeWithTag("quick-deck-action-large-text").getUnclippedBoundsInRoot()
        assertTrue((bounds.bottom - bounds.top).value >= 48f)
    }
}
