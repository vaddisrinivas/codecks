package io.codex.s23deck.ui.connection

import io.codex.s23deck.data.ConnectionConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionHealthTest {
    @Test
    fun mapsSetupProgressIntoDistinctHealthStates() {
        assertEquals(
            ConnectionHealthKind.NotConfigured,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, error = null).kind,
        )
        assertEquals(
            ConnectionHealthKind.NeedsFingerprint,
            connectionHealth(ConnectionConfig(host = "mac.local", user = "me"), ConnectionOperation.Idle, error = null).kind,
        )
        assertEquals(
            ConnectionHealthKind.NeedsKey,
            connectionHealth(
                ConnectionConfig(host = "mac.local", user = "me", hostKey = "mac.local ssh-ed25519 key"),
                ConnectionOperation.Idle,
                error = null,
            ).kind,
        )
        assertEquals(
            ConnectionHealthKind.Ready,
            connectionHealth(
                ConnectionConfig(host = "mac.local", user = "me", hostKey = "mac.local ssh-ed25519 key", hasKey = true),
                ConnectionOperation.Idle,
                error = null,
            ).kind,
        )
    }

    @Test
    fun operationTakesPriorityOverSavedConfig() {
        val ready = ConnectionConfig(host = "mac.local", user = "me", hostKey = "key", hasKey = true)

        assertEquals(ConnectionHealthKind.Scanning, connectionHealth(ready, ConnectionOperation.Scanning, null).kind)
        assertEquals(ConnectionHealthKind.Verifying, connectionHealth(ready, ConnectionOperation.Verifying, null).kind)
        assertEquals(ConnectionHealthKind.Connecting, connectionHealth(ready, ConnectionOperation.Connecting, null).kind)
        assertEquals(ConnectionHealthKind.Testing, connectionHealth(ready, ConnectionOperation.Testing, null).kind)
    }

    @Test
    fun mapsFailureMessagesIntoActionableHealthStates() {
        assertEquals(
            ConnectionHealthKind.FingerprintMismatch,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Host key verification failed").kind,
        )
        assertEquals(
            ConnectionHealthKind.AuthFailed,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "Permission denied, please try again").kind,
        )
        assertEquals(
            ConnectionHealthKind.Offline,
            connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "No route to host").kind,
        )
    }

    @Test
    fun exposesStableLabelsForSharedSurfaces() {
        assertEquals(
            "Ready",
            connectionHealth(
                ConnectionConfig(host = "mac.local", user = "me", hostKey = "mac.local ssh-ed25519 key", hasKey = true),
                ConnectionOperation.Idle,
                error = null,
            ).statusLabel(),
        )
        assertEquals("Setup", connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, error = null).statusLabel())
        assertEquals("Offline", connectionHealth(ConnectionConfig(), ConnectionOperation.Idle, "No route to host").statusLabel())
    }
}
