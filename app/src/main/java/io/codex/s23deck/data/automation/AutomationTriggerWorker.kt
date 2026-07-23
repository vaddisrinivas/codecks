package io.codex.s23deck.data.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.codecks.data.automation.AutomationTriggerWorker as CurrentAutomationTriggerWorker

/**
 * Compatibility shim for pre-Codecks WorkManager rows that stored the old worker class name.
 *
 * Upgraded installs may briefly ask WorkManager to instantiate this legacy name before the current
 * unique periodic work is refreshed. Keep the shim small: refresh the current worker and finish.
 */
class AutomationTriggerWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        CurrentAutomationTriggerWorker.enqueue(applicationContext)
        return Result.success()
    }
}
