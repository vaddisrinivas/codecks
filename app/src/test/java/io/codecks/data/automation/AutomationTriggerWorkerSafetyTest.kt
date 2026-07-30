package io.codecks.data.automation

import io.codecks.core.actions.ActionResultStatus
import io.codecks.core.actions.ActionSpec
import io.codecks.domain.automation.AutomationLiveTestAssertion
import io.codecks.domain.automation.AutomationLiveTestCleanup
import io.codecks.domain.automation.AutomationLiveTestReceipt
import io.codecks.domain.automation.AutomationPreflightArea
import io.codecks.domain.automation.AutomationPreflightCheck
import io.codecks.domain.automation.AutomationPreflightReceipt
import io.codecks.domain.automation.AutomationRecipe
import io.codecks.domain.automation.AutomationTrigger
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
        val result = automaticRunBlocker(
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
            automaticRunBlocker(
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
        val result = automaticRunBlocker(mutated, "mac|user|22|hostKey", 1_000L)
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
            automaticRunBlocker(stale, "mac|user|22|hostKey", 1_000L) == null,
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
            automaticRunBlocker(
                stale,
                "mac|user|22|hostKey",
                48 * 60 * 60 * 1000L,
            ),
        )
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
            automaticRunBlocker(
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
            steps = listOf(ActionSpec.ShellCommand("focus", "Accessibility", "osascript -e 'tell app \"System Events\" to key down \"a\"'")),
        )
        val stale = permissionRecipe.copy(
            lastPreflight = recipePreflightReceipt(
                permissionRecipe.revisionFingerprint(),
                permissionSnapshot = setOf("local.route"),
            ),
            lastLiveTest = recipeLiveTestReceipt(permissionRecipe.revisionFingerprint()),
        )
        assertNotNull(
            automaticRunBlocker(
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
            steps = listOf(ActionSpec.ShellCommand("focus", "Focus Mode", "open -a Notes")),
        )
        return base.copy(
            lastPreflight = recipePreflightReceipt(base.revisionFingerprint()),
            lastLiveTest = recipeLiveTestReceipt(base.revisionFingerprint()),
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
    )

    private fun recipeLiveTestReceipt(revision: String): AutomationLiveTestReceipt = AutomationLiveTestReceipt(
        recipeRevision = revision,
        checkedAtMillis = 1_000L,
        preflightCheckedAtMillis = 1_000L,
        assertions = listOf(
            AutomationLiveTestAssertion(
                stepId = "focus",
                stepTitle = "Focus Mode",
                passed = true,
                message = "ok",
            ),
        ),
        cleanup = AutomationLiveTestCleanup(
            command = "echo ok",
            passed = true,
            message = "cleanup ok",
        ),
        macIdentity = "mac|user|22|hostKey",
    )
}
