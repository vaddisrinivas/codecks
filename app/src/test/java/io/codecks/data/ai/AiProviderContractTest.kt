package io.codecks.data.ai

import io.codecks.domain.ai.ActionDraftJson
import io.codecks.domain.ai.DraftKind
import io.codecks.domain.ai.DraftRequest
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
                            """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"schemaVersion\":2,\"status\":\"ready\",\"message\":\"Ready\",\"questions\":[],\"assumptions\":[],\"proposal\":{\"id\":\"draft.open\",\"title\":\"Open Docs\",\"description\":\"Open documentation\",\"requiredCapabilities\":[],\"target\":{\"type\":\"AnyConnected\",\"id\":null},\"safety\":{\"level\":\"Normal\",\"requiresConfirmation\":false,\"confirmationTitle\":null,\"confirmationBody\":null},\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"label\":\"Open docs\",\"url\":\"https://example.com\",\"text\":null,\"delayMs\":null,\"templateId\":null,\"requiresConfirmation\":false}]}}"}]}]}""",
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
        assertEquals("https://api.openai.com/v1/responses", httpClient.requests.last().url)
        assertTrue(payload.contains("\"text\""))
        assertTrue(payload.contains("\"json_schema\""))
        assertTrue(payload.contains("\"minItems\":1"))
        assertTrue(payload.contains("Bundled Codecks AI Agent test context"))
        assertTrue(payload.contains("\"schemaVersion\""))
        assertTrue(payload.contains("\"proposal\""))
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
    fun openAiProvider_surfacesRefusalAndIncompleteResponses() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("openai", SecretValue.of("secret"))
        val provider = OpenAiProvider(
            keyStore,
            FakeAiHttpClient(
                mutableListOf(
                    AiHttpResponse(
                        200,
                        """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"Cannot help with that"}]}]}""",
                    ),
                    AiHttpResponse(200, """{"status":"incomplete","output":[]}"""),
                ),
            ),
        )

        assertTrue(provider.draftAction(DraftRequest("bad request", "gpt-5.5")).exceptionOrNull() is AiProviderException.Refused)
        assertTrue(provider.draftAction(DraftRequest("long request", "gpt-5.5")).exceptionOrNull() is AiProviderException.Incomplete)
    }

    @Test
    fun openAiProvider_mapsProviderFailureFixtures() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("openai", SecretValue.of("secret"))
        val provider = OpenAiProvider(
            keyStore,
            FakeAiHttpClient(
                mutableListOf(
                    AiHttpResponse(429, """{"error":"rate"}"""),
                    AiHttpResponse(408, """{"error":"timeout"}"""),
                    AiHttpResponse(500, """{"error":"server"}"""),
                    AiHttpResponse(200, """{"status":"completed","output":[]}"""),
                ),
            ),
        )

        assertTrue(provider.draftAction(DraftRequest("rate", "gpt-5.5")).exceptionOrNull() is AiProviderException.RateLimited)
        assertTrue(provider.draftAction(DraftRequest("timeout", "gpt-5.5")).exceptionOrNull() is AiProviderException.Timeout)
        assertTrue(provider.draftAction(DraftRequest("server", "gpt-5.5")).exceptionOrNull() is AiProviderException.RemoteFailure)
        assertTrue(provider.draftAction(DraftRequest("malformed", "gpt-5.5")).exceptionOrNull() is AiProviderException.MalformedJson)
    }

    @Test
    fun openAiProvider_includesRepairInstructionsWithoutSecrets() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("openai", SecretValue.of("secret"))
        val httpClient =
            FakeAiHttpClient(
                responses =
                    mutableListOf(
                        AiHttpResponse(
                            200,
                            """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"schemaVersion\":2,\"status\":\"needs_input\",\"message\":\"Need detail\",\"questions\":[\"Which app?\"],\"assumptions\":[],\"proposal\":null}"}]}]}""",
                        ),
                    ),
            )
        val provider = OpenAiProvider(keyStore, httpClient)

        provider.draftAction(
            DraftRequest(
                prompt = "repair",
                modelId = "gpt-5.5",
                repairInstructions = "steps[0].url: URL must be absolute http or https",
            ),
        ).getOrThrow()

        val payload = httpClient.requests.single().body.orEmpty()
        assertTrue(payload.contains("Repair instructions"))
        assertTrue(payload.contains("steps[0].url"))
        assertFalse(payload.contains("secret"))
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
                            """{"choices":[{"message":{"content":"{\"schemaVersion\":2,\"status\":\"ready\",\"message\":\"Ready\",\"questions\":[],\"assumptions\":[],\"proposal\":{\"id\":\"deck\",\"title\":\"Deck\",\"description\":\"Docs deck\",\"actions\":[{\"id\":\"open\",\"title\":\"Open\",\"description\":\"Open docs\",\"requiredCapabilities\":[],\"target\":{\"type\":\"AnyConnected\",\"id\":null},\"safety\":{\"level\":\"Normal\",\"requiresConfirmation\":false,\"confirmationTitle\":null,\"confirmationBody\":null},\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"label\":\"Open docs\",\"url\":\"https://example.com\",\"text\":null,\"delayMs\":null,\"templateId\":null,\"requiresConfirmation\":false}]}}}"}}]}""",
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
    fun anthropicProvider_usesForcedStructuredToolResult() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("anthropic", SecretValue.of("secret"))
        val httpClient =
            FakeAiHttpClient(
                responses =
                    mutableListOf(
                        AiHttpResponse(200, """{"data":[{"id":"claude-sonnet-4-6"}]}"""),
                        AiHttpResponse(
                            200,
                            """{"content":[{"type":"tool_use","id":"toolu_1","name":"emit_ai_creator_v2_draft","input":{"schemaVersion":2,"status":"ready","message":"Ready","questions":[],"assumptions":[],"proposal":{"id":"draft.open","title":"Open Docs","description":"Open documentation","requiredCapabilities":[],"target":{"type":"AnyConnected","id":null},"safety":{"level":"Normal","requiresConfirmation":false,"confirmationTitle":null,"confirmationBody":null},"steps":[{"id":"step-1","type":"open_url","label":"Open docs","url":"https://example.com","text":null,"delayMs":null,"templateId":null,"requiresConfirmation":false}]}}}]}""",
                        ),
                    ),
            )
        val provider = AnthropicProvider(keyStore, httpClient)

        provider.test().getOrThrow()
        val json = provider.draftAction(DraftRequest("make action", "claude-sonnet-4-6")).getOrThrow().json

        assertTrue(json.contains("\"schemaVersion\":2"))
        val payload = httpClient.requests.last().body.orEmpty()
        assertTrue(payload.contains("\"tools\""))
        assertTrue(payload.contains("\"input_schema\""))
        assertTrue(payload.contains("\"tool_choice\""))
        assertTrue(payload.contains("emit_ai_creator_v2_draft"))
        assertFalse(payload.contains("secret"))
    }

    @Test
    fun anthropicProvider_rejectsMissingDraftToolResult() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("anthropic", SecretValue.of("secret"))
        val provider = AnthropicProvider(
            keyStore,
            FakeAiHttpClient(
                mutableListOf(
                    AiHttpResponse(200, """{"content":[{"type":"text","text":"not a tool call"}]}"""),
                ),
            ),
        )

        assertTrue(provider.draftAction(DraftRequest("make action", "claude-sonnet-4-6")).exceptionOrNull() is AiProviderException.MalformedJson)
    }

    @Test
    fun geminiProvider_usesNativeResponseJsonSchemaForV2Drafts() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("gemini", SecretValue.of("secret"))
        val httpClient =
            FakeAiHttpClient(
                responses =
                    mutableListOf(
                        AiHttpResponse(200, """{"models":[{"name":"models/gemini-2.5-flash"}]}"""),
                        AiHttpResponse(
                            200,
                            """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"schemaVersion\":2,\"status\":\"ready\",\"message\":\"Ready\",\"questions\":[],\"assumptions\":[],\"proposal\":{\"id\":\"draft.open\",\"title\":\"Open Docs\",\"description\":\"Open documentation\",\"requiredCapabilities\":[],\"target\":{\"type\":\"AnyConnected\",\"id\":null},\"safety\":{\"level\":\"Normal\",\"requiresConfirmation\":false,\"confirmationTitle\":null,\"confirmationBody\":null},\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"label\":\"Open docs\",\"url\":\"https://example.com\",\"text\":null,\"delayMs\":null,\"templateId\":null,\"requiresConfirmation\":false}]}}"}]}}]}""",
                        ),
                    ),
            )
        val provider = GeminiProvider(keyStore, httpClient)

        provider.test().getOrThrow()
        val json = provider.draftAction(DraftRequest("make action", "gemini-2.5-flash")).getOrThrow().json

        assertTrue(json.contains("Open Docs"))
        val payload = httpClient.requests.last().body.orEmpty()
        assertTrue(payload.contains("\"responseMimeType\":\"application/json\""))
        assertTrue(payload.contains("\"responseJsonSchema\""))
        assertTrue(payload.contains("\"anyOf\""))
        assertFalse(payload.contains("secret"))
    }

    @Test
    fun geminiProvider_surfacesTruncatedResponses() = runTest {
        val keyStore = InMemorySecureApiKeyStore()
        keyStore.saveKey("gemini", SecretValue.of("secret"))
        val provider = GeminiProvider(
            keyStore,
            FakeAiHttpClient(
                mutableListOf(
                    AiHttpResponse(200, """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[]}}]}"""),
                ),
            ),
        )

        assertTrue(provider.draftAction(DraftRequest("make action", "gemini-2.5-flash")).exceptionOrNull() is AiProviderException.Incomplete)
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
        provider.draftAction(DraftRequest("rank apps", "gemini-2.5-flash", DraftKind.ContextApps)).getOrThrow()

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
