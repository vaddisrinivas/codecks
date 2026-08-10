package io.codecks.spikes

import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.DefaultState
import ru.nsk.kstatemachine.state.FinalState
import ru.nsk.kstatemachine.state.addFinalState
import ru.nsk.kstatemachine.state.addInitialState
import ru.nsk.kstatemachine.state.addState
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStateMachine
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope

sealed interface ExecutionEvent : Event {
    data object Validate : ExecutionEvent
    data object Begin : ExecutionEvent
    data object Succeed : ExecutionEvent
    data object Fail : ExecutionEvent
    data object Cancel : ExecutionEvent
}

@Serializable
enum class StableExecutionState { DRAFT, READY, RUNNING, SUCCEEDED, FAILED, CANCELLED }

sealed class ExecutionState(val stable: StableExecutionState) : DefaultState() {
    data object Draft : ExecutionState(StableExecutionState.DRAFT)
    data object Ready : ExecutionState(StableExecutionState.READY)
    data object Running : ExecutionState(StableExecutionState.RUNNING)
    data object Succeeded : ExecutionState(StableExecutionState.SUCCEEDED), FinalState
    data object Failed : ExecutionState(StableExecutionState.FAILED), FinalState
    data object Cancelled : ExecutionState(StableExecutionState.CANCELLED), FinalState
}

suspend fun createExecutionMachine(scope: CoroutineScope): StateMachine = createStateMachine(scope = scope) {
    addInitialState(ExecutionState.Draft) {
        transition<ExecutionEvent.Validate>(targetState = ExecutionState.Ready)
        transition<ExecutionEvent.Cancel>(targetState = ExecutionState.Cancelled)
    }
    addState(ExecutionState.Ready) {
        transition<ExecutionEvent.Begin>(targetState = ExecutionState.Running)
        transition<ExecutionEvent.Cancel>(targetState = ExecutionState.Cancelled)
    }
    addState(ExecutionState.Running) {
        transition<ExecutionEvent.Succeed>(targetState = ExecutionState.Succeeded)
        transition<ExecutionEvent.Fail>(targetState = ExecutionState.Failed)
        transition<ExecutionEvent.Cancel>(targetState = ExecutionState.Cancelled)
    }
    addFinalState(ExecutionState.Succeeded)
    addFinalState(ExecutionState.Failed)
    addFinalState(ExecutionState.Cancelled)
}
