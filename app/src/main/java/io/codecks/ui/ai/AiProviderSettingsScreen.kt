package io.codecks.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.codecks.BuildConfig
import io.codecks.core.design.DeckBridgeDesignTokens
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.toAiArtifact
import io.codecks.core.trackpad.TrackpadFloatingMenuLayout
import io.codecks.core.trackpad.TrackpadRailSide
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.actions.RawCommandPolicy
import io.codecks.data.ai.AiCredentialImporter
import io.codecks.data.ai.AiArtifactRepository
import io.codecks.data.ai.AiGenerationHistoryRepository
import io.codecks.data.ai.AiProviderException
import io.codecks.data.ai.AiProviderFactory
import io.codecks.data.ai.AndroidSecureApiKeyStore
import io.codecks.data.ai.DeckBridgeAiAgentPack
import io.codecks.data.ai.DefaultAiArtifactRepository
import io.codecks.data.ai.DefaultAiGenerationHistoryRepository
import io.codecks.data.ai.ImportedAiCredential
import io.codecks.data.ai.SecretValue
import io.codecks.data.ai.SecureApiKeyStore
import io.codecks.domain.ai.AiModel
import io.codecks.domain.ai.ActionCapability
import io.codecks.domain.ai.ActionDraftValidator
import io.codecks.domain.ai.AiDraftProposalUnavailable
import io.codecks.domain.ai.AiBuilder
import io.codecks.domain.ai.DraftKind
import io.codecks.domain.ai.DraftRequest
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.ai.AiProviderCatalog
import io.codecks.domain.ai.AiProviderSpec
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactTest
import io.codecks.domain.ai.AiArtifactTestStatus
import io.codecks.domain.ai.AiChatMessage
import io.codecks.domain.ai.AiChatRole
import io.codecks.domain.ai.AiGenerationRecord
import io.codecks.domain.ai.AiGenerationStatus
import io.codecks.domain.ai.DraftEnvelopeStatus
import io.codecks.domain.ai.FeatureGate
import io.codecks.domain.ai.SemanticDraftValidationException
import io.codecks.domain.features.Entitlement
import io.codecks.domain.features.EntitlementRepository
import io.codecks.domain.features.EntitlementStatus
import io.codecks.domain.features.EntitlementTier
import io.codecks.domain.DeckAction
import io.codecks.ui.designsystem.DeckFilterPill
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.theme.DeckBridgeAccent
import io.codecks.ui.theme.DeckBridgeBorderStyle
import io.codecks.ui.theme.DeckBridgeShapeStyle
import io.codecks.ui.theme.DeckBridgeSurfaceStyle
import io.codecks.ui.theme.DeckBridgeThemeMode
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
    Chat,
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
    val isImportingFromMac: Boolean = false,
    val pendingImportedCredential: ImportedAiCredential? = null,
    val entitlement: Entitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
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
    val messages: List<AiChatMessage> = listOf(aiWelcomeMessage()),
    val testingArtifactId: String? = null,
    val message: String? = null,
    val skillInstructions: String = "",
) {
    val selectedModel: AiModel =
        selectedProvider.models.firstOrNull { it.id == selectedModelId } ?: selectedProvider.models.first()
    val premiumAllowed: Boolean = entitlement.allows(FeatureGate.AiBuilder)
}

private fun aiWelcomeMessage(): AiChatMessage =
    AiChatMessage(
        id = "welcome",
        role = AiChatRole.Assistant,
        text = "Tell me what you want this Codecks button, deck, or automation to do. We will shape one artifact here, then Accept saves it disabled until you test and enable it.",
    )

