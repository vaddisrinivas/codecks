package io.codecks.data.smart

import android.content.Context
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartSurface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartContextRepositoryTest {
    @Test
    fun currentUsesOpaqueMacIdAndSurfaceCapabilities() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val repository = SmartContextRepository(appContext)
        val context = repository.current(
            currentSurface = SmartSurface.Deck,
            selectedMacId = SmartMacId("mac_opaque_id_123"),
            macConnected = true,
            macInputConnected = false,
            activeMacApp = SmartAppKey("com.google.Chrome"),
            recentActionIds = listOf("finder", "finder", "search", "browser"),
        )

        assertEquals("mac_opaque_id_123", context.selectedMacId?.value)
        assertTrue(context.supportedCapabilities.contains(SmartCapability.MacCommand))
        assertFalse(context.supportedCapabilities.contains(SmartCapability.MacInput))
        assertEquals(listOf("finder", "search", "browser"), context.recentActionIds)
    }

    @Test
    fun currentAddsMacInputCapabilityOnlyWhenConnected() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val repository = SmartContextRepository(appContext)

        val disconnected = repository.current(
            currentSurface = SmartSurface.Deck,
            selectedMacId = SmartMacId("mac_opaque_id_123"),
            macConnected = true,
            macInputConnected = false,
            activeMacApp = SmartAppKey("com.google.Chrome"),
            recentActionIds = listOf("finder"),
        )
        val connected = repository.current(
            currentSurface = SmartSurface.Deck,
            selectedMacId = SmartMacId("mac_opaque_id_123"),
            macConnected = true,
            macInputConnected = true,
            activeMacApp = SmartAppKey("com.google.Chrome"),
            recentActionIds = listOf("finder"),
        )

        assertFalse(disconnected.supportedCapabilities.contains(SmartCapability.MacInput))
        assertTrue(connected.supportedCapabilities.contains(SmartCapability.MacInput))
    }
}
