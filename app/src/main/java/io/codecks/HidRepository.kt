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

data class HidHost(
    val address: String,
    val label: String,
)

data class HidRehydrationRecord(
    val schemaVersion: Int = HID_REHYDRATION_SCHEMA_VERSION,
    val desiredConnectionState: HidDesiredConnectionState,
) {
    init {
        require(schemaVersion == HID_REHYDRATION_SCHEMA_VERSION)
    }
}

internal object HidRehydrationCodec {
    fun encode(record: HidRehydrationRecord): String =
        "${record.schemaVersion}|${record.desiredConnectionState.persistedCode}"

    fun decode(value: String?): HidRehydrationRecord {
        if (value == null) {
            return HidRehydrationRecord(desiredConnectionState = HidDesiredConnectionState.Connected)
        }
        val parts = value.split('|')
        if (parts.size != 2 || parts[0].toIntOrNull() != HID_REHYDRATION_SCHEMA_VERSION) {
            return HidRehydrationRecord(desiredConnectionState = HidDesiredConnectionState.Disconnected)
        }
        return HidRehydrationRecord(
            desiredConnectionState = HidDesiredConnectionState.fromPersistedCode(parts[1]),
        )
    }
}

enum class HidLifecycle {
    Idle,
    Opening,
    Ready,
    Connected,
    PermissionMissing,
    Unavailable,
    Suspended,
    Failed,
}

enum class HidDesiredConnectionState(val persistedCode: String) {
    Connected("connected"),
    Disconnected("disconnected"),
    ;

    companion object {
        fun fromPersistedCode(value: String?): HidDesiredConnectionState =
            entries.firstOrNull { it.persistedCode == value } ?: Disconnected
    }
}

enum class HidFailureClass {
    None,
    Transient,
    RepairRequired,
}

enum class HidRetryDisposition {
    Idle,
    Scheduled,
    Attempting,
    Suspended,
    BlockedUntilRepair,
}

data class HidRetryMetadata(
    val disposition: HidRetryDisposition = HidRetryDisposition.Idle,
    val attempt: Int = 0,
    val nextAttemptAtMillis: Long = 0L,
) {
    init {
        require(attempt >= 0)
        require(nextAttemptAtMillis >= 0L)
        require(disposition != HidRetryDisposition.BlockedUntilRepair || nextAttemptAtMillis == 0L)
    }
}

data class HidState(
    val status: String = "Bluetooth idle",
    val lifecycle: HidLifecycle = HidLifecycle.Idle,
    val isReady: Boolean = false,
    val isConnected: Boolean = false,
    val hosts: List<HidHost> = emptyList(),
    val selectedHostAddress: String? = null,
    val desiredConnectionState: HidDesiredConnectionState = HidDesiredConnectionState.Connected,
    val failureClass: HidFailureClass = HidFailureClass.None,
    val issueCode: ConnectionIssueCode? = null,
    val retry: HidRetryMetadata = HidRetryMetadata(),
    val appVisibility: HidAppVisibility = HidAppVisibility.Unknown,
    val screenState: HidScreenState = HidScreenState.Unknown,
    val userLockState: HidUserLockState = HidUserLockState.Unknown,
    val bluetoothPower: HidBluetoothPower = HidBluetoothPower.Unknown,
    val inputAccess: HidInputAccess = HidInputAccess.Full,
    val lastTransitionReason: String = "Bluetooth idle",
    val lastTransitionAtMillis: Long = 0L,
) {
    val autoReconnectEnabled: Boolean
        get() = desiredConnectionState == HidDesiredConnectionState.Connected &&
            retry.disposition != HidRetryDisposition.Suspended &&
            retry.disposition != HidRetryDisposition.BlockedUntilRepair

    val reconnectAttempt: Int
        get() = retry.attempt

    val nextReconnectAtMillis: Long
        get() = retry.nextAttemptAtMillis
}

enum class HidAppVisibility {
    Unknown,
    Foreground,
    Background,
}

enum class HidScreenState {
    Unknown,
    On,
    Off,
}

enum class HidUserLockState {
    Unknown,
    Unlocked,
    Locked,
}

enum class HidBluetoothPower {
    Unknown,
    On,
    Off,
}

enum class HidInputAccess {
    Full,
    PointerOnly,
}

sealed interface HidSystemEvent {
    data object AppForegrounded : HidSystemEvent
    data object AppBackgrounded : HidSystemEvent
    data object ScreenOn : HidSystemEvent
    data object ScreenOff : HidSystemEvent
    data object UserLocked : HidSystemEvent
    data object UserUnlocked : HidSystemEvent
    data object BluetoothOn : HidSystemEvent
    data object BluetoothOff : HidSystemEvent
    data object ManualRetry : HidSystemEvent
}

