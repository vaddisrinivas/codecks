package io.codecks.ui.mouse.lockscreen

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.MainActivity
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.core.trackpad.LockscreenControlState
import io.codecks.core.trackpad.LockscreenDecision
import io.codecks.core.trackpad.TrackpadEntryOrigin
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.domain.contextdeck.MiniDeckCommand
import io.codecks.ui.theme.CodecksTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockscreenMiniDeckInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun optInShowsExactlyFourAccessibleMediaTargets() {
        val control = LockscreenControlState(
            keyguardShowing = true,
            deviceLocked = true,
            userUnlockedSinceBoot = true,
            hidConnected = true,
            selectedHostPresent = true,
            bluetoothPermissionGranted = true,
            featureEnabled = true,
            entryOrigin = TrackpadEntryOrigin.InternalApp,
            miniDeckEnabled = true,
        )
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CodecksTheme {
                    LockscreenTrackpadScreen(
                        state = LockscreenTrackpadUiState(control, LockscreenDecision.AllowRestrictedPointer, TrackpadSettings(lockscreenMiniDeckEnabled = true)),
                        onMove = { _, _ -> }, onScroll = { _, _ -> }, onClick = {}, onPress = {},
                        onReleaseButtons = {}, onMiniDeckCommand = {}, onUnlock = {}, onClose = {},
                    )
                }
            }
        }
        rule.waitForIdle()

        MiniDeckCommand.entries.forEach { command ->
            rule.onNodeWithTag("mini-deck-${command.name}")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(CodecksDesignTokens.Size.minTouchTarget)
        }
    }
}
