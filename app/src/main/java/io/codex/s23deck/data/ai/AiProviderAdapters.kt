package io.codex.s23deck.data.ai

import io.codex.s23deck.domain.ai.ActionCapability
import io.codex.s23deck.domain.ai.ActionDraftJson
import io.codex.s23deck.domain.ai.ApprovedAiActionCatalog
import io.codex.s23deck.domain.ai.AiModel
import io.codex.s23deck.domain.ai.AiProvider
import io.codex.s23deck.domain.ai.AiProviderCatalog
import io.codex.s23deck.domain.ai.AiProviderId
import io.codex.s23deck.domain.ai.AiProviderSpec
import io.codex.s23deck.domain.ai.DraftKind
import io.codex.s23deck.domain.ai.DraftRequest
import io.codex.s23deck.data.ai.JsonValue
import io.codex.s23deck.data.ai.asObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class OpenAiProvider(
    private val keyStore: SecureApiKeyStore,
    httpClient: AiHttpClient = UrlConnectionAiHttpClient(),
) : LiveAiProvider(AiProviderCatalog.openAi, keyStore, httpClient)

class AnthropicProvider(
    private val keyStore: SecureApiKeyStore,
    httpClient: AiHttpClient = UrlConnectionAiHttpClient(),
) : LiveAiProvider(AiProviderCatalog.anthropic, keyStore, httpClient)

class OpenRouterProvider(
    private val keyStore: SecureApiKeyStore,
    httpClient: AiHttpClient = UrlConnectionAiHttpClient(),
) : LiveAiProvider(AiProviderCatalog.openRouter, keyStore, httpClient)

class LiteLlmProvider(
    private val keyStore: SecureApiKeyStore,
    httpClient: AiHttpClient = UrlConnectionAiHttpClient(),
    baseUrl: String = DEFAULT_LITELLM_BASE_URL,
) : LiveAiProvider(AiProviderCatalog.liteLlm, keyStore, httpClient, baseUrl)

class GeminiProvider(
    private val keyStore: SecureApiKeyStore,
    httpClient: AiHttpClient = UrlConnectionAiHttpClient(),
) : LiveAiProvider(AiProviderCatalog.gemini, keyStore, httpClient)

