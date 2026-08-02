package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.ShellTrustLevel
import io.codecks.core.actions.commandRevision
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.smart.SmartCapability
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationLiveTestEngineTest {
    private val privateHomePrefix = listOf("", "Users", "").joinToString("/")

    @Test
    fun passingReceiptBindsRevisionPlanPreflightPolicyAndAssertionsWithoutRawData() = runBlocking {
        val recipe = recipe()
        val preflight = preflight(recipe)
        val receipt = engine { AutomationActionProbeResult(exitCode = 0) }
            .run(recipe, preflight)
        val plan = AutomationExecutionPlanCompiler.compile(recipe).getOrThrow()

        assertEquals(AutomationLiveTestTerminalStatus.PASSED, receipt.terminalStatus)
        assertEquals(recipe.revisionFingerprint(), receipt.recipeRevision)
        assertEquals(plan.planHash, receipt.normalizedPlanHash)
        assertEquals(preflight.receiptId, receipt.preflightReceiptId)
        assertEquals(AutomationLiveTestTimeoutPolicy.BOUNDED_V1.persistedCode, receipt.timeoutPolicyCode)
        assertEquals(plan.assertions.map { it.assertionId }, receipt.assertions.map { it.assertionId })
        assertTrue(receipt.assertions.all { it.outcomeCode == AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO })
        assertEquals(preflight.macIdentity, receipt.macIdentity)
        assertTrue(receipt.cleanup.command.isEmpty())
        assertFalse(receipt.toString().contains(privateHomePrefix))
        assertFalse(receipt.toString().contains("raw stdout"))
    }

    @Test
    fun timeoutCancellationInterruptionAndAssertionFailureAreTerminalAndCannotPromote() = runBlocking {
        val validated = validatedRecipe()
        val preflight = preflight(validated)
        val preflightPassed = validated.withPreflightReceipt(preflight)

        val failed = engine { AutomationActionProbeResult(exitCode = 9) }
            .run(preflightPassed, preflight)
        val interrupted = engine { AutomationActionProbeResult(exitCode = null, interrupted = true) }
            .run(preflightPassed, preflight)
        val cancelled = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { AutomationActionProbeResult(0) },
            timeoutRunner = AutomationLiveTestTimeoutRunner { _, _ -> throw CancellationException() },
        ).run(preflightPassed, preflight)
        val timedOut = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { AutomationActionProbeResult(0) },
            timeoutRunner = AutomationLiveTestTimeoutRunner { _, _ ->
                throw AutomationLiveTestTimedOutException()
            },
        ).run(preflightPassed, preflight)

        assertBlocked(preflightPassed, failed, AutomationLiveTestTerminalStatus.ASSERTION_FAILED)
        assertBlocked(preflightPassed, interrupted, AutomationLiveTestTerminalStatus.PROCESS_INTERRUPTED)
        assertBlocked(preflightPassed, cancelled, AutomationLiveTestTerminalStatus.CANCELLED)
        assertBlocked(preflightPassed, timedOut, AutomationLiveTestTerminalStatus.TIMED_OUT)
    }

    @Test
    fun staleRevisionAndMismatchedPreflightCannotPromote() = runBlocking {
        val validated = validatedRecipe()
        val preflight = preflight(validated)
        val preflightPassed = validated.withPreflightReceipt(preflight)
        var revisionChecks = 0
        val stale = engine { AutomationActionProbeResult(0) }.run(
            recipe = preflightPassed,
            preflight = preflight,
            currentRevision = {
                revisionChecks += 1
                if (revisionChecks >= 3) "changed-revision" else preflightPassed.revisionFingerprint()
            },
        )
        val mismatched = engine { AutomationActionProbeResult(0) }.run(
            recipe = preflightPassed,
            preflight = preflight.copy(recipeRevision = "other"),
        )

        assertBlocked(preflightPassed, stale, AutomationLiveTestTerminalStatus.STALE_REVISION)
        assertBlocked(preflightPassed, mismatched, AutomationLiveTestTerminalStatus.PREFLIGHT_MISMATCH)
    }

    @Test
    fun forgedPlanHashOrReceiptIdCannotPromote() = runBlocking {
        val validated = validatedRecipe()
        val preflight = preflight(validated)
        val preflightPassed = validated.withPreflightReceipt(preflight)
        val valid = engine { AutomationActionProbeResult(0) }.run(preflightPassed, preflight)

        val forgedPlan = valid.copy(normalizedPlanHash = "forged")
        val forgedReceipt = valid.copy(receiptId = "forged")

        assertEquals(
            AutomationStage.PREFLIGHT_PASSED,
            preflightPassed.withLiveTestReceipt(forgedPlan).stage,
        )
        assertEquals(
            AutomationStage.PREFLIGHT_PASSED,
            preflightPassed.withLiveTestReceipt(forgedReceipt).stage,
        )
    }

    @Test
    fun repositoryGateStripsRawOutputAndRejectsMismatchedHostIdentity() = runBlocking {
        val validated = validatedRecipe()
        val preflight = preflight(validated)
        val preflightPassed = validated.withPreflightReceipt(preflight)
        val valid = engine { AutomationActionProbeResult(0) }.run(preflightPassed, preflight)
        val raw = valid.copy(
            macIdentity = "user@host.example",
            assertions = valid.assertions.map {
                it.copy(
                    stepTitle = listOf("", "Users", "example", "private.sh").joinToString("/"),
                    message = "raw stdout and stderr",
                )
            },
            cleanup = valid.cleanup.copy(
                command = "rm " + listOf("", "Users", "example", "private.tmp").joinToString("/"),
                message = "raw cleanup output",
            ),
        )

        val recorded = preflightPassed.withLiveTestReceipt(raw)
        val terminal = requireNotNull(recorded.lastLiveTest)

        assertEquals(AutomationStage.PREFLIGHT_PASSED, recorded.stage)
        assertTrue(terminal.macIdentity.isEmpty())
        assertTrue(terminal.cleanup.command.isEmpty())
        assertFalse(terminal.toString().contains("host.example"))
        assertFalse(terminal.toString().contains(privateHomePrefix))
        assertFalse(terminal.toString().contains("stdout"))
        assertFalse(terminal.toString().contains("stderr"))
    }

    private fun assertBlocked(
        recipe: AutomationRecipe,
        receipt: AutomationLiveTestReceipt,
        status: AutomationLiveTestTerminalStatus,
    ) {
        assertEquals(status, receipt.terminalStatus)
        val recorded = recipe.withLiveTestReceipt(receipt)
        assertEquals(AutomationStage.PREFLIGHT_PASSED, recorded.stage)
        assertNull(recorded.gateStamp?.liveTestReceiptId)
        assertFalse(recorded.enabled)
    }

    private fun engine(
        result: suspend (NormalizedAutomationAction) -> AutomationActionProbeResult,
    ): AutomationLiveTestEngine {
        val time = AtomicLong(1_000L)
        return AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor(result),
            clock = AutomationLiveTestClock { time.getAndIncrement() },
        )
    }

    private fun validatedRecipe(): AutomationRecipe {
        val draft = recipe().enforceRevisionGate(previous = null)
        return draft.withValidationResult(
            ActionResult(
                actionId = draft.id,
                title = draft.title,
                status = ActionResultStatus.Succeeded,
                message = "validated",
                logs = "validated",
                timestampMillis = 100L,
            ),
            draft.revisionFingerprint(),
        )
    }

    private fun recipe(): AutomationRecipe {
        val command = "open 'https://example.com'"
        val revision = commandRevision(
            command = command,
            targetSelector = TargetSelector.CurrentDevice,
            origin = CommandOrigin.AiGenerated,
            dangerous = false,
        )
        return AutomationRecipe(
            id = "live-test",
            title = "Live test",
            description = "",
            enabled = false,
            steps = listOf(
                ActionSpec.ShellCommand(
                    id = "open",
                    title = "Open",
                    command = command,
                    trustLevel = ShellTrustLevel.Generated,
                    commandOrigin = CommandOrigin.AiGenerated,
                    review = CommandReview(reviewedRevision = revision),
                ),
            ),
        )
    }

    private fun preflight(recipe: AutomationRecipe): AutomationPreflightReceipt =
        AutomationPreflightReceipt(
            recipeRevision = recipe.revisionFingerprint(),
            checkedAtMillis = 200L,
            macIdentity = "opaque-host-version",
            targetId = "current",
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            checks = listOf(
                automationConnectionPreflightCheck(null, checkedAtEpochMs = 200L),
            ),
            commandTools = setOf("open"),
            commandPaths = emptySet(),
            permissionSnapshot = emptySet(),
            requiredCheckCodes = setOf(AutomationCapabilityCodes.Connection),
        )
}
