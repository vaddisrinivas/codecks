package io.codecks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidSessionServicePolicyTest {
    @Test
    fun notificationUsesGenericTrackpadEntryWithoutStopAction() {
        val source = File("src/main/java/io/codecks/HidSessionService.kt").readText()

        assertTrue(source.contains("TrackpadEntryActivity.notificationPendingIntent(this)"))
        assertTrue(source.contains("""setContentTitle("Codecks Bluetooth input")"""))
        assertTrue(source.contains("""setContentText("Keeping Trackpad and Keyboard ready for your Mac.")"""))
        assertTrue(source.contains(""".addAction(R.drawable.ic_launcher, "Trackpad", pendingOpen)"""))
        assertFalse(source.contains("ACTION_STOP"))
        assertFalse(source.contains("Stop action"))
        assertFalse(source.contains(""", "Stop","""))
    }
}