abstract class LiveAiProvider(
    private val spec: AiProviderSpec,
    private val keyStore: SecureApiKeyStore,
    private val httpClient: AiHttpClient,
    private val openAiCompatibleBaseUrl: String? = null,
) : AiProvider {
    override suspend fun listModels(): Result<List<AiModel>> = Result.success(spec.models)

    override suspend fun test(): Result<Unit> = runCatching {
        val key = requireKey()
        val response =
            when (spec.id) {
                AiProviderId.OpenAI -> httpClient.execute(
                    AiHttpRequest(
                        method = "GET",
                        url = "https://api.openai.com/v1/models",
                        headers = mapOf("Authorization" to "Bearer ${key.revealForProviderCall()}"),
                    ),
                )
                AiProviderId.Anthropic -> httpClient.execute(
                    AiHttpRequest(
                        method = "GET",
                        url = "https://api.anthropic.com/v1/models",
                        headers = mapOf(
                            "x-api-key" to key.revealForProviderCall(),
                            "anthropic-version" to "2023-06-01",
                        ),
                    ),
                )
                AiProviderId.OpenRouter -> httpClient.execute(
                    AiHttpRequest(
                        method = "GET",
                        url = "https://openrouter.ai/api/v1/key",
                        headers = mapOf("Authorization" to "Bearer ${key.revealForProviderCall()}"),
                    ),
                )
                AiProviderId.LiteLLM -> httpClient.execute(
                    AiHttpRequest(
                        method = "GET",
                        url = "${openAiCompatibleBaseUrl()}/v1/models",
                        headers = mapOf("Authorization" to "Bearer ${key.revealForProviderCall()}"),
                    ),
                )
                AiProviderId.Gemini -> httpClient.execute(
                    AiHttpRequest(
                        method = "GET",
                        url = "https://generativelanguage.googleapis.com/v1beta/models",
                        headers = mapOf("x-goog-api-key" to key.revealForProviderCall()),
                    ),
                )
            }
        ensureSuccess(response)
        Unit
    }.mapError()

    override suspend fun draftAction(request: DraftRequest): Result<ActionDraftJson> = runCatching {
        val model = spec.models.firstOrNull { it.id == request.modelId }
        if (model == null) {
            throw AiProviderException.UnsupportedModel("Unsupported model ${request.modelId} for ${spec.label}")
        }
        if (request.draftKind != DraftKind.ContextApps && !model.supportsStructuredDrafts) {
            throw AiProviderException.UnsupportedModel("${model.label} is not enabled for strict AI Creator V2 drafts")
        }
        val key = requireKey()
        val response =
            when (spec.id) {
                AiProviderId.OpenAI -> callOpenAi(request, key)
                AiProviderId.Anthropic -> callAnthropic(request, key)
                AiProviderId.OpenRouter -> callOpenRouter(request, key)
                AiProviderId.LiteLLM -> callLiteLlm(request, key)
                AiProviderId.Gemini -> callGemini(request, key)
            }
        ensureSuccess(response)
        ActionDraftJson(extractResponseText(response.body, request))
    }.mapError()

    private suspend fun callOpenAi(request: DraftRequest, key: SecretValue): AiHttpResponse =
        httpClient.execute(
            AiHttpRequest(
                method = "POST",
                url = "https://api.openai.com/v1/responses",
                headers = mapOf("Authorization" to "Bearer ${key.revealForProviderCall()}"),
                body = buildOpenAiResponsesRequest(request),
            ),
        )

    private suspend fun callOpenRouter(request: DraftRequest, key: SecretValue): AiHttpResponse =
        httpClient.execute(
            AiHttpRequest(
                method = "POST",
                url = "https://openrouter.ai/api/v1/chat/completions",
                headers = mapOf("Authorization" to "Bearer ${key.revealForProviderCall()}"),
                body = buildOpenAiCompatibleRequest(request),
            ),
        )

    private suspend fun callLiteLlm(request: DraftRequest, key: SecretValue): AiHttpResponse =
        httpClient.execute(
            AiHttpRequest(
                method = "POST",
                url = "${openAiCompatibleBaseUrl()}/v1/chat/completions",
                headers = mapOf("Authorization" to "Bearer ${key.revealForProviderCall()}"),
                body = buildOpenAiCompatibleRequest(request),
            ),
        )

    private suspend fun callAnthropic(request: DraftRequest, key: SecretValue): AiHttpResponse =
        httpClient.execute(
            AiHttpRequest(
                method = "POST",
                url = "https://api.anthropic.com/v1/messages",
                headers = mapOf(
                    "x-api-key" to key.revealForProviderCall(),
                    "anthropic-version" to "2023-06-01",
                ),
                body = buildAnthropicRequest(request),
            ),
        )

    private suspend fun callGemini(request: DraftRequest, key: SecretValue): AiHttpResponse =
        httpClient.execute(
            AiHttpRequest(
                method = "POST",
                url = "https://generativelanguage.googleapis.com/v1beta/models/${request.modelId}:generateContent",
                headers = mapOf("x-goog-api-key" to key.revealForProviderCall()),
                body = buildGeminiRequest(request),
            ),
        )

    private suspend fun requireKey(): SecretValue =
        keyStore.loadKey(spec.providerId) ?: throw AiProviderException.AuthFailure("Missing API key for ${spec.providerId}")

    private fun openAiCompatibleBaseUrl(): String =
        openAiCompatibleBaseUrl.orEmpty().trim().trimEnd('/').ifBlank { DEFAULT_LITELLM_BASE_URL }

    private fun buildOpenAiCompatibleRequest(request: DraftRequest): String =
        jsonObject(
            "model" to request.modelId,
            "temperature" to 0.2,
            "messages" to listOf(systemMessage(request), userMessage(request.prompt)),
            "response_format" to mapOf("type" to "json_schema", "json_schema" to jsonSchemaConfig(request)),
        )

    private fun buildOpenAiResponsesRequest(request: DraftRequest): String =
        jsonObject(
            "model" to request.modelId,
            "store" to false,
            "input" to listOf(systemMessage(request), userMessage(request.prompt)),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to schemaName(request),
                    "strict" to true,
                    "schema" to schemaFor(request),
                ),
            ),
        )

    private fun buildAnthropicRequest(request: DraftRequest): String =
        jsonObject(
            "model" to request.modelId,
            "max_tokens" to 900,
            "temperature" to 0.2,
            "system" to systemPrompt(request),
            "messages" to listOf(mapOf("role" to "user", "content" to request.prompt)),
            "tools" to if (request.draftKind == DraftKind.ContextApps) {
                emptyList<Map<String, Any>>()
            } else {
                listOf(
                    mapOf(
                        "name" to ANTHROPIC_DRAFT_TOOL_NAME,
                        "description" to "Return one AI Creator V2 draft envelope. Do not execute anything.",
                        "input_schema" to StrictJsonSchemaAdapter.expandNullableTypeArrays(schemaFor(request)),
                        "strict" to true,
                    ),
                )
            },
            "tool_choice" to if (request.draftKind == DraftKind.ContextApps) {
                mapOf("type" to "auto")
            } else {
                mapOf("type" to "tool", "name" to ANTHROPIC_DRAFT_TOOL_NAME)
            },
        )

    private fun buildGeminiRequest(request: DraftRequest): String =
        jsonObject(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to "${systemPrompt(request)}\n\nUser request:\n${request.prompt}"),
                    ),
                ),
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.2,
                "responseMimeType" to "application/json",
                "responseJsonSchema" to GeminiSchemaAdapter.toResponseJsonSchema(
                    schemaFor(request),
                    relaxServingState = request.draftKind == DraftKind.Deck,
                ),
            ),
        )

    private fun systemMessage(request: DraftRequest): Map<String, Any> =
        mapOf("role" to "system", "content" to systemPrompt(request))

    private fun userMessage(prompt: String): Map<String, Any> =
        mapOf("role" to "user", "content" to prompt)

    private fun systemPrompt(request: DraftRequest): String =
        buildString {
            appendLine("Return only valid JSON for the requested draft.")
            appendLine("Do not use markdown fences.")
            appendLine("Use the exact schema shape. Every field is required; use null only where the schema allows null.")
            if (request.availableCapabilities.isNotEmpty()) {
                appendLine(
                    "Supported capabilities: ${
                        request.availableCapabilities.sortedBy { it.name }.joinToString { it.name }
                    }.",
                )
            }
            appendLine("Target selector: ${request.target}.")
            appendLine("Draft type: ${request.draftKind.name.lowercase()}.")
            if (request.repairInstructions.isNotBlank()) {
                appendLine()
                appendLine("Repair instructions:")
                appendLine(request.repairInstructions)
            }
            if (request.draftKind != DraftKind.ContextApps) {
                appendLine("AI Creator V2 contract: status must be ready, needs_input, unsupported, or refused.")
                appendLine("If request is ambiguous, return needs_input with short questions and proposal null.")
                appendLine("Never create runtime shell, SSH, script, or AppleScript command steps.")
                appendLine("If the user explicitly asks to copy command-looking text, put it only in a clipboard_text step as inert text.")
                appendLine("Capability enum values: ${ActionCapability.entries.joinToString { it.name }}.")
                appendLine("Target type enum values: AnyConnected, ActiveDevice, DeviceId, GroupId.")
                appendLine("Safety level enum values: Normal, Dangerous.")
                appendLine("Use only typed steps: ${ApprovedAiActionCatalog.stepTypes.sorted().joinToString()}.")
                appendLine("For template steps, use only template IDs: ${ApprovedAiActionCatalog.templateIds.sorted().joinToString()}.")
                appendLine("Codecks compiles typed steps into runtime actions after validation and dry run.")
            }
            if (request.agentContext.isNotBlank()) {
                appendLine()
                appendLine("Bundled Codecks agent pack:")
                appendLine(request.agentContext)
            }
        }

    private fun schemaName(request: DraftRequest): String =
        when (request.draftKind) {
            DraftKind.Action -> "action_draft_v2"
            DraftKind.Automation -> "automation_draft_v2"
            DraftKind.Deck -> "deck_draft_v2"
            DraftKind.ContextApps -> "context_app_suggestions"
        }

    private fun jsonSchemaConfig(request: DraftRequest): Map<String, Any> =
        mapOf("name" to schemaName(request), "strict" to true, "schema" to schemaFor(request))

    private fun schemaFor(request: DraftRequest): Map<String, Any> =
        when (request.draftKind) {
            DraftKind.Action,
            DraftKind.Automation,
            DraftKind.Deck,
            -> AiDraftSchemaV2.schemaFor(request.draftKind)
            DraftKind.ContextApps -> contextAppsSchema()
        }

    private fun contextAppsSchema(): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("schemaVersion", "reason", "apps"),
            "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer"),
                "reason" to mapOf("type" to "string"),
                "apps" to mapOf(
                    "type" to "array",
                    "minItems" to 1,
                    "maxItems" to 8,
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to listOf("packageName", "label", "reason"),
                        "properties" to mapOf(
                            "packageName" to mapOf("type" to "string"),
                            "label" to mapOf("type" to "string"),
                            "reason" to mapOf("type" to "string"),
                        ),
                    ),
                ),
            ),
        )

    private fun actionSchema(): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("prompt", "definition"),
            "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer"),
                "prompt" to mapOf("type" to "string"),
                "providerModel" to mapOf("type" to "string"),
                "definition" to definitionSchema(),
            ),
        )

    private fun automationSchema(): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("prompt", "id", "label", "category", "definition"),
            "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer"),
                "prompt" to mapOf("type" to "string"),
                "id" to mapOf("type" to "string"),
                "label" to mapOf("type" to "string"),
                "description" to mapOf("type" to "string"),
                "category" to mapOf("type" to "string"),
                "dangerous" to mapOf("type" to "boolean"),
                "definition" to definitionSchema(),
            ),
        )

    private fun deckSchema(): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("prompt", "id", "title", "actions"),
            "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer"),
                "prompt" to mapOf("type" to "string"),
                "id" to mapOf("type" to "string"),
                "title" to mapOf("type" to "string"),
                "description" to mapOf("type" to "string"),
                "actions" to mapOf(
                    "type" to "array",
                    "minItems" to 1,
                    "maxItems" to 12,
                    "items" to definitionSchema(),
                ),
            ),
        )

    private fun definitionSchema(): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("id", "title", "steps"),
            "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer"),
                "id" to mapOf("type" to "string"),
                "title" to mapOf("type" to "string"),
                "description" to mapOf("type" to "string"),
                "requiredCapabilities" to enumArraySchema(ActionCapability.entries.map { it.name }),
                "variables" to variablesSchema(),
                "templates" to templatesSchema(),
                "target" to targetSchema(),
                "safety" to safetySchema(),
                "steps" to stepsSchema(),
            ),
        )

    private fun variablesSchema(): Map<String, Any> =
        mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "required" to listOf("name", "label"),
                "properties" to mapOf(
                    "name" to mapOf("type" to "string"),
                    "label" to mapOf("type" to "string"),
                    "required" to mapOf("type" to "boolean"),
                    "defaultValue" to mapOf("type" to "string"),
                ),
            ),
        )

    private fun templatesSchema(): Map<String, Any> =
        mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "required" to listOf("id", "body"),
                "properties" to mapOf(
                    "id" to mapOf("type" to "string"),
                    "body" to mapOf("type" to "string"),
                ),
            ),
        )

    private fun targetSchema(): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "type" to mapOf("type" to "string"),
                "id" to mapOf("type" to "string"),
            ),
        )

    private fun safetySchema(): Map<String, Any> =
        mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "level" to mapOf("type" to "string"),
                "requiresConfirmation" to mapOf("type" to "boolean"),
                "confirmationTitle" to mapOf("type" to "string"),
                "confirmationBody" to mapOf("type" to "string"),
            ),
        )

    private fun stepsSchema(): Map<String, Any> =
        mapOf(
            "type" to "array",
            "minItems" to 1,
            "items" to mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "required" to listOf("id", "type"),
                "properties" to mapOf(
                    "id" to mapOf("type" to "string"),
                    "type" to mapOf("type" to "string"),
                    "label" to mapOf("type" to "string"),
                    "value" to mapOf("type" to "string"),
                    "url" to mapOf("type" to "string"),
                    "delayMs" to mapOf("type" to "integer"),
                    "requiredCapabilities" to enumArraySchema(ActionCapability.entries.map { it.name }),
                    "confirmedDangerous" to mapOf("type" to "boolean"),
                    "retry" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "properties" to mapOf(
                            "maxAttempts" to mapOf("type" to "integer"),
                            "delayMs" to mapOf("type" to "integer"),
                        ),
                    ),
                ),
            ),
        )

    private fun enumArraySchema(values: List<String>): Map<String, Any> =
        mapOf("type" to "array", "items" to mapOf("type" to "string", "enum" to values))

    private fun ensureSuccess(response: AiHttpResponse) {
        val detail = response.providerErrorDetail()
        when (response.statusCode) {
            in 200..299 -> return
            401, 403 -> throw AiProviderException.AuthFailure("Authentication failed for ${spec.label}$detail")
            408 -> throw AiProviderException.Timeout("${spec.label} request timed out$detail")
            429 -> throw AiProviderException.RateLimited("${spec.label} rate limited the request$detail")
            else -> throw AiProviderException.RemoteFailure("${spec.label} request failed with ${response.statusCode}$detail")
        }
    }

    private fun AiHttpResponse.providerErrorDetail(): String =
        parseProviderErrorDetail(body).takeIf { it.isNotBlank() }?.let { ": ${it.take(MAX_PROVIDER_ERROR_DETAIL)}" }.orEmpty()

    private fun parseProviderErrorDetail(body: String): String =
        runCatching {
            val root = parseJsonObject(body)
            root.optObj("error")?.let { error ->
                error.optString("message") ?: error.optString("type")
            } ?: root.optString("message").orEmpty()
        }.getOrDefault("")

    private fun extractResponseText(body: String, request: DraftRequest): String {
        val root = parseJsonObject(body)
        return when (spec.id) {
            AiProviderId.OpenAI, AiProviderId.OpenRouter, AiProviderId.LiteLLM -> {
                if (spec.id == AiProviderId.OpenAI && root.has("output")) {
                    return extractOpenAiResponsesText(root)
                }
                val choice = root.array("choices").first().asObject()
                val contentValue = choice.obj("message").let { message ->
                    message.optString("refusal")?.let { throw AiProviderException.Refused(it) }
                    message.optString("content")?.let { return@let JsonValue.Str(it) }
                        ?: JsonValue.Arr(message.array("content"))
                }
                when (contentValue) {
                    is JsonValue.Str -> contentValue.value
                    is JsonValue.Arr -> {
                        val chunks = mutableListOf<String>()
                        contentValue.items.forEach { item ->
                            val textItem = item.asObject()
                            if (textItem.optString("type") == "text") {
                                chunks += textItem.string("text")
                            }
                        }
                        chunks.joinToString("")
                    }
                    else -> throw AiProviderException.MalformedJson("Unexpected response content from ${spec.label}")
                }
            }
            AiProviderId.Anthropic -> {
                val content = root.array("content")
                val chunks = mutableListOf<String>()
                content.forEach { item ->
                    val value = item as? JsonValue.Obj ?: return@forEach
                    val textItem = value.asObject()
                    when (textItem.optString("type")) {
                        "text" -> chunks += textItem.string("text")
                        "tool_use" -> {
                            if (textItem.optString("name") == ANTHROPIC_DRAFT_TOOL_NAME) {
                                val input = value.fields["input"]
                                    ?: throw AiProviderException.MalformedJson("Anthropic tool call missing input")
                                return jsonValueString(input)
                            }
                        }
                    }
                }
                if (request.draftKind != DraftKind.ContextApps) {
                    throw AiProviderException.MalformedJson("Anthropic response did not include a draft tool result")
                }
                chunks.joinToString("").ifBlank {
                    throw AiProviderException.MalformedJson("Anthropic response did not include a draft tool result")
                }
            }
            AiProviderId.Gemini -> {
                val candidate = root.array("candidates").first().asObject()
                if (candidate.optString("finishReason") == "MAX_TOKENS") {
                    throw AiProviderException.Incomplete("Gemini response was incomplete")
                }
                val parts = candidate.obj("content").array("parts")
                val chunks = mutableListOf<String>()
                parts.forEach { item ->
                    val textItem = item.asObject()
                    textItem.optString("text")?.let { chunks += it }
                }
                chunks.joinToString("")
            }
        }
    }

    private fun extractOpenAiResponsesText(root: io.codex.s23deck.data.ai.JsonObject): String {
        val status = root.optString("status").orEmpty()
        if (status == "incomplete") {
            throw AiProviderException.Incomplete("OpenAI response was incomplete")
        }
        val chunks = mutableListOf<String>()
        root.array("output").forEach { output ->
            val outputObject = output.asObject()
            if (outputObject.optString("type") != "message") return@forEach
            outputObject.array("content").forEach { content ->
                val contentObject = content.asObject()
                when (contentObject.optString("type")) {
                    "output_text" -> chunks += contentObject.string("text")
                    "refusal" -> throw AiProviderException.Refused(contentObject.string("refusal"))
                }
            }
        }
        return chunks.joinToString("").ifBlank {
            throw AiProviderException.MalformedJson("OpenAI response did not include output text")
        }
    }

    private companion object {
        const val ANTHROPIC_DRAFT_TOOL_NAME = "emit_ai_creator_v2_draft"
        const val MAX_PROVIDER_ERROR_DETAIL = 240
    }
}

