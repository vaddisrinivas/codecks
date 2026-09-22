package io.codecks

import android.bluetooth.BluetoothDevice
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import io.codecks.domain.connection.ConnectionIssueCode
import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.privacy.recordTerminal
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticResultCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.selects.select
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val RECONNECT_TICK_MS = 8_000L
private val RECONNECT_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)
private const val HID_CONTROL_QUEUE_CAPACITY = 64

internal fun interface HidClock {
    fun nowMillis(): Long
}

internal class HidReconnectPolicy(
    private val clock: HidClock,
) {
    fun nowMillis(): Long = clock.nowMillis()

    fun scheduledRetry(
        attempt: Int,
        existingDeadlineMillis: Long = 0L,
        failureClass: HidFailureClass = HidFailureClass.Transient,
    ): HidRetryMetadata {
        if (failureClass != HidFailureClass.Transient) {
            return HidRetryMetadata(HidRetryDisposition.BlockedUntilRepair)
        }
        val boundedAttempt = attempt.coerceAtLeast(1)
        val now = clock.nowMillis()
        return HidRetryMetadata(
            disposition = HidRetryDisposition.Scheduled,
            attempt = boundedAttempt,
            nextAttemptAtMillis = if (existingDeadlineMillis > now) {
                existingDeadlineMillis
            } else {
                now + reconnectBackoffMillis(boundedAttempt)
            },
        )
    }
}

