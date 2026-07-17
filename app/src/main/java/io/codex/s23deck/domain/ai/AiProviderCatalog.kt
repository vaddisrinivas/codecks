package io.codex.s23deck.domain.ai

enum class AiProviderId {
    OpenAI,
    Anthropic,
    OpenRouter,
    LiteLLM,
    Gemini,
}

data class AiProviderSpec(
    val id: AiProviderId,
    val providerId: String,
    val label: String,
    val models: List<AiModel>,
)

object AiProviderCatalog {
    const val DefaultProviderId = "openai"
    const val DefaultModelId = "gpt-5.5"

    val openAi =
        AiProviderSpec(
            id = AiProviderId.OpenAI,
            providerId = "openai",
            label = "OpenAI",
            models = listOf(AiModel("gpt-5.5", "GPT-5.5"), AiModel("gpt-5-mini", "GPT-5 Mini"), AiModel("gpt-5", "GPT-5")),
        )

    val anthropic =
        AiProviderSpec(
            id = AiProviderId.Anthropic,
            providerId = "anthropic",
            label = "Anthropic",
            models = listOf(
                AiModel("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                AiModel("claude-haiku-4-6", "Claude Haiku 4.6"),
            ),
        )

    val openRouter =
        AiProviderSpec(
            id = AiProviderId.OpenRouter,
            providerId = "openrouter",
            label = "OpenRouter",
            models = listOf(
                AiModel("openrouter/auto", "OpenRouter Auto"),
                AiModel("anthropic/claude-sonnet-4.6", "Claude Sonnet via OpenRouter"),
            ),
        )

    val liteLlm =
        AiProviderSpec(
            id = AiProviderId.LiteLLM,
            providerId = "litellm",
            label = "LiteLLM",
            models = listOf(
                AiModel("gpt-5.5", "GPT-5.5"),
                AiModel("openai/gpt-5.5", "OpenAI GPT-5.5"),
                AiModel("gpt-5-mini", "GPT-5 Mini"),
                AiModel("gpt-5", "GPT-5"),
                AiModel("openai/gpt-5-mini", "OpenAI GPT-5 Mini"),
                AiModel("azure/gpt-4.1-mini", "Azure GPT-4.1 Mini"),
            ),
        )

    val gemini =
        AiProviderSpec(
            id = AiProviderId.Gemini,
            providerId = "gemini",
            label = "Gemini",
            models = listOf(
                AiModel("gemini-2.5-flash", "Gemini 2.5 Flash"),
                AiModel("gemini-2.5-pro", "Gemini 2.5 Pro"),
            ),
        )

    val all: List<AiProviderSpec> = listOf(openAi, anthropic, openRouter, liteLlm, gemini)

    fun byProviderId(providerId: String): AiProviderSpec? = all.firstOrNull { it.providerId == providerId }

    fun defaultProvider(): AiProviderSpec = byProviderId(DefaultProviderId) ?: openAi

    fun defaultModelId(providerId: String = DefaultProviderId): String =
        byProviderId(providerId)
            ?.models
            ?.firstOrNull { it.id == DefaultModelId }
            ?.id
            ?: byProviderId(providerId)?.models?.firstOrNull()?.id
            ?: defaultProvider().models.first().id
}
