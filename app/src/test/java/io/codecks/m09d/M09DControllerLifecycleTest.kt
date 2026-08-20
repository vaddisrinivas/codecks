package io.codecks.m09d

import io.codecks.AiArtifactPlacementRoute
import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.data.ActionRepository
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.HostKeyVerification
import io.codecks.data.ai.AiArtifactRepository
import io.codecks.data.ai.AiArtifactJsonCodec
import io.codecks.data.ai.AiHttpClient
import io.codecks.data.ai.AiHttpRequest
import io.codecks.data.ai.AiHttpResponse
import io.codecks.data.ai.AiProviderFactory
import io.codecks.data.ai.InMemorySecureApiKeyStore
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.ai.AiArtifactTest
import io.codecks.domain.ai.AiArtifactTestStatus
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckSlot
import io.codecks.domain.features.Entitlement
import io.codecks.domain.features.FakeEntitlementRepository
import io.codecks.routeAiArtifactPlacement
import io.codecks.ui.ai.AiProviderSettingsController
import io.codecks.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class M09DControllerLifecycleTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun homeEditAssignPersistsExactSavePath() = runTest(dispatcher) {
        val focus = action("focus")
        val blank = blank()
        val browser = action("browser")
        val repository = LifecycleActionRepository(listOf(focus, blank), listOf(focus, blank, browser))
        val home = home(repository)
        runCurrent()

        home.assign(1, browser)
        runCurrent()

        assertEquals(listOf("focus", "browser"), home.uiState.value.actions.map(DeckAction::id))
        assertEquals(listOf("focus", "browser"), repository.saved.single().actions.map(DeckAction::id))
    }

    @Test
    fun homeReassignClearsPriorUndoPolicy() = runTest(dispatcher) {
        val focus = action("focus")
        val blank = blank()
        val browser = action("browser")
        val repository = LifecycleActionRepository(listOf(focus, blank), listOf(focus, blank, browser))
        val home = home(repository)
        runCurrent()

        home.remove(0)
        runCurrent()
        assertTrue(home.uiState.value.pendingDeckUndo != null)
        home.assign(0, browser)
        runCurrent()

        assertNull(home.uiState.value.pendingDeckUndo)
        home.undoLastDeckEdit()
        runCurrent()
        assertEquals(listOf("browser", "blank"), repository.saved.last().actions.map(DeckAction::id))
    }

    @Test
    fun homeMoveUndoRestoresExactLayoutAndSavePath() = runTest(dispatcher) {
        val focus = action("focus")
        val browser = action("browser")
        val repository = LifecycleActionRepository(listOf(focus, browser), listOf(focus, browser, blank()))
        val home = home(repository)
        runCurrent()

        home.move(0, 1)
        runCurrent()
        home.undoLastDeckEdit()
        runCurrent()

        assertEquals(listOf("focus", "browser"), home.uiState.value.actions.map(DeckAction::id))
        assertEquals(listOf("browser", "focus"), repository.saved.first().actions.map(DeckAction::id))
        assertEquals(listOf("focus", "browser"), repository.saved.last().actions.map(DeckAction::id))
    }

    @Test
    fun homeRemoveUndoRestoresExactActionAndSavePath() = runTest(dispatcher) {
        val focus = action("focus")
        val blank = blank()
        val repository = LifecycleActionRepository(listOf(focus), listOf(focus, blank))
        val home = home(repository)
        runCurrent()

        home.remove(0)
        runCurrent()
        assertEquals("blank", repository.saved.last().actions.single().id)
        home.undoLastDeckEdit()
        runCurrent()

        assertEquals("focus", home.uiState.value.actions.single().id)
        assertEquals("focus", repository.saved.last().actions.single().id)
    }

    @Test
    fun aiCreateSurvivesRepositoryBackedControllerRecreationProxy() = runTest(dispatcher) {
        val repository = LifecycleArtifactRepository()
        val first = aiController(this, repository, mutableListOf(readyResponse("created", "Created")))
        first.setApiKey("test-key")
        first.saveApiKey()
        runCurrent()
        first.setPrompt("create control")
        first.generateDraft()
        runCurrent()
        val createdId = repository.snapshot().single().id
        first.close()

        val recreated = aiController(this, repository)
        runCurrent()
        assertEquals(createdId, recreated.uiState.value.artifacts.single().id)
        recreated.close()
    }

    @Test
    fun aiTestSurvivesRepositoryBackedControllerRecreationProxy() = runTest(dispatcher) {
        val artifact = artifact("tested")
        val repository = LifecycleArtifactRepository(listOf(artifact))
        val first = aiController(this, repository)
        runCurrent()
        first.testArtifact(artifact.id)
        runCurrent()
        first.close()

        val recreated = aiController(this, repository)
        runCurrent()
        assertEquals(AiArtifactTestStatus.Succeeded, recreated.uiState.value.artifacts.single().lastTest?.status)
        recreated.close()
    }

    @Test
    fun aiRefinePreservesSourceAcrossRepositoryBackedControllerRecreationProxy() = runTest(dispatcher) {
        val source = artifact("source")
        val repository = LifecycleArtifactRepository(listOf(source))
        val first = aiController(this, repository, mutableListOf(readyResponse("refined", "Refined")))
        first.setApiKey("test-key")
        first.saveApiKey()
        runCurrent()
        first.startRefinement(source.id)
        first.setPrompt("refine safely")
        first.generateDraft()
        runCurrent()
        first.close()

        val recreated = aiController(this, repository)
        runCurrent()
        val artifacts = recreated.uiState.value.artifacts
        assertTrue(artifacts.any { it.id == source.id })
        assertTrue(artifacts.any { it.id != source.id && it.title == "Refined" })
        recreated.close()
    }

    @Test
    fun aiSaveOnlySurvivesRepositoryBackedControllerRecreationProxy() = runTest(dispatcher) {
        val repository = LifecycleArtifactRepository()
        val first = aiController(this, repository, mutableListOf(readyResponse("saved", "Saved")))
        first.setApiKey("test-key")
        first.saveApiKey()
        runCurrent()
        first.setPrompt("save this control")
        first.generateDraft()
        runCurrent()
        val generatedId = requireNotNull(first.uiState.value.generatedArtifactId)
        val exactRepositoryBytes = repository.canonicalBytes()

        first.markSavedOnly(generatedId)
        assertTrue(first.uiState.value.message.orEmpty().contains("saved to catalog"))
        assertNull(first.uiState.value.generatedArtifactId)
        assertNull(repository.snapshot().single().lastPlacementRequest)
        assertArrayEquals(exactRepositoryBytes, repository.canonicalBytes())
        first.close()

        val recreatedRepository = LifecycleArtifactRepository.fromCanonicalBytes(exactRepositoryBytes)
        val recreated = aiController(this, recreatedRepository)
        runCurrent()
        assertEquals(generatedId, recreated.uiState.value.artifacts.single().id)
        assertArrayEquals(exactRepositoryBytes, recreatedRepository.canonicalBytes())
        assertNull(recreated.uiState.value.artifacts.single().lastPlacementRequest)
        recreated.close()
    }

    @Test
    fun aiDeleteIsNonUndoableAcrossRepositoryBackedControllerRecreationProxy() = runTest(dispatcher) {
        val deleted = artifact("deleted")
        val repository = LifecycleArtifactRepository(listOf(deleted))
        val first = aiController(this, repository)
        runCurrent()
        first.deleteArtifact(deleted.id)
        runCurrent()
        first.close()

        val recreated = aiController(this, repository)
        runCurrent()
        assertTrue(recreated.uiState.value.artifacts.isEmpty())
        assertTrue(repository.snapshot().isEmpty())
        recreated.close()
    }

    @Test
    fun compositionPlacementBridgeDrivesHomePreferredSlotAndExactSavePath() = runTest(dispatcher) {
        val blank = blank()
        val repository = LifecycleActionRepository(listOf(blank, blank), listOf(blank))
        val home = home(repository)
        val artifact = artifact("placed")
        runCurrent()
        var deckRouteCompleted = false

        val route = routeAiArtifactPlacement(
            artifact = artifact,
            preferredDeckSlot = 1,
            saveAutomation = { false },
            placeOnDeck = home::requestArtifactPlacement,
            onAutomationSaved = { error("wrong route") },
            onDeckPlacementRequested = { deckRouteCompleted = true },
        )
        runCurrent()

        assertEquals(AiArtifactPlacementRoute.DECK, route)
        assertTrue(deckRouteCompleted)
        assertTrue(repository.saved.single().actions[1].id.startsWith("artifact_placed"))
        assertEquals(repository.saved.single(), home.uiState.value.deckLayout)
    }

    private fun home(repository: LifecycleActionRepository) =
        HomeViewModel(repository, ReadyConnectionRepository(), ImmediateRunner())

    private fun aiController(
        scope: TestScope,
        repository: LifecycleArtifactRepository,
        responses: MutableList<AiHttpResponse> = mutableListOf(),
    ): AiProviderSettingsController {
        val keyStore = InMemorySecureApiKeyStore()
        return AiProviderSettingsController(
            keyStore = keyStore,
            providerFactory = AiProviderFactory(
                keyStore,
                object : AiHttpClient {
                    override suspend fun execute(request: AiHttpRequest): AiHttpResponse =
                        responses.removeFirstOrNull() ?: AiHttpResponse(200, "{\"data\":[]}")
                },
            ),
            entitlementRepository = FakeEntitlementRepository(Entitlement(localOnly = true)),
            scope = TestScope(StandardTestDispatcher(scope.testScheduler)),
            artifactRepository = repository,
        )
    }
}