@Singleton
class DefaultHidRepository @Inject constructor(
    @ApplicationContext context: Context,
) : HidRepository {
    private val prefs = context.getSharedPreferences("hid_repository", Context.MODE_PRIVATE)
    private val rehydrationPrefs = context.getSharedPreferences("hid_rehydration", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(HidState())
    override val state: StateFlow<HidState> = _state.asStateFlow()
    private var devices: List<BluetoothDevice> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val priorityEvents = Channel<HidControlEvent>(capacity = HID_CONTROL_QUEUE_CAPACITY)
    private val persistenceWrites = Channel<HidPersistenceWrite>(capacity = Channel.UNLIMITED)
    private val controllerStatuses = Channel<String>(capacity = Channel.CONFLATED)
    private var reconnectJob: Job? = null
    private var userDisconnected = false
    private val reconnectPolicy = HidReconnectPolicy(HidClock { System.currentTimeMillis() })
    private val diagnosticEventStore = DiagnosticEventStore(context)
    private val controller = HidController(context) { status ->
        enqueueControlEvent(HidControlEvent.ControllerStatus(status))
    }

    init {
        val rehydration = HidRehydrationCodec.decode(
            rehydrationPrefs.getString(PREF_REHYDRATION_RECORD, null),
        )
        userDisconnected = rehydration.desiredConnectionState == HidDesiredConnectionState.Disconnected
        _state.value = _state.value.copy(
            selectedHostAddress = prefs.getString(PREF_SELECTED_HOST, null),
            desiredConnectionState = rehydration.desiredConnectionState,
        )
        scope.launch {
            while (isActive) {
                val event = priorityEvents.tryReceive().getOrNull() ?: select {
                    priorityEvents.onReceiveCatching { it.getOrNull() }
                    controllerStatuses.onReceiveCatching {
                        it.getOrNull()?.let(HidControlEvent::ControllerStatus)
                    }
                } ?: break
                try {
                    when (event) {
                        is HidControlEvent.ControllerStatus -> refreshState(event.status)
                        is HidControlEvent.System -> applySystemEvent(event.event)
                        is HidControlEvent.Connect -> connectNow(event.address)
                        HidControlEvent.Disconnect -> disconnectNow()
                        HidControlEvent.RefreshHosts -> refreshHostsNow()
                        HidControlEvent.Maintain -> maintainNow()
                        HidControlEvent.ProfileRepair -> maintainNow(allowSingleRepairAttempt = true)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    _state.update {
                        it.copy(
                            status = "Bluetooth event failed: ${error.message ?: "unknown error"}",
                            lifecycle = HidLifecycle.Failed,
                            lastTransitionReason = "Bluetooth event failed",
                            lastTransitionAtMillis = reconnectPolicy.nowMillis(),
                        )
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            for (write in persistenceWrites) {
                if (!runCatching(write.write).getOrDefault(false)) {
                    _state.update {
                        it.copy(
                            status = write.failureMessage,
                            lastTransitionReason = write.failureMessage,
                            lastTransitionAtMillis = reconnectPolicy.nowMillis(),
                        )
                    }
                }
            }
        }
    }

    override fun start() {
        enqueueControlEvent(HidControlEvent.ProfileRepair)
    }

    override fun maintain() {
        enqueueControlEvent(HidControlEvent.Maintain)
    }

    override fun onSystemEvent(event: HidSystemEvent) {
        if (event.requiresImmediateInputInvalidation()) {
            // Safety transitions update access and invalidate input before queued maintenance.
            applySystemEvent(event)
            return
        }
        enqueueControlEvent(HidControlEvent.System(event))
    }

    private fun maintainNow(allowSingleRepairAttempt: Boolean = false) {
        val current = _state.value
        if (current.bluetoothPower == HidBluetoothPower.Off ||
            (current.failureClass == HidFailureClass.RepairRequired && !allowSingleRepairAttempt)
        ) {
            return
        }
        if (!controller.isReady) controller.openProfile()
        refreshHostsNow()
        ensureReconnectLoop()
    }

    override fun refreshHosts() {
        enqueueControlEvent(HidControlEvent.RefreshHosts)
    }

    private fun refreshHostsNow() {
        devices = controller.bondedDevices()
        val hosts = devices.mapNotNull { device ->
            runCatching {
                HidHost(device.address, HidController.deviceLabel(device))
            }.getOrNull()
        }
        val selected = _state.value.selectedHostAddress
        val selectedStillBonded = selected == null || hosts.any { it.address == selected }
        if (!selectedStillBonded) {
            persistOnIo("Could not clear unavailable HID host") {
                prefs.edit().remove(PREF_SELECTED_HOST).commit()
            }
        }
        _state.update { state ->
            val nextSelected = state.selectedHostAddress?.takeIf { address -> hosts.any { it.address == address } }
            state.copy(
                hosts = prioritizeHosts(hosts, nextSelected),
                selectedHostAddress = nextSelected,
                status = if (state.selectedHostAddress != null && nextSelected == null) {
                    "Saved Mac unavailable"
                } else {
                    state.status
                },
            )
        }
        attemptAutoReconnect()
    }

    override fun connect(address: String) {
        enqueueControlEvent(HidControlEvent.Connect(address))
    }

    private fun connectNow(address: String) {
        userDisconnected = false
        persistDesiredConnectionState(HidDesiredConnectionState.Connected)
        saveSelectedHost(address)
        _state.update {
            it.copy(
                desiredConnectionState = HidDesiredConnectionState.Connected,
                failureClass = HidFailureClass.None,
                issueCode = ConnectionIssueCode.CONNECTING,
                retry = HidRetryMetadata(disposition = HidRetryDisposition.Attempting),
            )
        }
        val target = devices.firstOrNull { runCatching { it.address == address }.getOrDefault(false) }
        if (target == null) {
            _state.update {
                it.copy(
                    failureClass = HidFailureClass.RepairRequired,
                    issueCode = ConnectionIssueCode.HOST_UNPAIRED,
                    retry = HidRetryMetadata(disposition = HidRetryDisposition.BlockedUntilRepair),
                )
            }
        } else {
            controller.connect(target)
        }
    }

    override fun disconnect() {
        enqueueControlEvent(HidControlEvent.Disconnect)
    }

    private fun disconnectNow() {
        userDisconnected = true
        _state.update {
            it.copy(
                desiredConnectionState = HidDesiredConnectionState.Disconnected,
                failureClass = HidFailureClass.None,
                issueCode = null,
                retry = HidRetryMetadata(),
            )
        }
        controller.releaseAllInputs()
        controller.disconnect()
        persistDesiredConnectionState(HidDesiredConnectionState.Disconnected)
    }
    override fun move(dx: Int, dy: Int) = controller.sendMouse(dx, dy, 0, 0)
    override fun scroll(vertical: Int, horizontal: Int) = controller.sendMouse(0, 0, vertical, horizontal)
    override fun click(buttonMask: Int) = controller.click(buttonMask)
    override fun press(buttonMask: Int) = controller.setMouseButtons(buttonMask)
    override fun releaseButtons() = controller.releaseAllInputs()
    override fun typeText(text: String) {
        if (_state.value.inputAccess == HidInputAccess.Full) controller.typeText(text)
    }
    override suspend fun deliverText(text: String): Result<HidDeliveryReceipt> = runCatching {
        require(_state.value.inputAccess == HidInputAccess.Full) { "Keyboard input is locked" }
        require(_state.value.isConnected) { "Bluetooth keyboard is not connected" }
        require(text.isNotEmpty()) { "Text is empty" }
        controller.typeTextConfirmed(text).awaitConfirmedDelivery(
            failureMessage = "Mac did not accept the Bluetooth text report",
            releaseAllInputs = controller::releaseAllInputs,
        )
        HidDeliveryReceipt("text", text.length)
    }

    override suspend fun deliver(command: HidCommand): Result<HidDeliveryReceipt> = runCatching {
        require(_state.value.inputAccess == HidInputAccess.Full) { "Keyboard input is locked" }
        require(_state.value.isConnected) { "Bluetooth keyboard is not connected" }
        val stroke = when (command) {
            HidCommand.Paste -> HidReports.MOD_GUI to HidReports.KEY_V
            HidCommand.Enter -> 0.toByte() to HidReports.KEY_ENTER
            else -> error("Confirmed HID delivery is unavailable for ${command.name}")
        }
        controller.keyTapConfirmed(stroke.first, stroke.second).awaitConfirmedDelivery(
            failureMessage = "Mac did not accept the Bluetooth ${command.name.lowercase()} report",
            releaseAllInputs = controller::releaseAllInputs,
        )
        HidDeliveryReceipt(command.name, 1)
    }
    override fun send(command: HidCommand) {
        if (_state.value.inputAccess != HidInputAccess.Full) return
        sendUnchecked(command)
    }
    override fun sendRestrictedMedia(command: HidCommand): Boolean {
        if (_state.value.inputAccess != HidInputAccess.PointerOnly || !_state.value.isConnected || !command.isRestrictedMediaCommand()) {
            return false
        }
        sendUnchecked(command)
        return true
    }
    private fun sendUnchecked(command: HidCommand) {
        when (command) {
            HidCommand.Copy -> key(HidReports.MOD_GUI, HidReports.KEY_C)
            HidCommand.Paste -> key(HidReports.MOD_GUI, HidReports.KEY_V)
            HidCommand.Cut -> key(HidReports.MOD_GUI, HidReports.KEY_X)
            HidCommand.SelectAll -> key(HidReports.MOD_GUI, HidReports.KEY_A)
            HidCommand.Undo -> key(HidReports.MOD_GUI, HidReports.KEY_Z)
            HidCommand.Redo -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_Z,
            )
            HidCommand.Find -> key(HidReports.MOD_GUI, HidReports.KEY_F)
            HidCommand.Save -> key(HidReports.MOD_GUI, HidReports.KEY_S)
            HidCommand.NewDocument -> key(HidReports.MOD_GUI, HidReports.KEY_N)
            HidCommand.OpenDocument -> key(HidReports.MOD_GUI, HidReports.KEY_O)
            HidCommand.CloseWindow -> key(HidReports.MOD_GUI, HidReports.KEY_W)
            HidCommand.Enter -> key(0, HidReports.KEY_ENTER)
            HidCommand.CommandEnter -> key(HidReports.MOD_GUI, HidReports.KEY_ENTER)
            HidCommand.Tab -> key(0, HidReports.KEY_TAB)
            HidCommand.Escape -> key(0, HidReports.KEY_ESC)
            HidCommand.Backspace -> key(0, HidReports.KEY_BACKSPACE)
            HidCommand.ForwardDelete -> key(0, HidReports.KEY_DELETE)
            HidCommand.LineStart -> key(HidReports.MOD_GUI, HidReports.KEY_LEFT)
            HidCommand.LineEnd -> key(HidReports.MOD_GUI, HidReports.KEY_RIGHT)
            HidCommand.WordLeft -> key(HidReports.MOD_ALT, HidReports.KEY_LEFT)
            HidCommand.WordRight -> key(HidReports.MOD_ALT, HidReports.KEY_RIGHT)
            HidCommand.Spotlight -> key(HidReports.MOD_GUI, HidReports.KEY_SPACE)
            HidCommand.MissionControl -> key(HidReports.MOD_CTRL, HidReports.KEY_UP)
            HidCommand.AppExpose -> key(HidReports.MOD_CTRL, HidReports.KEY_DOWN)
            HidCommand.Launchpad -> key(0, HidReports.KEY_F4)
            HidCommand.ShowDesktop -> key(HidReports.MOD_GUI, HidReports.KEY_F11)
            HidCommand.NotificationCenter -> key(HidReports.MOD_GUI, HidReports.KEY_F12)
            HidCommand.AppSwitcher -> key(HidReports.MOD_GUI, HidReports.KEY_TAB)
            HidCommand.WindowSwitcher -> key(HidReports.MOD_GUI, HidReports.KEY_GRAVE)
            HidCommand.BrowserBack -> key(HidReports.MOD_GUI, HidReports.KEY_LEFT_BRACKET)
            HidCommand.BrowserForward -> key(HidReports.MOD_GUI, HidReports.KEY_RIGHT_BRACKET)
            HidCommand.Reload -> key(HidReports.MOD_GUI, HidReports.KEY_R)
            HidCommand.SpaceLeft -> key(HidReports.MOD_CTRL, HidReports.KEY_LEFT)
            HidCommand.SpaceRight -> key(HidReports.MOD_CTRL, HidReports.KEY_RIGHT)
            HidCommand.ScreenshotArea -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_4,
            )
            HidCommand.ScreenshotWindow -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_5,
            )
            HidCommand.PresentationPrevious -> key(0, HidReports.KEY_LEFT)
            HidCommand.PresentationNext -> key(0, HidReports.KEY_RIGHT)
            HidCommand.PresentationPlayPause -> key(0, HidReports.KEY_SPACE)
            HidCommand.PresentationStart -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_ENTER,
            )
            HidCommand.PresentationFirst -> key(0, HidReports.KEY_HOME)
            HidCommand.PresentationLast -> key(0, HidReports.KEY_END)
            HidCommand.PresentationBlack -> key(0, HidReports.KEY_B)
            HidCommand.PresentationWhite -> key(0, HidReports.KEY_W)
            HidCommand.PresentationExit -> key(0, HidReports.KEY_ESC)
            HidCommand.MediaPlayPause -> consumer(HidReports.CONSUMER_PLAY_PAUSE)
            HidCommand.MediaPrevious -> consumer(HidReports.CONSUMER_SCAN_PREVIOUS)
            HidCommand.MediaNext -> consumer(HidReports.CONSUMER_SCAN_NEXT)
            HidCommand.MediaMute -> consumer(HidReports.CONSUMER_MUTE)
            HidCommand.MediaVolumeDown -> consumer(HidReports.CONSUMER_VOLUME_DOWN)
            HidCommand.MediaVolumeUp -> consumer(HidReports.CONSUMER_VOLUME_UP)
        }
    }

    private fun key(modifier: Byte, key: Byte) = controller.keyTap(modifier, key)
    private fun consumer(usage: Int) = controller.consumerTap(usage)

    private fun refreshState(status: String) {
        val now = reconnectPolicy.nowMillis()
        val previousLifecycle = _state.value.lifecycle
        _state.update {
            if (it.bluetoothPower == HidBluetoothPower.Off) {
                return@update it.copy(
                    status = status,
                    lifecycle = HidLifecycle.Suspended,
                    isReady = false,
                    isConnected = false,
                    failureClass = HidFailureClass.Transient,
                    issueCode = ConnectionIssueCode.BLUETOOTH_DISABLED,
                    retry = HidRetryMetadata(HidRetryDisposition.Suspended),
                )
            }
            val signal = classifyHidLifecycleSignal(status, controller.isReady, controller.isConnected)
            val decision = resolveHidLifecycleTransition(it.lifecycle, signal, it.desiredConnectionState)
            val missingTarget = decision.lifecycle == HidLifecycle.Ready && it.selectedHostAddress == null
            val changed = status != it.status ||
                decision.lifecycle != it.lifecycle ||
                controller.isConnected != it.isConnected
            it.copy(
                status = status,
                lifecycle = decision.lifecycle,
                isReady = controller.isReady,
                isConnected = controller.isConnected,
                failureClass = if (missingTarget) HidFailureClass.RepairRequired else decision.failureClass,
                issueCode = if (missingTarget) ConnectionIssueCode.HOST_UNPAIRED else decision.issueCode,
                retry = when {
                    missingTarget -> HidRetryMetadata(disposition = HidRetryDisposition.BlockedUntilRepair)
                    else -> when (decision.retryDisposition) {
                        HidRetryDisposition.BlockedUntilRepair -> HidRetryMetadata(
                            disposition = HidRetryDisposition.BlockedUntilRepair,
                        )
                        HidRetryDisposition.Attempting -> it.retry.copy(
                            disposition = HidRetryDisposition.Attempting,
                            nextAttemptAtMillis = 0L,
                        )
                        HidRetryDisposition.Scheduled -> it.retry.copy(disposition = HidRetryDisposition.Scheduled)
                        HidRetryDisposition.Suspended -> HidRetryMetadata(HidRetryDisposition.Suspended)
                        HidRetryDisposition.Idle -> HidRetryMetadata()
                    }
                },
                lastTransitionReason = if (changed) status else it.lastTransitionReason,
                lastTransitionAtMillis = if (changed) now else it.lastTransitionAtMillis,
            )
        }
        val nextLifecycle = _state.value.lifecycle
        if (nextLifecycle != previousLifecycle) {
            val result = when (nextLifecycle) {
                HidLifecycle.Connected,
                HidLifecycle.Ready,
                -> DiagnosticResultCode.SUCCEEDED
                HidLifecycle.Failed -> DiagnosticResultCode.FAILED
                HidLifecycle.PermissionMissing,
                HidLifecycle.Unavailable,
                -> DiagnosticResultCode.BLOCKED
                HidLifecycle.Suspended -> DiagnosticResultCode.RETRYABLE
                HidLifecycle.Idle,
                HidLifecycle.Opening,
                -> DiagnosticResultCode.SKIPPED
            }
            diagnosticEventStore.recordTerminal(
                component = DiagnosticComponent.HID,
                result = result,
                attempt = _state.value.retry.attempt,
                timestampEpochMs = now,
            )
        }
        if (controller.isConnected) {
            _state.update {
                it.copy(
                    desiredConnectionState = HidDesiredConnectionState.Connected,
                    retry = HidRetryMetadata(),
                )
            }
        } else if (status.equals("Disconnected", ignoreCase = true) && !userDisconnected) {
            scheduleNextReconnectAttempt()
        } else if (status.equals("Connect request failed", ignoreCase = true) && !userDisconnected) {
            scheduleNextReconnectAttempt()
        } else if (status.equals("Disconnected", ignoreCase = true)) {
            _state.update { it.copy(retry = HidRetryMetadata()) }
        }
        attemptAutoReconnect()
    }

    private fun attemptAutoReconnect() {
        if (userDisconnected) return
        val current = _state.value
        if (current.desiredConnectionState != HidDesiredConnectionState.Connected ||
            current.bluetoothPower == HidBluetoothPower.Off ||
            current.retry.disposition == HidRetryDisposition.BlockedUntilRepair
        ) {
            return
        }
        val selectedAddress = current.selectedHostAddress ?: return
        val now = reconnectPolicy.nowMillis()
        if (!controller.isReady || controller.isConnected || now < current.nextReconnectAtMillis) {
            return
        }
        devices.firstOrNull { runCatching { it.address == selectedAddress }.getOrDefault(false) }
            ?.let { device ->
                val attempt = current.reconnectAttempt + 1
                val nextDelayMillis = reconnectBackoffMillis(attempt)
                _state.update {
                    it.copy(
                        retry = HidRetryMetadata(
                            disposition = HidRetryDisposition.Attempting,
                            attempt = attempt,
                            nextAttemptAtMillis = now + nextDelayMillis,
                        ),
                        status = if (attempt == 1) {
                            "Reconnecting ${HidController.deviceLabel(device)}"
                        } else {
                            "Reconnecting ${HidController.deviceLabel(device)} (try $attempt)"
                        },
                        failureClass = HidFailureClass.None,
                        issueCode = ConnectionIssueCode.CONNECTING,
                    )
                }
                controller.connect(device)
            }
    }

    private fun ensureReconnectLoop() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (isActive) {
                val state = _state.value
                if (!userDisconnected &&
                    state.desiredConnectionState == HidDesiredConnectionState.Connected &&
                    state.bluetoothPower != HidBluetoothPower.Off &&
                    state.failureClass != HidFailureClass.RepairRequired &&
                    !controller.isConnected
                ) {
                    if (!controller.isReady) controller.openProfile()
                    refreshHostsNow()
                }
                delay(RECONNECT_TICK_MS)
            }
        }
    }

    private fun scheduleNextReconnectAttempt() {
        _state.update { state ->
            if (state.desiredConnectionState != HidDesiredConnectionState.Connected ||
                state.failureClass == HidFailureClass.RepairRequired
            ) {
                return@update state
            }
            val attempt = state.reconnectAttempt.coerceAtLeast(1)
            state.copy(
                failureClass = HidFailureClass.Transient,
                issueCode = ConnectionIssueCode.CONNECT_BACKOFF,
                retry = reconnectPolicy.scheduledRetry(
                    attempt = attempt,
                    existingDeadlineMillis = state.nextReconnectAtMillis,
                ),
            )
        }
    }

    private fun saveSelectedHost(address: String) {
        _state.update { it.copy(selectedHostAddress = address) }
        persistOnIo("Could not persist selected HID host") {
            prefs.edit().putString(PREF_SELECTED_HOST, address).commit()
        }
    }

    private companion object {
        const val PREF_SELECTED_HOST = "selected_host_address"
        const val PREF_REHYDRATION_RECORD = "desired_connection_v1"
    }

    private fun enqueueControlEvent(event: HidControlEvent) {
        if (event is HidControlEvent.ControllerStatus) {
            controllerStatuses.trySend(event.status)
            return
        }
        if (priorityEvents.trySend(event).isFailure) {
            scope.launch { priorityEvents.send(event) }
        }
    }

    private fun applySystemEvent(event: HidSystemEvent) {
        val decision = reduceHidSystemEvent(_state.value, event)
        _state.value = decision.state
        if (decision.invalidatePendingInputs) {
            controller.releaseAllInputs()
        }
        if (decision.cancelConnectionAttempt) {
            controller.releaseAllInputs()
            controller.disconnect()
        }
        if (decision.maintainConnection) {
            maintainNow(allowSingleRepairAttempt = event == HidSystemEvent.ManualRetry)
        }
    }

    private fun persistDesiredConnectionState(desired: HidDesiredConnectionState) {
        val encoded = HidRehydrationCodec.encode(
            HidRehydrationRecord(desiredConnectionState = desired),
        )
        persistOnIo("Could not persist HID connection intent") {
            rehydrationPrefs.edit().putString(PREF_REHYDRATION_RECORD, encoded).commit()
        }
    }

    private fun persistOnIo(failureMessage: String, write: () -> Boolean) {
        check(persistenceWrites.trySend(HidPersistenceWrite(failureMessage, write)).isSuccess) {
            "HID persistence queue is unavailable"
        }
    }
}

