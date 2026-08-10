package io.codecks.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconManagerFailureTest {
    @Test
    fun currentAndStartupReconcileNeverThrowOrMutateOnBackendReadFailure() {
        val backend = FakeBackend(
            enabled = setOf(LauncherIcon.PointerGrid),
            failReads = setOf(LauncherIcon.RobotFace),
        )
        val manager = LauncherIconManager(backend, FakeStore())

        assertTrue(runCatching { manager.current() }.isSuccess)
        assertTrue(runCatching { manager.reconcile() }.isSuccess)
        assertTrue(backend.mutations.isEmpty())
        assertTrue(LauncherIcon.PointerGrid in backend.enabled)
    }

    @Test
    fun targetAndDefaultEnableFailuresNeverDisableSurvivingLauncher() {
        val backend = FakeBackend(
            enabled = setOf(LauncherIcon.PointerGrid),
            failSets = setOf(LauncherIcon.RobotGrid, LauncherIcon.RobotFace),
        )
        val manager = LauncherIconManager(backend, FakeStore("pointer_grid"))

        assertTrue(manager.select(LauncherIcon.RobotGrid).isFailure)
        assertTrue(LauncherIcon.PointerGrid in backend.enabled)
        assertFalse(backend.mutations.contains(LauncherIcon.PointerGrid to false))
    }

    @Test
    fun recoveryReadFailureDoesNotDisableUnknownSurvivor() {
        val backend = FakeBackend(
            enabled = setOf(LauncherIcon.MinimalGreen),
            failReads = setOf(LauncherIcon.RobotFace),
            failSets = setOf(LauncherIcon.RobotGrid),
        )
        val manager = LauncherIconManager(backend, FakeStore("minimal_green"))

        assertTrue(manager.select(LauncherIcon.RobotGrid).isFailure)
        assertTrue(LauncherIcon.MinimalGreen in backend.enabled)
        assertTrue(backend.mutations.none { (_, enabled) -> !enabled })
    }

    @Test
    fun preferenceBackendFailuresRemainNonThrowingAndLeaveAConfirmedLauncher() {
        val backend = FakeBackend(enabled = setOf(LauncherIcon.PointerGrid))
        val store = FakeStore(failReads = true, failWrites = true)
        val manager = LauncherIconManager(backend, store)

        assertTrue(runCatching { manager.current() }.isSuccess)
        assertTrue(runCatching { manager.reconcile() }.isSuccess)
        assertTrue(backend.enabled.isNotEmpty())
    }

    @Test
    fun widgetRefreshFailureCannotInvalidateSuccessfulSelection() {
        val backend = FakeBackend(enabled = setOf(LauncherIcon.RobotFace))
        val manager = LauncherIconManager(backend, FakeStore()) { error("widget backend failed") }

        assertEquals(LauncherIcon.PointerGrid, manager.select(LauncherIcon.PointerGrid).getOrThrow())
        assertEquals(setOf(LauncherIcon.PointerGrid), backend.enabled)
    }

    private class FakeBackend(
        enabled: Set<LauncherIcon>,
        private val failReads: Set<LauncherIcon> = emptySet(),
        private val failSets: Set<LauncherIcon> = emptySet(),
    ) : LauncherComponentBackend {
        val enabled = enabled.toMutableSet()
        val mutations = mutableListOf<Pair<LauncherIcon, Boolean>>()

        override fun isEnabled(icon: LauncherIcon): Boolean {
            if (icon in failReads) error("read failed for $icon")
            return icon in enabled
        }

        override fun setEnabled(icon: LauncherIcon, enabled: Boolean) {
            mutations += icon to enabled
            if (icon in failSets) error("write failed for $icon")
            if (enabled) this.enabled += icon else this.enabled -= icon
        }
    }

    private class FakeStore(
        private var value: String? = null,
        private val failReads: Boolean = false,
        private val failWrites: Boolean = false,
    ) : LauncherSelectionStore {
        override fun read(): String? {
            if (failReads) error("preference read failed")
            return value
        }

        override fun write(value: String): Boolean {
            if (failWrites) error("preference write failed")
            this.value = value
            return true
        }
    }
}
