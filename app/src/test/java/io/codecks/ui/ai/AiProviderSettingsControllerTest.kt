package io.codecks.ui.ai

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.data.ai.AiArtifactRepository
import io.codecks.data.ai.AiCredentialImporter
import io.codecks.data.ai.AiHttpClient
import io.codecks.data.ai.AiHttpRequest
import io.codecks.data.ai.AiHttpResponse
import io.codecks.data.ai.AiProviderFactory
import io.codecks.data.ai.ImportedAiCredential
import io.codecks.data.ai.InMemorySecureApiKeyStore
import io.codecks.data.ai.SecretValue
import io.codecks.domain.features.Entitlement
import io.codecks.domain.features.EntitlementStatus
import io.codecks.domain.features.EntitlementTier
import io.codecks.domain.features.FakeEntitlementRepository
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.AiArtifactTest
import io.codecks.domain.ai.AiArtifactTestStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiProviderSettingsControllerTest {
    private val premium = Entitlement(EntitlementTier.Premium, EntitlementStatus.Active)

    @Test
    fun providerSelectionResetsModelAndKeyInput() = runTest {
        val controller = controllerIn(this)

        controller.setApiKey("sk-never-log-me")
        controller.selectProvider(AiProviderChoice.Anthropic)
        runCurrent()

        assertEquals(AiProviderChoice.Anthropic, controller.uiState.value.selectedProvider)
        assertEquals(AiProviderChoice.Anthropic.models.first().id, controller.uiState.value.selectedModelId)
        assertEquals("", controller.uiState.value.apiKeyInput)
    }

    @Test
    fun saveKeyClearsPlaintextAndTestsProvider() = runTest {
        val controller = controllerIn(this)

        controller.setApiKey("sk-test-secret")
        controller.saveApiKey()
        runCurrent()
        controller.testProvider()
        runCurrent()

        val state = controller.uiState.value
        assertEquals("", state.apiKeyInput)
        assertTrue(state.hasSavedKey)
        assertEquals(AiProviderTestStatus.Success, state.testStatus)
        assertEquals("Connected to OpenAI", state.message)
        assertFalse(state.message.orEmpty().contains("sk-test-secret"))
    }

    @Test
    fun typedUnsavedKeyDoesNotEnableProviderTest() = runTest {
        val controller = controllerIn(this)

        controller.setApiKey("sk-unsaved-secret")
        controller.testProvider()
        runCurrent()

        val state = controller.uiState.value
        assertFalse(state.hasSavedKey)
        assertEquals(AiProviderTestStatus.Failure, state.testStatus)
        assertEquals("Save an API key before testing", state.message)
        assertFalse(state.message.orEmpty().contains("sk-unsaved-secret"))
    }

    @Test
    fun freeEntitlementBlocksProviderTest() = runTest {
        val controller = controllerIn(
            scope = this,
            entitlement = Entitlement(EntitlementTier.Free, EntitlementStatus.Free),
        )

        controller.setApiKey("sk-test-secret")
        controller.testProvider()
        runCurrent()

        assertEquals(AiProviderTestStatus.Failure, controller.uiState.value.testStatus)
        assertEquals("AI Creator is not enabled", controller.uiState.value.message)
    }

    @Test
    fun importFromMacRequiresExplicitSaveBeforeProviderTest() = runTest {
        val controller = controllerIn(
            scope = this,
            importer = FakeAiCredentialImporter(
                ImportedAiCredential(
                    providerId = "openai",
                    key = SecretValue.of("sk-imported-secret"),
                    baseUrl = null,
                    source = "Mac env OPENAI_API_KEY",
                ),
            ),
        )

        controller.importFromMac()
        runCurrent()

        assertFalse(controller.uiState.value.hasSavedKey)
        assertEquals("Mac env OPENAI_API_KEY", controller.uiState.value.pendingImportedCredential?.source)
        assertTrue(controller.uiState.value.message.orEmpty().contains("Tap Save imported key"))

        controller.testProvider()
        runCurrent()

        assertEquals(AiProviderTestStatus.Failure, controller.uiState.value.testStatus)
        assertEquals("Save an API key before testing", controller.uiState.value.message)

        controller.confirmImportedCredential()
        runCurrent()
        controller.testProvider()
        runCurrent()

        assertTrue(controller.uiState.value.hasSavedKey)
        assertEquals(null, controller.uiState.value.pendingImportedCredential)
        assertEquals(AiProviderTestStatus.Success, controller.uiState.value.testStatus)
        assertFalse(controller.uiState.value.message.orEmpty().contains("sk-imported-secret"))
    }

    @Test
    fun generateDraftStoresArtifactAndTestsFromChat() = runTest {
        val artifacts = InMemoryAiArtifactRepository()
        val runner = RecordingActionRunner()
        val controller = controllerIn(
            scope = this,
            artifactRepository = artifacts,
            actionRunner = runner,
            responses = mutableListOf(
                AiHttpResponse(
                    200,
                    """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"schemaVersion\":2,\"status\":\"ready\",\"message\":\"Ready\",\"questions\":[],\"assumptions\":[],\"proposal\":{\"id\":\"draft.open\",\"title\":\"Open Docs\",\"description\":\"Open the docs workspace\",\"requiredCapabilities\":[],\"target\":{\"type\":\"AnyConnected\",\"id\":null},\"safety\":{\"level\":\"Normal\",\"requiresConfirmation\":false,\"confirmationTitle\":null,\"confirmationBody\":null},\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"label\":\"Open docs\",\"url\":\"https://docs.example.com\",\"text\":null,\"delayMs\":null,\"templateId\":null,\"requiresConfirmation\":false}]}}"}]}]}""",
                ),
            ),
        )

        controller.setApiKey("sk-test-secret")
        controller.saveApiKey()
        runCurrent()
        controller.setPrompt("open docs")
        controller.generateDraft()
        runCurrent()

        val artifact = controller.uiState.value.artifacts.single()
        assertEquals("Open Docs", artifact.title)
        assertEquals("open 'https://docs.example.com'", artifact.actions.single().command)
        assertTrue(controller.uiState.value.messages.any { it.artifactId == artifact.id })
        val history = controller.uiState.value.generationHistory.single()
        assertEquals("openai", history.providerId)
        assertEquals("gpt-5.5", history.modelId)
        assertEquals(artifact.id, history.artifactId)
        assertFalse(history.message.contains("sk-test-secret"))

        controller.testArtifact(artifact.id)
        runCurrent()

        assertEquals(emptyList<String>(), runner.commands)
        assertEquals(AiArtifactTestStatus.Succeeded, controller.uiState.value.artifacts.single().lastTest?.status)
        assertTrue(controller.uiState.value.artifacts.single().lastTest?.message.orEmpty().contains("dry run passed"))
    }

    @Test
    fun refineDraftCreatesReplacementWithoutMutatingSourceArtifact() = runTest {
        val controller = controllerIn(
            scope = this,
            responses = mutableListOf(
                openAiReadyActionResponse("draft.open", "Open Docs", "https://docs.example.com"),
                openAiReadyActionResponse("draft.open_new", "Open New Docs", "https://new-docs.example.com"),
            ),
        )

        controller.setApiKey("sk-test-secret")
        controller.saveApiKey()
        runCurrent()
        controller.setPrompt("open docs")
        controller.generateDraft()
        runCurrent()
        val original = controller.uiState.value.artifacts.single()

        controller.startRefinement(original.id)
        assertEquals(original.id, controller.uiState.value.refiningArtifact?.id)
        controller.setPrompt("use the new docs site")
        controller.generateDraft()
        runCurrent()

        val state = controller.uiState.value
        assertEquals(null, state.refiningArtifact)
        assertEquals(original.id, state.lastRefinedFromArtifact?.id)
        assertEquals(2, state.artifacts.size)
        assertEquals("Open New Docs", state.artifacts.first().title)
        assertEquals("Open Docs", state.artifacts.last().title)
        assertEquals("open 'https://docs.example.com'", state.artifacts.last().actions.single().command)
        assertEquals("open 'https://new-docs.example.com'", state.artifacts.first().actions.single().command)
    }

    @Test
    fun cancelRefinementClearsRefinementWithoutDeletingArtifact() = runTest {
        val controller = controllerIn(
            scope = this,
            responses = mutableListOf(openAiReadyActionResponse("draft.open", "Open Docs", "https://docs.example.com")),
        )

        controller.setApiKey("sk-test-secret")
        controller.saveApiKey()
        runCurrent()
        controller.setPrompt("open docs")
        controller.generateDraft()
        runCurrent()
        val artifact = controller.uiState.value.artifacts.single()

        controller.startRefinement(artifact.id)
        controller.cancelRefinement()

        assertEquals(null, controller.uiState.value.refiningArtifact)
        assertEquals(listOf(artifact), controller.uiState.value.artifacts)
    }

    @Test
    fun testArtifactUsesCanonicalPolicyForUnsupportedCommands() = runTest {
        val artifacts = InMemoryAiArtifactRepository()
        val artifact = AiArtifact(
            id = "cleanup",
            kind = AiArtifactKind.Button,
            title = "Cleanup",
            prompt = "cleanup",
            actions = listOf(AiArtifactAction("cleanup", "Cleanup", "rm -rf /tmp/nope")),
        )
        artifacts.save(artifact)
        val controller = controllerIn(scope = this, artifactRepository = artifacts)
        runCurrent()

        controller.testArtifact(artifact.id)
        runCurrent()

        val test = controller.uiState.value.artifacts.single().lastTest
        assertEquals(AiArtifactTestStatus.Failed, test?.status)
        assertTrue(test?.message.orEmpty().contains("recursive force delete is blocked"))
    }

    @Test
    fun testArtifactTreatsDangerousAllowedCommandAsConfirmationRequired() = runTest {
        val artifacts = InMemoryAiArtifactRepository()
        val artifact = AiArtifact(
            id = "focus",
            kind = AiArtifactKind.Button,
            title = "Focus",
            prompt = "focus",
            actions = listOf(AiArtifactAction("focus", "Focus", "caffeinate -u -t 30", dangerous = true)),
        )
        artifacts.save(artifact)
        val controller = controllerIn(scope = this, artifactRepository = artifacts)
        runCurrent()

        controller.testArtifact(artifact.id)
        runCurrent()

        val test = controller.uiState.value.artifacts.single().lastTest
        assertEquals(AiArtifactTestStatus.RequiresConfirmation, test?.status)
        assertTrue(test?.message.orEmpty().contains("needs confirmation"))
    }

    private fun controllerIn(
        scope: TestScope,
        entitlement: Entitlement = premium,
        artifactRepository: AiArtifactRepository? = null,
        actionRunner: ActionRunner? = null,
        importer: AiCredentialImporter? = null,
        responses: MutableList<AiHttpResponse> = mutableListOf(AiHttpResponse(200, """{"data":[{"id":"gpt-5-mini"}]}""")),
    ): AiProviderSettingsController {
        val keyStore = InMemorySecureApiKeyStore()
        return AiProviderSettingsController(
            keyStore = keyStore,
            providerFactory =
                AiProviderFactory(
                    keyStore = keyStore,
                    httpClient = object : AiHttpClient {
                        override suspend fun execute(request: AiHttpRequest): AiHttpResponse =
                            responses.removeAt(0)
                    },
                ),
            entitlementRepository = FakeEntitlementRepository(entitlement),
            scope = TestScope(StandardTestDispatcher(scope.testScheduler)),
            artifactRepository = artifactRepository,
            actionRunner = actionRunner,
            macCredentialImporter = importer,
        )
    }
}

