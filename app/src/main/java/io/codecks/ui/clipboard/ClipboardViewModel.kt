package io.codecks.ui.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PersistableBundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.data.ConnectionRepository
import io.codecks.data.clipboard.ClipboardSettingsRepository
import io.codecks.data.clipboard.ClipboardLastSyncStore
import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.privacy.recordTerminal
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticResultCode
import io.codecks.domain.clipboard.ClipboardDirection
import io.codecks.domain.clipboard.ClipboardBatteryPolicy
import io.codecks.domain.clipboard.ClipboardFailureCode
import io.codecks.domain.clipboard.ClipboardEndpoint
import io.codecks.domain.clipboard.ClipboardHash
import io.codecks.domain.clipboard.ClipboardContentGuard
import io.codecks.domain.clipboard.ClipboardRevision
import io.codecks.domain.clipboard.ClipboardOperation
import io.codecks.domain.clipboard.ClipboardReceipt
import io.codecks.domain.clipboard.ClipboardSourceId
import io.codecks.domain.clipboard.ClipboardSessionPhase
import io.codecks.domain.clipboard.ClipboardSessionState
import io.codecks.domain.clipboard.ClipboardSyncAction
import io.codecks.domain.clipboard.ClipboardSyncEngine
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.domain.clipboard.ClipboardSyncSnapshot
import io.codecks.domain.clipboard.ClipboardTerminalResult
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

private const val MAX_SYNC_INTERVAL_MINUTES = 240
private const val MIN_SYNC_INTERVAL_MINUTES = 1
private const val MAX_FAILURE_CLASS = "runtime.unknown"

data class ClipboardUiState(
    val phoneText: String = "",
    val macText: String = "",
    val mode: ClipboardSyncMode = ClipboardSyncMode.Off,
    val status: String = "Clipboard idle",
    val isRunning: Boolean = false,
    val connectionReady: Boolean = false,
    val connectionConfigured: Boolean = false,
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
    val lastSyncReceipt: ClipboardReceipt? = null,
    val session: ClipboardSessionState = ClipboardSessionState(),
    val batterySaverActive: Boolean = false,
    val pendingSharedText: Boolean = false,
)

internal data class PendingClipboardVerification(
    val direction: ClipboardDirection,
    val expectedHash: String,
)

internal fun PendingClipboardVerification.matches(phoneText: String?, macText: String?): Boolean =
    when (direction) {
        ClipboardDirection.PhoneToMac -> macText?.let(ClipboardHash::of) == expectedHash
        ClipboardDirection.MacToPhone -> phoneText?.let(ClipboardHash::of) == expectedHash
        ClipboardDirection.Bidirectional -> false
    }

internal fun sharedTextTerminalConsumes(result: ClipboardTerminalResult): Boolean =
    result == ClipboardTerminalResult.VerifiedSuccess ||
        result == ClipboardTerminalResult.AppliedUnverified

internal fun clipboardSessionExpiryDelayMillis(
    expiresAtElapsedRealtimeMillis: Long,
    elapsedRealtimeMillis: Long,
): Long = (expiresAtElapsedRealtimeMillis - elapsedRealtimeMillis).coerceAtLeast(0L)

