package io.codecks.domain.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockCodexCockpitTest {
    @Test
    fun `mock cockpit contains the planned task states`() {
        val snapshot = MockCodexCockpit.snapshot()

        assertTrue(snapshot.tasks.any { it.state == CodexTaskState.Running })
        assertTrue(snapshot.tasks.any { it.state == CodexTaskState.Released })
        assertTrue(snapshot.tasks.any { it.state == CodexTaskState.Blocked })
        assertTrue(snapshot.tasks.any { it.state == CodexTaskState.Queued })
        assertEquals(snapshot.tasks.count { it.state == CodexTaskState.Running }, snapshot.runningCount)
        assertEquals(snapshot.tasks.count { it.state == CodexTaskState.Queued }, snapshot.queueCount)
    }

    @Test
    fun `mock fancy buttons include empty emoji release voice and guarded command buttons`() {
        val buttons = MockCodexCockpit.snapshot().buttons

        assertTrue(buttons.any { it.type == FancyButtonType.Empty })
        assertTrue(buttons.any { it.type == FancyButtonType.Emoji && it.effect.kind == DeckEffectKind.ConfettiBurst })
        assertTrue(buttons.any { it.type == FancyButtonType.Release })
        assertTrue(buttons.any { it.type == FancyButtonType.Voice })
        assertTrue(buttons.any { it.safetyLevel == FancyButtonSafety.Dangerous })
    }

    @Test
    fun `theme presets cover the initial fancy worlds`() {
        val themeIds = MockCodexCockpit.themePresets().map { it.id }.toSet()

        assertTrue("terminal-neon" in themeIds)
        assertTrue("arcade-glass" in themeIds)
        assertTrue("studio-console" in themeIds)
        assertTrue("emoji-carnival" in themeIds)
        assertTrue("focus-minimal" in themeIds)
        assertTrue("aurora-pixel" in themeIds)
    }
}

