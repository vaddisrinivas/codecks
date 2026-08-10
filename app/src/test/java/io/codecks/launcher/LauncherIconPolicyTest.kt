package io.codecks.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconPolicyTest {
    @Test
    fun failedReplacementCanNeverAuthorizeDisablingSurvivingLauncher() {
        assertFalse(LauncherIconPolicy.mayDisableAlternatives(replacementConfirmedEnabled = false))
        assertTrue(LauncherIconPolicy.mayDisableAlternatives(replacementConfirmedEnabled = true))
    }

    @Test
    fun validPersistedSelectionWinsEvenWhenComponentNeedsRepair() {
        assertEquals(
            LauncherIcon.PointerGrid,
            LauncherIconPolicy.recoveryTarget("pointer_grid", emptySet()),
        )
    }

    @Test
    fun missingStatePreservesSingleEnabledLauncherAfterRestore() {
        assertEquals(
            LauncherIcon.RobotGrid,
            LauncherIconPolicy.recoveryTarget(null, setOf(LauncherIcon.RobotGrid)),
        )
    }

    @Test
    fun corruptOrAmbiguousStateFallsBackToRobotFace() {
        assertEquals(
            LauncherIcon.RobotFace,
            LauncherIconPolicy.recoveryTarget("corrupt", emptySet()),
        )
        assertEquals(
            LauncherIcon.RobotFace,
            LauncherIconPolicy.recoveryTarget(
                null,
                setOf(LauncherIcon.PointerGrid, LauncherIcon.MinimalGreen),
            ),
        )
    }

    @Test
    fun legacyNamesMigrateWithoutChangingIdentity() {
        assertEquals(LauncherIcon.RobotFace, LauncherIconPolicy.migratePersisted("robot"))
        assertEquals(LauncherIcon.PointerGrid, LauncherIconPolicy.migratePersisted("PointerGrid"))
        assertEquals(LauncherIcon.MinimalGreen, LauncherIconPolicy.migratePersisted("green"))
    }
}
