package io.codex.s23deck.data.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveAiProviderEnvTest {
    @Test
    fun openAiEnvKey_canReachModelsEndpoint() = runTest {
        val key = System.getenv("OPENAI_API_KEY").orEmpty()
        assumeTrue("OPENAI_API_KEY not set", key.isNotBlank())

        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("openai", SecretValue.of(key))

        OpenAiProvider(keyStore).test().getOrThrow()
    }

    @Test
    fun liteLlmEnvKey_canReachConfiguredProxyWhenBaseUrlIsSet() = runTest {
        val key = System.getenv("LITELLM_MASTER_KEY").orEmpty()
        val baseUrl = System.getenv("LITELLM_BASE_URL").orEmpty()
        assumeTrue("LITELLM_MASTER_KEY or LITELLM_BASE_URL not set", key.isNotBlank() && baseUrl.isNotBlank())

        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("litellm", SecretValue.of(key))

        LiteLlmProvider(keyStore, baseUrl = baseUrl).test().getOrThrow()
    }
}
