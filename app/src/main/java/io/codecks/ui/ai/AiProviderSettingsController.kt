package io.codecks.ui.ai

import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.RawCommandPolicy
import io.codecks.core.actions.toAiArtifact
import io.codecks.data.ai.AiArtifactRepository
import io.codecks.data.ai.AiGenerationHistoryRepository
import io.codecks.data.ai.AiProviderException
import io.codecks.data.ai.AiProviderFactory
import io.codecks.data.ai.SecretValue
import io.codecks.data.ai.SecureApiKeyStore
import io.codecks.domain.ai.ActionCapability
import io.codecks.domain.ai.ActionDraftValidator
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactTest
import io.codecks.domain.ai.AiArtifactTestStatus
import io.codecks.domain.ai.AiBuilder
import io.codecks.domain.ai.AiDraftProposalUnavailable
import io.codecks.domain.ai.AiGenerationRecord
import io.codecks.domain.ai.AiGenerationStatus
import io.codecks.domain.ai.AiModel
import io.codecks.domain.ai.AiProviderCatalog
import io.codecks.domain.ai.AiProviderSpec
import io.codecks.domain.ai.DraftEnvelopeStatus
import io.codecks.domain.ai.DraftKind
import io.codecks.domain.ai.DraftRequest
import io.codecks.domain.ai.FeatureGate
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.ai.SemanticDraftValidationException
import io.codecks.domain.features.Entitlement
import io.codecks.domain.features.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiProviderChoice(
    val spec: AiProviderSpec,
) {
    OpenAI(AiProviderCatalog.openAi),
    Anthropic(AiProviderCatalog.anthropic),
    OpenRouter(AiProviderCatalog.openRouter),
    LiteLLM(AiProviderCatalog.liteLlm),
    Gemini(AiProviderCatalog.gemini),
}

val AiProviderChoice.providerId: String get() = spec.providerId
val AiProviderChoice.label: String get() = spec.label
val AiProviderChoice.models get() = spec.models
private val DefaultAiProviderChoice: AiProviderChoice
    get() = AiProviderChoice.entries.firstOrNull { it.providerId == AiProviderCatalog.DefaultProviderId } ?: AiProviderChoice.OpenAI

private fun AiProviderChoice.defaultModelId(): String = AiProviderCatalog.defaultModelId(providerId)

enum class AiWorkspaceMode {
    Workspace,
    ProviderSettings,
}

data class AiLocalCommandResult(
    val message: String,
    val artifactId: String? = null,
    val actionId: String? = null,
    val actionLabel: String? = null,
)

enum class AiProviderTestStatus {
    Idle,
    Running,
    Success,
    Failure,
}

data class AiProviderSettingsState(
    val selectedProvider: AiProviderChoice = DefaultAiProviderChoice,
    val selectedModelId: String = DefaultAiProviderChoice.defaultModelId(),
    val apiKeyInput: String = "",
    val baseUrlInput: String = "",
    val savedBaseUrl: String = "",
    val hasSavedKey: Boolean = false,
    val entitlement: Entitlement = Entitlement(),
    val testStatus: AiProviderTestStatus = AiProviderTestStatus.Idle,
    val prompt: String = "",
    val draftKind: DraftKind = DraftKind.Action,
    val isGenerating: Boolean = false,
    val generatedDraft: GeneratedDraft? = null,
    val generatedArtifactId: String? = null,
    val artifacts: List<AiArtifact> = emptyList(),
    val generationHistory: List<AiGenerationRecord> = emptyList(),
    val refiningArtifact: AiArtifact? = null,
    val lastRefinedFromArtifact: AiArtifact? = null,
    val lastRefinedArtifactId: String? = null,
    val testingArtifactId: String? = null,
    val message: String? = null,
    val skillInstructions: String = "",
) {
    val selectedModel: AiModel =
        selectedProvider.models.firstOrNull { it.id == selectedModelId } ?: selectedProvider.models.first()
    val aiAllowed: Boolean = entitlement.allows(FeatureGate.AiBuilder)
}