data class HidSystemEventDecision(
    val state: HidState,
    val cancelConnectionAttempt: Boolean = false,
    val maintainConnection: Boolean = false,
)

internal fun reduceHidSystemEvent(
    current: HidState,
    event: HidSystemEvent,
): HidSystemEventDecision = when (event) {
    HidSystemEvent.AppForegrounded -> HidSystemEventDecision(
        current.copy(appVisibility = HidAppVisibility.Foreground),
    )
    HidSystemEvent.AppBackgrounded -> HidSystemEventDecision(
        current.copy(appVisibility = HidAppVisibility.Background),
    )
    HidSystemEvent.ScreenOn -> HidSystemEventDecision(
        state = current.copy(screenState = HidScreenState.On),
        maintainConnection = current.desiredConnectionState == HidDesiredConnectionState.Connected &&
            current.failureClass == HidFailureClass.Transient &&
            current.bluetoothPower != HidBluetoothPower.Off,
    )
    HidSystemEvent.ScreenOff -> HidSystemEventDecision(
        current.copy(screenState = HidScreenState.Off),
    )
    HidSystemEvent.UserLocked -> HidSystemEventDecision(
        current.copy(
            userLockState = HidUserLockState.Locked,
            inputAccess = HidInputAccess.PointerOnly,
        ),
    )
    HidSystemEvent.UserUnlocked -> HidSystemEventDecision(
        current.copy(
            userLockState = HidUserLockState.Unlocked,
            inputAccess = HidInputAccess.Full,
        ),
    )
    HidSystemEvent.BluetoothOff -> HidSystemEventDecision(
        state = current.copy(
            lifecycle = HidLifecycle.Suspended,
            isReady = false,
            isConnected = false,
            bluetoothPower = HidBluetoothPower.Off,
            failureClass = HidFailureClass.Transient,
            issueCode = ConnectionIssueCode.BLUETOOTH_DISABLED,
            retry = HidRetryMetadata(HidRetryDisposition.Suspended),
        ),
        cancelConnectionAttempt = true,
    )
    HidSystemEvent.BluetoothOn -> {
        val reconnectDesired = current.desiredConnectionState == HidDesiredConnectionState.Connected
        HidSystemEventDecision(
            state = current.copy(
                lifecycle = if (current.lifecycle == HidLifecycle.Suspended) HidLifecycle.Idle else current.lifecycle,
                bluetoothPower = HidBluetoothPower.On,
                failureClass = if (current.lifecycle == HidLifecycle.Suspended) HidFailureClass.None else current.failureClass,
                issueCode = if (current.lifecycle == HidLifecycle.Suspended) {
                    ConnectionIssueCode.HID_PROFILE_UNREGISTERED
                } else {
                    current.issueCode
                },
                retry = if (current.lifecycle == HidLifecycle.Suspended) HidRetryMetadata() else current.retry,
            ),
            maintainConnection = reconnectDesired,
        )
    }
    HidSystemEvent.ManualRetry -> HidSystemEventDecision(
        state = if (!current.canRetryTransientFailureNow()) {
            current
        } else {
            current.copy(retry = HidRetryMetadata(HidRetryDisposition.Attempting))
        },
        maintainConnection = current.canRetryTransientFailureNow(),
    )
}

private fun HidState.canRetryTransientFailureNow(): Boolean =
    desiredConnectionState == HidDesiredConnectionState.Connected &&
        failureClass == HidFailureClass.Transient &&
        bluetoothPower != HidBluetoothPower.Off

enum class HidLifecycleSignal {
    Idle,
    Opening,
    Ready,
    Connecting,
    Connected,
    Disconnected,
    PermissionMissing,
    BluetoothUnavailable,
    ProfileUnregistered,
    ProfileRegistrationFailed,
    ConnectionRequestFailed,
    Unknown,
}

data class HidTransitionDecision(
    val lifecycle: HidLifecycle,
    val failureClass: HidFailureClass,
    val issueCode: ConnectionIssueCode?,
    val retryDisposition: HidRetryDisposition,
)

internal fun classifyHidLifecycleSignal(
    status: String,
    isReady: Boolean,
    isConnected: Boolean,
): HidLifecycleSignal {
    val normalized = status.trim().lowercase()
    return when {
        "permission" in normalized -> HidLifecycleSignal.PermissionMissing
        "unavailable" in normalized -> HidLifecycleSignal.BluetoothUnavailable
        normalized == "hid registration failed" -> HidLifecycleSignal.ProfileRegistrationFailed
        normalized in setOf(
            "hid profile open failed",
            "hid profile not ready",
            "register hid first",
            "hid profile closed",
            "hid unregistered",
            "bluetooth closed",
        ) -> HidLifecycleSignal.ProfileUnregistered
        normalized == "connect request failed" -> HidLifecycleSignal.ConnectionRequestFailed
        normalized == "disconnected" -> HidLifecycleSignal.Disconnected
        isConnected || normalized.startsWith("connected ") -> HidLifecycleSignal.Connected
        normalized.startsWith("connecting ") -> HidLifecycleSignal.Connecting
        normalized.startsWith("opening ") || normalized.startsWith("registering ") -> HidLifecycleSignal.Opening
        isReady || normalized in setOf("hid registered", "hid profile ready") -> HidLifecycleSignal.Ready
        normalized == "bluetooth idle" -> HidLifecycleSignal.Idle
        else -> HidLifecycleSignal.Unknown
    }
}

