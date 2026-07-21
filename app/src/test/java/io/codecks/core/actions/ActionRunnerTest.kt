package io.codecks.core.actions

import io.codecks.data.ActionRepository
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.device.Capability
import io.codecks.domain.device.DeviceGroup
import io.codecks.domain.device.DeviceId
import io.codecks.domain.device.DeviceRepository
import io.codecks.domain.device.TargetDevice
import io.codecks.domain.device.TargetSelector
import io.codecks.domain.device.TransportId
import io.codecks.domain.device.TransportRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionRunnerTest {
    @Test
    fun dangerousAction_requiresConfirmationByDefault() = runTest {
        val runner = testRunner()

        val result = runner.run(ActionSpec.CatalogAction("lock", "Lock", dangerous = true))

        assertEquals(ActionResultStatus.RequiresConfirmation, result.status)
    }

    @Test
    fun shellCommand_usesConnectionRepository() = runTest {
        val connection = FakeConnectionRepository()
        val runner = testRunner(connection = connection)

        val result = runner.run(ActionSpec.ShellCommand("hello", "Hello", "printf hi"))

        assertEquals(ActionResultStatus.Succeeded, result.status)
        assertEquals("printf hi", connection.lastCommand)
    }

    @Test
    fun shellCommand_allCompatibleTargets_runsOnEveryReadyDevice() = runTest {
        val connection = FakeConnectionRepository()
        val devices = FakeDeviceRepository(
            listOf(
                target("mac_a"),
                target("mac_b"),
            ),
        )
        val runner = testRunner(connection = connection, devices = devices)

        val result = runner.run(
            ActionSpec.ShellCommand(
                id = "hello",
                title = "Hello",
                command = "printf hi",
                targetSelector = TargetSelector.AllCompatibleDevices,
            ),
        )

        assertEquals(ActionResultStatus.Succeeded, result.status)
        assertEquals(listOf("mac_a", "mac_b"), connection.targetCommands.map { it.first }.sorted())
        assertEquals("2 targets", result.target)
    }

    @Test
    fun generatedShellCommand_requiresReviewBeforeRunning() = runTest {
        val connection = FakeConnectionRepository()
        val runner = testRunner(connection = connection)

        val result = runner.run(
            ActionSpec.ShellCommand(
                id = "generated",
                title = "Generated",
                command = "open https://example.com",
                trustLevel = ShellTrustLevel.Generated,
            ),
        )

        assertEquals(ActionResultStatus.RequiresConfirmation, result.status)
        assertEquals(null, connection.lastCommand)
    }

    @Test
    fun generatedShellCommand_blocksUnsupportedTemplates() = runTest {
        val runner = testRunner()

        val result = runner.run(
            ActionSpec.ShellCommand(
                id = "generated",
                title = "Generated",
                command = "echo hello",
                trustLevel = ShellTrustLevel.Generated,
            ),
            allowDangerous = true,
        )

        assertEquals(ActionResultStatus.Failed, result.status)
    }

    private fun testRunner(
        connection: FakeConnectionRepository = FakeConnectionRepository(),
        devices: DeviceRepository = FakeDeviceRepository(),
    ) = DefaultActionRunner(
        FakeActionRepository(),
        connection,
        devices,
        FakeTransportRegistry(),
    )
}

private class FakeActionRepository : ActionRepository {
    private val action = DeckAction("finder", "Finder", ActionKind.Ssh, ActionIcon.Finder)
    override fun favorites(): List<DeckAction> = listOf(action)
    override fun observeFavorites(): Flow<List<DeckAction>> = MutableStateFlow(listOf(action))
    override fun allActions(): List<DeckAction> = listOf(action)
    override suspend fun saveFavorites(actions: List<DeckAction>) = Unit
    override suspend fun run(action: DeckAction): Result<String> = Result.success("${action.label} ok")
    override suspend fun test(action: DeckAction): Result<String> = Result.success("${action.label} verified")
}

private class FakeConnectionRepository : ConnectionRepository {
    var lastCommand: String? = null
    val targetCommands = mutableListOf<Pair<String, String>>()
    override val config = MutableStateFlow(ConnectionConfig("mac.local", 22, "user", hasKey = true, hostKey = "key"))
    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey(): Result<String> = Result.success("key")
    override suspend fun publicKey(): String = "key"
    override suspend fun installKey(password: String): Result<String> = Result.success("installed")
    override suspend fun test(password: String?): Result<String> = Result.success("connected")
    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> = Result.success("ran")
    override suspend fun runCommand(command: String): Result<String> {
        lastCommand = command
        return Result.success("command ok")
    }
    override suspend fun runCommandOnTarget(targetId: String, command: String): Result<String> {
        targetCommands += targetId to command
        return runCommand(command)
    }
    override suspend fun runActionOnTarget(targetId: String, actionId: String, dangerous: Boolean): Result<String> =
        runAction(actionId, dangerous)
}

private class FakeDeviceRepository(
    private val devices: List<TargetDevice> = listOf(target("mac_user_mac_local")),
) : DeviceRepository {
    override suspend fun devices(): List<TargetDevice> = devices
    override suspend fun groups(): List<DeviceGroup> = emptyList()
    override suspend fun currentDeviceId(): DeviceId = devices.first().id
}

private class FakeTransportRegistry : TransportRegistry

private fun target(id: String): TargetDevice = TargetDevice(
    id = DeviceId(id),
    name = id,
    platform = "macOS",
    transports = setOf(TransportId("ssh")),
    capabilities = setOf(Capability("ssh"), Capability("deck")),
)
