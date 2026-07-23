package io.codecks.data.smart

import android.content.Context
import io.codecks.data.context.DeviceSurfaceContextSource
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.smart.SmartContext
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartPhoneContext
import io.codecks.domain.smart.SmartSurface
import java.time.Instant
import java.time.ZoneId

class SmartContextRepository(private val context: Context) {
    fun current(
        currentSurface: SmartSurface,
        selectedMacId: SmartMacId?,
        macConnected: Boolean,
        macInputConnected: Boolean,
        activeMacApp: SmartAppKey?,
        recentActionIds: List<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): SmartContext {
        val surface = DeviceSurfaceContextSource(context.applicationContext).current()
        return SmartContext(
            currentSurface = currentSurface,
            selectedMacId = selectedMacId,
            macConnected = macConnected,
            macInputConnected = macInputConnected,
            activeMacApp = activeMacApp,
            recentActionIds = recentActionIds.distinct().take(12),
            supportedCapabilities = buildSet {
                add(SmartCapability.LocalNavigation)
                add(SmartCapability.ConnectionRepair)
                add(SmartCapability.Keyboard)
                add(SmartCapability.Clipboard)
                if (macConnected) add(SmartCapability.MacCommand)
                if (macInputConnected) add(SmartCapability.MacInput)
            },
            hourBucket = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).hour,
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + FIVE_MINUTES_MS,
            phoneContext = if (surface.kind.name == "Desktop") SmartPhoneContext.Desktop else SmartPhoneContext.Phone,
        )
    }

    private companion object {
        const val FIVE_MINUTES_MS = 5L * 60L * 1000L
    }
}
