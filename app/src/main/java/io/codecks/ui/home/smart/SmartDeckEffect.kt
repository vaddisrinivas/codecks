package io.codecks.ui.home.smart

sealed interface SmartDeckEffect {
    data class Execute(val request: SmartRunRequest) : SmartDeckEffect
    data class Pin(val suggestion: SmartDeckSuggestionUi) : SmartDeckEffect
    data class ShowExplanation(val confidence: String, val reason: String) : SmartDeckEffect
    data class ConfirmDangerousSuggestion(val suggestion: SmartDeckSuggestionUi) : SmartDeckEffect
}
