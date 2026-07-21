package io.codecks

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.codecks.data.observability.PrivacyCrashAnrReporter
import io.codecks.domain.observability.TelemetryEvent
import io.codecks.domain.observability.TelemetryEventName
import io.codecks.domain.observability.TelemetryResult

@HiltAndroidApp
class CodecksApplication : Application() {
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