class AiProviderSettingsController(
    private val keyStore: SecureApiKeyStore,
    private val providerFactory: AiProviderFactory,
    private val entitlementRepository: EntitlementRepository,
    private val scope: CoroutineScope,
    private val artifactRepository: AiArtifactRepository? = null,
    private val generationHistoryRepository: AiGenerationHistoryRepository? = null,
    private val actionRunner: ActionRunner? = null,
    private val agentContext: String = "",
    private val initialSkillInstructions: String = "",
    private val localCommandHandler: suspend (String) -> AiLocalCommandResult? = { null },
) {
    private val jobs = mutableListOf<Job>()
    private val _uiState = MutableStateFlow(AiProviderSettingsState(skillInstructions = initialSkillInstructions))
    val uiState: StateFlow<AiProviderSettingsState> = _uiState.asStateFlow()

    init {
        jobs += scope.launch {
            entitlementRepository.entitlement.collect { entitlement ->
                _uiState.update { it.copy(entitlement = entitlement) }
            }
        }
        artifactRepository?.let { repository ->
            jobs += scope.launch {
                repository.artifacts.collect { artifacts ->
                    _uiState.update { it.copy(artifacts = artifacts) }
                }
            }
        }
        generationHistoryRepository?.let { repository ->
            jobs += scope.launch {
                repository.records.collect { records ->
                    _uiState.update { it.copy(generationHistory = records) }
                }
            }
        }
        refreshSavedKey()
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun selectProvider(provider: AiProviderChoice) {
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                selectedModelId = provider.defaultModelId(),
                apiKeyInput = "",
                baseUrlInput = "",
                testStatus = AiProviderTestStatus.Idle,
                message = null,
            )
        }
        refreshSavedKey()
    }

    fun selectModel(modelId: String) {
        if (_uiState.value.selectedProvider.models.none { it.id == modelId }) return
        _uiState.update { it.copy(selectedModelId = modelId, message = null) }
    }

    fun setApiKey(value: String) {
        _uiState.update {
            it.copy(
                apiKeyInput = value,
                testStatus = AiProviderTestStatus.Idle,
                message = null,
            )
        }
    }

    fun setBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrlInput = value.trim(), testStatus = AiProviderTestStatus.Idle, message = null) }
    }

    fun setPrompt(value: String) {
        _uiState.update { it.copy(prompt = value, message = null) }
    }

    fun setDraftKind(value: DraftKind) {
        _uiState.update { it.copy(draftKind = value, refiningArtifact = null, message = null) }
    }

    fun setSkillInstructions(value: String) {
        _uiState.update { it.copy(skillInstructions = value, message = null) }
    }

    fun startRefinement(artifactId: String) {
        val artifact = _uiState.value.artifacts.firstOrNull { it.id == artifactId } ?: return
        _uiState.update {
            it.copy(
                draftKind = artifact.kind.toDraftKind(),
                generatedDraft = null,
                generatedArtifactId = artifact.id,
                refiningArtifact = artifact,
                prompt = artifact.prompt,
                message = "Loaded ${artifact.title}. Edit the prompt, then regenerate.",
            )
        }
    }

    fun cancelRefinement() {
        _uiState.update { it.copy(refiningArtifact = null, message = "Prompt edit discarded") }
    }

    fun generateDraft() {
        val state = _uiState.value
        val prompt = state.prompt.trim()
        val refinementSource = state.refiningArtifact
            ?: state.generatedArtifactId?.let { id -> state.artifacts.firstOrNull { it.id == id } }
        if (prompt.isBlank()) {
            _uiState.update {
                it.copy(
                    message = if (refinementSource == null) {
                        "Describe what the control should do"
                    } else {
                        "Edit the prompt before regenerating"
                    },
                )
            }
            return
        }
        scope.launch {
            _uiState.update {
                it.copy(
                    generatedDraft = null,
                    message = null,
                )
            }
            localCommandHandler(prompt)?.let { result ->
                _uiState.update {
                    it.copy(
                        prompt = "",
                        isGenerating = false,
                        message = result.message,
                    )
                }
                return@launch
            }
            if (!keyStore.hasKey(state.selectedProvider.providerId)) {
                _uiState.update { it.copy(message = "Save an API key before generating") }
                return@launch
            }
            if (!state.selectedModel.supportsStructuredDrafts) {
                _uiState.update {
                    it.copy(message = "${state.selectedModel.label} cannot create Codecks drafts. Choose another model.")
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    generatedDraft = null,
                    message = null,
                )
            }
            val provider = createProvider(state)
            val builder = AiBuilder(provider, ActionDraftValidator(), entitlementRepository = entitlementRepository)
            val request = DraftRequest(
                prompt = refinementSource?.toRefinementPrompt(prompt) ?: prompt,
                modelId = state.selectedModelId,
                draftKind = state.draftKind,
                availableCapabilities = ActionCapability.entries.toSet(),
                agentContext = buildAgentContext(state.skillInstructions),
            )
            builder.requestValidatedDraft(request).fold(
                onSuccess = { draft ->
                    draft.toAiArtifact(prompt).fold(
                        onSuccess = { artifact ->
                            recordGeneration(
                                state = state,
                                status = AiGenerationStatus.Ready,
                                message = "Draft ready. Test it before saving.",
                                artifactId = artifact.id,
                            )
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    generatedDraft = draft,
                                    generatedArtifactId = artifact.id,
                                    artifacts = listOf(artifact) + it.artifacts.filterNot { item -> item.id == artifact.id },
                                    refiningArtifact = null,
                                    lastRefinedFromArtifact = refinementSource,
                                    lastRefinedArtifactId = artifact.id.takeIf { refinementSource != null },
                                    message = "Draft preview updated",
                                )
                            }
                        },
                        onFailure = { error ->
                            val message = error.message ?: "Codecks could not turn this draft into a runnable button or rule"
                            recordGeneration(
                                state = state,
                                status = AiGenerationStatus.Failed,
                                message = message,
                                validationErrors = listOf(message),
                            )
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    message = message,
                                )
                            }
                        },
                    )
                },
                onFailure = { error ->
                    val message = error.message ?: "Generation failed"
                    recordGeneration(
                        state = state,
                        status = error.generationStatus(),
                        message = message,
                        validationErrors = (error as? SemanticDraftValidationException)
                            ?.errors
                            ?.map { "${it.path}: ${it.message}" }
                            .orEmpty(),
                    )
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            message = message,
                        )
                    }
                },
            )
        }
    }

    fun testArtifact(artifactId: String) {
        val artifact = _uiState.value.artifacts.firstOrNull { it.id == artifactId } ?: return
        if (artifact.actions.isEmpty()) {
            _uiState.update { it.copy(message = "Draft has no runnable button") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(testingArtifactId = artifactId, message = null) }
            val validationError = artifact.actions.firstNotNullOfOrNull { it.command.aiDryRunError() }
            var finalStatus = if (validationError == null) AiArtifactTestStatus.Succeeded else AiArtifactTestStatus.Failed
            var finalMessage = validationError ?: "${artifact.title} passed the safety check. No Mac command ran."
            if (validationError == null) {
                if (artifact.actions.any { it.dangerous }) {
                    finalStatus = AiArtifactTestStatus.RequiresConfirmation
                    finalMessage = "${artifact.title} passed the safety check. It needs confirmation before running."
                }
            }
            val test = AiArtifactTest(finalStatus, finalMessage)
            artifactRepository?.save(artifact.copy(lastTest = test))
            _uiState.update {
                it.copy(
                    testingArtifactId = null,
                    artifacts = it.artifacts.map { item -> if (item.id == artifactId) item.copy(lastTest = test) else item },
                    message = finalMessage,
                )
            }
        }
    }

    fun deleteArtifact(artifactId: String) {
        scope.launch {
            artifactRepository?.delete(artifactId)
            _uiState.update { state ->
                state.copy(
                    artifacts = state.artifacts.filterNot { it.id == artifactId },
                    message = "Draft removed",
                )
            }
        }
    }

    fun markDraftSaved() {
        _uiState.update {
            it.copy(
                prompt = "",
                generatedDraft = null,
                generatedArtifactId = null,
                refiningArtifact = null,
                lastRefinedFromArtifact = null,
                lastRefinedArtifactId = null,
                message = "Saved disabled. Test it from Deck or Rules before enabling.",
            )
        }
    }

    fun newArtifact() {
        _uiState.update {
            it.copy(
                prompt = "",
                generatedDraft = null,
                generatedArtifactId = null,
                refiningArtifact = null,
                lastRefinedFromArtifact = null,
                lastRefinedArtifactId = null,
                message = "New draft started",
            )
        }
    }

    fun saveApiKey() {
        val state = _uiState.value
        val trimmed = state.apiKeyInput.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(testStatus = AiProviderTestStatus.Failure, message = "Enter an API key before saving") }
            return
        }
        scope.launch {
            keyStore.saveKey(state.selectedProvider.providerId, SecretValue.of(trimmed))
            saveBaseUrlIfNeeded(state)
            _uiState.update {
                it.copy(
                    apiKeyInput = "",
                    hasSavedKey = true,
                        savedBaseUrl = state.baseUrlInput.takeIf { url -> state.selectedProvider == AiProviderChoice.LiteLLM && url.isNotBlank() }
                        ?: it.savedBaseUrl,
                    testStatus = AiProviderTestStatus.Idle,
                    message = "API key saved",
                )
            }
        }
    }

    fun testProvider() {
        val state = _uiState.value
        scope.launch {
            _uiState.update { it.copy(testStatus = AiProviderTestStatus.Running, message = null) }
            if (!keyStore.hasKey(state.selectedProvider.providerId)) {
                _uiState.update { it.copy(testStatus = AiProviderTestStatus.Failure, message = "Save an API key before testing") }
                return@launch
            }
            val provider = createProvider(state)
            val result = provider.test()
            _uiState.update { currentState ->
                result.fold(
                    onSuccess = { currentState.copy(testStatus = AiProviderTestStatus.Success, message = "${state.selectedProvider.label} ready") },
                    onFailure = { error -> currentState.copy(testStatus = AiProviderTestStatus.Failure, message = error.message ?: "AI key test failed") },
                )
            }
        }
    }

    fun refreshEntitlement() {
        scope.launch {
            entitlementRepository.refresh()
                .onFailure { error -> _uiState.update { it.copy(message = error.message ?: "Could not refresh entitlement") } }
        }
    }

    private fun refreshSavedKey() {
        val providerId = _uiState.value.selectedProvider.providerId
        scope.launch {
            val savedBaseUrl = keyStore.loadKey(baseUrlKey(providerId))?.revealForProviderCall().orEmpty()
            _uiState.update {
                it.copy(
                    hasSavedKey = keyStore.hasKey(providerId),
                    savedBaseUrl = savedBaseUrl,
                    baseUrlInput = savedBaseUrl,
                )
            }
        }
    }

    private fun createProvider(state: AiProviderSettingsState) =
        providerFactory.create(
            state.selectedProvider.providerId,
            state.savedBaseUrl.takeIf { state.selectedProvider == AiProviderChoice.LiteLLM && it.isNotBlank() },
        )

    private fun buildAgentContext(skillInstructions: String): String = buildString {
        appendLine(agentContext.trim())
        val skill = skillInstructions.trim()
        if (skill.isNotBlank()) {
            appendLine()
            appendLine("# User Tailored Skill")
            appendLine(skill)
        }
    }

    private suspend fun recordGeneration(
        state: AiProviderSettingsState,
        status: AiGenerationStatus,
        message: String,
        validationErrors: List<String> = emptyList(),
        artifactId: String? = null,
    ) {
        val record = AiGenerationRecord(
            id = "generation_${System.currentTimeMillis()}",
            providerId = state.selectedProvider.providerId,
            providerLabel = state.selectedProvider.label,
            modelId = state.selectedModel.id,
            modelLabel = state.selectedModel.label,
            draftKind = state.draftKind,
            status = status,
            message = message.take(MAX_GENERATION_MESSAGE_LENGTH),
            validationErrors = validationErrors.map { it.take(MAX_GENERATION_MESSAGE_LENGTH) },
            artifactId = artifactId,
        )
        generationHistoryRepository?.save(record)
        if (generationHistoryRepository == null) {
            _uiState.update { it.copy(generationHistory = (listOf(record) + it.generationHistory).take(MAX_LOCAL_HISTORY)) }
        }
    }

    private suspend fun saveBaseUrlIfNeeded(state: AiProviderSettingsState) {
        if (state.selectedProvider != AiProviderChoice.LiteLLM) return
        val baseUrl = state.baseUrlInput.trim()
        if (baseUrl.isBlank()) return
        keyStore.saveKey(baseUrlKey(state.selectedProvider.providerId), SecretValue.of(baseUrl))
    }
}

