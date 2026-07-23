package io.codecks.ui.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.codecks.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartupInstrumentedTest {
    @Test
    fun coldStartReachesResumedActivityAndProcessSurvives() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
                assertTrue(activity.window.decorView.isAttachedToWindow)
            }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }
}
