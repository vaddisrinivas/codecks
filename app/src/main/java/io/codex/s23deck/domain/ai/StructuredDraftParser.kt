package io.codex.s23deck.domain.ai

import io.codex.s23deck.data.ai.AiProviderException
import io.codex.s23deck.data.ai.JsonValue
import io.codex.s23deck.data.ai.parseJsonObject
import io.codex.s23deck.data.ai.asObject

class StructuredDraftParser {
    fun parse(request: DraftRequest, payload: ActionDraftJson): Result<GeneratedDraft> =
        runCatching {
            val root = parseJsonObject(extractJsonObject(payload.json))
            when (request.draftKind) {
                DraftKind.Action -> GeneratedDraft.Action(parseActionDraft(root))
                DraftKind.Automation -> GeneratedDraft.Automation(parseAutomationDraft(root))
                DraftKind.Deck -> GeneratedDraft.Deck(parseDeckDraft(root))
                DraftKind.ContextApps -> throw IllegalArgumentException("Context app suggestions are parsed by ContextAppSuggestionParser")
            }
        }.recoverCatching { error ->
            throw AiProviderException.MalformedJson(error.message ?: "Could not parse draft JSON")
        }

    private fun parseActionDraft(root: io.codex.s23deck.data.ai.JsonObject): ActionDraft =
        ActionDraft(
            schemaVersion = root.int("schemaVersion", CURRENT_ACTION_SCHEMA_VERSION),
            prompt = root.string("prompt"),
            providerModel = root.optString("providerModel")?.ifBlank { null },
            definition = parseDefinition(root.obj("definition")),
        )

    private fun parseAutomationDraft(root: io.codex.s23deck.data.ai.JsonObject): AutomationDraft =
        AutomationDraft(
            schemaVersion = root.int("schemaVersion", CURRENT_AUTOMATION_SCHEMA_VERSION),
            prompt = root.string("prompt"),
            id = root.string("id"),
            label = root.string("label"),
            description = root.optString("description").orEmpty(),
            category = root.string("category"),
            dangerous = root.bool("dangerous"),
            definition = parseDefinition(root.obj("definition")),
        )

    private fun parseDeckDraft(root: io.codex.s23deck.data.ai.JsonObject): DeckDraft =
        DeckDraft(
            schemaVersion = root.int("schemaVersion", CURRENT_ACTION_SCHEMA_VERSION),
            prompt = root.string("prompt"),
            id = root.string("id"),
            title = root.string("title"),
            description = root.optString("description").orEmpty(),
            actions = root.array("actions").map { parseDefinition(it.asObject()) },
        )

    private fun parseDefinition(root: io.codex.s23deck.data.ai.JsonObject): ActionDefinition =
        ActionDefinition(
            schemaVersion = root.int("schemaVersion", CURRENT_ACTION_SCHEMA_VERSION),
            id = root.string("id"),
            title = root.string("title"),
            description = root.optString("description").orEmpty(),
            variables = root.array("variables").map { parseVariable(it.asObject()) },
            templates = root.array("templates").map { parseTemplate(it.asObject()) },
            requiredCapabilities = root.array("requiredCapabilities").map { ActionCapability.valueOf((it as JsonValue.Str).value) },
            target = parseTarget(root.optObj("target")),
            safety = parseSafety(root.optObj("safety")),
            steps = root.array("steps").map { parseStep(it.asObject()) },
        )

    private fun parseVariable(root: io.codex.s23deck.data.ai.JsonObject): ActionVariable =
        ActionVariable(
            name = root.string("name"),
            label = root.string("label"),
            required = root.bool("required"),
            defaultValue = root.optString("defaultValue")?.ifBlank { null },
        )

    private fun parseTemplate(root: io.codex.s23deck.data.ai.JsonObject): ActionTemplate =
        ActionTemplate(id = root.string("id"), body = root.string("body"))

    private fun parseTarget(root: io.codex.s23deck.data.ai.JsonObject?): TargetSelector {
        val type = root?.optString("type").orEmpty()
        return when (type) {
            "ActiveDevice" -> TargetSelector.ActiveDevice
            "DeviceId" -> TargetSelector.DeviceId(root?.optString("id").orEmpty())
            "GroupId" -> TargetSelector.GroupId(root?.optString("id").orEmpty())
            else -> TargetSelector.AnyConnected
        }
    }

    private fun parseSafety(root: io.codex.s23deck.data.ai.JsonObject?): SafetyMetadata =
        SafetyMetadata(
            level = root?.optString("level")?.takeIf { it.isNotBlank() }?.let { SafetyLevel.valueOf(it) } ?: SafetyLevel.Normal,
            requiresConfirmation = root?.bool("requiresConfirmation") ?: false,
            confirmationTitle = root?.optString("confirmationTitle")?.ifBlank { null },
            confirmationBody = root?.optString("confirmationBody")?.ifBlank { null },
        )

    private fun parseStep(root: io.codex.s23deck.data.ai.JsonObject): ActionStep =
        ActionStep(
            id = root.string("id"),
            type = root.string("type"),
            label = root.optString("label").orEmpty(),
            value = root.optString("value")?.ifBlank { null },
            url = root.optString("url")?.ifBlank { null },
            delayMs = if (root.has("delayMs")) root.long("delayMs", 0L) else null,
            retry = parseRetry(root.optObj("retry")),
            requiredCapabilities = root.array("requiredCapabilities").map { ActionCapability.valueOf((it as JsonValue.Str).value) },
            confirmedDangerous = root.bool("confirmedDangerous", false),
        )

    private fun parseRetry(root: io.codex.s23deck.data.ai.JsonObject?): RetryPolicy =
        RetryPolicy(
            maxAttempts = root?.int("maxAttempts", 1) ?: 1,
            delayMs = root?.long("delayMs", 0L) ?: 0L,
        )

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fenced = Regex("^```(?:json)?\\s*(\\{.*})\\s*```$", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
        if (fenced != null) return fenced
        throw IllegalArgumentException("Response must be a JSON object or a single fenced JSON object")
    }
}
