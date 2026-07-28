package io.codecks.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecksHelperUiStateTest {
    @Test
    fun notPairedExplainsSetupAndDisablesActions() {
        val state = codecksHelperUiState(
            pairedDisplayName = null,
            connectionKind = CodecksHelperConnectionKind.Idle,
            discoveredCount = 1,
        )

        assertEquals("Not paired", state.statusLabel)
        assertTrue(state.statusDetail.contains("Codecks Mac helper"))
        assertFalse(state.canConnect)
        assertFalse(state.canRunActions)
    }

    @Test
    fun pairedAndDiscoveredCanConnect() {
        val state = codecksHelperUiState(
            pairedDisplayName = "Example MacBook Pro",
            connectionKind = CodecksHelperConnectionKind.Idle,
            discoveredCount = 1,
        )

        assertEquals("Ready to connect", state.statusLabel)
        assertTrue(state.canConnect)
        assertFalse(state.canRunActions)
    }

    @Test
    fun pairedWithSavedEndpointCanConnectWithoutNearbyDiscovery() {
        val state = codecksHelperUiState(
            pairedDisplayName = "Example MacBook Pro",
            connectionKind = CodecksHelperConnectionKind.Idle,
            discoveredCount = 0,
            hasSavedEndpoint = true,
        )

        assertEquals("Ready to connect", state.statusLabel)
        assertTrue(state.statusDetail.contains("Saved Codecks helper endpoint"))
        assertTrue(state.canConnect)
    }

    @Test
    fun connectedCanRunVisibleActions() {
        val state = codecksHelperUiState(
            pairedDisplayName = "Example MacBook Pro",
            connectionKind = CodecksHelperConnectionKind.Connected,
            discoveredCount = 1,
        )

        assertEquals("Connected", state.statusLabel)
        assertFalse(state.canConnect)
        assertTrue(state.canRunActions)
        assertTrue(state.statusDetail.contains("Codecks helper actions"))
    }

    @Test
    fun failedStateCanReconnectWhenNearby() {
        val state = codecksHelperUiState(
            pairedDisplayName = "Example MacBook Pro",
            connectionKind = CodecksHelperConnectionKind.Failed,
            discoveredCount = 1,
            failureCode = "helper_authentication_failed",
        )

        assertEquals("Needs attention", state.statusLabel)
        assertTrue(state.statusDetail.contains("helper_authentication_failed"))
        assertTrue(state.canConnect)
        assertFalse(state.canRunActions)
    }

    @Test
    fun visibleSpotlightActionUsesProviderCompatibleRevision() {
        val mainActivity = File("src/main/java/io/codecks/MainActivity.kt").readText()

        assertTrue(mainActivity.contains("actionId = \"spotlight.search\""))
        assertTrue(mainActivity.contains("actionRevision = codecksSpotlightActionRevision(sanitizedQuery)"))
        assertFalse(mainActivity.contains("actionRevision = \"codecks-visible-spotlight-v1\""))
    }
}
