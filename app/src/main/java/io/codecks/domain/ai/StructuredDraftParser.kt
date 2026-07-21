package io.codecks.domain.ai

import io.codecks.data.ai.AiProviderException
import io.codecks.data.ai.JsonValue
import io.codecks.data.ai.parseJsonObject
import io.codecks.data.ai.asObject

class StructuredDraftParser {
    fun parse(request: DraftRequest, payload: ActionDraftJson): Result<GeneratedDraft> =
        runCatching {
            val root = parseJsonObject(extractJsonObject(payload.json))
            if (root.int("schemaVersion", CURRENT_ACTION_SCHEMA_VERSION) == CURRENT_DRAFT_ENVELOPE_VERSION) {
                return@runCatching parseV2Envelope(request, root)
            }
            when (request.draftKind) {
                DraftKind.Action -> GeneratedDraft.Action(parseActionDraft(root))
                DraftKind.Automation -> GeneratedDraft.Automation(parseAutomationDraft(root))
                DraftKind.Deck -> GeneratedDraft.Deck(parseDeckDraft(root))
                DraftKind.ContextApps -> throw IllegalArgumentException("Context app suggestions are parsed by ContextAppSuggestionParser")
            }
        }.recoverCatching { error ->
            when (error) {
                is AiDraftProposalUnavailable -> throw error
                is AiProviderException -> throw error
                else -> throw AiProviderException.MalformedJson(error.message ?: "Could not parse draft JSON")
            }
        }

    private fun parseActionDraft(root: io.codecks.data.ai.JsonObject): ActionDraft =
        ActionDraft(
            schemaVersion = root.int("schemaVersion", CURRENT_ACTION_SCHEMA_VERSION),
            prompt = root.string("prompt"),
            providerModel = root.optString("providerModel")?.ifBlank { null },
            definition = parseDefinition(root.obj("definition")),
        )

    private fun parseAutomationDraft(root: io.codecks.data.ai.JsonObject): AutomationDraft =
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

    private fun parseDeckDraft(root: io.codecks.data.ai.JsonObject): DeckDraft =
        DeckDraft(
            schemaVersion = root.int("schemaVersion", CURRENT_ACTION_SCHEMA_VERSION),
            prompt = root.string("prompt"),
            id = root.string("id"),
            title = root.string("title"),
            description = root.optString("description").orEmpty(),
            actions = root.array("actions").map { parseDefinition(it.asObject()) },
        )

    private fun parseDefinition(root: io.codecks.data.ai.JsonObject): ActionDefinition =
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

    private fun parseVariable(root: io.codecks.data.ai.JsonObject): ActionVariable =
        ActionVariable(
            name = root.string("name"),
            label = root.string("label"),
            required = root.bool("required"),
            defaultValue = root.optString("defaultValue")?.ifBlank { null },
        )

    private fun parseTemplate(root: io.codecks.data.ai.JsonObject): ActionTemplate =
        ActionTemplate(id = root.string("id"), body = root.string("body"))

    private fun parseTarget(root: io.codecks.data.ai.JsonObject?): TargetSelector {
        val type = root?.optString("type").orEmpty()
        return when (type) {
            "ActiveDevice" -> TargetSelector.ActiveDevice
            "DeviceId" -> TargetSelector.DeviceId(root?.optString("id").orEmpty())
            "GroupId" -> TargetSelector.GroupId(root?.optString("id").orEmpty())
            else -> TargetSelector.AnyConnected
        }
    }

    private fun parseSafety(root: io.codecks.data.ai.JsonObject?): SafetyMetadata =
        SafetyMetadata(
            level = root?.optString("level")?.takeIf { it.isNotBlank() }?.let { SafetyLevel.valueOf(it) } ?: SafetyLevel.Normal,
            requiresConfirmation = root?.bool("requiresConfirmation") ?: false,
            confirmationTitle = root?.optString("confirmationTitle")?.ifBlank { null },
            confirmationBody = root?.optString("confirmationBody")?.ifBlank { null },
        )

