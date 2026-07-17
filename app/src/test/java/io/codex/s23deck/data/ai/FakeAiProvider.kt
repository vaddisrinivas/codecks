package io.codex.s23deck.data.ai

import io.codex.s23deck.domain.ai.ActionDraftJson
import io.codex.s23deck.domain.ai.AiModel
import io.codex.s23deck.domain.ai.AiProvider
import io.codex.s23deck.domain.ai.DraftRequest

class FakeAiProvider(
    private val outcome: Outcome = Outcome.Success(ActionDraftJson("{}")),
    private val models: List<AiModel> = listOf(AiModel("fake-model", "Fake Model")),
) : AiProvider {
    override suspend fun listModels() = Result.success(models)
    override suspend fun test(): Result<Unit> = when (outcome) {
        Outcome.AuthFailure -> Result.failure(AiProviderException.AuthFailure("Auth failed"))
        Outcome.Timeout -> Result.failure(AiProviderException.Timeout("Timed out"))
        else -> Result.success(Unit)
    }
    override suspend fun draftAction(request: DraftRequest): Result<ActionDraftJson> = when (outcome) {
        is Outcome.Success -> Result.success(outcome.draft)
        Outcome.MalformedJson -> Result.failure(AiProviderException.MalformedJson("Malformed JSON"))
        Outcome.RateLimit -> Result.failure(AiProviderException.RateLimited("Rate limited"))
        Outcome.AuthFailure -> Result.failure(AiProviderException.AuthFailure("Auth failed"))
        Outcome.Timeout -> Result.failure(AiProviderException.Timeout("Timed out"))
        Outcome.UnsupportedModel -> Result.failure(AiProviderException.UnsupportedModel("Unsupported model"))
    }
    sealed interface Outcome {
        data class Success(val draft: ActionDraftJson) : Outcome
        data object MalformedJson : Outcome
        data object RateLimit : Outcome
        data object AuthFailure : Outcome
        data object Timeout : Outcome
        data object UnsupportedModel : Outcome
    }
}

class InMemorySecureApiKeyStore : SecureApiKeyStore {
    private val keys = mutableMapOf<String, SecretValue>()
    override suspend fun hasKey(providerId: String) = keys.containsKey(providerId)
    override suspend fun saveKey(providerId: String, key: SecretValue) { keys[providerId] = key }
    override suspend fun loadKey(providerId: String) = keys[providerId]
    override suspend fun deleteKey(providerId: String) { keys.remove(providerId) }
}

