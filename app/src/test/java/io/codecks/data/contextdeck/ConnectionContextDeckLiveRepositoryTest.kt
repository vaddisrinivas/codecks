package io.codecks.data.contextdeck

import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.domain.contextdeck.AnalogControlKind
import io.codecks.domain.contextdeck.LiveSignalId
import io.codecks.domain.contextdeck.LiveSignalValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionContextDeckLiveRepositoryTest {
    @Test
    fun parserBuildsTruthfulKnownAndUnavailableState() {
        val state = parseContextDeckLiveSnapshot("1\t42\t1\t25\t0\t-1", 9L)

        assertEquals(LiveSignalValue.Active, state.signals.first { it.id == LiveSignalId.Mute }.value)
        assertEquals(LiveSignalValue.Unknown, state.signals.first { it.id == LiveSignalId.Camera }.value)
        assertEquals(42, state.analogControls.first { it.kind == AnalogControlKind.Volume }.valuePercent)
        assertEquals(null, state.analogControls.first { it.kind == AnalogControlKind.Brightness }.valuePercent)
    }

    @Test
    fun parserRejectsWrongCountBitsAndRanges() {
        assertTrue(runCatching { parseContextDeckLiveSnapshot("1\t2", 0) }.isFailure)
        assertTrue(runCatching { parseContextDeckLiveSnapshot("yes\t42\t1\t25\t0\t-1", 0) }.isFailure)
        assertTrue(runCatching { parseContextDeckLiveSnapshot("1\t101\t1\t25\t0\t-1", 0) }.isFailure)
        assertTrue(runCatching { parseContextDeckLiveSnapshot("1\t42\t1\t25\t2\t-1", 0) }.isFailure)
    }

    @Test
    fun analogCommandsContainOnlyBoundedGeneratedNumbers() {
        assertEquals("/usr/bin/osascript -e 'set volume output volume 75'", volumeCommand(75))
        assertTrue(brightnessCommand(20).contains("-brightness=20%"))
        assertTrue(timelineCommand(33).contains("d*33/100"))
        assertTrue(runCatching { volumeCommand(101) }.isFailure)
    }

    @Test
    fun repositoryUsesBundledReadAndOneCommitCommand() = runTest {
        val connection = FakeConnectionRepository("0\t50\t0\t-1\t1\t80")
        val repository = ConnectionContextDeckLiveRepository(connection) { 100L }

        assertTrue(repository.refresh().isSuccess)
        assertTrue(repository.setAnalog(AnalogControlKind.Volume, 60).isSuccess)
        assertEquals(2, connection.commands.size)
        assertEquals(CONTEXT_DECK_LIVE_STATE_COMMAND, connection.commands.first())
        assertEquals(volumeCommand(60), connection.commands.last())
    }
}

private class FakeConnectionRepository(private val output: String) : ConnectionRepository {
    val commands = mutableListOf<String>()
    override val config: Flow<ConnectionConfig> = flowOf(ConnectionConfig())
    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey(): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun publicKey(): String = ""
    override suspend fun trustHostKey(): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun confirmPendingHostKey(): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun rotateKey(): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun resetTrust(): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun installKey(password: String): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun test(password: String?): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun runCommand(command: String): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun runCommandWithInput(command: String, stdin: String): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun validateCommandSyntax(command: String): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun runCommandSecret(command: String): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun selectTarget(targetId: String): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun removeTarget(targetId: String): Result<String> = Result.failure(UnsupportedOperationException())
    override suspend fun runBundledCommand(command: String): Result<String> {
        commands += command
        return Result.success(if (commands.size == 1) output else "ok")
    }
}
