package io.codex.s23deck.ui.keyboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codex.s23deck.HidCommand
import io.codex.s23deck.HidRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class KeyboardViewModel @Inject constructor(
    private val hidRepository: HidRepository,
) : ViewModel() {
    val hidState = hidRepository.state

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    fun start() = hidRepository.start()
    fun refreshHosts() = hidRepository.refreshHosts()
    fun connect(address: String) = hidRepository.connect(address)
    fun setText(value: String) {
        _text.value = value
    }

    fun clearText() {
        _text.value = ""
    }

    fun typeText() {
        _text.value.takeIf(String::isNotBlank)?.let(hidRepository::typeText)
    }

    fun send(command: HidCommand) = hidRepository.send(command)
}
