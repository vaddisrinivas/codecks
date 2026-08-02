package io.codecks.domain.automation

import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class AutomationLiveTestTimeoutPolicy(
    val persistedCode: String,
    val totalTimeoutMillis: Long,
) {
    BOUNDED_V1("bounded_v1_90s", 90_000L),
}

data class AutomationActionProbeResult(
    val exitCode: Int?,
    val interrupted: Boolean = false,
)

fun interface AutomationLiveTestActionExecutor {
    suspend fun execute(action: NormalizedAutomationAction): AutomationActionProbeResult
}

fun interface AutomationLiveTestClock {
    fun nowMillis(): Long
}

fun interface AutomationLiveTestTimeoutRunner {
    suspend fun run(
        timeoutMillis: Long,
        block: suspend () -> AutomationLiveTestReceipt,
    ): AutomationLiveTestReceipt
}

private object CoroutineAutomationLiveTestTimeoutRunner : AutomationLiveTestTimeoutRunner {
    override suspend fun run(
        timeoutMillis: Long,
        block: suspend () -> AutomationLiveTestReceipt,
    ): AutomationLiveTestReceipt = try {
        withTimeout(timeoutMillis) { block() }
    } catch (_: TimeoutCancellationException) {
        throw AutomationLiveTestTimedOutException()
    }
}

class AutomationLiveTestTimedOutException : RuntimeException()

