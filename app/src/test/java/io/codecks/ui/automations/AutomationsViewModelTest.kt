package io.codecks.ui.automations

import io.codecks.core.actions.ActionResult
import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionRunner
import io.codecks.core.actions.ActionSpec
import io.codecks.core.actions.commandRevision
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.data.automation.AutomationRepository
import io.codecks.domain.CommandOrigin
import io.codecks.domain.CommandReview
import io.codecks.domain.ai.ActionDefinition
import io.codecks.domain.ai.ActionStep
import io.codecks.domain.ai.ActionStepTypes
import io.codecks.domain.ai.AutomationDraft
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactAction
import io.codecks.domain.ai.AiArtifactKind
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationRunSummary
import io.codecks.domain.automation.AutomationSafety
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationTriggerEngine
import io.codecks.domain.automation.AutomationTriggerEvaluation
import io.codecks.domain.automation.hasCurrentSuccessfulTest
import io.codecks.domain.automation.revisionFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationsViewModelTest {
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
    fun runRecipe_recordsLastRun() = runTest(dispatcher) {
        val repository = FakeAutomationRepository()
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        viewModel.run("focus")
        runCurrent()

        assertEquals(null, viewModel.uiState.value.runningActionId)
        assertTrue(repository.recordedResults.single().succeeded)
        assertEquals("Focus detailed log", repository.recipes.value.single { it.id == "focus" }.lastRun?.logs)
        assertEquals("Focus detailed log", repository.recipes.value.single { it.id == "focus" }.runHistory.single().logs)
        assertEquals(listOf("Last run OK: Focus ok"), viewModel.uiState.value.automations.single { it.id == "focus" }.runHistoryLabels)
        assertEquals("Focus ok", viewModel.uiState.value.message)
    }

    @Test
    fun checkTriggersNow_runsDueRecipes() = runTest(dispatcher) {
        val repository = FakeAutomationRepository()
        val runner = FakeRunner()
        val triggerEngine = FakeTriggerEngine(dueRecipeIds = setOf("focus"))
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), runner, triggerEngine)
        runCurrent()

        viewModel.checkTriggersNow()
        runCurrent()

        assertEquals(listOf("focus"), runner.ranIds)
        assertEquals("1 trigger matched", viewModel.uiState.value.triggerMonitorLabel)
        assertTrue(repository.recordedResults.single().succeeded)
    }

    @Test
    fun checkTriggersNow_doesNotOverlapActiveEvaluation() = runTest(dispatcher) {
        val repository = FakeAutomationRepository()
        val triggerEngine = FakeTriggerEngine(dueRecipeIds = setOf("focus"))
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), triggerEngine)
        runCurrent()

        viewModel.checkTriggersNow()
        viewModel.checkTriggersNow()
        assertEquals("Trigger check already running", viewModel.uiState.value.message)
        runCurrent()

        assertEquals(1, triggerEngine.evaluateCount)
    }

    @Test
    fun testRecipe_dryRunsWithoutExecutingActions() = runTest(dispatcher) {
        val previousRun = AutomationRunSummary(
            status = ActionResultStatus.Failed,
            message = "Previous real run failed",
            logs = "Previous log",
            timestampMillis = 10L,
        )
        val repository = FakeAutomationRepository(
            listOf(
                AutomationRecipe(
                    id = "focus",
                    title = "Focus",
                    description = "Start focus",
                    steps = listOf(ActionSpec.ShellCommand("focus", "Focus", "caffeinate")),
                    lastRun = previousRun,
                    runHistory = listOf(previousRun),
                ),
            ),
        )
        val runner = FakeRunner()
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), runner, FakeTriggerEngine())
        runCurrent()

        viewModel.test("focus")
        runCurrent()

        assertEquals(emptyList<String>(), runner.ranIds)
        assertEquals(emptyList<ActionResult>(), repository.recordedResults)
        assertEquals(previousRun, repository.recipes.value.single { it.id == "focus" }.lastRun)
        assertEquals(listOf(previousRun), repository.recipes.value.single { it.id == "focus" }.runHistory)
        assertTrue(viewModel.uiState.value.message.orEmpty().startsWith("Test:"))
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("No Mac command ran"))
    }

    @Test
    fun enableRequiresPassingValidationForCurrentRevision() = runTest(dispatcher) {
        val repository = FakeAutomationRepository(
            listOf(
                AutomationRecipe(
                    id = "focus",
                    title = "Focus",
                    description = "Start focus",
                    enabled = false,
                    steps = listOf(ActionSpec.ShellCommand("focus", "Focus", "caffeinate")),
                ),
            ),
        )
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        viewModel.toggle("focus", true)
        runCurrent()
        assertFalse(repository.recipes.value.single().enabled)

        viewModel.test("focus")
        runCurrent()
        viewModel.toggle("focus", true)
        runCurrent()

        assertTrue(repository.recipes.value.single().enabled)
        assertTrue(viewModel.uiState.value.automations.single().lastTestSucceeded == true)
    }

    @Test
    fun editInvalidatesPreviousValidation() = runTest(dispatcher) {
        val original = AutomationRecipe(
            id = "focus",
            title = "Focus",
            description = "Start focus",
            enabled = false,
            steps = listOf(ActionSpec.ShellCommand("focus", "Focus", "caffeinate")),
        )
        val repository = FakeAutomationRepository(listOf(original))
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()
        viewModel.test("focus")
        runCurrent()
        assertTrue(viewModel.uiState.value.automations.single().canEnable)

        viewModel.edit(
            AutomationDraftInput(
                recipeId = "focus",
                title = "Focus changed",
                triggerType = AutomationTriggerDraftType.Manual,
                triggerValue = "",
                command = "open -a Safari",
                enabled = false,
            ),
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.automations.single().canEnable)
    }

    @Test
    fun runDangerousRecipe_requiresConfirmationInsteadOfAllowingDangerous() = runTest(dispatcher) {
        val dangerousRecipe = AutomationRecipe(
            id = "danger",
            title = "Danger",
            description = "Danger",
            steps = listOf(
                ActionSpec.ShellCommand(
                    id = "danger",
                    title = "Danger",
                    command = "osascript -e 'display dialog \"danger\"'",
                    dangerous = true,
                ),
            ),
        )
        val repository = FakeAutomationRepository(listOf(dangerousRecipe))
        val runner = FakeRunner()
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), runner, FakeTriggerEngine())
        runCurrent()

        viewModel.run("danger")
        runCurrent()

        assertEquals(listOf("danger"), runner.ranIds)
        assertEquals(listOf(false), runner.allowDangerousValues)
        assertFalse(repository.recordedResults.single().succeeded)
        assertEquals(ActionResultStatus.RequiresConfirmation, repository.recordedResults.single().status)
    }

    @Test
    fun triggeredDangerousRecipe_recordsConfirmationWithoutExecuting() = runTest(dispatcher) {
        val dangerousRecipe = AutomationRecipe(
            id = "danger",
            title = "Danger",
            description = "Danger",
            trigger = AutomationTrigger.ActiveApp("Browser"),
            steps = listOf(
                ActionSpec.ShellCommand(
                    id = "danger",
                    title = "Danger",
                    command = "osascript -e 'display dialog \"danger\"'",
                    dangerous = true,
                ),
            ),
            safety = AutomationSafety(requiresConfirmation = true),
        )
        val repository = FakeAutomationRepository(listOf(dangerousRecipe))
        val runner = FakeRunner()
        val viewModel = AutomationsViewModel(
            repository,
            ReadyConnectionRepository(),
            runner,
            FakeTriggerEngine(dueRecipeIds = setOf("danger")),
        )
        runCurrent()

        viewModel.checkTriggersNow()
        runCurrent()

        assertEquals(emptyList<String>(), runner.ranIds)
        assertEquals(ActionResultStatus.RequiresConfirmation, repository.recordedResults.single().status)
        assertEquals("Trigger needs confirmation: Danger", viewModel.uiState.value.message)
    }

    @Test
    fun create_savesRunnableTriggeredRecipe() = runTest(dispatcher) {
        val repository = FakeAutomationRepository()
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        viewModel.create(
            AutomationDraftInput(
                title = "Open Safari",
                triggerType = AutomationTriggerDraftType.ActiveApp,
                triggerValue = "Safari",
                command = "open -a Safari",
            ),
        )
        runCurrent()

        val recipe = repository.recipes.value.first { it.title == "Open Safari" }
        val step = recipe.steps.single() as ActionSpec.ShellCommand
        assertTrue(recipe.trigger is AutomationTrigger.ActiveApp)
        assertEquals(CommandOrigin.UserAuthored, step.commandOrigin)
        assertEquals(step.commandRevision(), step.review.reviewedRevision)
        assertEquals("App: Safari", viewModel.uiState.value.automations.first { it.label == "Open Safari" }.triggerLabel)
        assertEquals("Open Safari saved", viewModel.uiState.value.message)
    }

    @Test
    fun create_rejectsKnownBlockedCommand() = runTest(dispatcher) {
        val repository = FakeAutomationRepository(emptyList())
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        viewModel.create(
            AutomationDraftInput(
                title = "Bad",
                triggerType = AutomationTriggerDraftType.Manual,
                triggerValue = "",
                command = "rm -rf /tmp/codecks-danger",
            ),
        )
        runCurrent()

        assertEquals(emptyList<AutomationRecipe>(), repository.recipes.value)
        assertTrue(viewModel.uiState.value.message.orEmpty().startsWith("Command blocked:"))
    }

    @Test
    fun create_supportsWeekdayTimeTrigger() = runTest(dispatcher) {
        val repository = FakeAutomationRepository(emptyList())
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        viewModel.create(
            AutomationDraftInput(
                title = "Weekday Standup",
                triggerType = AutomationTriggerDraftType.TimeOfDay,
                triggerValue = "09:30",
                command = "open -a Calendar",
                weekdays = setOf("Mon", "Tue", "Wed", "Thu", "Fri"),
            ),
        )
        runCurrent()

        val trigger = repository.recipes.value.single().trigger
        assertTrue(trigger is AutomationTrigger.TimeOfDay)
        trigger as AutomationTrigger.TimeOfDay
        assertEquals(9, trigger.hour)
        assertEquals(30, trigger.minute)
        assertEquals(setOf("Mon", "Tue", "Wed", "Thu", "Fri"), trigger.days)
    }

    @Test
    fun deleteRecipe_canBeUndone() = runTest(dispatcher) {
        val recipe = AutomationRecipe(
            id = "focus",
            title = "Focus",
            description = "Start focus",
            steps = listOf(ActionSpec.ShellCommand("focus", "Focus", "caffeinate")),
        )
        val repository = FakeAutomationRepository(listOf(recipe))
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        viewModel.delete("focus")
        runCurrent()

        assertEquals(emptyList<AutomationRecipe>(), repository.recipes.value)
        assertEquals("Deleted Focus", viewModel.uiState.value.message)
        assertEquals(PendingAutomationUndo("focus", "Focus"), viewModel.uiState.value.pendingUndo)

        viewModel.undoDelete()
        runCurrent()

        assertEquals(listOf(recipe), repository.recipes.value)
        assertEquals("Restored Focus", viewModel.uiState.value.message)
        assertEquals(null, viewModel.uiState.value.pendingUndo)
    }

    @Test
    fun editRecipe_preservesIdAndUpdatesRunnableRecipe() = runTest(dispatcher) {
        val recipe = AutomationRecipe(
            id = "focus",
            title = "Focus",
            description = "Manual",
            steps = listOf(ActionSpec.ShellCommand("focus", "Focus", "caffeinate")),
        )
        val repository = FakeAutomationRepository(listOf(recipe))
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        viewModel.edit(
            AutomationDraftInput(
                recipeId = "focus",
                title = "Open Safari",
                triggerType = AutomationTriggerDraftType.ActiveApp,
                triggerValue = "Safari",
                command = "open -a Safari",
                enabled = false,
            ),
        )
        runCurrent()

        val edited = repository.recipes.value.single()
        assertEquals("focus", edited.id)
        assertEquals("Open Safari", edited.title)
        assertEquals(false, edited.enabled)
        assertTrue(edited.trigger is AutomationTrigger.ActiveApp)
        assertEquals("open -a Safari", (edited.steps.single() as ActionSpec.ShellCommand).command)
        assertEquals("Open Safari saved", viewModel.uiState.value.message)
    }

    @Test
    fun saveGeneratedAutomationDraft_createsDisabledManualRecipe() = runTest(dispatcher) {
        val repository = FakeAutomationRepository(emptyList())
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        val consumed = viewModel.saveGeneratedDraft(
            GeneratedDraft.Automation(
                AutomationDraft(
                    prompt = "make a meeting setup automation",
                    id = "meeting",
                    label = "Meeting Setup",
                    category = "Meetings",
                    definition = ActionDefinition(
                        id = "meeting",
                        title = "Meeting Setup",
                        steps = listOf(ActionStep("open", ActionStepTypes.OpenUrl, url = "https://zoom.us")),
                    ),
                ),
            ),
        )
        runCurrent()

        val recipe = repository.recipes.value.single()
        assertTrue(consumed)
        assertFalse(recipe.enabled)
        assertEquals(AutomationTrigger.Manual, recipe.trigger)
        assertEquals("Manual", viewModel.uiState.value.automations.single().triggerLabel)
        assertEquals("Meeting Setup saved", viewModel.uiState.value.message)
    }

    @Test
    fun saveAutomationArtifact_createsDisabledManualRecipe() = runTest(dispatcher) {
        val repository = FakeAutomationRepository(emptyList())
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        val consumed = viewModel.saveArtifact(
            AiArtifact(
                id = "artifact-1",
                kind = AiArtifactKind.Automation,
                title = "Daily Setup",
                description = "",
                prompt = "daily setup",
                createdAtMillis = 1L,
                actions = listOf(
                    AiArtifactAction("open", "Open Calendar", "open 'https://calendar.google.com'"),
                ),
            ),
        )
        runCurrent()

        val recipe = repository.recipes.value.single()
        assertTrue(consumed)
        assertFalse(recipe.enabled)
        assertEquals(AutomationTrigger.Manual, recipe.trigger)
        assertEquals("AI-created automation from: daily setup", recipe.description)
        assertEquals("Manual", viewModel.uiState.value.automations.single().triggerLabel)
    }

    @Test
    fun generatedAutomationStep_persistsReviewedButUntestedRevision() = runTest(dispatcher) {
        val repository = FakeAutomationRepository(emptyList())
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()

        val consumed = viewModel.saveArtifact(
            AiArtifact(
                id = "artifact-1",
                kind = AiArtifactKind.Automation,
                title = "Daily Setup",
                prompt = "daily setup",
                actions = listOf(
                    AiArtifactAction("open", "Open Calendar", "open 'https://calendar.google.com'"),
                ),
            ),
        )
        runCurrent()

        val step = repository.recipes.value.single().steps.single() as ActionSpec.ShellCommand
        assertTrue(consumed)
        assertEquals(CommandOrigin.AiGenerated, step.commandOrigin)
        assertEquals(step.commandRevision(), step.review.reviewedRevision)
        assertEquals(null, step.review.checkedRevision)
        assertFalse(repository.recipes.value.single().hasCurrentSuccessfulTest())
    }

    @Test
    fun editedReviewedCommand_invalidatesRuleTestRevision() = runTest(dispatcher) {
        val command = "open -a Safari"
        val original = AutomationRecipe(
            id = "focus",
            title = "Focus",
            description = "Start focus",
            enabled = false,
            steps = listOf(
                ActionSpec.ShellCommand(
                    id = "focus",
                    title = "Focus",
                    command = command,
                    commandOrigin = CommandOrigin.UserAuthored,
                    review = CommandReview(
                        reviewedRevision = commandRevision(
                            command = command,
                            targetSelector = io.codecks.domain.device.TargetSelector.CurrentDevice,
                            origin = CommandOrigin.UserAuthored,
                            dangerous = false,
                        ),
                    ),
                ),
            ),
        )
        val repository = FakeAutomationRepository(listOf(original))
        val viewModel = AutomationsViewModel(repository, ReadyConnectionRepository(), FakeRunner(), FakeTriggerEngine())
        runCurrent()
        viewModel.test("focus")
        runCurrent()
        assertTrue(repository.recipes.value.single().hasCurrentSuccessfulTest())

        viewModel.edit(
            AutomationDraftInput(
                recipeId = "focus",
                title = "Focus",
                triggerType = AutomationTriggerDraftType.Manual,
                triggerValue = "",
                command = "open -a Notes",
                enabled = false,
            ),
        )
        runCurrent()

        val edited = repository.recipes.value.single()
        assertEquals(null, edited.lastTest)
        assertEquals(null, edited.lastTestRevision)
        assertFalse(edited.hasCurrentSuccessfulTest())
    }
}

