package io.codecks.domain.decks

import io.codecks.domain.targets.TargetSelection

object DefaultDeckFactory {
    fun mainDeck(target: TargetSelection = TargetSelection.Current): Deck = Deck(
        id = "deck-main",
        name = "Main Deck",
        styleRef = "luminous-oled-v1",
        defaultTarget = target,
        pages = listOf(
            DeckPage(
                id = "page-main",
                name = "Main",
                grid = KeyGrid(columns = 4, rows = 5),
                buttons = listOf(
                    button("finder", 0, 0, "Finder", "Folder", "mac.finder.open", target),
                    button("terminal", 1, 0, "Term", "Terminal", "mac.terminal.open", target),
                    button("spotlight", 2, 0, "Spot", "Search", "mac.spotlight.open", target),
                    button("screenshot", 3, 0, "Shot", "Crop", "mac.screenshot.capture", target),
                    button("mission", 0, 1, "Mission", "Grid", "mac.mission-control.show", target),
                    button("space-left", 1, 1, "Space-", "ArrowLeft", "mac.space.left", target),
                    button("space-right", 2, 1, "Space+", "ArrowRight", "mac.space.right", target),
                    button("full-screen", 3, 1, "Full", "Maximize", "mac.window.full-screen", target),
                    button("prev-app", 0, 2, "Prev", "SkipBack", "mac.app.previous", target),
                    button("next-app", 1, 2, "Next", "SkipForward", "mac.app.next", target),
                    button("new-tab", 2, 2, "NewTab", "Plus", "mac.browser.new-tab", target),
                    button("play", 3, 2, "Play", "Play", "mac.media.play-pause", target),
                    button("mute", 0, 3, "Mute", "VolumeOff", "mac.media.mute", target),
                    button("volume-down", 1, 3, "Vol-", "VolumeDown", "mac.media.volume-down", target),
                    button("volume-up", 2, 3, "Vol+", "VolumeUp", "mac.media.volume-up", target),
                    button("lock", 3, 3, "Lock", "Lock", "mac.lock", target),
                    button(
                        id = "trackpad",
                        column = 0,
                        row = 4,
                        label = "Trackpad",
                        glyph = "Touchpad",
                        actionId = "android.trackpad.open",
                        target = TargetSelection.Current,
                        span = KeySpan(columns = 3),
                    ),
                    button("keyboard", 3, 4, "Keys", "Keyboard", "android.keyboard.open", TargetSelection.Current),
                ),
            ),
        ),
    )

    private fun button(
        id: String,
        column: Int,
        row: Int,
        label: String,
        glyph: String,
        actionId: String,
        target: TargetSelection,
        span: KeySpan = KeySpan(),
    ): DeckButton = DeckButton(
        id = "button-$id",
        position = KeyPosition(column, row),
        span = span,
        presentation = ButtonPresentation(label = label, glyph = glyph),
        actionRef = ActionRef(stableId = actionId),
        targetRef = target,
    )
}
