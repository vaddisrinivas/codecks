package io.codecks.ui.connection

import io.codecks.HidHost
import io.codecks.HidLifecycle
import io.codecks.HidState
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidHealthTest {
    @Test
    fun permissionMissingTakesPriority() {
        val health = HidState(
            lifecycle = HidLifecycle.Connected,
            isReady = true,
            isConnected = true,
        ).hidHealth(permissionGranted = false)

        assertEquals(HidHealthKind.PermissionMissing, health.kind)
        assertEquals("Setup needed", health.statusLabel())
        assertFalse(health.canSendInput)
    }

    @Test
    fun mapsServiceLifecycleToActionableLabels() {
        assertEquals(HidHealthKind.Stopped, HidState().hidHealth(permissionGranted = true).kind)
        assertEquals(
            HidHealthKind.Starting,
            HidState(status = "Registering HID app", lifecycle = HidLifecycle.Opening).hidHealth(permissionGranted = true).kind,
        )
        assertEquals(
            HidHealthKind.Failed,
            HidState(status = "HID registration failed", lifecycle = HidLifecycle.Failed).hidHealth(permissionGranted = true).kind,
        )
    }

    @Test
    fun separatesConfiguredTargetFromOnlineTarget() {
        val host = HidHost(address = "AA:BB", label = "Test MacBook")
        val configured = HidState(
            status = "HID registered",
            lifecycle = HidLifecycle.Ready,
            isReady = true,
            hosts = listOf(host),
            selectedHostAddress = host.address,
        ).hidHealth(permissionGranted = true)

        assertEquals(HidHealthKind.ReadyToConnect, configured.kind)
        assertEquals("Ready", configured.statusLabel())
        assertFalse(configured.canSendInput)

        val connected = HidState(
            status = "Connected Test MacBook",
            lifecycle = HidLifecycle.Connected,
            bluetoothPower = io.codecks.HidBluetoothPower.On,
            isReady = true,
            isConnected = true,
            hosts = listOf(host),
            selectedHostAddress = host.address,
        ).hidHealth(permissionGranted = true)

        assertEquals(HidHealthKind.Connected, connected.kind)
        assertEquals("Connected", connected.statusLabel())
        assertTrue(connected.canSendInput)
    }

    @Test
    fun readyWithoutTargetPromptsForTarget() {
        val health = HidState(
            status = "HID registered",
            lifecycle = HidLifecycle.Ready,
            isReady = true,
            hosts = emptyList(),
        ).hidHealth(permissionGranted = true)

        assertEquals(HidHealthKind.ReadyNoTarget, health.kind)
        assertEquals("Setup needed", health.statusLabel())
    }

    @Test
    fun exposesTypedIssuesAndSafeRemediation() {
        val permissionMissing = HidState(
            lifecycle = HidLifecycle.PermissionMissing,
        ).hidHealth(permissionGranted = false)
        val registrationFailed = HidState(
            status = "HID registration failed",
            lifecycle = HidLifecycle.Failed,
        ).hidHealth(permissionGranted = true)

        assertEquals(ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED, permissionMissing.issueCode)
        assertEquals(
            listOf(RemediationAction.RequestBluetoothPermission),
            permissionMissing.remediations,
        )
        assertEquals(ConnectionIssueCode.HID_PROFILE_REGISTRATION_FAILED, registrationFailed.issueCode)
        assertTrue(registrationFailed.remediations.contains(RemediationAction.RetryHidRegistration))
    }

    @Test
    fun transientConnectionFailureIsRetryingNotRegistrationFailure() {
        val health = HidState(
            status = "Connect request failed",
            lifecycle = HidLifecycle.Ready,
            isReady = true,
            issueCode = ConnectionIssueCode.CONNECT_BACKOFF,
            retry = io.codecks.HidRetryMetadata(
                disposition = io.codecks.HidRetryDisposition.Scheduled,
                attempt = 1,
                nextAttemptAtMillis = System.currentTimeMillis() + 2_000L,
            ),
        ).hidHealth(permissionGranted = true)

        assertEquals(HidHealthKind.Reconnecting, health.kind)
        assertEquals(ConnectionIssueCode.CONNECT_BACKOFF, health.issueCode)
    }
}
