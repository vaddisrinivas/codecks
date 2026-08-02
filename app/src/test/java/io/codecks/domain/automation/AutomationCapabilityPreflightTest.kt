package io.codecks.domain.automation

import io.codecks.domain.connection.CapabilityStatus
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction
import io.codecks.domain.smart.SmartCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationCapabilityPreflightTest {
    @Test
    fun connectionFailureCodesRemainDistinctAndActionable() {
        val offline = automationConnectionPreflightCheck(
            ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP,
            checkedAtEpochMs = 10L,
        )
        val auth = automationConnectionPreflightCheck(
            ConnectionIssueCode.SSH_AUTH_FAILED,
            checkedAtEpochMs = 11L,
        )
        val hostKey = automationConnectionPreflightCheck(
            ConnectionIssueCode.SSH_HOST_KEY_MISMATCH,
            checkedAtEpochMs = 12L,
        )

        assertEquals(CapabilityStatus.RETRYABLE, offline.capability.status)
        assertEquals(RemediationAction.OpenMacWakeHelp, offline.capability.remediation)
        assertEquals(CapabilityStatus.BLOCKED, auth.capability.status)
        assertEquals(RemediationAction.ReenterSshCredentials, auth.capability.remediation)
        assertEquals(CapabilityStatus.BLOCKED, hostKey.capability.status)
        assertEquals(RemediationAction.ReviewChangedHostKey, hostKey.capability.remediation)
    }

    @Test
    fun missingToolHasStableCapabilityAndSpecificRemediation() {
        val check = automationToolPreflightCheck(
            toolCode = "Better Display CLI !!! with a very long human supplied name",
            available = false,
            checkedAtEpochMs = 20L,
        )

        assertEquals(CapabilityStatus.BLOCKED, check.capability.status)
        assertTrue(check.capability.capabilityCode.startsWith("automation.mac.tool."))
        assertTrue(check.capability.capabilityCode.length <= 64)
        assertTrue(check.capability.remediation is RemediationAction.OpenMissingToolInstructions)
    }

    @Test
    fun everyMandatoryCheckMustBeSatisfied() {
        val optionalFailure = receipt(
            listOf(
                automationToolPreflightCheck("optional_tool", false, 20L).copy(mandatory = false),
                automationConnectionPreflightCheck(null, 20L),
            ),
        )
        val mandatoryFailure = receipt(
            listOf(
                automationToolPreflightCheck("required_tool", false, 20L),
                automationConnectionPreflightCheck(null, 20L),
            ),
        )

        assertTrue(optionalFailure.mandatoryChecksSatisfied())
        assertFalse(mandatoryFailure.mandatoryChecksSatisfied())
        assertFalse(receipt(emptyList()).mandatoryChecksSatisfied())
    }

    private fun receipt(checks: List<AutomationPreflightCheck>) =
        AutomationPreflightReceipt(
            recipeRevision = "revision",
            checkedAtMillis = 20L,
            macIdentity = "host",
            targetId = "current",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            checks = checks,
            commandTools = emptySet(),
            commandPaths = emptySet(),
            permissionSnapshot = emptySet(),
        )
}
