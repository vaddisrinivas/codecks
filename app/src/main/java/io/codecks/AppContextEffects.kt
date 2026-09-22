package io.codecks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import kotlinx.coroutines.delay

internal fun Throwable.rethrowIfCancellationOrFatalForUi() {
    when (this) {
        is kotlinx.coroutines.CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        is LinkageError,
        -> throw this
    }
}

internal fun Map<FeatureFlag, Boolean>.focusedEnabled(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)

internal fun DeckAction.visibleForFlags(flags: Map<FeatureFlag, Boolean>): Boolean = when (kind) {
    ActionKind.Ssh -> true
    ActionKind.Local -> id in setOf("add_button", "blank", "blank_spacer", "magic_blank", "confetti", "sparkle", "emoji_heart", "emoji_fire", "emoji_focus", "emoji_coffee") ||
        route in setOf("trackpad", "automations", "ai", "button_picker", "empty_slot", "layout_builder", "celebrate", "decor") ||
        (route in setOf("keyboard", "text") && flags.focusedEnabled(FeatureFlag.Keyboard)) ||
        (route == "clipboard" && flags.focusedEnabled(FeatureFlag.Clipboard)) ||
        (id == "clipboard" && flags.focusedEnabled(FeatureFlag.Clipboard)) ||
        (route == "settings" && flags.focusedEnabled(FeatureFlag.Settings)) ||
        (route == "setup_scan" && flags.focusedEnabled(FeatureFlag.Connection)) || false
}

@Composable
internal fun AppContextEffects(
    reactiveInputs: List<Any?>,
    reactiveTrackpadVisible: Boolean,
    contextDeckPolling: Boolean,
    updateReactiveInputs: suspend () -> Unit,
    setReactiveTrackpadVisible: (Boolean) -> Unit,
    refreshContextDeck: () -> Unit,
) {
    LaunchedEffect(reactiveInputs) { updateReactiveInputs() }
    LaunchedEffect(reactiveTrackpadVisible) { setReactiveTrackpadVisible(reactiveTrackpadVisible) }
    LaunchedEffect(contextDeckPolling) {
        while (contextDeckPolling) {
            refreshContextDeck()
            delay(3_000L)
        }
    }
}
