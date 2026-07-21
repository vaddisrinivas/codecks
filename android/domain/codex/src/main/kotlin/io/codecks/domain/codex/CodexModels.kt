package io.codecks.domain.codex

import kotlinx.serialization.Serializable

@Serializable
enum class CodexTaskState {
    Idle,
    Queued,
    Running,
    NeedsAttention,
    Blocked,
    Succeeded,
    Failed,
    Released,
    Offline,
}

@Serializable
enum class CodexEffortMode {
    Fast,
    Standard,
    Deep,
    Unknown,
}

@Serializable
data class CodexTaskSnapshot(
    val id: String,
    val title: String,
    val repoPath: String,
    val branch: String,
    val state: CodexTaskState,
    val elapsedLabel: String,
    val updatedLabel: String,
    val needsAttention: Boolean = false,
    val hasUnread: Boolean = false,
    val effortMode: CodexEffortMode = CodexEffortMode.Unknown,
    val safeSummary: String = "",
    val source: String = "mock",
)

@Serializable
enum class FancyButtonType {
    Empty,
    Emoji,
    CodexTask,
    Command,
    Release,
    Scene,
    Voice,
}

@Serializable
enum class FancyButtonSafety {
    Safe,
    ConfirmationRequired,
    Dangerous,
}

@Serializable
enum class DeckEffectKind {
    None,
    ConfettiBurst,
    EmojiRain,
    SparkTrail,
    ShockwavePulse,
    FireworkGrid,
    NeonSweep,
    DangerPulse,
    CalmGlow,
}

@Serializable
enum class DeckEffectTrigger {
    Press,
    Complete,
    Attention,
    Manual,
}

@Serializable
data class DeckEffectSpec(
    val id: String,
    val kind: DeckEffectKind,
    val trigger: DeckEffectTrigger,
    val intensity: Float = 1f,
    val durationMs: Int = 1200,
    val emojiSet: List<String> = emptyList(),
    val reducedMotionFallback: DeckEffectKind = DeckEffectKind.CalmGlow,
)

@Serializable
data class DeckButtonSpec(
    val id: String,
    val slot: Int,
    val type: FancyButtonType,
    val label: String,
    val symbol: String? = null,
    val emoji: String? = null,
    val statusLabel: String? = null,
    val themeRole: String = "primary",
    val action: String? = null,
    val effect: DeckEffectSpec = DeckEffectSpec(
        id = "effect-none",
        kind = DeckEffectKind.None,
        trigger = DeckEffectTrigger.Press,
    ),
    val safetyLevel: FancyButtonSafety = FancyButtonSafety.Safe,
    val enabled: Boolean = true,
)

@Serializable
data class ThemePresetSpec(
    val id: String,
    val name: String,
    val description: String,
    val accent: String,
    val effectPack: List<DeckEffectKind>,
)

data class CodexCockpitSnapshot(
    val tasks: List<CodexTaskSnapshot>,
    val buttons: List<DeckButtonSpec>,
    val themes: List<ThemePresetSpec>,
    val activeThemeId: String,
    val queueCount: Int,
    val runningCount: Int,
    val attentionCount: Int,
    val quotaLabel: String,
)

