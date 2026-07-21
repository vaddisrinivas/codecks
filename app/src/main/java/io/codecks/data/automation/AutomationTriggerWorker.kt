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
import io.codecks.domain.automation.AutomationTriggerEngine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

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
            val connectionReady = deps.connectionRepository().config.first().isReady
            if (!connectionReady) return Result.success()

            val recipes = deps.automationRepository().recipes.first()
            val due = deps.triggerEngine().evaluate(recipes).dueRecipes
            due.forEach { recipe ->
                deps.automationRepository().recordRun(
                    recipe.id,
                    runRecipe(recipe, deps.actionRunner()),
                )
            }
            Result.success()
        } finally {
            AUTOMATION_RUN_LOCK.unlock()
        }
    }

    private suspend fun runRecipe(recipe: AutomationRecipe, actionRunner: ActionRunner): ActionResult {
        if (recipe.safety.requiresConfirmation || recipe.steps.any { it.dangerous }) {
            return ActionResult(
                actionId = recipe.id,
                title = recipe.title,
                status = ActionResultStatus.RequiresConfirmation,
                message = "Trigger matched, but ${recipe.title} needs manual confirmation",
            )
        }
        if (recipe.steps.isEmpty()) {
            return ActionResult(
                actionId = recipe.id,
                title = recipe.title,
                status = ActionResultStatus.Failed,
                message = "Recipe has no actions",
            )
        }
        var last = ActionResult(
            actionId = recipe.id,
            title = recipe.title,
            status = ActionResultStatus.Succeeded,
            message = "Automation completed",
        )
        recipe.steps.forEach { step ->
            val result = actionRunner.run(step, allowDangerous = false)
            last = result
            if (!result.succeeded) {
                return result.copy(actionId = recipe.id, title = recipe.title)
            }
        }
        return last.copy(actionId = recipe.id, title = recipe.title)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "deckbridge_automation_triggers"

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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationWorkerEntryPoint {
    fun automationRepository(): AutomationRepository
    fun connectionRepository(): ConnectionRepository
    fun actionRunner(): ActionRunner
    fun triggerEngine(): AutomationTriggerEngine
}
