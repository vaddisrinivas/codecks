package io.codecks.data.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.ExecutionAuthorization
import io.codecks.domain.automation.AutomationLiveTestAssertion
import io.codecks.domain.automation.AutomationLiveTestCleanupCode
import io.codecks.domain.automation.AutomationLiveTestCleanup
import io.codecks.domain.automation.AutomationLiveTestOutcomeCode
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationLiveTestTerminalStatus
import io.codecks.domain.automation.AutomationStepTerminalStatus
import io.codecks.domain.automation.AutomationUndoGuarantee
import io.codecks.domain.automation.AutomationGateStamp
import io.codecks.domain.automation.AutomationPreflightArea
import io.codecks.domain.automation.AutomationPreflightCheck
import io.codecks.domain.automation.AutomationPreflightReceipt
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationWorkerOutcome
import io.codecks.domain.automation.AutomationWorkerOutcomeCode
import io.codecks.domain.automation.AutomationWorkerRetryDisposition
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.automation.isOpaqueAutomationConnectionIdentity
import io.codecks.domain.automation.redactedTerminal
import io.codecks.domain.device.DeviceGroupId
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.connection.CapabilityCheck
import io.codecks.domain.connection.CapabilityStatus
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.domain.connection.RemediationAction
import io.codecks.domain.connection.persistedCode
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.putIfMissing(key: String, value: Any) {
    if (!has(key) || isNull(key)) put(key, value)
}

internal fun ActionResult.toStoredAutomationSummary(): AutomationRunSummary =
    AutomationRunSummary(
        status = status,
        message = "Automation ${status.name.lowercase()}",
        logs = "",
        timestampMillis = timestampMillis,
    )

internal fun quarantinePayload(raw: String, store: String): String = JSONObject().apply {
    put("schemaVersion", 2)
    put("store", store)
    put("quarantinedAtMillis", System.currentTimeMillis())
    put("payloadLength", raw.length.coerceAtMost(MAX_QUARANTINE_REPORTED_LENGTH))
    put("payloadSha256", raw.sha256())
}.toString()

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private const val MAX_QUARANTINE_REPORTED_LENGTH = 10_000_000

internal fun AutomationRunSummary.toJson(): JSONObject = JSONObject().apply {
    put("status", status.name)
    put("message", message)
    put("logs", logs)
    put("timestampMillis", timestampMillis)
}

internal fun AutomationWorkerOutcome.toJson(): JSONObject = JSONObject().apply {
    put("code", code.persistedCode)
    put("checkedAtMillis", checkedAtMillis)
    put("scheduledRevision", scheduledRevision)
    put("currentRevision", currentRevision)
    put("retryDisposition", retryDisposition.persistedCode)
    put("workerAttempt", workerAttempt)
}

internal fun JSONObject?.toAutomationWorkerOutcome(): AutomationWorkerOutcome? {
    if (this == null) return null
    return AutomationWorkerOutcome(
        code = AutomationWorkerOutcomeCode.fromPersistedCode(optString("code")),
        checkedAtMillis = optLong("checkedAtMillis", 0L),
        scheduledRevision = optString("scheduledRevision"),
        currentRevision = optString("currentRevision"),
        retryDisposition = AutomationWorkerRetryDisposition.fromPersistedCode(
            optString("retryDisposition"),
        ),
        workerAttempt = optInt("workerAttempt", 0),
    )
}

internal fun JSONObject.toAutomationRunSummary(): AutomationRunSummary =
    optString("status").let { rawStatus ->
        val status = ActionResultStatus.entries.firstOrNull { it.name == rawStatus }
            ?: ActionResultStatus.Failed
        AutomationRunSummary(
        status = status,
        message = "Automation ${status.name.lowercase()}",
        logs = "",
        timestampMillis = optLong("timestampMillis", System.currentTimeMillis()),
    )
    }

internal fun JSONObject.putCommonTrust(spec: ActionSpec) {
    put("commandOrigin", spec.commandOrigin.name)
    put("commandReview", spec.review.toJson())
    put("confirmationTitle", spec.confirmationTitle)
    put("confirmationBody", spec.confirmationBody)
    put("riskReason", spec.riskReason)
    put("executionAuthorization", spec.authorization.toJson())
}

internal fun CommandReview.toJson(): JSONObject = JSONObject().apply {
    put("reviewedRevision", reviewedRevision)
    put("checkedRevision", checkedRevision)
}

