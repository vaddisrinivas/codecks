package io.codecks.data.context

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager

data class ContextAppRankingGateResult(
    val allowed: Boolean,
    val reason: String,
)

class ContextAppRankingGate(
    private val context: Context,
) {
    fun evaluate(): ContextAppRankingGateResult {
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

        return ContextAppRankingGateResult(allowed = true, reason = "Allowed")
    }

    private fun blocked(reason: String) = ContextAppRankingGateResult(allowed = false, reason = reason)

    private companion object {
        val sleepInterruptionFilters = setOf(
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
        )
    }
}
