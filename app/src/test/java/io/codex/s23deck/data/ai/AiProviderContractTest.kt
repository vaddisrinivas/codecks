package io.codex.s23deck.data.ai

import io.codex.s23deck.domain.ai.ActionDraftJson
import io.codex.s23deck.domain.ai.DraftKind
import io.codex.s23deck.domain.ai.DraftRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderContractTest {
    @Test
    fun fakeProvider_coversSuccessAndFailureModes() = runTest {
        assertEquals(
            """{"ok":true}""",
            FakeAiProvider(FakeAiProvider.Outcome.Success(ActionDraftJson("""{"ok":true}""")))
                .draftAction(DraftRequest("make action", "fake-model"))
                .getOrThrow()
                .json,
        )

        val outcomes = listOf(
            FakeAiProvider.Outcome.MalformedJson,
            FakeAiProvider.Outcome.RateLimit,
            FakeAiProvider.Outcome.AuthFailure,
            FakeAiProvider.Outcome.Timeout,
            FakeAiProvider.Outcome.UnsupportedModel,
        )

        outcomes.forEach { outcome ->
            assertTrue(FakeAiProvider(outcome).draftAction(DraftRequest("make action", "fake-model")).isFailure)
        }
    }

    @Test
    fun openAiProvider_usesHttpValidationAndStructuredDraftRequest() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        val httpClient =
            FakeAiHttpClient(
                responses =
                    mutableListOf(
                        AiHttpResponse(200, """{"data":[{"id":"gpt-5-mini"}]}"""),
                        AiHttpResponse(
                            200,
                            """{"choices":[{"message":{"content":"{\"prompt\":\"make action\",\"definition\":{\"id\":\"draft.open\",\"title\":\"Open Docs\",\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"url\":\"https://example.com\"}]}}"}}]}""",
                        ),
                    ),
            )
        val provider = OpenAiProvider(keyStore, httpClient)

        assertTrue(provider.test().isFailure)
        keyStore.saveKey("openai", SecretValue.of("secret"))
        provider.test().getOrThrow()
        val json = provider.draftAction(
            DraftRequest(
                "make action",
                "gpt-5-mini",
                DraftKind.Action,
                agentContext = "Bundled Codecks AI Agent test context",
            ),
        ).getOrThrow().json
        assertTrue(json.contains("Open Docs"))

        val payload = httpClient.requests.last().body.orEmpty()
        assertTrue(payload.contains("\"response_format\""))
        assertTrue(payload.contains("\"json_schema\""))
        assertTrue(payload.contains("\"minItems\":1"))
        assertTrue(payload.contains("Bundled Codecks AI Agent test context"))
        assertFalse(payload.contains("secret"))
    }

    @Test
    fun unsupportedModel_isRejectedBeforeNetwork() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("openai", SecretValue.of("secret"))
        val httpClient = FakeAiHttpClient(mutableListOf())
        val provider = OpenAiProvider(keyStore, httpClient)

        val result = provider.draftAction(DraftRequest("make action", "not-a-real-model"))

        assertTrue(result.isFailure)
        assertTrue(httpClient.requests.isEmpty())
    }

    @Test
    fun liteLlmProvider_usesConfiguredOpenAiCompatibleBaseUrl() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("litellm", SecretValue.of("secret"))
        val httpClient =
            FakeAiHttpClient(
                responses =
                    mutableListOf(
                        AiHttpResponse(200, """{"data":[{"id":"gpt-5-mini"}]}"""),
                        AiHttpResponse(
                            200,
                            """{"choices":[{"message":{"content":"{\"prompt\":\"make deck\",\"id\":\"deck\",\"title\":\"Deck\",\"actions\":[{\"id\":\"open\",\"title\":\"Open\",\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"url\":\"https://example.com\"}]}]}"}}]}""",
                        ),
                    ),
            )
        val provider = LiteLlmProvider(keyStore, httpClient, "http://127.0.0.1:4000")

        provider.test().getOrThrow()
        provider.draftAction(DraftRequest("make deck", "gpt-5-mini", DraftKind.Deck)).getOrThrow()

        assertEquals("http://127.0.0.1:4000/v1/models", httpClient.requests.first().url)
        assertEquals("http://127.0.0.1:4000/v1/chat/completions", httpClient.requests.last().url)
        assertFalse(httpClient.requests.last().body.orEmpty().contains("secret"))
    }

    @Test
    fun geminiProvider_sendsApiKeyInHeaderNotQuery() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("gemini", SecretValue.of("secret"))
        val httpClient =
            FakeAiHttpClient(
                responses =
                    mutableListOf(
                        AiHttpResponse(200, """{"models":[{"name":"models/gemini-2.5-flash"}]}"""),
                        AiHttpResponse(
                            200,
                            """{"candidates":[{"content":{"parts":[{"text":"{\"prompt\":\"make action\",\"definition\":{\"id\":\"draft.open\",\"title\":\"Open Docs\",\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"url\":\"https://example.com\"}]}}"}]}}]}""",
                        ),
                    ),
            )
        val provider = GeminiProvider(keyStore, httpClient)

        provider.test().getOrThrow()
        provider.draftAction(DraftRequest("make action", "gemini-2.5-flash")).getOrThrow()

        assertFalse(httpClient.requests.first().url.contains("key="))
        assertEquals("secret", httpClient.requests.first().headers["x-goog-api-key"])
        assertFalse(httpClient.requests.last().url.contains("key="))
        assertEquals("secret", httpClient.requests.last().headers["x-goog-api-key"])
        assertFalse(httpClient.requests.last().body.orEmpty().contains("secret"))
    }

    @Test
    fun secretValue_isRedactedWhenStringified() {
        assertEquals(SecretValue.REDACTED, SecretValue.of("secret").toString())
    }
}

private class FakeAiHttpClient(
    val responses: MutableList<AiHttpResponse>,
) : AiHttpClient {
    val requests = mutableListOf<AiHttpRequest>()

    override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
        requests += request
        return responses.removeAt(0)
    }
}
