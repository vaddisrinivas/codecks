package io.codecks.domain.ai

object MacVisualEffectCatalog {
    private val glyphsByBuiltInId = mapOf(
        "confetti" to "🎉,✨,🎊,⭐,💫",
        "sparkle" to "✨,⭐,💫,✦,✧",
        "emoji_heart" to "💚,💖,💕,❤️,✨",
        "emoji_fire" to "🔥,✨,⚡️,💥,⭐",
        "emoji_focus" to "🎯,✨,✅,⚡️,⭐",
        "emoji_coffee" to "☕️,✨,💚,⭐,🌿",
        "magic_blank" to "✨,💫,✦,✧,⭐",
    )
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
        val glyphs = glyphsByBuiltInId[id] ?: return null
        val title = label.toEffectTitle()
        return macOverlayCommand(glyphs = glyphs, title = title)
    }

    /**
     * Trust only byte-for-byte output that this compiler can reproduce. The heredoc marker is
     * public syntax, not provenance, and must never be accepted on its own.
     */
    fun isKnownCommand(command: String?): Boolean {
        command ?: return false
        val titleLine = command.lineSequence()
            .singleOrNull { it.trimStart().startsWith("const effectTitle = '") }
            ?.trim()
            ?: return false
        val title = titleLine.removePrefix("const effectTitle = '").removeSuffix("'")
        if (titleLine != "const effectTitle = '$title'" || title.toEffectTitle() != title) return false
        return glyphsByBuiltInId.values.any { glyphs ->
            command == macOverlayCommand(glyphs = glyphs, title = title)
        }
    }

    private fun String.toEffectTitle(): String =
        filter { it.isLetterOrDigit() || it in " _-!?" }
            .take(32)
            .ifBlank { "Codecks" }

    private fun macOverlayCommand(glyphs: String, title: String): String =
        """
        osascript -l JavaScript <<'CODECKS_VISUAL_EFFECT_V1'
        ObjC.import('Cocoa')
        ObjC.import('CoreGraphics')
        const app = $.NSApplication.sharedApplication
        app.setActivationPolicy($.NSApplicationActivationPolicyAccessory)
        const glyphs = '$glyphs'.split(',')
        const effectTitle = '$title'
        const pieces = []
        const windows = []
        const screens = $.NSScreen.screens
        const level = $.CGWindowLevelForKey($.kCGScreenSaverWindowLevelKey)
        for (let screenIndex = 0; screenIndex < screens.count; screenIndex++) {
          const frame = screens.objectAtIndex(screenIndex).frame
          const window = $.NSWindow.alloc.initWithContentRectStyleMaskBackingDefer(
            frame,
            $.NSWindowStyleMaskBorderless,
            $.NSBackingStoreBuffered,
            false
          )
          window.setOpaque(false)
          window.setBackgroundColor($.NSColor.clearColor)
          window.setIgnoresMouseEvents(true)
          window.setLevel(level)
          window.setCollectionBehavior(
            $.NSWindowCollectionBehaviorCanJoinAllSpaces |
            $.NSWindowCollectionBehaviorFullScreenAuxiliary |
            $.NSWindowCollectionBehaviorStationary
          )
          window.orderFront(null)
          windows.push(window)
          for (let index = 0; index < 96; index++) {
            const view = $.NSTextField.alloc.initWithFrame($.NSMakeRect(
              Math.random() * Math.max(1, frame.size.width - 64),
              frame.size.height + Math.random() * 320,
              64,
              64
            ))
            view.setStringValue(glyphs[index % glyphs.length])
            view.setFont($.NSFont.systemFontOfSize(24 + Math.random() * 22))
            view.setBezeled(false)
            view.setDrawsBackground(false)
            view.setEditable(false)
            view.setSelectable(false)
            view.setAlignment($.NSTextAlignmentCenter)
            window.contentView.addSubview(view)
            pieces.push({
              view: view,
              speed: 4.5 + Math.random() * 8.5,
              sway: 0.8 + Math.random() * 3.2,
              phase: Math.random() * 6.28
            })
          }
        }
        const started = Date.now()
        while (Date.now() - started < 3600) {
          const elapsed = Date.now() - started
          const seconds = elapsed / 1000
          pieces.forEach(piece => {
            const frame = piece.view.frame
            frame.origin.y -= piece.speed
            frame.origin.x += Math.sin(seconds * 4 + piece.phase) * piece.sway
            piece.view.setFrame(frame)
            piece.view.setAlphaValue(Math.max(0, 1 - (elapsed - 2700) / 850))
          })
          $.NSRunLoop.currentRunLoop.runUntilDate($.NSDate.dateWithTimeIntervalSinceNow(0.016))
        }
        windows.forEach(window => window.orderOut(null))
        // CODECKS_VISUAL_EFFECT_V1
        effectTitle
        CODECKS_VISUAL_EFFECT_V1
        """.trimIndent()
}
