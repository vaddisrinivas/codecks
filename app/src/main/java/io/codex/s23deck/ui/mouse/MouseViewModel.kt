package io.codex.s23deck.ui.mouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codex.s23deck.core.trackpad.PointerDeltaAccumulator
import io.codex.s23deck.core.trackpad.TrackpadSettings
import io.codex.s23deck.core.trackpad.TrackpadSettingsRepository
import io.codex.s23deck.HidCommand
import io.codex.s23deck.HidRepository
import io.codex.s23deck.HidState
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
}
