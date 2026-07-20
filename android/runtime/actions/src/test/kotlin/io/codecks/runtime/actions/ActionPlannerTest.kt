package io.codecks.runtime.actions

import io.codecks.domain.actions.ActionInvocation
import io.codecks.domain.actions.CoreActionCatalog
import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.SshIdentity
import io.codecks.domain.targets.TargetCapability
import io.codecks.domain.targets.TargetSelection
import io.codecks.domain.targets.TrustState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPlannerTest {
    private val target = MacTarget(
        logicalId = "macbook",
        displayName = "MacBook",
        sshIdentity = SshIdentity(host = "192.168.1.5", username = "sv"),
        capabilities = setOf(TargetCapability.SSH_APP_CONTROL, TargetCapability.SSH_SYSTEM_CONTROL),
        trustState = TrustState.TRUSTED,
        liveState = LiveState.ONLINE,
    )

    @Test
    fun `safe action plans without confirmation`() {
        val plan = ActionPlanner().plan(
            definition = CoreActionCatalog.requireDefinition("mac.finder.open"),
            invocation = invocation("mac.finder.open"),
            currentTarget = target,
            savedTargets = listOf(target),
        )

        assertEquals("macbook", plan.resolvedTargetId)
        assertTrue(plan.confirmations.isEmpty())
        assertEquals("mac.finder.open", plan.steps.single().stableId)
    }

    @Test
    fun `confirm action creates expiring confirmation`() {
        val plan = ActionPlanner().plan(
            definition = CoreActionCatalog.requireDefinition("mac.lock"),
            invocation = invocation("mac.lock"),
            currentTarget = target,
            savedTargets = listOf(target),
        )

        assertEquals(1, plan.confirmations.size)
        assertEquals(1_060_000L, plan.confirmations.single().expiresAtEpochMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing capability fails closed`() {
        ActionPlanner().plan(
            definition = CoreActionCatalog.requireDefinition("mac.media.play-pause"),
            invocation = invocation("mac.media.play-pause"),
            currentTarget = target,
            savedTargets = listOf(target),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `untrusted target fails closed`() {
        ActionPlanner().plan(
            definition = CoreActionCatalog.requireDefinition("mac.finder.open"),
            invocation = invocation("mac.finder.open"),
            currentTarget = target.copy(trustState = TrustState.UNTRUSTED),
            savedTargets = listOf(target),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `offline target fails closed`() {
        ActionPlanner().plan(
            definition = CoreActionCatalog.requireDefinition("mac.finder.open"),
            invocation = invocation("mac.finder.open"),
            currentTarget = target.copy(liveState = LiveState.OFFLINE),
            savedTargets = listOf(target),
        )
    }

    private fun invocation(actionId: String) = ActionInvocation(
        invocationId = "inv-1",
        actionId = actionId,
        targetSelection = TargetSelection.Current,
        origin = "test",
        requestedAtEpochMillis = 1_000_000,
    )
}
