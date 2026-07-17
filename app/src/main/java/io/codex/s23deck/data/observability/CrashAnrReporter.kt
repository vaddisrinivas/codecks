package io.codex.s23deck.data.observability

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import android.util.Log
import io.codex.s23deck.domain.observability.TelemetryEvent

interface CrashAnrReporter {
    fun install()
    fun recordCoarseEvent(event: TelemetryEvent)
}

class PrivacyCrashAnrReporter(
    private val application: Application,
) : CrashAnrReporter {
    private var installed = false
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun install() {
        if (installed) return
        installed = true
        reportHistoricalAnrs()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordCrash(throwable)
            previousHandler?.uncaughtException(thread, throwable) ?: Runtime.getRuntime().exit(10)
        }
    }

    override fun recordCoarseEvent(event: TelemetryEvent) {
        Log.i(
            TAG,
            "metric name=${event.name.wire} route=${event.route.wire} result=${event.result.wire} latency=${event.latencyBucket?.wire ?: "none"} error=${event.errorCode?.wire ?: "none"}",
        )
    }

    private fun recordCrash(throwable: Throwable) {
        Log.e(TAG, "crash kind=${throwable.javaClass.simpleName}")
    }

    private fun reportHistoricalAnrs() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = application.getSystemService(ActivityManager::class.java) ?: return
        val anrCount = manager.getHistoricalProcessExitReasons(application.packageName, 0, 5)
            .count { it.reason == ApplicationExitInfo.REASON_ANR }
        if (anrCount > 0) {
            Log.w(TAG, "anr exits=$anrCount")
        }
    }

    private companion object {
        const val TAG = "DeckBridgeObs"
    }
}
