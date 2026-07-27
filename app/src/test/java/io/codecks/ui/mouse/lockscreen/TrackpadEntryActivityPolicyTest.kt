package io.codecks.ui.mouse.lockscreen

import android.content.Intent
import io.codecks.PUBLIC_TRACKPAD_URI
import io.codecks.core.trackpad.TrackpadEntryOrigin
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadEntryActivityPolicyTest {
    @Test
    fun publicTrackpadUriWinsOverInjectedExtras() {
        assertEquals(
            TrackpadEntryOrigin.ExactPublicUri,
            TrackpadEntryActivity.resolveTrustedEntryOrigin(
                action = Intent.ACTION_VIEW,
                dataString = PUBLIC_TRACKPAD_URI,
                declaredOrigin = TrackpadEntryOrigin.InternalWidget.name,
                providedToken = "bad-token",
                expectedToken = "good-token",
            ),
        )
    }

    @Test
    fun unsignedInternalOriginFallsBackToUnknown() {
        assertEquals(
            TrackpadEntryOrigin.Unknown,
            TrackpadEntryActivity.resolveTrustedEntryOrigin(
                action = null,
                dataString = null,
                declaredOrigin = TrackpadEntryOrigin.InternalWidget.name,
                providedToken = null,
                expectedToken = "good-token",
            ),
        )
    }

    @Test
    fun signedInternalOriginIsAccepted() {
        assertEquals(
            TrackpadEntryOrigin.InternalWidget,
            TrackpadEntryActivity.resolveTrustedEntryOrigin(
                action = null,
                dataString = null,
                declaredOrigin = TrackpadEntryOrigin.InternalWidget.name,
                providedToken = "good-token",
                expectedToken = "good-token",
            ),
        )
    }

    @Test
    fun fullTrackpadIntentTargetsOnlyMainTrackpadDestination() {
        val source = File("src/main/java/io/codecks/ui/mouse/lockscreen/TrackpadEntryActivity.kt").readText()

        assertTrue(source.contains("""Intent(context, MainActivity::class.java)"""))
        assertTrue(source.contains("""putExtra(MainActivity.EXTRA_DESTINATION, "mouse")"""))
    }
}
