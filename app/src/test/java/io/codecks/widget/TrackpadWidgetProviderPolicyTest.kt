package io.codecks.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadWidgetProviderPolicyTest {
    @Test
    fun widgetOnlyRoutesToSignedTrackpadEntryPendingIntent() {
        val source = File("src/main/java/io/codecks/widget/TrackpadWidgetProvider.kt").readText()

        assertTrue(source.contains("TrackpadEntryActivity.widgetPendingIntent(context)"))
        assertTrue(source.contains("setOnClickPendingIntent(R.id.trackpad_widget_root"))
    }
}
