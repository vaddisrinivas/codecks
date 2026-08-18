package io.codecks.internalquality

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.ui.mouse.RawTrackpadView
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M11DexProxyInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun managedWindow1280x720() = managedWindowProfile(1280, 720)
    @Test fun managedWindow1920x1080() = managedWindowProfile(1920, 1080)
    @Test fun freeformWindow1280x720() = freeformWindowUiProxy(1280, 720)
    @Test fun freeformWindow1920x1080() = freeformWindowUiProxy(1920, 1080)
    @Test fun secondaryDisplay1280x720() = secondaryDisplayProxy(1280, 720)
    @Test fun secondaryDisplay1920x1080() = secondaryDisplayProxy(1920, 1080)

    private fun managedWindowProfile(width: Int, height: Int) {
        withDisplay(width, height) {
            var durableBefore = 0
            ActivityScenario.launch(M11DexProxyActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.resetDurableMarker()
                    assertWindow(activity, width, height)
                    activity.probeView.requestFocus()
                    assertTrue(activity.probeView.hasFocus())
                    sendMouseAndKeyboard(activity)
                }
                waitUntil {
                    var delivered = false
                    scenario.onActivity { delivered = it.pointerEvents == 1 && it.keyboardEvents == 1 }
                    delivered
                }
                scenario.onActivity { activity ->
                    assertEquals(1, activity.pointerEvents)
                    assertEquals(1, activity.keyboardEvents)
                    assertEquals(1, activity.windowMarker)
                    assertEquals(1, activity.durableMarker)
                    durableBefore = activity.durableMarker
                }
                assertProductSurfaces(scenario)
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertWindow(activity, width, height)
                    assertEquals(1, activity.windowMarker)
                    assertEquals(durableBefore, activity.durableMarker)
                }
                rotateAndAssertSwap(scenario, width, height)
            }
            ActivityScenario.launch(M11DexProxyActivity::class.java).use { restarted ->
                restarted.onActivity { activity ->
                    assertEquals(0, activity.windowMarker)
                    assertEquals(durableBefore, activity.durableMarker)
                }
            }
        }
    }

    // Exact product dialog exercised at both display profiles; OS task freeform remains NOT_RUN.
    private fun freeformWindowUiProxy(width: Int, height: Int) {
        withDisplay(width, height) {
            ActivityScenario.launch(M11DexProxyActivity::class.java).use { scenario ->
                waitForSurfaces(scenario)
                scenario.onActivity { it.openProductDialog() }
                compose.onNodeWithText("Support bundle").assertIsDisplayed()
                compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Preview ready"))
                    .assertIsDisplayed()
                compose.onNodeWithText("Redacted health", substring = true).assertIsDisplayed()
                compose.onNodeWithText("Bounded operation receipts", substring = true).assertIsDisplayed()
                compose.onNodeWithText("Never includes credentials", substring = true).assertIsDisplayed()
                val bounds = compose.onNodeWithText("Support bundle").fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width in 1f..width.toFloat())
                assertTrue(bounds.height in 1f..height.toFloat())
                compose.onNodeWithContentDescription("Generate support bundle")
                    .assertHasClickAction().performClick()
                scenario.onActivity { assertEquals(1, it.dialogEvents) }
            }
        }
    }

    private fun secondaryDisplayProxy(width: Int, height: Int) {
        shell("settings put global overlay_display_devices ${width}x${height}/160")
        try {
            ActivityScenario.launch(M11DexProxyActivity::class.java).use { scenario ->
                lateinit var presentation: Presentation
                lateinit var marker: TextView
                scenario.onActivity { activity ->
                    val manager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val display = waitForSecondaryDisplay(manager, width, height)
                    presentation = Presentation(activity, display)
                    marker = TextView(presentation.context).apply { text = "display-move-proxy:$width:$height" }
                    presentation.setContentView(marker)
                    presentation.show()
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    assertNotEquals(Display.DEFAULT_DISPLAY, presentation.display.displayId)
                    assertEquals(width, presentation.display.mode.physicalWidth)
                    assertEquals(height, presentation.display.mode.physicalHeight)
                    assertTrue(marker.isAttachedToWindow)
                    assertEquals("display-move-proxy:$width:$height", marker.text.toString())
                    presentation.dismiss()
                }
            }
        } finally {
            shell("settings delete global overlay_display_devices")
        }
    }

    private fun sendMouseAndKeyboard(activity: M11DexProxyActivity) {
        val now = android.os.SystemClock.uptimeMillis()
        val x = activity.probeView.width / 2f
        val y = activity.probeView.height / 2f
        val down = mouseEvent(now, now, MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, x, y)
        val up = mouseEvent(now, now + 1, MotionEvent.ACTION_UP, 0, x, y)
        try {
            assertTrue(activity.probeView.dispatchTouchEvent(down))
            assertTrue(activity.probeView.dispatchTouchEvent(up))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun mouseEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        buttons: Int,
        x: Float,
        y: Float,
    ): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })
        return MotionEvent.obtain(
            downTime, eventTime, action, 1, properties, coordinates,
            0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
    }

    private fun rotateAndAssertSwap(scenario: ActivityScenario<M11DexProxyActivity>, width: Int, height: Int) {
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 1")
        waitUntil {
            var swapped = false
            scenario.onActivity { activity ->
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                swapped = bounds.width() == height && bounds.height() == width
            }
            swapped
        }
        shell("settings put system user_rotation 0")
        waitUntil {
            var restored = false
            scenario.onActivity { activity ->
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                restored = bounds.width() == width && bounds.height() == height
            }
            restored
        }
    }

    private fun assertWindow(activity: M11DexProxyActivity, width: Int, height: Int) {
        val bounds = activity.windowManager.currentWindowMetrics.bounds
        assertEquals(width, bounds.width())
        assertEquals(height, bounds.height())
        assertTrue(activity.window.decorView.isAttachedToWindow)
    }

    private fun assertProductSurfaces(scenario: ActivityScenario<M11DexProxyActivity>) {
        waitForSurfaces(scenario)
        scenario.onActivity { activity ->
            assertTrue(activity.probeView is RawTrackpadView)
            assertEquals("M11 Trackpad surface", activity.probeView.contentDescription)
        }
        compose.onNodeWithTag("m11-deck-action").assertHasClickAction().performClick()
        compose.onNodeWithTag("destination-trackpad").assertHasClickAction().performClick()
        scenario.onActivity { activity ->
            assertEquals(1, activity.deckEvents)
            assertEquals(1, activity.navigationEvents)
            assertEquals(io.codecks.navigation.MouseRoute, activity.selectedRoute)
        }
    }

    private fun waitForSurfaces(scenario: ActivityScenario<M11DexProxyActivity>) {
        waitUntil {
            var ready = false
            scenario.onActivity { ready = it.productSurfacesReady }
            ready
        }
    }

    private fun withDisplay(width: Int, height: Int, block: () -> Unit) {
        shell("wm size ${width}x$height")
        shell("wm density 160")
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 0")
        try {
            waitUntil { displaySize() == (width to height) }
            block()
        } finally {
            shell("settings put system user_rotation 0")
            shell("settings put system accelerometer_rotation 1")
            shell("wm size reset")
            shell("wm density reset")
        }
    }

    private fun displaySize(): Pair<Int, Int> {
        val output = shell("wm size")
        val match = Regex("Override size: (\\d+)x(\\d+)").find(output)
            ?: Regex("Physical size: (\\d+)x(\\d+)").find(output)
            ?: error("wm size did not report a display size: $output")
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private fun waitForSecondaryDisplay(manager: DisplayManager, width: Int, height: Int): Display {
        var found: Display? = null
        waitUntil(timeoutMs = 10_000) {
            found = manager.displays.firstOrNull {
                it.displayId != Display.DEFAULT_DISPLAY &&
                    it.mode.physicalWidth == width && it.mode.physicalHeight == height
            }
            found != null
        }
        return requireNotNull(found)
    }

    private fun waitUntil(timeoutMs: Long = 8_000, predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (!predicate()) {
            if (android.os.SystemClock.uptimeMillis() >= deadline) error("Timed out waiting for emulator proxy state")
            android.os.SystemClock.sleep(100)
        }
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use(::readFully)

    private fun readFully(descriptor: ParcelFileDescriptor): String =
        FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
}
