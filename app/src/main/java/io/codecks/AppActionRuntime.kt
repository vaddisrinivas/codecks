package io.codecks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import io.codecks.domain.ActionKind
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.LocalActionResult
import io.codecks.ui.app.LocalActionDispatcher
import io.codecks.ui.automations.AutomationsUiState
import io.codecks.ui.automations.AutomationsViewModel
import io.codecks.ui.home.HomeActionDispatchResult
import io.codecks.ui.home.HomeStatusFeedback
import io.codecks.ui.home.HomeUiState
import io.codecks.ui.home.HomeViewModel
import io.codecks.ui.home.homeStatusFeedback
import io.codecks.ui.home.smart.SmartDeckEffect
import io.codecks.ui.home.smart.SmartDeckViewModel
import io.codecks.ui.home.smart.SmartRunId
import io.codecks.navigation.AutomationsRoute
import io.codecks.navigation.ClipboardRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.KeyboardRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.SettingsRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class AppActionContext(
    val route: NavKey,
    val macCommandsReady: Boolean,
    val macInputConnected: Boolean,
    val reactiveTrackpadEnabled: Boolean,
    val homeState: HomeUiState,
    val automationsState: AutomationsUiState,
    val hidRepository: HidRepository,
    val homeViewModel: HomeViewModel,
    val automationsViewModel: AutomationsViewModel,
    val smartDeckViewModel: SmartDeckViewModel?,
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val navigate: (NavKey, Boolean) -> Unit,
)

internal data class AppActionRuntime(
    val execute: (DeckAction, Boolean) -> LocalActionResult?,
    val celebrationLabel: String?,
    val clearCelebration: () -> Unit,
)

