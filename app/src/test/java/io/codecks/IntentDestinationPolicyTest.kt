    package io.codecks

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentDestinationPolicyTest {
    @Test
    fun ignoresDestinationExtraWithoutInternalToken() {
        assertNull(
            resolveDestinationRequest(
                action = null,
                type = null,
                destination = "advanced",
                providedToken = null,
                expectedToken = "known-token",
            ),
        )
    }

    @Test
    fun acceptsDestinationExtraWithInternalToken() {
        assertEquals(
            "mouse",
            resolveDestinationRequest(
                action = null,
                type = null,
                destination = "mouse",
                providedToken = "known-token",
                expectedToken = "known-token",
            ),
        )
    }

    @Test
    fun routesPlainTextShareToAiWithoutToken() {
        assertEquals(
            "ai",
            resolveDestinationRequest(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                destination = "advanced",
                providedToken = null,
                expectedToken = "known-token",
            ),
        )
    }

    @Test
    fun acceptsDebugDestinationActionInDebugBuild() {
        assertEquals(
            if (BuildConfig.DEBUG) "settings" else null,
            resolveDestinationRequest(
                action = InternalIntentAuth.ACTION_DEBUG_OPEN_DESTINATION,
                type = null,
                destination = "settings",
                providedToken = null,
                expectedToken = "known-token",
            ),
        )
    }
}
