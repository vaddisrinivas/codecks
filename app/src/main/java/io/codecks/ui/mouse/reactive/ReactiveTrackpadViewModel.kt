package io.codecks.ui.mouse.reactive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.codecks.domain.reactive.ControlId
import io.codecks.domain.reactive.MacStateConnectionState
import io.codecks.domain.reactive.MacStateRepository
import io.codecks.domain.reactive.MacStateSnapshot
import io.codecks.domain.reactive.ReactiveActionExecutor
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveAuthorization
import io.codecks.domain.reactive.ReactiveControl
import io.codecks.domain.reactive.ReactiveEngine
import io.codecks.domain.reactive.ReactiveIcon
import io.codecks.domain.reactive.ReactiveRisk
import io.codecks.domain.reactive.ReactiveTrackpadContext
import io.codecks.domain.reactive.ReactiveTrackpadMode
import io.codecks.domain.reactive.TrackpadVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReactiveControlUi(
    val id: ControlId,
    val title: String,
    val subtitle: String?,
    val icon: ReactiveIcon,
    val risk: ReactiveRisk,
    val reversible: Boolean,
    val reason: String,
)

data class ReactiveConfirmationUi(
    val controlId: ControlId,
    val title: String,
    val body: String,
)

data class ReactiveTrackpadUiState(
    val macState: MacStateSnapshot? = null,
    val controls: List<ReactiveControlUi> = emptyList(),
    val mode: ReactiveTrackpadMode = ReactiveTrackpadMode.Pointer,
    val connectionLabel: String = "Setup needed",
    val lastResult: ReactiveActionResult? = null,
    val pendingConfirmation: ReactiveConfirmationUi? = null,
    val loading: Boolean = false,
)

class ReactiveTrackpadViewModel(
    private val macStateRepository: MacStateRepository,
    private val engine: ReactiveEngine,
    private val executor: ReactiveActionExecutor,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _mode = MutableStateFlow(ReactiveTrackpadMode.Pointer)
    private val _controls = MutableStateFlow<List<ReactiveControl>>(emptyList())
    private val _lastResult = MutableStateFlow<ReactiveActionResult?>(null)
    private val _pendingConfirmation = MutableStateFlow<PendingReactiveConfirmation?>(null)
    private val _loading = MutableStateFlow(false)

    private data class ReactiveTrackpadBaseState(
        val macState: MacStateSnapshot?,
        val connection: MacStateConnectionState,
        val controls: List<ReactiveControl>,
        val mode: ReactiveTrackpadMode,
    )

    val uiState: StateFlow<ReactiveTrackpadUiState> = combine(
        combine(
            macStateRepository.state,
            macStateRepository.connection,
            _controls,
            _mode,
        ) { macState, connection, controls, mode ->
            ReactiveTrackpadBaseState(
                macState = macState,
                connection = connection,
                controls = controls,
                mode = mode,
            )
        },
        _lastResult,
        _pendingConfirmation,
        _loading,
    ) { base, lastResult, pendingConfirmation, loading ->
        ReactiveTrackpadUiState(
            macState = base.macState,
            controls = base.controls.map { it.toUi() },
            mode = base.mode,
            connectionLabel = base.connection.label(base.macState),
            lastResult = lastResult,
            pendingConfirmation = pendingConfirmation?.toUi(),
            loading = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReactiveTrackpadUiState(),
    )

    init {
        viewModelScope.launch {
            combine(macStateRepository.state, _mode) { macState, mode ->
                if (macState == null) {
                    emptyList()
                } else {
                    engine.controls(
                        state = macState,
                        context = ReactiveTrackpadContext(mode = mode),
                        nowMillis = nowMillis(),
                    ).controls
                }
            }.collect { controls ->
                _controls.value = controls
            }
        }
    }

    fun setVisible(visible: Boolean) {
        if (visible) {
            macStateRepository.start(TrackpadVisibility.Visible)
        } else {
            macStateRepository.stop()
        }
    }

    fun refreshBasic() {
        viewModelScope.launch {
            _loading.value = true
            try {
                macStateRepository.refreshBasic()
            } finally {
                _loading.value = false
            }
        }
    }

    fun runControl(controlId: ControlId) {
        val control = _controls.value.firstOrNull { it.id == controlId } ?: return
        execute(control, ReactiveAuthorization())
    }

    fun confirmPending() {
        val pending = _pendingConfirmation.value ?: return
        execute(
            pending.control,
            ReactiveAuthorization(confirmedActionRevision = pending.actionRevision),
        )
    }

    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    fun runReviewed(controlId: ControlId) {
        val control = _controls.value.firstOrNull { it.id == controlId } ?: return
        execute(
            control,
            ReactiveAuthorization(reviewedActionRevision = control.actionRevision),
        )
    }

    private fun execute(
        control: ReactiveControl,
        authorization: ReactiveAuthorization,
    ) {
        viewModelScope.launch {
            val outcome = executor.execute(
                control = control,
                authorization = authorization,
                nowMillis = nowMillis(),
                currentState = macStateRepository.state.value,
            )
            when (val result = outcome.result) {
                is ReactiveActionResult.RequiresConfirmation -> {
                    _pendingConfirmation.value = PendingReactiveConfirmation(
                        control = control,
                        actionRevision = result.actionRevision,
                        title = result.title,
                        body = result.body,
                    )
                }
                else -> {
                    _pendingConfirmation.value = null
                    _lastResult.value = result
                }
            }
        }
    }
}

private data class PendingReactiveConfirmation(
    val control: ReactiveControl,
    val actionRevision: io.codecks.domain.reactive.ActionRevision,
    val title: String,
    val body: String,
) {
    fun toUi(): ReactiveConfirmationUi = ReactiveConfirmationUi(
        controlId = control.id,
        title = title,
        body = body,
    )
}

private fun ReactiveControl.toUi(): ReactiveControlUi = ReactiveControlUi(
    id = id,
    title = title,
    subtitle = subtitle,
    icon = icon,
    risk = risk,
    reversible = reversible,
    reason = reason,
)

private fun MacStateConnectionState.label(macState: MacStateSnapshot?): String = when (this) {
    MacStateConnectionState.Idle -> "Setup needed"
    is MacStateConnectionState.Connecting -> "Connecting"
    is MacStateConnectionState.Connected -> macState?.frontApp?.value?.displayName?.let { "$it ready" } ?: "Ready"
    is MacStateConnectionState.Degraded -> "Limited"
    is MacStateConnectionState.Disconnected -> "Setup needed"
}
