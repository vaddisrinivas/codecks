package io.codex.s23deck.ui.home

import io.codex.s23deck.data.ActionRepository
import io.codex.s23deck.data.ConnectionConfig
import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.core.actions.ActionResult
import io.codex.s23deck.core.actions.ActionResultStatus
import io.codex.s23deck.core.actions.ActionRunner
import io.codex.s23deck.core.actions.ActionSpec
import io.codex.s23deck.domain.ActionIcon
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.ActionStatus
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.domain.ai.ActionDefinition
import io.codex.s23deck.domain.ai.ActionDraft
import io.codex.s23deck.domain.ai.ActionStep
import io.codex.s23deck.domain.ai.ActionStepTypes
import io.codex.s23deck.domain.ai.DeckDraft
import io.codex.s23deck.domain.ai.GeneratedDraft
import io.codex.s23deck.domain.deck.DeckTemplate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun run_exposesRunningThenSuccess() = runTest(dispatcher) {
        val repository = GatedActionRepository()
        val connectionRepository = ReadyConnectionRepository()
        val runner = DeferredActionRunner()
        val viewModel = HomeViewModel(repository, connectionRepository, runner)

        runCurrent()

        viewModel.run(repository.action)
        runCurrent()

        assertEquals(ActionStatus.Running(repository.action.id), viewModel.uiState.value.actionStatus)

        runner.result.complete(ActionResult(repository.action.id, repository.action.label, ActionResultStatus.Succeeded, "Finder sent"))
        runCurrent()

        val status = viewModel.uiState.value.actionStatus
        assertTrue(status is ActionStatus.Succeeded)
        assertEquals("Finder sent", (status as ActionStatus.Succeeded).message)
    }

    @Test
    fun restoredCustomFavorites_areMergedIntoActionLibrary() = runTest(dispatcher) {
        val staticAction = DeckAction("finder", "Finder", ActionKind.Ssh, ActionIcon.Finder)
        val customAction = DeckAction(
            id = "ai_focus",
            label = "Focus setup",
            kind = ActionKind.Ssh,
            icon = ActionIcon.Apps,
            command = "open -a Calendar",
        )
        val repository = GatedActionRepository(
            action = staticAction,
            favorites = listOf(staticAction, customAction),
            allActions = listOf(staticAction),
        )
        val connectionRepository = ReadyConnectionRepository()
        val viewModel = HomeViewModel(repository, connectionRepository, ImmediateActionRunner())

        runCurrent()

        assertEquals(listOf("finder", "ai_focus"), viewModel.uiState.value.actions.map(DeckAction::id))
        assertEquals(listOf("finder", "ai_focus"), viewModel.uiState.value.allActions.map(DeckAction::id))
    }

    @Test
    fun saveGeneratedDeck_addsMultipleControlsToDeck() = runTest(dispatcher) {
        val addButton = DeckAction("add_button", "Add", ActionKind.Local, ActionIcon.Add)
        val repository = GatedActionRepository(
            favorites = listOf(addButton, addButton),
            allActions = listOf(addButton),
        )
        val connectionRepository = ReadyConnectionRepository()
        val viewModel = HomeViewModel(repository, connectionRepository, ImmediateActionRunner())
        runCurrent()

        viewModel.saveGeneratedDraft(
            GeneratedDraft.Deck(
                DeckDraft(
                    prompt = "coding deck",
                    id = "coding.deck",
                    title = "Coding Deck",
                    actions = listOf(
                        ActionDefinition(
                            id = "open.github",
                            title = "Open GitHub",
                            steps = listOf(ActionStep("open", ActionStepTypes.OpenUrl, url = "https://github.com")),
                        ),
                        ActionDefinition(
                            id = "open.terminal",
                            title = "Open Terminal",
                            steps = listOf(ActionStep("terminal", ActionStepTypes.Shell, value = "open -a Terminal")),
                        ),
                    ),
                ),
            ),
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("ai_open_github_1", "ai_open_terminal_2"), state.actions.map(DeckAction::id))
        assertEquals("2 deck controls saved", (state.actionStatus as ActionStatus.Succeeded).message)
    }

    @Test
    fun testAction_usesRepositoryVerifier() = runTest(dispatcher) {
        val repository = GatedActionRepository()
        val connectionRepository = ReadyConnectionRepository()
        val viewModel = HomeViewModel(repository, connectionRepository, ImmediateActionRunner())

        runCurrent()

        viewModel.test(repository.action)
        runCurrent()

        assertEquals(repository.action.id, repository.lastTestedActionId)
        val status = viewModel.uiState.value.actionStatus
        assertTrue(status is ActionStatus.Succeeded)
        assertEquals("Finder verified", (status as ActionStatus.Succeeded).message)
    }

    @Test
    fun saveDeckWhileTemplateActive_persistsCustomFavoritesNotTemplateActions() = runTest(dispatcher) {
        val custom = DeckAction("custom", "Custom", ActionKind.Ssh, ActionIcon.Apps)
        val template = DeckAction("template", "Template", ActionKind.Ssh, ActionIcon.Browser)
        val repository = GatedActionRepository(
            action = custom,
            favorites = listOf(custom),
            allActions = listOf(custom, template),
            templateActions = mapOf("browser" to listOf(template)),
        )
        val viewModel = HomeViewModel(repository, ReadyConnectionRepository(), ImmediateActionRunner())
        runCurrent()

        viewModel.applyTemplate("browser")
        assertEquals(listOf("template"), viewModel.uiState.value.actions.map(DeckAction::id))

        viewModel.saveDeck()
        runCurrent()

        assertEquals(listOf("custom"), repository.savedFavorites.map(DeckAction::id))
    }

    @Test
    fun editingWhileTemplateActive_switchesToCustomDeckBeforeSaving() = runTest(dispatcher) {
        val custom = DeckAction("custom", "Custom", ActionKind.Ssh, ActionIcon.Apps)
        val replacement = DeckAction("replacement", "Replacement", ActionKind.Ssh, ActionIcon.Apps)
        val template = DeckAction("template", "Template", ActionKind.Ssh, ActionIcon.Browser)
        val repository = GatedActionRepository(
            action = custom,
            favorites = listOf(custom),
            allActions = listOf(custom, replacement, template),
            templateActions = mapOf("browser" to listOf(template)),
        )
        val viewModel = HomeViewModel(repository, ReadyConnectionRepository(), ImmediateActionRunner())
        runCurrent()

        viewModel.applyTemplate("browser")
        viewModel.assign(0, replacement)
        viewModel.saveDeck()
        runCurrent()

        assertEquals(CUSTOM_TEMPLATE_ID, viewModel.uiState.value.activeTemplateId)
        assertEquals(listOf("replacement"), viewModel.uiState.value.actions.map(DeckAction::id))
        assertEquals(listOf("replacement"), repository.savedFavorites.map(DeckAction::id))
    }

    @Test
    fun duplicateAction_createsUniqueIdInsteadOfDuplicateLazyListKey() = runTest(dispatcher) {
        val original = DeckAction("focus", "Focus", ActionKind.Ssh, ActionIcon.Apps)
        val blank = DeckAction("blank", "Blank", ActionKind.Local, ActionIcon.Add)
        val repository = GatedActionRepository(
            action = original,
            favorites = listOf(original, blank),
            allActions = listOf(original, blank),
        )
        val viewModel = HomeViewModel(repository, ReadyConnectionRepository(), ImmediateActionRunner())
        runCurrent()

        viewModel.duplicateAction(original)
        runCurrent()

        val ids = viewModel.uiState.value.actions.map(DeckAction::id)
        assertEquals(listOf("focus", "focus_copy"), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun removeAction_canBeUndoneBeforeNextDeckEdit() = runTest(dispatcher) {
        val original = DeckAction("focus", "Focus", ActionKind.Ssh, ActionIcon.Apps)
        val blank = DeckAction("blank", "Blank", ActionKind.Local, ActionIcon.Add)
        val repository = GatedActionRepository(
            action = original,
            favorites = listOf(original),
            allActions = listOf(original, blank),
        )
        val viewModel = HomeViewModel(repository, ReadyConnectionRepository(), ImmediateActionRunner())
        runCurrent()

        viewModel.remove(0)
        runCurrent()

        assertEquals(listOf("blank"), viewModel.uiState.value.actions.map(DeckAction::id))
        assertEquals(PendingDeckUndo(0, original), viewModel.uiState.value.pendingDeckUndo)
        assertEquals(ActionStatus.Succeeded("deck_remove", "Removed Focus"), viewModel.uiState.value.actionStatus)

        viewModel.undoLastDeckEdit()
        runCurrent()

        assertEquals(listOf("focus"), viewModel.uiState.value.actions.map(DeckAction::id))
        assertEquals(null, viewModel.uiState.value.pendingDeckUndo)
        assertEquals(ActionStatus.Succeeded("deck_undo", "Restored Focus"), viewModel.uiState.value.actionStatus)
    }

    @Test
    fun duplicateAction_whenDeckFull_failsWithoutGrowingDeck() = runTest(dispatcher) {
        val original = DeckAction("focus", "Focus", ActionKind.Ssh, ActionIcon.Apps)
        val second = DeckAction("browser", "Browser", ActionKind.Ssh, ActionIcon.Browser)
        val repository = GatedActionRepository(
            action = original,
            favorites = listOf(original, second),
            allActions = listOf(original, second),
        )
        val viewModel = HomeViewModel(repository, ReadyConnectionRepository(), ImmediateActionRunner())
        runCurrent()

        viewModel.duplicateAction(original)
        runCurrent()

        assertEquals(listOf("focus", "browser"), viewModel.uiState.value.actions.map(DeckAction::id))
        assertEquals(
            ActionStatus.Failed("deck_full", "Deck is full. Remove a control or empty a slot before duplicating Focus."),
            viewModel.uiState.value.actionStatus,
        )
    }

    @Test
    fun saveGeneratedDeck_whenDeckFull_failsWithoutGrowingDeck() = runTest(dispatcher) {
        val favorites = (1..12).map { index ->
            DeckAction("slot_$index", "Slot $index", ActionKind.Ssh, ActionIcon.Apps)
        }
        val repository = GatedActionRepository(
            action = favorites.first(),
            favorites = favorites,
            allActions = favorites,
        )
        val viewModel = HomeViewModel(repository, ReadyConnectionRepository(), ImmediateActionRunner())
        runCurrent()

        viewModel.saveGeneratedDraft(
            GeneratedDraft.Action(
                ActionDraft(
                    prompt = "add one",
                    definition = ActionDefinition(
                        id = "new.action",
                        title = "New Action",
                        steps = listOf(ActionStep("open", ActionStepTypes.Shell, value = "open -a Notes")),
                    ),
                ),
            ),
        )
        runCurrent()

        val ids = viewModel.uiState.value.actions.map(DeckAction::id)
        assertEquals((1..12).map { "slot_$it" }, ids)
        assertEquals(emptyList<DeckAction>(), repository.savedFavorites)
        assertEquals(
            ActionStatus.Failed("deck_full", "Deck needs 1 empty slot(s). Empty slots before saving this draft."),
            viewModel.uiState.value.actionStatus,
        )
    }
}

private class GatedActionRepository(
    val action: DeckAction = DeckAction("finder", "Finder", ActionKind.Ssh, ActionIcon.Finder),
    private val favorites: List<DeckAction> = listOf(action),
    private val allActions: List<DeckAction> = listOf(action),
    private val templateActions: Map<String, List<DeckAction>> = emptyMap(),
) : ActionRepository {
    var lastTestedActionId: String? = null
    var savedFavorites: List<DeckAction> = emptyList()

    override fun favorites(): List<DeckAction> = favorites

    override fun observeFavorites(): Flow<List<DeckAction>> = MutableStateFlow(favorites)

    override fun allActions(): List<DeckAction> = allActions

    override fun deckTemplates(): List<DeckTemplate> =
        templateActions.keys.map {
            DeckTemplate(
                id = it,
                title = it.replaceFirstChar(Char::titlecase),
                subtitle = "",
                icon = ActionIcon.Apps,
                actionIds = templateActions.getValue(it).map(DeckAction::id),
            )
        }

    override fun actionsForTemplate(templateId: String): List<DeckAction> =
        templateActions[templateId].orEmpty()

    override suspend fun saveFavorites(actions: List<DeckAction>) {
        savedFavorites = actions
    }

    override suspend fun run(action: DeckAction): Result<String> = Result.success("${action.label} sent")

    override suspend fun test(action: DeckAction): Result<String> {
        lastTestedActionId = action.id
        return Result.success("${action.label} verified")
    }
}

private class DeferredActionRunner : ActionRunner {
    val result = CompletableDeferred<ActionResult>()

    override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult = result.await()
}

private class ImmediateActionRunner : ActionRunner {
    override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult =
        ActionResult(spec.id, spec.title, ActionResultStatus.Succeeded, "${spec.title} sent")
}

private class ReadyConnectionRepository : ConnectionRepository {
    override val config = MutableStateFlow(
        ConnectionConfig("mac.local", 22, "user", hasKey = true, hostKey = "mac ssh-ed25519 key"),
    )

    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey(): Result<String> = Result.success("public-key")
    override suspend fun publicKey(): String = "public-key"
    override suspend fun installKey(password: String): Result<String> = Result.success("installed")
    override suspend fun test(password: String?): Result<String> = Result.success("connected")
    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> =
        Result.success("sent")
}
