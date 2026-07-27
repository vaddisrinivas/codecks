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
                dataUri = null,
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
                dataUri = null,
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
                dataUri = null,
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
                dataUri = null,
                destination = "settings",
                providedToken = null,
                expectedToken = "known-token",
            ),
        )
    }

    @Test
    fun routesExactPublicTrackpadUriWithoutToken() {
        assertNull(
            resolveDestinationRequest(
                action = Intent.ACTION_VIEW,
                type = null,
                dataUri = PUBLIC_TRACKPAD_URI,
                destination = null,
                providedToken = null,
                expectedToken = "known-token",
            ),
        )
    }

    @Test
    fun rejectsUnknownPublicUriWithoutToken() {
        assertNull(
            resolveDestinationRequest(
                action = Intent.ACTION_VIEW,
                type = null,
                dataUri = "codecks://settings",
                destination = null,
                providedToken = null,
                expectedToken = "known-token",
            ),
        )
    }
}
