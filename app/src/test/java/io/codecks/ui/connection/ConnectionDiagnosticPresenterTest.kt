package io.codecks.ui.connection

import io.codecks.data.ConnectionConfig
import io.codecks.domain.connection.ConnectionIssueCode
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDiagnosticPresenterTest {
    @Test
    fun everyTypedIssueHasDeterministicSafePresentation() {
        ConnectionIssueCode.entries.forEach { issue ->
            val first = presentConnectionDiagnostic(readyConfig, ConnectionOperation.Idle, issue)
            val second = presentConnectionDiagnostic(readyConfig, ConnectionOperation.Idle, issue)

            assertEquals(first, second)
            assertEquals(issue, first.issueCode)
            assertTrue(first.title.isNotBlank())
            assertTrue(first.detail.isNotBlank())
            assertFalse(first.detail.contains("exception", ignoreCase = true))
        }
    }

    @Test
    fun connectingAndBackoffExposeAttemptState() {
        val connecting = presentConnectionDiagnostic(
            config = readyConfig,
            operation = ConnectionOperation.Connecting,
            issueCode = null,
            attempt = 3,
        )
        val backoff = presentConnectionDiagnostic(
            config = readyConfig,
            operation = ConnectionOperation.Idle,
            issueCode = ConnectionIssueCode.CONNECT_BACKOFF,
            attempt = 4,
            retryAtMillis = 25_000L,
            nowMillis = 10_000L,
        )

        assertEquals(ConnectionPresentationState.Checking, connecting.state)
        assertEquals(3, connecting.attempt)
        assertEquals(ConnectionPresentationState.Reconnecting, backoff.state)
        assertEquals(4, backoff.attempt)
        assertEquals(15, backoff.retryInSeconds)
        assertEquals(listOf(ConnectionRepair.RetryNow), backoff.repairActions)
    }

    @Test
    fun failuresRemainDistinctAndUnknownFallsBackSafely() {
        val expected = mapOf(
            ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP to ConnectionPresentationState.Sleeping,
            ConnectionIssueCode.SSH_AUTH_FAILED to ConnectionPresentationState.AuthenticationFailed,
            ConnectionIssueCode.SSH_HOST_KEY_MISMATCH to ConnectionPresentationState.IdentityMismatch,
            ConnectionIssueCode.MAC_TOOL_MISSING to ConnectionPresentationState.SetupRequired,
            ConnectionIssueCode.UNKNOWN to ConnectionPresentationState.Failed,
        )

        expected.forEach { (issue, state) ->
            assertEquals(
                state,
                presentConnectionDiagnostic(readyConfig, ConnectionOperation.Idle, issue).state,
            )
        }
    }

    @Test
    fun `diagnostic state and repairs are identical to unified presentation`() {
        ConnectionIssueCode.entries.forEach { issue ->
            val diagnostic = presentConnectionDiagnostic(readyConfig, ConnectionOperation.Idle, issue)
            val unified = ConnectionHealth(
                kind = when (issue) {
                    ConnectionIssueCode.SSH_AUTH_FAILED,
                    ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
                    ConnectionIssueCode.HOST_UNPAIRED,
                    -> ConnectionHealthKind.AuthFailed
                    ConnectionIssueCode.SSH_HOST_KEY_MISMATCH -> ConnectionHealthKind.FingerprintMismatch
                    ConnectionIssueCode.CONNECTING -> ConnectionHealthKind.Connecting
                    else -> ConnectionHealthKind.Offline
                },
                title = "ignored",
                detail = "ignored",
                issueOverride = issue,
            ).toUnifiedConnectionPresentation()

            assertEquals(unified.state, diagnostic.state)
            assertEquals(unified.repairs, diagnostic.repairActions)
            assertEquals(unified.supportCode, diagnostic.supportCode)
        }
    }

    @Test
    fun `production diagnostics expose only typed repair promises`() {
        ConnectionIssueCode.entries.forEach { issue ->
            presentConnectionDiagnostic(readyConfig, ConnectionOperation.Idle, issue)
                .repairActions
                .forEach { repair -> assertTrue(repair in ConnectionRepair.entries) }
        }
        assertFalse(ConnectionRepair.entries.any { it.name.contains("Wake", ignoreCase = true) })
    }

    @Test
    fun setupSurfacesDoNotRenderRawExceptionText() {
        val settingsScreen = File(
            "src/main/java/io/codecks/ui/settings/SettingsConnectionSections.kt",
        ).readText()

        assertFalse(settingsScreen.contains("state.error?.let { Text(it"))
        assertTrue(settingsScreen.contains("state.connectionDiagnostic()"))
    }

    @Test
    fun writesCompleteDiagnosticEvidence() {
        val matrix = JSONArray()
        ConnectionIssueCode.entries.forEach { issue ->
            val diagnostic = presentConnectionDiagnostic(
                config = readyConfig,
                operation = ConnectionOperation.Idle,
                issueCode = issue,
                attempt = 2,
                retryAtMillis = 20_000L,
                nowMillis = 10_000L,
            )
            matrix.put(
                JSONObject()
                    .put("issue", issue.persistedCode)
                    .put("state", diagnostic.state.name)
                    .put("title", diagnostic.title)
                    .put("detail", diagnostic.detail)
                    .put("attempt", diagnostic.attempt)
                    .put("retryInSeconds", diagnostic.retryInSeconds)
                    .put("repairActions", JSONArray(diagnostic.repairActions.map { it.name })),
            )
        }
        val output = evidenceDirectory().resolve("connection_diagnostic_matrix.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(matrix.toString(2))

        assertEquals(ConnectionIssueCode.entries.size, matrix.length())
        assertTrue(output.isFile)
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/HID-05")
    }

    private companion object {
        val readyConfig = ConnectionConfig(
            host = "mac.local",
            port = 22,
            user = "codecks",
            hasKey = true,
            hostKey = "mac.local ssh-ed25519 key",
        )
    }
}