class AutomationLiveTestEngine(
    private val executor: AutomationLiveTestActionExecutor,
    private val clock: AutomationLiveTestClock = AutomationLiveTestClock(System::currentTimeMillis),
    private val timeoutRunner: AutomationLiveTestTimeoutRunner = CoroutineAutomationLiveTestTimeoutRunner,
) {
    suspend fun run(
        recipe: AutomationRecipe,
        preflight: AutomationPreflightReceipt,
        currentRevision: () -> String = recipe::revisionFingerprint,
        timeoutPolicy: AutomationLiveTestTimeoutPolicy = AutomationLiveTestTimeoutPolicy.BOUNDED_V1,
    ): AutomationLiveTestReceipt {
        val startedAt = clock.nowMillis()
        val revision = recipe.revisionFingerprint()
        val plan = AutomationExecutionPlanCompiler.compile(recipe).getOrElse {
            return terminalReceipt(
                revision,
                null,
                preflight,
                timeoutPolicy,
                startedAt,
                AutomationLiveTestTerminalStatus.PLAN_REJECTED,
            )
        }
        if (preflight.recipeRevision != revision) {
            return terminalReceipt(
                revision,
                plan,
                preflight,
                timeoutPolicy,
                startedAt,
                AutomationLiveTestTerminalStatus.PREFLIGHT_MISMATCH,
            )
        }
        if (currentRevision() != revision) {
            return terminalReceipt(
                revision,
                plan,
                preflight,
                timeoutPolicy,
                startedAt,
                AutomationLiveTestTerminalStatus.STALE_REVISION,
            )
        }
        return try {
            timeoutRunner.run(timeoutPolicy.totalTimeoutMillis) {
                executePlan(
                    plan,
                    revision,
                    preflight,
                    timeoutPolicy,
                    startedAt,
                    currentRevision,
                )
            }.redactedTerminal()
        } catch (_: AutomationLiveTestTimedOutException) {
            terminalReceipt(
                revision,
                plan,
                preflight,
                timeoutPolicy,
                startedAt,
                AutomationLiveTestTerminalStatus.TIMED_OUT,
            )
        } catch (_: CancellationException) {
            terminalReceipt(
                revision,
                plan,
                preflight,
                timeoutPolicy,
                startedAt,
                AutomationLiveTestTerminalStatus.CANCELLED,
            )
        } catch (_: InterruptedException) {
            terminalReceipt(
                revision,
                plan,
                preflight,
                timeoutPolicy,
                startedAt,
                AutomationLiveTestTerminalStatus.PROCESS_INTERRUPTED,
            )
        } catch (_: Exception) {
            terminalReceipt(
                revision,
                plan,
                preflight,
                timeoutPolicy,
                startedAt,
                AutomationLiveTestTerminalStatus.PROCESS_INTERRUPTED,
            )
        }
    }

    private suspend fun executePlan(
        plan: NormalizedAutomationPlan,
        revision: String,
        preflight: AutomationPreflightReceipt,
        policy: AutomationLiveTestTimeoutPolicy,
        startedAt: Long,
        currentRevision: () -> String,
    ): AutomationLiveTestReceipt {
        val assertions = mutableListOf<AutomationLiveTestAssertion>()
        for (action in plan.actions) {
            if (currentRevision() != revision) {
                return terminalReceipt(
                    revision,
                    plan,
                    preflight,
                    policy,
                    startedAt,
                    AutomationLiveTestTerminalStatus.STALE_REVISION,
                    assertions,
                )
            }
            val result = executor.execute(action)
            val outcome = when {
                result.interrupted -> AutomationLiveTestOutcomeCode.INTERRUPTED
                result.exitCode == action.assertion.expectedExitCode ->
                    AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO
                else -> AutomationLiveTestOutcomeCode.NON_ZERO_EXIT
            }
            assertions += action.toReceiptAssertion(outcome)
            if (outcome == AutomationLiveTestOutcomeCode.INTERRUPTED) {
                return terminalReceipt(
                    revision,
                    plan,
                    preflight,
                    policy,
                    startedAt,
                    AutomationLiveTestTerminalStatus.PROCESS_INTERRUPTED,
                    assertions,
                )
            }
            if (outcome != AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO) {
                return terminalReceipt(
                    revision,
                    plan,
                    preflight,
                    policy,
                    startedAt,
                    AutomationLiveTestTerminalStatus.ASSERTION_FAILED,
                    assertions,
                )
            }
        }
        if (currentRevision() != revision) {
            return terminalReceipt(
                revision,
                plan,
                preflight,
                policy,
                startedAt,
                AutomationLiveTestTerminalStatus.STALE_REVISION,
                assertions,
            )
        }
        return terminalReceipt(
            revision,
            plan,
            preflight,
            policy,
            startedAt,
            AutomationLiveTestTerminalStatus.PASSED,
            assertions,
        )
    }

    private suspend fun terminalReceipt(
        revision: String,
        plan: NormalizedAutomationPlan?,
        preflight: AutomationPreflightReceipt,
        policy: AutomationLiveTestTimeoutPolicy,
        startedAt: Long,
        requestedStatus: AutomationLiveTestTerminalStatus,
        completedAssertions: List<AutomationLiveTestAssertion> = emptyList(),
    ): AutomationLiveTestReceipt {
        val assertions = completedAssertions.toMutableList()
        plan?.actions
            ?.drop(assertions.size)
            ?.forEachIndexed { remainingIndex, action ->
                val stepStatus = when {
                    remainingIndex > 0 -> AutomationStepTerminalStatus.SKIPPED_DEPENDENCY
                    requestedStatus == AutomationLiveTestTerminalStatus.TIMED_OUT ->
                        AutomationStepTerminalStatus.TIMED_OUT
                    requestedStatus == AutomationLiveTestTerminalStatus.CANCELLED ->
                        AutomationStepTerminalStatus.CANCELLED
                    requestedStatus == AutomationLiveTestTerminalStatus.PROCESS_INTERRUPTED ->
                        AutomationStepTerminalStatus.INTERRUPTED
                    requestedStatus == AutomationLiveTestTerminalStatus.ASSERTION_FAILED ->
                        AutomationStepTerminalStatus.SKIPPED_DEPENDENCY
                    else -> AutomationStepTerminalStatus.NOT_STARTED
                }
                assertions += action.toUnexecutedReceiptAssertion(stepStatus)
            }
        val cleanup = runCleanup(plan?.cleanup, requestedStatus)
        val recoveryRequired = cleanup.outcomeCode in setOf(
            AutomationLiveTestCleanupCode.FAILED,
            AutomationLiveTestCleanupCode.INTERRUPTED,
        )
        val terminalStatus = if (recoveryRequired) {
            AutomationLiveTestTerminalStatus.RECOVERY_REQUIRED
        } else {
            requestedStatus
        }
        return AutomationLiveTestReceipt(
            recipeRevision = revision,
            checkedAtMillis = startedAt,
            preflightCheckedAtMillis = preflight.checkedAtMillis,
            assertions = assertions,
            cleanup = cleanup,
            normalizedPlanHash = plan?.planHash.orEmpty(),
            preflightReceiptId = preflight.receiptId,
            timeoutPolicyCode = policy.persistedCode,
            terminalStatus = terminalStatus,
            recoveryRequired = recoveryRequired,
            completedAtMillis = clock.nowMillis().coerceAtLeast(startedAt),
        ).redactedTerminal()
    }

    private suspend fun runCleanup(
        cleanup: NormalizedAutomationCleanup?,
        terminalStatus: AutomationLiveTestTerminalStatus,
    ): AutomationLiveTestCleanup {
        val trigger = terminalStatus.cleanupTrigger()
        if (cleanup == null || trigger == null || trigger !in cleanup.runAfter) {
            return AutomationLiveTestCleanup(
                command = "",
                passed = true,
                message = AutomationLiveTestCleanupCode.NOT_REQUIRED.persistedCode,
                outcomeCode = AutomationLiveTestCleanupCode.NOT_REQUIRED,
                undoGuarantee = cleanup?.undoGuarantee ?: AutomationUndoGuarantee.NONE,
            )
        }
        val result = runCatching {
            withContext(NonCancellable) { executor.execute(cleanup.action) }
        }
        val outcome = when {
            result.isFailure -> AutomationLiveTestCleanupCode.INTERRUPTED
            result.getOrNull()?.interrupted == true -> AutomationLiveTestCleanupCode.INTERRUPTED
            result.getOrNull()?.exitCode == cleanup.action.assertion.expectedExitCode ->
                AutomationLiveTestCleanupCode.SUCCEEDED
            else -> AutomationLiveTestCleanupCode.FAILED
        }
        return AutomationLiveTestCleanup(
            command = "",
            passed = outcome == AutomationLiveTestCleanupCode.SUCCEEDED,
            message = outcome.persistedCode,
            outcomeCode = outcome,
            cleanupId = cleanup.action.assertion.assertionId,
            actionRevision = cleanup.action.actionRevision,
            undoGuarantee = cleanup.undoGuarantee,
        )
    }
}

