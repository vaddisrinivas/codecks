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
        return provider.draftAction(request).mapCatching { raw ->
            when (val parsed = parser.parse(request, raw).getOrThrow()) {
                is GeneratedDraft.Action -> {
                    when (val validation = validator.validate(parsed.draft)) {
                        ValidationResult.Valid -> parsed
                        is ValidationResult.Invalid -> throw IllegalArgumentException(validation.errors.joinToString { it.message })
                    }
                }
                is GeneratedDraft.Automation -> {
                    when (val validation = automationValidator.validate(parsed.draft)) {
                        ValidationResult.Valid -> parsed
                        is ValidationResult.Invalid -> throw IllegalArgumentException(validation.errors.joinToString { it.message })
                    }
                }
                is GeneratedDraft.Deck -> {
                    if (parsed.draft.actions.isEmpty()) {
                        throw IllegalArgumentException("Deck drafts must include at least one action")
                    }
                    parsed.draft.actions.forEach { definition ->
                        when (val validation = validator.validate(definition)) {
                            ValidationResult.Valid -> Unit
                            is ValidationResult.Invalid -> throw IllegalArgumentException(validation.errors.joinToString { it.message })
                        }
                    }
                    parsed
                }
            }
        }
    }
}

enum class FeatureGate {
    AiBuilder,
}

class AiBuilderUnavailable(message: String) : IllegalStateException(message)
