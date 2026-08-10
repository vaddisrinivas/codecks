package io.codecks.ui.connection

import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.platform.helper.ReactiveHelperSessionStatus
import io.codecks.ui.clipboard.ClipboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedConnectionPresentationTest {
    @Test
    fun `bluetooth permission has typed repair and redacted support code`() {
        val result = HidHealth(
            HidHealthKind.PermissionMissing,
            title = "raw title",
            detail = "device 01:23:45:67:89",
        ).toUnifiedConnectionPresentation()

        assertEquals(ConnectionPresentationState.PermissionRequired, result.state)
        assertEquals(listOf(ConnectionRepair.RequestPermission), result.repairs)
        assertEquals("CX-HID-PERM", result.supportCode)
        assertFalse(result.detail.contains("01:23"))
    }

    @Test
    fun `ssh authentication and host key failures remain distinct`() {
        val auth = ConnectionHealth(
            ConnectionHealthKind.AuthFailed,
            "raw",
            "password hunter2",
        ).toUnifiedConnectionPresentation()
        val hostKey = ConnectionHealth(
            ConnectionHealthKind.FingerprintMismatch,
            "raw",
            "SHA256:private-fingerprint",
        ).toUnifiedConnectionPresentation()

        assertEquals(ConnectionPresentationState.AuthenticationFailed, auth.state)
        assertEquals(ConnectionRepair.ReenterCredentials, auth.repairs.single())
        assertEquals("CX-SSH-AUTH", auth.supportCode)
        assertEquals(ConnectionPresentationState.IdentityMismatch, hostKey.state)
        assertEquals(ConnectionRepair.ReviewIdentity, hostKey.repairs.single())
        assertEquals("CX-SSH-HOSTKEY", hostKey.supportCode)
        assertFalse(auth.detail.contains("hunter2"))
        assertFalse(hostKey.detail.contains("SHA256"))
    }

    @Test
    fun `sleeping Mac and reconnect backoff have different recovery`() {
        val sleeping = ConnectionHealth(
            ConnectionHealthKind.Offline,
            "raw",
            "192.168.1.2",
        ).toUnifiedConnectionPresentation()
        val reconnecting = ConnectionHealth(
            ConnectionHealthKind.Offline,
            "raw",
            "retry",
            issueOverride = ConnectionIssueCode.CONNECT_BACKOFF,
        ).toUnifiedConnectionPresentation()

        assertEquals(ConnectionPresentationState.Sleeping, sleeping.state)
        assertEquals(listOf(ConnectionRepair.RetryNow), sleeping.repairs)
        assertEquals(ConnectionPresentationState.Reconnecting, reconnecting.state)
        assertEquals(listOf(ConnectionRepair.RetryNow), reconnecting.repairs)
    }

    @Test
    fun `helper failure codes map to typed diagnosis without echoing unknown text`() {
        val identity = ReactiveHelperSessionStatus.Failed("helper_identity_mismatch")
            .toUnifiedConnectionPresentation()
        val unknown = ReactiveHelperSessionStatus.Failed("secret-host-user-token")
            .toUnifiedConnectionPresentation()

        assertEquals(ConnectionPresentationState.IdentityMismatch, identity.state)
        assertEquals("CX-HLP-IDENTITY", identity.supportCode)
        assertEquals(ConnectionPresentationState.Failed, unknown.state)
        assertEquals("CX-HLP-FAIL", unknown.supportCode)
        assertFalse(unknown.detail.contains("secret-host-user-token"))
    }

    @Test
    fun `clipboard distinguishes setup offline conflict and ready`() {
        val setup = ClipboardUiState().toUnifiedConnectionPresentation()
        val offline = ClipboardUiState(connectionConfigured = true, isRemoteOffline = true)
            .toUnifiedConnectionPresentation()
        val conflict = ClipboardUiState(connectionReady = true, hasConflict = true)
            .toUnifiedConnectionPresentation()
        val ready = ClipboardUiState(connectionReady = true).toUnifiedConnectionPresentation()

        assertEquals(ConnectionPresentationState.SetupRequired, setup.state)
        assertEquals(ConnectionPresentationState.Sleeping, offline.state)
        assertEquals(listOf(ConnectionRepair.RetryNow), offline.repairs)
        assertEquals(ConnectionPresentationState.Conflict, conflict.state)
        assertEquals(ConnectionPresentationState.Ready, ready.state)
        assertTrue(ready.isReady)
    }

    @Test
    fun `support codes are stable and contain no supplied identifiers`() {
        val supplied = "Srinivas-Mac.local|user@example.com|SHA256:private-fingerprint|10.0.0.4"
        val presentations = listOf(
            HidHealth(HidHealthKind.Failed, supplied, supplied).toUnifiedConnectionPresentation(),
            ConnectionHealth(ConnectionHealthKind.Offline, supplied, supplied).toUnifiedConnectionPresentation(),
            ReactiveHelperSessionStatus.Failed(supplied).toUnifiedConnectionPresentation(),
            ClipboardUiState(connectionReady = true, lastFailureClass = supplied).toUnifiedConnectionPresentation(),
        )

        presentations.forEach {
            assertTrue(it.supportCode.startsWith("CX-"))
            assertFalse(it.title.contains(supplied))
            assertFalse(it.detail.contains(supplied))
            assertFalse(it.supportCode.contains(supplied))
        }
    }

    @Test
    fun `unknown SSH and terminal HID failure are redacted and repairable`() {
        val secret = "exception host=private.local user=owner fingerprint=SHA256:secret"
        val unknown = ConnectionHealth(
            kind = ConnectionHealthKind.Offline,
            title = secret,
            detail = secret,
            issueOverride = ConnectionIssueCode.UNKNOWN,
        ).toUnifiedConnectionPresentation()
        val hidFailure = HidHealth(HidHealthKind.Failed, secret, secret)
            .toUnifiedConnectionPresentation()

        assertEquals(ConnectionPresentationState.Failed, unknown.state)
        assertTrue(ConnectionRepair.ContactSupport in unknown.repairs)
        assertEquals(ConnectionPresentationState.Failed, hidFailure.state)
        assertTrue(ConnectionRepair.RetryNow in hidFailure.repairs)
        listOf(unknown, hidFailure).forEach {
            assertFalse(it.title.contains("private.local"))
            assertFalse(it.detail.contains("owner"))
            assertFalse(it.detail.contains("SHA256"))
        }
    }

    @Test
    fun `actual SSH diagnostic never echoes injected exception text`() {
        val secret = "host private.local user owner fingerprint SHA256:secret unexpected"
        val diagnostic = ConnectionUiState(error = secret).connectionDiagnostic()

        assertEquals("CX-SSH-HOSTKEY", diagnostic.supportCode)
        assertFalse(diagnostic.title.contains(secret))
        assertFalse(diagnostic.detail.contains(secret))
        assertEquals(listOf(ConnectionRepair.ReviewIdentity), diagnostic.repairActions)
    }
}
