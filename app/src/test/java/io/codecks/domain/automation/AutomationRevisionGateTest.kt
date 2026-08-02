package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.CommandOrigin
import io.codecks.domain.smart.SmartCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRevisionGateTest {
    @Test
    fun onlyValidForwardStageOrderProducesExecutableGate() {
        val draft = recipe().enforceRevisionGate(previous = null)
        assertEquals(AutomationStage.DRAFT, draft.stage)
        assertFalse(draft.enabled)

        val validated = draft.withValidationResult(successResult(), draft.revisionFingerprint())
        assertEquals(AutomationStage.VALIDATED, validated.stage)

        val preflight = validated.withPreflightReceipt(passingPreflight(validated))
        assertEquals(AutomationStage.PREFLIGHT_PASSED, preflight.stage)

        val liveTest = preflight.withLiveTestReceipt(passingLiveTest(preflight))
        assertEquals(AutomationStage.LIVE_TEST_PASSED, liveTest.stage)
        assertFalse(liveTest.hasCurrentRevisionGateForExecution())

        val enabled = liveTest.copy(enabled = true).enforceRevisionGate(previous = liveTest)
        assertEquals(AutomationStage.ENABLED, enabled.stage)
        assertTrue(enabled.enabled)
        assertTrue(enabled.hasCurrentRevisionGateForExecution())
    }

    @Test
    fun stageCannotSkipValidationOrPreflight() {
        val draft = recipe().enforceRevisionGate(previous = null)

        val skippedToPreflight = draft.withPreflightReceipt(passingPreflight(draft))
        val skippedToLive = draft.withLiveTestReceipt(passingLiveTest(draft))

        assertEquals(AutomationStage.DRAFT, skippedToPreflight.stage)
        assertEquals(AutomationStage.DRAFT, skippedToLive.stage)
        assertNull(skippedToPreflight.gateStamp?.preflightReceiptId)
        assertNull(skippedToLive.gateStamp?.liveTestReceiptId)
    }

    @Test
    fun failedReceiptsCannotAdvanceStage() {
        val draft = recipe().enforceRevisionGate(previous = null)
        val validated = draft.withValidationResult(successResult(), draft.revisionFingerprint())
        val failedPreflight = passingPreflight(validated).copy(
            checks = listOf(AutomationPreflightCheck(AutomationPreflightArea.Tool, false, "missing")),
        )
        val afterPreflight = validated.withPreflightReceipt(failedPreflight)
        assertEquals(AutomationStage.VALIDATED, afterPreflight.stage)

        val preflightPassed = validated.withPreflightReceipt(passingPreflight(validated))
        val failedLive = passingLiveTest(preflightPassed).copy(
            assertions = listOf(AutomationLiveTestAssertion("step", "Step", false, "failed")),
        )
        val afterLive = preflightPassed.withLiveTestReceipt(failedLive)
        assertEquals(AutomationStage.PREFLIGHT_PASSED, afterLive.stage)
        assertNull(afterLive.gateStamp?.liveTestReceiptId)
    }

    @Test
    fun executableEditCreatesNewDraftAndClearsAllReceipts() {
        val enabled = fullyEnabledRecipe()
        val edited = enabled.copy(
            steps = listOf(ActionSpec.ShellCommand("step", "Step", "open -a Safari")),
            enabled = true,
        ).enforceRevisionGate(previous = enabled)

        assertEquals(AutomationStage.DRAFT, edited.stage)
        assertFalse(edited.enabled)
        assertNull(edited.lastTest)
        assertNull(edited.lastPreflight)
        assertNull(edited.lastLiveTest)
        assertEquals(edited.revisionFingerprint(), edited.gateStamp?.revisionId)
    }

    private fun fullyEnabledRecipe(): AutomationRecipe {
        val draft = recipe().enforceRevisionGate(previous = null)
        val validated = draft.withValidationResult(successResult(), draft.revisionFingerprint())
        val preflight = validated.withPreflightReceipt(passingPreflight(validated))
        val liveTest = preflight.withLiveTestReceipt(passingLiveTest(preflight))
        return liveTest.copy(enabled = true).enforceRevisionGate(previous = liveTest)
    }

    private fun recipe(): AutomationRecipe =
        AutomationRecipe(
            id = "focus",
            title = "Focus",
            description = "Focus",
            enabled = true,
            trigger = AutomationTrigger.TimeOfDay(9, 0),
            steps = listOf(
                ActionSpec.ShellCommand(
                    "step",
                    "Step",
                    "open -a Notes",
                    commandOrigin = CommandOrigin.Bundled,
                ),
            ),
        )

    private fun successResult(): ActionResult =
        ActionResult(
            actionId = "focus",
            title = "Focus",
            status = ActionResultStatus.Succeeded,
            message = "validated",
            timestampMillis = 100L,
        )

    private fun passingPreflight(recipe: AutomationRecipe): AutomationPreflightReceipt =
        AutomationPreflightReceipt(
            recipeRevision = recipe.revisionFingerprint(),
            checkedAtMillis = 200L,
            macIdentity = "opaque-test-host",
            targetId = "current",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            checks = listOf(AutomationPreflightCheck(AutomationPreflightArea.Connection, true, "ready")),
            commandTools = setOf("open"),
            commandPaths = emptySet(),
            permissionSnapshot = emptySet(),
            requiredCheckCodes = setOf(AutomationCapabilityCodes.Connection),
        )

    private fun passingLiveTest(recipe: AutomationRecipe): AutomationLiveTestReceipt =
        AutomationExecutionPlanCompiler.compile(recipe).getOrThrow().let { plan ->
            val preflight = recipe.lastPreflight ?: passingPreflight(recipe)
            AutomationLiveTestReceipt(
            recipeRevision = recipe.revisionFingerprint(),
            checkedAtMillis = 300L,
            preflightCheckedAtMillis = preflight.checkedAtMillis,
            assertions = plan.assertions.mapIndexed { index, assertion ->
                AutomationLiveTestAssertion(
                    stepId = assertion.assertionId,
                    stepTitle = "Action ${index + 1}",
                    passed = true,
                    message = AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO.persistedCode,
                    assertionId = assertion.assertionId,
                    actionRevision = assertion.actionRevision,
                    outcomeCode = AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO,
                    terminalStatus = AutomationStepTerminalStatus.SUCCEEDED,
                    ordinal = index,
                )
            },
            cleanup = AutomationLiveTestCleanup(
                command = "",
                passed = true,
                message = AutomationLiveTestCleanupCode.NOT_REQUIRED.persistedCode,
                outcomeCode = AutomationLiveTestCleanupCode.NOT_REQUIRED,
            ),
            macIdentity = preflight.macIdentity,
            normalizedPlanHash = plan.planHash,
            preflightReceiptId = preflight.receiptId,
            timeoutPolicyCode = AutomationLiveTestTimeoutPolicy.BOUNDED_V1.persistedCode,
            terminalStatus = AutomationLiveTestTerminalStatus.PASSED,
            completedAtMillis = 301L,
        )
    }
}
