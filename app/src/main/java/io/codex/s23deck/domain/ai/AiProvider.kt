package io.codex.s23deck.domain.ai

data class AiModel(
    val id: String,
    val label: String,
    val supportsStructuredDrafts: Boolean = true,
)

data class DraftRequest(
    val prompt: String,
    val modelId: String,
    val draftKind: DraftKind = DraftKind.Action,
    val availableCapabilities: Set<ActionCapability> = emptySet(),
    val target: TargetSelector = TargetSelector.AnyConnected,
    val agentContext: String = "",
    val repairInstructions: String = "",
)

enum class DraftKind {
    Action,
    Automation,
    Deck,
    ContextApps,
}

data class ActionDraftJson(
    val json: String,
)

sealed interface GeneratedDraft {
    data class Action(val draft: ActionDraft) : GeneratedDraft

    data class Automation(val draft: AutomationDraft) : GeneratedDraft

    data class Deck(val draft: DeckDraft) : GeneratedDraft
}

interface AiProvider {
    suspend fun listModels(): Result<List<AiModel>>
    suspend fun test(): Result<Unit>
    suspend fun draftAction(request: DraftRequest): Result<ActionDraftJson>
}

class AiBuilder(
    private val provider: AiProvider,
    private val validator: ActionDraftValidator,
    private val automationValidator: AutomationDraftValidator = AutomationDraftValidator(),
    private val parser: StructuredDraftParser = StructuredDraftParser(),
    private val entitlementRepository: io.codex.s23deck.domain.commerce.EntitlementRepository,
) {
    suspend fun validateDraft(draft: ActionDraft): Result<ValidationResult> {
        val entitlement = entitlementRepository.currentEntitlement()
        if (!entitlement.allows(FeatureGate.AiBuilder)) {
            return Result.failure(AiBuilderUnavailable("AI Creator requires premium entitlement"))
        }
        return Result.success(validator.validate(draft))
    }

    suspend fun requestDraft(request: DraftRequest): Result<ActionDraftJson> {
        val entitlement = entitlementRepository.currentEntitlement()
        if (!entitlement.allows(FeatureGate.AiBuilder)) {
            return Result.failure(AiBuilderUnavailable("AI Creator requires premium entitlement"))
        }
        return provider.draftAction(request)
    }

    suspend fun requestValidatedDraft(request: DraftRequest): Result<GeneratedDraft> {
        val entitlement = entitlementRepository.currentEntitlement()
        if (!entitlement.allows(FeatureGate.AiBuilder)) {
            return Result.failure(AiBuilderUnavailable("AI Creator requires premium entitlement"))
        }
        return requestValidatedDraftOnce(request).recoverCatching { error ->
            if (error !is SemanticDraftValidationException || request.repairInstructions.isNotBlank()) {
                throw error
            }
            requestValidatedDraftOnce(request.copy(repairInstructions = error.toRepairInstructions())).getOrThrow()
        }
    }

    private suspend fun requestValidatedDraftOnce(request: DraftRequest): Result<GeneratedDraft> =
        provider.draftAction(request).mapCatching { raw ->
            val parsed = parser.parse(request, raw).getOrThrow()
            validateGeneratedDraft(parsed)
            parsed
        }

    private fun validateGeneratedDraft(parsed: GeneratedDraft) {
        val errors = when (parsed) {
            is GeneratedDraft.Action -> validator.validate(parsed.draft).errorsOrEmpty()
            is GeneratedDraft.Automation -> automationValidator.validate(parsed.draft).errorsOrEmpty()
            is GeneratedDraft.Deck -> buildList {
                if (parsed.draft.actions.isEmpty()) {
                    add(ValidationError("actions", "Deck drafts must include at least one action"))
                }
                if (parsed.draft.actions.size > MAX_GENERATED_DECK_ACTIONS) {
                    add(ValidationError("actions", "Deck drafts can include at most $MAX_GENERATED_DECK_ACTIONS actions"))
                }
                val actionIds = mutableSetOf<String>()
                parsed.draft.actions.forEachIndexed { index, definition ->
                    if (!actionIds.add(definition.id)) {
                        add(ValidationError("actions[$index].id", "Deck action id must be unique"))
                    }
                    validator.validate(definition).errorsOrEmpty().forEach { error ->
                        add(ValidationError("actions[$index].${error.path}", error.message))
                    }
                }
            }
        }
        if (errors.isNotEmpty()) throw SemanticDraftValidationException(errors)
    }

    private companion object {
        const val MAX_GENERATED_DECK_ACTIONS = 12
    }
}

enum class FeatureGate {
    AiBuilder,
}

class AiBuilderUnavailable(message: String) : IllegalStateException(message)

class SemanticDraftValidationException(
    val errors: List<ValidationError>,
) : IllegalArgumentException(errors.joinToString { "${it.path}: ${it.message}" }) {
    fun toRepairInstructions(): String =
        buildString {
            appendLine("Repair the previous draft. Return the same V2 envelope schema.")
            appendLine("Fix exactly these semantic validation errors:")
            errors.take(MAX_REPAIR_ERRORS).forEach { error ->
                appendLine("- ${error.path}: ${error.message}")
            }
            appendLine("Do not add shell commands or unapproved templates.")
        }

    private companion object {
        const val MAX_REPAIR_ERRORS = 12
    }
}

private fun ValidationResult.errorsOrEmpty(): List<ValidationError> =
    when (this) {
        ValidationResult.Valid -> emptyList()
        is ValidationResult.Invalid -> errors
    }