internal fun AutomationPreflightReceipt.toJson(): JSONObject = JSONObject().apply {
    require(isOpaqueAutomationConnectionIdentity(macIdentity)) {
        "Automation preflight identity must be opaque."
    }
    put("receiptId", receiptId)
    put("recipeRevision", recipeRevision)
    put("checkedAtMillis", checkedAtMillis)
    put("macIdentity", macIdentity)
    put("targetId", targetId)
    put("requiredCapabilities", JSONArray().apply {
        requiredCapabilities.forEach { put(it.name) }
    })
    put("commandTools", JSONArray().apply {
        commandTools.forEach { put(it) }
    })
    put("commandPaths", JSONArray().apply {
        commandPaths.forEach { put(it) }
    })
    put("commandApps", JSONArray().apply {
        commandApps.forEach { put(it) }
    })
    put("permissionSnapshot", JSONArray().apply {
        permissionSnapshot.forEach { put(it) }
    })
    put("requiredPermissions", JSONArray().apply {
        requiredPermissions.forEach { put(it) }
    })
    put("requiredCheckCodes", JSONArray().apply {
        requiredCheckCodes.forEach { put(it) }
    })
    put("checks", JSONArray().apply {
        checks.forEach { check ->
            put(
                JSONObject().apply {
                    put("area", check.area.name)
                    put("passed", check.passed)
                    put("message", check.message)
                    put("capabilityCode", check.capability.capabilityCode)
                    put("capabilityStatus", check.capability.status.persistedCode)
                    check.capability.issueCode?.let { put("issueCode", it.persistedCode) }
                    check.capability.remediation?.let { remediation ->
                        put("remediationCode", remediation.persistedCode)
                        if (remediation is RemediationAction.OpenMissingToolInstructions) {
                            put("remediationToolCode", remediation.toolCode)
                        }
                    }
                    put("checkedAtEpochMs", check.capability.checkedAtEpochMs)
                    check.capability.validUntilEpochMs?.let { put("validUntilEpochMs", it) }
                    put("mandatory", check.mandatory)
                },
            )
        }
    })
}

internal fun JSONObject.toAutomationPreflightReceipt(): AutomationPreflightReceipt? {
    val preflight = this
    val macIdentity = preflight.optString("macIdentity")
        .takeIf(::isOpaqueAutomationConnectionIdentity)
        ?: return null
    return AutomationPreflightReceipt(
        recipeRevision = preflight.optString("recipeRevision").ifBlank { return null },
        checkedAtMillis = preflight.optLong("checkedAtMillis", 0L),
        macIdentity = macIdentity,
        targetId = preflight.optString("targetId").ifBlank { "current" },
        requiredCapabilities = preflight.optJSONArray("requiredCapabilities")?.toSmartCapabilities().orEmpty(),
        checks = preflight.optJSONArray("checks")?.toPreflightChecks().orEmpty(),
        commandTools = preflight.optJSONArray("commandTools")?.toStringSet().orEmpty(),
        commandPaths = preflight.optJSONArray("commandPaths")?.toStringSet().orEmpty(),
        commandApps = preflight.optJSONArray("commandApps")?.toStringSet().orEmpty(),
        permissionSnapshot = preflight.optJSONArray("permissionSnapshot")?.toStringSet().orEmpty(),
        requiredPermissions = preflight.optJSONArray("requiredPermissions")?.toStringSet().orEmpty(),
        requiredCheckCodes = preflight.optJSONArray("requiredCheckCodes")?.toStringSet().orEmpty(),
        receiptId = preflight.optString("receiptId").ifBlank {
            io.codecks.domain.automation.automationReceiptId(
                "preflight",
                preflight.optString("recipeRevision"),
                preflight.optLong("checkedAtMillis", 0L),
            )
        },
    )
}

internal fun JSONObject.toAutomationLiveTestReceipt(): AutomationLiveTestReceipt? {
    return AutomationLiveTestReceipt(
        recipeRevision = optString("recipeRevision").ifBlank { return null },
        checkedAtMillis = optLong("checkedAtMillis", 0L),
        preflightCheckedAtMillis = optLong("preflightCheckedAtMillis", 0L),
        assertions = optJSONArray("assertions")?.toLiveTestAssertions().orEmpty(),
        cleanup = optJSONObject("cleanup")?.toAutomationLiveTestCleanup() ?: return null,
        macIdentity = optString("macIdentity"),
        normalizedPlanHash = optString("normalizedPlanHash"),
        preflightReceiptId = optString("preflightReceiptId"),
        timeoutPolicyCode = optString("timeoutPolicyCode"),
        terminalStatus = AutomationLiveTestTerminalStatus.fromPersistedCode(
            optString("terminalStatus"),
        ),
        recoveryRequired = optBoolean("recoveryRequired", false),
        completedAtMillis = optLong(
            "completedAtMillis",
            optLong("checkedAtMillis", 0L),
        ),
        receiptId = optString("receiptId"),
    ).redactedTerminal()
}