private object StrictJsonSchemaAdapter {
    fun expandNullableTypeArrays(schema: Map<String, Any>): Map<String, Any> =
        schema.mapValues { (_, value) -> adapt(value) }

    @Suppress("UNCHECKED_CAST")
    private fun adapt(value: Any): Any =
        when (value) {
            is Map<*, *> -> {
                val source = value as Map<String, Any>
                val types = source["type"] as? List<*>
                if (types != null && types.any { it == "null" }) {
                    val adaptedBase = (source - "type").mapValues { (_, child) -> adapt(child) }
                    val nonNullTypes = types.filterIsInstance<String>().filterNot { it == "null" }
                    mapOf(
                        "anyOf" to nonNullTypes.map { type ->
                            mapOf("type" to type) + adaptedBase.withoutNullEnum()
                        } + mapOf("type" to "null"),
                    )
                } else {
                    source.mapValues { (_, child) -> adapt(child) }
                }
            }
            is List<*> -> value.mapNotNull { item -> item?.let(::adapt) }
            else -> value
        }

    private fun Map<String, Any>.withoutNullEnum(): Map<String, Any> {
        val values = this["enum"] as? List<*> ?: return this
        return this + ("enum" to values.filterNotNull())
    }
}

private object GeminiSchemaAdapter {
    fun toResponseJsonSchema(
        schema: Map<String, Any>,
        relaxServingState: Boolean,
    ): Map<String, Any> {
        val expanded = StrictJsonSchemaAdapter.expandNullableTypeArrays(schema)
        @Suppress("UNCHECKED_CAST")
        return if (relaxServingState) relaxStateHeavyConstraints(expanded) as Map<String, Any> else expanded
    }

