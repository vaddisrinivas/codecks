package io.codecks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.codecks.data.privacy.SupportBundleBuilder
import io.codecks.data.privacy.SupportBundleTempFilePolicy
import io.codecks.domain.privacy.SupportBundleSnapshot
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class SupportBundleFailure {
    BUILD_FAILED,
    CACHE_WRITE_FAILED,
    SHARE_PICKER_UNAVAILABLE,
}

sealed interface SupportBundleUiState {
    data object Idle : SupportBundleUiState
    data class Preview(
        val includedSections: List<String> = listOf("Manifest", "Health", "Typed events", "Settings"),
        val prohibitedDataStatement: String =
            "Never includes messages, clipboard text, commands, logs, prompts, paths, hosts, or device identifiers.",
    ) : SupportBundleUiState
    data object Generating : SupportBundleUiState
    data class Ready(val file: File) : SupportBundleUiState
    data class Failure(val kind: SupportBundleFailure) : SupportBundleUiState
}

class SupportBundleViewModel(
    private val tempFiles: SupportBundleTempFilePolicy,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val backgroundContext: CoroutineContext = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow<SupportBundleUiState>(SupportBundleUiState.Idle)
    val state: StateFlow<SupportBundleUiState> = _state.asStateFlow()
    private var generationJob: Job? = null
    private var pendingFile: File? = null

    init {
        viewModelScope.launch(backgroundContext) {
            tempFiles.cleanupExpired(nowEpochMs())
        }
    }

    fun preview() {
        if (_state.value is SupportBundleUiState.Generating) return
        _state.value = SupportBundleUiState.Preview()
    }

    fun generate(snapshot: SupportBundleSnapshot) {
        if ((_state.value as? SupportBundleUiState.Failure)?.kind ==
            SupportBundleFailure.SHARE_PICKER_UNAVAILABLE
        ) {
            pendingFile?.let {
                _state.value = SupportBundleUiState.Ready(it)
                return
            }
        }
        if (_state.value !is SupportBundleUiState.Preview &&
            _state.value !is SupportBundleUiState.Failure
        ) return
        _state.value = SupportBundleUiState.Generating
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val archive = withContext(backgroundContext) {
                runCatching { SupportBundleBuilder.build(snapshot) }
            }.getOrElse {
                _state.value = SupportBundleUiState.Failure(SupportBundleFailure.BUILD_FAILED)
                return@launch
            }
            val file = withContext(backgroundContext + NonCancellable) {
                tempFiles.write(archive, nowEpochMs())
            }.getOrElse {
                _state.value = SupportBundleUiState.Failure(SupportBundleFailure.CACHE_WRITE_FAILED)
                return@launch
            }
            pendingFile = file
            if (!isActive || _state.value !is SupportBundleUiState.Generating) {
                withContext(backgroundContext) { tempFiles.cancel(file) }
                pendingFile = null
                return@launch
            }
            _state.value = SupportBundleUiState.Ready(file)
        }
    }

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
        val file = (_state.value as? SupportBundleUiState.Ready)?.file ?: pendingFile
        pendingFile = null
        viewModelScope.launch(backgroundContext) { tempFiles.cancel(file) }
        _state.value = SupportBundleUiState.Idle
    }

    fun shared() {
        if (_state.value is SupportBundleUiState.Ready) {
            pendingFile = null
            _state.value = SupportBundleUiState.Idle
        }
    }

    fun shareFailed() {
        if (_state.value is SupportBundleUiState.Ready) {
            _state.value = SupportBundleUiState.Failure(SupportBundleFailure.SHARE_PICKER_UNAVAILABLE)
        }
    }
}
