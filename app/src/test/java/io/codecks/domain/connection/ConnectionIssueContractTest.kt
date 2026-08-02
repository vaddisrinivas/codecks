package io.codecks.domain.connection

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val contractJson = Json { prettyPrint = true }

class ConnectionIssueContractTest {
    @Test
    fun requiredIssueCasesHaveStableCodesSeverityAndRemediation() {
        assertEquals(
            setOf(
                "bluetooth_permission_denied",
                "bluetooth_disabled",
                "hid_profile_unregistered",
                "hid_profile_registration_failed",
                "hid_transport_timeout",
                "host_unpaired",
                "connecting",
                "connect_backoff",
                "mac_offline_or_asleep",
                "ssh_auth_failed",
                "ssh_host_key_mismatch",
                "mac_tool_missing",
                "unknown",
            ),
            ConnectionIssueCode.entries.map { it.persistedCode }.toSet(),
        )

        ConnectionIssueCode.entries.forEach { issue ->
            assertTrue(issue.persistedCode.matches(Regex("[a-z][a-z0-9_]{0,63}")))
            assertTrue("${issue.name} needs a remediation", issue.remediations.isNotEmpty())
            assertNotNull(issue.severity)
            issue.remediations.forEach { remediation ->
                assertTrue(remediation.persistedCode.matches(Regex("[a-z][a-z0-9_]{0,63}")))
            }
        }
    }

    @Test
    fun persistedIssueCodesRoundTripWithoutEnumOrdinals() {
        ConnectionIssueCode.entries.forEach { issue ->
            assertEquals(issue, ConnectionIssueCode.fromPersistedCode(issue.persistedCode))
            assertFalse(issue.persistedCode == issue.ordinal.toString())
        }
    }

    @Test
    fun unknownFutureCodesDegradeSafely() {
        assertEquals(ConnectionIssueCode.UNKNOWN, ConnectionIssueCode.fromPersistedCode("future_issue"))
        assertEquals(ConnectionIssueCode.UNKNOWN, ConnectionIssueCode.fromPersistedCode(null))
        assertEquals(CapabilityStatus.UNKNOWN, CapabilityStatus.fromPersistedCode("future_status"))
    }

    @Test
    fun capabilityChecksEnforceTypedSafeState() {
        val blocked = CapabilityCheck(
            capabilityCode = "mac.ssh",
            status = CapabilityStatus.BLOCKED,
            issueCode = ConnectionIssueCode.SSH_AUTH_FAILED,
            remediation = RemediationAction.ReenterSshCredentials,
            checkedAtEpochMs = 100L,
            validUntilEpochMs = 200L,
        )

        assertEquals("mac.ssh", blocked.capabilityCode)
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityCheck(
                capabilityCode = listOf("", "Users", "person", "private-key").joinToString("/"),
                status = CapabilityStatus.UNKNOWN,
                issueCode = null,
                remediation = null,
                checkedAtEpochMs = 100L,
                validUntilEpochMs = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityCheck(
                capabilityCode = "mac.ssh",
                status = CapabilityStatus.SATISFIED,
                issueCode = ConnectionIssueCode.SSH_AUTH_FAILED,
                remediation = RemediationAction.ReenterSshCredentials,
                checkedAtEpochMs = 100L,
                validUntilEpochMs = null,
            )
        }
    }

    @Test
    fun writesBoundedContractEvidence() {
        val contract = buildJsonArray {
            ConnectionIssueCode.entries.forEach { issue ->
                add(
                    buildJsonObject {
                        put("code", issue.persistedCode)
                        put("severity", issue.severity.persistedCode)
                        putJsonArray("remediations") {
                            issue.remediations.forEach { remediation ->
                                add(
                                    buildJsonObject {
                                        put("code", remediation.persistedCode)
                                        if (remediation is RemediationAction.OpenMissingToolInstructions) {
                                            put("toolCode", remediation.toolCode)
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
        val moduleDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val output = requireNotNull(moduleDirectory.parentFile)
            .resolve("build/ga-evidence/ARC-01/connection_issue_contract.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(contractJson.encodeToString(contract))

        assertTrue(output.isFile)
        val privateHomePrefix = listOf("", "Users", "").joinToString("/")
        assertFalse(output.readText().contains(privateHomePrefix))
    }
}
