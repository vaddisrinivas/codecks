package io.codecks.domain.assurance

import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.DeckAction
import io.codecks.domain.ai.ActionDefinition
import io.codecks.domain.ai.ActionDraft
import io.codecks.domain.ai.ActionStep
import io.codecks.domain.ai.ActionStepTypes
import io.codecks.domain.ai.SafetyMetadata
import io.codecks.domain.ai.TargetSelector
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationStage
import io.codecks.domain.reactive.ActionRevision
import io.codecks.domain.reactive.CodecksCapability
import io.codecks.domain.reactive.ControlId
import io.codecks.domain.reactive.ReactiveAction
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveControlPolicy
import io.codecks.domain.reactive.ReactiveControlSource
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.sshpack.SshActionPack
import io.codecks.domain.sshpack.SshActionPackValidator
import io.codecks.domain.sshpack.SshCatalogActionContract
import io.codecks.domain.sshpack.SshPreflightRequirement
import io.codecks.domain.sshpack.TypedSshAction
import io.codecks.domain.catalog.CatalogId
import io.codecks.domain.catalog.CatalogVersion
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionAssuranceEngineTest {
    @Test
    fun partialFailureRetriesOnlySafeRetryableComponentAndIdempotencyReplaysReceipt() = runTest {
        val adapter = RecordingAdapter(
            first = listOf(
                success("hid"),
                failure("ssh", retryable = true),
                failure("helper", retryable = false),
            ),
            retry = listOf(success("ssh")),
        )
        val engine = ActionAssuranceEngine(adapter, nowMillis = { 10L })
        val request = request(idempotencyKey = "same")

        val first = engine.execute(request, currentRevision = "r1")
        val replay = engine.execute(request, currentRevision = "r1")
        assertSame(first, replay)
        assertEquals(AssuranceReceiptStatus.PartialFailure, first.status)
        assertNotNull(first.retryToken)

        val retryRequest = request(operationId = "op-retry", idempotencyKey = "retry")
        val retry = engine.retry(first.receiptId, first.retryToken!!, retryRequest, currentRevision = "r1")
        assertEquals(AssuranceReceiptStatus.Succeeded, retry.status)
        assertEquals(setOf("ssh"), adapter.requestedComponents.single())
        assertFalse(adapter.requestedComponents.single().contains("helper"))
        val repeated = engine.retry(
            first.receiptId,
            first.retryToken!!,
            request(operationId = "op-retry-2", idempotencyKey = "retry-2"),
            currentRevision = "r1",
        )
        assertEquals(AssuranceReceiptStatus.Denied, repeated.status)
        assertEquals(1, adapter.requestedComponents.size)
    }

    @Test
    fun retryTokenIsConsumedBeforeThrowingAdapterPreflight() = runTest {
        var preflightCalls = 0
        var executeCalls = 0
        val adapter = object : ActionAssuranceAdapter {
            override suspend fun preflight(request: AssuranceRequest): List<AssurancePreflightCheck> {
                preflightCalls += 1
                if (preflightCalls > 1) error("preflight crashed")
                return emptyList()
            }

            override suspend fun execute(
                request: AssuranceRequest,
                componentIds: Set<String>?,
            ): List<AssuranceComponentExecution> {
                executeCalls += 1
                return listOf(failure("ssh", retryable = true))
            }
        }
        val engine = ActionAssuranceEngine(adapter)
        val first = engine.execute(request(), "r1")
        val token = requireNotNull(first.retryToken)
        val retryRequest = request(operationId = "retry-op", idempotencyKey = "retry-key")

        val preflightFailure = engine.retry(first.receiptId, token, retryRequest, "r1")
        val repeated = engine.retry(first.receiptId, token, retryRequest.copy(idempotencyKey = "retry-key-2"), "r1")

        assertEquals(AssuranceReceiptStatus.Denied, preflightFailure.status)
        assertTrue(preflightFailure.preflight.any { it.code == AssurancePreflightCode.Transport && !it.passed })
        assertEquals(AssuranceReceiptStatus.Denied, repeated.status)
        assertEquals(1, executeCalls)
        assertEquals(2, preflightCalls)
    }

    @Test
    fun staleRevisionReviewPermissionPolicyAndImportedRuleFailClosed() = runTest {
        val adapter = RecordingAdapter(listOf(success("ssh")))
        val engine = ActionAssuranceEngine(adapter)
        val stale = engine.execute(request(), currentRevision = "r2")
        assertEquals(AssuranceReceiptStatus.Denied, stale.status)

        val unreviewed = engine.execute(
            request(operationId = "unreviewed", idempotencyKey = "unreviewed", review = null),
            currentRevision = "r1",
        )
        assertEquals(AssuranceReceiptStatus.Denied, unreviewed.status)

        val denied = engine.execute(
            request(
                operationId = "denied",
                idempotencyKey = "denied",
                permission = false,
                policy = false,
                imported = true,
            ),
            currentRevision = "r1",
        )
        assertEquals(AssuranceReceiptStatus.Denied, denied.status)
        assertTrue(denied.preflight.any { it.code == AssurancePreflightCode.ImportDisabled && !it.passed })
        assertEquals(0, adapter.executeCalls)
        assertEquals(0, adapter.preflightCalls)
    }

    @Test
    fun cancellationPropagatesWithoutSyntheticReceipt() = runTest {
        val adapter = object : ActionAssuranceAdapter {
            override suspend fun execute(
                request: AssuranceRequest,
                componentIds: Set<String>?,
            ): List<AssuranceComponentExecution> = throw CancellationException("cancel")
        }
        val engine = ActionAssuranceEngine(adapter)
        val error = runCatching { engine.execute(request(), "r1") }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    @Test
    fun undoIsBoundToReceiptSingleUseAndProducesTypedReceipt() = runTest {
        val adapter = RecordingAdapter(
            first = listOf(success("local", undo = "opaque-undo")),
            undo = listOf(success("local")),
        )
        val engine = ActionAssuranceEngine(adapter)
        val request = request(reversible = true)
        val receipt = engine.execute(request, "r1")
        val token = requireNotNull(receipt.undoToken)

        val undone = engine.undo(receipt.receiptId, token)
        assertTrue(undone is AssuranceUndoResult.Succeeded)
        assertEquals(AssuranceUndoResult.Denied("undo_token_consumed"), engine.undo(receipt.receiptId, token))
    }

    @Test
    fun failedOrCancelledUndoDoesNotConsumeTokenBeforeConfirmedSuccess() = runTest {
        var undoCalls = 0
        val adapter = object : ActionAssuranceAdapter {
            override suspend fun execute(
                request: AssuranceRequest,
                componentIds: Set<String>?,
            ) = listOf(success("local", undo = "opaque"))

            override suspend fun undo(
                request: AssuranceRequest,
                componentTokens: Map<String, String>,
            ): List<AssuranceComponentExecution> {
                undoCalls += 1
                if (undoCalls == 1) throw CancellationException("cancel undo")
                if (undoCalls == 2) return listOf(failure("local", retryable = false))
                return listOf(success("local"))
            }
        }
        val engine = ActionAssuranceEngine(adapter)
        val receipt = engine.execute(request(reversible = true), "r1")
        val token = requireNotNull(receipt.undoToken)

        assertTrue(runCatching { engine.undo(receipt.receiptId, token) }.exceptionOrNull() is CancellationException)
        assertEquals(AssuranceUndoResult.Denied("undo_failed"), engine.undo(receipt.receiptId, token))
        assertTrue(engine.undo(receipt.receiptId, token) is AssuranceUndoResult.Succeeded)
        assertEquals(AssuranceUndoResult.Denied("undo_token_consumed"), engine.undo(receipt.receiptId, token))
    }

    @Test
    fun receiptsNeverPersistTransportSecretsOrFreeText() = runTest {
        val secret = "user@example.com password=abc host=10.0.0.7 clipboard=private"
        val engine = ActionAssuranceEngine(
            RecordingAdapter(listOf(failure(secret, retryable = true))),
        )
        val secretRequest = AssuranceRequest(
            operationId = secret,
            idempotencyKey = secret,
            subjectId = secret,
            revision = secret,
            source = AssuranceSource.Deck,
            transport = AssuranceTransport.Ssh,
            reviewRequired = true,
            review = AssuranceReview(secret, true),
        )
        val receipt = engine.execute(secretRequest, secret)
        val serialized = receipt.toString()
        assertFalse(serialized.contains("example.com"))
        assertFalse(serialized.contains("password"))
        assertFalse(serialized.contains("10.0.0.7"))
        assertFalse(serialized.contains("clipboard"))
        assertTrue(receipt.components.single().code.length <= 96)
    }

    @Test
    fun sourceAdaptersBindRulesAiReactiveAndTypedSshToSameContract() {
        val rule = AutomationRecipe("rule", "Rule", "", enabled = false, steps = emptyList(), stage = AutomationStage.DRAFT)
        assertEquals(AssuranceSource.Rule, rule.toAssuranceRequest(imported = true).source)
        assertTrue(rule.toAssuranceRequest(imported = true).imported)

        val ai = ActionDraft(
            prompt = "private prompt",
            definition = ActionDefinition(
                id = "ai",
                title = "AI",
                target = TargetSelector.ActiveDevice,
                safety = SafetyMetadata(),
                steps = listOf(ActionStep("s", ActionStepTypes.Shell, value = "echo unsafe")),
            ),
        ).toAssuranceRequest()
        assertEquals(AssuranceSource.AiDraft, ai.source)
        assertTrue(ai.reviewRequired)
        assertFalse(ai.policyPassed)
        assertFalse(ai.revision.contains("private"))

        val reactive = control().toAssuranceRequest()
        assertEquals(AssuranceSource.Reactive, reactive.source)
        assertEquals(AssuranceTransport.Hid, reactive.transport)

        val revision = "a".repeat(64)
        val pack = SshActionPack(
            CatalogId("pack"),
            CatalogVersion(1, 0, 0),
            listOf(TypedSshAction(CatalogId("typed"), CatalogId("catalog"), "Typed")),
        )
        val valid = SshActionPackValidator.validate(pack) {
            SshCatalogActionContract(
                it,
                revision,
                setOf(
                    SshPreflightRequirement.SSH_CONNECTION,
                    SshPreflightRequirement.PINNED_HOST_IDENTITY,
                    SshPreflightRequirement.COMMAND_SAFETY,
                ),
                requiresConfirmation = false,
            )
        }
        val request = (valid as io.codecks.domain.sshpack.SshPackValidationResult.Valid)
            .toAssuranceRequests().single()
        assertEquals(AssuranceSource.TypedSshCatalog, request.source)
        assertEquals(revision, request.revision)
    }

    private fun request(
        operationId: String = "op",
        idempotencyKey: String = "key",
        review: AssuranceReview? = AssuranceReview("r1", true),
        permission: Boolean = true,
        policy: Boolean = true,
        imported: Boolean = false,
        reversible: Boolean = false,
    ) = AssuranceRequest(
        operationId,
        idempotencyKey,
        "subject",
        "r1",
        AssuranceSource.Deck,
        AssuranceTransport.Ssh,
        reviewRequired = true,
        review = review,
        permissionGranted = permission,
        policyPassed = policy,
        imported = imported,
        reversible = reversible,
    )

    private fun success(id: String, undo: String? = null) = AssuranceComponentExecution(
        id,
        AssuranceComponentStatus.Succeeded,
        "completed",
        undoToken = undo,
    )

    private fun failure(id: String, retryable: Boolean) = AssuranceComponentExecution(
        id,
        AssuranceComponentStatus.Failed,
        id,
        retryable = retryable,
    )

    private fun control() = ReactiveControl(
        id = ControlId("control"),
        title = "Control",
        subtitle = null,
        icon = ReactiveIcon.Generic,
        action = ReactiveAction.Hid(io.codecks.domain.reactive.SharedHidCommand.Enter),
        source = ReactiveControlSource.ShortcutCatalog,
        basePriority = 1,
        reason = "test",
        requiredCapabilities = emptySet<CodecksCapability>(),
        risk = ReactiveRisk.Safe,
        reversible = false,
        stateRevision = 1,
        actionRevision = ActionRevision("r1"),
        expiresAtMillis = 100,
        policy = ReactiveControlPolicy.Allow,
    )
}

private class RecordingAdapter(
    private val first: List<AssuranceComponentExecution>,
    private val retry: List<AssuranceComponentExecution> = first,
    private val undo: List<AssuranceComponentExecution> = emptyList(),
) : ActionAssuranceAdapter {
    var executeCalls = 0
    var preflightCalls = 0
    val requestedComponents = mutableListOf<Set<String>>()

    override suspend fun preflight(request: AssuranceRequest): List<AssurancePreflightCheck> {
        preflightCalls += 1
        return emptyList()
    }

    override suspend fun execute(
        request: AssuranceRequest,
        componentIds: Set<String>?,
    ): List<AssuranceComponentExecution> {
        executeCalls += 1
        if (componentIds != null) requestedComponents += componentIds
        return if (componentIds == null) first else retry
    }

    override suspend fun undo(
        request: AssuranceRequest,
        componentTokens: Map<String, String>,
    ): List<AssuranceComponentExecution> = undo
}
