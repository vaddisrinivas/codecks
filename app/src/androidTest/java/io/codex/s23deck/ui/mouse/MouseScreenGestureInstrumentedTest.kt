package io.codex.s23deck.ui.mouse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.multiTouchSwipe
import io.codex.s23deck.HidCommand
import io.codex.s23deck.HidState
import io.codex.s23deck.GestureTestActivity
import io.codex.s23deck.ui.theme.DeckBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MouseScreenGestureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<GestureTestActivity>()

    @Test
    fun threeFingerSwipesEmitMacWindowCommands() {
        val commands = setTestContent()
        assertSwipeCommand(commands, 3, Offset(96f, 0f), HidCommand.SpaceRight)
        assertSwipeCommand(commands, 3, Offset(-96f, 0f), HidCommand.SpaceLeft)
        assertSwipeCommand(commands, 3, Offset(0f, -96f), HidCommand.MissionControl)
        assertSwipeCommand(commands, 3, Offset(0f, 96f), HidCommand.AppExpose)
    }

    @Test
    fun fourFingerSwipesEmitMacWindowCommands() {
        val commands = setTestContent()
        assertSwipeCommand(commands, 4, Offset(96f, 0f), HidCommand.SpaceRight)
        assertSwipeCommand(commands, 4, Offset(-96f, 0f), HidCommand.SpaceLeft)
        assertSwipeCommand(commands, 4, Offset(0f, -96f), HidCommand.MissionControl)
        assertSwipeCommand(commands, 4, Offset(0f, 96f), HidCommand.AppExpose)
    }

    @Test
    fun fiveFingerSwipesEmitFiveFingerCommands() {
        val commands = setTestContent()
        assertSwipeCommand(commands, 5, Offset(96f, 0f), HidCommand.AppSwitcher)
        assertSwipeCommand(commands, 5, Offset(-96f, 0f), HidCommand.AppSwitcher)
        assertSwipeCommand(commands, 5, Offset(0f, -96f), HidCommand.MissionControl)
        assertSwipeCommand(commands, 5, Offset(0f, 96f), HidCommand.ShowDesktop)
    }

    private fun setTestContent(): MutableList<HidCommand> {
        val commands = mutableListOf<HidCommand>()
        composeRule.setContent {
            DeckBridgeTheme {
                MouseScreen(
                    state = HidState(isConnected = true, isReady = true),
                    contentPadding = PaddingValues(),
                    permissionGranted = true,
                    onRequestPermission = {},
                    onStart = {},
                    onRefreshHosts = {},
                    onConnect = {},
                    onMove = { _, _ -> },
                    onScroll = {},
                    onLeftClick = {},
                    onRightClick = {},
                    onCommand = { commands.add(it) },
                )
            }
        }
        return commands
    }

    private fun assertSwipeCommand(
        commands: MutableList<HidCommand>,
        pointerCount: Int,
        delta: Offset,
        expected: HidCommand,
    ) {
        composeRule.runOnIdle { commands.clear() }

        composeRule.onNodeWithTag(TrackpadTestTag).performTouchInput {
            val durationMillis = 300L
            val origin = center - (delta / 2f)
            val spacing = 18f
            val paths = List(pointerCount) { index ->
                { timeMillis: Long ->
                    val fraction = (timeMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
                    origin + Offset((index - pointerCount / 2f) * spacing, 0f) + (delta * fraction)
                }
            }
            multiTouchSwipe(paths, durationMillis)
        }

        composeRule.runOnIdle {
            assertEquals(listOf(expected), commands)
        }
    }
}
