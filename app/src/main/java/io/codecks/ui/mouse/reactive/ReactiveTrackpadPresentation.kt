package io.codecks.ui.mouse.reactive

import io.codecks.domain.reactive.ReactiveActionResult

private const val VisibleReactiveControlLimit = 4

data class ReactiveTrackpadPresentation(
    val summary: String,
    val resultMessage: String?,
    val visibleControls: List<ReactiveControlUi>,
    val overflowControls: List<ReactiveControlUi>,
) {
    val hasOverflow: Boolean get() = overflowControls.isNotEmpty()
}

fun ReactiveTrackpadUiState.present(): ReactiveTrackpadPresentation = ReactiveTrackpadPresentation(
    summary = when {
        loading -> "Refreshing Mac context."
        macState == null -> "Choose a Mac first. Controls appear from the current front app."
        controls.isNotEmpty() && overflowCount() > 0 -> "Showing the top app-aware controls. Open More for the rest."
        controls.isNotEmpty() -> "Temporary app-aware controls. Nothing runs until you tap."
        macState.frontApp.value == null -> "No front app signal yet. Refresh to pick up the current Mac app."
        else -> "No quick controls for this app yet."
    },
    resultMessage = when (val result = lastResult) {
        is ReactiveActionResult.Succeeded -> "Done"
        is ReactiveActionResult.Failed -> "Could not run that control"
        is ReactiveActionResult.RequiresReview -> "That control still needs review"
        is ReactiveActionResult.Unsupported -> "That control is not wired yet"
        ReactiveActionResult.Expired -> "That control expired"
        is ReactiveActionResult.RequiresConfirmation,
        null,
        -> null
    },
    visibleControls = controls.take(VisibleReactiveControlLimit),
    overflowControls = controls.drop(VisibleReactiveControlLimit),
)

fun ReactiveTrackpadUiState.overflowCount(): Int =
    (controls.size - VisibleReactiveControlLimit).coerceAtLeast(0)
