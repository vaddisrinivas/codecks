package io.codecks.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.SshDiscovery
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
    val message: String? = null,
    val error: String? = null,
    val pendingFingerprint: String? = null,
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val sshDiscovery: SshDiscovery,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionUiState())
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
                it.copy(operation = ConnectionOperation.Verifying, message = null, error = null)
            }
            runCatching {
                connectionRepository.save(state.host, port, state.user)
                connectionRepository.trustHostKey().getOrThrow()
            }.onSuccess { message ->
                _uiState.update {
                    it.copy(
                        operation = ConnectionOperation.Idle,
                        pendingFingerprint = message,
                        message = "$message. Trust it only if this matches your Mac.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        operation = ConnectionOperation.Idle,
                        error = error.message ?: "Could not verify Mac fingerprint",
                    )
                }
            }
        }
    }

    fun confirmHostKey() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(operation = ConnectionOperation.Verifying, message = null, error = null)
            }
            connectionRepository.confirmPendingHostKey()
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            pendingFingerprint = null,
                            message = message,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Could not trust Mac fingerprint",
                        )
                    }
                }
        }
    }

    fun scan() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(operation = ConnectionOperation.Scanning, message = null, error = null)
            }
            val port = _uiState.value.port.toIntOrNull() ?: 22
            val hosts = runCatching { sshDiscovery.scan(port) }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Could not scan this network") }
                }
                .getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(
                    discoveredHosts = hosts,
                    host = state.host.ifBlank { hosts.singleOrNull().orEmpty() },
                    operation = ConnectionOperation.Idle,
                    message = when {
                        hosts.isEmpty() -> "No Mac with Remote Login found"
                        hosts.size == 1 -> "Mac found"
                        else -> "${hosts.size} compatible devices found"
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
                it.copy(operation = ConnectionOperation.Connecting, message = null, error = null)
            }
            runCatching {
                connectionRepository.save(state.host, port, state.user)
                connectionRepository.installKey(state.password).getOrThrow()
                connectionRepository.test().getOrThrow()
            }.onSuccess { message ->
                _uiState.update {
                    it.copy(
                        operation = ConnectionOperation.Idle,
                        password = "",
                        message = message,
                        error = null,
                    )
                }
            }.onFailure { error ->
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
                it.copy(operation = ConnectionOperation.Testing, message = null, error = null)
            }
            connectionRepository.test()
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(operation = ConnectionOperation.Idle, message = message)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Connection test failed",
                        )
                    }
                }
        }
    }

    fun rotateKey() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(operation = ConnectionOperation.Connecting, message = null, error = null)
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
                it.copy(operation = ConnectionOperation.Verifying, message = null, error = null)
            }
            connectionRepository.resetTrust()
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            pendingFingerprint = null,
                            message = message,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            error = error.message ?: "Could not reset Mac fingerprint",
                        )
                    }
                }
        }
    }

    fun removeCurrentTarget() {
        if (_uiState.value.operation != ConnectionOperation.Idle) return
        val targetId = _uiState.value.config.targetId()
        if (targetId.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(operation = ConnectionOperation.Connecting, message = null, error = null)
            }
            connectionRepository.removeTarget(targetId)
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(
                            operation = ConnectionOperation.Idle,
                            host = "",
                            port = "22",
                            user = "",
                            password = "",
                            pendingFingerprint = null,
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
}

private fun ConnectionConfig.targetId(): String =
    if (host.isBlank() || user.isBlank()) "" else "mac_${user}_${host}_${port}"
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("")
        .trim('_')