    private fun parseStep(root: io.codecks.data.ai.JsonObject): ActionStep =
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

    private fun parseRetry(root: io.codecks.data.ai.JsonObject?): RetryPolicy =
        RetryPolicy(
            maxAttempts = root?.int("maxAttempts", 1) ?: 1,
            delayMs = root?.long("delayMs", 0L) ?: 0L,
        )

    private fun parseV2Envelope(
        request: DraftRequest,
        root: io.codecks.data.ai.JsonObject,
    ): GeneratedDraft {
        val status = parseEnvelopeStatus(root.string("status"))
        val message = root.string("message")
        val questions = root.array("questions").mapNotNull { (it as? JsonValue.Str)?.value }
        val metadata = DraftReviewMetadata(
            message = message,
            assumptions = root.array("assumptions").mapNotNull { (it as? JsonValue.Str)?.value },
        )
        if (status != DraftEnvelopeStatus.Ready) {
            throw AiDraftProposalUnavailable(status, message, questions)
        }
        val proposal = root.optObj("proposal")
            ?: throw IllegalArgumentException("Ready V2 draft requires a proposal")
        return when (request.draftKind) {
            DraftKind.Action -> GeneratedDraft.Action(
                ActionDraft(
                    schemaVersion = CURRENT_ACTION_SCHEMA_VERSION,
                    prompt = request.prompt,
                    providerModel = request.modelId,
                    definition = parseV2Definition(proposal),
                    metadata = metadata,
                ),
            )
            DraftKind.Automation -> GeneratedDraft.Automation(parseV2Automation(request, proposal, metadata))
            DraftKind.Deck -> GeneratedDraft.Deck(parseV2Deck(request, proposal, metadata))
            DraftKind.ContextApps -> throw IllegalArgumentException("Context app suggestions are parsed by ContextAppSuggestionParser")
        }
    }

    private fun parseEnvelopeStatus(value: String): DraftEnvelopeStatus =
        DraftEnvelopeStatus.entries.firstOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("Unsupported V2 status $value")

    private fun parseV2Automation(
        request: DraftRequest,
        root: io.codecks.data.ai.JsonObject,
        metadata: DraftReviewMetadata,
    ): AutomationDraft =
        AutomationDraft(
            schemaVersion = CURRENT_AUTOMATION_SCHEMA_VERSION,
            prompt = request.prompt,
            id = root.string("id"),
            label = root.string("label"),
            description = root.string("description"),
            category = root.string("category"),
            dangerous = root.bool("dangerous"),
            definition = parseV2Definition(root.obj("definition")),
            metadata = metadata,
        )

    private fun parseV2Deck(
        request: DraftRequest,
        root: io.codecks.data.ai.JsonObject,
        metadata: DraftReviewMetadata,
    ): DeckDraft =
        DeckDraft(
            schemaVersion = CURRENT_ACTION_SCHEMA_VERSION,
            prompt = request.prompt,
            id = root.string("id"),
            title = root.string("title"),
            description = root.string("description"),
            actions = root.array("actions").map { parseV2Definition(it.asObject()) },
            metadata = metadata,
        )

    private fun parseV2Definition(root: io.codecks.data.ai.JsonObject): ActionDefinition =
        ActionDefinition(
            schemaVersion = CURRENT_ACTION_SCHEMA_VERSION,
            id = root.string("id"),
            title = root.string("title"),
            description = root.string("description"),
            variables = emptyList(),
            templates = emptyList(),
            requiredCapabilities = parseCapabilities(root.array("requiredCapabilities")),
            target = parseV2Target(root.obj("target")),
            safety = parseV2Safety(root.obj("safety")),
            steps = root.array("steps").map { parseV2Step(it.asObject()) },
        )