private suspend fun <T> CompletableFuture<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        whenComplete { value, error ->
            if (!continuation.isActive) return@whenComplete
            if (error != null) continuation.resumeWithException(error) else continuation.resume(value)
        }
        continuation.invokeOnCancellation {
            cancel(true)
        }
    }

internal suspend fun CompletableFuture<Boolean>.awaitConfirmedDelivery(
    failureMessage: String,
    releaseAllInputs: () -> Unit,
) {
    val accepted = try {
        awaitResult()
    } catch (failure: Throwable) {
        val primaryFailure = failure.unwrapCompletionFailure()
        releaseAfterConfirmedFailure(primaryFailure, releaseAllInputs)
        throw primaryFailure
    }
    if (!accepted) {
        val failure = IllegalStateException(failureMessage)
        releaseAfterConfirmedFailure(failure, releaseAllInputs)
        throw failure
    }
}

private fun Throwable.unwrapCompletionFailure(): Throwable =
    if (this is CompletionException && cause != null) cause!! else this

private fun releaseAfterConfirmedFailure(
    primaryFailure: Throwable,
    releaseAllInputs: () -> Unit,
) {
    try {
        releaseAllInputs()
    } catch (cleanupFailure: Throwable) {
        if (cleanupFailure !== primaryFailure) {
            primaryFailure.addSuppressed(cleanupFailure)
        }
    }
}