internal fun AutomationLiveTestReceipt.toJson(): JSONObject = JSONObject().apply {
    val terminal = redactedTerminal()
    put("receiptId", terminal.receiptId)
    put("recipeRevision", terminal.recipeRevision)
    put("checkedAtMillis", terminal.checkedAtMillis)
    put("completedAtMillis", terminal.completedAtMillis)
    put("preflightCheckedAtMillis", terminal.preflightCheckedAtMillis)
    put("macIdentity", terminal.macIdentity)
    put("normalizedPlanHash", terminal.normalizedPlanHash)
    put("preflightReceiptId", terminal.preflightReceiptId)
    put("timeoutPolicyCode", terminal.timeoutPolicyCode)
    put("terminalStatus", terminal.terminalStatus.persistedCode)
    put("recoveryRequired", terminal.recoveryRequired)
    put("assertions", JSONArray().apply {
        terminal.assertions.forEach { assertion ->
            put(
                JSONObject().apply {
                    put("assertionId", assertion.assertionId)
                    put("actionRevision", assertion.actionRevision)
                    put("ordinal", assertion.ordinal)
                    put("passed", assertion.passed)
                    put("outcomeCode", assertion.outcomeCode.persistedCode)
                    put("stepTerminalStatus", assertion.terminalStatus.persistedCode)
                },
            )
        }
    })
    put("cleanup", terminal.cleanup.toJson())
}

internal fun AutomationGateStamp.toJson(): JSONObject = JSONObject().apply {
    put("revisionId", revisionId)
    put("policyVersion", policyVersion)
    put("capabilityFingerprint", capabilityFingerprint)
    hostTrustVersion?.let { put("hostTrustVersion", it) }
    validationReceiptId?.let { put("validationReceiptId", it) }
    preflightReceiptId?.let { put("preflightReceiptId", it) }
    liveTestReceiptId?.let { put("liveTestReceiptId", it) }
}

internal fun JSONObject.toAutomationGateStamp(): AutomationGateStamp? =
    AutomationGateStamp(
        revisionId = optString("revisionId").ifBlank { return null },
        policyVersion = optInt("policyVersion", 0),
        capabilityFingerprint = optString("capabilityFingerprint"),
        hostTrustVersion = optString("hostTrustVersion").takeIf(String::isNotBlank),
        validationReceiptId = optString("validationReceiptId").takeIf(String::isNotBlank),
        preflightReceiptId = optString("preflightReceiptId").takeIf(String::isNotBlank),
        liveTestReceiptId = optString("liveTestReceiptId").takeIf(String::isNotBlank),
    )

internal fun AutomationLiveTestCleanup.toJson(): JSONObject = JSONObject().apply {
    put("passed", passed)
    put("outcomeCode", outcomeCode.persistedCode)
    put("cleanupId", cleanupId)
    put("actionRevision", actionRevision)
    put("undoGuarantee", undoGuarantee.persistedCode)
}

private fun JSONObject.toAutomationLiveTestCleanup(): AutomationLiveTestCleanup? {
    return AutomationLiveTestCleanup(
        command = "",
        passed = optBoolean("passed", false),
        message = AutomationLiveTestCleanupCode.fromPersistedCode(
            optString("outcomeCode"),
        ).persistedCode,
        outcomeCode = AutomationLiveTestCleanupCode.fromPersistedCode(
            optString("outcomeCode"),
        ),
        cleanupId = optString("cleanupId"),
        actionRevision = optString("actionRevision"),
        undoGuarantee = AutomationUndoGuarantee.fromPersistedCode(
            optString("undoGuarantee"),
        ),
    )
}

private fun JSONArray.toStringSet(): Set<String> = buildSet {
    repeat(length()) { index ->
        val value = optString(index).ifBlank { return@repeat }
        add(value)
    }
}

private fun JSONArray.toSmartCapabilities(): Set<SmartCapability> = buildSet {
    repeat(length()) { index ->
        val capability = runCatching {
            SmartCapability.valueOf(optString(index))
        }.getOrNull()
        capability?.let(::add)
    }
}

