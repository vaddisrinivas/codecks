package io.codecks.internalquality.m16

import io.codecks.HidCommand
import io.codecks.HidDeliveryReceipt
import io.codecks.HidInputAccess
import io.codecks.HidLifecycle
import io.codecks.HidRepository
import io.codecks.HidState
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.ConnectionTarget
import io.codecks.data.LanSshDiscovery
import io.codecks.data.SshDiscovery
import io.codecks.domain.connection.SshSetupFailureCode
import io.codecks.domain.connection.classifySshSetupFailure
import io.codecks.ui.connection.ConnectionOperation
import io.codecks.ui.connection.ConnectionViewModel
import io.codecks.ui.keyboard.KeyboardViewModel
import io.codecks.ui.keyboard.MacTextDelivery
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Real product ViewModels/sessions with only HID/SSH/clipboard transport edges scripted. */
internal class M16ProductSessions {
    private val hid = ScriptedHidEdge()
    private val ssh = ScriptedSshEdge()
    private val keyboard = KeyboardViewModel(hid, MacTextDelivery(ssh))
    private val connection = ConnectionViewModel(ssh, SshDiscovery { emptyList() }, LanSshDiscovery())

    suspend fun keyboardRoundTrip(sequence: Int) {
        keyboard.setText("bounded-$sequence")
        keyboard.typeText()
        await { !keyboard.uiState.value.isSending }
        check(keyboard.uiState.value.recentSends.firstOrNull() == "bounded-$sequence") { "keyboard_session_failed" }
        check(hid.deliveries > 0) { "keyboard_hid_edge_unused" }
    }

    suspend fun sshTypedFailure() {
        ssh.failNextTest = true
        connection.test()
        await { connection.uiState.value.operation == ConnectionOperation.Idle && connection.uiState.value.error != null }
        check(classifySshSetupFailure(connection.uiState.value.error) == SshSetupFailureCode.Authentication) { "ssh_failure_not_typed" }
    }

    suspend fun sshRecovery() {
        ssh.failNextTest = false
        connection.test()
        await { connection.uiState.value.operation == ConnectionOperation.Idle && connection.uiState.value.message != null }
        check(connection.uiState.value.error == null) { "ssh_recovery_failed" }
    }

    private suspend fun await(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(10L)
        }
        error("product_session_timeout")
    }
}

private class ScriptedHidEdge : HidRepository {
    override val state = MutableStateFlow(HidState(lifecycle = HidLifecycle.Connected, isReady = true, isConnected = true, inputAccess = HidInputAccess.Full))
    var deliveries = 0
    override fun start() = Unit
    override fun refreshHosts() = Unit
    override fun connect(address: String) = Unit
    override fun disconnect() = Unit
    override fun move(dx: Int, dy: Int) = Unit
    override fun scroll(vertical: Int, horizontal: Int) = Unit
    override fun click(buttonMask: Int) = Unit
    override fun press(buttonMask: Int) = Unit
    override fun releaseButtons() = Unit
    override fun typeText(text: String) { deliveries++ }
    override fun send(command: HidCommand) { deliveries++ }
    override suspend fun deliverText(text: String): Result<HidDeliveryReceipt> =
        Result.success(HidDeliveryReceipt("text", text.length)).also { deliveries++ }
    override suspend fun deliver(command: HidCommand): Result<HidDeliveryReceipt> =
        Result.success(HidDeliveryReceipt(command.name, 1)).also { deliveries++ }
}

private class ScriptedSshEdge : ConnectionRepository {
    override val config = MutableStateFlow(ConnectionConfig("m16.invalid", 22, "profile", hasKey = true, hostKey = "ssh-ed25519 bounded"))
    var failNextTest = false
    override suspend fun save(host: String, port: Int, user: String) { config.value = config.value.copy(host = host, port = port, user = user) }
    override suspend fun generateKey() = Result.success("bounded-public-key")
    override suspend fun publicKey() = "bounded-public-key"
    override suspend fun trustHostKey() = Result.success("trusted")
    override suspend fun confirmPendingHostKey() = Result.success("confirmed")
    override suspend fun rotateKey() = Result.success("rotated")
    override suspend fun resetTrust() = Result.success("reset")
    override suspend fun installKey(password: String) = Result.success("installed")
    override suspend fun test(password: String?): Result<String> =
        if (failNextTest.also { failNextTest = false }) Result.failure(SecurityException("Authentication failed"))
        else Result.success("Connected")
    override suspend fun runRequiredSetupProbe() = Result.success("codecks-core-ready")
    override suspend fun runAction(actionId: String, dangerous: Boolean) = Result.success("sent")
    override suspend fun runCommand(command: String) = Result.success("ok")
    override suspend fun runCommandWithInput(command: String, stdin: String) = Result.success("copied")
    override suspend fun validateCommandSyntax(command: String) = Result.success("syntax ok")
    override suspend fun runCommandSecret(command: String) = Result.success("sent")
    override suspend fun savedTargets(): List<ConnectionTarget> = emptyList()
    override suspend fun selectTarget(targetId: String) = Result.success("selected")
    override suspend fun removeTarget(targetId: String) = Result.success("removed")
}
