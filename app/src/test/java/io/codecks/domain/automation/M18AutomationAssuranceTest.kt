package io.codecks.domain.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.commandRevision
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.assurance.ActionAssuranceAdapter
import io.codecks.domain.assurance.ActionAssuranceEngine
import io.codecks.domain.assurance.AssuranceComponentExecution
import io.codecks.domain.assurance.AssuranceComponentStatus
import io.codecks.domain.assurance.AssuranceReceiptStatus
import io.codecks.domain.assurance.AssuranceSource
import io.codecks.domain.assurance.AssuranceUndoResult
import io.codecks.domain.assurance.toAssuranceRequest
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.smart.SmartCapability
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class M18AutomationAssuranceTest {
    @Test
    fun importedAutomationLosesEveryExecutionProofAndCannotDispatch() = runTest {
        val enabled = enabledRecipe()
        val imported = enabled.withoutImportedExecutionProof()
        val adapter = RecordingAssuranceAdapter(listOf(success("ssh")))
        val receipt = ActionAssuranceEngine(adapter).execute(
            imported.toAssuranceRequest(imported = true, operationId = "import-op"),
            imported.revisionFingerprint(),
        )

        assertFalse(imported.enabled)
        assertEquals(AutomationStage.DRAFT, imported.stage)
        assertNull(imported.lastPreflight)
        assertNull(imported.lastLiveTest)
        assertEquals(AssuranceReceiptStatus.Denied, receipt.status)
        assertEquals(0, adapter.executeCalls)
    }

    @Test
    fun revisionPermissionAndHostChangesHaveDistinctFailClosedOutcomes() = runTest {
        val recipe = enabledRecipe()
        val revision = evaluateAutomationWorkerEligibility(
            recipe, "stale", IDENTITY, setOf("open"), emptySet(), 1_003L,
        )
        val permissionRecipe = enabledRecipe(command = ACCESSIBILITY_COMMAND)
        val permission = evaluateAutomationWorkerEligibility(
            permissionRecipe,
            permissionRecipe.revisionFingerprint(),
            IDENTITY,
            setOf("osascript"),
            emptySet(),
            1_003L,
        )
        val host = evaluateAutomationWorkerEligibility(
            recipe, recipe.revisionFingerprint(), "changed-host", setOf("open"), emptySet(), 1_003L,
        )

        assertEquals(AutomationWorkerOutcomeCode.STALE_REVISION, revision.code)
        assertEquals(AutomationWorkerOutcomeCode.PERMISSIONS_CHANGED, permission.code)
        assertEquals(AutomationWorkerOutcomeCode.TRUST_CHANGED, host.code)
        assertTrue(listOf(revision, permission, host).all(AutomationWorkerOutcome::requiresNeedsReview))
    }

    @Test
    fun generatedQuotingAndShellMetacharacterCorpusFailsClosed() {
        val commands = listOf(
            "printf %s \"\$(whoami)\" | pbcopy",
            "printf %s '\${HOME}' | pbcopy",
            "open -a Notes '`whoami`'",
            "open -a Notes; rm -rf /",
            "python3 -c 'print(1)'",
            "printf hi > ~/.ssh/authorized_keys",
            "open -a Notes\u0000touch /tmp/bypass",
        )

        commands.forEachIndexed { index, command ->
            val result = AutomationExecutionPlanCompiler.compile(
                recipe(listOf(reviewedGeneratedStep("bad-$index", command))),
            )
            assertTrue(command, result.isFailure)
        }
    }

    @Test
    fun actionCountAndUtf8CommandSizeAreBoundedBeforeExecution() {
        val exactCount = recipe(
            (0 until MAX_AUTOMATION_ACTIONS).map { index -> bundledStep("step-$index", "open -a Notes") },
        )
        val countPlusCleanup = recipe(
            steps = (0 until MAX_AUTOMATION_ACTIONS).map { index ->
                bundledStep("step-$index", "open -a Notes")
            },
            cleanup = true,
        )
        val exactUtf8Bytes = recipe(listOf(bundledStep("exact-bytes", "é".repeat(MAX_AUTOMATION_COMMAND_BYTES / 2))))
        val oversizedUtf8Bytes = recipe(
            listOf(bundledStep("large-bytes", "é".repeat(MAX_AUTOMATION_COMMAND_BYTES / 2) + "x")),
        )

        assertTrue(AutomationExecutionPlanCompiler.compile(exactCount).isSuccess)
        assertTrue(AutomationExecutionPlanCompiler.compile(countPlusCleanup).isFailure)
        assertTrue(AutomationExecutionPlanCompiler.compile(exactUtf8Bytes).isSuccess)
        assertTrue(AutomationExecutionPlanCompiler.compile(oversizedUtf8Bytes).isFailure)
    }

    @Test
    fun exactIdempotencyReplayReturnsOriginalReceiptButSourceChangeConflicts() = runTest {
        val adapter = RecordingAssuranceAdapter(listOf(success("ssh")))
        val engine = ActionAssuranceEngine(adapter, nowMillis = { 10L })
        val ruleRequest = enabledRecipe().toAssuranceRequest(operationId = "same")

        val first = engine.execute(ruleRequest, ruleRequest.revision)
        val replay = engine.execute(ruleRequest, ruleRequest.revision)
        val sourceConflict = engine.execute(
            ruleRequest.copy(source = AssuranceSource.Deck),
            ruleRequest.revision,
        )

        assertSame(first, replay)
        assertEquals(AssuranceReceiptStatus.Succeeded, first.status)
        assertEquals(AssuranceSource.Rule, first.source)
        assertEquals(AssuranceReceiptStatus.Denied, sourceConflict.status)
        assertEquals(1, adapter.executeCalls)
    }

    @Test
    fun partialFailureRetryTargetsOnlyRetryableComponentAndIsSingleUse() = runTest {
        val adapter = RecordingAssuranceAdapter(
            first = listOf(success("local"), failure("ssh", retryable = true), failure("helper", retryable = false)),
            retry = listOf(success("ssh")),
        )
        val engine = ActionAssuranceEngine(adapter)
        val request = enabledRecipe().toAssuranceRequest(operationId = "partial")
        val first = engine.execute(request, request.revision)
        val token = requireNotNull(first.retryToken)
        val retryRequest = request.copy(operationId = "retry", idempotencyKey = "retry")

        val retried = engine.retry(first.receiptId, token, retryRequest, request.revision)
        val repeated = engine.retry(
            first.receiptId,
            token,
            retryRequest.copy(operationId = "retry-2", idempotencyKey = "retry-2"),
            request.revision,
        )

        assertEquals(AssuranceReceiptStatus.PartialFailure, first.status)
        assertEquals(AssuranceReceiptStatus.Succeeded, retried.status)
        assertEquals(setOf("ssh"), adapter.requestedComponents.single())
        assertEquals(AssuranceReceiptStatus.Denied, repeated.status)
    }

    @Test
    fun cancellationPropagatesWithoutSuccessOrReplayReceipt() = runTest {
        var executions = 0
        val adapter = object : ActionAssuranceAdapter {
            override suspend fun execute(
                request: io.codecks.domain.assurance.AssuranceRequest,
                componentIds: Set<String>?,
            ): List<AssuranceComponentExecution> {
                executions += 1
                throw CancellationException("cancel")
            }
        }
        val engine = ActionAssuranceEngine(adapter)
        val request = enabledRecipe().toAssuranceRequest(operationId = "cancel")

        assertTrue(runCatching { engine.execute(request, request.revision) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { engine.execute(request, request.revision) }.exceptionOrNull() is CancellationException)
        assertEquals(2, executions)
    }

    @Test
    fun undoIsReceiptBoundAndSingleUse() = runTest {
        val adapter = RecordingAssuranceAdapter(
            first = listOf(success("ssh", undoToken = "component-undo")),
            undo = listOf(success("ssh")),
        )
        val engine = ActionAssuranceEngine(adapter)
        val request = enabledRecipe(cleanup = true).toAssuranceRequest(operationId = "undo")
        val receipt = engine.execute(request, request.revision)
        val token = requireNotNull(receipt.undoToken)

        assertEquals(AssuranceUndoResult.Denied("undo_token_invalid"), engine.undo(receipt.receiptId, "wrong"))
        assertTrue(engine.undo(receipt.receiptId, token) is AssuranceUndoResult.Succeeded)
        assertEquals(AssuranceUndoResult.Denied("undo_token_consumed"), engine.undo(receipt.receiptId, token))
    }

    @Test
    fun partialLiveTestAndFailedCleanupCannotProduceEnablementProof() = runTest {
        val validated = validatedRecipe(cleanup = true)
        val preflight = preflight(validated)
        val ready = validated.withPreflightReceipt(preflight)
        val receipt = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { action ->
                AutomationActionProbeResult(if (action.actionId == "cleanup") 2 else 0)
            },
            clock = AutomationLiveTestClock { 1_001L },
        ).run(ready, preflight)
        val recorded = ready.withLiveTestReceipt(receipt)

        assertEquals(AutomationLiveTestTerminalStatus.RECOVERY_REQUIRED, receipt.terminalStatus)
        assertTrue(recorded.recoveryRequired)
        assertFalse(recorded.enabled)
        assertNull(recorded.gateStamp?.liveTestReceiptId)
    }

    @Test
    fun receiptAndPlanHashesChangeWithTheirBoundSources() = runTest {
        val first = validatedRecipe(command = "open -a Notes")
        val second = validatedRecipe(command = "open -a Calendar")
        val firstPreflight = preflight(first)
        val secondPreflight = preflight(second)
        val firstPlan = AutomationExecutionPlanCompiler.compile(first).getOrThrow()
        val secondPlan = AutomationExecutionPlanCompiler.compile(second).getOrThrow()

        assertNotEquals(first.revisionFingerprint(), second.revisionFingerprint())
        assertNotEquals(firstPlan.planHash, secondPlan.planHash)
        assertNotEquals(firstPreflight.receiptId, secondPreflight.receiptId)
        assertFalse(firstPreflight.copy(commandTools = setOf("other")).mandatoryChecksSatisfied())
    }

    private suspend fun enabledRecipe(
        command: String = "open -a Notes",
        cleanup: Boolean = false,
    ): AutomationRecipe {
        val validated = validatedRecipe(command, cleanup)
        val preflight = preflight(validated)
        val ready = validated.withPreflightReceipt(preflight)
        val liveReceipt = AutomationLiveTestEngine(
            executor = AutomationLiveTestActionExecutor { AutomationActionProbeResult(0) },
            clock = AutomationLiveTestClock { 1_001L },
        ).run(ready, preflight)
        val live = ready.withLiveTestReceipt(liveReceipt)
        return live.copy(enabled = true).enforceRevisionGate(previous = live)
    }

    private fun validatedRecipe(
        command: String = "open -a Notes",
        cleanup: Boolean = false,
    ): AutomationRecipe {
        val draft = recipe(
            steps = listOf(bundledStep("step", command)),
            cleanup = cleanup,
        ).enforceRevisionGate(previous = null)
        return draft.withValidationResult(
            ActionResult(
                actionId = draft.id,
                title = draft.title,
                status = ActionResultStatus.Succeeded,
                message = "validated",
                timestampMillis = 999L,
            ),
            draft.revisionFingerprint(),
        )
    }

    private fun recipe(
        steps: List<ActionSpec>,
        cleanup: Boolean = false,
    ) = AutomationRecipe(
        id = "m18-recipe",
        title = "M18 recipe",
        description = "",
        enabled = false,
        steps = steps,
        cleanupDefinition = if (cleanup) {
            AutomationCleanupDefinition(
                action = bundledStep("cleanup", "open -a Finder"),
                runAfter = AutomationCleanupTrigger.entries.toSet(),
                undoGuarantee = AutomationUndoGuarantee.GUARANTEED,
            )
        } else {
            AutomationCleanupDefinition()
        },
    )

    private fun preflight(recipe: AutomationRecipe): AutomationPreflightReceipt {
        val tools = recipe.requiredCommandTools()
        val permissions = recipe.requiredPermissions()
        val checks = buildList {
            add(typedCheck(AutomationPreflightArea.Identity, AutomationCapabilityCodes.Identity))
            add(typedCheck(AutomationPreflightArea.Connection, AutomationCapabilityCodes.Connection))
            add(typedCheck(AutomationPreflightArea.Provider, AutomationCapabilityCodes.Provider))
            add(typedCheck(AutomationPreflightArea.Target, AutomationCapabilityCodes.Target))
            tools.forEach { add(typedCheck(AutomationPreflightArea.Tool, automationRequirementCode(AutomationCapabilityCodes.Tool, it))) }
            permissions.forEach { add(typedCheck(AutomationPreflightArea.Permission, automationRequirementCode(AutomationCapabilityCodes.Permission, it))) }
        }
        return AutomationPreflightReceipt(
            recipeRevision = recipe.revisionFingerprint(),
            checkedAtMillis = 1_000L,
            macIdentity = IDENTITY,
            targetId = IDENTITY,
            requiredCapabilities = setOf(SmartCapability.MacCommand),
            checks = checks,
            commandTools = tools,
            commandPaths = emptySet(),
            permissionSnapshot = permissions,
            requiredPermissions = permissions,
            requiredCheckCodes = automationRequiredPreflightCheckCodes(tools, emptySet(), emptySet(), permissions),
        )
    }

    private fun typedCheck(area: AutomationPreflightArea, code: String) =
        AutomationPreflightCheck.typed(
            area = area,
            capabilityCode = code,
            status = io.codecks.domain.connection.CapabilityStatus.SATISFIED,
            issueCode = null,
            remediation = null,
            checkedAtEpochMs = 1_000L,
            message = "satisfied",
        )

    private fun bundledStep(id: String, command: String) = ActionSpec.ShellCommand(
        id = id,
        title = id,
        command = command,
        commandOrigin = CommandOrigin.Bundled,
    )

    private fun reviewedGeneratedStep(id: String, command: String): ActionSpec.ShellCommand {
        val revision = commandRevision(
            command = command,
            targetSelector = TargetSelector.CurrentDevice,
            origin = CommandOrigin.AiGenerated,
            dangerous = false,
        )
        return ActionSpec.ShellCommand(
            id = id,
            title = id,
            command = command,
            commandOrigin = CommandOrigin.AiGenerated,
            review = CommandReview(reviewedRevision = revision),
        )
    }

    private fun success(id: String, undoToken: String? = null) = AssuranceComponentExecution(
        componentId = id,
        status = AssuranceComponentStatus.Succeeded,
        code = "completed",
        undoToken = undoToken,
    )

    private fun failure(id: String, retryable: Boolean) = AssuranceComponentExecution(
        componentId = id,
        status = AssuranceComponentStatus.Failed,
        code = "failed",
        retryable = retryable,
    )

    private companion object {
        const val IDENTITY = "connection_identity_v1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val ACCESSIBILITY_COMMAND =
            "osascript -e 'tell application \"System Events\" to get name of first process'"
    }
}

private class RecordingAssuranceAdapter(
    private val first: List<AssuranceComponentExecution>,
    private val retry: List<AssuranceComponentExecution> = first,
    private val undo: List<AssuranceComponentExecution> = emptyList(),
) : ActionAssuranceAdapter {
    var executeCalls: Int = 0
    val requestedComponents = mutableListOf<Set<String>>()

    override suspend fun execute(
        request: io.codecks.domain.assurance.AssuranceRequest,
        componentIds: Set<String>?,
    ): List<AssuranceComponentExecution> {
        executeCalls += 1
        if (componentIds != null) requestedComponents += componentIds
        return if (componentIds == null) first else retry
    }

    override suspend fun undo(
        request: io.codecks.domain.assurance.AssuranceRequest,
        componentTokens: Map<String, String>,
    ): List<AssuranceComponentExecution> = undo
}
