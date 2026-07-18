package io.codex.s23deck.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.codex.s23deck.HidCommand
import io.codex.s23deck.HidRepository
import io.codex.s23deck.data.ConnectionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class KeyboardDeliveryMode(val label: String) {
    Auto("Auto"),
    BluetoothTyping("Bluetooth"),
    MacClipboardPaste("Pasteboard"),
}

data class KeyboardUiState(
    val text: String = "",
    val deliveryMode: KeyboardDeliveryMode = KeyboardDeliveryMode.Auto,
    val isSending: Boolean = false,
    val status: String = "Ready",
    val recentSends: List<String> = emptyList(),
    val snippets: List<String> = defaultTextSnippets,
)

@HiltViewModel
class KeyboardViewModel @Inject constructor(
    private val hidRepository: HidRepository,
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {
    val hidState = hidRepository.state

    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState = _uiState.asStateFlow()

    fun start() = hidRepository.start()
    fun refreshHosts() = hidRepository.refreshHosts()
    fun connect(address: String) = hidRepository.connect(address)
    fun setText(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    fun clearText() {
        _uiState.update { it.copy(text = "") }
    }

    fun setDeliveryMode(mode: KeyboardDeliveryMode) {
        _uiState.update { it.copy(deliveryMode = mode) }
    }

    fun useSnippet(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    fun typeText() {
        val snapshot = _uiState.value
        val text = snapshot.text.takeIf(String::isNotBlank) ?: return
        if (snapshot.isSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, status = "Sending…") }
            val result = when (resolvedMode(snapshot.deliveryMode, text)) {
                KeyboardDeliveryMode.BluetoothTyping -> sendViaBluetooth(text)
                KeyboardDeliveryMode.MacClipboardPaste -> sendViaPasteboard(text)
                KeyboardDeliveryMode.Auto -> error("Auto should resolve before sending")
            }
            result
                .onSuccess { message ->
                    _uiState.update {
                        it.copy(
                            status = message,
                            recentSends = (listOf(text) + it.recentSends).distinct().take(MAX_RECENT_SENDS),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(status = error.message ?: "Send failed") }
                }
            _uiState.update { it.copy(isSending = false) }
        }
    }

    fun send(command: HidCommand) = hidRepository.send(command)

    private fun resolvedMode(mode: KeyboardDeliveryMode, text: String): KeyboardDeliveryMode =
        if (mode != KeyboardDeliveryMode.Auto) {
            mode
        } else if (text.isHidTextFriendly() && text.length <= HID_TEXT_LIMIT) {
            KeyboardDeliveryMode.BluetoothTyping
        } else {
            KeyboardDeliveryMode.MacClipboardPaste
        }

    private fun sendViaBluetooth(text: String): Result<String> = runCatching {
        require(hidRepository.state.value.isConnected) { "Bluetooth keyboard is not connected" }
        require(text.isHidTextFriendly()) { "Use Pasteboard mode for emoji, smart quotes, or non-ASCII text" }
        hidRepository.typeText(text)
        "Typed ${text.length} chars over Bluetooth"
    }

    private suspend fun sendViaPasteboard(text: String): Result<String> =
        connectionRepository.writeMacClipboard(text).mapCatching {
            if (hidRepository.state.value.isConnected) {
                hidRepository.send(HidCommand.Paste)
            } else {
                connectionRepository.runCommand(
                    "osascript -e 'tell application \"System Events\" to keystroke \"v\" using command down'",
                ).getOrThrow()
            }
            "Pasted ${text.length} chars into Mac"
        }

    private fun String.isHidTextFriendly(): Boolean =
        all { char -> char == '\n' || char == '\r' || char == '\t' || char.code in 32..126 }

    private companion object {
        const val HID_TEXT_LIMIT = 240
        const val MAX_RECENT_SENDS = 6
    }
}

private val defaultTextSnippets = listOf(
    "ok sounds good",
    "give me one sec",
    "can you send me the link?",
    "I’m joining now",
)
