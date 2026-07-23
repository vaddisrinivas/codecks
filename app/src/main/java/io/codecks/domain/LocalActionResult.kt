package io.codecks.domain

sealed interface LocalActionResult {
    data object Succeeded : LocalActionResult
    data object Navigated : LocalActionResult
    data class Failed(val message: String) : LocalActionResult
}
