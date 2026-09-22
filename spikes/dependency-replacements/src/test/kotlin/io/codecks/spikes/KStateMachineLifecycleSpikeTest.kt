package io.codecks.spikes

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nsk.kstatemachine.statemachine.destroy

class KStateMachineLifecycleSpikeTest {
    @Test
    fun exhaustiveAcceptedTransitionTable() = runTest {
        val paths = listOf(
            listOf(ExecutionEvent.Validate, ExecutionEvent.Begin, ExecutionEvent.Succeed) to StableExecutionState.SUCCEEDED,
            listOf(ExecutionEvent.Validate, ExecutionEvent.Begin, ExecutionEvent.Fail) to StableExecutionState.FAILED,
            listOf(ExecutionEvent.Cancel) to StableExecutionState.CANCELLED,
            listOf(ExecutionEvent.Validate, ExecutionEvent.Cancel) to StableExecutionState.CANCELLED,
            listOf(ExecutionEvent.Validate, ExecutionEvent.Begin, ExecutionEvent.Cancel) to StableExecutionState.CANCELLED,
        )
        paths.forEach { (events, expected) ->
            val machine = createExecutionMachine(this)
            events.forEach { machine.processEvent(it) }
            val active = listOf(
                ExecutionState.Draft,
                ExecutionState.Ready,
                ExecutionState.Running,
                ExecutionState.Succeeded,
                ExecutionState.Failed,
                ExecutionState.Cancelled,
            ).single { it.isActive }
            assertEquals(expected, active.stable)
            machine.destroy()
        }
    }

    @Test
    fun stableStateIsSerializableWithoutSideEffects() {
        StableExecutionState.entries.forEach { state ->
            assertEquals(state, Json.decodeFromString<StableExecutionState>(Json.encodeToString(state)))
        }
    }
}
