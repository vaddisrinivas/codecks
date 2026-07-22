package io.codecks.data.automation

import io.codecks.core.actions.ActionSpec
import io.codecks.data.ConnectionConfig
import io.codecks.data.ConnectionRepository
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationTrigger
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAutomationTriggerEngineTest {
    @Test
    fun activeAppTriggerFiresOncePerAppFingerprint() = runTest {
        val connection = ScriptedConnectionRepository("Google Chrome")
        val engine = DefaultAutomationTriggerEngine(connection)
        val recipe = triggerRecipe(AutomationTrigger.ActiveApp("Chrome"))

        val first = engine.evaluate(listOf(recipe))
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
