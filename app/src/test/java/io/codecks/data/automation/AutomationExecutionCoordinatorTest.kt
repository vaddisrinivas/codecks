package io.codecks.data.automation

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.ConnectionTarget
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationWorkerOutcome
import io.codecks.domain.automation.AutomationWorkerOutcomeCode
import io.codecks.domain.device.DeviceGroup
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.TargetDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationExecutionCoordinatorTest {
    @Test
    fun onlyUnclassifiedBlockedPreparationRetainsTriggerClaimForRetry() {
        assertEquals(
            AutomationClaimDisposition.RELEASE_FOR_RETRY,
            AutomationPreparation.Blocked("temporary").claimDisposition(),
        )
        assertEquals(
            AutomationClaimDisposition.COMPLETE,
            AutomationPreparation.Blocked(
                "terminal",
                AutomationWorkerOutcome(
                    code = AutomationWorkerOutcomeCode.REQUIREMENTS_CHANGED,
                    checkedAtMillis = 1,
                    scheduledRevision = "one",
                    currentRevision = "one",
                ),
            ).claimDisposition(),
        )
    }

    @Test
    fun configuredIdentityWinsOverUnrelatedCurrentDevice() = runTest {
        val config = ConnectionConfig("a.local", 22, "user", hasKey = true, hostKey = "key-a")
        val connection = CoordinatorConnectionRepository(config)
        val coordinator = AutomationExecutionCoordinator(
            CoordinatorAutomationRepository(),
            connection,
            CoordinatorActionRunner,
            CoordinatorDeviceRepository(DeviceId("mac-b")),
        )

        assertEquals(DeviceId("mac-a"), coordinator.currentTargetId(config))
    }
}

private class CoordinatorConnectionRepository(config: ConnectionConfig) : ConnectionRepository {
    override val config = MutableStateFlow(config)
    override suspend fun savedTargets(): List<ConnectionTarget> = listOf(
        ConnectionTarget("mac-a", "a.local", 22, "user", true, "key-a"),
        ConnectionTarget("mac-b", "b.local", 22, "user", true, "key-b"),
    )
    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey() = Result.success("key")
    override suspend fun publicKey() = "key"
    override suspend fun trustHostKey() = Result.success("trusted")
    override suspend fun confirmPendingHostKey() = Result.success("confirmed")
    override suspend fun rotateKey() = Result.success("rotated")
    override suspend fun resetTrust() = Result.success("reset")
    override suspend fun installKey(password: String) = Result.success("installed")
    override suspend fun test(password: String?) = Result.success("ready")
    override suspend fun runAction(actionId: String, dangerous: Boolean) = Result.success("ok")
    override suspend fun runCommand(command: String) = Result.success("ok")
    override suspend fun runCommandWithInput(command: String, stdin: String) = Result.success("ok")
    override suspend fun validateCommandSyntax(command: String) = Result.success("ok")
    override suspend fun runCommandSecret(command: String) = Result.success("ok")
    override suspend fun selectTarget(targetId: String) = Result.success("ok")
    override suspend fun removeTarget(targetId: String) = Result.success("ok")
}

private class CoordinatorDeviceRepository(private val current: DeviceId) : DeviceRepository {
    override suspend fun devices(): List<TargetDevice> = emptyList()
    override suspend fun groups(): List<DeviceGroup> = emptyList()
    override suspend fun currentDeviceId(): DeviceId = current
}

private class CoordinatorAutomationRepository : AutomationRepository {
    override val recipes: Flow<List<AutomationRecipe>> = flowOf(emptyList())
    override suspend fun save(recipe: AutomationRecipe) = Unit
    override suspend fun delete(recipeId: String) = Unit
    override suspend fun duplicate(recipeId: String) = Unit
    override suspend fun recordRun(recipeId: String, result: ActionResult) = Unit
    override suspend fun recordLiveTest(recipeId: String, receipt: AutomationLiveTestReceipt) = false
    override suspend fun exportRecipes() = Result.success("")
    override suspend fun validateRecipes(payload: String) = Result.success(Unit)
    override suspend fun importRecipes(payload: String) = Result.success(Unit)
    override suspend fun resetDefaults() = Unit
}

private object CoordinatorActionRunner : ActionRunner {
    override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult =
        ActionResult(spec.id, spec.title, ActionResultStatus.Succeeded, "ok")
}
