package io.codecks.data.smart

import android.content.Context
import io.codecks.data.context.DeviceSurfaceContextSource
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.smart.SmartContext

class SmartContextRepository(private val context: Context) {
    fun current(
        currentSurface: String,
        selectedMacId: String?,
        macConnected: Boolean,
        activeMacApp: String?,
        recentActionIds: List<String>,
        notificationSourceKeys: List<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): SmartContext {
        val surface = DeviceSurfaceContextSource(context.applicationContext).current()
        return SmartContext(
            currentSurface = currentSurface,
            selectedMacId = selectedMacId,
            macConnected = macConnected,
            activeMacApp = activeMacApp,
            recentActionIds = recentActionIds.distinct().take(12),
            notificationSourceKeys = notificationSourceKeys.map { it.sanitizeContextSource() }.filter(String::isNotBlank).distinct().take(6),
            coarsePhoneContext = if (surface.kind.name == "Desktop") "desktop" else "phone",
            supportedCapabilities = buildSet {
                add(SmartCapability.LocalNavigation)
                add(SmartCapability.ConnectionRepair)
                add(SmartCapability.Keyboard)
                add(SmartCapability.Clipboard)
                if (macConnected) add(SmartCapability.MacCommand)
            },
            hourBucket = ((nowMillis / (60L * 60L * 1000L)) % 24L).toInt(),
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + FIVE_MINUTES_MS,
        )
    }

    private fun String.sanitizeContextSource(): String =
        lowercase().replace(Regex("[^a-z0-9._:-]+"), "_").trim('_').take(80)

    private companion object {
        const val FIVE_MINUTES_MS = 5L * 60L * 1000L
    }
}
