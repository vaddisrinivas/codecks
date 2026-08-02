package io.codecks.data.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Binds
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.Module
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.data.ConnectionRepository
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationExecutionEngine
import io.codecks.domain.automation.AutomationWorkerOutcome
import io.codecks.domain.automation.AutomationWorkerOutcomeCode
import io.codecks.domain.automation.AutomationWorkerRetryDisposition
import io.codecks.domain.automation.hasCurrentValidLiveTest
import io.codecks.domain.automation.AutomationTriggerEngine
import io.codecks.domain.automation.automationConnectionIdentity
import io.codecks.domain.automation.automationPermissionProbeCommand
import io.codecks.domain.automation.evaluateAutomationWorkerEligibility
import io.codecks.domain.automation.requiredCommandTools
import io.codecks.domain.automation.requiredPermissions
import io.codecks.domain.automation.revisionFingerprint
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

interface AutomationScheduler {
    fun start()
}

@Singleton
class WorkManagerAutomationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AutomationScheduler {
    override fun start() {
        AutomationTriggerWorker.enqueue(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AutomationSchedulerModule {
    @Binds
    abstract fun bindAutomationScheduler(impl: WorkManagerAutomationScheduler): AutomationScheduler
}

class AutomationTriggerWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!AUTOMATION_RUN_LOCK.tryLock()) return Result.retry()
        return try {
            val deps = EntryPointAccessors.fromApplication(applicationContext, AutomationWorkerEntryPoint::class.java)
            val recipes = deps.automationRepository().recipes.first()
            val due = deps.triggerEngine().evaluate(recipes).dueRecipes
            for (scheduled in due) {
                val current = deps.automationRepository().recipes.first()
                    .firstOrNull { it.id == scheduled.id }
                    ?: continue
                val macConfig = deps.connectionRepository().config.first()
                val macIdentity = automationConnectionIdentity(
                    host = macConfig.host,
                    port = macConfig.port,
                    user = macConfig.user,
                    hostKey = macConfig.hostKey,
                )
                val requiredTools = current.requiredCommandTools()
                val preliminary = evaluateAutomationWorkerEligibility(
                    recipe = current,
                    scheduledRevision = scheduled.revisionFingerprint(),
                    currentMacIdentity = macIdentity,
                    availableTools = requiredTools,
                    currentPermissions = current.requiredPermissions(),
                    nowMillis = System.currentTimeMillis(),
                    workerAttempt = runAttemptCount,
                )
                if (preliminary.code !in setOf(
                        AutomationWorkerOutcomeCode.ELIGIBLE,
                        AutomationWorkerOutcomeCode.WORKER_RECREATED,
                    )
                ) {
                    deps.automationRepository().recordWorkerOutcome(current.id, preliminary)
                    continue
                }
                val availableTools = requiredTools.filterTo(mutableSetOf()) { tool ->
                    deps.connectionRepository().runCommand("command -v $tool").isSuccess
                }
                val executionSnapshot = deps.automationRepository().recipes.first()
                    .firstOrNull { it.id == scheduled.id }
                    ?: continue
                val currentPermissions = probeCurrentPermissions(
                    executionSnapshot,
                    deps.connectionRepository(),
                )
                // Permission/tool probes can outlive a connection-profile change. Identity is the
                // final read before eligibility and execution.
                val executionConfig = deps.connectionRepository().config.first()
                val executionIdentity = automationConnectionIdentity(
                    host = executionConfig.host,
                    port = executionConfig.port,
                    user = executionConfig.user,
                    hostKey = executionConfig.hostKey,
                )
                val eligibility = evaluateAutomationWorkerEligibility(
                    recipe = executionSnapshot,
                    scheduledRevision = scheduled.revisionFingerprint(),
                    currentMacIdentity = executionIdentity,
                    availableTools = availableTools,
                    currentPermissions = currentPermissions,
                    nowMillis = System.currentTimeMillis(),
                    workerAttempt = runAttemptCount,
                )
                deps.automationRepository().recordWorkerOutcome(executionSnapshot.id, eligibility)
                if (eligibility.code !in setOf(
                        AutomationWorkerOutcomeCode.ELIGIBLE,
                        AutomationWorkerOutcomeCode.WORKER_RECREATED,
                    )
                ) {
                    continue
                }
                val runResult = try {
                    automaticRunBlocker(
                        executionSnapshot,
                        executionIdentity,
                        System.currentTimeMillis(),
                    ) ?: AutomationExecutionEngine(deps.actionRunner()).run(
                        executionSnapshot,
                        allowDangerous = false,
                    )
                } catch (_: CancellationException) {
                    withContext(NonCancellable) {
                        deps.automationRepository().recordWorkerOutcome(
                            executionSnapshot.id,
                            AutomationWorkerOutcome(
                                code = AutomationWorkerOutcomeCode.INTERRUPTED,
                                checkedAtMillis = System.currentTimeMillis(),
                                scheduledRevision = scheduled.revisionFingerprint(),
                                currentRevision = executionSnapshot.revisionFingerprint(),
                                retryDisposition = AutomationWorkerRetryDisposition.RETRY,
                                workerAttempt = runAttemptCount,
                            ),
                        )
                    }
                    return Result.retry()
                }
                deps.automationRepository().recordRun(
                    executionSnapshot.id,
                    runResult,
                )
                deps.automationRepository().recordWorkerOutcome(
                    executionSnapshot.id,
                    eligibility.copy(
                        code = if (!runResult.succeeded) {
                            AutomationWorkerOutcomeCode.EXECUTION_FAILED
                        } else if (runAttemptCount > 0) {
                            AutomationWorkerOutcomeCode.WORKER_RECREATED
                        } else {
                            AutomationWorkerOutcomeCode.EXECUTED
                        },
                        checkedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
            Result.success()
        } finally {
            AUTOMATION_RUN_LOCK.unlock()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "codecks_automation_triggers"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutomationTriggerWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private val AUTOMATION_RUN_LOCK = Mutex()
    }
}

private suspend fun probeCurrentPermissions(
    recipe: AutomationRecipe,
    connectionRepository: ConnectionRepository,
): Set<String> = recipe.requiredPermissions().filterTo(mutableSetOf()) { permission ->
    val command = automationPermissionProbeCommand(permission) ?: return@filterTo false
    connectionRepository.runCommand(command).isSuccess
}

/**
 * Background-triggered rules may only run after their exact current revision has passed the
 * explicit test flow. This also keeps rules persisted by older app versions fail-closed.
 */
internal fun automaticRunBlocker(recipe: AutomationRecipe, macIdentity: String, nowMillis: Long): ActionResult? =
    if (recipe.hasCurrentValidLiveTest(
        nowMillis = nowMillis,
        requiredMacIdentity = macIdentity,
        requiredPermissions = recipe.requiredPermissions(),
    )) {
        null
    } else {
        ActionResult(
            actionId = recipe.id,
            title = recipe.title,
            status = ActionResultStatus.RequiresReview,
            message = "Trigger matched, but ${recipe.title} needs a recent approved live test for this revision",
        )
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationWorkerEntryPoint {
    fun automationRepository(): AutomationRepository
    fun connectionRepository(): ConnectionRepository
    fun actionRunner(): ActionRunner
    fun triggerEngine(): AutomationTriggerEngine
}
