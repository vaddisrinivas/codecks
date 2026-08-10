package io.codecks

import io.codecks.domain.connection.ConnectionIssueCode
import kotlinx.coroutines.flow.StateFlow

private const val HID_REHYDRATION_SCHEMA_VERSION = 1

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

internal fun HidSystemEvent.requiresImmediateInputInvalidation(): Boolean =
    this == HidSystemEvent.UserLocked || this == HidSystemEvent.BluetoothOff

data class HidSystemEventDecision(
    val state: HidState,
    val cancelConnectionAttempt: Boolean = false,
    val maintainConnection: Boolean = false,
    val invalidatePendingInputs: Boolean = false,
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
        invalidatePendingInputs = true,
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
    ConnectionTimedOut,
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
        normalized == "hid connect timed out" -> HidLifecycleSignal.ConnectionTimedOut
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
    HidLifecycleSignal.ConnectionTimedOut -> HidTransitionDecision(
        HidLifecycle.Failed,
        HidFailureClass.RepairRequired,
        ConnectionIssueCode.HID_TRANSPORT_TIMEOUT,
        HidRetryDisposition.BlockedUntilRepair,
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
    suspend fun deliverText(text: String): Result<HidDeliveryReceipt> =
        Result.failure(UnsupportedOperationException("Confirmed HID text delivery unavailable"))
    suspend fun deliver(command: HidCommand): Result<HidDeliveryReceipt> =
        Result.failure(UnsupportedOperationException("Confirmed HID command delivery unavailable"))
}

data class HidDeliveryReceipt(
    val operation: String,
    val acceptedUnits: Int,
) {
    init {
        require(operation.isNotBlank())
        require(acceptedUnits > 0)
    }
}
