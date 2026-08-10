package io.codecks.internalquality

import android.app.Dialog
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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M11DexProxyInstrumentedTest {
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
                    assertTrue(activity.probeView.hasFocus())
                    sendMouseAndKeyboard(activity)
                    assertEquals(1, activity.pointerEvents)
                    assertEquals(1, activity.keyboardEvents)
                    assertEquals(1, activity.windowMarker)
                    assertEquals(1, activity.durableMarker)
                    durableBefore = activity.durableMarker
                }
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

    // Dialog sizing exercises bounded window UI only; it does not prove OS task freeform mode.
    private fun freeformWindowUiProxy(width: Int, height: Int) {
        withDisplay(width, height) {
            ActivityScenario.launch(M11DexProxyActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val dialog = Dialog(activity)
                    val marker = TextView(activity).apply { text = "$width x $height freeform-window UI proxy" }
                    dialog.setContentView(marker)
                    dialog.window?.setLayout(width, height)
                    dialog.show()
                    dialog.window?.setLayout(width, height)
                    val attributes = requireNotNull(dialog.window).attributes
                    assertEquals(width, attributes.width)
                    assertEquals(height, attributes.height)
                    assertTrue(dialog.isShowing)
                    assertTrue(marker.parent != null)
                    dialog.dismiss()
                }
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
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        val up = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_UP, 10f, 10f, 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        try {
            activity.dispatchTouchEvent(down)
            activity.dispatchTouchEvent(up)
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } finally {
            down.recycle()
            up.recycle()
        }
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
