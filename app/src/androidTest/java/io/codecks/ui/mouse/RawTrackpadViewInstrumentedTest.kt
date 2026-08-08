package io.codecks.ui.mouse

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RawTrackpadViewInstrumentedTest {
    @Test
    fun tapThenMove_dispatchesPressMoveReleaseForWhiteboardDrag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()

        instrumentation.runOnMainSync {
            val view = RawTrackpadView(instrumentation.targetContext).apply {
                hapticsEnabled = false
                scrollRailEnabled = false
                precisionScrollRailEnabled = false
                onLeftClick = { events += "click" }
                onPress = { mask -> events += "press:$mask" }
                onMove = { _, _ -> events += "move" }
                onReleaseButtons = { events += "release" }
                layout(0, 0, 600, 800)
            }

            val firstDownTime = SystemClock.uptimeMillis()
            view.dispatchTouch(MotionEvent.ACTION_DOWN, firstDownTime, firstDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_UP, firstDownTime, firstDownTime + 20L, 240f, 360f)

            val dragDownTime = firstDownTime + 40L
            view.dispatchTouch(MotionEvent.ACTION_DOWN, dragDownTime, dragDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_MOVE, dragDownTime, dragDownTime + 20L, 320f, 410f)
            view.dispatchTouch(MotionEvent.ACTION_UP, dragDownTime, dragDownTime + 40L, 320f, 410f)
        }

        assertEquals("press:1", events.first())
        assertTrue(events.contains("move"))
        assertEquals("release", events.last())
        assertEquals(1, events.count { it == "press:1" })
        assertEquals(1, events.count { it == "release" })
        assertFalse(events.contains("click"))
    }

    @Test
    fun moveWithoutArmedTap_movesPointerWithoutHoldingButton() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()

        instrumentation.runOnMainSync {
            val view = RawTrackpadView(instrumentation.targetContext).apply {
                hapticsEnabled = false
                scrollRailEnabled = false
                precisionScrollRailEnabled = false
                onPress = { events += "press" }
                onMove = { _, _ -> events += "move" }
                onReleaseButtons = { events += "release" }
                layout(0, 0, 600, 800)
            }

            val downTime = SystemClock.uptimeMillis()
            view.dispatchTouch(MotionEvent.ACTION_DOWN, downTime, downTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_MOVE, downTime, downTime + 20L, 320f, 410f)
            view.dispatchTouch(MotionEvent.ACTION_UP, downTime, downTime + 40L, 320f, 410f)
        }

        assertTrue(events.contains("move"))
        assertFalse(events.contains("press"))
        assertFalse(events.contains("release"))
    }

    @Test
    fun canceledTapDragCandidate_doesNotArmFollowingMove() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()

        instrumentation.runOnMainSync {
            val view = testView(instrumentation.targetContext, events)
            val firstDownTime = SystemClock.uptimeMillis()
            view.dispatchTouch(MotionEvent.ACTION_DOWN, firstDownTime, firstDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_UP, firstDownTime, firstDownTime + 20L, 240f, 360f)

            val candidateDownTime = firstDownTime + 40L
            view.dispatchTouch(MotionEvent.ACTION_DOWN, candidateDownTime, candidateDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_CANCEL, candidateDownTime, candidateDownTime + 10L, 240f, 360f)

            val nextDownTime = firstDownTime + 70L
            view.dispatchTouch(MotionEvent.ACTION_DOWN, nextDownTime, nextDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_MOVE, nextDownTime, nextDownTime + 20L, 320f, 410f)
            view.dispatchTouch(MotionEvent.ACTION_UP, nextDownTime, nextDownTime + 40L, 320f, 410f)
        }

        assertTrue(events.contains("move"))
        assertFalse(events.contains("press:1"))
        assertFalse(events.contains("release"))
        assertFalse(events.contains("click"))
    }

    @Test
    fun cancelAfterTapDragStart_releasesExactlyOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()

        instrumentation.runOnMainSync {
            val view = testView(instrumentation.targetContext, events)
            val firstDownTime = SystemClock.uptimeMillis()
            view.dispatchTouch(MotionEvent.ACTION_DOWN, firstDownTime, firstDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_UP, firstDownTime, firstDownTime + 20L, 240f, 360f)

            val dragDownTime = firstDownTime + 40L
            view.dispatchTouch(MotionEvent.ACTION_DOWN, dragDownTime, dragDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_MOVE, dragDownTime, dragDownTime + 20L, 320f, 410f)
            view.dispatchTouch(MotionEvent.ACTION_CANCEL, dragDownTime, dragDownTime + 40L, 320f, 410f)
        }

        assertEquals(1, events.count { it == "press:1" })
        assertEquals(1, events.count { it == "release" })
        assertFalse(events.contains("click"))
    }

    @Test
    fun cancelAfterLongPressDrag_releasesExactlyOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()
        lateinit var view: RawTrackpadView
        var downTime = 0L
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        try {
            instrumentation.runOnMainSync {
                view = testView(activity, events)
                activity.setContentView(view)
                downTime = SystemClock.uptimeMillis()
                view.dispatchTouch(MotionEvent.ACTION_DOWN, downTime, downTime, 240f, 360f)
            }
            SystemClock.sleep(300L)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                view.dispatchTouch(MotionEvent.ACTION_CANCEL, downTime, downTime + 300L, 240f, 360f)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }

        assertEquals(1, events.count { it == "press:1" })
        assertEquals(1, events.count { it == "release" })
        assertFalse(events.contains("click"))
    }

    @Test
    fun tapThenHold_suppressesClickBeforeStartingDrag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()
        lateinit var view: RawTrackpadView
        var firstDownTime = 0L
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        try {
            instrumentation.runOnMainSync {
                view = testView(activity, events)
                activity.setContentView(view)
                firstDownTime = SystemClock.uptimeMillis()
                view.dispatchTouch(MotionEvent.ACTION_DOWN, firstDownTime, firstDownTime, 240f, 360f)
                view.dispatchTouch(MotionEvent.ACTION_UP, firstDownTime, firstDownTime + 20L, 240f, 360f)
                val secondDownTime = firstDownTime + 40L
                view.dispatchTouch(MotionEvent.ACTION_DOWN, secondDownTime, secondDownTime, 240f, 360f)
            }
            SystemClock.sleep(300L)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                view.dispatchTouch(
                    MotionEvent.ACTION_CANCEL,
                    firstDownTime + 40L,
                    firstDownTime + 340L,
                    240f,
                    360f,
                )
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }

        assertEquals(1, events.count { it == "press:1" })
        assertEquals(1, events.count { it == "release" })
        assertFalse(events.contains("click"))
    }

    @Test
    fun secondTapAfterFirstClickDispatch_emitsExactlyTwoTotalClicks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()
        lateinit var view: RawTrackpadView
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        try {
            instrumentation.runOnMainSync {
                view = testView(activity, events)
                activity.setContentView(view)
                val firstDownTime = SystemClock.uptimeMillis()
                view.dispatchTouch(MotionEvent.ACTION_DOWN, firstDownTime, firstDownTime, 240f, 360f)
                view.dispatchTouch(MotionEvent.ACTION_UP, firstDownTime, firstDownTime + 20L, 240f, 360f)
            }
            SystemClock.sleep(220L)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(1, events.count { it == "click" })
                val secondDownTime = SystemClock.uptimeMillis()
                view.dispatchTouch(MotionEvent.ACTION_DOWN, secondDownTime, secondDownTime, 240f, 360f)
                view.dispatchTouch(MotionEvent.ACTION_UP, secondDownTime, secondDownTime + 20L, 240f, 360f)

                // The post-delay second click must not leave a tap-drag arm
                // behind for the next ordinary pointer movement.
                val thirdDownTime = secondDownTime + 40L
                view.dispatchTouch(MotionEvent.ACTION_DOWN, thirdDownTime, thirdDownTime, 240f, 360f)
                view.dispatchTouch(MotionEvent.ACTION_MOVE, thirdDownTime, thirdDownTime + 20L, 320f, 410f)
                view.dispatchTouch(MotionEvent.ACTION_UP, thirdDownTime, thirdDownTime + 40L, 320f, 410f)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }

        assertEquals(2, events.count { it == "click" })
        assertFalse(events.contains("press:1"))
        assertFalse(events.contains("release"))
    }

    @Test
    fun disablingActiveTapDrag_releasesOnceAndClearsArm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val events = mutableListOf<String>()

        instrumentation.runOnMainSync {
            val view = testView(instrumentation.targetContext, events)
            val firstDownTime = SystemClock.uptimeMillis()
            view.dispatchTouch(MotionEvent.ACTION_DOWN, firstDownTime, firstDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_UP, firstDownTime, firstDownTime + 20L, 240f, 360f)

            val dragDownTime = firstDownTime + 40L
            view.dispatchTouch(MotionEvent.ACTION_DOWN, dragDownTime, dragDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_MOVE, dragDownTime, dragDownTime + 20L, 320f, 410f)
            view.enabledForInput = false

            view.enabledForInput = true
            val nextDownTime = firstDownTime + 90L
            view.dispatchTouch(MotionEvent.ACTION_DOWN, nextDownTime, nextDownTime, 240f, 360f)
            view.dispatchTouch(MotionEvent.ACTION_MOVE, nextDownTime, nextDownTime + 20L, 320f, 410f)
            view.dispatchTouch(MotionEvent.ACTION_UP, nextDownTime, nextDownTime + 40L, 320f, 410f)
        }

        assertEquals(1, events.count { it == "press:1" })
        assertEquals(1, events.count { it == "release" })
        assertFalse(events.contains("click"))
    }

    private fun testView(
        context: android.content.Context,
        events: MutableList<String>,
    ): RawTrackpadView = RawTrackpadView(context).apply {
        hapticsEnabled = false
        scrollRailEnabled = false
        precisionScrollRailEnabled = false
        onLeftClick = { events += "click" }
        onPress = { mask -> events += "press:$mask" }
        onMove = { _, _ -> events += "move" }
        onReleaseButtons = { events += "release" }
        onDoubleTap = { events += "click"; events += "click" }
        layout(0, 0, 600, 800)
    }

    private fun RawTrackpadView.dispatchTouch(
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
        expectedHandled: Boolean = true,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).use { event ->
            assertEquals(expectedHandled, onTouchEvent(event))
        }
    }

    private inline fun MotionEvent.use(block: (MotionEvent) -> Unit) {
        try {
            block(this)
        } finally {
            recycle()
        }
    }
}
