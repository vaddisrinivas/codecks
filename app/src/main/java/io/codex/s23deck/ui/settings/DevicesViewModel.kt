package io.codex.s23deck.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.domain.device.DeviceGroup
import io.codex.s23deck.domain.device.DeviceRepository
import io.codex.s23deck.domain.device.TargetDevice
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUiState(
    val devices: List<TargetDevice> = emptyList(),
    val groups: List<DeviceGroup> = emptyList(),
    val currentDeviceId: String? = null,
    val message: String? = null,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionRepository.config.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    devices = deviceRepository.devices(),
                    groups = deviceRepository.groups(),
                    currentDeviceId = deviceRepository.currentDeviceId()?.value,
                )
            }
        }
    }

    fun select(deviceId: String) {
        viewModelScope.launch {
            connectionRepository.selectTarget(deviceId)
                .onSuccess { message ->
                    _uiState.update { it.copy(message = message) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "Could not switch target") }
                }
        }
    }
}
