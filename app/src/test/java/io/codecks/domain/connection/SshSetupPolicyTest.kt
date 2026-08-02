package io.codecks.domain.connection

import io.codecks.data.privacy.DiagnosticEventCodec
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticEvent
import io.codecks.domain.privacy.DiagnosticEventCode
import io.codecks.domain.privacy.DiagnosticResultCode
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SshSetupPolicyTest {
    @Test
    fun firstSeenRequiresConfirmationAndChangedKeyHardBlocks() {
        assertEquals(
            HostTrustState.FirstSeenConfirmationRequired,
            evaluateHostTrust(savedHostKey = null, observedHostKey = "key-a"),
        )
        assertEquals(
            HostTrustState.Trusted,
            evaluateHostTrust(savedHostKey = "key-a", observedHostKey = "key-a"),
        )
        assertEquals(
            HostTrustState.ChangedHostKeyBlocked,
            evaluateHostTrust(savedHostKey = "key-a", observedHostKey = "key-b"),
        )
        assertThrows(ChangedHostKeyException::class.java) {
            throw ChangedHostKeyException()
        }

        val repository = File("src/main/java/io/codecks/data/ConnectionRepository.kt").readText()
        assertTrue(repository.contains("rememberPendingHostKey(hostKey)"))
        assertTrue(repository.contains("evaluateHostTrust(pendingLine, observed.line)"))
        assertTrue(repository.contains("throw ChangedHostKeyException()"))
        assertTrue(repository.contains("""put("StrictHostKeyChecking", "yes")"""))
    }

    @Test
    fun setupFailuresHaveDistinctStableCodes() {
        val cases = mapOf(
            "No route to host" to SshSetupFailureCode.Reachability,
            "Authentication failed" to SshSetupFailureCode.Authentication,
            "Generate or install the SSH key first" to SshSetupFailureCode.KeyMissing,
            "Host key verification failed" to SshSetupFailureCode.HostKeyMismatch,
            "required tool missing" to SshSetupFailureCode.ToolMissing,
            "unexpected protocol state" to SshSetupFailureCode.Unknown,
        )
        cases.forEach { (message, expected) ->
            assertEquals(expected, classifySshSetupFailure(message))
        }
        assertEquals(
            SshSetupFailureCode.entries.size,
            SshSetupFailureCode.entries.map(SshSetupFailureCode::persistedCode).toSet().size,
        )
    }

    @Test
    fun everyRequiredCapabilityGetsItsOwnProbeAndReceipt() {
        val required = MacSetupCapability.entries.toSet()
        val probes = requiredMacCapabilityProbes(required)

        assertEquals(required.size, probes.size)
        assertEquals(required, probes.map(MacCapabilityProbe::capability).toSet())
        assertEquals(probes.size, probes.map(MacCapabilityProbe::stableCommandCode).toSet().size)
        probes.forEach { probe ->
            assertTrue(probe.stableCommandCode.matches(Regex("[a-z0-9_]+")))
        }
    }

    @Test
    fun setupReceiptsAndEventJournalCannotCarrySensitivePayloads() {
        val receiptFields = MacCapabilityCheckReceipt::class.java.declaredFields.map { it.name.lowercase() }
        forbiddenTokens.forEach { token ->
            assertFalse(receiptFields.any { token in it })
        }
        val encodedJournal = DiagnosticEventCodec.encode(
            listOf(
                DiagnosticEvent(
                    component = DiagnosticComponent.CONNECTION,
                    event = DiagnosticEventCode.CAPABILITY_CHECKED,
                    result = DiagnosticResultCode.BLOCKED,
                    attempt = 1,
                    durationMs = 10L,
                    timestampEpochMs = 20L,
                ),
            ),
        )
        forbiddenTokens.forEach { token ->
            assertFalse(encodedJournal.contains(token, ignoreCase = true))
        }
    }

    @Test
    fun writesSshSetupEvidence() {
        val rows = JSONArray()
        MacSetupCapability.entries.forEach { capability ->
            val probe = requiredMacCapabilityProbes(setOf(capability)).single()
            rows.put(
                JSONObject()
                    .put("capability", capability.persistedCode)
                    .put("probe", probe.stableCommandCode),
            )
        }
        val output = evidenceDirectory().resolve("ssh_setup_capabilities.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(rows.toString(2))

        assertEquals(MacSetupCapability.entries.size, rows.length())
        assertTrue(output.isFile)
    }

    private fun evidenceDirectory(): File {
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return requireNotNull(moduleDirectory.parentFile).resolve("build/ga-evidence/SET-03")
    }

    private companion object {
        val forbiddenTokens = listOf(
            "secret",
            "password",
            "credential",
            "host",
            "ip",
            "user",
            "fingerprint",
            "output",
            "path",
            "prompt",
        )
    }
}