private data class HidPersistenceWrite(
    val failureMessage: String,
    val write: () -> Boolean,
)

private sealed interface HidControlEvent {
    data class ControllerStatus(val status: String) : HidControlEvent
    data class System(val event: HidSystemEvent) : HidControlEvent
    data class Connect(val address: String) : HidControlEvent
    data object Disconnect : HidControlEvent
    data object RefreshHosts : HidControlEvent
    data object Maintain : HidControlEvent
    data object ProfileRepair : HidControlEvent
}

private fun reconnectBackoffMillis(attempt: Int): Long =
    RECONNECT_BACKOFF_MS[(attempt - 1).coerceIn(0, RECONNECT_BACKOFF_MS.lastIndex)]

fun HidState.redactedDiagnosticSummary(nowMillis: Long = System.currentTimeMillis()): String {
    val retryInSeconds = ((nextReconnectAtMillis - nowMillis).coerceAtLeast(0L) / 1_000L).toInt()
    val ageSeconds = lastTransitionAtMillis
        .takeIf { it > 0L }
        ?.let { ((nowMillis - it).coerceAtLeast(0L) / 1_000L).toInt() }
    return buildString {
        append("lifecycle=$lifecycle")
        append(" desired=$desiredConnectionState")
        append(" failure=$failureClass")
        append(" issue=${issueCode?.persistedCode ?: "none"}")
        append(" app=$appVisibility")
        append(" screen=$screenState")
        append(" lock=$userLockState")
        append(" bluetooth=$bluetoothPower")
        append(" input=$inputAccess")
        append(" ready=$isReady")
        append(" connected=$isConnected")
        append(" hosts=${hosts.size}")
        append(" selected=${selectedHostAddress != null}")
        append(" reconnectAttempt=$reconnectAttempt")
        append(" retryIn=${retryInSeconds}s")
        append(" lastReason=${lastTransitionReason.safeHidReason()}")
        if (ageSeconds != null) append(" lastAge=${ageSeconds}s")
    }
}

