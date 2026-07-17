package io.codex.s23deck.domain.ai

data class AiChatMessage(
    val id: String,
    val role: AiChatRole,
    val text: String,
    val artifactId: String? = null,
    val actionId: String? = null,
    val actionLabel: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

enum class AiChatRole {
    User,
    Assistant,
}

data class AiArtifact(
    val id: String,
    val kind: AiArtifactKind,
    val title: String,
    val description: String = "",
    val prompt: String,
    val actions: List<AiArtifactAction>,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastTest: AiArtifactTest? = null,
)

enum class AiArtifactKind {
    Button,
    Deck,
    Automation,
    Clock,
}

data class AiArtifactAction(
    val id: String,
    val title: String,
    val command: String,
    val dangerous: Boolean = false,
)

data class AiArtifactTest(
    val status: AiArtifactTestStatus,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis(),
)

enum class AiArtifactTestStatus {
    Succeeded,
    Failed,
    RequiresConfirmation,
}
