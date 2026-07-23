package io.codecks.ui.app

import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.ActionIcon
import io.codecks.domain.LocalActionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalActionDispatcherTest {
    @Test
    fun trackpadNavigationReturnsNavigated() {
        val state = mutableState()
        val action = deckAction("trackpad", "trackpad")

        val result = makeDispatcher(state).handleAction(action)

        assertEquals(LocalActionResult.Navigated, result)
        assertTrue(state.trackpadOpened)
    }

    @Test
    fun keyboardNavigationReturnsNavigated() {
        val state = mutableState()
        val action = deckAction("keyboard", "keyboard")

        val result = makeDispatcher(state).handleAction(action)

        assertEquals(LocalActionResult.Navigated, result)
        assertTrue(state.keyboardOpened)
    }

    @Test
    fun clipboardNavigationReturnsNavigated() {
        val state = mutableState()
        val action = deckAction("clipboard", "clipboard")

        val result = makeDispatcher(state).handleAction(action)

        assertEquals(LocalActionResult.Navigated, result)
        assertTrue(state.clipboardOpened)
    }

    @Test
    fun settingsNavigationReturnsNavigated() {
        val state = mutableState()
        val action = deckAction("settings", "settings")

        val result = makeDispatcher(state).handleAction(action)

        assertEquals(LocalActionResult.Navigated, result)
        assertTrue(state.settingsOpened)
    }

    @Test
    fun mediaActionFailsWithoutMacInput() {
        val state = mutableState()
        val action = deckAction("hid_media_play_pause", "hid_media_play_pause")

        val result = makeDispatcher(state, supportsMacInput = { false }).handleAction(action)

        assertTrue(result is LocalActionResult.Failed)
        assertEquals("Connect Mac input first", (result as LocalActionResult.Failed).message)
        assertTrue(state.missingInputMessageShown)
        assertEquals(0, state.mediaCommandsSent)
    }

    @Test
    fun mediaActionSucceedsWithMacInput() {
        val state = mutableState()
        val action = deckAction("hid_media_next", "hid_media_next")

        val result = makeDispatcher(state).handleAction(action)

        assertEquals(LocalActionResult.Succeeded, result)
        assertEquals(1, state.mediaCommandsSent)
    }

    @Test
    fun rememberedDispatcherReadsLatestMacInputState() {
        val state = mutableState()
        var macInputConnected = false
        val dispatcher = makeDispatcher(state, supportsMacInput = { macInputConnected })
        val action = deckAction("hid_media_next", "hid_media_next")

        val disconnectedResult = dispatcher.handleAction(action)
        macInputConnected = true
        val connectedResult = dispatcher.handleAction(action)

        assertTrue(disconnectedResult is LocalActionResult.Failed)
        assertEquals(LocalActionResult.Succeeded, connectedResult)
        assertEquals(1, state.mediaCommandsSent)
    }

    @Test
    fun celebrationsReportSuccess() {
        val state = mutableState()
        val action = deckAction("celebrate", "celebrate")

        val result = makeDispatcher(state).handleAction(action)

        assertEquals(LocalActionResult.Succeeded, result)
        assertEquals("celebrate", state.celebrationLabel)
    }

    @Test
    fun unsupportedLocalRoutesReportFailure() {
        val state = mutableState()
        val action = deckAction("decor", "decor")

        val result = makeDispatcher(state).handleAction(action)

        assertTrue(result is LocalActionResult.Failed)
        assertEquals("Unsupported local action", (result as LocalActionResult.Failed).message)
    }

    private fun makeDispatcher(
        state: DispatcherState,
        supportsMacInput: () -> Boolean = { true },
    ) = LocalActionDispatcher(
        onTrackpad = { state.trackpadOpened = true },
        onKeyboard = { state.keyboardOpened = true },
        onAutomations = { state.automationsOpened = true },
        onClipboard = { state.clipboardOpened = true },
        onSettings = { state.settingsOpened = true },
        onEditor = { state.editorOpened = true },
        onCelebration = { state.celebrationLabel = it },
        onMissingMacInput = { state.missingInputMessageShown = true },
        onSendMediaPlayPause = { state.mediaCommandsSent += 1 },
        onSendMediaNext = { state.mediaCommandsSent += 1 },
        onSendMediaPrevious = { state.mediaCommandsSent += 1 },
        onUnsupported = { state.unsupportedHandled = true },
        supportsMacInput = supportsMacInput,
    )

    private fun deckAction(id: String, route: String?) = DeckAction(
        id = id,
        label = id,
        kind = ActionKind.Local,
        icon = ActionIcon.Control,
        route = route,
    )

    private data class DispatcherState(
        var trackpadOpened: Boolean = false,
        var keyboardOpened: Boolean = false,
        var automationsOpened: Boolean = false,
        var clipboardOpened: Boolean = false,
        var settingsOpened: Boolean = false,
        var editorOpened: Boolean = false,
        var celebrationLabel: String? = null,
        var missingInputMessageShown: Boolean = false,
        var mediaCommandsSent: Int = 0,
        var unsupportedHandled: Boolean = false,
    )

    private fun mutableState() = DispatcherState()
}