private class FakeAutomationRepository(
    initialRecipes: List<AutomationRecipe> = listOf(
        AutomationRecipe(
            id = "focus",
            title = "Focus",
            description = "Start focus",
            steps = listOf(ActionSpec.ShellCommand("focus", "Focus", "caffeinate")),
        ),
    ),
) : AutomationRepository {
    override val recipes = MutableStateFlow(initialRecipes)
    val recordedResults = mutableListOf<ActionResult>()
    override suspend fun save(recipe: AutomationRecipe) {
        val previous = recipes.value.firstOrNull { it.id == recipe.id }
        val saved = if (previous != null && previous.revisionFingerprint() != recipe.revisionFingerprint()) {
            recipe.copy(lastTest = null, lastTestRevision = null, pendingApproval = null)
        } else {
            recipe
        }
        recipes.value = recipes.value.filterNot { it.id == recipe.id } + saved
    }
    override suspend fun delete(recipeId: String) {
        recipes.value = recipes.value.filterNot { it.id == recipeId }
    }
    override suspend fun duplicate(recipeId: String) = Unit
    override suspend fun recordRun(recipeId: String, result: ActionResult) {
        recordedResults += result
        val summary = AutomationRunSummary(
            status = result.status,
            message = result.message,
            logs = result.logs,
            timestampMillis = result.timestampMillis,
        )
        recipes.value = recipes.value.map { recipe ->
            if (recipe.id == recipeId) {
                recipe.copy(
                    lastRun = summary,
                    runHistory = (listOf(summary) + recipe.runHistory).take(10),
                    pendingApproval = if (result.status == ActionResultStatus.RequiresConfirmation) summary else null,
                )
            } else {
                recipe
            }
        }
    }
    override suspend fun recordTest(recipeId: String, result: ActionResult, revision: String) {
        val summary = AutomationRunSummary(
            status = result.status,
            message = result.message,
            logs = result.logs,
            timestampMillis = result.timestampMillis,
        )
        recipes.value = recipes.value.map { recipe ->
            if (recipe.id == recipeId) recipe.copy(lastTest = summary, lastTestRevision = revision) else recipe
        }
    }
    override suspend fun clearPendingApproval(recipeId: String) {
        recipes.value = recipes.value.map { recipe ->
            if (recipe.id == recipeId) recipe.copy(pendingApproval = null) else recipe
        }
    }
    override suspend fun exportRecipes(): Result<String> = Result.success("")
    override suspend fun validateRecipes(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun importRecipes(payload: String): Result<Unit> = Result.success(Unit)
    override suspend fun resetDefaults() = Unit
}

private class FakeRunner : ActionRunner {
    val ranIds = mutableListOf<String>()
    val allowDangerousValues = mutableListOf<Boolean>()

    override suspend fun run(spec: ActionSpec, allowDangerous: Boolean): ActionResult {
        ranIds += spec.id
        allowDangerousValues += allowDangerous
        if (spec.dangerous && !allowDangerous) {
            return ActionResult(spec.id, spec.title, ActionResultStatus.RequiresConfirmation, "Confirmation required")
        }
        return ActionResult(
            actionId = spec.id,
            title = spec.title,
            status = ActionResultStatus.Succeeded,
            message = "${spec.title} ok",
            logs = "${spec.title} detailed log",
        )
    }
}

private class FakeTriggerEngine(
    private val dueRecipeIds: Set<String> = emptySet(),
) : AutomationTriggerEngine {
    var evaluateCount = 0

    override suspend fun evaluate(recipes: List<AutomationRecipe>): AutomationTriggerEvaluation {
        evaluateCount += 1
        val due = recipes.filter { it.id in dueRecipeIds }
        return AutomationTriggerEvaluation(
            dueRecipes = due,
            checkedCount = recipes.size,
            message = if (due.isEmpty()) "Checked ${recipes.size} triggers" else "${due.size} trigger matched",
        )
    }
}

private class ReadyConnectionRepository : ConnectionRepository {
    override val config = MutableStateFlow(ConnectionConfig("mac.local", 22, "user", hasKey = true, hostKey = "key"))
    override suspend fun save(host: String, port: Int, user: String) = Unit
    override suspend fun generateKey(): Result<String> = Result.success("key")
    override suspend fun publicKey(): String = "key"
    override suspend fun trustHostKey(): Result<String> = Result.success("trusted")
    override suspend fun confirmPendingHostKey(): Result<String> = Result.success("confirmed")
    override suspend fun rotateKey(): Result<String> = Result.success("rotated")
    override suspend fun resetTrust(): Result<String> = Result.success("reset")
    override suspend fun installKey(password: String): Result<String> = Result.success("installed")
    override suspend fun test(password: String?): Result<String> = Result.success("connected")
    override suspend fun runCommand(command: String): Result<String> = Result.success("sent")
    override suspend fun runCommandWithInput(command: String, stdin: String): Result<String> = Result.success("sent")
    override suspend fun validateCommandSyntax(command: String): Result<String> = Result.success("syntax ok")
    override suspend fun runCommandSecret(command: String): Result<String> = Result.success("sent")
    override suspend fun selectTarget(targetId: String): Result<String> = Result.success("selected")
    override suspend fun removeTarget(targetId: String): Result<String> = Result.success("removed")
    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> = Result.success("ran")
}
