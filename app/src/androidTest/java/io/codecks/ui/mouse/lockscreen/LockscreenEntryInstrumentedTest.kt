package io.codecks.ui.mouse.lockscreen

import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.PUBLIC_TRACKPAD_URI
import io.codecks.core.trackpad.TrackpadEntryOrigin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockscreenEntryInstrumentedTest {
    @Test
    fun restrictedActivityIsSecureAndDoesNotExposeKeyboardSurface() {
        val scenario = ActivityScenario.launch<LockscreenTrackpadActivity>(
            LockscreenTrackpadActivity.intent(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                TrackpadEntryOrigin.ExactPublicUri,
            ),
        )
        try {
            scenario.onActivity { activity ->
                assertTrue(
                    activity.window.attributes.flags.toLong() and WindowManager.LayoutParams.FLAG_SECURE.toLong() != 0L,
                )
                assertFalse(activity.intent.getBooleanExtra("keyboard", false))
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun exactPublicRouteRemainsTheOnlyUnsignedExternalRoute() {
        assertTrue(PUBLIC_TRACKPAD_URI == "codecks://trackpad")
        assertFalse(
            TrackpadEntryActivity.resolveTrustedEntryOrigin(
                Intent.ACTION_VIEW,
                "codecks://trackpad?command=lock",
                TrackpadEntryOrigin.InternalWidget.name,
                null,
                "expected",
            ) == TrackpadEntryOrigin.ExactPublicUri,
        )
    }
}
