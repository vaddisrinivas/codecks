package io.codecks.data.ai

data class ImportedAiCredential(
    val providerId: String,
    val key: SecretValue,
    val baseUrl: String?,
    val source: String,
)

interface AiCredentialImporter {
    suspend fun importCredential(providerId: String): Result<ImportedAiCredential>
}