private fun NormalizedAutomationAction.toReceiptAssertion(
    outcome: AutomationLiveTestOutcomeCode,
): AutomationLiveTestAssertion =
    AutomationLiveTestAssertion(
        stepId = assertion.assertionId,
        stepTitle = "Action ${ordinal + 1}",
        passed = outcome == AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO,
        message = outcome.persistedCode,
        assertionId = assertion.assertionId,
        actionRevision = actionRevision,
        outcomeCode = outcome,
        terminalStatus = when (outcome) {
            AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO -> AutomationStepTerminalStatus.SUCCEEDED
            AutomationLiveTestOutcomeCode.NON_ZERO_EXIT -> AutomationStepTerminalStatus.FAILED
            AutomationLiveTestOutcomeCode.INTERRUPTED -> AutomationStepTerminalStatus.INTERRUPTED
            AutomationLiveTestOutcomeCode.NOT_RUN,
            AutomationLiveTestOutcomeCode.UNKNOWN,
            -> AutomationStepTerminalStatus.NOT_STARTED
        },
        ordinal = ordinal,
    )

private fun NormalizedAutomationAction.toUnexecutedReceiptAssertion(
    status: AutomationStepTerminalStatus,
): AutomationLiveTestAssertion =
    AutomationLiveTestAssertion(
        stepId = assertion.assertionId,
        stepTitle = "Action ${ordinal + 1}",
        passed = false,
        message = status.persistedCode,
        assertionId = assertion.assertionId,
        actionRevision = actionRevision,
        outcomeCode = AutomationLiveTestOutcomeCode.NOT_RUN,
        terminalStatus = status,
        ordinal = ordinal,
    )

private fun AutomationLiveTestTerminalStatus.cleanupTrigger(): AutomationCleanupTrigger? = when (this) {
    AutomationLiveTestTerminalStatus.PASSED -> AutomationCleanupTrigger.SUCCESS
    AutomationLiveTestTerminalStatus.ASSERTION_FAILED,
    AutomationLiveTestTerminalStatus.PROCESS_INTERRUPTED,
    AutomationLiveTestTerminalStatus.RECOVERY_REQUIRED,
    -> AutomationCleanupTrigger.FAILURE
    AutomationLiveTestTerminalStatus.TIMED_OUT -> AutomationCleanupTrigger.TIMEOUT
    AutomationLiveTestTerminalStatus.CANCELLED -> AutomationCleanupTrigger.CANCEL
    AutomationLiveTestTerminalStatus.STALE_REVISION,
    AutomationLiveTestTerminalStatus.PREFLIGHT_MISMATCH,
    AutomationLiveTestTerminalStatus.PLAN_REJECTED,
    AutomationLiveTestTerminalStatus.UNKNOWN,
    -> null
}

fun AutomationLiveTestReceipt.redactedTerminal(): AutomationLiveTestReceipt =
    copy(
        macIdentity = "",
        assertions = assertions.map { assertion ->
            assertion.copy(
                stepId = assertion.assertionId.ifBlank { "terminal" },
                stepTitle = if (assertion.ordinal >= 0) "Action ${assertion.ordinal + 1}" else "Live test",
                message = assertion.outcomeCode.persistedCode,
            )
        },
        cleanup = cleanup.copy(
            command = "",
            message = cleanup.outcomeCode.persistedCode,
        ),
    )