    private fun parseCapabilities(values: List<JsonValue>): List<ActionCapability> =
        values.mapNotNull { value ->
            val raw = (value as? JsonValue.Str)?.value ?: return@mapNotNull null
            parseCapability(raw)
        }.distinct()

    private fun parseCapability(raw: String): ActionCapability? {
        val normalized = raw.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .lowercase()
        return when (normalized) {
            "advanced", "admin", "dangerous" -> ActionCapability.Advanced
            "browser", "open_url", "url_opener", "url", "web", "website", "link" -> ActionCapability.Browser
            "clipboard", "copy", "paste", "copy_paste" -> ActionCapability.Clipboard
            "hidkeyboard", "hid_keyboard", "keyboard", "key", "hotkey", "shortcut", "mac", "mac_shortcut" ->
                ActionCapability.HidKeyboard
            "hidmouse", "hid_mouse", "mouse", "trackpad", "pointer" -> ActionCapability.HidMouse
            "media", "audio", "playback" -> ActionCapability.Media
            "shell", "terminal", "command_line" -> ActionCapability.Shell
            "ssh", "remote_shell" -> ActionCapability.Ssh
            else -> ActionCapability.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }
    }

    private fun parseV2Target(root: io.codecks.data.ai.JsonObject): TargetSelector =
        when (root.string("type")) {
            "AnyConnected" -> TargetSelector.AnyConnected
            "ActiveDevice" -> TargetSelector.ActiveDevice
            "DeviceId" -> TargetSelector.DeviceId(root.optString("id").orEmpty())
            "GroupId" -> TargetSelector.GroupId(root.optString("id").orEmpty())
            else -> TargetSelector.AnyConnected
        }

    private fun parseV2Safety(root: io.codecks.data.ai.JsonObject): SafetyMetadata =
        SafetyMetadata(
            level = SafetyLevel.valueOf(root.string("level")),
            requiresConfirmation = root.bool("requiresConfirmation"),
            confirmationTitle = root.optString("confirmationTitle")?.ifBlank { null },
            confirmationBody = root.optString("confirmationBody")?.ifBlank { null },
        )

    private fun parseV2Step(root: io.codecks.data.ai.JsonObject): ActionStep {
        val id = root.string("id")
        val label = root.string("label")
        val requiresConfirmation = root.bool("requiresConfirmation")
        return when (val type = root.string("type")) {
            V2StepTypes.OpenUrl -> ActionStep(
                id = id,
                type = ActionStepTypes.OpenUrl,
                label = label,
                url = root.optString("url")?.ifBlank { null }
                    ?: throw IllegalArgumentException("open_url step requires url"),
                confirmedDangerous = requiresConfirmation,
            )
            V2StepTypes.ClipboardText -> ActionStep(
                id = id,
                type = ActionStepTypes.ClipboardText,
                label = label,
                value = root.optString("text").orEmpty(),
                confirmedDangerous = requiresConfirmation,
            )
            V2StepTypes.Delay -> ActionStep(
                id = id,
                type = ActionStepTypes.Delay,
                label = label,
                delayMs = root.long("delayMs", -1L).takeIf { it >= 0L }
                    ?: throw IllegalArgumentException("delay step requires delayMs"),
                confirmedDangerous = requiresConfirmation,
            )
            V2StepTypes.Template -> {
                val templateId = root.optString("templateId")?.ifBlank { null }
                    ?: throw IllegalArgumentException("template step requires templateId")
                val command = ApprovedAiActionCatalog.commandFor(templateId)
                    ?: throw IllegalArgumentException("Unsupported approved template $templateId")
                ActionStep(
                    id = id,
                    type = ActionStepTypes.Shell,
                    label = label.ifBlank { templateId },
                    value = command,
                    requiredCapabilities = listOf(ActionCapability.Advanced),
                    confirmedDangerous = requiresConfirmation,
                )
            }
            else -> throw IllegalArgumentException("Unsupported V2 step type $type")
        }
    }

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
