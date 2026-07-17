package io.codex.s23deck.domain.ai

data class AiGenerationRecord(
    val id: String,
    val providerId: String,
    val providerLabel: String,
    val modelId: String,
    val modelLabel: String,
    val draftKind: DraftKind,
    val status: AiGenerationStatus,
    val message: String,
    val validationErrors: List<String> = emptyList(),
    val artifactId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

enum class AiGenerationStatus {
    Ready,
    NeedsInput,
    Unsupported,
    Refused,
    Failed,
}
