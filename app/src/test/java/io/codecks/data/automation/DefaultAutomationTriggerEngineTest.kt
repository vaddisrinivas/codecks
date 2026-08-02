package io.codecks.data.automation

import io.codecks.core.actions.ActionSpec
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.revisionFingerprint
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAutomationTriggerEngineTest {
    @Test
    fun pendingClaimRetriesUntilTerminalCompletion() = runTest {
        val state = InMemoryAutomationTriggerStateStore()
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))
        val first = DefaultAutomationTriggerEngine(ScriptedConnectionRepository("Google Chrome"), state) {
            LocalDateTime.of(2026, 7, 16, 9, 0)
        }

        val firstEvaluation = first.evaluate(listOf(recipe))
        assertEquals(listOf(recipe), firstEvaluation.dueRecipes)
        val recreated = DefaultAutomationTriggerEngine(ScriptedConnectionRepository("Google Chrome"), state) {
            LocalDateTime.of(2026, 7, 16, 9, 1)
        }
        assertTrue(recreated.evaluate(listOf(recipe)).dueRecipes.isEmpty())
        assertTrue(first.complete(firstEvaluation.claimsByRecipeId.getValue(recipe.id)))
    }

    @Test
    fun retryableBlockReleasesExactClaimAndReacquiresBeforeLeaseExpiry() = runTest {
        val state = InMemoryAutomationTriggerStateStore()
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))
        val now = LocalDateTime.of(2026, 7, 16, 9, 0)
        val engine = DefaultAutomationTriggerEngine(ScriptedConnectionRepository("Google Chrome"), state) { now }

        val first = engine.evaluate(listOf(recipe)).claimsByRecipeId.getValue(recipe.id)
        assertTrue(engine.release(first))
        val replacement = engine.evaluate(listOf(recipe)).claimsByRecipeId.getValue(recipe.id)

        assertFalse(first.claimId == replacement.claimId)
        assertFalse(engine.release(first))
        assertTrue(engine.complete(replacement))
    }

    @Test
    fun exactClaimCompletionCannotCommitAChangedFingerprint() = runTest {
        val connection = ScriptedConnectionRepository("Google Chrome")
        val engine = DefaultAutomationTriggerEngine(connection)
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Browser"))
        val first = engine.evaluate(listOf(recipe)).claimsByRecipeId.getValue(recipe.id)

        connection.rawOutput = "Safari"
        assertTrue(engine.evaluate(listOf(recipe)).dueRecipes.isEmpty())
        assertTrue(engine.complete(first))

        assertEquals(listOf(recipe), engine.evaluate(listOf(recipe)).dueRecipes)
    }

    @Test
    fun collidingJavaHashCodesUseDifferentPersistentKeys() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertFalse(automationTriggerStateKey("Aa") == automationTriggerStateKey("BB"))
        assertEquals(automationLegacyTriggerStateKey("Aa"), automationLegacyTriggerStateKey("BB"))
    }

    @Test
    fun collidingLegacyKeysMigrateForEveryRecipeWithoutReplay() = runTest {
        val state = InMemoryAutomationTriggerStateStore()
        val legacyFingerprint = "chrome::${"Google Chrome".hashCode()}"
        state.seedLegacy("Aa", legacyFingerprint)
        val connection = ScriptedConnectionRepository("Google Chrome")
        val first = triggerRecipe(AutomationTrigger.ActiveApp("Chrome")).copy(id = "Aa")
        val second = triggerRecipe(AutomationTrigger.ActiveApp("Chrome")).copy(id = "BB")
        val engine = DefaultAutomationTriggerEngine(connection, state) {
            LocalDateTime.of(2026, 7, 16, 9, 0)
        }

        assertTrue(engine.evaluate(listOf(first, second)).dueRecipes.isEmpty())
        assertTrue(state.get("Aa").orEmpty().startsWith("done|chrome::"))
        assertTrue(state.get("BB").orEmpty().startsWith("done|chrome::"))

        val firstDone = state.get("Aa")
        assertTrue(state.compareAndSet("Aa", firstDone, null))
        assertEquals(null, state.get("Aa"))
        assertTrue(state.get("BB").orEmpty().startsWith("done|chrome::"))
    }

    @Test
    fun legacyTextFingerprintMigratesWithoutReplayingTheTrigger() = runTest {
        val state = InMemoryAutomationTriggerStateStore()
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))
        state.put(recipe.id, "chrome::${"Google Chrome".hashCode()}")
        val engine = DefaultAutomationTriggerEngine(ScriptedConnectionRepository("Google Chrome"), state) {
            LocalDateTime.of(2026, 7, 16, 9, 0)
        }

        assertTrue(engine.evaluate(listOf(recipe)).dueRecipes.isEmpty())
        assertTrue(state.get(recipe.id).orEmpty().startsWith("done|chrome::"))
    }

    @Test
    fun expiredLeaseCanBeReclaimed() = runTest {
        val now = LocalDateTime.of(2026, 7, 16, 9, 0)
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))
        val state = InMemoryAutomationTriggerStateStore()
        val expiredAt = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        state.put(
            recipe.id,
            "running|old-claim|$expiredAt|${recipe.revisionFingerprint()}|old-fingerprint",
        )
        val engine = DefaultAutomationTriggerEngine(ScriptedConnectionRepository("Google Chrome"), state) { now }

        assertEquals(listOf(recipe), engine.evaluate(listOf(recipe)).dueRecipes)
    }

    @Test
    fun recipeEditInvalidatesAnOutstandingClaimWithoutRunningTheNewRevision() = runTest {
        val state = InMemoryAutomationTriggerStateStore()
        val engine = DefaultAutomationTriggerEngine(ScriptedConnectionRepository("Google Chrome"), state) {
            LocalDateTime.of(2026, 7, 16, 9, 0)
        }
        val original = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))
        assertEquals(listOf(original), engine.evaluate(listOf(original)).dueRecipes)
        val edited = original.copy(
            steps = listOf(ActionSpec.ShellCommand("trigger", "Trigger", "echo edited")),
        )

        assertTrue(engine.evaluate(listOf(edited)).dueRecipes.isEmpty())
    }

    @Test
    fun claimAcquisitionStopsAfterBoundedCasContention() = runTest {
        val state = AlwaysContendedTriggerStateStore()
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))
        val engine = DefaultAutomationTriggerEngine(ScriptedConnectionRepository("Google Chrome"), state) {
            LocalDateTime.of(2026, 7, 16, 9, 0)
        }

        assertTrue(engine.evaluate(listOf(recipe)).dueRecipes.isEmpty())
        assertEquals(8, state.compareAndSetCalls)
    }

    @Test
    fun activeAppTriggerFiresOncePerAppFingerprint() = runTest {
        val connection = ScriptedConnectionRepository("Google Chrome")
        val engine = DefaultAutomationTriggerEngine(connection)
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))

        val first = engine.evaluate(listOf(recipe))
        engine.complete(recipe.id)
        val second = engine.evaluate(listOf(recipe))

        assertEquals(listOf(recipe), first.dueRecipes)
        assertTrue(second.dueRecipes.isEmpty())
    }

    @Test
    fun activeAppTriggerUsesRawOutputAndBrowserAliases() = runTest {
        val connection = ScriptedConnectionRepository(
            output = "summary clipped",
            rawOutput = "Google Chrome",
        )
        val engine = DefaultAutomationTriggerEngine(connection)
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Browser"))

        val result = engine.evaluate(listOf(recipe))

        assertEquals(listOf(recipe), result.dueRecipes)
    }

    @Test
    fun fileChangedTriggerSkipsInitialFingerprintThenFiresOnChange() = runTest {
        val connection = ScriptedConnectionRepository("100")
        val engine = DefaultAutomationTriggerEngine(connection)
        val recipe = triggerRecipe(AutomationTrigger.FileChanged("~/Downloads"))

        assertTrue(engine.evaluate(listOf(recipe)).dueRecipes.isEmpty())
        connection.output = "101"
        connection.rawOutput = "101"

        assertEquals(listOf(recipe), engine.evaluate(listOf(recipe)).dueRecipes)
    }

    @Test
    fun batteryTriggerFiresWhenBelowThreshold() = runTest {
        val connection = ScriptedConnectionRepository("19")
        val engine = DefaultAutomationTriggerEngine(connection)
        val recipe = triggerRecipe(AutomationTrigger.BatteryBelow(20))

        assertEquals(listOf(recipe), engine.evaluate(listOf(recipe)).dueRecipes)
    }

    @Test
    fun timeTriggerUsesGraceWindowAndDurableFingerprint() = runTest {
        val state = InMemoryAutomationTriggerStateStore()
        val now = LocalDateTime.of(2026, 7, 16, 9, 14)
        val recipe = triggerRecipe(AutomationTrigger.TimeOfDay(9, 0, setOf("Thu")))
        val firstEngine = DefaultAutomationTriggerEngine(ScriptedConnectionRepository(""), state) { now }

        assertEquals(listOf(recipe), firstEngine.evaluate(listOf(recipe)).dueRecipes)
        firstEngine.complete(recipe.id)
        assertFalse(firstEngine.evaluate(listOf(recipe)).dueRecipes.isNotEmpty())

        val recreatedEngine = DefaultAutomationTriggerEngine(ScriptedConnectionRepository(""), state) { now.plusMinutes(15) }
        assertTrue(recreatedEngine.evaluate(listOf(recipe)).dueRecipes.isEmpty())
    }

    @Test
    fun timeTriggerDoesNotFireOutsideWindow() = runTest {
        val recipe = triggerRecipe(AutomationTrigger.TimeOfDay(9, 0, setOf("Thu")))
        val before = DefaultAutomationTriggerEngine(
            ScriptedConnectionRepository(""),
            InMemoryAutomationTriggerStateStore(),
        ) { LocalDateTime.of(2026, 7, 16, 8, 59) }
        val late = DefaultAutomationTriggerEngine(
            ScriptedConnectionRepository(""),
            InMemoryAutomationTriggerStateStore(),
        ) { LocalDateTime.of(2026, 7, 16, 10, 31) }

        assertTrue(before.evaluate(listOf(recipe)).dueRecipes.isEmpty())
        assertTrue(late.evaluate(listOf(recipe)).dueRecipes.isEmpty())
    }

    private fun triggerRecipe(trigger: AutomationTrigger): AutomationRecipe =
        AutomationRecipe(
            id = "trigger",
            title = "Trigger",
            description = "Triggered recipe",
            trigger = trigger,
            steps = listOf(ActionSpec.ShellCommand("trigger", "Trigger", "true")),
        )
}

private class AlwaysContendedTriggerStateStore : AutomationTriggerStateStore {
    var compareAndSetCalls = 0

    override suspend fun get(recipeId: String): String? = null

    override suspend fun put(recipeId: String, fingerprint: String) = Unit

    override suspend fun compareAndSet(recipeId: String, expected: String?, updated: String?): Boolean {
        compareAndSetCalls += 1
        return false
    }
}

private class ScriptedConnectionRepository(
    var output: String,
    var rawOutput: String = output,
) : ConnectionRepository {
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
    override suspend fun runAction(actionId: String, dangerous: Boolean): Result<String> = Result.success("ran")
    override suspend fun runCommand(command: String): Result<String> = Result.success(output)
    override suspend fun runCommandRaw(command: String): Result<String> = Result.success(rawOutput)
    override suspend fun runCommandWithInput(command: String, stdin: String): Result<String> = Result.success(output)
    override suspend fun validateCommandSyntax(command: String): Result<String> = Result.success("syntax ok")
    override suspend fun runCommandSecret(command: String): Result<String> = Result.success(output)
    override suspend fun selectTarget(targetId: String): Result<String> = Result.success("selected")
    override suspend fun removeTarget(targetId: String): Result<String> = Result.success("removed")
}
