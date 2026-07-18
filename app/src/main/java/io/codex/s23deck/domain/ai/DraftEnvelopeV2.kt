package io.codex.s23deck.domain.ai

const val CURRENT_DRAFT_ENVELOPE_VERSION = 2

enum class DraftEnvelopeStatus(val wireName: String) {
    Ready("ready"),
    NeedsInput("needs_input"),
    Unsupported("unsupported"),
    Refused("refused"),
}

class AiDraftProposalUnavailable(
    val status: DraftEnvelopeStatus,
    val userMessage: String,
    val questions: List<String> = emptyList(),
) : IllegalStateException(
    buildString {
        append(userMessage.ifBlank { "AI draft is not ready" })
        if (questions.isNotEmpty()) {
            append(": ")
            append(questions.joinToString())
        }
    },
)

object ApprovedAiActionCatalog {
    val stepTypes: Set<String> = setOf(
        V2StepTypes.OpenUrl,
        V2StepTypes.ClipboardText,
        V2StepTypes.Delay,
        V2StepTypes.Template,
    )

    val templateIds: Set<String> = setOf(
        "mac.focus_ping_30",
        "mac.open_finder",
        "mac.open_terminal",
        "mac.open_spotlight",
        "mac.mission_control",
        "mac.screenshot",
        "mac.full_screen",
        "mac.new_tab",
        "mac.play_pause",
        "mac.volume_up",
        "mac.volume_down",
        "mac.mute",
    )

    fun commandFor(templateId: String): String? =
        when (templateId) {
            "mac.focus_ping_30" -> "caffeinate -u -t 30"
            "mac.open_finder" -> "open -a Finder"
            "mac.open_terminal" -> "open -a Terminal"
            "mac.open_spotlight" -> "osascript -e 'tell application \"System Events\" to key code 49 using command down'"
            "mac.mission_control" -> "osascript -e 'tell application \"System Events\" to key code 126 using control down'"
            "mac.screenshot" -> "osascript -e 'tell application \"System Events\" to key code 20 using {command down, shift down}'"
            "mac.full_screen" -> "osascript -e 'tell application \"System Events\" to key code 3 using {control down, command down}'"
            "mac.new_tab" -> "osascript -e 'tell application \"System Events\" to key code 17 using command down'"
            "mac.play_pause" -> "osascript -e 'tell application \"System Events\" to key code 49'"
            "mac.volume_up" -> "osascript -e 'set volume output volume ((output volume of (get volume settings)) + 10)'"
            "mac.volume_down" -> "osascript -e 'set volume output volume ((output volume of (get volume settings)) - 10)'"
            "mac.mute" -> "osascript -e 'set volume with output muted'"
            else -> null
        }
}

object V2StepTypes {
    const val OpenUrl = "open_url"
    const val ClipboardText = "clipboard_text"
    const val Delay = "delay"
    const val Template = "template"
}