private fun JSONArray.toPreflightChecks(): List<AutomationPreflightCheck> = buildList {
    repeat(length()) { index ->
        val item = getJSONObject(index)
        val area = runCatching {
            AutomationPreflightArea.valueOf(item.optString("area"))
        }.getOrNull() ?: AutomationPreflightArea.Identity
        val capabilityCode = item.optString("capabilityCode")
        if (capabilityCode.isBlank()) {
            add(
                AutomationPreflightCheck(
                    area = area,
                    passed = item.optBoolean("passed", false),
                    message = item.optString("message").ifBlank { "check failed" },
                ),
            )
            return@repeat
        }
        val status = CapabilityStatus.fromPersistedCode(item.optString("capabilityStatus"))
        val issue = item.optString("issueCode")
            .takeIf(String::isNotBlank)
            ?.let(ConnectionIssueCode::fromPersistedCode)
            ?: ConnectionIssueCode.UNKNOWN.takeIf {
                status == CapabilityStatus.RETRYABLE || status == CapabilityStatus.BLOCKED
            }
        val remediation = item.toRemediationAction(issue)
        add(
            AutomationPreflightCheck(
                area = area,
                passed = status == CapabilityStatus.SATISFIED,
                message = item.optString("message").ifBlank { "check failed" },
                capability = CapabilityCheck(
                    capabilityCode = capabilityCode,
                    status = status,
                    issueCode = issue,
                    remediation = remediation,
                    checkedAtEpochMs = item.optLong("checkedAtEpochMs", 0L),
                    validUntilEpochMs = item.optLong("validUntilEpochMs")
                        .takeIf { item.has("validUntilEpochMs") },
                ),
                mandatory = item.optBoolean("mandatory", true),
            ),
        )
    }
}

private fun JSONObject.toRemediationAction(
    issue: ConnectionIssueCode?,
): RemediationAction? {
    val decoded = when (optString("remediationCode")) {
        "request_bluetooth_permission" -> RemediationAction.RequestBluetoothPermission
        "open_bluetooth_settings" -> RemediationAction.OpenBluetoothSettings
        "open_system_pairing" -> RemediationAction.OpenSystemPairing
        "retry_hid_registration" -> RemediationAction.RetryHidRegistration
        "retry_connection_now" -> RemediationAction.RetryConnectionNow
        "open_mac_wake_help" -> RemediationAction.OpenMacWakeHelp
        "reenter_ssh_credentials" -> RemediationAction.ReenterSshCredentials
        "review_changed_host_key" -> RemediationAction.ReviewChangedHostKey
        "open_missing_tool_instructions" -> RemediationAction.OpenMissingToolInstructions(
            optString("remediationToolCode").ifBlank { "unknown" },
        )
        "contact_support" -> RemediationAction.ContactSupport
        else -> null
    }
    return decoded
        ?: issue?.remediations?.firstOrNull()
        ?: RemediationAction.ContactSupport.takeIf {
            CapabilityStatus.fromPersistedCode(optString("capabilityStatus")) in
                setOf(CapabilityStatus.RETRYABLE, CapabilityStatus.BLOCKED)
        }
}

private fun JSONArray.toLiveTestAssertions(): List<AutomationLiveTestAssertion> = buildList {
    repeat(length()) { index ->
        val item = getJSONObject(index)
        add(
            AutomationLiveTestAssertion(
                stepId = item.optString("assertionId").ifBlank { "terminal" },
                stepTitle = if (item.optInt("ordinal", -1) >= 0) {
                    "Action ${item.optInt("ordinal") + 1}"
                } else {
                    "Live test"
                },
                passed = item.optBoolean("passed", false),
                message = AutomationLiveTestOutcomeCode.fromPersistedCode(
                    item.optString("outcomeCode"),
                ).persistedCode,
                assertionId = item.optString("assertionId"),
                actionRevision = item.optString("actionRevision"),
                outcomeCode = AutomationLiveTestOutcomeCode.fromPersistedCode(
                    item.optString("outcomeCode"),
                ),
                terminalStatus = AutomationStepTerminalStatus.fromPersistedCode(
                    item.optString("stepTerminalStatus"),
                ),
                ordinal = item.optInt("ordinal", -1),
            ),
        )
    }
}

internal fun JSONObject?.toCommandReview(): CommandReview {
    if (this == null) return CommandReview()
    return CommandReview(
        reviewedRevision = optString("reviewedRevision").takeIf(String::isNotBlank),
        checkedRevision = optString("checkedRevision").takeIf(String::isNotBlank),
    )
}

internal fun ExecutionAuthorization.toJson(): JSONObject = JSONObject().apply {
    put("dangerousRevisionConfirmed", dangerousRevisionConfirmed)
}

