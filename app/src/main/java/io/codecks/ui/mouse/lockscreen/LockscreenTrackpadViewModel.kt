package io.codecks.ui.mouse.lockscreen

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.codecks.HidRepository
import io.codecks.core.trackpad.LockscreenCapability
import io.codecks.core.trackpad.LockscreenControlState
import io.codecks.core.trackpad.LockscreenDecision
import io.codecks.core.trackpad.LockscreenTrackpadPolicy
import io.codecks.core.trackpad.PointerDeltaAccumulator
import io.codecks.core.trackpad.TrackpadEntryOrigin
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface LockscreenPointerPort {
    fun move(dx: Int, dy: Int)
    fun scroll(vertical: Int, horizontal: Int)
    fun click(buttonMask: Int)
    fun press(buttonMask: Int)
    fun releaseButtons()
}

private class HidLockscreenPointerPort(
    private val hidRepository: HidRepository,
) : LockscreenPointerPort {
    override fun move(dx: Int, dy: Int) = hidRepository.move(dx, dy)
    override fun scroll(vertical: Int, horizontal: Int) = hidRepository.scroll(vertical, horizontal)
    override fun click(buttonMask: Int) = hidRepository.click(buttonMask)
    override fun press(buttonMask: Int) = hidRepository.press(buttonMask)
    override fun releaseButtons() = hidRepository.releaseButtons()
}

data class LockscreenTrackpadUiState(
    val controlState: LockscreenControlState,
    val decision: LockscreenDecision,
    val settings: TrackpadSettings,
)

sealed interface LockscreenTrackpadEvent {
    data object Finish : LockscreenTrackpadEvent
    data object RequestUnlock : LockscreenTrackpadEvent
}

@HiltViewModel
class LockscreenTrackpadViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val hidRepository: HidRepository,
    private val trackpadSettingsRepository: TrackpadSettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val pointerAccumulator = PointerDeltaAccumulator()
    private val pointerPort: LockscreenPointerPort = HidLockscreenPointerPort(hidRepository)
    private val entryOrigin = TrackpadEntryOrigin.entries.firstOrNull {
        it.name == savedStateHandle.get<String>(TrackpadEntryActivity.EXTRA_ENTRY_ORIGIN)
    } ?: TrackpadEntryOrigin.Unknown
    private val _events = MutableSharedFlow<LockscreenTrackpadEvent>(extraBufferCapacity = 1)
    val events: Flow<LockscreenTrackpadEvent> = _events

    private val deviceState = flow {
        while (true) {
            emit(readDeviceState())
            delay(750L)
        }
    }.distinctUntilChanged()

    val uiState = combine(
        hidRepository.state,
        trackpadSettingsRepository.settings,
        deviceState,
    ) { hidState, settings, deviceState ->
        val controlState = LockscreenControlState(
            keyguardShowing = deviceState.keyguardShowing,
            deviceLocked = deviceState.deviceLocked,
            userUnlockedSinceBoot = deviceState.userUnlockedSinceBoot,
            hidConnected = hidState.isConnected,
            selectedHostPresent = hidState.selectedHostAddress != null,
            bluetoothPermissionGranted = deviceState.bluetoothPermissionGranted,
            featureEnabled = settings.lockscreenTrackpadEnabled,
            entryOrigin = entryOrigin,
        )
        LockscreenTrackpadUiState(
            controlState = controlState,
            decision = LockscreenTrackpadPolicy.decision(controlState),
            settings = settings,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LockscreenTrackpadUiState(
            controlState = LockscreenControlState(
                keyguardShowing = true,
                deviceLocked = true,
                userUnlockedSinceBoot = false,
                hidConnected = false,
                selectedHostPresent = false,
                bluetoothPermissionGranted = false,
                featureEnabled = false,
                entryOrigin = entryOrigin,
            ),
            decision = LockscreenDecision.RequireUnlock,
            settings = TrackpadSettings(),
        ),
    )

    init {
        viewModelScope.launch {
            var wasPointerAllowed = false
            uiState.collect { state ->
                val pointerAllowed = state.decision == LockscreenDecision.AllowRestrictedPointer
                if (wasPointerAllowed && !pointerAllowed) {
                    pointerPort.releaseButtons()
                    _events.tryEmit(LockscreenTrackpadEvent.Finish)
                }
                wasPointerAllowed = pointerAllowed
            }
        }
    }

    fun move(dx: Float, dy: Float) {
        if (!canDispatch(LockscreenCapability.PointerMove)) return
        pointerAccumulator.consume(dx, dy)?.let { delta ->
            pointerPort.move(delta.dx, delta.dy)
        }
    }

    fun scroll(vertical: Int, horizontal: Int) {
        if (!canDispatch(LockscreenCapability.PointerScroll)) return
        pointerPort.scroll(vertical, horizontal)
    }

    fun click(buttonMask: Int) {
        if (!canDispatch(LockscreenCapability.MouseButton)) return
        pointerPort.click(buttonMask)
    }

    fun press(buttonMask: Int) {
        if (!canDispatch(LockscreenCapability.MouseButton)) return
        pointerPort.press(buttonMask)
    }

    fun releaseButtons() {
        pointerPort.releaseButtons()
    }

    fun requestUnlock() {
        _events.tryEmit(LockscreenTrackpadEvent.RequestUnlock)
    }

    private fun canDispatch(capability: LockscreenCapability): Boolean {
        // Re-read volatile lock/keyguard and permission state at dispatch time;
        // the 750 ms UI polling interval must not create an input window.
        val state = uiState.value
        val device = readDeviceState()
        val current = state.controlState.copy(
            keyguardShowing = device.keyguardShowing,
            deviceLocked = device.deviceLocked,
            userUnlockedSinceBoot = device.userUnlockedSinceBoot,
            hidConnected = hidRepository.state.value.isConnected,
            selectedHostPresent = hidRepository.state.value.selectedHostAddress != null,
            bluetoothPermissionGranted = device.bluetoothPermissionGranted,
        )
        return LockscreenTrackpadPolicy.allows(capability, current)
    }

    private fun readDeviceState(): DeviceStateSnapshot {
        val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
        val userManager = appContext.getSystemService(UserManager::class.java)
        return DeviceStateSnapshot(
            keyguardShowing = keyguardManager?.isKeyguardLocked == true,
            deviceLocked = keyguardManager?.isDeviceLocked == true,
            userUnlockedSinceBoot = userManager?.isUserUnlocked == true,
            bluetoothPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
}

private data class DeviceStateSnapshot(
    val keyguardShowing: Boolean,
    val deviceLocked: Boolean,
    val userUnlockedSinceBoot: Boolean,
    val bluetoothPermissionGranted: Boolean,
)
