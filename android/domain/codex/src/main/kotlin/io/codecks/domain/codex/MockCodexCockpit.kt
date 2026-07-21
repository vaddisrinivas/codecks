package io.codecks.domain.codex

object MockCodexCockpit {
    fun snapshot(): CodexCockpitSnapshot {
        val tasks = listOf(
            CodexTaskSnapshot(
                id = "task-cockpit-ui",
                title = "Build Codex cockpit",
                repoPath = "~/Projects/codecks",
                branch = "codex/all-waves-trackpad-hid",
                state = CodexTaskState.Running,
                elapsedLabel = "18m",
                updatedLabel = "now",
                effortMode = CodexEffortMode.Deep,
                safeSummary = "Mock dashboard, fancy buttons, and themes are being wired first.",
            ),
            CodexTaskSnapshot(
                id = "task-release",
                title = "Release v0.1.4",
                repoPath = "~/Projects/codecks",
                branch = "main",
                state = CodexTaskState.Released,
                elapsedLabel = "done",
                updatedLabel = "today",
                hasUnread = true,
                effortMode = CodexEffortMode.Standard,
                safeSummary = "APK, tag, and GitHub release were published from local work.",
            ),
            CodexTaskSnapshot(
                id = "task-appfunctions",
                title = "Park AppFunctions",
                repoPath = "~/Projects/codecks/android",
                branch = "main",
                state = CodexTaskState.Blocked,
                elapsedLabel = "parked",
                updatedLabel = "today",
                needsAttention = true,
                effortMode = CodexEffortMode.Unknown,
                safeSummary = "Integration is preserved but paused until official dependency coordinates are confirmed.",
            ),
            CodexTaskSnapshot(
                id = "task-bridge",
                title = "Local bridge protocol",
                repoPath = "~/Projects/codecks",
                branch = "future",
                state = CodexTaskState.Queued,
                elapsedLabel = "next",
                updatedLabel = "planned",
                effortMode = CodexEffortMode.Fast,
                safeSummary = "Sanitized snapshots only; prompt/source content stays out by default.",
            ),
        )

        val buttons = listOf(
            DeckButtonSpec(
                id = "button-confetti",
                slot = 0,
                type = FancyButtonType.Emoji,
                label = "Confetti",
                emoji = "🎉",
                statusLabel = "tap joy",
                themeRole = "success",
                effect = DeckEffectSpec(
                    id = "effect-confetti",
                    kind = DeckEffectKind.ConfettiBurst,
                    trigger = DeckEffectTrigger.Press,
                    emojiSet = listOf("🎉", "✨", "🚀"),
                ),
            ),
            DeckButtonSpec(
                id = "button-emoji-storm",
                slot = 1,
                type = FancyButtonType.Emoji,
                label = "Emoji storm",
                emoji = "✨",
                statusLabel = "chaos",
                themeRole = "arcade",
                effect = DeckEffectSpec(
                    id = "effect-emoji-rain",
                    kind = DeckEffectKind.EmojiRain,
                    trigger = DeckEffectTrigger.Press,
                    emojiSet = listOf("🔥", "💫", "⚡", "🧠"),
                ),
            ),
            DeckButtonSpec(
                id = "button-empty-one",
                slot = 2,
                type = FancyButtonType.Empty,
                label = "Empty magic",
                emoji = "◇",
                statusLabel = "assign later",
                themeRole = "empty",
                effect = DeckEffectSpec(
                    id = "effect-calm",
                    kind = DeckEffectKind.CalmGlow,
                    trigger = DeckEffectTrigger.Press,
                ),
            ),
            DeckButtonSpec(
                id = "button-voice",
                slot = 3,
                type = FancyButtonType.Voice,
                label = "Push talk",
                emoji = "🎙",
                statusLabel = "planned",
                themeRole = "voice",
                effect = DeckEffectSpec(
                    id = "effect-neon",
                    kind = DeckEffectKind.NeonSweep,
                    trigger = DeckEffectTrigger.Press,
                ),
            ),
            DeckButtonSpec(
                id = "button-ci",
                slot = 4,
                type = FancyButtonType.Release,
                label = "CI",
                emoji = "✅",
                statusLabel = "read-only",
                themeRole = "success",
                effect = DeckEffectSpec(
                    id = "effect-spark",
                    kind = DeckEffectKind.SparkTrail,
                    trigger = DeckEffectTrigger.Press,
                ),
            ),
            DeckButtonSpec(
                id = "button-danger",
                slot = 5,
                type = FancyButtonType.Command,
                label = "Stop task",
                emoji = "⛔",
                statusLabel = "guarded",
                themeRole = "danger",
                safetyLevel = FancyButtonSafety.Dangerous,
                effect = DeckEffectSpec(
                    id = "effect-danger",
                    kind = DeckEffectKind.DangerPulse,
                    trigger = DeckEffectTrigger.Press,
                ),
            ),
        )

        return CodexCockpitSnapshot(
            tasks = tasks,
            buttons = buttons,
            themes = themePresets(),
            activeThemeId = "aurora-pixel",
            queueCount = tasks.count { it.state == CodexTaskState.Queued },
            runningCount = tasks.count { it.state == CodexTaskState.Running },
            attentionCount = tasks.count { it.needsAttention || it.hasUnread },
            quotaLabel = "quota hidden until bridge",
        )
    }

    fun themePresets(): List<ThemePresetSpec> = listOf(
        ThemePresetSpec(
            id = "terminal-neon",
            name = "Terminal Neon",
            description = "Phosphor green, amber status, black glass.",
            accent = "#7CFFB2",
            effectPack = listOf(DeckEffectKind.NeonSweep, DeckEffectKind.CalmGlow),
        ),
        ThemePresetSpec(
            id = "arcade-glass",
            name = "Arcade Glass",
            description = "Saturated tiles, bevel glow, playful bursts.",
            accent = "#64F4FF",
            effectPack = listOf(DeckEffectKind.ConfettiBurst, DeckEffectKind.FireworkGrid),
        ),
        ThemePresetSpec(
            id = "studio-console",
            name = "Studio Console",
            description = "Meters, tactile panels, control-room energy.",
            accent = "#FFD166",
            effectPack = listOf(DeckEffectKind.ShockwavePulse, DeckEffectKind.NeonSweep),
        ),
        ThemePresetSpec(
            id = "emoji-carnival",
            name = "Emoji Carnival",
            description = "Sticker-bright buttons and ridiculous joy.",
            accent = "#FF5D7A",
            effectPack = listOf(DeckEffectKind.EmojiRain, DeckEffectKind.ConfettiBurst),
        ),
        ThemePresetSpec(
            id = "focus-minimal",
            name = "Focus Minimal",
            description = "Quiet panels with precise colored status lights.",
            accent = "#E8F8FA",
            effectPack = listOf(DeckEffectKind.CalmGlow),
        ),
        ThemePresetSpec(
            id = "aurora-pixel",
            name = "Aurora Pixel",
            description = "OLED gradients, cyan-green glows, night-sky particles.",
            accent = "#64F4FF",
            effectPack = listOf(DeckEffectKind.SparkTrail, DeckEffectKind.ConfettiBurst),
        ),
    )
}

