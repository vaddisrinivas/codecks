package io.codecks.domain.routines

import io.codecks.data.routines.BundledRoutineBank
import io.codecks.domain.ActionIcon
import io.codecks.domain.ActionKind
import io.codecks.domain.DeckAction
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineBankTest {
    private val bank = BundledRoutineBank.bank
    private val actionIds = bank.routines.flatMap { it.actionIds }.distinct()
    private val actions = actionIds.associateWith { DeckAction(it, it, ActionKind.Local, ActionIcon.Apps) }
    private val engine = RoutineInstallEngine(actions)

    @Test
    fun offlineBankCoversEveryRequiredCategoryWithStableDeterministicIds() {
        assertEquals(RoutineCategory.entries.toSet(), bank.routines.map { it.category }.toSet())
        assertEquals(bank.routines.sortedBy { it.id }.map { it.id }, bank.routines.map { it.id })
        assertTrue(bank.search("developer").any { it.category == RoutineCategory.DEVELOPER })
        assertTrue(bank.routines.all { it.actionIds.isNotEmpty() })
    }

    @Test
    fun previewDoesNotMutateAndInstallRequiresExplicitConflictChoice() {
        val before = DeckLayout(slots = listOf(DeckSlot("existing", actions.getValue(actionIds.first()))))
        val routine = bank.get(RoutineId("routine.media"))!!
        val preview = engine.preview(routine, before)

        assertTrue(preview.ready)
        assertEquals(1, before.slots.size)
        assertTrue(engine.install(preview, before, RoutineConflictChoice.KEEP_CURRENT) is RoutineInstallResult.Rejected)

        val installed = engine.install(preview, before, RoutineConflictChoice.REPLACE) as RoutineInstallResult.Installed
        assertEquals(routine.actionIds, installed.layout.actions.map { it.id })
        val restored = engine.rollback(installed.layout, installed.rollback) as RoutineInstallResult.Installed
        assertEquals(before, restored.layout)
    }

    @Test
    fun missingActionsAndStaleRollbackFailClosed() {
        val routine = bank.get(RoutineId("routine.safety"))!!
        val incomplete = RoutineInstallEngine(emptyMap()).preview(routine, DeckLayout.Empty)
        assertFalse(incomplete.ready)
        assertTrue(RoutineInstallEngine(emptyMap()).install(incomplete, DeckLayout.Empty, RoutineConflictChoice.REPLACE) is RoutineInstallResult.Rejected)

        val preview = engine.preview(routine, DeckLayout.Empty)
        val installed = engine.install(preview, DeckLayout.Empty, RoutineConflictChoice.REPLACE) as RoutineInstallResult.Installed
        val externallyChanged = installed.layout.copy(columns = 3)
        assertTrue(engine.rollback(externallyChanged, installed.rollback) is RoutineInstallResult.Rejected)
    }
}
