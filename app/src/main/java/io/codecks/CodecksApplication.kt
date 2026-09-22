package io.codecks

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.codecks.data.observability.PrivacyCrashAnrReporter
import io.codecks.domain.observability.TelemetryEvent
import io.codecks.domain.observability.TelemetryEventName
import io.codecks.domain.observability.TelemetryResult
import io.codecks.launcher.LauncherIconManager

@HiltAndroidApp
class CodecksApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // playInternal M16 workers must not run process-global launcher/telemetry
        // initialization against the shared package data directory.
        if (packageName == "app.codecks.internal" && getProcessName().matches(Regex("app\\.codecks\\.internal:m16(p0[1-5]|evidence)"))) return
        runCatching { LauncherIconManager(this).reconcile() }
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
