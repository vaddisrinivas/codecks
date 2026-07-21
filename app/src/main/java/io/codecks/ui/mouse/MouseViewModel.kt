package io.codecks.ui.mouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codecks.core.trackpad.PointerDeltaAccumulator
import io.codecks.core.trackpad.TrackpadGestureEngine
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadSettingsRepository
import io.codecks.HidCommand
import io.codecks.HidRepository
import io.codecks.HidState
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MouseViewModel @Inject constructor(
    private val hidRepository: HidRepository,
    private val trackpadSettingsRepository: TrackpadSettingsRepository,
) : ViewModel() {
    val state = hidRepository.state
    val settings = trackpadSettingsRepository.settings
    private val pointerAccumulator = PointerDeltaAccumulator()

    fun start() = hidRepository.start()
    fun refreshHosts() = hidRepository.refreshHosts()
    fun connect(address: String) = hidRepository.connect(address)
    fun disconnect() = hidRepository.disconnect()
    fun move(dx: Float, dy: Float) {
        pointerAccumulator.consume(dx, dy)?.let { delta ->
            hidRepository.move(delta.dx, delta.dy)
        }
    }
    fun scroll(vertical: Int) = hidRepository.scroll(vertical)
    fun horizontalScroll(horizontal: Int) = hidRepository.scroll(0, horizontal)
    fun leftClick() = hidRepository.click(1)
    fun rightClick() = hidRepository.click(2)
    fun middleClick() = hidRepository.click(4)
    fun press(buttonMask: Int) = hidRepository.press(buttonMask)
    fun releaseButtons() = hidRepository.releaseButtons()
    fun send(command: HidCommand) = hidRepository.send(command)

    fun updateSettings(transform: (TrackpadSettings) -> TrackpadSettings) {
        viewModelScope.launch {
            trackpadSettingsRepository.update(transform)
        }
    }

    fun markLatestTapWrong() {
        viewModelScope.launch {
            trackpadSettingsRepository.update {
                it.copy(
                    tapCorrectionCount = (it.tapCorrectionCount + 1).coerceAtMost(999),
                    tapMovementThresholdPx = (it.tapMovementThresholdPx - 1f).coerceIn(
                        TrackpadGestureEngine.MIN_TAP_MOVEMENT_THRESHOLD_PX,
                        TrackpadGestureEngine.MAX_TAP_MOVEMENT_THRESHOLD_PX,
                    ),
                    doubleTapTimeoutMillis = (it.doubleTapTimeoutMillis + 20).coerceAtMost(760),
                )
            }
        }
    }
}
