package io.codecks.ui.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartupInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

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

    @Test fun validPairingIntentIsClearedBeforeUseOnCreate() {
        assertPairingIntentCleared(pairingIntent(validPairingUri()))
    }

    @Test fun rejectedLegacyPairingIntentIsClearedBeforeUseOnCreate() {
        assertPairingIntentCleared(pairingIntent("codecks://helper-pair?payload=%7B%22sharedSecretHex%22%3A%2201%22%7D"))
    }

    @Test fun malformedPairingIntentIsClearedBeforeUseOnCreate() {
        assertPairingIntentCleared(pairingIntent("codecks://helper-pair?payload=%ZZ"))
    }

    private fun assertPairingIntentCleared(intent: Intent) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        try {
            instrumentation.runOnMainSync {
                assertEquals(Intent.ACTION_MAIN, activity.intent.action)
                assertEquals(null, activity.intent.data)
                assertTrue(activity.intent.extras == null || activity.intent.extras!!.isEmpty)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun pairingIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
        .putExtra("must_clear", "bootstrap")

    private fun validPairingUri(): String {
        val secret = (0 until 32).joinToString("") { "%02x".format(it) }
        val payload = """{"schema":"codecks.pairing.v2","offerId":"AAAAAAAAAAAAAAAAAAAAAA","issuedAtMillis":1000,"expiresAtMillis":121000,"macId":"desk-mac","displayName":"Desk Mac","helperId":"codecks-mac-helper","publicKeyFingerprint":"${"a".repeat(64)}","host":"192.168.1.20","port":47321,"offerNonce":"BBBBBBBBBBBBBBBBBBBBBQ","offerSecretHex":"$secret"}"""
        return "codecks://helper-pair?payload=${Uri.encode(payload)}"
    }
}
