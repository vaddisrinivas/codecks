package io.codecks.domain.codex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object CodexBridgeSnapshotParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(rawJson: String): CodexCockpitSnapshot {
        val bridge = json.decodeFromString(BridgeSnapshot.serializer(), rawJson)
        require(!bridge.privacy.promptContentIncluded) { "Prompt content is not allowed in default cockpit snapshots." }
        require(!bridge.privacy.sourceContentIncluded) { "Source content is not allowed in default cockpit snapshots." }

        val tasks = bridge.tasks.map { it.toDomain(source = bridge.source) }
        return CodexCockpitSnapshot(
            tasks = tasks,
            buttons = MockCodexCockpit.snapshot().buttons,
            themes = MockCodexCockpit.themePresets(),
            activeThemeId = MockCodexCockpit.snapshot().activeThemeId,
            queueCount = tasks.count { it.state == CodexTaskState.Queued },
            runningCount = tasks.count { it.state == CodexTaskState.Running },
            attentionCount = tasks.count { it.needsAttention || it.hasUnread },
            quotaLabel = "bridge status-only",
        )
    }
}

@Serializable
private data class BridgeSnapshot(
    val version: Int,
    val generatedAt: String,
    val source: String,
    val privacy: BridgePrivacy,
    val tasks: List<BridgeTask>,
)

@Serializable
private data class BridgePrivacy(
    val promptContentIncluded: Boolean,
    val sourceContentIncluded: Boolean,
    val notes: String = "",
)

@Serializable
private data class BridgeTask(
    val id: String,
    val title: String,
    val repoPath: String,
    val branch: String,
    val state: CodexTaskState,
    val elapsedLabel: String,
    val updatedLabel: String,
    val needsAttention: Boolean,
    val hasUnread: Boolean,
    val effortMode: CodexEffortMode = CodexEffortMode.Unknown,
    val safeSummary: String,
) {
    fun toDomain(source: String): CodexTaskSnapshot = CodexTaskSnapshot(
        id = id,
        title = title,
        repoPath = repoPath,
        branch = branch,
        state = state,
        elapsedLabel = elapsedLabel,
        updatedLabel = updatedLabel,
        needsAttention = needsAttention,
        hasUnread = hasUnread,
        effortMode = effortMode,
        safeSummary = safeSummary,
        source = source,
    )
}