@Composable
internal fun rememberAppActionRuntime(context: AppActionContext): AppActionRuntime {
    var pendingDangerousAction by remember { mutableStateOf<DeckAction?>(null) }
    var acceptedSmartHomeRunId by remember { mutableStateOf<SmartRunId?>(null) }
    var celebrationLabel by remember { mutableStateOf<String?>(null) }
    val currentNavigate by rememberUpdatedState(context.navigate)
    val currentMacInputConnected by rememberUpdatedState(context.macInputConnected)
    val currentMacCommandsReady by rememberUpdatedState(context.macCommandsReady)
    val localActionDispatcher = remember(context.hidRepository, context.scope, context.snackbarHostState) {
        LocalActionDispatcher(
            onTrackpad = { currentNavigate(MouseRoute, true) },
            onKeyboard = { currentNavigate(KeyboardRoute, false) },
            onAutomations = { currentNavigate(AutomationsRoute, false) },
            onClipboard = { currentNavigate(ClipboardRoute, false) },
            onSettings = { currentNavigate(SettingsRoute, false) },
            onEditor = { currentNavigate(HomeRoute, true) },
            onCelebration = { celebrationLabel = it },
            onMissingMacInput = {
                context.scope.launch { context.snackbarHostState.showSnackbar("Connect Mac input first") }
            },
            onSendMediaPlayPause = { context.hidRepository.send(HidCommand.MediaPlayPause) },
            onSendMediaNext = { context.hidRepository.send(HidCommand.MediaNext) },
            onSendMediaPrevious = { context.hidRepository.send(HidCommand.MediaPrevious) },
            onUnsupported = {},
            supportsMacInput = { currentMacInputConnected },
        )
    }
    val execute: (DeckAction, Boolean) -> LocalActionResult? = { action, allowDangerous ->
        when {
            action.kind == ActionKind.Local -> localActionDispatcher.handleAction(action)
            !context.macCommandsReady -> {
                context.scope.launch {
                    context.snackbarHostState.showSnackbar("Test the Mac connection before running this action")
                }
                LocalActionResult.Failed("Mac controls are not verified")
            }
            action.dangerous && !allowDangerous -> {
                pendingDangerousAction = action
                null
            }
            else -> {
                context.homeViewModel.run(action, allowDangerous = allowDangerous)
                null
            }
        }
    }

    LaunchedEffect(context.macCommandsReady) {
        if (context.macCommandsReady) context.automationsViewModel.startTriggerMonitor()
    }
    LaunchedEffect(context.route, context.macCommandsReady, context.homeState.dynamicDeckEnabled) {
        if (context.route == HomeRoute && context.macCommandsReady && context.homeState.dynamicDeckEnabled) {
            while (true) {
                context.homeViewModel.refreshActiveMacApp()
                delay(10_000)
            }
        }
    }
    LaunchedEffect(context.route, context.macCommandsReady, context.reactiveTrackpadEnabled) {
        if (context.route == MouseRoute && context.macCommandsReady && context.reactiveTrackpadEnabled) {
            context.homeViewModel.refreshActiveMacApp()
            while (true) {
                delay(10_000)
                context.homeViewModel.refreshActiveMacApp()
            }
        }
    }
    LaunchedEffect(Unit) {
        context.smartDeckViewModel?.effects?.collect { effect ->
            when (effect) {
                is SmartDeckEffect.Execute -> {
                    val request = effect.request
                    if (request.suggestion.action.kind == ActionKind.Local) {
                        context.smartDeckViewModel.onExecutionAccepted(request.id)
                        val result = localActionDispatcher.handleAction(request.suggestion.action)
                            ?: LocalActionResult.Failed("Unsupported local action")
                        context.smartDeckViewModel.onLocalSuggestionResult(request.id, result)
                    } else if (!currentMacCommandsReady) {
                        context.smartDeckViewModel.onExecutionRejected(request.id)
                        context.scope.launch {
                            context.snackbarHostState.showSnackbar(
                                "Test the Mac connection before running this suggestion",
                            )
                        }
                    } else when (
                        context.homeViewModel.run(
                            request.suggestion.action,
                            allowDangerous = request.allowDangerous,
                        )
                    ) {
                        HomeActionDispatchResult.Accepted -> {
                            context.smartDeckViewModel.onExecutionAccepted(request.id)
                            acceptedSmartHomeRunId = request.id
                        }
                        HomeActionDispatchResult.Busy,
                        is HomeActionDispatchResult.Rejected,
                        -> context.smartDeckViewModel.onExecutionRejected(request.id)
                    }
                }
                is SmartDeckEffect.Pin -> context.homeViewModel.pinAction(effect.suggestion.action)
                is SmartDeckEffect.ShowExplanation -> context.scope.launch {
                    context.snackbarHostState.showSnackbar("${effect.confidence}: ${effect.reason}")
                }
                is SmartDeckEffect.ConfirmDangerousSuggestion -> pendingDangerousAction = effect.suggestion.action
            }
        }
    }
    LaunchedEffect(context.homeState.actionStatus) {
        when (context.homeState.actionStatus) {
            is ActionStatus.Succeeded -> acceptedSmartHomeRunId?.let { runId ->
                context.smartDeckViewModel?.onExecutionCompleted(runId, succeeded = true)
                acceptedSmartHomeRunId = null
            }
            is ActionStatus.Failed -> acceptedSmartHomeRunId?.let { runId ->
                context.smartDeckViewModel?.onExecutionCompleted(runId, succeeded = false)
                acceptedSmartHomeRunId = null
            }
            else -> Unit
        }
        when (val feedback = homeStatusFeedback(context.homeState.actionStatus)) {
            HomeStatusFeedback.None -> Unit
            is HomeStatusFeedback.TileOnly -> {
                delay(feedback.lingerMillis)
                context.homeViewModel.consumeResult()
            }
            is HomeStatusFeedback.Snackbar -> {
                val result = context.snackbarHostState.showSnackbar(feedback.message, feedback.actionLabel)
                if (result == SnackbarResult.ActionPerformed) {
                    if (feedback.actionLabel == "Undo") context.homeViewModel.undoLastDeckEdit()
                    else context.navigate(SettingsRoute, false)
                }
                context.homeViewModel.consumeResult()
            }
        }
    }
    LaunchedEffect(context.automationsState.message) {
        val message = context.automationsState.message ?: return@LaunchedEffect
        val undoable = context.automationsState.pendingUndo != null && message.startsWith("Deleted ")
        val result = context.snackbarHostState.showSnackbar(message, if (undoable) "Undo" else null)
        if (result == SnackbarResult.ActionPerformed && undoable) context.automationsViewModel.undoDelete()
        else context.automationsViewModel.consumeMessage()
    }

    pendingDangerousAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                pendingDangerousAction = null
                context.smartDeckViewModel?.cancelDangerousSuggestion()
            },
            title = { Text(action.label) },
            text = { Text(action.description) },
            confirmButton = {
                TextButton(onClick = {
                    val smart = context.smartDeckViewModel?.pendingDangerousSuggestion?.value != null
                    pendingDangerousAction = null
                    if (smart) context.smartDeckViewModel.confirmDangerousSuggestion()
                    else execute(action, true)
                }) { Text("Run") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDangerousAction = null
                    context.smartDeckViewModel?.cancelDangerousSuggestion()
                }) { Text("Cancel") }
            },
        )
    }
    return AppActionRuntime(execute, celebrationLabel) { celebrationLabel = null }
}