internal fun resolveHidLifecycleTransition(
    current: HidLifecycle,
    signal: HidLifecycleSignal,
    desired: HidDesiredConnectionState,
): HidTransitionDecision = when (signal) {
    HidLifecycleSignal.Idle -> HidTransitionDecision(
        HidLifecycle.Idle,
        HidFailureClass.None,
        ConnectionIssueCode.HID_PROFILE_UNREGISTERED,
        HidRetryDisposition.Idle,
    )
    HidLifecycleSignal.Opening -> HidTransitionDecision(
        HidLifecycle.Opening,
        HidFailureClass.None,
        ConnectionIssueCode.CONNECTING,
        HidRetryDisposition.Idle,
    )
    HidLifecycleSignal.Ready -> HidTransitionDecision(
        HidLifecycle.Ready,
        HidFailureClass.None,
        null,
        HidRetryDisposition.Idle,
    )
    HidLifecycleSignal.Connecting -> HidTransitionDecision(
        HidLifecycle.Opening,
        HidFailureClass.None,
        ConnectionIssueCode.CONNECTING,
        HidRetryDisposition.Attempting,
    )
    HidLifecycleSignal.Connected -> HidTransitionDecision(
        HidLifecycle.Connected,
        HidFailureClass.None,
        null,
        HidRetryDisposition.Idle,
    )
    HidLifecycleSignal.Disconnected -> if (desired == HidDesiredConnectionState.Connected) {
        HidTransitionDecision(
            HidLifecycle.Ready,
            HidFailureClass.Transient,
            ConnectionIssueCode.CONNECT_BACKOFF,
            HidRetryDisposition.Scheduled,
        )
    } else {
        HidTransitionDecision(HidLifecycle.Ready, HidFailureClass.None, null, HidRetryDisposition.Idle)
    }
    HidLifecycleSignal.PermissionMissing -> HidTransitionDecision(
        HidLifecycle.PermissionMissing,
        HidFailureClass.RepairRequired,
        ConnectionIssueCode.BLUETOOTH_PERMISSION_DENIED,
        HidRetryDisposition.BlockedUntilRepair,
    )
    HidLifecycleSignal.BluetoothUnavailable -> HidTransitionDecision(
        HidLifecycle.Unavailable,
        HidFailureClass.Transient,
        ConnectionIssueCode.BLUETOOTH_DISABLED,
        HidRetryDisposition.Suspended,
    )
    HidLifecycleSignal.ProfileUnregistered -> HidTransitionDecision(
        HidLifecycle.Idle,
        HidFailureClass.RepairRequired,
        ConnectionIssueCode.HID_PROFILE_UNREGISTERED,
        HidRetryDisposition.BlockedUntilRepair,
    )
    HidLifecycleSignal.ProfileRegistrationFailed -> HidTransitionDecision(
        HidLifecycle.Failed,
        HidFailureClass.RepairRequired,
        ConnectionIssueCode.HID_PROFILE_REGISTRATION_FAILED,
        HidRetryDisposition.BlockedUntilRepair,
    )
    HidLifecycleSignal.ConnectionRequestFailed -> HidTransitionDecision(
        HidLifecycle.Ready,
        HidFailureClass.Transient,
        ConnectionIssueCode.CONNECT_BACKOFF,
        HidRetryDisposition.Scheduled,
    )
    HidLifecycleSignal.Unknown -> if (current == HidLifecycle.Connected) {
        HidTransitionDecision(HidLifecycle.Connected, HidFailureClass.None, null, HidRetryDisposition.Idle)
    } else {
        HidTransitionDecision(
            lifecycle = current,
            failureClass = HidFailureClass.Transient,
            issueCode = ConnectionIssueCode.UNKNOWN,
            retryDisposition = HidRetryDisposition.Idle,
        )
    }
}

