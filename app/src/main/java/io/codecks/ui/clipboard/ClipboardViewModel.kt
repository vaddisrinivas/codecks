package io.codecks.ui.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.HiltViewModel
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

private const val MAX_SYNC_INTERVAL_MINUTES = 240
private const val MIN_SYNC_INTERVAL_MINUTES = 1
private const val MAX_AUTO_RETRY_COUNT = 7
private const val MIN_AUTO_RETRY_DELAY_MS = 3_000L
private const val MAX_AUTO_RETRY_DELAY_MS = 120_000L
private const val MAX_FAILURE_CLASS = "runtime.unknown"

data class ClipboardReceiptState(
    val direction: String,
    val target: String,
    val startedAtMillis: Long,
    val retry: Int,
    val durationMillis: Long,
    val failureClass: String?,
    val sensitive: Boolean,
    val message: String,
)

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
    val phoneRisk: String? = null,
    val macRisk: String? = null,
    val lastSafetyWarning: String? = null,
    val liveSyncVisible: Boolean = false,
    val syncFailureCount: Int = 0,
    val nextSyncDelaySeconds: Long = 0L,
    val lastFailureClass: String? = null,
    val lastManualReceipt: ClipboardReceiptState? = null,
)

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: ClipboardSettingsRepository,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
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
                val previous = _uiState.value
                _uiState.update {
                    it.copy(
                        connectionReady = config.isReady,
                        isRemoteOffline = !config.isReady && previous.mode != ClipboardSyncMode.Off,
                    )
                }
            }
        }
    }

    fun setPhoneText(value: String) {
        observePhoneText(value)
    }

    fun setMode(mode: ClipboardSyncMode) {
        applySyncSettings(mode, _uiState.value.syncIntervalMinutes)
        viewModelScope.launch {
            settingsRepository.saveMode(mode)
        }
    }

    fun setLiveSyncSessionActive(active: Boolean) {
        _uiState.update {
            val becameInactive = it.liveSyncVisible && !active
            it.copy(
                liveSyncVisible = active,
                status = if (becameInactive && it.mode != ClipboardSyncMode.Off) {
                    "Visible sync paused"
                } else {
                    it.status
                },
            )
        }
        restartSyncLoop()
    }

    private fun applySyncSettings(mode: ClipboardSyncMode, intervalMinutes: Int, status: String? = null) {
        val interval = intervalMinutes.coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES)
        _uiState.update {
            it.copy(
                mode = mode,
                syncIntervalMinutes = interval,
                syncFailureCount = if (mode == ClipboardSyncMode.Off) 0 else it.syncFailureCount,
                isRemoteOffline = !it.connectionReady && mode != ClipboardSyncMode.Off,
                status = status ?: it.status,
            )
        }
        if (status == null && mode == ClipboardSyncMode.Off) {
            _uiState.update {
                it.copy(
                    status = "Clipboard idle",
                    lastFailureClass = null,
                    lastManualReceipt = null,
                )
            }
        }
        restartSyncLoop()
    }

    private fun restartSyncLoop() {
        syncJob?.cancel()
        if (_uiState.value.mode != ClipboardSyncMode.Off && _uiState.value.liveSyncVisible) {
            syncJob = viewModelScope.launch {
                while (isActive) {
                    syncOnce()
                    val delayMillis = nextSyncDelayMillis()
                    _uiState.update { it.copy(nextSyncDelaySeconds = delayMillis / 1000L) }
                    delay(delayMillis)
                }
            }
        } else {
            _uiState.update { it.copy(nextSyncDelaySeconds = 0L) }
        }
    }

    fun setSyncIntervalMinutes(value: Int) {
        val interval = value.coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES)
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
        observePhoneText(text)
    }

    fun pullFromMac() {
        runManual(
            direction = "Mac -> Phone",
            target = "phone",
            operation = "Get from Mac",
            text = null,
            retry = 0,
        ) {
            pullFromMacSync()
        }
    }

    fun pushToMac() {
        sendPhoneTextToMac(_uiState.value.phoneText)
    }

    fun sendSharedTextToMac(text: String) {
        sendPhoneTextToMac(text, operation = "Share to Mac")
    }

    private fun sendPhoneTextToMac(text: String, operation: String = "Send to Mac") {
        runManual(
            direction = "Phone -> Mac",
            target = "mac",
            operation = operation,
            text = text,
            retry = 0,
        ) {
            syncEngine.markApplied(ClipboardSyncAction.WriteToMac(ClipboardHash.of(text)))
            connectionRepository.writeMacClipboard(text)
                .onSuccess {
                    val observation = syncEngine.observe(ClipboardEndpoint.Mac, text, macSource, nowMillis())
                    _uiState.update {
                        it.copy(
                            macText = text,
                            macHash = observation.revision.hash.shortHash(),
                            isRemoteOffline = false,
                            macPreview = ClipboardContentGuard.safePreview(text),
                            macRisk = ClipboardContentGuard.riskFor(text)?.label,
                            lastSafetyWarning = null,
                        ).withSnapshot(observation.snapshot)
                    }
                }
        }
    }

    private suspend fun syncOnce() {
        val state = _uiState.value
        if (state.mode == ClipboardSyncMode.Off || state.isRunning || !state.liveSyncVisible) return
        refreshPhone()
        if (!state.connectionReady) {
            noteAutoFailure(RuntimeException("Connect your Mac first"), operation = "Auto sync")
            return
        }

        if (state.mode == ClipboardSyncMode.MacToPhone || state.mode == ClipboardSyncMode.Bidirectional) {
            val macResult = observeMacClipboard()
            if (macResult.isFailure) {
                noteAutoFailure(macResult.exceptionOrNull(), operation = "Reading Mac")
                return
            }
        }

        when (val action = syncEngine.decide(_uiState.value.mode, nowMillis())) {
            ClipboardSyncAction.None -> {
                noteAutoSuccess()
                updateSnapshot()
            }
            is ClipboardSyncAction.Conflict -> {
                _uiState.update { it.copy(hasConflict = true, status = "Conflict") }
                updateSnapshot()
            }
            is ClipboardSyncAction.WriteToMac -> {
                val risk = ClipboardContentGuard.riskFor(_uiState.value.phoneText)
                if (risk != null) {
                    _uiState.update {
                        it.copy(
                            hasConflict = false,
                            status = "Auto sync skipped",
                            lastSafetyWarning = "${risk.label}: ${risk.reason}",
                        )
                    }
                    noteAutoSuccess()
                    return
                }
                val result = pushToMacSync(_uiState.value.phoneText)
                if (result.isSuccess) {
                    noteAutoSuccess()
                } else {
                    noteAutoFailure(result.exceptionOrNull(), operation = "Auto sync")
                }
            }
            is ClipboardSyncAction.WriteToPhone -> {
                val risk = ClipboardContentGuard.riskFor(_uiState.value.macText)
                if (risk != null) {
                    _uiState.update {
                        it.copy(
                            hasConflict = false,
                            status = "Auto sync skipped",
                            lastSafetyWarning = "${risk.label}: ${risk.reason}",
                        )
                    }
                    noteAutoSuccess()
                    return
                }
                val result = writePhoneClipboard(_uiState.value.macText, status = "Synced from Mac")
                if (result.isSuccess) {
                    noteAutoSuccess()
                } else {
                    noteAutoFailure(result.exceptionOrNull(), operation = "Auto sync")
                }
            }
        }
    }

    private suspend fun pullFromMacSync(): Result<String> =
        observeMacClipboard().onSuccess { value ->
            syncEngine.markApplied(ClipboardSyncAction.WriteToPhone(ClipboardHash.of(value)))
            writePhoneClipboard(value, status = "Mac to phone")
        }

    private suspend fun pushToMacSync(text: String): Result<String> {
        return connectionRepository.writeMacClipboard(text).onSuccess {
            val observation = syncEngine.observe(ClipboardEndpoint.Mac, text, macSource, nowMillis())
            _uiState.update {
                it.copy(
                    macText = text,
                    macHash = observation.revision.hash.shortHash(),
                    isRemoteOffline = false,
                    macPreview = ClipboardContentGuard.safePreview(text),
                    macRisk = ClipboardContentGuard.riskFor(text)?.label,
                    lastSafetyWarning = null,
                    hasConflict = false,
                ).withSnapshot(observation.snapshot)
            }
        }
    }

    private suspend fun observeMacClipboard(): Result<String> = runResult { connectionRepository.runCommand("pbpaste") }
        .onSuccess { value ->
            val observation = syncEngine.observe(ClipboardEndpoint.Mac, value, macSource, nowMillis())
            val risk = ClipboardContentGuard.riskFor(value)
            _uiState.update {
                it.copy(
                    macText = value,
                    macHash = observation.revision.hash.shortHash(),
                    isRemoteOffline = false,
                    macPreview = ClipboardContentGuard.safePreview(value),
                    macRisk = risk?.label,
                ).withSnapshot(observation.snapshot)
            }
        }

    private suspend fun writePhoneClipboard(text: String, status: String = "Synced from Mac"): Result<String> {
        return runResult { clipboardManager.setPrimaryClip(ClipData.newPlainText("Codecks", text)); "ok" }
            .onSuccess {
                val observation = syncEngine.observe(ClipboardEndpoint.Phone, text, phoneSource, nowMillis())
                val risk = ClipboardContentGuard.riskFor(text)
                _uiState.update {
                    it.copy(
                        phoneText = text,
                        phoneHash = observation.revision.hash.shortHash(),
                        phonePreview = ClipboardContentGuard.safePreview(text),
                        phoneRisk = risk?.label,
                        lastSafetyWarning = null,
                        status = status,
                    ).withSnapshot(observation.snapshot)
                }
            }
    }

    private fun runManual(
        direction: String,
        target: String,
        operation: String,
        text: String?,
        retry: Int,
        block: suspend () -> Result<String>,
    ) {
        if (_uiState.value.isRunning) return
        if (text != null && text.isBlank()) {
            _uiState.update {
                it.copy(
                    isRunning = false,
                    status = "Action skipped",
                    lastManualReceipt = ClipboardReceiptState(
                        direction = direction,
                        target = target,
                        startedAtMillis = nowMillis(),
                        retry = retry,
                        durationMillis = 0L,
                        failureClass = classifyFailure(IllegalArgumentException("Clipboard text is empty")),
                        sensitive = false,
                        message = "Clipboard text is empty",
                    ),
                )
            }
            return
        }

        val startedAt = nowMillis()
        _uiState.update {
            it.copy(
                isRunning = true,
                status = operation,
            )
        }
        viewModelScope.launch {
            val result = runResult(block)
            val duration = nowMillis() - startedAt
            val receiptText = text ?: _uiState.value.phoneText
            val sensitive = ClipboardContentGuard.riskFor(receiptText) != null
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isRemoteOffline = false,
                        syncFailureCount = 0,
                        lastFailureClass = null,
                        nextSyncDelaySeconds = (it.syncIntervalMinutes * 60L),
                        lastManualReceipt = ClipboardReceiptState(
                            direction = direction,
                            target = target,
                            startedAtMillis = startedAt,
                            retry = retry,
                            durationMillis = duration,
                            failureClass = null,
                            sensitive = sensitive,
                            message = operation,
                        ),
                    )
                }
            } else {
                val failureClass = classifyFailure(result.exceptionOrNull())
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isRemoteOffline = failureClass.startsWith("connectivity."),
                        syncFailureCount = if (it.mode != ClipboardSyncMode.Off) it.syncFailureCount else 0,
                        lastFailureClass = failureClass,
                        lastManualReceipt = ClipboardReceiptState(
                            direction = direction,
                            target = target,
                            startedAtMillis = startedAt,
                            retry = retry,
                            durationMillis = duration,
                            failureClass = failureClass,
                            sensitive = sensitive,
                            message = result.exceptionOrNull()?.message.orEmpty(),
                        ),
                    )
                }
            }
            updateSnapshot()
            refreshPhone()
        }
    }

    private suspend fun runResult(block: suspend () -> Result<String>): Result<String> = try {
        block()
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun noteAutoFailure(error: Throwable?, operation: String) {
        val failureClass = classifyFailure(error)
        val delayMillis = retryDelayMillis(_uiState.value.syncFailureCount + 1)
        _uiState.update {
            it.copy(
                isRemoteOffline = failureClass.startsWith("connectivity."),
                syncFailureCount = (it.syncFailureCount + 1).coerceAtMost(MAX_AUTO_RETRY_COUNT),
                lastFailureClass = failureClass,
                status = operation,
                nextSyncDelaySeconds = delayMillis / 1000L,
            )
        }
    }

    private fun noteAutoSuccess() {
        _uiState.update {
            it.copy(
                syncFailureCount = 0,
                lastFailureClass = null,
                nextSyncDelaySeconds = it.syncIntervalMinutes * 60L,
                isRemoteOffline = false,
            )
        }
    }

    private fun nextSyncDelayMillis(): Long {
        val failures = _uiState.value.syncFailureCount
        return if (failures <= 0) {
            _uiState.value.syncIntervalMinutes * 60_000L
        } else {
            retryDelayMillis(failures)
        }
    }

    private fun retryDelayMillis(failureTier: Int): Long =
        when (failureTier) {
            1 -> MIN_AUTO_RETRY_DELAY_MS
            2 -> 8_000L
            3 -> 15_000L
            4 -> 30_000L
            5 -> 60_000L
            else -> MAX_AUTO_RETRY_DELAY_MS
        }.let { minOf(it, MAX_AUTO_RETRY_DELAY_MS) }

    private fun classifyFailure(error: Throwable?): String = when (val message = (error?.message.orEmpty())) {
        in "" -> MAX_FAILURE_CLASS
        else -> when {
            message.contains("Connect your Mac first") || message.contains("Mac offline") -> "connectivity.missing_connection"
            message.contains("Verify the Mac fingerprint first") -> "connectivity.missing_fingerprint"
            message.contains("Generate or install the SSH key first") -> "connectivity.missing_ssh_key"
            message.contains("Command timed out", ignoreCase = true) -> "runtime.timeout"
            message.contains("Command is empty") -> "input.empty"
            message.contains("Clipboard text is empty") -> "input.empty"
            else -> MAX_FAILURE_CLASS
        }
    }

    private fun observePhoneText(value: String) {
        val observation = syncEngine.observe(ClipboardEndpoint.Phone, value, phoneSource, nowMillis())
        val risk = ClipboardContentGuard.riskFor(value)
        _uiState.update {
            it.copy(
                phoneText = value,
                phoneHash = observation.revision.hash.shortHash(),
                phoneRisk = risk?.label,
                phonePreview = ClipboardContentGuard.safePreview(value),
            ).withSnapshot(observation.snapshot)
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
                status = if (snapshot.staleEndpoints.isEmpty() && !it.hasConflict) it.status else "Stale",
            )
        }
    }

    private fun ClipboardUiState.withSnapshot(snapshot: ClipboardSyncSnapshot): ClipboardUiState = copy(
        latestRevision = snapshot.latestRevision,
        history = snapshot.history,
        hasConflict = snapshot.conflict != null,
        staleEndpoints = snapshot.staleEndpoints,
    )

    private fun String.shortHash(): String = take(12)
}
