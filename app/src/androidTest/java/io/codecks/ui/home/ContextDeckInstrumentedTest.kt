package io.codecks.ui.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.MainActivity
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.contextdeck.AnalogControl
import io.codecks.domain.contextdeck.AnalogControlKind
import io.codecks.domain.contextdeck.LiveSignal
import io.codecks.domain.contextdeck.LiveSignalId
import io.codecks.domain.contextdeck.LiveSignalValue
import io.codecks.domain.contextdeck.ModifierLayer
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.reactive.ObservationStatus
import io.codecks.domain.reactive.StateSource
import io.codecks.ui.theme.CodecksTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextDeckInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun liveAnalogAndModifierControlsExposeMinimumTargets() {
        val actions = (1..4).map { DeckAction("action_$it", "Action $it", ActionKind.Local, ActionIcon.Apps) }
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CodecksTheme {
                    Box(Modifier.size(412.dp, 915.dp)) {
                        HomeScreen(
                            state = HomeUiState(
                                actions = actions,
                                deckLayout = DeckLayout.fromActions(actions),
                                allActions = actions,
                                modifierLayer = ModifierLayer("context_fn", actions.take(2)),
                                liveSignals = listOf(
                                    LiveSignal(LiveSignalId.Mute, LiveSignalValue.Active, ObservationStatus.Fresh, 1L, StateSource.SshProbe),
                                ),
                                analogControls = listOf(AnalogControl(AnalogControlKind.Volume, 40, ObservationStatus.Fresh)),
                            ),
                            contentPadding = PaddingValues(0.dp),
                            onAction = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("context-deck-live-rail").assertIsDisplayed()
        rule.onNodeWithTag("context-modifier-layer").assertHeightIsAtLeast(CodecksDesignTokens.Size.minTouchTarget)
        rule.onNodeWithTag("live-mute").assertHeightIsAtLeast(CodecksDesignTokens.Size.minTouchTarget)
        rule.onNodeWithTag("analog-volume").assertIsDisplayed()
    }
}