    @Suppress("UNCHECKED_CAST")
    private fun relaxStateHeavyConstraints(value: Any): Any =
        when (value) {
            is Map<*, *> -> buildMap {
                (value as Map<String, Any>).forEach { (key, child) ->
                    val enumSize = (child as? List<*>)?.size ?: 0
                    when {
                        key == "minItems" || key == "maxItems" -> Unit
                        key == "enum" && enumSize > MAX_GEMINI_SERVING_ENUM -> Unit
                        else -> put(key, relaxStateHeavyConstraints(child))
                    }
                }
            }
            is List<*> -> value.mapNotNull { item -> item?.let(::relaxStateHeavyConstraints) }
            else -> value
        }

    private const val MAX_GEMINI_SERVING_ENUM = 4
}

sealed class AiProviderException(message: String) : Exception(message) {
    class AuthFailure(message: String) : AiProviderException(message)
    class RateLimited(message: String) : AiProviderException(message)
    class Timeout(message: String) : AiProviderException(message)
    class UnsupportedModel(message: String) : AiProviderException(message)
    class MalformedJson(message: String) : AiProviderException(message)
    class Refused(message: String) : AiProviderException(message)
    class Incomplete(message: String) : AiProviderException(message)
    class RemoteFailure(message: String) : AiProviderException(message)
}

