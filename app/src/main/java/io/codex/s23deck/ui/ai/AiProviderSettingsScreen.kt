package io.codex.s23deck.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Science
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.codex.s23deck.BuildConfig
import io.codex.s23deck.core.actions.ActionRunner
import io.codex.s23deck.core.actions.toAiArtifact
import io.codex.s23deck.core.trackpad.TrackpadFloatingMenuLayout
import io.codex.s23deck.core.trackpad.TrackpadRailSide
import io.codex.s23deck.core.trackpad.TrackpadSettings
import io.codex.s23deck.core.actions.RawCommandPolicy
import io.codex.s23deck.data.ai.AiCredentialImporter
import io.codex.s23deck.data.ai.AiArtifactRepository
import io.codex.s23deck.data.ai.AiProviderFactory
import io.codex.s23deck.data.ai.AndroidSecureApiKeyStore
import io.codex.s23deck.data.ai.DeckBridgeAiAgentPack
import io.codex.s23deck.data.ai.DefaultAiArtifactRepository
import io.codex.s23deck.data.ai.ImportedAiCredential
import io.codex.s23deck.data.ai.SecretValue
import io.codex.s23deck.data.ai.SecureApiKeyStore
import io.codex.s23deck.domain.ai.AiModel
import io.codex.s23deck.domain.ai.ActionCapability
import io.codex.s23deck.domain.ai.ActionDraftValidator
import io.codex.s23deck.domain.ai.AiBuilder
import io.codex.s23deck.domain.ai.DraftKind
import io.codex.s23deck.domain.ai.DraftRequest
import io.codex.s23deck.domain.ai.GeneratedDraft
import io.codex.s23deck.domain.ai.AiProviderCatalog
import io.codex.s23deck.domain.ai.AiProviderSpec
import io.codex.s23deck.domain.ai.AiArtifact
import io.codex.s23deck.domain.ai.AiArtifactTest
import io.codex.s23deck.domain.ai.AiArtifactTestStatus
import io.codex.s23deck.domain.ai.AiChatMessage
import io.codex.s23deck.domain.ai.AiChatRole
import io.codex.s23deck.domain.ai.FeatureGate
import io.codex.s23deck.domain.commerce.Entitlement
import io.codex.s23deck.domain.commerce.EntitlementRepository
import io.codex.s23deck.domain.commerce.EntitlementStatus
import io.codex.s23deck.domain.commerce.EntitlementTier
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.ui.designsystem.DeckFilterPill
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckPage
import io.codex.s23deck.ui.designsystem.CodecksPanel
import io.codex.s23deck.ui.theme.DeckBridgeAccent
import io.codex.s23deck.ui.theme.DeckBridgeBorderStyle
import io.codex.s23deck.ui.theme.DeckBridgeShapeStyle
import io.codex.s23deck.ui.theme.DeckBridgeSurfaceStyle
import io.codex.s23deck.ui.theme.DeckBridgeThemeMode
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
    val messages: List<AiChatMessage> = listOf(
        AiChatMessage(
            id = "welcome",
            role = AiChatRole.Assistant,
            text = "Tell me what you want Codecks to do. I can create a button, a deck, or an automation, then you can test it here.",
        ),
    ),
    val testingArtifactId: String? = null,
    val message: String? = null,
) {
    val selectedModel: AiModel =
        selectedProvider.models.firstOrNull { it.id == selectedModelId } ?: selectedProvider.models.first()
    val premiumAllowed: Boolean = entitlement.allows(FeatureGate.AiBuilder)
}

