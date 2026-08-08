package io.codecks.internalcommercial.labui

import android.content.Intent
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialLabActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<CommercialLabActivity>()

    @Test
    fun internalBannerAndAccessibleActionsAreVisible() {
        rule.onNodeWithText("INTERNAL COMMERCIAL LAB").assertIsDisplayed()
        rule.onNodeWithContentDescription("Simulate internal sign in")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        rule.onNodeWithText("Session: active · Consent: unknown").assertIsDisplayed()
    }

    @Test
    fun destructiveDeletionNeedsTwoExplicitSteps() {
        rule.onNodeWithContentDescription("Simulate internal sign in").performClick()
        rule.onNodeWithContentDescription("Delete internal test account").performClick()
        rule.onNodeWithText("Delete internal test account?").assertIsDisplayed()
        rule.onNodeWithContentDescription("Confirm test account deletion").performClick()
        rule.onNodeWithText("Reauthenticate").assertIsDisplayed()
        rule.onNodeWithContentDescription("Reauthenticate and delete test account").performClick()
        rule.onNodeWithText("Session: deleted · Consent: unknown").assertIsDisplayed()
    }

    @Test
    fun typedLabStateSurvivesActivityRecreation() {
        rule.onNodeWithContentDescription("Simulate internal sign in").performClick()
        rule.onNodeWithText("Billing").performClick()
        rule.onNodeWithContentDescription("Start an internal pending purchase").performClick()

        rule.activityRule.scenario.recreate()

        rule.onNodeWithText("Session: active · Consent: unknown").assertIsDisplayed()
        rule.onNodeWithText("Current: pending").assertIsDisplayed()
        rule.onNodeWithText("Purchase pending; no entitlement granted").assertIsDisplayed()
    }

    @Test
    fun publicPackageCannotResolveLabActivity() {
        val intent = Intent().setClassName("app.codecks", CommercialLabActivity::class.java.name)
        assertNull(rule.activity.packageManager.resolveActivity(intent, 0))
    }
}