enum class HidCommand {
    Copy,
    Paste,
    Cut,
    SelectAll,
    Undo,
    Redo,
    Find,
    Save,
    NewDocument,
    OpenDocument,
    CloseWindow,
    Enter,
    CommandEnter,
    Tab,
    Escape,
    Backspace,
    ForwardDelete,
    LineStart,
    LineEnd,
    WordLeft,
    WordRight,
    Spotlight,
    MissionControl,
    AppExpose,
    Launchpad,
    ShowDesktop,
    NotificationCenter,
    AppSwitcher,
    WindowSwitcher,
    BrowserBack,
    BrowserForward,
    Reload,
    SpaceLeft,
    SpaceRight,
    ScreenshotArea,
    ScreenshotWindow,
    PresentationPrevious,
    PresentationNext,
    PresentationPlayPause,
    PresentationStart,
    PresentationFirst,
    PresentationLast,
    PresentationBlack,
    PresentationWhite,
    PresentationExit,
    MediaPlayPause,
    MediaPrevious,
    MediaNext,
    MediaMute,
    MediaVolumeDown,
    MediaVolumeUp,
}

interface HidRepository {
    val state: StateFlow<HidState>
    fun start()
    fun maintain() = start()
    fun onSystemEvent(event: HidSystemEvent) = Unit
    fun refreshHosts()
    fun connect(address: String)
    fun disconnect()
    fun move(dx: Int, dy: Int)
    fun scroll(vertical: Int, horizontal: Int = 0)
    fun click(buttonMask: Int)
    fun press(buttonMask: Int)
    fun releaseButtons()
    fun typeText(text: String)
    fun send(command: HidCommand)
}

private const val RECONNECT_TICK_MS = 8_000L
private val RECONNECT_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)
private const val HID_REHYDRATION_SCHEMA_VERSION = 1

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controlEvents = Channel<HidControlEvent>(Channel.UNLIMITED)
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
            for (event in controlEvents) {
                when (event) {
                    is HidControlEvent.ControllerStatus -> refreshState(event.status)
                    is HidControlEvent.System -> applySystemEvent(event.event)
                    HidControlEvent.Maintain -> maintainNow()
                    HidControlEvent.ProfileRepair -> maintainNow(allowSingleRepairAttempt = true)
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
        enqueueControlEvent(HidControlEvent.System(event))
    }

    private fun maintainNow(allowSingleRepairAttempt: Boolean = false) {
        val current = _state.value
        if (current.bluetoothPower == HidBluetoothPower.Off ||
            (current.failureClass == HidFailureClass.RepairRequired && !allowSingleRepairAttempt)
        ) {
            return
        }
        controller.openProfile()
        refreshHosts()
        ensureReconnectLoop()
    }

    override fun refreshHosts() {
        devices = controller.bondedDevices()
        val hosts = devices.mapNotNull { device ->
            runCatching {
                HidHost(device.address, HidController.deviceLabel(device))
            }.getOrNull()
        }
        val selected = _state.value.selectedHostAddress
        val selectedStillBonded = selected == null || hosts.any { it.address == selected }
        if (!selectedStillBonded) {
            prefs.edit().remove(PREF_SELECTED_HOST).apply()
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
        userDisconnected = true
        persistDesiredConnectionState(HidDesiredConnectionState.Disconnected)
        _state.update {
            it.copy(
                desiredConnectionState = HidDesiredConnectionState.Disconnected,
                failureClass = HidFailureClass.None,
                issueCode = null,
                retry = HidRetryMetadata(),
            )
        }
        controller.disconnect()
    }
    override fun move(dx: Int, dy: Int) = controller.sendMouse(dx, dy, 0, 0)
    override fun scroll(vertical: Int, horizontal: Int) = controller.sendMouse(0, 0, vertical, horizontal)
    override fun click(buttonMask: Int) = controller.click(buttonMask)
    override fun press(buttonMask: Int) = controller.setMouseButtons(buttonMask)
    override fun releaseButtons() = controller.releaseAllInputs()
    override fun typeText(text: String) {
        if (_state.value.inputAccess == HidInputAccess.Full) controller.typeText(text)
    }
    override fun send(command: HidCommand) {
        if (_state.value.inputAccess != HidInputAccess.Full) return
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
                    state.failureClass != HidFailureClass.RepairRequired
                ) {
                    controller.openProfile()
                    refreshHosts()
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
        prefs.edit().putString(PREF_SELECTED_HOST, address).apply()
        _state.update { it.copy(selectedHostAddress = address) }
    }

    private companion object {
        const val PREF_SELECTED_HOST = "selected_host_address"
        const val PREF_REHYDRATION_RECORD = "desired_connection_v1"
    }

    private fun enqueueControlEvent(event: HidControlEvent) {
        check(controlEvents.trySend(event).isSuccess) { "HID control path is unavailable." }
    }

    private fun applySystemEvent(event: HidSystemEvent) {
        val decision = reduceHidSystemEvent(_state.value, event)
        _state.value = decision.state
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
        rehydrationPrefs.edit().putString(PREF_REHYDRATION_RECORD, encoded).apply()
    }
}

private sealed interface HidControlEvent {
    data class ControllerStatus(val status: String) : HidControlEvent
    data class System(val event: HidSystemEvent) : HidControlEvent
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
