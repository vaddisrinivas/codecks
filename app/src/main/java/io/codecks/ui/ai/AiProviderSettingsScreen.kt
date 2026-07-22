package io.codecks.ui.ai

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.codecks.BuildConfig
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.RawCommandPolicy
import io.codecks.core.actions.toAiArtifact
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.data.ai.AiArtifactRepository
import io.codecks.data.ai.AiGenerationHistoryRepository
import io.codecks.data.ai.AiProviderException
import io.codecks.data.ai.AiProviderFactory
import io.codecks.data.ai.AndroidSecureApiKeyStore
import io.codecks.data.ai.CodecksAiAgentPack
import io.codecks.data.ai.DefaultAiArtifactRepository
import io.codecks.data.ai.DefaultAiGenerationHistoryRepository
import io.codecks.data.ai.SecretValue
import io.codecks.data.ai.SecureApiKeyStore
import io.codecks.domain.DeckAction
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
import io.codecks.ui.designsystem.DeckPage
import io.codecks.ui.theme.CodecksAccent
import io.codecks.ui.theme.CodecksBorderStyle
import io.codecks.ui.theme.CodecksShapeStyle
import io.codecks.ui.theme.CodecksSurfaceStyle
import io.codecks.ui.theme.CodecksThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


@Composable
fun AiProviderSettingsRoute(
    entitlementRepository: EntitlementRepository,
    contentPadding: PaddingValues,
    actionRunner: ActionRunner? = null,
    mode: AiWorkspaceMode = AiWorkspaceMode.Workspace,
    availableActions: List<DeckAction> = emptyList(),
    onRunAction: (DeckAction) -> Unit = {},
    trackpadSettings: TrackpadSettings = TrackpadSettings(),
    onTrackpadSettingsChange: (((TrackpadSettings) -> TrackpadSettings) -> Unit)? = null,
    onThemeModeChange: (CodecksThemeMode) -> Unit = {},
    onThemeAccentChange: (CodecksAccent) -> Unit = {},
    onThemeSurfaceStyleChange: (CodecksSurfaceStyle) -> Unit = {},
    onThemeBorderStyleChange: (CodecksBorderStyle) -> Unit = {},
    onThemeShapeStyleChange: (CodecksShapeStyle) -> Unit = {},
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
    val agentPack = remember(context) { CodecksAiAgentPack.load(context.applicationContext) }
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
        onNewArtifact = controller::newArtifact,
        contextAppsEnabled = contextAppsEnabled,
        modifier = modifier,
    )
}

@Composable
fun AiProviderSettingsScreen(
    state: AiProviderSettingsState,
    mode: AiWorkspaceMode = AiWorkspaceMode.Workspace,
    contentPadding: PaddingValues,
    onProviderSelected: (AiProviderChoice) -> Unit,
    onModelSelected: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
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
    onNewArtifact: () -> Unit = {},
    contextAppsEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val providerSettingsOpen = mode == AiWorkspaceMode.ProviderSettings
    val draftKinds = remember { DraftKind.entries }
    val currentArtifact = state.generatedArtifactId
        ?.let { id -> state.artifacts.firstOrNull { it.id == id } }
    val previousArtifacts = state.artifacts.filterNot { it.id == currentArtifact?.id }
    if (mode == AiWorkspaceMode.Workspace) {
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
            onNewArtifact = onNewArtifact,
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
                text = if (mode == AiWorkspaceMode.Workspace) {
                    "Saved drafts stay disabled until you test and enable them."
                } else {
                    "AI keys are stored encrypted on this phone. Paste keys manually; SSH key scraping is disabled."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}
