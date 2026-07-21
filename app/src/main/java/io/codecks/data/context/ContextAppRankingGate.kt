package io.codecks.data.context

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager
import java.time.LocalTime

data class ContextAppRankingGateResult(
    val allowed: Boolean,
    val reason: String,
)

class ContextAppRankingGate(
    private val context: Context,
) {
    fun evaluate(now: LocalTime = LocalTime.now()): ContextAppRankingGateResult {
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager?.isInteractive == false) {
            return blocked("Skipped: phone screen is off/locked")
        }

        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isDeviceLocked == true) {
            return blocked("Skipped: phone is locked")
        }

        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
            return blocked("Skipped: driving/car mode")
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager?.currentInterruptionFilter in sleepInterruptionFilters) {
            return blocked("Skipped: sleep/DND mode")
        }

        if (now.hour in SLEEP_HOURS) {
            return blocked("Skipped: quiet sleep hours")
        }

        return ContextAppRankingGateResult(allowed = true, reason = "Allowed")
    }

    private fun blocked(reason: String) = ContextAppRankingGateResult(allowed = false, reason = reason)

    private companion object {
        val SLEEP_HOURS = setOf(0, 1, 2, 3, 4, 5)
        val sleepInterruptionFilters = setOf(
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
        )
    }
}
