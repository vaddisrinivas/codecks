package io.codecks.domain.ai

object MacVisualEffectCatalog {
    val templateIds: Set<String> = setOf(
        "codecks.confetti",
        "codecks.sparkle",
        "codecks.love",
        "codecks.fire",
        "codecks.focus",
        "codecks.coffee",
        "codecks.magic",
    )

    fun commandForTemplate(templateId: String): String? =
        when (templateId) {
            "codecks.confetti" -> commandForBuiltIn("confetti", "Confetti")
            "codecks.sparkle" -> commandForBuiltIn("sparkle", "Sparkle")
            "codecks.love" -> commandForBuiltIn("emoji_heart", "Love")
            "codecks.fire" -> commandForBuiltIn("emoji_fire", "Fire")
            "codecks.focus" -> commandForBuiltIn("emoji_focus", "Focus")
            "codecks.coffee" -> commandForBuiltIn("emoji_coffee", "Coffee")
            "codecks.magic" -> commandForBuiltIn("magic_blank", "Magic")
            else -> null
        }

    fun commandForBuiltIn(id: String, label: String): String? {
        val glyphs = when (id) {
            "confetti" -> "🎉,✨,🎊,⭐,💫"
            "sparkle" -> "✨,⭐,💫,✦,✧"
            "emoji_heart" -> "💚,💖,💕,❤️,✨"
            "emoji_fire" -> "🔥,✨,⚡️,💥,⭐"
            "emoji_focus" -> "🎯,✨,✅,⚡️,⭐"
            "emoji_coffee" -> "☕️,✨,💚,⭐,🌿"
            "magic_blank" -> "✨,💫,✦,✧,⭐"
            else -> return null
        }
        val title = label.filterNot { it.isISOControl() }.ifBlank { "Codecks" }
        return macOverlayCommand(glyphs = glyphs, title = title)
    }

    fun isKnownCommand(command: String?): Boolean =
        command != null && command.contains("CODECKS_VISUAL_EFFECT_V1")

    private fun macOverlayCommand(glyphs: String, title: String): String =
        """
        osascript -l JavaScript <<'CODECKS_VISUAL_EFFECT_V1'
        ObjC.import('Cocoa')
        const glyphs = '$glyphs'.split(',')
        const title = '$title'.replace(/'/g, '')
        const screen = $.NSScreen.mainScreen.frame
        const window = $.NSWindow.alloc.initWithContentRectStyleMaskBackingDefer(
          screen,
          $.NSBorderlessWindowMask,
          $.NSBackingStoreBuffered,
          false
        )
        window.level = $.NSScreenSaverWindowLevel
        window.opaque = false
        window.backgroundColor = $.NSColor.clearColor
        window.ignoresMouseEvents = true
        window.collectionBehavior = $.NSWindowCollectionBehaviorCanJoinAllSpaces | $.NSWindowCollectionBehaviorFullScreenAuxiliary
        const root = $.NSView.alloc.initWithFrame(screen)
        window.contentView = root
        const makeLabel = (text, size, x, y) => {
          const label = $.NSTextField.alloc.initWithFrame($.NSMakeRect(x, y, 90, 90))
          label.stringValue = text
          label.font = $.NSFont.systemFontOfSize(size)
          label.drawsBackground = false
          label.bezeled = false
          label.editable = false
          label.selectable = false
          label.alignment = $.NSCenterTextAlignment
          root.addSubview(label)
          return label
        }
        makeLabel(title, 28, screen.size.width / 2 - 180, screen.size.height / 2 - 32)
        for (let i = 0; i < 70; i++) {
          const glyph = glyphs[i % glyphs.length]
          makeLabel(glyph, 28 + (i % 6) * 4, Math.random() * screen.size.width, screen.size.height - Math.random() * 160)
        }
        window.makeKeyAndOrderFront(null)
        const app = $.NSApplication.sharedApplication
        const deadline = Date.now() + 1600
        while (Date.now() < deadline) {
          app.nextEventMatchingMaskUntilDateInModeDequeue($.NSAnyEventMask, $.NSDate.dateWithTimeIntervalSinceNow(0.05), $.NSDefaultRunLoopMode, true)
        }
        window.orderOut(null)
        CODECKS_VISUAL_EFFECT_V1
        """.trimIndent()
}