internal fun JSONObject?.toExecutionAuthorization(): ExecutionAuthorization {
    if (this == null) return ExecutionAuthorization()
    return ExecutionAuthorization(
        dangerousRevisionConfirmed = optString("dangerousRevisionConfirmed").takeIf(String::isNotBlank),
    )
}

internal fun JSONObject.optCommandOrigin(fallback: CommandOrigin): CommandOrigin =
    optString("commandOrigin").takeIf(String::isNotBlank)
        ?.let { runCatching { CommandOrigin.valueOf(it) }.getOrNull() }
        ?: fallback

internal fun TargetSelector.toJson(): JSONObject = JSONObject().apply {
    when (val selector = this@toJson) {
        TargetSelector.CurrentDevice -> put("type", "current")
        TargetSelector.AllCompatibleDevices -> put("type", "all")
        TargetSelector.AskAtRunTime -> put("type", "ask")
        is TargetSelector.SpecificDevice -> {
            put("type", "device")
            put("id", selector.deviceId.value)
        }
        is TargetSelector.DeviceGroup -> {
            put("type", "group")
            put("id", selector.groupId.value)
        }
    }

}

internal fun JSONObject?.toTargetSelector(): TargetSelector {
    if (this == null) return TargetSelector.CurrentDevice
    return when (optString("type")) {
        "all" -> TargetSelector.AllCompatibleDevices
        "ask" -> TargetSelector.AskAtRunTime
        "device" -> optString("id").takeIf(String::isNotBlank)
            ?.let { TargetSelector.SpecificDevice(DeviceId(it)) }
            ?: TargetSelector.CurrentDevice
        "group" -> optString("id").takeIf(String::isNotBlank)
            ?.let { TargetSelector.DeviceGroup(DeviceGroupId(it)) }
            ?: TargetSelector.CurrentDevice
        else -> TargetSelector.CurrentDevice
    }
}

internal fun AutomationTrigger.toJson(): JSONObject = JSONObject().apply {
    when (this@toJson) {
        AutomationTrigger.Manual -> put("type", "manual")
        is AutomationTrigger.AiSuggested -> {
            put("type", "ai")
            put("prompt", prompt)
        }
        is AutomationTrigger.TimeOfDay -> {
            put("type", "time")
            put("hour", hour)
            put("minute", minute)
            put("days", JSONArray().apply { days.forEach(::put) })
        }
        is AutomationTrigger.ActiveApp -> {
            put("type", "app")
            put("appName", appName)
        }
        is AutomationTrigger.ClipboardContains -> {
            put("type", "clipboard")
            put("text", text)
        }
        is AutomationTrigger.WifiSsid -> {
            put("type", "wifi")
            put("ssid", ssid)
        }
        AutomationTrigger.MacAwake -> put("type", "mac_awake")
        is AutomationTrigger.FileChanged -> {
            put("type", "file")
            put("path", path)
        }
        is AutomationTrigger.BatteryBelow -> {
            put("type", "battery")
            put("percent", percent)
        }
    }
}

internal fun JSONObject.trigger(): AutomationTrigger {
    val raw = opt("trigger")
    return when (raw) {
        is JSONObject -> raw.toTrigger()
        is String -> raw.toTrigger()
        else -> AutomationTrigger.Manual
    }
}

private fun JSONObject.toTrigger(): AutomationTrigger =
    when (optString("type")) {
        "ai" -> AutomationTrigger.AiSuggested(optString("prompt"))
        "time" -> AutomationTrigger.TimeOfDay(
            hour = optInt("hour", 9),
            minute = optInt("minute", 0),
            days = optJSONArray("days")?.let { days ->
                buildSet {
                    repeat(days.length()) { index -> add(days.optString(index)) }
                }
            }.orEmpty(),
        )
        "app" -> AutomationTrigger.ActiveApp(optString("appName").ifBlank { "App" })
        "clipboard" -> AutomationTrigger.ClipboardContains(optString("text"))
        "wifi" -> AutomationTrigger.WifiSsid(optString("ssid").ifBlank { "Wi-Fi" })
        "mac_awake" -> AutomationTrigger.MacAwake
        "file" -> AutomationTrigger.FileChanged(optString("path").ifBlank { "~" })
        "battery" -> AutomationTrigger.BatteryBelow(optInt("percent", 20).coerceIn(1, 100))
        else -> AutomationTrigger.Manual
    }

private fun String.toTrigger(): AutomationTrigger =
    if (startsWith("ai:")) AutomationTrigger.AiSuggested(removePrefix("ai:")) else AutomationTrigger.Manual
