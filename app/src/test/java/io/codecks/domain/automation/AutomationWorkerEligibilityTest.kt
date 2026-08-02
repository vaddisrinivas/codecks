package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.CommandOrigin
import io.codecks.domain.smart.SmartCapability
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationWorkerEligibilityTest {
    @Test
    fun exactReloadedSnapshotIsEligibleAndRetryAttemptIsTypedRecreation() = runBlocking {
        val recipe = enabledRecipe()

        val eligible = evaluateAutomationWorkerEligibility(
            recipe = recipe,
            scheduledRevision = recipe.revisionFingerprint(),
            currentMacIdentity = IDENTITY,
            availableTools = setOf("osascript"),
            currentPermissions = setOf("permission.accessibility"),
            nowMillis = 1_003L,
        )
        val recreated = evaluateAutomationWorkerEligibility(
            recipe = recipe,
            scheduledRevision = recipe.revisionFingerprint(),
            currentMacIdentity = IDENTITY,
            availableTools = setOf("osascript"),
            currentPermissions = setOf("permission.accessibility"),
            nowMillis = 1_003L,
            workerAttempt = 2,
        )

        assertEquals(AutomationWorkerOutcomeCode.ELIGIBLE, eligible.code)
        assertEquals(AutomationWorkerOutcomeCode.WORKER_RECREATED, recreated.code)
        assertEquals(2, recreated.workerAttempt)
    }

    @Test
    fun staleScheduledRevisionPersistsNeedsReviewAndClearsExecutionGate() = runBlocking {
        val recipe = enabledRecipe()
        val stale = evaluateAutomationWorkerEligibility(
            recipe = recipe,
            scheduledRevision = "stale",
            currentMacIdentity = IDENTITY,
            availableTools = emptySet(),
            currentPermissions = emptySet(),
            nowMillis = 1_003L,
        )

        val persisted = recipe.withWorkerOutcome(stale)

        assertEquals(AutomationWorkerOutcomeCode.STALE_REVISION, stale.code)
        assertTrue(stale.requiresNeedsReview)
        assertEquals(AutomationStage.NEEDS_REVIEW, persisted.stage)
        assertFalse(persisted.enabled)
        assertNull(persisted.gateStamp?.liveTestReceiptId)
        assertEquals(stale, persisted.lastWorkerOutcome)
    }

    @Test
    fun trustPermissionToolAndRecoveryChangesAreDistinctFailClosedCodes() = runBlocking {
        val recipe = enabledRecipe()
        val trust = evaluateAutomationWorkerEligibility(
            recipe,
            recipe.revisionFingerprint(),
            "changed-host",
            setOf("osascript"),
            setOf("permission.accessibility"),
            1_003L,
        )
        val permissions = evaluateAutomationWorkerEligibility(
            recipe,
            recipe.revisionFingerprint(),
            IDENTITY,
            setOf("osascript"),
            emptySet(),
            1_003L,
        )
        val tools = evaluateAutomationWorkerEligibility(
            recipe,
            recipe.revisionFingerprint(),
            IDENTITY,
            emptySet(),
            setOf("permission.accessibility"),
            1_003L,
        )
        val recovery = evaluateAutomationWorkerEligibility(
            recipe.copy(recoveryRequired = true),
            recipe.revisionFingerprint(),
            IDENTITY,
            setOf("osascript"),
            setOf("permission.accessibility"),
            1_003L,
        )

        assertEquals(AutomationWorkerOutcomeCode.TRUST_CHANGED, trust.code)
        assertEquals(AutomationWorkerOutcomeCode.PERMISSIONS_CHANGED, permissions.code)
        assertEquals(AutomationWorkerOutcomeCode.TOOLS_CHANGED, tools.code)
        assertEquals(AutomationWorkerOutcomeCode.RECOVERY_REQUIRED, recovery.code)
    }

    @Test
    fun interruptionAndRetryAreTypedWithoutPretendingExecutionSucceeded() {
        val outcome = AutomationWorkerOutcome(
            code = AutomationWorkerOutcomeCode.INTERRUPTED,
            checkedAtMillis = 2_000L,
            scheduledRevision = "scheduled",
            currentRevision = "current",
            retryDisposition = AutomationWorkerRetryDisposition.RETRY,
            workerAttempt = 1,
        )

        assertEquals(AutomationWorkerRetryDisposition.RETRY, outcome.retryDisposition)
        assertFalse(outcome.code == AutomationWorkerOutcomeCode.EXECUTED)
    }

    private suspend fun enabledRecipe(): AutomationRecipe {
        val base = AutomationRecipe(
            id = "worker",
            title = "Worker",
            description = "",
            enabled = false,
            steps = listOf(
                ActionSpec.ShellCommand(
                    id = "open",
                    title = "Open",
                    command = "osascript -e 'tell application \"System Events\" to get name of first process'",
                    commandOrigin = CommandOrigin.Bundled,
                ),
            ),
        )
        val draft = base.enforceRevisionGate(previous = null)
        val validated = draft.withValidationResult(
            ActionResult(
                actionId = draft.id,
                title = draft.title,
                status = ActionResultStatus.Succeeded,
                message = "validated",
                logs = "validated",
                timestampMillis = 999L,
            ),
            draft.revisionFingerprint(),
        )
        val preflight = AutomationPreflightReceipt(
            recipeRevision = validated.revisionFingerprint(),
            checkedAtMillis = 1_000L,
            macIdentity = IDENTITY,
            targetId = "current",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            checks = listOf(automationConnectionPreflightCheck(null, 1_000L)),
            commandTools = setOf("osascript"),
            commandPaths = emptySet(),
            permissionSnapshot = setOf("permission.accessibility"),
        )
        val ready = validated.withPreflightReceipt(preflight)
        val receipt = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { AutomationActionProbeResult(0) },
            clock = AutomationLiveTestClock { 1_001L },
        ).run(ready, preflight)
        val live = ready.withLiveTestReceipt(receipt)
        return live.copy(enabled = true).enforceRevisionGate(previous = live)
    }

    private companion object {
        const val IDENTITY = "mac|user|22|host-key"
    }
}
