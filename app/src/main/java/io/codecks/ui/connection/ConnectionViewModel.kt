package io.codecks.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.LanSshDiscovery
import io.codecks.data.SshDiscovery
import io.codecks.domain.connection.MacCapabilityCheckReceipt
import io.codecks.domain.connection.MacCapabilityCheckResult
import io.codecks.domain.connection.SshSetupFailureCode
import io.codecks.domain.connection.classifySshSetupFailure
import io.codecks.domain.connection.requiredMacCapabilityProbes
import io.codecks.domain.connection.safeShellCommand
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionOperation {
    Idle,
    Scanning,
    Verifying,
    Connecting,
    Testing,
}

data class ConnectionUiState(
    val config: ConnectionConfig = ConnectionConfig(),
    val host: String = "",
    val port: String = "22",
    val user: String = "",
    val password: String = "",
    val discoveredHosts: List<String> = emptyList(),
    val operation: ConnectionOperation = ConnectionOperation.Idle,
    val connectionAttempt: Int = 0,
    val retryAtMillis: Long = 0L,
    val message: String? = null,
    val error: String? = null,
    val pendingFingerprint: String? = null,
    val setupSnapshot: SetupSnapshot = SetupSnapshot(),
    val sshTerminalReceipt: SshTerminalReceipt? = null,
    val macCapabilityReceipts: List<MacCapabilityCheckReceipt> = emptyList(),
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val sshDiscovery: SshDiscovery,
    private val lanSshDiscovery: LanSshDiscovery = LanSshDiscovery(),
    private val setupSnapshotStore: SetupSnapshotStore = SetupSnapshotStore.inMemory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ConnectionUiState(setupSnapshot = setupSnapshotStore.load()),
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionRepository.config.collect { config ->
                _uiState.update { state ->
                    state.copy(
                        config = config,
                        host = state.host.ifBlank { config.host },
                        port = if (state.port == "22" && config.port != 22) config.port.toString() else state.port,
                        user = state.user.ifBlank { config.user },
                    )
                }
            }
        }
    }

    fun setHost(value: String) = _uiState.update { it.copy(host = value, error = null) }
    fun setPort(value: String) = _uiState.update { it.copy(port = value.filter(Char::isDigit), error = null) }
    fun setUser(value: String) = _uiState.update { it.copy(user = value, error = null) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun setMessage(value: String) = _uiState.update { it.copy(message = value, error = null) }
    fun setError(value: String) = _uiState.update { it.copy(error = value, message = null) }

    fun credentialId(): String {
        val state = _uiState.value
        val port = state.port.ifBlank { "22" }
        return "${state.user}@${state.host}:$port"
    }

    fun applyPasswordCredential(id: String, password: String) {
        val expectedId = credentialId()
        if (id != expectedId) {
            _uiState.update {
                it.copy(
                    password = "",
                    message = null,
                    error = "Selected password does not match this Mac profile",
                )
            }
            return
        }
        _uiState.update { state ->
            state.copy(
                password = password,
                message = "Password filled from password manager",
                error = null,
            )
        }
    }

    fun selectHost(host: String) = _uiState.update { it.copy(host = host, error = null) }

    fun verifyHostKey() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            val state = _uiState.value
            val port = state.port.toIntOrNull()
            if (state.host.isBlank() || state.user.isBlank() || port == null) {
                _uiState.update { it.copy(error = "Enter the Mac, username, and port before verifying") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    operation = ConnectionOperation.Verifying,
                    connectionAttempt = it.connectionAttempt + 1,
                    retryAtMillis = 0L,
                    message = null,
                    error = null,
                )
            }
            runCatching {
                val endpointChanged = state.config.host != state.host.trim() ||
                    state.config.port != port ||
                    state.config.user != state.user.trim()
                if (endpointChanged) {
                    recordSetupResult(SetupStep.FindMac, SetupReceiptResult.Failed)
                }
                connectionRepository.save(state.host, port, state.user)
                connectionRepository.trustHostKey().getOrThrow()
            }.onSuccess { message ->
                recordSetupPass(SetupStep.FindMac)
                val confirmationRequired = message.startsWith("Fingerprint found:")
                if (!confirmationRequired) recordSetupPass(SetupStep.TrustMac)
                _uiState.update {
                    it.copy(
                        operation = ConnectionOperation.Idle,
                        pendingFingerprint = message.takeIf { confirmationRequired },
                        message = if (confirmationRequired) {
                            "$message. Confirm only if this is your Mac."
                        } else {
                            message
                        },
                        error = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        operation = ConnectionOperation.Idle,
                        error = error.message ?: "Could not check this Mac",
                    )
                }
            }
        }
    }

    fun confirmHostKey() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operation = ConnectionOperation.Verifying,
                    connectionAttempt = it.connectionAttempt + 1,
                    retryAtMillis = 0L,
                    message = null,
                    error = null,
                )
            }
            connectionRepository.confirmPendingHostKey()
                .onSuccess {
                    recordSetupPass(SetupStep.TrustMac)
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            pendingFingerprint = null,
                            message = "Mac trusted. You can save this Mac now.",
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    recordSetupResult(SetupStep.TrustMac, SetupReceiptResult.Failed)
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Could not trust this Mac",
                        )
                    }
                }
        }
    }

    fun scan() {
        scanWith(
            discovery = sshDiscovery,
            emptyMessage = "No Mac advertising SSH found. Enter hostname/IP, or run local network scan.",
            scanningMessage = null,
        )
    }

    fun scanLocalNetwork() {
        scanWith(
            discovery = lanSshDiscovery,
            emptyMessage = "No Macs found on this local network",
            scanningMessage = "Checking this network for Macs. This may contact devices on your Wi‑Fi.",
        )
    }

    private fun scanWith(
        discovery: SshDiscovery,
        emptyMessage: String,
        scanningMessage: String?,
    ) {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(operation = ConnectionOperation.Scanning, message = scanningMessage, error = null)
            }
            val port = _uiState.value.port.toIntOrNull() ?: 22
            val hosts = runCatching { discovery.scan(port) }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Could not scan this network") }
                }
                .getOrDefault(emptyList())
            if (hosts.isNotEmpty()) {
                recordSetupPass(SetupStep.FindMac)
            }
            _uiState.update { state ->
                state.copy(
                    discoveredHosts = hosts,
                    host = state.host.ifBlank { hosts.singleOrNull().orEmpty() },
                    operation = ConnectionOperation.Idle,
                    message = when {
                        hosts.isEmpty() -> emptyMessage
                        hosts.size == 1 -> "Mac found"
                        else -> "${hosts.size} Macs found"
                    },
                )
            }
        }
    }

    fun authorize() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            val state = _uiState.value
            val port = state.port.toIntOrNull()
            if (state.host.isBlank() || state.user.isBlank() || port == null || state.password.isEmpty()) {
                _uiState.update { it.copy(error = "Enter the Mac, username, password, and port") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    operation = ConnectionOperation.Connecting,
                    connectionAttempt = it.connectionAttempt + 1,
                    retryAtMillis = 0L,
                    message = null,
                    error = null,
                )
            }
            val startedAt = System.currentTimeMillis()
            runCatching {
                connectionRepository.save(state.host, port, state.user)
                connectionRepository.installKey(state.password).getOrThrow()
                recordSetupPass(SetupStep.Authorize)
                val message = connectionRepository.test().getOrThrow()
                val receipts = verifyRequiredMacCapabilities()
                receipts.firstOrNull { it.result == MacCapabilityCheckResult.Failed }?.let {
                    error("Required Mac capability failed: ${it.capability.persistedCode}")
                }
                val completedAt = System.currentTimeMillis()
                val duration = checkedTerminalDuration(startedAt, completedAt)
                recordSetupPass(SetupStep.VerifyControls)
                Triple(message, receipts, completedAt to duration)
            }.onSuccess { (message, receipts, timing) ->
                val (completedAt, duration) = timing
                val snapshot = _uiState.value.setupSnapshot
                val config = _uiState.value.config
                _uiState.update {
                    it.copy(
                        operation = ConnectionOperation.Idle,
                        password = "",
                        message = message,
                        macCapabilityReceipts = receipts,
                        sshTerminalReceipt = SshTerminalReceipt(
                            setupRevision = snapshot.revisionToken(),
                            macTargetId = config.setupTargetId(),
                            result = SshTerminalResult.Passed,
                            attempt = it.connectionAttempt.coerceAtLeast(1),
                            durationMs = duration,
                            completedAtEpochMs = completedAt,
                            confirmedCapabilities = REQUIRED_CORE_MAC_CAPABILITIES,
                            capabilityCheckedAtEpochMs = receipts.associate {
                                receipt -> receipt.capability to receipt.checkedAtEpochMs
                            },
                        ),
                        error = null,
                    )
                }
            }.onFailure { error ->
                recordSetupResult(SetupStep.VerifyControls, SetupReceiptResult.Failed)
                _uiState.update {
                    it.copy(
                        operation = ConnectionOperation.Idle,
                        password = "",
                        error = error.message ?: "Could not connect",
                    )
                }
            }
        }
    }

    fun test() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operation = ConnectionOperation.Testing,
                    connectionAttempt = it.connectionAttempt + 1,
                    retryAtMillis = 0L,
                    message = null,
                    error = null,
                )
            }
            val startedAt = System.currentTimeMillis()
            connectionRepository.test()
                .mapCatching { message ->
                    val receipts = verifyRequiredMacCapabilities()
                    receipts.firstOrNull { it.result == MacCapabilityCheckResult.Failed }?.let {
                        error("Required Mac capability failed: ${it.capability.persistedCode}")
                    }
                    val completedAt = System.currentTimeMillis()
                    val duration = checkedTerminalDuration(startedAt, completedAt)
                    Triple(message, receipts, completedAt to duration)
                }
                .onSuccess { (message, receipts, timing) ->
                    val (completedAt, duration) = timing
                    recordSetupPass(SetupStep.VerifyControls)
                    val snapshot = _uiState.value.setupSnapshot
                    val config = _uiState.value.config
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            message = message,
                            macCapabilityReceipts = receipts,
                            sshTerminalReceipt = SshTerminalReceipt(
                                setupRevision = snapshot.revisionToken(),
                                macTargetId = config.setupTargetId(),
                                result = SshTerminalResult.Passed,
                                attempt = it.connectionAttempt.coerceAtLeast(1),
                                durationMs = duration,
                                completedAtEpochMs = completedAt,
                                confirmedCapabilities = REQUIRED_CORE_MAC_CAPABILITIES,
                                capabilityCheckedAtEpochMs = receipts.associate {
                                    receipt -> receipt.capability to receipt.checkedAtEpochMs
                                },
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    recordSetupResult(SetupStep.VerifyControls, SetupReceiptResult.Failed)
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Connection test failed",
                        )
                    }
                }
        }
    }

    private suspend fun verifyRequiredMacCapabilities(): List<MacCapabilityCheckReceipt> =
        requiredMacCapabilityProbes(REQUIRED_CORE_MAC_CAPABILITIES).map { probe ->
            val checkedAt = System.currentTimeMillis()
            connectionRepository.runCommand(probe.safeShellCommand()).fold(
                onSuccess = {
                    MacCapabilityCheckReceipt(
                        capability = probe.capability,
                        result = MacCapabilityCheckResult.Passed,
                        failureCode = null,
                        checkedAtEpochMs = checkedAt,
                    )
                },
                onFailure = { error ->
                    MacCapabilityCheckReceipt(
                        capability = probe.capability,
                        result = MacCapabilityCheckResult.Failed,
                        failureCode = classifySshSetupFailure(error.message).takeUnless {
                            it == SshSetupFailureCode.Unknown
                        } ?: SshSetupFailureCode.Unknown,
                        checkedAtEpochMs = checkedAt,
                    )
                },
            )
        }

    fun rotateKey() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operation = ConnectionOperation.Connecting,
                    connectionAttempt = it.connectionAttempt + 1,
                    retryAtMillis = 0L,
                    message = null,
                    error = null,
                )
            }
            connectionRepository.rotateKey()
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(operation = ConnectionOperation.Idle, message = message, error = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Could not rotate SSH key",
                        )
                    }
                }
        }
    }

    fun resetTrust() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operation = ConnectionOperation.Verifying,
                    connectionAttempt = it.connectionAttempt + 1,
                    retryAtMillis = 0L,
                    message = null,
                    error = null,
                )
            }
            connectionRepository.resetTrust()
                .onSuccess {
                    val snapshot = setupSnapshotStore.record(
                        SetupStep.TrustMac,
                        SetupReceiptResult.Failed,
                    )
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            pendingFingerprint = null,
                            setupSnapshot = snapshot,
                            message = "Mac trust reset. Trust this Mac again before running controls.",
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Could not reset Mac trust",
                        )
                    }
                }
        }
    }

    fun removeCurrentTarget() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        val config = _uiState.value.config
        if (!config.isConfigured) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(operation = ConnectionOperation.Connecting, message = null, error = null)
            }
            val targetId = connectionRepository.savedTargets()
                .firstOrNull { target ->
                    target.host == config.host &&
                        target.port == config.port &&
                        target.user == config.user
                }
                ?.id
            val result = if (targetId == null) {
                Result.failure(IllegalStateException("Saved Mac target not found"))
            } else {
                connectionRepository.removeTarget(targetId)
            }
            result
                .onSuccess { message ->
                    val snapshot = setupSnapshotStore.clear()
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            host = "",
                            port = "22",
                            user = "",
                            password = "",
                            pendingFingerprint = null,
                            setupSnapshot = snapshot,
                            message = message,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Could not remove Mac",
                        )
                    }
                }
        }
    }

    fun resumeSetupStep(): SetupStep? = _uiState.value.setupSnapshot.firstMandatoryNotPassed

    private fun recordSetupPass(step: SetupStep) {
        recordSetupResult(step, SetupReceiptResult.Passed)
    }

    private fun recordSetupResult(step: SetupStep, result: SetupReceiptResult) {
        val snapshot = setupSnapshotStore.record(step, result)
        _uiState.update { it.copy(setupSnapshot = snapshot) }
    }
}

internal fun ConnectionConfig.setupTargetId(): String {
    val canonical = "${user.trim()}@${host.trim().lowercase()}:$port|${hostKey.trim()}"
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal fun checkedTerminalDuration(startedAtMillis: Long, completedAtMillis: Long): Long {
    val duration = (completedAtMillis - startedAtMillis).coerceAtLeast(0L)
    check(duration <= 30_000L) { "Setup test timed out" }
    return duration
}
