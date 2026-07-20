package io.codecks.transport.ssh

import io.codecks.domain.actions.ActionPlan
import io.codecks.domain.actions.ExecutorKind
import io.codecks.domain.actions.PlannedStep
import io.codecks.domain.actions.TimeoutPolicy
import io.codecks.domain.targets.TargetCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSshCommandCatalogTest {
    @Test
    fun finderProofMapsToHarmlessOpenCommand() {
        assertEquals("open -a Finder", CoreSshCommandCatalog.commandFor(plan("mac.finder.open")))
    }

    @Test
    fun urlCommandRejectsUnsupportedSchemes() {
        assertNull(CoreSshCommandCatalog.commandFor(plan("mac.url.open"), mapOf("url" to "file:///etc/passwd")))
    }

    @Test
    fun urlCommandSingleQuotesInjectionChars() {
        val command = CoreSshCommandCatalog.commandFor(
            plan("mac.url.open"),
            mapOf("url" to "https://example.com/a'b"),
        )

        assertTrue(command!!.startsWith("open 'https://example.com/"))
        assertTrue(command.contains("'\"'\"'"))
    }

    private fun plan(actionId: String) = ActionPlan(
        invocationId = "inv-1",
        resolvedTargetId = "mac-1",
        steps = listOf(
            PlannedStep(
                stableId = actionId,
                executorKind = ExecutorKind.SSH_MAC,
                safeSummary = actionId,
                timeoutPolicy = TimeoutPolicy(),
            ),
        ),
        expectedCapabilities = setOf(TargetCapability.SSH_APP_CONTROL),
    )
}
