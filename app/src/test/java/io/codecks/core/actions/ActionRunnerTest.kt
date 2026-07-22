package io.codecks.core.actions

import io.codecks.data.ActionRepository
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
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

        val spec = ActionSpec.ShellCommand(
            "hello",
            "Hello",
            "printf hi",
            review = CommandReview(
                reviewedRevision = commandRevision(
                    command = "printf hi",
                    targetSelector = TargetSelector.CurrentDevice,
                    origin = CommandOrigin.UserAuthored,
                    dangerous = false,
                ),
            ),
        )

        val result = runner.run(spec)

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
                review = CommandReview(
                    reviewedRevision = commandRevision(
                        command = "printf hi",
                        targetSelector = TargetSelector.AllCompatibleDevices,
                        origin = CommandOrigin.UserAuthored,
                        dangerous = false,
                    ),
                ),
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

        assertEquals(ActionResultStatus.RequiresReview, result.status)
        assertEquals(null, connection.lastCommand)
    }

    @Test
    fun generatedShellCommand_runsFreeCommandAfterReview() = runTest {
        val connection = FakeConnectionRepository()
        val runner = testRunner(connection = connection)

        val spec = ActionSpec.ShellCommand(
            id = "generated",
            title = "Generated",
            command = "echo hello",
            trustLevel = ShellTrustLevel.Generated,
            commandOrigin = CommandOrigin.AiGenerated,
            review = CommandReview(
                reviewedRevision = commandRevision(
                    command = "echo hello",
                    targetSelector = TargetSelector.CurrentDevice,
                    origin = CommandOrigin.AiGenerated,
                    dangerous = false,
                ),
            ),
        )
        val result = runner.run(spec)

        assertEquals(ActionResultStatus.Succeeded, result.status)
        assertEquals("echo hello", connection.lastCommand)
    }

    @Test
    fun exactBundledDeckCommand_usesBundledExecutionPath() = runTest {
        val connection = FakeConnectionRepository()
        val runner = testRunner(connection = connection)

        val result = runner.run(
            ActionSpec.DeckActionSpec(
                DeckAction("finder", "Finder", ActionKind.Ssh, ActionIcon.Finder, command = "open -a Finder"),
            ),
        )

        assertEquals(ActionResultStatus.Succeeded, result.status)
        assertEquals("open -a Finder", connection.lastBundledCommand)
    }

    @Test
    fun dangerousBundledDeckCommand_runsAfterExplicitApproval() = runTest {
        val connection = FakeConnectionRepository()
        val runner = testRunner(connection = connection)
        val action = DeckAction(
            "finder",
            "Finder",
            ActionKind.Ssh,
            ActionIcon.Finder,
            command = "open -a Finder",
            dangerous = true,
        )

        val blocked = runner.run(ActionSpec.DeckActionSpec(action))
        val approved = runner.run(ActionSpec.DeckActionSpec(action), allowDangerous = true)

        assertEquals(ActionResultStatus.RequiresConfirmation, blocked.status)
        assertEquals(ActionResultStatus.Succeeded, approved.status)
        assertEquals("open -a Finder", connection.lastBundledCommand)
    }

    @Test
    fun customDeckCommand_usesReviewedCustomExecutionPath() = runTest {
        val connection = FakeConnectionRepository()
        val runner = testRunner(connection = connection)

        val command = "echo changed"
        val result = runner.run(
            ActionSpec.DeckActionSpec(
                DeckAction(
                    "finder",
                    "Finder",
                    ActionKind.Ssh,
                    ActionIcon.Finder,
                    command = command,
                    commandOrigin = CommandOrigin.UserAuthored,
                    commandReview = CommandReview(
                        reviewedRevision = commandRevision(
                            command = command,
                            targetSelector = TargetSelector.CurrentDevice,
                            origin = CommandOrigin.UserAuthored,
                            dangerous = false,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(ActionResultStatus.Succeeded, result.status)
        assertEquals(null, connection.lastBundledCommand)
        assertEquals("echo changed", connection.lastCommand)
    }

    @Test
    fun customDeckCommand_requiresMatchingReviewBeforeRunning() = runTest {
        val connection = FakeConnectionRepository()
        val runner = testRunner(connection = connection)

        val result = runner.run(
            ActionSpec.DeckActionSpec(
                DeckAction(
                    "finder",
                    "Finder",
                    ActionKind.Ssh,
                    ActionIcon.Finder,
                    command = "echo changed",
                    commandOrigin = CommandOrigin.UserAuthored,
                ),
            ),
        )

        assertEquals(ActionResultStatus.RequiresReview, result.status)
        assertEquals(null, connection.lastCommand)
        assertEquals(null, connection.lastBundledCommand)
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
    private val action = DeckAction("finder", "Finder", ActionKind.Ssh, ActionIcon.Finder, command = "open -a Finder")
    override fun favorites(): List<DeckAction> = listOf(action)
    override fun observeFavorites(): Flow<List<DeckAction>> = MutableStateFlow(listOf(action))
    override fun allActions(): List<DeckAction> = listOf(action)
    override suspend fun saveFavorites(actions: List<DeckAction>) = Unit
    override suspend fun exportLayout(): Result<String> = Result.success("")
    override suspend fun validateLayout(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun importLayout(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun run(action: DeckAction): Result<String> = Result.success("${action.label} ok")
    override suspend fun test(action: DeckAction): Result<String> = Result.success("${action.label} verified")
}

private class FakeConnectionRepository : ConnectionRepository {
    var lastCommand: String? = null
    var lastBundledCommand: String? = null
    val targetCommands = mutableListOf<Pair<String, String>>()
    override val config = MutableStateFlow(ConnectionConfig("mac.local", 22, "user", hasKey = true, hostKey = "key"))
    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey(): Result<String> = Result.success("key")
    override suspend fun publicKey(): String = "key"
    override suspend fun trustHostKey(): Result<String> = Result.success("trusted")
    override suspend fun confirmPendingHostKey(): Result<String> = Result.success("confirmed")
    override suspend fun rotateKey(): Result<String> = Result.success("rotated")
    override suspend fun resetTrust(): Result<String> = Result.success("reset")
    override suspend fun installKey(password: String): Result<String> = Result.success("installed")
    override suspend fun test(password: String?): Result<String> = Result.success("connected")
    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> = Result.success("ran")
    override suspend fun runCommand(command: String): Result<String> {
        lastCommand = command
        return Result.success("command ok")
    }
    override suspend fun runBundledCommand(command: String): Result<String> {
        lastBundledCommand = command
        return Result.success("bundled command ok")
    }
    override suspend fun runCommandWithInput(command: String, stdin: String): Result<String> = runCommand(command)
    override suspend fun validateCommandSyntax(command: String): Result<String> = Result.success("syntax ok")
    override suspend fun runCommandSecret(command: String): Result<String> = runCommand(command)
    override suspend fun selectTarget(targetId: String): Result<String> = Result.success("selected")
    override suspend fun removeTarget(targetId: String): Result<String> = Result.success("removed")
    override suspend fun runCommandOnTarget(targetId: String, command: String): Result<String> {
        targetCommands += targetId to command
        return runCommand(command)
    }
    override suspend fun runReviewedCommandOnTarget(targetId: String, command: String): Result<String> {
        targetCommands += targetId to command
        return runCommand(command)
    }
    override suspend fun runBundledCommandOnTarget(targetId: String, command: String): Result<String> {
        targetCommands += targetId to command
        lastBundledCommand = command
        return Result.success("bundled command ok")
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
