package io.codex.s23deck

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.codex.s23deck.data.observability.PrivacyCrashAnrReporter
import io.codex.s23deck.domain.observability.TelemetryEvent
import io.codex.s23deck.domain.observability.TelemetryEventName
import io.codex.s23deck.domain.observability.TelemetryResult

@HiltAndroidApp
class DeckBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivacyCrashAnrReporter(this).apply {
            install()
            recordCoarseEvent(
                TelemetryEvent(
                    name = TelemetryEventName.AppStarted,
                    result = TelemetryResult.Success,
                ),
            )
        }
    }
}