internal fun prioritizeHosts(hosts: List<HidHost>, selectedAddress: String?): List<HidHost> {
    if (hosts.isEmpty()) return emptyList()

    val scored = hosts.map { host ->
        host to hostPriority(host, selectedAddress)
    }
    val source = scored.filter { (host, score) -> score > 0 || host.address == selectedAddress }
    return source
        .sortedWith(
            compareByDescending<Pair<HidHost, Int>> { it.second }
                .thenBy { it.first.label.lowercase() }
                .thenBy { it.first.address },
        )
        .map { it.first }
}

private fun hostPriority(host: HidHost, selectedAddress: String?): Int {
    val label = host.label.lowercase()
    var score = 0
    if (host.address == selectedAddress) score += 100
    if (COMPUTER_HINTS.any(label::contains)) score += 20
    if (UNRELATED_HINTS.any(label::contains)) score -= 80
    return score
}

private fun String.safeHidReason(): String =
    replace(Regex("""([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"""), "[bluetooth-address]")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(96)
        .ifBlank { "none" }

private val COMPUTER_HINTS = listOf(
    "macbook",
    "imac",
    "mac mini",
    "mac studio",
    "desktop",
    "laptop",
    "notebook",
    "workstation",
    "surface",
    "thinkpad",
    "xps",
    "pc",
)

private val UNRELATED_HINTS = listOf(
    "airpods",
    "buds",
    "headphone",
    "headset",
    "speaker",
    "earbud",
    "watch",
    "keyboard",
    "mouse",
    "trackpad",
    "phone",
    "galaxy",
    "iphone",
    "ipad",
    "tablet",
    "tv",
)
