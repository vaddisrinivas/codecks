package io.codecks.domain.ai

import kotlinx.serialization.Serializable

@Serializable
enum class AiArtifactType {
    BUTTON,
    DECK,
    AUTOMATION,
}

@Serializable
enum class AiProviderKind {
    OPENAI,
    ANTHROPIC,
    OPENROUTER,
    LITELLM,
    GEMINI,
}

@Serializable
data class AiModelOption(
    val provider: AiProviderKind,
    val modelId: String,
    val displayName: String,
    val available: Boolean,
)

@Serializable
data class AiDraft(
    val schemaVersion: Int,
    val artifactType: AiArtifactType,
    val sourcePrompt: String,
    val provider: AiProviderKind,
    val modelId: String,
    val redactedPayload: String,
    val validatedArtifactJson: String?,
    val warnings: List<String> = emptyList(),
)