internal fun clipboardAutomaticPollingEligible(
    mode: ClipboardSyncMode,
    connectionReady: Boolean,
    phase: ClipboardSessionPhase,
    batterySaverActive: Boolean,
): Boolean =
    mode != ClipboardSyncMode.Off &&
        connectionReady &&
        ClipboardBatteryPolicy.automaticPollingAllowed(phase, batterySaverActive)

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: ClipboardSettingsRepository,
) : ViewModel() {
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() }
    private val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val lastSyncStore = ClipboardLastSyncStore(context)
    private val diagnosticEventStore = DiagnosticEventStore(context)
    private val _uiState = MutableStateFlow(ClipboardUiState())
    val uiState: StateFlow<ClipboardUiState> = _uiState.asStateFlow()
    private val syncEngine = ClipboardSyncEngine()
    private val phoneSource = ClipboardSourceId("android-clipboard")
    private val macSource = ClipboardSourceId("mac-pbpaste")
    private var syncJob: Job? = null
    private var sessionExpiryJob: Job? = null
    private var pendingVerification: PendingClipboardVerification? = null
    private var pendingSharedTextValue: String? = null
    private val syncMutex = Mutex()
    private val syncConfigurationGeneration = AtomicLong()
    private var terminalProofReady = false
    private var currentConnectionConfigured = false
    private val batterySaverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                updateBatterySaverState()
                restartSyncLoop()
            }
        }
    }

    init {
        _uiState.update {
            it.copy(
                lastSyncReceipt = lastSyncStore.read(),
                batterySaverActive = isBatterySaverActive(),
            )
        }
        registerBatterySaverReceiver()
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
                currentConnectionConfigured = config.isConfigured
                val verifiedReady = config.isReady && terminalProofReady
                val previous = _uiState.value
                if (
                    previous.connectionReady != verifiedReady ||
                    previous.connectionConfigured != config.isConfigured
                ) {
                    syncConfigurationGeneration.incrementAndGet()
                }
                _uiState.update {
                    it.copy(
                        connectionReady = verifiedReady,
                        connectionConfigured = config.isConfigured,
                        isRemoteOffline = !verifiedReady && previous.mode != ClipboardSyncMode.Off,
                    )
                }
            }
        }
    }

    fun setTerminalProofReady(ready: Boolean) {
        if (terminalProofReady == ready) return
        terminalProofReady = ready
        syncConfigurationGeneration.incrementAndGet()
        _uiState.update {
            it.copy(
                connectionReady = currentConnectionConfigured && ready,
                connectionConfigured = currentConnectionConfigured,
                isRemoteOffline = !ready && it.mode != ClipboardSyncMode.Off,
            )
        }
        restartSyncLoop()
    }

    fun setPhoneText(value: String) {
        observePhoneText(value)
    }

    fun setMode(mode: ClipboardSyncMode) {
        if (_uiState.value.hasConflict && mode == _uiState.value.mode) {
            cancelConflict()
            return
        }
        applySyncSettings(mode, _uiState.value.syncIntervalMinutes)
        viewModelScope.launch {
            settingsRepository.saveMode(mode)
        }
    }

    private fun cancelConflict() {
        val operation = ClipboardOperation(ClipboardDirection.Bidirectional, nowMillis())
        if (syncEngine.cancelConflict()) {
            recordTerminal(operation, ClipboardTerminalResult.Cancellation)
            _uiState.update {
                it.copy(
                    hasConflict = false,
                    status = "Conflict cancelled",
                )
            }
        }
    }

    fun setLiveSyncSessionActive(active: Boolean) {
        syncConfigurationGeneration.incrementAndGet()
        updateSessionEnvironment(surfaceVisible = active)
    }

    fun setAppForegroundVisible(visible: Boolean) {
        syncConfigurationGeneration.incrementAndGet()
        updateSessionEnvironment(appForeground = visible)
    }

    fun startClipboardSession() {
        val now = nowMillis()
        _uiState.update {
            val session = it.session
                .withEnvironment(
                    deviceUnlocked = isDeviceUnlocked(),
                    nowMillis = now,
                    elapsedRealtimeMillis = elapsedRealtimeMillis(),
                )
                .start(now, elapsedRealtimeMillis = elapsedRealtimeMillis())
            it.copy(
                session = session,
                liveSyncVisible = session.canReadPhoneClipboard,
                status = sessionStatus(session.phase),
            )
        }
        syncConfigurationGeneration.incrementAndGet()
        scheduleSessionExpiry()
        restartSyncLoop()
        refreshPhone()
    }

    fun stopClipboardSession() {
        sessionExpiryJob?.cancel()
        sessionExpiryJob = null
        syncConfigurationGeneration.incrementAndGet()
        _uiState.update {
            it.copy(
                session = it.session.stop(),
                liveSyncVisible = false,
                status = "Clipboard session stopped",
            )
        }
        restartSyncLoop()
    }

    private fun updateSessionEnvironment(
        appForeground: Boolean? = null,
        surfaceVisible: Boolean? = null,
    ) {
        val now = nowMillis()
        _uiState.update {
            val session = it.session.withEnvironment(
                appForeground = appForeground ?: it.session.appForeground,
                surfaceVisible = surfaceVisible ?: it.session.surfaceVisible,
                deviceUnlocked = isDeviceUnlocked(),
                nowMillis = now,
                elapsedRealtimeMillis = elapsedRealtimeMillis(),
            )
            it.copy(
                session = session,
                liveSyncVisible = session.canReadPhoneClipboard,
                status = if (
                    it.session.phase == ClipboardSessionPhase.ActiveVisible &&
                    session.phase != ClipboardSessionPhase.ActiveVisible
                ) {
                    sessionStatus(session.phase)
                } else {
                    it.status
                },
            )
        }
        restartSyncLoop()
    }

    private fun applySyncSettings(mode: ClipboardSyncMode, intervalMinutes: Int, status: String? = null) {
        val interval = intervalMinutes.coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES)
        syncConfigurationGeneration.incrementAndGet()
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
                )
            }
        }
        restartSyncLoop()
    }

    private fun restartSyncLoop() {
        syncJob?.cancel()
        updateBatterySaverState()
        if (clipboardAutomaticPollingEligible(
                mode = _uiState.value.mode,
                connectionReady = _uiState.value.connectionReady,
                phase = _uiState.value.session.phase,
                batterySaverActive = _uiState.value.batterySaverActive,
            )
        ) {
            syncJob = viewModelScope.launch {
                while (isActive) {
                    syncOnce()
                    if (!automaticPollingAllowed()) return@launch
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
        if (!refreshSessionAccess()) return
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
            direction = ClipboardDirection.MacToPhone,
            operation = "Get from Mac",
            text = null,
            verify = { value -> readPhoneClipboardForVerification() == value },
        ) {
            pullFromMacSync()
        }
    }

    fun pushToMac() {
        sendPhoneTextToMac(_uiState.value.phoneText)
    }

    fun acceptSharedText(text: String, onConsumed: () -> Unit) {
        pendingSharedTextValue = text
        observePhoneText(text)
        _uiState.update { it.copy(pendingSharedText = true) }
        sendSharedTextToMac(text, onConsumed)
    }

    fun retrySharedText(onConsumed: () -> Unit) {
        pendingSharedTextValue?.let { sendSharedTextToMac(it, onConsumed) }
    }

    fun discardSharedText(onConsumed: () -> Unit) {
        pendingSharedTextValue = null
        _uiState.update { it.copy(pendingSharedText = false) }
        onConsumed()
    }

    private fun sendSharedTextToMac(text: String, onConsumed: () -> Unit) {
        sendPhoneTextToMac(
            text,
            operation = "Share to Mac",
            requiresClipboardSession = false,
            onTerminal = { result ->
                if (sharedTextTerminalConsumes(result)) {
                    pendingSharedTextValue = null
                    _uiState.update { it.copy(pendingSharedText = false) }
                    onConsumed()
                }
            },
        )
    }

    private fun sendPhoneTextToMac(
        text: String,
        operation: String = "Send to Mac",
        requiresClipboardSession: Boolean = true,
        onTerminal: (ClipboardTerminalResult) -> Unit = {},
    ) {
        runManual(
            direction = ClipboardDirection.PhoneToMac,
            operation = operation,
            text = text,
            verify = { connectionRepository.runCommand("pbpaste").getOrNull() == text },
            requiresClipboardSession = requiresClipboardSession,
            onTerminal = onTerminal,
        ) {
            syncEngine.markApplied(ClipboardSyncAction.WriteToMac(ClipboardHash.of(text)), nowMillis())
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

    private suspend fun syncOnce() = syncMutex.withLock {
        if (_uiState.value.mode == ClipboardSyncMode.Off || _uiState.value.isRunning || !automaticPollingAllowed()) {
            return@withLock
        }
        if (!refreshSessionAccess()) return@withLock
        refreshPhone()
        val state = _uiState.value
        val generation = syncConfigurationGeneration.get()
        if (!state.session.canReadPhoneClipboard || state.mode == ClipboardSyncMode.Off) return@withLock
        if (!state.connectionReady) {
            noteAutoFailure(RuntimeException("Connect your Mac first"), operation = "Auto sync")
            return@withLock
        }

        if (state.mode == ClipboardSyncMode.MacToPhone || state.mode == ClipboardSyncMode.Bidirectional) {
            val macResult = observeMacClipboard()
            if (macResult.isFailure) {
                noteAutoFailure(macResult.exceptionOrNull(), operation = "Reading Mac")
                return@withLock
            }
            if (!syncContextStillCurrent(generation, state.mode)) return@withLock
        }

        if (retryPendingVerification(generation, state.mode)) return@withLock
        when (val action = syncEngine.decide(state.mode, nowMillis())) {
            ClipboardSyncAction.None -> {
                noteAutoSuccess()
                updateSnapshot()
            }
            is ClipboardSyncAction.Conflict -> {
                _uiState.update { it.copy(hasConflict = true, status = "Conflict") }
                recordTerminal(
                    ClipboardOperation(ClipboardDirection.Bidirectional, nowMillis()),
                    ClipboardTerminalResult.Conflict,
                )
                updateSnapshot()
            }
            is ClipboardSyncAction.WriteToMac -> {
                val receiptOperation = ClipboardOperation(ClipboardDirection.PhoneToMac, nowMillis())
                val risk = ClipboardContentGuard.riskFor(_uiState.value.phoneText)
                if (risk != null) {
                    _uiState.update {
                        it.copy(
                            hasConflict = false,
                            status = "Auto sync skipped",
                            lastSafetyWarning = "${risk.label}: ${risk.reason}",
                        )
                    }
                    recordTerminal(
                        receiptOperation,
                        ClipboardTerminalResult.Blocked,
                    )
                    noteAutoBlocked()
                    return@withLock
                }
                if (!syncContextStillCurrent(generation, state.mode)) return@withLock
                syncEngine.markApplied(action, nowMillis())
                val result = pushToMacSync(_uiState.value.phoneText)
                if (result.isSuccess) {
                    val verified = connectionRepository.runCommand("pbpaste").getOrNull() == _uiState.value.phoneText
                    recordTerminal(
                        receiptOperation,
                        if (verified) {
                            ClipboardTerminalResult.VerifiedSuccess
                        } else {
                            ClipboardTerminalResult.AppliedUnverified
                        },
                    )
                    pendingVerification = if (verified) null else PendingClipboardVerification(
                        ClipboardDirection.PhoneToMac,
                        action.hash,
                    )
                    if (verified) noteAutoSuccess() else noteAutoUnverified()
                } else {
                    recordTerminal(
                        receiptOperation,
                        ClipboardTerminalResult.Failure,
                        failureCode(result.exceptionOrNull()),
                    )
                    noteAutoFailure(result.exceptionOrNull(), operation = "Auto sync")
                }
            }
            is ClipboardSyncAction.WriteToPhone -> {
                val receiptOperation = ClipboardOperation(ClipboardDirection.MacToPhone, nowMillis())
                val risk = ClipboardContentGuard.riskFor(_uiState.value.macText)
                if (risk != null) {
                    _uiState.update {
                        it.copy(
                            hasConflict = false,
                            status = "Auto sync skipped",
                            lastSafetyWarning = "${risk.label}: ${risk.reason}",
                        )
                    }
                    recordTerminal(
                        receiptOperation,
                        ClipboardTerminalResult.Blocked,
                    )
                    noteAutoBlocked()
                    return@withLock
                }
                if (!syncContextStillCurrent(generation, state.mode)) return@withLock
                syncEngine.markApplied(action, nowMillis())
                val result = writePhoneClipboard(_uiState.value.macText, status = "Synced from Mac")
                if (result.isSuccess) {
                    val verified = readPhoneClipboardForVerification() == _uiState.value.macText
                    recordTerminal(
                        receiptOperation,
                        if (verified) {
                            ClipboardTerminalResult.VerifiedSuccess
                        } else {
                            ClipboardTerminalResult.AppliedUnverified
                        },
                    )
                    pendingVerification = if (verified) null else PendingClipboardVerification(
                        ClipboardDirection.MacToPhone,
                        action.hash,
                    )
                    if (verified) noteAutoSuccess() else noteAutoUnverified()
                } else {
                    recordTerminal(
                        receiptOperation,
                        ClipboardTerminalResult.Failure,
                        failureCode(result.exceptionOrNull()),
                    )
                    noteAutoFailure(result.exceptionOrNull(), operation = "Auto sync")
                }
            }
        }
    }

    private suspend fun pullFromMacSync(): Result<String> {
        val observed = observeMacClipboard()
        if (observed.isFailure) return observed
        return try {
            val value = observed.getOrThrow()
            syncEngine.markApplied(ClipboardSyncAction.WriteToPhone(ClipboardHash.of(value)), nowMillis())
            writePhoneClipboard(value, status = "Mac to phone").getOrThrow()
            Result.success(value)
        } catch (error: Throwable) {
            error.rethrowIfCancellationOrFatal()
            Result.failure(error)
        }
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
        val risk = ClipboardContentGuard.riskFor(text)
        val clip = ClipData.newPlainText("Codecks", text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, risk != null)
            }
        }
        return runResult { clipboardManager.setPrimaryClip(clip); Result.success("ok") }
            .onSuccess {
                val observation = syncEngine.observe(ClipboardEndpoint.Phone, text, phoneSource, nowMillis())
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
        direction: ClipboardDirection,
        operation: String,
        text: String?,
        verify: suspend (String) -> Boolean,
        requiresClipboardSession: Boolean = true,
        onTerminal: (ClipboardTerminalResult) -> Unit = {},
        block: suspend () -> Result<String>,
    ) {
        if (_uiState.value.isRunning) return
        val startedAt = nowMillis()
        val receiptOperation = ClipboardOperation(direction, startedAt)
        if (requiresClipboardSession && !refreshSessionAccess()) {
            recordTerminal(receiptOperation, ClipboardTerminalResult.Blocked)
            onTerminal(ClipboardTerminalResult.Blocked)
            _uiState.update { it.copy(isRunning = false, status = sessionStatus(it.session.phase)) }
            return
        }
        if (text != null && text.isBlank()) {
            recordTerminal(
                receiptOperation,
                ClipboardTerminalResult.Failure,
                ClipboardFailureCode.EmptyInput,
            )
            onTerminal(ClipboardTerminalResult.Failure)
            _uiState.update {
                it.copy(
                    isRunning = false,
                    status = "Action skipped",
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isRunning = true,
                status = operation,
            )
        }
        viewModelScope.launch {
            syncMutex.withLock {
                val result = try {
                    block()
                } catch (cancelled: CancellationException) {
                    recordTerminal(receiptOperation, ClipboardTerminalResult.Cancellation)
                    onTerminal(ClipboardTerminalResult.Cancellation)
                    _uiState.update { it.copy(isRunning = false, status = "Clipboard action cancelled") }
                    throw cancelled
                } catch (error: Throwable) {
                    error.rethrowIfCancellationOrFatal()
                    Result.failure(error)
                }
                if (result.isSuccess) {
                    val verified = try {
                        verify(result.getOrThrow())
                    } catch (error: Throwable) {
                        error.rethrowIfCancellationOrFatal()
                        false
                    }
                    val terminalResult = if (verified) {
                        ClipboardTerminalResult.VerifiedSuccess
                    } else {
                        ClipboardTerminalResult.AppliedUnverified
                    }
                    recordTerminal(
                        receiptOperation,
                        terminalResult,
                    )
                    val expected = text ?: result.getOrThrow()
                    pendingVerification = if (verified) null else PendingClipboardVerification(
                        direction,
                        ClipboardHash.of(expected),
                    )
                    onTerminal(terminalResult)
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            isRemoteOffline = false,
                            syncFailureCount = if (verified) 0 else 1,
                            lastFailureClass = if (verified) null else VERIFICATION_FAILURE_CLASS,
                            lastSafetyWarning = if (verified) {
                                null
                            } else {
                                "Transfer applied, but readback could not be verified."
                            },
                            status = if (verified) it.status else "Verification pending",
                            nextSyncDelaySeconds = if (verified) {
                                it.syncIntervalMinutes * 60L
                            } else {
                                retryDelayMillis(1) / 1_000L
                            },
                        )
                    }
                } else {
                    val failureClass = classifyFailure(result.exceptionOrNull())
                    recordTerminal(
                        receiptOperation,
                        ClipboardTerminalResult.Failure,
                        failureCode(result.exceptionOrNull()),
                    )
                    pendingVerification = null
                    onTerminal(ClipboardTerminalResult.Failure)
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            isRemoteOffline = failureClass.startsWith("connectivity."),
                            syncFailureCount = if (it.mode != ClipboardSyncMode.Off) it.syncFailureCount else 0,
                            lastFailureClass = failureClass,
                        )
                    }
                }
                updateSnapshot()
            }
        }
    }

    private suspend fun runResult(block: suspend () -> Result<String>): Result<String> = try {
        block()
    } catch (error: Throwable) {
        error.rethrowIfCancellationOrFatal()
        Result.failure(error)
    }

    private fun noteAutoFailure(error: Throwable?, operation: String) {
        if (!automaticPollingAllowed()) {
            _uiState.update {
                it.copy(
                    syncFailureCount = 0,
                    nextSyncDelaySeconds = 0L,
                    status = if (it.batterySaverActive) {
                        "Automatic sync paused by Battery Saver"
                    } else {
                        sessionStatus(it.session.phase)
                    },
                )
            }
            return
        }
        val failureClass = classifyFailure(error)
        val delayMillis = retryDelayMillis(_uiState.value.syncFailureCount + 1)
        _uiState.update {
            it.copy(
                isRemoteOffline = failureClass.startsWith("connectivity."),
                syncFailureCount = (it.syncFailureCount + 1).coerceAtMost(ClipboardBatteryPolicy.MAX_RETRY_COUNT),
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

    private fun noteAutoUnverified() {
        val failureTier = (_uiState.value.syncFailureCount + 1)
            .coerceAtMost(ClipboardBatteryPolicy.MAX_RETRY_COUNT)
        _uiState.update {
            it.copy(
                syncFailureCount = failureTier,
                lastFailureClass = VERIFICATION_FAILURE_CLASS,
                lastSafetyWarning = "Transfer applied, but readback could not be verified.",
                status = "Verification pending",
                nextSyncDelaySeconds = retryDelayMillis(failureTier) / 1_000L,
            )
        }
    }

    private fun noteAutoBlocked() {
        _uiState.update {
            it.copy(
                syncFailureCount = 0,
                lastFailureClass = null,
                nextSyncDelaySeconds = it.syncIntervalMinutes * 60L,
                isRemoteOffline = false,
            )
        }
    }

    private suspend fun retryPendingVerification(
        generation: Long,
        mode: ClipboardSyncMode,
    ): Boolean {
        val pending = pendingVerification ?: return false
        if (!syncContextStillCurrent(generation, mode)) return true
        val operation = ClipboardOperation(pending.direction, nowMillis())
        val verified = pending.matches(
            phoneText = if (pending.direction == ClipboardDirection.MacToPhone) {
                readPhoneClipboardForVerification()
            } else null,
            macText = if (pending.direction == ClipboardDirection.PhoneToMac) {
                connectionRepository.runCommand("pbpaste").getOrNull()
            } else null,
        )
        recordTerminal(
            operation,
            if (verified) ClipboardTerminalResult.VerifiedSuccess else ClipboardTerminalResult.AppliedUnverified,
        )
        if (verified) pendingVerification = null
        if (verified) noteAutoSuccess() else noteAutoUnverified()
        return true
    }

    private fun syncContextStillCurrent(
        generation: Long,
        mode: ClipboardSyncMode,
    ): Boolean {
        val current = _uiState.value
        return syncConfigurationGeneration.get() == generation &&
            current.mode == mode &&
            current.connectionReady &&
            current.session.canReadPhoneClipboard &&
            automaticPollingAllowed()
    }

    private fun scheduleSessionExpiry() {
        sessionExpiryJob?.cancel()
        val expiresAt = _uiState.value.session.requestedUntilElapsedRealtimeMillis ?: return
        sessionExpiryJob = viewModelScope.launch {
            while (
                isActive &&
                _uiState.value.session.requestedUntilElapsedRealtimeMillis == expiresAt
            ) {
                delay(clipboardSessionExpiryDelayMillis(expiresAt, elapsedRealtimeMillis()))
                val evaluatedAt = nowMillis()
                _uiState.update {
                    val evaluated = it.session.withEnvironment(
                        deviceUnlocked = isDeviceUnlocked(),
                        nowMillis = evaluatedAt,
                        elapsedRealtimeMillis = elapsedRealtimeMillis(),
                    )
                    it.copy(
                        session = evaluated,
                        liveSyncVisible = evaluated.canReadPhoneClipboard,
                        status = sessionStatus(evaluated.phase),
                    )
                }
                if (_uiState.value.session.phase == ClipboardSessionPhase.Expired) break
                delay(1_000L)
            }
            syncConfigurationGeneration.incrementAndGet()
            restartSyncLoop()
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
        ClipboardBatteryPolicy.retryDelayMillis(failureTier)

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

    private fun failureCode(error: Throwable?): ClipboardFailureCode = when (classifyFailure(error)) {
        "connectivity.missing_connection" -> ClipboardFailureCode.MissingConnection
        "connectivity.missing_fingerprint" -> ClipboardFailureCode.MissingFingerprint
        "connectivity.missing_ssh_key" -> ClipboardFailureCode.MissingSshKey
        "runtime.timeout" -> ClipboardFailureCode.Timeout
        "input.empty" -> ClipboardFailureCode.EmptyInput
        else -> ClipboardFailureCode.Unknown
    }

    private fun recordTerminal(
        operation: ClipboardOperation,
        result: ClipboardTerminalResult,
        failureCode: ClipboardFailureCode = ClipboardFailureCode.None,
    ) {
        val receipt = operation.finish(result, failureCode, nowMillis())
        lastSyncStore.save(receipt)
        diagnosticEventStore.recordTerminal(
            component = DiagnosticComponent.CLIPBOARD,
            result = clipboardDiagnosticResult(receipt.terminalResult),
            durationMs = receipt.completedAtMillis - receipt.startedAtMillis,
            timestampEpochMs = receipt.completedAtMillis,
        )
        _uiState.update { it.copy(lastSyncReceipt = receipt) }
    }

    private fun readPhoneClipboardForVerification(): String? {
        if (!refreshSessionAccess()) return null
        return clipboardManager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
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

    private fun refreshSessionAccess(): Boolean {
        val now = nowMillis()
        var allowed = false
        _uiState.update {
            val session = it.session.withEnvironment(
                deviceUnlocked = isDeviceUnlocked(),
                nowMillis = now,
                elapsedRealtimeMillis = elapsedRealtimeMillis(),
            )
            allowed = session.canReadPhoneClipboard
            it.copy(
                session = session,
                liveSyncVisible = allowed,
                status = if (allowed) it.status else sessionStatus(session.phase),
            )
        }
        if (!allowed) restartSyncLoop()
        return allowed
    }

    private fun isDeviceUnlocked(): Boolean = keyguardManager?.isDeviceLocked != true

    private fun sessionStatus(phase: ClipboardSessionPhase): String = when (phase) {
        ClipboardSessionPhase.Inactive -> "Start a clipboard session"
        ClipboardSessionPhase.Hidden -> "Clipboard session paused while hidden"
        ClipboardSessionPhase.Locked -> "Clipboard session paused while locked"
        ClipboardSessionPhase.Expired -> "Clipboard session expired"
        ClipboardSessionPhase.ActiveVisible -> "Clipboard session active"
    }

    private fun automaticPollingAllowed(): Boolean {
        updateBatterySaverState()
        var allowed = false
        _uiState.update {
            val session = it.session.withEnvironment(
                deviceUnlocked = isDeviceUnlocked(),
                nowMillis = nowMillis(),
                elapsedRealtimeMillis = elapsedRealtimeMillis(),
            )
            allowed = ClipboardBatteryPolicy.automaticPollingAllowed(
                session.phase,
                it.batterySaverActive,
            )
            it.copy(
                session = session,
                liveSyncVisible = session.canReadPhoneClipboard,
                nextSyncDelaySeconds = if (allowed) it.nextSyncDelaySeconds else 0L,
            )
        }
        return allowed
    }

    private fun updateBatterySaverState() {
        val active = isBatterySaverActive()
        _uiState.update {
            if (it.batterySaverActive == active) {
                it
            } else {
                it.copy(
                    batterySaverActive = active,
                    nextSyncDelaySeconds = if (active) 0L else it.nextSyncDelaySeconds,
                    status = if (active && it.mode != ClipboardSyncMode.Off) {
                        "Automatic sync paused by Battery Saver"
                    } else {
                        it.status
                    },
                )
            }
        }
    }

    private fun isBatterySaverActive(): Boolean = powerManager?.isPowerSaveMode == true

    private fun registerBatterySaverReceiver() {
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(batterySaverReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(batterySaverReceiver, filter)
        }
    }

    override fun onCleared() {
        runCatching { context.unregisterReceiver(batterySaverReceiver) }
        syncJob?.cancel()
        sessionExpiryJob?.cancel()
        super.onCleared()
    }
}

internal fun clipboardDiagnosticResult(result: ClipboardTerminalResult): DiagnosticResultCode = when (result) {
    ClipboardTerminalResult.VerifiedSuccess -> DiagnosticResultCode.SUCCEEDED
    ClipboardTerminalResult.AppliedUnverified -> DiagnosticResultCode.RETRYABLE
    ClipboardTerminalResult.Blocked -> DiagnosticResultCode.BLOCKED
    ClipboardTerminalResult.Failure -> DiagnosticResultCode.FAILED
    ClipboardTerminalResult.Cancellation -> DiagnosticResultCode.CANCELLED
    ClipboardTerminalResult.Conflict -> DiagnosticResultCode.BLOCKED
}

private const val VERIFICATION_FAILURE_CLASS = "verification.unconfirmed"

private fun Throwable.rethrowIfCancellationOrFatal() {
    when (this) {
        is CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        is LinkageError,
        -> throw this
    }
}