class AiProviderSettingsController(
    private val keyStore: SecureApiKeyStore,
    private val providerFactory: AiProviderFactory,
    private val entitlementRepository: EntitlementRepository,
    private val scope: CoroutineScope,
    private val artifactRepository: AiArtifactRepository? = null,
    private val actionRunner: ActionRunner? = null,
    private val macCredentialImporter: AiCredentialImporter? = null,
    private val agentContext: String = "",
    private val localCommandHandler: suspend (String) -> AiLocalCommandResult? = { null },
) {
    private val jobs = mutableListOf<Job>()
    private val _uiState = MutableStateFlow(AiProviderSettingsState())
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
        _uiState.update { it.copy(draftKind = value, message = null) }
    }

    fun generateDraft() {
        val state = _uiState.value
        val prompt = state.prompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(message = "Describe what the control should do") }
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
                    generatedArtifactId = null,
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
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    generatedDraft = null,
                    generatedArtifactId = null,
                    message = null,
                )
            }
            val provider = createProvider(state)
            val builder = AiBuilder(provider, ActionDraftValidator(), entitlementRepository = entitlementRepository)
            val request = DraftRequest(
                prompt = prompt,
                modelId = state.selectedModelId,
                draftKind = state.draftKind,
                availableCapabilities = ActionCapability.entries.toSet(),
                agentContext = agentContext,
            )
            builder.requestValidatedDraft(request).fold(
                onSuccess = { draft ->
                    draft.toAiArtifact(prompt).fold(
                        onSuccess = { artifact ->
                            artifactRepository?.save(artifact)
                            val assistantMessage = AiChatMessage(
                                id = "assistant_${System.currentTimeMillis()}",
                                role = AiChatRole.Assistant,
                                text = "${artifact.title} is ready. Review it, test it, then save it.",
                                artifactId = artifact.id,
                            )
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    generatedDraft = draft,
                                    generatedArtifactId = artifact.id,
                                    artifacts = if (artifactRepository == null) listOf(artifact) + it.artifacts else it.artifacts,
                                    messages = it.messages + assistantMessage,
                                    message = "Artifact ready to test",
                                )
                            }
                        },
                        onFailure = { error ->
                            val message = error.message ?: "Draft could not be converted into a runnable artifact"
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
            artifactRepository?.recordTest(artifactId, test)
            _uiState.update {
                it.copy(
                    testingArtifactId = null,
                    artifacts = if (artifactRepository == null) {
                        it.artifacts.map { item -> if (item.id == artifactId) item.copy(lastTest = test) else item }
                    } else {
                        it.artifacts
                    },
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
                    artifacts = if (artifactRepository == null) state.artifacts.filterNot { it.id == artifactId } else state.artifacts,
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
                message = "Saved to Codecks",
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

    private suspend fun saveBaseUrlIfNeeded(state: AiProviderSettingsState) {
        if (state.selectedProvider != AiProviderChoice.LiteLLM) return
        val baseUrl = state.baseUrlInput.trim()
        if (baseUrl.isBlank()) return
        keyStore.saveKey(baseUrlKey(state.selectedProvider.providerId), SecretValue.of(baseUrl))
    }
}

private fun baseUrlKey(providerId: String): String = "base_url_$providerId"

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
        return AiLocalCommandResult("Opened Settings. Use Trackpad behavior to change this; chat did not change the setting.")
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
        return AiLocalCommandResult("Opened Settings. Use Appearance to change theme, color, borders, and shape; chat did not change them.")
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
    val controller = remember(resolvedKeyStore, entitlementRepository, artifactRepository, actionRunner, agentPack, localCommandHandler) {
        AiProviderSettingsController(
            keyStore = resolvedKeyStore,
            providerFactory = providerFactory,
            entitlementRepository = entitlementRepository,
            scope = scope,
            artifactRepository = artifactRepository,
            actionRunner = actionRunner,
            agentContext = agentPack.prompt,
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
        onGenerate = controller::generateDraft,
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
    onGenerate: () -> Unit,
    onSaveDraft: (GeneratedDraft) -> Unit,
    onSaveArtifact: (AiArtifact) -> Unit,
    onTestArtifact: (String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    onActionLink: (String) -> Unit,
    onOpenAiSettings: () -> Unit = {},
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
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        if (mode == AiWorkspaceMode.Chat) item {
            AiChatHero(
                providerLabel = "${state.selectedProvider.label} · ${state.selectedModel.label}",
                ready = state.premiumAllowed && state.hasSavedKey,
                artifactCount = state.artifacts.size,
                onOpenAiSettings = onOpenAiSettings,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        if (mode == AiWorkspaceMode.Chat) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    draftKinds.forEach { kind ->
                        DeckFilterPill(
                            label = kind.label,
                            selected = state.draftKind == kind,
                            onClick = { onDraftKindChanged(kind) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChanged,
                    label = { Text("What do you want Codecks to do?") },
                    placeholder = { Text(state.draftKind.placeholder) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                CodecksPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Text("AI request preview", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Destination: ${state.selectedProvider.label} · ${state.selectedModel.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Sent after local-command matching: your text below, selected artifact type, and bundled Codecks action schema. Notification text, app usage, SSH credentials, and other phone context are not included.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.prompt.ifBlank { "Nothing is sent until you enter a request and tap Send." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "The saved API key is used only as the selected provider's HTTPS authentication header; it is never inserted into the prompt.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DeckActionButton(
                    label = if (state.isGenerating) "Generating" else "Send",
                    onClick = onGenerate,
                    enabled = state.prompt.isNotBlank() && !state.isGenerating,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                )
                if (state.isGenerating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (mode == AiWorkspaceMode.Chat) state.messages.takeLast(10).forEach { message ->
            item {
                AiChatBubble(
                    message = message,
                    onActionLink = onActionLink,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
        }
        if (mode == AiWorkspaceMode.Chat && state.artifacts.isNotEmpty()) {
            item {
                Text(
                    text = "Artifacts",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        if (mode == AiWorkspaceMode.Chat) state.artifacts.take(10).forEach { artifact ->
            item {
                val draftToSave = state.generatedDraft.takeIf { state.generatedArtifactId == artifact.id }
                AiArtifactCard(
                    artifact = artifact,
                    isTesting = state.testingArtifactId == artifact.id,
                    onTest = { onTestArtifact(artifact.id) },
                    onSave = {
                        if (draftToSave != null) onSaveDraft(draftToSave) else onSaveArtifact(artifact)
                    },
                    onDelete = { onDeleteArtifact(artifact.id) },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
        }
        if (providerSettingsOpen) item {
            AiProviderSummary(
                state = state,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        if (providerSettingsOpen) item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                AiProviderChoice.entries.forEach { provider ->
                    DeckFilterPill(
                        label = provider.label,
                        selected = state.selectedProvider == provider,
                        onClick = { onProviderSelected(provider) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        if (providerSettingsOpen) item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text("Model", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    state.selectedProvider.models.forEach { model ->
                        DeckFilterPill(
                            label = model.label,
                            selected = state.selectedModelId == model.id,
                            onClick = { onModelSelected(model.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        if (providerSettingsOpen) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = "Paste a provider key here. Codecks does not read API keys from your Mac over SSH.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.pendingImportedCredential?.let { credential ->
                    Text(
                        text = "Found key from ${credential.source}. Nothing saved yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DeckActionButton(
                        label = "Save imported key",
                        onClick = onConfirmImportedCredential,
                        enabled = !state.isImportingFromMac,
                        icon = Icons.Outlined.Key,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                }
                if (state.selectedProvider == AiProviderChoice.LiteLLM) {
                    OutlinedTextField(
                        value = state.baseUrlInput,
                        onValueChange = onBaseUrlChanged,
                        label = { Text("Endpoint URL") },
                        placeholder = { Text(BuildConfig.LITELLM_BASE_URL) },
                        singleLine = true,
                        supportingText = {
                            Text(
                                if (state.savedBaseUrl.isNotBlank()) {
                                    "Saved endpoint used for generation"
                                } else {
                                    "Use this for LiteLLM, Azure/OpenAI-compatible gateways, or local model routers"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = state.apiKeyInput,
                    onValueChange = onApiKeyChanged,
                    label = { Text("${state.selectedProvider.label} API key") },
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            when {
                                state.hasSavedKey && state.apiKeyInput.isNotBlank() -> "Save to replace the stored key"
                                state.hasSavedKey -> "Saved key ready for tests and generation"
                                else -> "Save a key before testing or generating"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeckActionButton(
                        label = "Save key",
                        onClick = onSaveApiKey,
                        enabled = state.apiKeyInput.isNotBlank(),
                        icon = Icons.Outlined.Key,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    )
                    DeckActionButton(
                        label = if (state.testStatus == AiProviderTestStatus.Running) "Testing" else "Test key",
                        onClick = onTest,
                        enabled = state.hasSavedKey && state.premiumAllowed && state.testStatus != AiProviderTestStatus.Running,
                        icon = Icons.Outlined.Science,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    )
                }
            }
        }
        if (providerSettingsOpen) item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(
                        if (state.premiumAllowed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = if (state.premiumAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("AI access", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.premiumAllowed) "AI Creator enabled" else "AI Creator unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DeckActionButton(
                        label = "Refresh",
                        onClick = onRefreshEntitlement,
                        modifier = Modifier.heightIn(min = 48.dp).weight(0.7f),
                    )
                }
            }
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
                    "Drafts are validated before save. Test uses the action runner when a Mac target is ready; dangerous drafts require confirmation."
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
private fun AiChatHero(
    providerLabel: String,
    ready: Boolean,
    artifactCount: Int,
    onOpenAiSettings: () -> Unit,
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
                    Text("AI workspace", style = MaterialTheme.typography.titleLarge)
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
        }
    }
}

@Composable
private fun AiChatBubble(
    message: AiChatMessage,
    onActionLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == AiChatRole.User
    Surface(
        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
            Text(
                if (isUser) "You" else "Codecks",
                style = MaterialTheme.typography.labelMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
            if (!message.actionId.isNullOrBlank() && !message.actionLabel.isNullOrBlank()) {
                DeckActionButton(
                    label = "Open ${message.actionLabel}",
                    onClick = { onActionLink(message.actionId) },
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun AiArtifactCard(
    artifact: AiArtifact,
    isTesting: Boolean,
    onTest: () -> Unit,
    onSave: (() -> Unit)?,
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
            artifact.actions.take(4).forEachIndexed { index, action ->
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
                "Preview only. Test performs validation/dry run and never executes generated shell.",
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
                        label = if (artifact.kind == io.codex.s23deck.domain.ai.AiArtifactKind.Deck) "Save deck" else "Save",
                        onClick = onSave,
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
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

private val io.codex.s23deck.domain.ai.AiArtifactKind.label: String
    get() = when (this) {
        io.codex.s23deck.domain.ai.AiArtifactKind.Button -> "Button"
        io.codex.s23deck.domain.ai.AiArtifactKind.Deck -> "Deck"
        io.codex.s23deck.domain.ai.AiArtifactKind.Automation -> "Automation"
        io.codex.s23deck.domain.ai.AiArtifactKind.Clock -> "Clock"
    }

@Composable
private fun AiProviderSummary(
    state: AiProviderSettingsState,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text("Provider", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${state.selectedProvider.label} · ${state.selectedModel.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        !state.premiumAllowed -> "AI Creator is not enabled"
                        state.hasSavedKey -> "Encrypted key saved"
                        else -> "Save an API key before generating"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val DraftKind.label: String
    get() = when (this) {
        DraftKind.Action -> "Button"
        DraftKind.Deck -> "Deck"
        DraftKind.Automation -> "Automation"
        DraftKind.ContextApps -> "Context apps"
    }

private val DraftKind.placeholder: String
    get() = when (this) {
        DraftKind.Action -> "Open Linear and start my daily standup note"
        DraftKind.Deck -> "Build a coding deck with browser, terminal, GitHub, music, and focus controls"
        DraftKind.Automation -> "When I start a meeting, open notes, calendar, and mute media"
        DraftKind.ContextApps -> "Suggest the best phone apps for my current work context"
    }
