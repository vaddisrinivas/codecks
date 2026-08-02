package io.codecks.ui.keyboard

import io.codecks.HidCommand
import io.codecks.HidHost
import io.codecks.HidLifecycle
import io.codecks.HidInputAccess
import io.codecks.HidRepository
import io.codecks.HidState
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.ConnectionTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun failedEnterKeepsDraftForRetry() = runTest(dispatcher) {
        val hidRepository = FakeHidRepository(
            initialState = HidState(
                status = "Connected",
                lifecycle = HidLifecycle.Connected,
                isReady = true,
                isConnected = true,
                hosts = listOf(HidHost("AA:BB", "Mac")),
                selectedHostAddress = "AA:BB",
            ),
            sendFailure = IllegalStateException("Enter failed"),
        )
        val viewModel = KeyboardViewModel(
            hidRepository = hidRepository,
            macTextDelivery = MacTextDelivery(ReadyConnectionRepository()),
        )

        viewModel.setText("ship it")
        viewModel.typeText()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("ship it", state.text)
        assertEquals("Enter failed", state.status)
        assertTrue(hidRepository.typedTexts.contains("ship it"))
        assertTrue(hidRepository.sentCommands.contains(HidCommand.Enter))
        assertTrue(state.recentSends.isEmpty())
        assertFalse(state.isSending)
    }

    @Test
    fun pasteboardWorksWithoutHidButNeverCrossesLockedInputBoundary() = runTest(dispatcher) {
        val hid = FakeHidRepository(HidState(inputAccess = HidInputAccess.Full))
        val connection = ReadyConnectionRepository(
            onClipboardWrite = { hid.state.value = hid.state.value.copy(inputAccess = HidInputAccess.PointerOnly) },
        )
        val viewModel = KeyboardViewModel(hid, MacTextDelivery(connection))
        viewModel.setDeliveryMode(KeyboardDeliveryMode.MacClipboardPaste)
        viewModel.setText("emoji ❤️")

        viewModel.typeText()
        runCurrent()

        assertEquals("Unlock phone to use keyboard", viewModel.uiState.value.status)
        assertTrue(connection.clipboardWrites == 1)
        assertTrue(connection.commands.isEmpty())
        assertEquals("emoji ❤️", viewModel.uiState.value.text)
    }

    @Test
    fun offlinePasteboardUsesExplicitSshFallback() = runTest(dispatcher) {
        val hid = FakeHidRepository(HidState(inputAccess = HidInputAccess.Full))
        val connection = ReadyConnectionRepository()
        val viewModel = KeyboardViewModel(hid, MacTextDelivery(connection))
        viewModel.setDeliveryMode(KeyboardDeliveryMode.MacClipboardPaste)
        viewModel.setText("emoji ❤️")

        viewModel.typeText()
        runCurrent()

        assertEquals("", viewModel.uiState.value.text)
        assertEquals(1, connection.clipboardWrites)
        assertEquals(2, connection.commands.size)
    }
}

private class FakeHidRepository(
    initialState: HidState = HidState(),
    private val sendFailure: Throwable? = null,
) : HidRepository {
    override val state = MutableStateFlow(initialState)
    val typedTexts = mutableListOf<String>()
    val sentCommands = mutableListOf<HidCommand>()

    override fun start() = Unit
    override fun refreshHosts() = Unit
    override fun connect(address: String) = Unit
    override fun disconnect() = Unit
    override fun move(dx: Int, dy: Int) = Unit
    override fun scroll(vertical: Int, horizontal: Int) = Unit
    override fun click(buttonMask: Int) = Unit
    override fun press(buttonMask: Int) = Unit
    override fun releaseButtons() = Unit

    override fun typeText(text: String) {
        typedTexts += text
    }

    override fun send(command: HidCommand) {
        sentCommands += command
        if (command == HidCommand.Enter && sendFailure != null) throw sendFailure
    }
}

private class ReadyConnectionRepository(
    private val onClipboardWrite: () -> Unit = {},
) : ConnectionRepository {
    var clipboardWrites = 0
    val commands = mutableListOf<String>()
    override val config = MutableStateFlow(
        ConnectionConfig("mac.local", 22, "user", hasKey = true, hostKey = "mac ssh-ed25519 key"),
    )

    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey(): Result<String> = Result.success("public-key")
    override suspend fun publicKey(): String = "public-key"
    override suspend fun trustHostKey(): Result<String> = Result.success("trusted")
    override suspend fun confirmPendingHostKey(): Result<String> = Result.success("confirmed")
    override suspend fun rotateKey(): Result<String> = Result.success("rotated")
    override suspend fun resetTrust(): Result<String> = Result.success("reset")
    override suspend fun installKey(password: String): Result<String> = Result.success("installed")
    override suspend fun test(password: String?): Result<String> = Result.success("connected")
    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> = Result.success("sent")
    override suspend fun runCommand(command: String): Result<String> {
        commands += command
        return Result.success("sent")
    }
    override suspend fun runCommandWithInput(command: String, stdin: String): Result<String> {
        clipboardWrites += 1
        onClipboardWrite()
        return Result.success("sent")
    }
    override suspend fun validateCommandSyntax(command: String): Result<String> = Result.success("syntax ok")
    override suspend fun runCommandSecret(command: String): Result<String> = Result.success("sent")
    override suspend fun savedTargets(): List<ConnectionTarget> = emptyList()
    override suspend fun selectTarget(targetId: String): Result<String> = Result.success("selected")
    override suspend fun removeTarget(targetId: String): Result<String> = Result.success("removed")
}
