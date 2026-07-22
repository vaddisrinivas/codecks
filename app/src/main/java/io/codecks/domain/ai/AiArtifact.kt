package io.codecks.domain.ai

data class AiArtifact(
    val id: String,
    val kind: AiArtifactKind,
    val title: String,
    val description: String = "",
    val prompt: String,
    val actions: List<AiArtifactAction>,
    val review: AiArtifactReview = AiArtifactReview(),
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

data class AiArtifactReview(
    val assumptions: List<String> = emptyList(),
    val riskLevel: AiArtifactRiskLevel = AiArtifactRiskLevel.Normal,
    val requiresConfirmation: Boolean = false,
    val riskReason: String? = null,
    val target: String = "Any connected Mac",
    val trigger: String? = null,
    val requiredCapabilities: List<String> = emptyList(),
    val parameters: List<AiArtifactParameter> = emptyList(),
    val steps: List<AiArtifactStepReview> = emptyList(),
)

enum class AiArtifactRiskLevel {
    Normal,
    Dangerous,
}

data class AiArtifactParameter(
    val name: String,
    val label: String,
    val required: Boolean = false,
    val defaultValue: String? = null,
)

data class AiArtifactStepReview(
    val id: String,
    val label: String,
    val type: String,
    val summary: String,
    val requiresConfirmation: Boolean = false,
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