private fun action(id: String) = DeckAction(id, id.replaceFirstChar(Char::titlecase), ActionKind.Ssh, ActionIcon.Apps, command = "open -a Notes")
private fun blank() = DeckAction("blank", "Blank", ActionKind.Local, ActionIcon.Add)
private fun artifact(id: String) = AiArtifact(
    id = "artifact_$id",
    kind = AiArtifactKind.Button,
    title = id.replaceFirstChar(Char::titlecase),
    prompt = "$id prompt",
    actions = listOf(AiArtifactAction("open_$id", "Open ${id.replaceFirstChar(Char::titlecase)}", "open -a Notes")),
)

private fun readyResponse(id: String, title: String) = AiHttpResponse(
    200,
    """{"choices":[{"message":{"content":"{\"schemaVersion\":2,\"status\":\"ready\",\"message\":\"Ready\",\"questions\":[],\"assumptions\":[],\"proposal\":{\"id\":\"$id\",\"title\":\"$title\",\"description\":\"Safe action\",\"requiredCapabilities\":[],\"target\":{\"type\":\"AnyConnected\",\"id\":null},\"safety\":{\"level\":\"Normal\",\"requiresConfirmation\":false,\"confirmationTitle\":null,\"confirmationBody\":null},\"steps\":[{\"id\":\"step-1\",\"type\":\"open_url\",\"label\":\"Open\",\"url\":\"https://example.com\",\"text\":null,\"delayMs\":null,\"templateId\":null,\"requiresConfirmation\":false}]}}"}}]}""",
)

