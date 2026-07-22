package io.codecks.ui.mouse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import io.codecks.HidCommand
import io.codecks.HidState
import io.codecks.GestureTestActivity
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadGestureSample
import io.codecks.ui.theme.CodecksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class MouseScreenGestureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<GestureTestActivity>()
    private val gestureSamples = mutableListOf<TrackpadGestureSample>()

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

    @Test
    fun twoFingerDoubleTapEmitsWindowSwitcherInsteadOfRightClick() {
        var rightClicks = 0
        val samples = mutableListOf<TrackpadGestureSample>()
        val commands = setTestContent(
            onRightClick = { rightClicks += 1 },
            onGestureDiagnostic = samples::add,
        )

        performDoubleTap(pointerCount = 2)

        composeRule.runOnIdle {
            assertEquals(samples.joinToString { "${it.classification}@${it.durationMillis}" }, listOf(HidCommand.WindowSwitcher), commands)
            assertEquals(0, rightClicks)
        }
    }

    @Test
    fun threeAndFourFingerDoubleTapsEmitConfiguredCommands() {
        val commands = setTestContent()

        performDoubleTap(pointerCount = 3)
        performDoubleTap(pointerCount = 4)

        composeRule.runOnIdle {
            assertEquals(listOf(HidCommand.AppSwitcher, HidCommand.MissionControl), commands)
        }
    }

    @Test
    fun threeAndFourFingerHoldsEmitConfiguredCommands() {
        val settings = TrackpadSettings(multiFingerHoldMillis = 350)
        val commands = setTestContent(settings = settings)

        performStationaryGesture(pointerCount = 3, durationMillis = 500L)
        performStationaryGesture(pointerCount = 4, durationMillis = 500L)

        composeRule.runOnIdle {
            assertEquals(listOf(HidCommand.WindowSwitcher, HidCommand.ShowDesktop), commands)
        }
    }

    @Test
    fun fastAndSlowScrollRailsBothWorkAtDifferentSpeeds() {
        val scrollEvents = mutableListOf<Int>()
        setTestContent(
            settings = TrackpadSettings(naturalScroll = false),
            onScroll = scrollEvents::add,
        )

        performRailDrag(xFraction = 0.98f)
        val fastDistance = scrollEvents.sumOf(::abs)
        composeRule.runOnIdle { scrollEvents.clear() }

        performRailDrag(xFraction = 0.02f)
        val slowDistance = scrollEvents.sumOf(::abs)

        composeRule.runOnIdle {
            assertTrue("Fast rail emitted no scroll", fastDistance > 0)
            assertTrue("Slow rail emitted no scroll", slowDistance > 0)
            assertTrue("Slow rail was not slower: fast=$fastDistance slow=$slowDistance", fastDistance > slowDistance)
        }
    }

    private fun setTestContent(
        settings: TrackpadSettings = TrackpadSettings(),
        onRightClick: () -> Unit = {},
        onScroll: (Int) -> Unit = {},
        onGestureDiagnostic: (TrackpadGestureSample) -> Unit = {},
    ): MutableList<HidCommand> {
        val commands = mutableListOf<HidCommand>()
        composeRule.setContent {
            CodecksTheme {
                MouseScreen(
                    state = HidState(isConnected = true, isReady = true),
                    settings = settings,
                    contentPadding = PaddingValues(),
                    permissionGranted = true,
                    onRequestPermission = {},
                    onStart = {},
                    onRefreshHosts = {},
                    onConnect = {},
                    onMove = { _, _ -> },
                    onScroll = onScroll,
                    onLeftClick = {},
                    onRightClick = onRightClick,
                    onGestureDiagnostic = { sample ->
                        gestureSamples.add(sample)
                        onGestureDiagnostic(sample)
                    },
                    onCommand = { commands.add(it) },
                )
            }
        }
        return commands
    }

    private fun performStationaryGesture(pointerCount: Int, durationMillis: Long) {
        composeRule.onNodeWithTag(TrackpadTestTag).performTouchInput {
            val positions = gesturePositions(pointerCount, center)
            positions.forEachIndexed { id, position -> down(id, position) }
            move(durationMillis)
            positions.indices.reversed().forEach(::up)
        }
    }

    private fun performDoubleTap(pointerCount: Int) {
        composeRule.onNodeWithTag(TrackpadTestTag).performTouchInput {
            val positions = gesturePositions(pointerCount, center)
            repeat(2) {
                positions.forEachIndexed { id, position ->
                    down(id, position)
                    advanceEventTime(12L)
                }
                move(80L)
                positions.indices.reversed().forEach { id ->
                    up(id)
                    advanceEventTime(8L)
                }
                advanceEventTime(100L)
            }
        }
    }

    private fun performRailDrag(xFraction: Float) {
        composeRule.onNodeWithTag(TrackpadTestTag).performTouchInput {
            val start = Offset(center.x * 2f * xFraction, center.y - 72f)
            down(0, start)
            updatePointerTo(0, start + Offset(0f, 144f))
            move(180L)
            up(0)
        }
    }

    private fun assertSwipeCommand(
        commands: MutableList<HidCommand>,
        pointerCount: Int,
        delta: Offset,
        expected: HidCommand,
    ) {
        composeRule.runOnIdle {
            commands.clear()
            gestureSamples.clear()
        }

        composeRule.onNodeWithTag(TrackpadTestTag).performTouchInput {
            val durationMillis = 300L
            val origin = center - (delta / 2f)
            val starts = gesturePositions(pointerCount, origin)
            starts.forEachIndexed { id, position -> down(id, position) }
            starts.forEachIndexed { id, position -> updatePointerTo(id, position + delta) }
            move(durationMillis)
            starts.indices.reversed().forEach(::up)
        }

        composeRule.runOnIdle {
            assertEquals(
                gestureSamples.joinToString { "${it.classification}:${it.maxPointers}:${it.panDistancePx}" },
                listOf(expected),
                commands,
            )
        }
    }

    private fun gesturePositions(pointerCount: Int, origin: Offset): List<Offset> {
        val spacing = 18f
        return List(pointerCount) { index ->
            origin + Offset((index - pointerCount / 2f) * spacing, 0f)
        }
    }
}
