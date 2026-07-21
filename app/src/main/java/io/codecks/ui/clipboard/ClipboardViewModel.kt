package io.codecks.ui.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.data.ConnectionRepository
import io.codecks.data.clipboard.ClipboardSettingsRepository
import io.codecks.domain.clipboard.ClipboardEndpoint
import io.codecks.domain.clipboard.ClipboardHash
import io.codecks.domain.clipboard.ClipboardContentGuard
import io.codecks.domain.clipboard.ClipboardRevision
import io.codecks.domain.clipboard.ClipboardSourceId
import io.codecks.domain.clipboard.ClipboardSyncAction
import io.codecks.domain.clipboard.ClipboardSyncEngine
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.clipboard.ClipboardSyncSnapshot
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ClipboardUiState(
    val phoneText: String = "",
    val macText: String = "",
    val mode: ClipboardSyncMode = ClipboardSyncMode.Off,
    val status: String = "Clipboard idle",
    val isRunning: Boolean = false,
    val connectionReady: Boolean = false,
    val latestRevision: Long = 0L,
    val phoneHash: String = "",
    val macHash: String = "",
    val syncIntervalMinutes: Int = 5,
    val history: List<ClipboardRevision> = emptyList(),
    val hasConflict: Boolean = false,
    val isRemoteOffline: Boolean = false,
    val staleEndpoints: Set<ClipboardEndpoint> = emptySet(),
    val phonePreview: String = "Empty",
    val macPreview: String = "Empty",
    val lastSafetyWarning: String? = null,
)

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: ClipboardSettingsRepository,
) : ViewModel() {
    private val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    private val _uiState = MutableStateFlow(ClipboardUiState())
    val uiState: StateFlow<ClipboardUiState> = _uiState.asStateFlow()
    private val syncEngine = ClipboardSyncEngine()
    private val phoneSource = ClipboardSourceId("android-clipboard")
    private val macSource = ClipboardSourceId("mac-pbpaste")
    private var syncJob: Job? = null

    init {
        refreshPhone()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                applySyncSettings(
                    mode = settings.mode,
                    intervalMinutes = settings.intervalMinutes,
                    status = null,
                )
            }
        }
        viewModelScope.launch {
            connectionRepository.config.collect { config ->
                _uiState.update {
                    it.copy(
                        connectionReady = config.isReady,
                        isRemoteOffline = !config.isReady && it.mode != ClipboardSyncMode.Off,
                    )
                }
            }
        }
    }

    fun setPhoneText(value: String) {
        val observation = syncEngine.observe(ClipboardEndpoint.Phone, value, phoneSource, nowMillis())
        _uiState.update {
            it.copy(
                phoneText = value,
                phoneHash = observation.revision.hash.shortHash(),
                phonePreview = ClipboardContentGuard.safePreview(value),
                lastSafetyWarning = null,
                status = "Edited on phone",
            ).withSnapshot(observation.snapshot)
        }
    }

    fun setMode(mode: ClipboardSyncMode) {
        applySyncSettings(mode, _uiState.value.syncIntervalMinutes)
        viewModelScope.launch {
            settingsRepository.saveMode(mode)
        }
    }

    private fun applySyncSettings(
        mode: ClipboardSyncMode,
        intervalMinutes: Int,
        status: String? = if (mode == ClipboardSyncMode.Off) "Clipboard idle" else null,
    ) {
        val interval = intervalMinutes.coerceIn(1, 240)
        _uiState.update {
            it.copy(
                mode = mode,
                syncIntervalMinutes = interval,
                isRemoteOffline = !it.connectionReady && mode != ClipboardSyncMode.Off,
                status = status ?: it.status,
            )
        }
        restartSyncLoop()
    }

    private fun restartSyncLoop() {
        syncJob?.cancel()
        if (_uiState.value.mode != ClipboardSyncMode.Off) {
            syncJob = viewModelScope.launch {
                while (isActive) {
                    syncOnce()
                    delay(_uiState.value.syncIntervalMinutes.coerceIn(1, 240) * 60_000L)
                }
            }
        }
    }

    fun setSyncIntervalMinutes(value: Int) {
        val interval = value.coerceIn(1, 240)
        applySyncSettings(_uiState.value.mode, interval, status = "Sync every $interval min")
        viewModelScope.launch {
            settingsRepository.saveIntervalMinutes(interval)
        }
    }

    fun refreshPhone() {
        val text = clipboardManager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        val observation = syncEngine.observe(ClipboardEndpoint.Phone, text, phoneSource, nowMillis())
        _uiState.update {
            it.copy(
                phoneText = text,
                phoneHash = observation.revision.hash.shortHash(),
                phonePreview = ClipboardContentGuard.safePreview(text),
            )
                .withSnapshot(observation.snapshot)
        }
    }

    fun pullFromMac() {
        runClipboard("Reading Mac") { pullFromMacSync() }
    }

    fun pushToMac() {
        refreshPhone()
        val text = _uiState.value.phoneText
        runClipboard("Sending to Mac") { pushToMacSync(text) }
    }

    private suspend fun syncOnce() {
        refreshPhone()
        val state = _uiState.value
        if (state.mode == ClipboardSyncMode.Off || state.isRunning) return
        if (!state.connectionReady) {
            _uiState.update { it.copy(isRemoteOffline = true, status = "Mac offline") }
            return
        }
        if (state.mode == ClipboardSyncMode.MacToPhone || state.mode == ClipboardSyncMode.Bidirectional) {
            observeMacClipboard().onFailure { error ->
                _uiState.update { it.copy(isRemoteOffline = true, status = error.message ?: "Mac offline") }
                return
            }
        }

        when (val action = syncEngine.decide(_uiState.value.mode, nowMillis())) {
            ClipboardSyncAction.None -> updateSnapshot()
            is ClipboardSyncAction.Conflict -> {
                _uiState.update { it.copy(hasConflict = true, status = "Conflict") }
            }
            is ClipboardSyncAction.WriteToMac -> {
                ClipboardContentGuard.riskFor(_uiState.value.phoneText)?.let { risk ->
                    _uiState.update {
                        it.copy(
                            status = "Auto sync skipped",
                            lastSafetyWarning = "${risk.label}: ${risk.reason}",
                        )
                    }
                    return
                }
                syncEngine.markApplied(action)
                pushToMacSync(_uiState.value.phoneText)
            }
            is ClipboardSyncAction.WriteToPhone -> {
                ClipboardContentGuard.riskFor(_uiState.value.macText)?.let { risk ->
                    _uiState.update {
                        it.copy(
                            status = "Auto sync skipped",
                            lastSafetyWarning = "${risk.label}: ${risk.reason}",
                        )
                    }
                    return
                }
                syncEngine.markApplied(action)
                writePhoneClipboard(_uiState.value.macText)
            }
        }
    }

    private suspend fun pullFromMacSync(): Result<String> =
        observeMacClipboard().onSuccess { value ->
            syncEngine.markApplied(ClipboardSyncAction.WriteToPhone(ClipboardHash.of(value)))
            writePhoneClipboard(value, status = "Mac to phone")
        }

    private suspend fun pushToMacSync(text: String): Result<String> {
        syncEngine.markApplied(ClipboardSyncAction.WriteToMac(ClipboardHash.of(text)))
        return connectionRepository.writeMacClipboard(text)
            .onSuccess {
                val observation = syncEngine.observe(ClipboardEndpoint.Mac, text, macSource, nowMillis())
                _uiState.update {
                    it.copy(
                        macText = text,
                        macHash = observation.revision.hash.shortHash(),
                        isRemoteOffline = false,
                        macPreview = ClipboardContentGuard.safePreview(text),
                        lastSafetyWarning = null,
                        status = "Phone to Mac",
                    ).withSnapshot(observation.snapshot)
                }
            }
    }

    private suspend fun observeMacClipboard(): Result<String> =
        connectionRepository.runCommand("pbpaste").onSuccess { value ->
            val observation = syncEngine.observe(ClipboardEndpoint.Mac, value, macSource, nowMillis())
            _uiState.update {
                it.copy(
                    macText = value,
                    macHash = observation.revision.hash.shortHash(),
                    isRemoteOffline = false,
                    macPreview = ClipboardContentGuard.safePreview(value),
                ).withSnapshot(observation.snapshot)
            }
        }

    private fun writePhoneClipboard(text: String, status: String = "Synced from Mac") {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Codecks", text))
        val observation = syncEngine.observe(ClipboardEndpoint.Phone, text, phoneSource, nowMillis())
        _uiState.update {
            it.copy(
                phoneText = text,
                phoneHash = observation.revision.hash.shortHash(),
                phonePreview = ClipboardContentGuard.safePreview(text),
                lastSafetyWarning = null,
                status = status,
            ).withSnapshot(observation.snapshot)
        }
    }

    private fun runClipboard(label: String, block: suspend () -> Result<String>) {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, status = label) }
            val result = block()
            result.onFailure { error ->
                _uiState.update { it.copy(isRemoteOffline = true, status = error.message ?: "Clipboard sync failed") }
            }
            _uiState.update { it.copy(isRunning = false) }
        }
    }

    private fun updateSnapshot() {
        val snapshot = syncEngine.snapshot(nowMillis())
        _uiState.update {
            it.copy(
                staleEndpoints = snapshot.staleEndpoints,
                history = snapshot.history,
                hasConflict = snapshot.conflict != null,
                latestRevision = snapshot.latestRevision,
                status = if (snapshot.staleEndpoints.isEmpty()) it.status else "Stale",
            )
        }
    }

    private fun ClipboardUiState.withSnapshot(snapshot: ClipboardSyncSnapshot): ClipboardUiState =
        copy(
            latestRevision = snapshot.latestRevision,
            history = snapshot.history,
            hasConflict = snapshot.conflict != null,
            staleEndpoints = snapshot.staleEndpoints,
        )

    private fun String.shortHash(): String = take(12)
    private fun nowMillis(): Long = System.currentTimeMillis()
}