private class LifecycleActionRepository(
    initial: List<DeckAction>,
    private val catalog: List<DeckAction>,
) : ActionRepository {
    private val layouts = MutableStateFlow(DeckLayout.fromActions(initial))
    val saved = mutableListOf<DeckLayout>()
    override fun favorites() = layouts.value.actions
    override fun observeFavorites(): Flow<List<DeckAction>> = MutableStateFlow(layouts.value.actions)
    override fun layout() = layouts.value
    override fun observeLayout(): Flow<DeckLayout> = layouts
    override fun allActions() = (catalog + layouts.value.actions).distinctBy(DeckAction::id)
    override suspend fun saveFavorites(actions: List<DeckAction>) = saveLayout(DeckLayout.fromActions(actions))
    override suspend fun saveLayout(layout: DeckLayout) {
        saved += layout
        layouts.value = layout
    }
    override suspend fun exportLayout() = Result.success("")
    override suspend fun validateLayout(payload: String) = Result.success(Unit)
    override suspend fun importLayout(payload: String) = Result.success(Unit)
    override suspend fun run(action: DeckAction) = Result.success("sent")
    override suspend fun test(action: DeckAction) = Result.success("verified")
}

private class LifecycleArtifactRepository(initial: List<AiArtifact> = emptyList()) : AiArtifactRepository {
    private val state = MutableStateFlow(initial)
    override val artifacts: Flow<List<AiArtifact>> = state
    override suspend fun save(artifact: AiArtifact) {
        state.value = listOf(artifact) + state.value.filterNot { it.id == artifact.id }
    }
    override suspend fun recordTest(artifactId: String, test: AiArtifactTest) {
        state.value = state.value.map { if (it.id == artifactId) it.copy(lastTest = test) else it }
    }
    override suspend fun delete(artifactId: String) {
        state.value = state.value.filterNot { it.id == artifactId }
    }
    override suspend fun clear() {
        state.value = emptyList()
    }
    fun snapshot() = state.value
    fun canonicalBytes(): ByteArray = AiArtifactJsonCodec.encode(state.value).encodeToByteArray()

    companion object {
        fun fromCanonicalBytes(bytes: ByteArray) =
            LifecycleArtifactRepository(AiArtifactJsonCodec.decodeStrict(bytes.decodeToString()))
    }
}

private class ImmediateRunner : ActionRunner {
    override suspend fun run(spec: ActionSpec, allowDangerous: Boolean) =
        ActionResult(spec.id, spec.title, ActionResultStatus.Succeeded, "sent")
}

private class ReadyConnectionRepository : ConnectionRepository {
    override val config = MutableStateFlow(ConnectionConfig("mac", 22, "user", true, "key"))
    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey() = Result.success("key")
    override suspend fun publicKey() = "key"
    override suspend fun trustHostKey() = Result.success("trusted")
    override suspend fun verifyHostKey() = Result.success(HostKeyVerification("trusted"))
    override suspend fun confirmPendingHostKey() = Result.success("confirmed")
    override suspend fun rotateKey() = Result.success("rotated")
    override suspend fun resetTrust() = Result.success("reset")
    override suspend fun installKey(password: String) = Result.success("installed")
    override suspend fun test(password: String?) = Result.success("connected")
    override suspend fun runAction(actionId: String, dangerous: Boolean) = Result.success("sent")
    override suspend fun runCommand(command: String) = Result.success("sent")
    override suspend fun runCommandWithInput(command: String, stdin: String) = Result.success("sent")
    override suspend fun validateCommandSyntax(command: String) = Result.success("valid")
    override suspend fun runCommandSecret(command: String) = Result.success("sent")
    override suspend fun selectTarget(targetId: String) = Result.success("selected")
    override suspend fun removeTarget(targetId: String) = Result.success("removed")
}
