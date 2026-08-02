package io.codecks.data.automation

import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.CommandOrigin
import io.codecks.domain.automation.AutomationExecutionPlanCompiler
import io.codecks.domain.automation.AutomationLiveTestCleanupCode
import io.codecks.domain.automation.AutomationLiveTestOutcomeCode
import io.codecks.domain.automation.AutomationLiveTestTerminalStatus
import io.codecks.domain.automation.AutomationLiveTestTimeoutPolicy
import io.codecks.domain.automation.AutomationStepTerminalStatus
import io.codecks.domain.automation.AutomationLiveTestAssertion
import io.codecks.domain.automation.AutomationLiveTestCleanup
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationPreflightArea
import io.codecks.domain.automation.AutomationPreflightCheck
import io.codecks.domain.automation.AutomationPreflightReceipt
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationTrigger
import io.codecks.domain.automation.AutomationCapabilityCodes
import io.codecks.domain.automation.hasCurrentValidLiveTest
import io.codecks.domain.automation.requiredPermissions
import io.codecks.domain.smart.SmartCapability
import io.codecks.domain.automation.revisionFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationTriggerWorkerSafetyTest {
    @Test
    fun persistedEnabledRuleWithoutReceipts_isBlockedFromBackgroundExecution() {
        val result = backgroundGate(
            recipe().copy(lastPreflight = null, lastLiveTest = null),
            "mac|host|22|key",
            1_000L,
        )
        assertNotNull(result)
        assertEquals(
            ActionResultStatus.RequiresReview,
            result?.status,
        )
        assertEquals(
            "Trigger matched, but Focus Mode needs a recent approved live test for this revision",
            result?.message,
        )
    }

    @Test
    fun successfulReceiptsForSameIdentityAllowsBackgroundExecution() {
        assertNull(
            backgroundGate(
                recipe(),
                "mac|user|22|hostKey",
                1_000L,
            ),
        )
    }

    @Test
    fun changedRecipeRevisionInvalidatesLiveReceipt() {
        val mutated = recipe().copy(
            steps = listOf(ActionSpec.ShellCommand("focus", "Focus Mode", "open -a Safari")),
        )
        val result = backgroundGate(mutated, "mac|user|22|hostKey", 1_000L)
        assertNotNull(result)
        assertEquals(
            "Trigger matched, but Focus Mode needs a recent approved live test for this revision",
            result?.message,
        )
    }

    @Test
    fun staleIdentityInvalidatesLiveReceipt() {
        val stale = recipe().copy(
            lastPreflight = recipe().lastPreflight!!.copy(macIdentity = "other|user|22|key"),
            lastLiveTest = recipe().lastLiveTest!!.copy(macIdentity = "other|user|22|key"),
        )
        assertFalse(
            backgroundGate(stale, "mac|user|22|hostKey", 1_000L) == null,
        )
    }

    @Test
    fun staleLiveReceiptIsRejected() {
        val stale = recipe().copy(
            lastLiveTest = recipe().lastLiveTest!!.copy(
                checkedAtMillis = 1_000L - 2 * 24 * 60 * 60 * 1000L,
            ),
        )
        assertNotNull(
            backgroundGate(
                stale,
                "mac|user|22|hostKey",
                48 * 60 * 60 * 1000L,
            ),
        )
    }

    @Test
    fun futureDatedProofIsRejectedAfterClockRollback() {
        val future = recipe().let { current ->
            val preflight = current.lastPreflight!!.copy(checkedAtMillis = 10_000_000L)
            current.copy(
                lastPreflight = preflight,
                lastLiveTest = recipeLiveTestReceipt(current.copy(lastPreflight = preflight)).copy(
                    checkedAtMillis = 10_000_000L,
                    completedAtMillis = 10_000_001L,
                    preflightCheckedAtMillis = 10_000_000L,
                ),
            )
        }

        assertNotNull(backgroundGate(future, "mac|user|22|hostKey", 1_000L))
    }

    @Test
    fun stalePreflightReceiptIsRejected() {
        val stale = recipe().copy(
            lastPreflight = recipePreflightReceipt(
                recipe().revisionFingerprint(),
                checkedAtMillis = 1_000L,
                permissionSnapshot = emptySet(),
            ),
            lastLiveTest = recipe().lastLiveTest!!.copy(
                checkedAtMillis = 1_000L,
                preflightCheckedAtMillis = 1_000L,
            ),
        )
        assertNotNull(
            backgroundGate(
                stale,
                "mac|user|22|hostKey",
                2_000_000L,
            ),
        )
    }

    @Test
    fun changedPermissionRequirementInvalidatesLiveReceipt() {
        val permissionRecipe = AutomationRecipe(
            id = "accessibility",
            title = "Accessibility Gate",
            description = "Run with accessibility command",
            enabled = true,
            trigger = AutomationTrigger.BatteryBelow(20),
            steps = listOf(
                ActionSpec.ShellCommand(
                    "focus",
                    "Accessibility",
                    "osascript -e 'tell app \"System Events\" to key down \"a\"'",
                    commandOrigin = CommandOrigin.Bundled,
                ),
            ),
        )
        val stale = permissionRecipe.copy(
            lastPreflight = recipePreflightReceipt(
                permissionRecipe.revisionFingerprint(),
                permissionSnapshot = setOf("local.route"),
            ),
            lastLiveTest = recipeLiveTestReceipt(
                permissionRecipe.copy(
                    lastPreflight = recipePreflightReceipt(
                        permissionRecipe.revisionFingerprint(),
                        permissionSnapshot = setOf("local.route"),
                    ),
                ),
            ),
        )
        assertNotNull(
            backgroundGate(
                stale,
                "mac|user|22|hostKey",
                1_000L,
            ),
        )
    }

    private fun recipe(): AutomationRecipe {
        val base = AutomationRecipe(
            id = "focus",
            title = "Focus Mode",
            description = "Run when Chrome is active",
            enabled = true,
            trigger = AutomationTrigger.ActiveApp("Chrome"),
            steps = listOf(
                ActionSpec.ShellCommand(
                    "focus",
                    "Focus Mode",
                    "open -a Notes",
                    commandOrigin = CommandOrigin.Bundled,
                ),
            ),
        )
        val preflight = recipePreflightReceipt(base.revisionFingerprint())
        return base.copy(
            lastPreflight = preflight,
            lastLiveTest = recipeLiveTestReceipt(base.copy(lastPreflight = preflight)),
        )
    }

    private fun recipePreflightReceipt(
        revision: String,
        checkedAtMillis: Long = 1_000L,
        permissionSnapshot: Set<String> = setOf("local.route"),
    ): AutomationPreflightReceipt = AutomationPreflightReceipt(
        recipeRevision = revision,
        checkedAtMillis = checkedAtMillis,
        macIdentity = "mac|user|22|hostKey",
        targetId = "mac.local:22:user",
        requiredCapabilities = setOf(SmartCapability.MacCommand),
        checks = listOf(
            AutomationPreflightCheck(
                area = AutomationPreflightArea.Identity,
                passed = true,
                message = "identity",
            ),
        ),
        commandTools = setOf("open"),
        commandPaths = emptySet(),
        permissionSnapshot = permissionSnapshot,
        requiredCheckCodes = setOf(AutomationCapabilityCodes.Identity),
    )

    private fun recipeLiveTestReceipt(recipe: AutomationRecipe): AutomationLiveTestReceipt {
        val preflight = requireNotNull(recipe.lastPreflight)
        val plan = AutomationExecutionPlanCompiler.compile(recipe).getOrThrow()
        return AutomationLiveTestReceipt(
            recipeRevision = recipe.revisionFingerprint(),
            checkedAtMillis = 1_000L,
            preflightCheckedAtMillis = preflight.checkedAtMillis,
            assertions = plan.assertions.mapIndexed { index, assertion ->
                AutomationLiveTestAssertion(
                    stepId = assertion.assertionId,
                    stepTitle = "Action ${index + 1}",
                    passed = true,
                    message = AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO.persistedCode,
                    assertionId = assertion.assertionId,
                    actionRevision = assertion.actionRevision,
                    outcomeCode = AutomationLiveTestOutcomeCode.EXIT_CODE_ZERO,
                    terminalStatus = AutomationStepTerminalStatus.SUCCEEDED,
                    ordinal = index,
                )
            },
            cleanup = AutomationLiveTestCleanup(
                command = "",
                passed = true,
                message = AutomationLiveTestCleanupCode.NOT_REQUIRED.persistedCode,
                outcomeCode = AutomationLiveTestCleanupCode.NOT_REQUIRED,
            ),
            macIdentity = preflight.macIdentity,
            normalizedPlanHash = plan.planHash,
            preflightReceiptId = preflight.receiptId,
            timeoutPolicyCode = AutomationLiveTestTimeoutPolicy.BOUNDED_V1.persistedCode,
            terminalStatus = AutomationLiveTestTerminalStatus.PASSED,
            completedAtMillis = 1_001L,
        )
    }

    private fun backgroundGate(
        recipe: AutomationRecipe,
        macIdentity: String,
        nowMillis: Long,
    ) = if (recipe.hasCurrentValidLiveTest(nowMillis, macIdentity, recipe.requiredPermissions())) {
        null
    } else {
        io.codecks.core.actions.ActionResult(
            recipe.id,
            recipe.title,
            ActionResultStatus.RequiresReview,
            "Trigger matched, but ${recipe.title} needs a recent approved live test for this revision",
        )
    }
}
