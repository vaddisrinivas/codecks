package io.codecks.domain.automation

import io.codecks.domain.connection.CapabilityCheck
import io.codecks.domain.connection.CapabilityStatus
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction

object AutomationCapabilityCodes {
    const val Identity = "automation.ssh.identity"
    const val Connection = "automation.ssh.connection"
    const val Provider = "automation.provider"
    const val Tool = "automation.mac.tool"
    const val App = "automation.mac.app"
    const val Target = "automation.target"
    const val Permission = "automation.permission"
    const val Path = "automation.mac.path"
    const val ActionContract = "automation.action.contract"
    const val Schedule = "automation.schedule"
}

fun automationConnectionPreflightCheck(
    issueCode: ConnectionIssueCode?,
    checkedAtEpochMs: Long,
): AutomationPreflightCheck {
    val issue = issueCode
    val status = when (issue) {
        null -> CapabilityStatus.SATISFIED
        ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP,
        ConnectionIssueCode.CONNECTING,
        ConnectionIssueCode.CONNECT_BACKOFF,
        -> CapabilityStatus.RETRYABLE
        ConnectionIssueCode.SSH_AUTH_FAILED,
        ConnectionIssueCode.SSH_HOST_KEY_MISMATCH,
        ConnectionIssueCode.HOST_UNPAIRED,
        -> CapabilityStatus.BLOCKED
        else -> CapabilityStatus.UNKNOWN
    }
    return AutomationPreflightCheck.typed(
        area = AutomationPreflightArea.Connection,
        capabilityCode = AutomationCapabilityCodes.Connection,
        status = status,
        issueCode = issue,
        remediation = issue?.remediations?.firstOrNull(),
        checkedAtEpochMs = checkedAtEpochMs,
        message = status.persistedCode,
    )
}

fun automationToolPreflightCheck(
    toolCode: String,
    available: Boolean,
    checkedAtEpochMs: Long,
): AutomationPreflightCheck =
    AutomationPreflightCheck.typed(
        area = AutomationPreflightArea.Tool,
        capabilityCode = "${AutomationCapabilityCodes.Tool}.${toolCode.stableCapabilitySegment()}",
        status = if (available) CapabilityStatus.SATISFIED else CapabilityStatus.BLOCKED,
        issueCode = ConnectionIssueCode.MAC_TOOL_MISSING.takeUnless { available },
        remediation = RemediationAction.OpenMissingToolInstructions(toolCode.stableCapabilitySegment())
            .takeUnless { available },
        checkedAtEpochMs = checkedAtEpochMs,
        message = if (available) "satisfied" else "blocked",
    )

fun AutomationPreflightReceipt.mandatoryChecksSatisfied(): Boolean =
    checks.isNotEmpty() &&
        checks.filter(AutomationPreflightCheck::mandatory).isNotEmpty() &&
        checks.filter(AutomationPreflightCheck::mandatory).all { check ->
            check.capability.status == CapabilityStatus.SATISFIED
        }

internal fun AutomationPreflightArea.defaultCapability(
    passed: Boolean,
    checkedAtEpochMs: Long = 0L,
): CapabilityCheck {
    val issue = if (passed) null else defaultIssue()
    val status = when {
        passed -> CapabilityStatus.SATISFIED
        issue == ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP -> CapabilityStatus.RETRYABLE
        else -> CapabilityStatus.BLOCKED
    }
    return CapabilityCheck(
        capabilityCode = defaultCapabilityCode(),
        status = status,
        issueCode = issue,
        remediation = issue?.remediations?.firstOrNull() ?: RemediationAction.ContactSupport.takeUnless { passed },
        checkedAtEpochMs = checkedAtEpochMs,
        validUntilEpochMs = null,
    )
}

internal fun AutomationPreflightArea.defaultCapabilityCode(): String = when (this) {
    AutomationPreflightArea.Identity -> AutomationCapabilityCodes.Identity
    AutomationPreflightArea.Connection -> AutomationCapabilityCodes.Connection
    AutomationPreflightArea.Provider -> AutomationCapabilityCodes.Provider
    AutomationPreflightArea.Tool -> AutomationCapabilityCodes.Tool
    AutomationPreflightArea.App -> AutomationCapabilityCodes.App
    AutomationPreflightArea.Target -> AutomationCapabilityCodes.Target
    AutomationPreflightArea.Permission -> AutomationCapabilityCodes.Permission
    AutomationPreflightArea.Path -> AutomationCapabilityCodes.Path
    AutomationPreflightArea.ActionContract -> AutomationCapabilityCodes.ActionContract
    AutomationPreflightArea.Schedule -> AutomationCapabilityCodes.Schedule
}

private fun AutomationPreflightArea.defaultIssue(): ConnectionIssueCode = when (this) {
    AutomationPreflightArea.Identity,
    AutomationPreflightArea.Target,
    -> ConnectionIssueCode.HOST_UNPAIRED
    AutomationPreflightArea.Connection,
    AutomationPreflightArea.Provider,
    -> ConnectionIssueCode.MAC_OFFLINE_OR_ASLEEP
    AutomationPreflightArea.Tool -> ConnectionIssueCode.MAC_TOOL_MISSING
    AutomationPreflightArea.App,
    AutomationPreflightArea.Permission,
    AutomationPreflightArea.Path,
    AutomationPreflightArea.ActionContract,
    AutomationPreflightArea.Schedule,
    -> ConnectionIssueCode.UNKNOWN
}

private fun String.stableCapabilitySegment(): String =
    lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "_")
        .trim('_')
        .take(32)
        .ifBlank { "unknown" }
