package io.codecks.domain.routines

import io.codecks.domain.DeckAction
import io.codecks.domain.deck.DeckLayout
import io.codecks.domain.deck.DeckSlot
import java.util.Collections

private val SAFE_ID = Regex("[a-z][a-z0-9_.-]{2,63}")

@JvmInline
value class RoutineId(val value: String) : Comparable<RoutineId> {
    init { require(value.matches(SAFE_ID)) }
    override fun compareTo(other: RoutineId): Int = value.compareTo(other.value)
}

enum class RoutineCategory { DEVELOPER, PRESENTATION, MEETING, MEDIA, FOCUS, BROWSER, FINDER, ACCESSIBILITY, SAFETY }

class RoutineDefinition(
    val id: RoutineId,
    val title: String,
    val summary: String,
    val category: RoutineCategory,
    actionIds: List<String>,
) {
    val actionIds: List<String> = Collections.unmodifiableList(actionIds.toList())

    init {
        require(title.isNotBlank() && summary.isNotBlank())
        require(this.actionIds.isNotEmpty())
        require(this.actionIds.size <= MAX_ACTIONS)
        require(this.actionIds.all { it.matches(SAFE_ID) })
        require(this.actionIds.distinct().size == this.actionIds.size)
    }

    private companion object { const val MAX_ACTIONS = 24 }
}

class RoutineBank(routines: List<RoutineDefinition>) {
    val routines = routines.sortedBy { it.id }
    private val byId = this.routines.associateBy { it.id }

    init { require(this.routines.map { it.id }.distinct().size == this.routines.size) }

    fun search(query: String, category: RoutineCategory? = null): List<RoutineDefinition> {
        val needle = query.trim().lowercase().take(80)
        return routines.filter {
            (category == null || it.category == category) &&
                (needle.isBlank() || listOf(it.id.value, it.title, it.summary, it.category.name)
                    .any { token -> needle in token.lowercase() })
        }
    }

    fun get(id: RoutineId): RoutineDefinition? = byId[id]
}

enum class RoutineConflictChoice { KEEP_CURRENT, REPLACE }

data class RoutineInstallPreview(
    val routineId: RoutineId,
    val resolvedActions: List<DeckAction>,
    val missingActionIds: List<String>,
    val occupiedSlots: List<Int>,
) {
    val ready: Boolean get() = missingActionIds.isEmpty()
}

sealed interface RoutineInstallResult {
    data class Installed(val layout: DeckLayout, val rollback: RoutineRollback) : RoutineInstallResult
    data class Rejected(val reason: String) : RoutineInstallResult
}

class RoutineRollback internal constructor(
    val routineId: RoutineId,
    internal val before: DeckLayout,
    internal val installed: DeckLayout,
)

class RoutineInstallEngine(private val actionCatalog: Map<String, DeckAction>) {
    fun preview(routine: RoutineDefinition, current: DeckLayout): RoutineInstallPreview {
        val resolved = routine.actionIds.mapNotNull(actionCatalog::get)
        return RoutineInstallPreview(
            routineId = routine.id,
            resolvedActions = resolved,
            missingActionIds = routine.actionIds.filterNot(actionCatalog::containsKey),
            occupiedSlots = current.slots.indices.take(resolved.size),
        )
    }

    fun install(
        preview: RoutineInstallPreview,
        current: DeckLayout,
        choice: RoutineConflictChoice,
    ): RoutineInstallResult {
        if (!preview.ready) return RoutineInstallResult.Rejected("Routine references unavailable actions")
        if (preview.resolvedActions.isEmpty()) return RoutineInstallResult.Rejected("Routine is empty")
        val occupied = current.slots.isNotEmpty()
        if (occupied && choice == RoutineConflictChoice.KEEP_CURRENT) {
            return RoutineInstallResult.Rejected("Existing Deck kept")
        }
        val installed = DeckLayout(
            columns = current.columns,
            slots = preview.resolvedActions.mapIndexed { index, action ->
                DeckSlot("routine-${preview.routineId.value}-$index", action)
            },
        ).normalized()
        return RoutineInstallResult.Installed(
            installed,
            RoutineRollback(preview.routineId, current, installed),
        )
    }

    fun rollback(current: DeckLayout, rollback: RoutineRollback): RoutineInstallResult =
        if (current != rollback.installed) RoutineInstallResult.Rejected("Deck changed after install")
        else RoutineInstallResult.Installed(
            rollback.before,
            RoutineRollback(rollback.routineId, rollback.installed, rollback.before),
        )
}