private fun baseUrlKey(providerId: String): String = "base_url_$providerId"

private const val MAX_GENERATION_MESSAGE_LENGTH = 800
private const val MAX_LOCAL_HISTORY = 120

private fun io.codecks.domain.ai.AiArtifactKind.toDraftKind(): DraftKind =
    when (this) {
        io.codecks.domain.ai.AiArtifactKind.Button,
        io.codecks.domain.ai.AiArtifactKind.Clock,
        -> DraftKind.Action
        io.codecks.domain.ai.AiArtifactKind.Deck -> DraftKind.Deck
        io.codecks.domain.ai.AiArtifactKind.Automation -> DraftKind.Automation
    }

private fun AiArtifact.toRefinementPrompt(changeRequest: String): String =
    buildString {
        appendLine("Refine this existing Codecks ${kind.label.lowercase()} draft.")
        appendLine("Requested change: $changeRequest")
        appendLine()
        appendLine("Existing draft:")
        appendLine("Title: $title")
        appendLine("Description: ${description.ifBlank { "None" }}")
        appendLine("Kind: ${kind.label}")
        appendLine("Actions:")
        actions.take(12).forEachIndexed { index, action ->
            appendLine("${index + 1}. ${action.title} | dangerous=${action.dangerous} | command=${action.command.take(200)}")
        }
        appendLine()
        appendLine("Return a complete replacement proposal. Do not mutate the saved draft.")
    }

private fun Throwable.generationStatus(): AiGenerationStatus =
    when (this) {
        is AiDraftProposalUnavailable -> when (status) {
            DraftEnvelopeStatus.Ready -> AiGenerationStatus.Ready
            DraftEnvelopeStatus.NeedsInput -> AiGenerationStatus.NeedsInput
            DraftEnvelopeStatus.Unsupported -> AiGenerationStatus.Unsupported
            DraftEnvelopeStatus.Refused -> AiGenerationStatus.Refused
        }
        is AiProviderException.Refused -> AiGenerationStatus.Refused
        is AiProviderException.UnsupportedModel -> AiGenerationStatus.Unsupported
        else -> AiGenerationStatus.Failed
    }

private fun String.aiDryRunError(): String? =
    when {
        isBlank() -> "Test failed: generated button has an empty command."
        length > 8_000 -> "Test failed: generated command is too large."
        else -> RawCommandPolicy.firstViolation(this)?.let { "Blocked: $it" }
    }
