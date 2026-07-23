package io.codecks.ui.app

import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.LocalActionResult

class LocalActionDispatcher(
    private val onTrackpad: () -> Unit,
    private val onKeyboard: () -> Unit,
    private val onAutomations: () -> Unit,
    private val onClipboard: () -> Unit,
    private val onSettings: () -> Unit,
    private val onEditor: () -> Unit,
    private val onCelebration: (String) -> Unit,
    private val onMissingMacInput: () -> Unit,
    private val onSendMediaPlayPause: () -> Unit,
    private val onSendMediaNext: () -> Unit,
    private val onSendMediaPrevious: () -> Unit,
    private val onUnsupported: () -> Unit,
    private val supportsMacInput: () -> Boolean,
) {
    fun handleAction(action: DeckAction): LocalActionResult? {
        if (action.kind != ActionKind.Local) return null
        return when (action.route) {
            "trackpad" -> {
                onTrackpad()
                LocalActionResult.Navigated
            }
            "keyboard", "text" -> {
                onKeyboard()
                LocalActionResult.Navigated
            }
            "automations" -> {
                onAutomations()
                LocalActionResult.Navigated
            }
            "clipboard" -> {
                onClipboard()
                LocalActionResult.Navigated
            }
            "settings", "setup_scan" -> {
                onSettings()
                LocalActionResult.Navigated
            }
            "button_picker", "empty_slot", "layout_builder" -> {
                onEditor()
                LocalActionResult.Navigated
            }
            "celebrate" -> {
                onCelebration(action.label)
                LocalActionResult.Succeeded
            }
            "hid_media_play_pause" -> {
                when {
                    supportsMacInput() -> {
                        onSendMediaPlayPause()
                        LocalActionResult.Succeeded
                    }
                    else -> {
                        onMissingMacInput()
                        LocalActionResult.Failed("Connect Mac input first")
                    }
                }
            }
            "hid_media_next" -> {
                when {
                    supportsMacInput() -> {
                        onSendMediaNext()
                        LocalActionResult.Succeeded
                    }
                    else -> {
                        onMissingMacInput()
                        LocalActionResult.Failed("Connect Mac input first")
                    }
                }
            }
            "hid_media_previous" -> {
                when {
                    supportsMacInput() -> {
                        onSendMediaPrevious()
                        LocalActionResult.Succeeded
                    }
                    else -> {
                        onMissingMacInput()
                        LocalActionResult.Failed("Connect Mac input first")
                    }
                }
            }
            "decor", "list_apps_panel" -> {
                onUnsupported()
                LocalActionResult.Failed("Unsupported local action")
            }
            else -> {
                onUnsupported()
                LocalActionResult.Failed("Unsupported local action")
            }
        }
    }
}
