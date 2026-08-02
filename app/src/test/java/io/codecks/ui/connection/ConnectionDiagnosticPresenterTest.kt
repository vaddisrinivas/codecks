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

        assertEquals(ConnectionDiagnosticState.Connecting, connecting.state)
        assertEquals(3, connecting.attempt)
        assertEquals(ConnectionDiagnosticState.Backoff, backoff.state)
        assertEquals(4, backoff.attempt)
        assertEquals(15, backoff.retryInSeconds)
        assertEquals(listOf(ConnectionRepairAction.RetryNow), backoff.repairActions)
    }

    @Test
    fun failuresRemainDistinctAndUnknownFallsBackSafely() {
        val expected = mapOf(
            ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP to ConnectionDiagnosticState.Offline,
            ConnectionIssueCode.SSH_AUTH_FAILED to ConnectionDiagnosticState.AuthenticationFailed,
            ConnectionIssueCode.SSH_HOST_KEY_MISMATCH to ConnectionDiagnosticState.HostKeyMismatch,
            ConnectionIssueCode.MAC_TOOL_MISSING to ConnectionDiagnosticState.ToolMissing,
            ConnectionIssueCode.UNKNOWN to ConnectionDiagnosticState.Unknown,
        )

        expected.forEach { (issue, state) ->
            assertEquals(
                state,
                presentConnectionDiagnostic(readyConfig, ConnectionOperation.Idle, issue).state,
            )
        }
    }

    @Test
    fun setupSurfacesDoNotRenderRawExceptionText() {
        val connectionScreen = File("src/main/java/io/codecks/ui/connection/ConnectionScreen.kt").readText()
        val settingsScreen = File("src/main/java/io/codecks/ui/settings/SettingsScreen.kt").readText()

        assertFalse(connectionScreen.contains("Text(error"))
        assertFalse(settingsScreen.contains("state.error?.let { Text(it"))
        assertTrue(connectionScreen.contains("state.connectionDiagnostic()"))
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
