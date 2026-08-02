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
    DELETE_FAILED,
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
    data class ChooserOpened(
        val file: File,
        val targetChosen: Boolean = false,
    ) : SupportBundleUiState
    data class PendingRetained(val file: File) : SupportBundleUiState
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
    private val initializationJob = viewModelScope.launch(backgroundContext) {
        tempFiles.cleanupExpired(nowEpochMs())
        val recovered = tempFiles.pendingFiles().firstOrNull()
        if (recovered != null) {
            pendingFile = recovered
            if (_state.value !is SupportBundleUiState.Generating) {
                _state.value = SupportBundleUiState.PendingRetained(recovered)
            }
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
            initializationJob.join()
            pendingFile?.takeIf(File::isFile)?.let { recovered ->
                _state.value = SupportBundleUiState.PendingRetained(recovered)
                return@launch
            }
            val archive = try {
                withContext(backgroundContext) { SupportBundleBuilder.build(snapshot) }
            } catch (error: Throwable) {
                error.rethrowIfCancellationOrFatal()
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
                val deleted = withContext(backgroundContext + NonCancellable) { tempFiles.cancel(file) }
                pendingFile = if (deleted) null else file
                if (!deleted) {
                    _state.value = SupportBundleUiState.Failure(SupportBundleFailure.DELETE_FAILED)
                }
                return@launch
            }
            _state.value = SupportBundleUiState.Ready(file)
        }
    }

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
        val file = (_state.value as? SupportBundleUiState.Ready)?.file ?: pendingFile
        if (file == null) {
            _state.value = SupportBundleUiState.Idle
        } else {
            deletePending()
        }
    }

    fun chooserOpened() {
        val ready = _state.value as? SupportBundleUiState.Ready ?: return
        _state.value = SupportBundleUiState.ChooserOpened(ready.file)
    }

    fun shareTargetChosen() {
        val opened = _state.value as? SupportBundleUiState.ChooserOpened ?: return
        _state.value = opened.copy(targetChosen = true)
    }

    fun dismissRetaining() {
        val file = when (val current = _state.value) {
            is SupportBundleUiState.ChooserOpened -> current.file
            is SupportBundleUiState.Failure -> pendingFile
            else -> null
        } ?: return
        if (file.isFile) {
            _state.value = SupportBundleUiState.PendingRetained(file)
        }
    }

    fun retryShare() {
        val file = when (val current = _state.value) {
            is SupportBundleUiState.ChooserOpened -> current.file
            is SupportBundleUiState.PendingRetained -> current.file
            is SupportBundleUiState.Failure -> pendingFile
            else -> null
        }
        if (file?.isFile == true) {
            _state.value = SupportBundleUiState.Ready(file)
        }
    }

    fun deletePending() {
        generationJob?.cancel()
        generationJob = null
        val file = pendingFile ?: when (val current = _state.value) {
            is SupportBundleUiState.Ready -> current.file
            is SupportBundleUiState.ChooserOpened -> current.file
            is SupportBundleUiState.PendingRetained -> current.file
            else -> null
        }
        viewModelScope.launch(backgroundContext) {
            val deleted = tempFiles.cancel(file)
            if (!deleted) {
                _state.value = SupportBundleUiState.Failure(SupportBundleFailure.DELETE_FAILED)
                return@launch
            }
            val next = tempFiles.pendingFiles().firstOrNull()
            pendingFile = next
            _state.value = if (next == null) {
                SupportBundleUiState.Idle
            } else {
                SupportBundleUiState.PendingRetained(next)
            }
        }
    }

    fun shareFailed() {
        if (_state.value is SupportBundleUiState.Ready) {
            _state.value = SupportBundleUiState.Failure(SupportBundleFailure.SHARE_PICKER_UNAVAILABLE)
        }
    }
}

private fun Throwable.rethrowIfCancellationOrFatal() {
    when (this) {
        is kotlinx.coroutines.CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        is LinkageError,
        -> throw this
    }
}
