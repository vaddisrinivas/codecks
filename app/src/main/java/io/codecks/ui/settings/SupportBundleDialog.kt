package io.codecks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.unit.dp
import io.codecks.ui.app.AccessibleAction
import io.codecks.ui.app.AccessibleStatus
import io.codecks.ui.app.AccessibleStatusKind

internal enum class SupportBundleConfirmAction {
    Generate,
    RetryShare,
    RetryDelete,
}

internal fun supportBundleConfirmAction(state: SupportBundleUiState): SupportBundleConfirmAction =
    when (state) {
        is SupportBundleUiState.ChooserOpened,
        is SupportBundleUiState.PendingRetained,
        -> SupportBundleConfirmAction.RetryShare
        is SupportBundleUiState.Failure -> when (state.kind) {
            SupportBundleFailure.SHARE_PICKER_UNAVAILABLE -> SupportBundleConfirmAction.RetryShare
            SupportBundleFailure.DELETE_FAILED -> SupportBundleConfirmAction.RetryDelete
            SupportBundleFailure.BUILD_FAILED,
            SupportBundleFailure.CACHE_WRITE_FAILED,
            -> SupportBundleConfirmAction.Generate
        }
        else -> SupportBundleConfirmAction.Generate
    }

internal fun supportBundleDismissRetainsPending(state: SupportBundleUiState): Boolean =
    state is SupportBundleUiState.ChooserOpened ||
        (state is SupportBundleUiState.Failure &&
            state.kind in setOf(
                SupportBundleFailure.SHARE_PICKER_UNAVAILABLE,
                SupportBundleFailure.DELETE_FAILED,
            ))

@Composable
fun SupportBundleDialog(
    state: SupportBundleUiState,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onRetryShare: () -> Unit = {},
    onDeletePending: () -> Unit = {},
    onCloseRetaining: () -> Unit = {},
) {
    if (
        state is SupportBundleUiState.Idle ||
        state is SupportBundleUiState.Ready ||
        state is SupportBundleUiState.PendingRetained
    ) return
    val failureFocusRequester = remember { FocusRequester() }
    val failureKey = (state as? SupportBundleUiState.Failure)?.kind?.name
    LaunchedEffect(failureKey) {
        if (failureKey != null) failureFocusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = {
            if (supportBundleDismissRetainsPending(state)) onCloseRetaining() else onCancel()
        },
        title = { Text("Support bundle") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (state) {
                    is SupportBundleUiState.Preview -> {
                        AccessibleStatus(
                            stateDescription = "Preview ready",
                            detail = "No file exists until you choose Generate.",
                            kind = AccessibleStatusKind.Information,
                        )
                        Text("Included:")
                        state.includedSections.forEach { Text("• $it") }
                        Text(state.prohibitedDataStatement)
                        Text("Codecks opens Android’s share picker. It never uploads this bundle.")
                    }
                    SupportBundleUiState.Generating -> AccessibleStatus(
                        stateDescription = "Generating support bundle",
                        kind = AccessibleStatusKind.Busy,
                        announceChanges = true,
                    )
                    is SupportBundleUiState.Failure -> AccessibleStatus(
                        stateDescription = "Support bundle failed",
                        detail = state.kind.name.lowercase().replace('_', ' '),
                        kind = AccessibleStatusKind.Error,
                        announceChanges = true,
                        modifier = Modifier
                            .focusRequester(failureFocusRequester)
                            .focusable(),
                    )
                    is SupportBundleUiState.ChooserOpened -> {
                        AccessibleStatus(
                            stateDescription = if (state.targetChosen) {
                                "Share target chosen"
                            } else {
                                "Share picker opened"
                            },
                            detail = "Codecks cannot verify delivery. The pending bundle is retained until you retry or delete it.",
                            kind = AccessibleStatusKind.Information,
                            announceChanges = true,
                        )
                    }
                    is SupportBundleUiState.PendingRetained -> Unit
                    SupportBundleUiState.Idle,
                    is SupportBundleUiState.Ready,
                    -> Unit
                }
            }
        },
        confirmButton = {
            val action = supportBundleConfirmAction(state)
            AccessibleAction(
                label = when (action) {
                    SupportBundleConfirmAction.Generate -> "Generate support bundle"
                    SupportBundleConfirmAction.RetryShare -> "Open share picker again"
                    SupportBundleConfirmAction.RetryDelete -> "Retry deleting support bundle"
                },
                onClick = when (action) {
                    SupportBundleConfirmAction.Generate -> onGenerate
                    SupportBundleConfirmAction.RetryShare -> onRetryShare
                    SupportBundleConfirmAction.RetryDelete -> onDeletePending
                },
                enabled = state !is SupportBundleUiState.Generating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                    Text(
                        when (action) {
                            SupportBundleConfirmAction.Generate -> "Generate"
                            SupportBundleConfirmAction.RetryShare -> "Share again"
                            SupportBundleConfirmAction.RetryDelete -> "Retry delete"
                        },
                    )
            }
        },
        dismissButton = {
            val chooser = state is SupportBundleUiState.ChooserOpened
            val retainFailure = state is SupportBundleUiState.Failure &&
                supportBundleDismissRetainsPending(state)
            AccessibleAction(
                label = when {
                    chooser -> "Delete pending support bundle"
                    retainFailure -> "Close and retain pending support bundle"
                    else -> "Cancel support bundle"
                },
                onClick = when {
                    chooser -> onDeletePending
                    retainFailure -> onCloseRetaining
                    else -> onCancel
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        chooser -> "Delete"
                        retainFailure -> "Close"
                        else -> "Cancel"
                    },
                )
            }
        },
    )
}

internal fun supportBundleDismissDeletesPending(state: SupportBundleUiState): Boolean =
    !supportBundleDismissRetainsPending(state) &&
        state !is SupportBundleUiState.PendingRetained