private fun openAiReadyActionResponse(id: String, title: String, url: String): AiHttpResponse =
    AiHttpResponse(
        200,
        """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"schemaVersion\":2,\"status\":\"ready\",\"message\":\"Ready\",\"questions\":[],\"assumptions\":[],\"proposal\":{\"id\":\"$id\",\"title\":\"$title\",\"description\":\"Open the docs workspace\",\"requiredCapabilities\":[],\"target\":{\"type\":\"AnyConnected\",\"id\":null},\"safety\":{\"level\":\"Normal\",\"requiresConfirmation\":false,\"confirmationTitle\":null,\"confirmationBody\":null},\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"label\":\"Open docs\",\"url\":\"$url\",\"text\":null,\"delayMs\":null,\"templateId\":null,\"requiresConfirmation\":false}]}}"}]}]}""",
    )

private class FakeAiCredentialImporter(
    private val credential: ImportedAiCredential,
) : AiCredentialImporter {
    override suspend fun importCredential(providerId: String): Result<ImportedAiCredential> =
        Result.success(credential.copy(providerId = providerId))
}

private class InMemoryAiArtifactRepository : AiArtifactRepository {
    private val state = MutableStateFlow<List<AiArtifact>>(emptyList())
    override val artifacts: Flow<List<AiArtifact>> = state

    override suspend fun save(artifact: AiArtifact) {
        state.value = listOf(artifact) + state.value.filterNot { it.id == artifact.id }
    }

    override suspend fun recordTest(artifactId: String, test: AiArtifactTest) {
        state.value = state.value.map { artifact ->
            if (artifact.id == artifactId) artifact.copy(lastTest = test) else artifact
        }
    }

    override suspend fun delete(artifactId: String) {
        state.value = state.value.filterNot { it.id == artifactId }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

private class RecordingActionRunner : ActionRunner {
    val commands = mutableListOf<String>()

    override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult {
        val command = (spec as ActionSpec.ShellCommand).command
        commands += command
        return ActionResult(
            actionId = spec.id,
            title = spec.title,
            status = ActionResultStatus.Succeeded,
            message = "ok",
        )
    }
}