data class AiHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class AiHttpResponse(
    val statusCode: Int,
    val body: String,
)

interface AiHttpClient {
    suspend fun execute(request: AiHttpRequest): AiHttpResponse
}

class UrlConnectionAiHttpClient : AiHttpClient {
    override suspend fun execute(request: AiHttpRequest): AiHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = 15_000
                readTimeout = 30_000
                doInput = true
                setRequestProperty("Content-Type", "application/json")
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (request.body != null) {
                    doOutput = true
                    outputStream.bufferedWriter().use { it.write(request.body) }
                }
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                AiHttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } catch (error: SocketTimeoutException) {
                throw AiProviderException.Timeout(error.message ?: "Timed out")
            } catch (error: IOException) {
                throw AiProviderException.RemoteFailure(error.message ?: "Network error")
            } finally {
                connection.disconnect()
            }
        }
}

class AiProviderFactory(
    private val keyStore: SecureApiKeyStore,
    private val httpClient: AiHttpClient = UrlConnectionAiHttpClient(),
    private val liteLlmBaseUrl: String = DEFAULT_LITELLM_BASE_URL,
) {
    fun create(providerId: String, liteLlmBaseUrlOverride: String? = null): AiProvider =
        when (AiProviderCatalog.byProviderId(providerId)?.id) {
            AiProviderId.OpenAI -> OpenAiProvider(keyStore, httpClient)
            AiProviderId.Anthropic -> AnthropicProvider(keyStore, httpClient)
            AiProviderId.OpenRouter -> OpenRouterProvider(keyStore, httpClient)
            AiProviderId.LiteLLM -> LiteLlmProvider(
                keyStore,
                httpClient,
                liteLlmBaseUrlOverride?.takeIf { it.isNotBlank() } ?: liteLlmBaseUrl,
            )
            AiProviderId.Gemini -> GeminiProvider(keyStore, httpClient)
            null -> throw IllegalArgumentException("Unknown AI provider $providerId")
        }
}

const val DEFAULT_LITELLM_BASE_URL = ""

private fun <T> Result<T>.mapError(): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = {
            when (it) {
                is AiProviderException -> Result.failure(it)
                else -> Result.failure(AiProviderException.RemoteFailure(it.message ?: "Provider call failed"))
            }
        },
    )