class AiProviderSettingsController(
    private val keyStore: SecureApiKeyStore,
    private val providerFactory: AiProviderFactory,
    private val entitlementRepository: EntitlementRepository,
    private val scope: CoroutineScope,
    private val artifactRepository: AiArtifactRepository? = null,
    private val generationHistoryRepository: AiGenerationHistoryRepository? = null,
    private val actionRunner: ActionRunner? = null,
    private val macCredentialImporter: AiCredentialImporter? = null,
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
                pendingImportedCredential = null,
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
                pendingImportedCredential = null,
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
            val userMessage = AiChatMessage(
                id = "user_${System.currentTimeMillis()}",
                role = AiChatRole.User,
                text = prompt,
            )
            _uiState.update {
                it.copy(
                    generatedDraft = null,
                    message = null,
                    messages = it.messages + userMessage,
                )
            }
            localCommandHandler(prompt)?.let { result ->
                _uiState.update {
                    it.copy(
                        prompt = "",
                        isGenerating = false,
                        messages = it.messages + AiChatMessage(
                            id = "assistant_${System.currentTimeMillis()}",
                            role = AiChatRole.Assistant,
                            text = result.message,
                            artifactId = result.artifactId,
                            actionId = result.actionId,
                            actionLabel = result.actionLabel,
                        ),
                        message = result.message,
                    )
                }
                return@launch
            }
            val entitlement = entitlementRepository.currentEntitlement()
            _uiState.update { it.copy(entitlement = entitlement) }
            if (!entitlement.allows(FeatureGate.AiBuilder)) {
                _uiState.update { it.copy(message = "AI Creator is not enabled") }
                return@launch
            }
            if (!keyStore.hasKey(state.selectedProvider.providerId)) {
                _uiState.update { it.copy(message = "Save an API key before generating") }
                return@launch
            }
            if (state.draftKind != DraftKind.ContextApps && !state.selectedModel.supportsStructuredDrafts) {
                _uiState.update {
                    it.copy(message = "${state.selectedModel.label} is not enabled for strict AI Creator V2 drafts")
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
                                message = "Ready; dry run required before save",
                                artifactId = artifact.id,
                            )
                            val assistantMessage = AiChatMessage(
                                id = "assistant_${System.currentTimeMillis()}",
                                role = AiChatRole.Assistant,
                                text = "${artifact.title} preview updated. Edit the prompt and regenerate, or Accept to save disabled until tested.",
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
                                    messages = it.messages + assistantMessage,
                                    message = "Artifact preview updated",
                                )
                            }
                        },
                        onFailure = { error ->
                            val message = error.message ?: "Draft could not be converted into a runnable artifact"
                            recordGeneration(
                                state = state,
                                status = AiGenerationStatus.Failed,
                                message = message,
                                validationErrors = listOf(message),
                            )
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    messages = it.messages + AiChatMessage(
                                        id = "assistant_${System.currentTimeMillis()}",
                                        role = AiChatRole.Assistant,
                                        text = message,
                                    ),
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
                            messages = it.messages + AiChatMessage(
                                id = "assistant_${System.currentTimeMillis()}",
                                role = AiChatRole.Assistant,
                                text = message,
                            ),
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
            _uiState.update { it.copy(message = "Artifact has no runnable action") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(testingArtifactId = artifactId, message = null) }
            val validationError = artifact.actions.firstNotNullOfOrNull { it.command.aiDryRunError() }
            var finalStatus = if (validationError == null) AiArtifactTestStatus.Succeeded else AiArtifactTestStatus.Failed
            var finalMessage = validationError ?: "${artifact.title} dry run passed. No Mac command was executed."
            if (validationError == null) {
                if (artifact.actions.any { it.dangerous }) {
                    finalStatus = AiArtifactTestStatus.RequiresConfirmation
                    finalMessage = "${artifact.title} passed validation. It needs confirmation before running."
                }
            }
            val test = AiArtifactTest(finalStatus, finalMessage)
            artifactRepository?.save(artifact.copy(lastTest = test))
            _uiState.update {
                it.copy(
                    testingArtifactId = null,
                    artifacts = it.artifacts.map { item -> if (item.id == artifactId) item.copy(lastTest = test) else item },
                    messages = it.messages + AiChatMessage(
                        id = "assistant_${System.currentTimeMillis()}",
                        role = AiChatRole.Assistant,
                        text = finalMessage,
                        artifactId = artifactId,
                    ),
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
                    message = "Artifact removed",
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
                messages = listOf(aiWelcomeMessage()),
                message = "Saved disabled. Test it from Deck or Rules before enabling.",
            )
        }
    }

    fun newChat() {
        _uiState.update {
            it.copy(
                prompt = "",
                generatedDraft = null,
                generatedArtifactId = null,
                refiningArtifact = null,
                lastRefinedFromArtifact = null,
                lastRefinedArtifactId = null,
                messages = listOf(aiWelcomeMessage()),
                message = "New artifact started",
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
                    pendingImportedCredential = null,
                    savedBaseUrl = state.baseUrlInput.takeIf { url -> state.selectedProvider == AiProviderChoice.LiteLLM && url.isNotBlank() }
                        ?: it.savedBaseUrl,
                    testStatus = AiProviderTestStatus.Idle,
                    message = "API key saved",
                )
            }
        }
    }

    fun importFromMac() {
        val importer = macCredentialImporter
        if (importer == null) {
            _uiState.update { it.copy(message = "Connect Mac before importing AI credentials") }
            return
        }
        val state = _uiState.value
        scope.launch {
            _uiState.update { it.copy(isImportingFromMac = true, message = null, testStatus = AiProviderTestStatus.Idle) }
            importer.importCredential(state.selectedProvider.providerId).fold(
                onSuccess = { credential ->
                    _uiState.update {
                        it.copy(
                            isImportingFromMac = false,
                            pendingImportedCredential = credential,
                            baseUrlInput = credential.baseUrl.orEmpty(),
                            message = "Found ${state.selectedProvider.label} key from ${credential.source}. Tap Save imported key to store it.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isImportingFromMac = false,
                            message = error.message ?: "Could not import from Mac",
                        )
                    }
                },
            )
        }
    }

    fun confirmImportedCredential() {
        val credential = _uiState.value.pendingImportedCredential
        if (credential == null) {
            _uiState.update { it.copy(message = "Import from Mac first") }
            return
        }
        scope.launch {
            keyStore.saveKey(credential.providerId, credential.key)
            if (!credential.baseUrl.isNullOrBlank()) {
                keyStore.saveKey(baseUrlKey(credential.providerId), SecretValue.of(credential.baseUrl))
            }
            _uiState.update {
                it.copy(
                    hasSavedKey = it.selectedProvider.providerId == credential.providerId || it.hasSavedKey,
                    pendingImportedCredential = null,
                    savedBaseUrl = credential.baseUrl.orEmpty(),
                    baseUrlInput = credential.baseUrl.orEmpty(),
                    testStatus = AiProviderTestStatus.Idle,
                    message = "Imported ${it.selectedProvider.label} key from ${credential.source}",
                )
            }
        }
    }

    fun testProvider() {
        val state = _uiState.value
        scope.launch {
            val entitlement = entitlementRepository.currentEntitlement()
            _uiState.update { it.copy(entitlement = entitlement) }
            if (!entitlement.allows(FeatureGate.AiBuilder)) {
                _uiState.update { it.copy(testStatus = AiProviderTestStatus.Failure, message = "AI Creator is not enabled") }
                return@launch
            }
            _uiState.update { it.copy(testStatus = AiProviderTestStatus.Running, message = null) }
            if (!keyStore.hasKey(state.selectedProvider.providerId)) {
                _uiState.update { it.copy(testStatus = AiProviderTestStatus.Failure, message = "Save an API key before testing") }
                return@launch
            }
            val provider = createProvider(state)
            val result = provider.test()
            _uiState.update { currentState ->
                result.fold(
                    onSuccess = { currentState.copy(testStatus = AiProviderTestStatus.Success, message = "Connected to ${state.selectedProvider.label}") },
                    onFailure = { error -> currentState.copy(testStatus = AiProviderTestStatus.Failure, message = error.message ?: "Provider test failed") },
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
        appendLine("Existing artifact:")
        appendLine("Title: $title")
        appendLine("Description: ${description.ifBlank { "None" }}")
        appendLine("Kind: ${kind.label}")
        appendLine("Actions:")
        actions.take(12).forEachIndexed { index, action ->
            appendLine("${index + 1}. ${action.title} | dangerous=${action.dangerous} | command=${action.command.take(200)}")
        }
        appendLine()
        appendLine("Return a complete replacement V2 proposal. Do not mutate the saved artifact.")
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

private suspend fun handleLocalAiCommand(
    prompt: String,
    actions: List<DeckAction>,
    onRunAction: (DeckAction) -> Unit,
    trackpadSettings: TrackpadSettings,
    onTrackpadSettingsChange: (((TrackpadSettings) -> TrackpadSettings) -> Unit)?,
    onThemeModeChange: (DeckBridgeThemeMode) -> Unit,
    onThemeAccentChange: (DeckBridgeAccent) -> Unit,
    onThemeSurfaceStyleChange: (DeckBridgeSurfaceStyle) -> Unit,
    onThemeBorderStyleChange: (DeckBridgeBorderStyle) -> Unit,
    onThemeShapeStyleChange: (DeckBridgeShapeStyle) -> Unit,
    onOpenDeck: () -> Unit,
    onOpenTrackpad: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiSettings: () -> Unit,
): AiLocalCommandResult? {
    val text = prompt.normalizedCommandText()
    if (text.isBlank()) return null

    fun updateTrackpad(
        @Suppress("UNUSED_PARAMETER") message: String,
        @Suppress("UNUSED_PARAMETER") transform: (TrackpadSettings) -> TrackpadSettings,
    ): AiLocalCommandResult? {
        onOpenSettings()
        return AiLocalCommandResult("Opened Settings. Use Trackpad behavior to change this; AI did not change the setting.")
    }

    if (text.hasAnyPhrase("double tap")) {
        if (text.hasAnyWord("fast", "high", "quick", "sensitive")) {
            val next = (trackpadSettings.doubleTapTimeoutMillis + 100).coerceAtMost(900)
            return updateTrackpad("Double tap is less twitchy now: ${next}ms timeout.") {
                it.copy(doubleTapTimeoutMillis = next)
            }
        }
        if (text.hasAnyWord("slow", "slower", "low")) {
            val next = (trackpadSettings.doubleTapTimeoutMillis - 100).coerceAtLeast(350)
            return updateTrackpad("Double tap is faster now: ${next}ms timeout.") {
                it.copy(doubleTapTimeoutMillis = next)
            }
        }
    }

    if (text.hasAnyWord("trackpad", "mouse", "pointer", "sensitivity", "cursor")) {
        if (text.hasAnyWord("fast", "high", "sensitive", "jittery", "reduce", "slower", "down")) {
            val next = (trackpadSettings.pointerSpeed - 0.08f).coerceIn(0.3f, 1.35f)
            return updateTrackpad("Trackpad pointer speed reduced to ${next.asSettingValue()}.") {
                it.copy(pointerSpeed = next)
            }
        }
        if (text.hasAnyWord("slow", "increase", "faster", "up")) {
            val next = (trackpadSettings.pointerSpeed + 0.08f).coerceIn(0.3f, 1.35f)
            return updateTrackpad("Trackpad pointer speed increased to ${next.asSettingValue()}.") {
                it.copy(pointerSpeed = next)
            }
        }
    }

    if (text.hasAnyWord("scroll", "rail")) {
        if (text.hasAnyWord("left")) {
            return updateTrackpad("Scroll rail moved to the left side.") { it.copy(railSide = TrackpadRailSide.Left) }
        }
        if (text.hasAnyWord("right")) {
            return updateTrackpad("Scroll rail moved to the right side.") { it.copy(railSide = TrackpadRailSide.Right) }
        }
        if (text.hasAnyWord("off", "disable", "hide")) {
            return updateTrackpad("Scroll rail disabled.") { it.copy(scrollRailEnabled = false) }
        }
        if (text.hasAnyWord("on", "enable", "show")) {
            return updateTrackpad("Scroll rail enabled.") { it.copy(scrollRailEnabled = true) }
        }
        if (text.hasAnyWord("fast", "high", "reduce", "slower", "down")) {
            val next = (trackpadSettings.scrollSpeed - 0.1f).coerceIn(0.35f, 1.8f)
            return updateTrackpad("Scroll speed reduced to ${next.asSettingValue()}.") { it.copy(scrollSpeed = next) }
        }
        if (text.hasAnyWord("slow", "increase", "faster", "up")) {
            val next = (trackpadSettings.scrollSpeed + 0.1f).coerceIn(0.35f, 1.8f)
            return updateTrackpad("Scroll speed increased to ${next.asSettingValue()}.") { it.copy(scrollSpeed = next) }
        }
    }

    if (text.hasAnyWord("haptic", "haptics", "vibration", "vibrate")) {
        if (text.hasAnyWord("off", "disable")) {
            return updateTrackpad("Trackpad haptics disabled.") { it.copy(hapticsEnabled = false) }
        }
        if (text.hasAnyWord("on", "enable")) {
            return updateTrackpad("Trackpad haptics enabled.") { it.copy(hapticsEnabled = true) }
        }
    }

    if (text.hasAnyPhrase("floating menu", "action menu", "menu layout")) {
        if (text.hasAnyWord("horizontal")) {
            return updateTrackpad("Trackpad menu set to horizontal.") { it.copy(floatingMenuLayout = TrackpadFloatingMenuLayout.Horizontal) }
        }
        if (text.hasAnyWord("vertical")) {
            return updateTrackpad("Trackpad menu set to vertical.") { it.copy(floatingMenuLayout = TrackpadFloatingMenuLayout.Vertical) }
        }
    }

    if (text.hasAnyWord("theme", "color", "colors", "bland", "oled", "dark", "light", "accent", "border", "shape")) {
        onOpenSettings()
        return AiLocalCommandResult("Opened Settings. Use Appearance to change theme, color, borders, and shape; AI did not change them.")
    }

    if (text.hasAnyPhrase("ai settings", "api key", "provider", "model")) {
        onOpenAiSettings()
        return AiLocalCommandResult("Opened AI provider settings.")
    }
    if (text.hasAnyPhrase("feature flag", "feature flags", "labs", "settings")) {
        onOpenSettings()
        return AiLocalCommandResult("Opened Settings.")
    }
    if (text.hasAnyWord("trackpad", "mouse")) {
        onOpenTrackpad()
        return AiLocalCommandResult("Opened Trackpad.")
    }
    if (text.hasAnyWord("deck", "buttons")) {
        onOpenDeck()
        return AiLocalCommandResult("Opened Deck.")
    }

    val creationIntent = text.hasAnyWord("create", "make", "build", "generate", "new") &&
        text.hasAnyWord("button", "deck", "automation", "workflow")
    if (creationIntent) return null

    val matchedAction = actions
        .map { it to it.aiCommandScore(text) }
        .filter { (_, score) -> score > 0 }
        .maxByOrNull { (_, score) -> score }
        ?.first
    if (matchedAction != null) {
        val shouldRun = text.hasAnyWord("trigger", "run", "execute", "press", "tap", "click")
        if (shouldRun) {
            return AiLocalCommandResult(
                message = "Found ${matchedAction.label}. Open it on Deck and press Run there.",
                actionId = matchedAction.id,
                actionLabel = matchedAction.label,
            )
        }
        return AiLocalCommandResult(
            message = "Found ${matchedAction.label}. Open it on Deck, or say \"run ${matchedAction.label}\" to trigger it.",
            actionId = matchedAction.id,
            actionLabel = matchedAction.label,
        )
    }

    return null
}

private fun DeckAction.aiCommandScore(query: String): Int {
    val labelText = label.normalizedCommandText()
    val idText = id.normalizedCommandText()
    val labelWords = labelText.matchWords()
    val idWords = idText.matchWords()
    val queryWords = query.matchWords()
    return when {
        labelText.isNotBlank() && query.contains(labelText) -> 120
        idText.isNotBlank() && query.contains(idText) -> 110
        labelWords.isNotEmpty() && queryWords.containsAll(labelWords) -> 95
        idWords.isNotEmpty() && queryWords.containsAll(idWords) -> 90
        labelWords.size == 1 && labelWords.any(queryWords::contains) -> 80
        idWords.size == 1 && idWords.any(queryWords::contains) -> 75
        else -> 0
    }
}

private fun String.normalizedCommandText(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private fun String.hasAnyWord(vararg words: String): Boolean =
    words.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(this) }

private fun String.hasAnyPhrase(vararg phrases: String): Boolean =
    phrases.any { phrase -> contains(phrase.normalizedCommandText()) }

private fun String.matchWords(): Set<String> =
    split(" ")
        .filter { it.length >= 3 && it !in setOf("button", "buttons", "deck", "control", "controls") }
        .toSet()

private fun Float.asSettingValue(): String = "%.2f".format(this)

private fun String.aiDryRunError(): String? =
    when {
        isBlank() -> "Dry run failed: generated action has an empty command."
        length > 8_000 -> "Dry run failed: generated command is too large."
        else -> RawCommandPolicy.firstAllowlistViolation(this)?.let { "Needs manual review: $it" }
    }

@Composable
fun AiProviderSettingsRoute(
    entitlementRepository: EntitlementRepository,
    contentPadding: PaddingValues,
    actionRunner: ActionRunner? = null,
    mode: AiWorkspaceMode = AiWorkspaceMode.Chat,
    availableActions: List<DeckAction> = emptyList(),
    onRunAction: (DeckAction) -> Unit = {},
    trackpadSettings: TrackpadSettings = TrackpadSettings(),
    onTrackpadSettingsChange: (((TrackpadSettings) -> TrackpadSettings) -> Unit)? = null,
    onThemeModeChange: (DeckBridgeThemeMode) -> Unit = {},
    onThemeAccentChange: (DeckBridgeAccent) -> Unit = {},
    onThemeSurfaceStyleChange: (DeckBridgeSurfaceStyle) -> Unit = {},
    onThemeBorderStyleChange: (DeckBridgeBorderStyle) -> Unit = {},
    onThemeShapeStyleChange: (DeckBridgeShapeStyle) -> Unit = {},
    onOpenDeck: () -> Unit = {},
    onOpenTrackpad: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onOpenAction: (String) -> Unit = { onOpenDeck() },
    onSaveDraft: (GeneratedDraft) -> Unit = {},
    onSaveArtifact: (AiArtifact) -> Unit = {},
    contextAppsEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    keyStore: SecureApiKeyStore? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolvedKeyStore = remember(context, keyStore) { keyStore ?: AndroidSecureApiKeyStore(context.applicationContext) }
    val providerFactory = remember(resolvedKeyStore) {
        AiProviderFactory(resolvedKeyStore, liteLlmBaseUrl = BuildConfig.LITELLM_BASE_URL)
    }
    val artifactRepository = remember(context) { DefaultAiArtifactRepository(context.applicationContext) }
    val generationHistoryRepository = remember(context) { DefaultAiGenerationHistoryRepository(context.applicationContext) }
    val agentPack = remember(context) { DeckBridgeAiAgentPack.load(context.applicationContext) }
    val currentActions by rememberUpdatedState(availableActions)
    val currentTrackpadSettings by rememberUpdatedState(trackpadSettings)
    val localCommandHandler: suspend (String) -> AiLocalCommandResult? = remember(
        onRunAction,
        onTrackpadSettingsChange,
        onThemeModeChange,
        onThemeAccentChange,
        onThemeSurfaceStyleChange,
        onThemeBorderStyleChange,
        onThemeShapeStyleChange,
        onOpenDeck,
        onOpenTrackpad,
        onOpenSettings,
        onOpenAiSettings,
    ) {
        { prompt ->
            handleLocalAiCommand(
                prompt = prompt,
                actions = currentActions,
                onRunAction = onRunAction,
                trackpadSettings = currentTrackpadSettings,
                onTrackpadSettingsChange = onTrackpadSettingsChange,
                onThemeModeChange = onThemeModeChange,
                onThemeAccentChange = onThemeAccentChange,
                onThemeSurfaceStyleChange = onThemeSurfaceStyleChange,
                onThemeBorderStyleChange = onThemeBorderStyleChange,
                onThemeShapeStyleChange = onThemeShapeStyleChange,
                onOpenDeck = onOpenDeck,
                onOpenTrackpad = onOpenTrackpad,
                onOpenSettings = onOpenSettings,
                onOpenAiSettings = onOpenAiSettings,
            )
        }
    }
    val controller = remember(
        resolvedKeyStore,
        entitlementRepository,
        artifactRepository,
        generationHistoryRepository,
        actionRunner,
        agentPack,
        localCommandHandler,
    ) {
        AiProviderSettingsController(
            keyStore = resolvedKeyStore,
            providerFactory = providerFactory,
            entitlementRepository = entitlementRepository,
            scope = scope,
            artifactRepository = artifactRepository,
            generationHistoryRepository = generationHistoryRepository,
            actionRunner = actionRunner,
            agentContext = buildString {
                appendLine("# Bundled Codecks AI Agent")
                appendLine(agentPack.agent.trim())
                appendLine()
                appendLine("# Bundled Schema")
                appendLine(agentPack.schema.trim())
            },
            initialSkillInstructions = agentPack.skill,
            localCommandHandler = localCommandHandler,
        )
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    val state by controller.uiState.collectAsState()
    AiProviderSettingsScreen(
        state = state,
        mode = mode,
        contentPadding = contentPadding,
        onProviderSelected = controller::selectProvider,
        onModelSelected = controller::selectModel,
        onApiKeyChanged = controller::setApiKey,
        onBaseUrlChanged = controller::setBaseUrl,
        onSaveApiKey = controller::saveApiKey,
        onConfirmImportedCredential = controller::confirmImportedCredential,
        onTest = controller::testProvider,
        onRefreshEntitlement = controller::refreshEntitlement,
        onPromptChanged = controller::setPrompt,
        onDraftKindChanged = controller::setDraftKind,
        onSkillInstructionsChanged = controller::setSkillInstructions,
        onGenerate = controller::generateDraft,
        onRefineArtifact = controller::startRefinement,
        onCancelRefinement = controller::cancelRefinement,
        onSaveDraft = { draft ->
            onSaveDraft(draft)
            controller.markDraftSaved()
        },
        onSaveArtifact = { artifact ->
            onSaveArtifact(artifact)
            controller.markDraftSaved()
        },
        onTestArtifact = controller::testArtifact,
        onDeleteArtifact = controller::deleteArtifact,
        onActionLink = onOpenAction,
        onOpenAiSettings = onOpenAiSettings,
        onNewChat = controller::newChat,
        contextAppsEnabled = contextAppsEnabled,
        modifier = modifier,
    )
}

@Composable
fun AiProviderSettingsScreen(
    state: AiProviderSettingsState,
    mode: AiWorkspaceMode = AiWorkspaceMode.Chat,
    contentPadding: PaddingValues,
    onProviderSelected: (AiProviderChoice) -> Unit,
    onModelSelected: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onConfirmImportedCredential: () -> Unit,
    onTest: () -> Unit,
    onRefreshEntitlement: () -> Unit,
    onPromptChanged: (String) -> Unit,
    onDraftKindChanged: (DraftKind) -> Unit,
    onSkillInstructionsChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onRefineArtifact: (String) -> Unit,
    onCancelRefinement: () -> Unit,
    onSaveDraft: (GeneratedDraft) -> Unit,
    onSaveArtifact: (AiArtifact) -> Unit,
    onTestArtifact: (String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    onActionLink: (String) -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onNewChat: () -> Unit = {},
    contextAppsEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val providerSettingsOpen = mode == AiWorkspaceMode.ProviderSettings
    val draftKinds = remember(contextAppsEnabled) {
        DraftKind.entries.filter { contextAppsEnabled || it != DraftKind.ContextApps }
    }
    LaunchedEffect(contextAppsEnabled, state.draftKind) {
        if (!contextAppsEnabled && state.draftKind == DraftKind.ContextApps) {
            onDraftKindChanged(DraftKind.Action)
        }
    }
    val currentArtifact = state.generatedArtifactId
        ?.let { id -> state.artifacts.firstOrNull { it.id == id } }
    val previousArtifacts = state.artifacts.filterNot { it.id == currentArtifact?.id }
    if (mode == AiWorkspaceMode.Chat) {
        AiArtifactWorkspaceScreen(
            state = state,
            draftKinds = draftKinds,
            currentArtifact = currentArtifact,
            previousArtifacts = previousArtifacts,
            contentPadding = contentPadding,
            onDraftKindChanged = onDraftKindChanged,
            onSkillInstructionsChanged = onSkillInstructionsChanged,
            onPromptChanged = onPromptChanged,
            onGenerate = onGenerate,
            onCancelRefinement = onCancelRefinement,
            onTestArtifact = onTestArtifact,
            onSaveDraft = onSaveDraft,
            onRefineArtifact = onRefineArtifact,
            onDeleteArtifact = onDeleteArtifact,
            onOpenAiSettings = onOpenAiSettings,
            onNewChat = onNewChat,
            modifier = modifier,
        )
        return
    }
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        if (providerSettingsOpen) {
            aiProviderSettingsItems(
                state = state,
                onProviderSelected = onProviderSelected,
                onModelSelected = onModelSelected,
                onApiKeyChanged = onApiKeyChanged,
                onBaseUrlChanged = onBaseUrlChanged,
                onSaveApiKey = onSaveApiKey,
                onConfirmImportedCredential = onConfirmImportedCredential,
                onTest = onTest,
                onRefreshEntitlement = onRefreshEntitlement,
            )
        }
        if (state.message != null) {
            item {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.testStatus == AiProviderTestStatus.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        item {
            Text(
                text = if (mode == AiWorkspaceMode.Chat) {
                    "Accept saves the current artifact disabled/unverified. Test it before enabling or running it."
                } else {
                    "Provider keys are stored encrypted on this phone. Paste keys manually; SSH key scraping is disabled."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun AiArtifactWorkspaceScreen(
    state: AiProviderSettingsState,
    draftKinds: List<DraftKind>,
    currentArtifact: AiArtifact?,
    previousArtifacts: List<AiArtifact>,
    contentPadding: PaddingValues,
    onDraftKindChanged: (DraftKind) -> Unit,
    onSkillInstructionsChanged: (String) -> Unit,
    onPromptChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancelRefinement: () -> Unit,
    onTestArtifact: (String) -> Unit,
    onSaveDraft: (GeneratedDraft) -> Unit,
    onRefineArtifact: (String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.fillMaxSize())
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 260.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = DeckBridgeDesignTokens.Size.sheetMaxWidth),
        ) {
            item {
                AiWorkspaceHero(
                    providerLabel = "${state.selectedProvider.label} · ${state.selectedModel.label}",
                    ready = state.premiumAllowed && state.hasSavedKey,
                    artifactCount = state.artifacts.size,
                    onOpenAiSettings = onOpenAiSettings,
                    onNewArtifact = onNewChat,
                    hasActiveArtifact = currentArtifact != null || state.prompt.isNotBlank(),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item {
                AiSkillStrip(
                    draftKinds = draftKinds,
                    selected = state.draftKind,
                    onSelected = onDraftKindChanged,
                    skillInstructions = state.skillInstructions,
                    onSkillInstructionsChanged = onSkillInstructionsChanged,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (currentArtifact != null) {
                item {
                    Text(
                        text = "Result preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                item {
                    val artifact = currentArtifact
                    val draftToSave = state.generatedDraft.takeIf { state.generatedArtifactId == artifact.id }
                    AiArtifactCard(
                        artifact = artifact,
                        refinementSource = state.lastRefinedFromArtifact.takeIf { state.lastRefinedArtifactId == artifact.id },
                        isTesting = state.testingArtifactId == artifact.id,
                        onTest = { onTestArtifact(artifact.id) },
                        onSave = draftToSave?.let {
                            { onSaveDraft(it) }
                        },
                        saveLabel = "Accept disabled",
                        onRefine = { onRefineArtifact(artifact.id) },
                        onDelete = { onDeleteArtifact(artifact.id) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                item {
                    AiEmptyArtifactPreview(
                        selected = state.draftKind,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (previousArtifacts.isNotEmpty()) {
                item {
                    Text(
                        text = "Saved drafts",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
            }
            previousArtifacts.take(6).forEach { artifact ->
                item {
                    AiArtifactHistoryCard(
                        artifact = artifact,
                        onRestore = { onRefineArtifact(artifact.id) },
                        onDelete = { onDeleteArtifact(artifact.id) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (state.generationHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Generation log",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
            }
            state.generationHistory.take(4).forEach { record ->
                item {
                    AiGenerationRecordCard(
                        record = record,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            item {
                Text(
                    text = "Accept saves the current artifact disabled/unverified. Test it before enabling or running it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        AiBottomPromptComposer(
            state = state,
            currentArtifact = currentArtifact,
            onPromptChanged = onPromptChanged,
            onGenerate = onGenerate,
            onCancelRefinement = onCancelRefinement,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = DeckBridgeDesignTokens.Size.sheetMaxWidth)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AiWorkspaceHero(
    providerLabel: String,
    ready: Boolean,
    artifactCount: Int,
    onOpenAiSettings: () -> Unit,
    onNewArtifact: () -> Unit,
    hasActiveArtifact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("AI builder", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${if (ready) "Ready" else "Provider setup needed"} · $providerLabel · $artifactCount saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!ready) {
                DeckActionButton(
                    label = "Open AI settings",
                    onClick = onOpenAiSettings,
                    icon = Icons.Outlined.Settings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
            }
            if (hasActiveArtifact) {
                DeckActionButton(
                    label = "New artifact",
                    onClick = onNewArtifact,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
            }
        }
    }
}

@Composable
private fun AiSkillStrip(
    draftKinds: List<DraftKind>,
    selected: DraftKind,
    onSelected: (DraftKind) -> Unit,
    skillInstructions: String,
    onSkillInstructionsChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var skillEditorOpen by remember { mutableStateOf(false) }
    CodecksPanel(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Text("Skill", style = MaterialTheme.typography.titleSmall)
            Text(
                text = selected.skillDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                draftKinds.forEach { kind ->
                    DeckFilterPill(
                        label = kind.skillLabel,
                        selected = selected == kind,
                        onClick = { onSelected(kind) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
            DeckActionButton(
                label = if (skillEditorOpen) "Hide skill" else "Edit skill",
                onClick = { skillEditorOpen = !skillEditorOpen },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
            if (skillEditorOpen) {
                OutlinedTextField(
                    value = skillInstructions,
                    onValueChange = onSkillInstructionsChanged,
                    label = { Text("Editable skill instructions") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = "This skill text is sent with each generation. Edit it here to tailor how Codecks builds artifacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiEmptyArtifactPreview(
    selected: DraftKind,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(18.dp)) {
            Text("No result yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick a skill, write one instruction below, then generate. The preview appears here; edit the prompt and regenerate until it is right.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                selected.placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AiBottomPromptComposer(
    state: AiProviderSettingsState,
    currentArtifact: AiArtifact?,
    onPromptChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancelRefinement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.draftKind.skillLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (currentArtifact == null) "New artifact" else "Edit prompt → regenerate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChanged,
                label = { Text("Instruction") },
                placeholder = { Text(state.draftKind.placeholder) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            state.refiningArtifact?.let { artifact ->
                Text(
                    text = "Loaded: ${artifact.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.testStatus == AiProviderTestStatus.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isGenerating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckActionButton(
                    label = when {
                        state.isGenerating -> "Thinking…"
                        currentArtifact != null || state.refiningArtifact != null -> "Regenerate"
                        else -> "Generate"
                    },
                    onClick = onGenerate,
                    enabled = state.prompt.isNotBlank() && !state.isGenerating,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                )
                if (state.refiningArtifact != null) {
                    DeckActionButton(
                        label = "Clear",
                        onClick = onCancelRefinement,
                        enabled = !state.isGenerating,
                        icon = Icons.Outlined.ErrorOutline,
                        modifier = Modifier.weight(0.7f).heightIn(min = 52.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiArtifactCard(
    artifact: AiArtifact,
    refinementSource: AiArtifact?,
    isTesting: Boolean,
    onTest: () -> Unit,
    onSave: (() -> Unit)?,
    saveLabel: String? = null,
    onRefine: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(artifact.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${artifact.kind.label} · ${artifact.actions.size} action${if (artifact.actions.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (artifact.description.isNotBlank()) {
                Text(
                    artifact.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            refinementSource?.let { source ->
                AiRefinementDiffCard(before = source, after = artifact)
            }
            AiArtifactReviewPanel(artifact = artifact)
            artifact.actions.forEachIndexed { index, action ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${index + 1}. ${action.title}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            action.command.ifBlank { "No command" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Review before accepting. Dry run validates generated shell and never executes it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            artifact.lastTest?.let { test ->
                Text(
                    text = test.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (test.status) {
                        AiArtifactTestStatus.Succeeded -> MaterialTheme.colorScheme.primary
                        AiArtifactTestStatus.Failed -> MaterialTheme.colorScheme.error
                        AiArtifactTestStatus.RequiresConfirmation -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckActionButton(
                    label = if (isTesting) "Dry running" else "Dry run",
                    onClick = onTest,
                    enabled = !isTesting,
                    icon = Icons.Outlined.PlayArrow,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                if (onSave != null) {
                    DeckActionButton(
                        label = saveLabel ?: if (artifact.kind == io.codecks.domain.ai.AiArtifactKind.Deck) "Save deck" else "Save",
                        onClick = onSave,
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
                DeckActionButton(
                    label = "Refine",
                    onClick = onRefine,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                DeckActionButton(
                    label = "Remove",
                    onClick = onDelete,
                    icon = Icons.Outlined.ErrorOutline,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun AiArtifactHistoryCard(
    artifact: AiArtifact,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(artifact.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${artifact.kind.label} · ${artifact.actions.size} action${if (artifact.actions.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (artifact.description.isNotBlank()) {
                Text(
                    artifact.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckActionButton(
                    label = "Load prompt",
                    onClick = onRestore,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                DeckActionButton(
                    label = "Remove",
                    onClick = onDelete,
                    icon = Icons.Outlined.ErrorOutline,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun AiArtifactReviewPanel(
    artifact: AiArtifact,
    modifier: Modifier = Modifier,
) {
    val review = artifact.review
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (review.requiresConfirmation) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            Text("Proposal review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            AiReviewLine(
                label = "Risk",
                value = if (review.requiresConfirmation) {
                    "${review.riskLevel.name} · confirmation required"
                } else {
                    review.riskLevel.name
                },
            )
            AiReviewLine(label = "Target", value = review.target)
            review.trigger?.let { trigger -> AiReviewLine(label = "Trigger", value = trigger) }
            AiReviewList(
                label = "Assumptions",
                values = review.assumptions.ifEmpty { listOf("No extra assumptions supplied") },
            )
            AiReviewList(
                label = "Capabilities",
                values = review.requiredCapabilities.ifEmpty { listOf("No special capability requested") },
            )
            AiReviewList(
                label = "Parameters",
                values = review.parameters.map { parameter ->
                    buildString {
                        append(parameter.label.ifBlank { parameter.name })
                        append(if (parameter.required) " · required" else " · optional")
                        parameter.defaultValue?.takeIf { it.isNotBlank() }?.let { append(" · default: $it") }
                    }
                }.ifEmpty { listOf("No runtime parameters") },
            )
            AiReviewList(
                label = "Compiled steps",
                values = review.steps.mapIndexed { index, step ->
                    "${index + 1}. ${step.label} · ${step.type} · ${step.summary}" +
                        if (step.requiresConfirmation) " · confirms" else ""
                }.ifEmpty { listOf("No compiled steps") },
            )
        }
    }
}

@Composable
private fun AiReviewLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AiReviewList(
    label: String,
    values: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEach { value ->
            Text("• $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val io.codecks.domain.ai.AiArtifactKind.label: String
    get() = when (this) {
        io.codecks.domain.ai.AiArtifactKind.Button -> "Button"
        io.codecks.domain.ai.AiArtifactKind.Deck -> "Deck"
        io.codecks.domain.ai.AiArtifactKind.Automation -> "Automation"
        io.codecks.domain.ai.AiArtifactKind.Clock -> "Clock"
    }

@Composable
private fun AiRefinementDiffCard(
    before: AiArtifact,
    after: AiArtifact,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
            Text("Refinement compare", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Before: ${before.title} · ${before.actions.size} action${if (before.actions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "After: ${after.title} · ${after.actions.size} action${if (after.actions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = after.changedActionSummary(before),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun AiArtifact.changedActionSummary(before: AiArtifact): String {
    val beforeCommands = before.actions.associateBy { it.id }
    val changed = actions.filter { action -> beforeCommands[action.id]?.command != action.command }
    return when {
        changed.isEmpty() && actions.size == before.actions.size -> "No command-level changes detected; review labels and descriptions."
        changed.isEmpty() -> "Action count changed from ${before.actions.size} to ${actions.size}."
        else -> "Changed: ${changed.take(3).joinToString { it.title }}${if (changed.size > 3) " +${changed.size - 3}" else ""}"
    }
}

@Composable
private fun AiGenerationRecordCard(
    record: AiGenerationRecord,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.status.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = record.status.color(),
                )
                Text(
                    text = "${record.draftKind.label} · ${record.providerLabel} · ${record.modelLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = record.message.ifBlank { "No message" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (record.validationErrors.isNotEmpty()) {
                Text(
                    text = record.validationErrors.take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private val AiGenerationStatus.label: String
    get() = when (this) {
        AiGenerationStatus.Ready -> "Ready"
        AiGenerationStatus.NeedsInput -> "Needs input"
        AiGenerationStatus.Unsupported -> "Unsupported"
        AiGenerationStatus.Refused -> "Refused"
        AiGenerationStatus.Failed -> "Failed"
    }

@Composable
private fun AiGenerationStatus.color() =
    when (this) {
        AiGenerationStatus.Ready -> MaterialTheme.colorScheme.primary
        AiGenerationStatus.NeedsInput -> MaterialTheme.colorScheme.secondary
        AiGenerationStatus.Unsupported -> MaterialTheme.colorScheme.onSurfaceVariant
        AiGenerationStatus.Refused -> MaterialTheme.colorScheme.error
        AiGenerationStatus.Failed -> MaterialTheme.colorScheme.error
    }

private val DraftKind.label: String
    get() = when (this) {
        DraftKind.Action -> "Button"
        DraftKind.Deck -> "Deck"
        DraftKind.Automation -> "Automation"
        DraftKind.ContextApps -> "Context apps"
    }

private val DraftKind.skillLabel: String
    get() = when (this) {
        DraftKind.Action -> "Button"
        DraftKind.Deck -> "Deck"
        DraftKind.Automation -> "Rule"
        DraftKind.ContextApps -> "Context"
    }

private val DraftKind.skillDescription: String
    get() = when (this) {
        DraftKind.Action -> "Create one safe Deck button from a plain-English task."
        DraftKind.Deck -> "Create a grouped Deck of related buttons."
        DraftKind.Automation -> "Create a disabled rule/manual automation from a task."
        DraftKind.ContextApps -> "Suggest context-aware app/action lanes from local signals."
    }

private val DraftKind.placeholder: String
    get() = when (this) {
        DraftKind.Action -> "Open Linear and start my daily standup note"
        DraftKind.Deck -> "Build a coding deck with browser, terminal, GitHub, music, and focus controls"
        DraftKind.Automation -> "When I start a meeting, open notes, calendar, and mute media"
        DraftKind.ContextApps -> "Suggest the best phone apps for my current work context"
    }
