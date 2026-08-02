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
import io.codecks.domain.automation.AutomationWorkerOutcome
import io.codecks.domain.automation.AutomationWorkerOutcomeCode
import io.codecks.domain.automation.AutomationWorkerRetryDisposition
import io.codecks.domain.automation.AutomationTriggerEngine
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
    fun stop()
}

@Singleton
class WorkManagerAutomationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AutomationScheduler {
    override fun start() {
        AutomationTriggerWorker.enqueue(context)
    }

    override fun stop() {
        AutomationTriggerWorker.cancel(context)
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
            val evaluation = deps.triggerEngine().evaluate(recipes)
            for (scheduled in evaluation.dueRecipes) {
                val claim = evaluation.claimsByRecipeId.getValue(scheduled.id)
                if (claim.recipeRevision != scheduled.revisionFingerprint()) {
                    deps.triggerEngine().complete(claim)
                    continue
                }
                val preparation = deps.executionCoordinator().prepareAutomatic(
                    recipeId = scheduled.id,
                    scheduledRevision = scheduled.revisionFingerprint(),
                    workerAttempt = runAttemptCount,
                )
                if (preparation is AutomationPreparation.Blocked) {
                    if (preparation.claimDisposition() == AutomationClaimDisposition.RELEASE_FOR_RETRY) {
                        deps.triggerEngine().release(claim)
                        return Result.retry()
                    }
                    deps.automationRepository().recordWorkerOutcome(
                        scheduled.id,
                        requireNotNull(preparation.outcome),
                    )
                    deps.triggerEngine().complete(claim)
                    continue
                }
                preparation as AutomationPreparation.Ready
                deps.automationRepository().recordWorkerOutcome(
                    scheduled.id,
                    preparation.outcome.copy(
                        code = AutomationWorkerOutcomeCode.EXECUTION_STARTED,
                        checkedAtMillis = System.currentTimeMillis(),
                    ),
                )
                val runResult = try {
                    deps.executionCoordinator().run(preparation.executable)
                } catch (_: CancellationException) {
                    withContext(NonCancellable) {
                        deps.automationRepository().recordWorkerOutcome(
                            scheduled.id,
                            AutomationWorkerOutcome(
                                code = AutomationWorkerOutcomeCode.EXECUTION_UNCERTAIN,
                                checkedAtMillis = System.currentTimeMillis(),
                                scheduledRevision = scheduled.revisionFingerprint(),
                                currentRevision = preparation.executable.recipe.revisionFingerprint(),
                                retryDisposition = AutomationWorkerRetryDisposition.NONE,
                                workerAttempt = runAttemptCount,
                            ),
                        )
                        deps.triggerEngine().complete(claim)
                    }
                    return Result.success()
                }
                deps.automationRepository().recordRun(
                    scheduled.id,
                    runResult,
                )
                deps.automationRepository().recordWorkerOutcome(
                    scheduled.id,
                    preparation.outcome.copy(
                        code = if (runResult.succeeded && runAttemptCount > 0) {
                            AutomationWorkerOutcomeCode.WORKER_RECREATED
                        } else {
                            io.codecks.domain.automation.automaticOutcomeCode(runResult.status)
                        },
                        checkedAtMillis = System.currentTimeMillis(),
                    ),
                )
                deps.triggerEngine().complete(claim)
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

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private val AUTOMATION_RUN_LOCK = Mutex()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationWorkerEntryPoint {
    fun automationRepository(): AutomationRepository
    fun executionCoordinator(): AutomationExecutionCoordinator
    fun triggerEngine(): AutomationTriggerEngine
}
