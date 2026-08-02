package io.codecks.ui.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex

enum class AccessibilityFocusProblem {
    None,
    Invalid,
    Blocking,
}

data class AccessibilityReflowPolicy(
    val stackControls: Boolean,
    val scrollLongContent: Boolean,
    val minimumTargetDp: Int = 48,
)

fun accessibilityReflowPolicy(fontScale: Float): AccessibilityReflowPolicy {
    require(fontScale.isFinite() && fontScale > 0f)
    val enlargedText = fontScale >= 2f
    return AccessibilityReflowPolicy(
        stackControls = enlargedText,
        scrollLongContent = enlargedText,
    )
}

/**
 * A stable failure key prevents recomposition/polling from repeatedly stealing focus.
 * Leaving the failure state resets [previousFailureKey] to null, so a later failure can focus again.
 */
fun shouldRequestBlockingFailureFocus(
    previousFailureKey: String?,
    currentFailureKey: String?,
): Boolean = currentFailureKey != null && currentFailureKey != previousFailureKey

data class AccessibilityFocusCandidate<T>(
    val key: String,
    val problem: AccessibilityFocusProblem,
    val target: T,
) {
    init {
        require(key.isNotBlank())
    }
}

fun <T> firstAccessibilityProblem(
    candidatesInVisualOrder: List<AccessibilityFocusCandidate<T>>,
): AccessibilityFocusCandidate<T>? = candidatesInVisualOrder.firstOrNull {
    it.problem == AccessibilityFocusProblem.Invalid ||
        it.problem == AccessibilityFocusProblem.Blocking
}

fun <T> requestFirstAccessibilityProblem(
    candidatesInVisualOrder: List<AccessibilityFocusCandidate<T>>,
    requestFocus: (T) -> Boolean,
): Boolean {
    val firstProblem = firstAccessibilityProblem(candidatesInVisualOrder) ?: return false
    return requestFocus(firstProblem.target)
}

data class ComposeAccessibilityFocusTarget(
    val key: String,
    val problem: AccessibilityFocusProblem,
    val focusRequester: FocusRequester,
)

/**
 * Call after validation/action failure. Visual list order is the deterministic focus order.
 */
fun requestFirstAccessibilityProblem(
    targetsInVisualOrder: List<ComposeAccessibilityFocusTarget>,
): Boolean = requestFirstAccessibilityProblem(
    candidatesInVisualOrder = targetsInVisualOrder.map {
        AccessibilityFocusCandidate(it.key, it.problem, it.focusRequester)
    },
    requestFocus = FocusRequester::requestFocus,
)

fun Modifier.accessibilityFocusTarget(
    focusRequester: FocusRequester,
    traversalOrder: Float,
): Modifier = focusRequester(focusRequester)
    .semantics { traversalIndex = traversalOrder }

fun Modifier.accessibilityTraversalOrder(order: Float): Modifier =
    semantics { traversalIndex = order }
