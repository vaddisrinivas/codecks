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

@Composable
fun SupportBundleDialog(
    state: SupportBundleUiState,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state is SupportBundleUiState.Idle || state is SupportBundleUiState.Ready) return
    val failureFocusRequester = remember { FocusRequester() }
    val failureKey = (state as? SupportBundleUiState.Failure)?.kind?.name
    LaunchedEffect(failureKey) {
        if (failureKey != null) failureFocusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onCancel,
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
                    SupportBundleUiState.Idle,
                    is SupportBundleUiState.Ready,
                    -> Unit
                }
            }
        },
        confirmButton = {
            AccessibleAction(
                label = if (state is SupportBundleUiState.Failure) "Try again" else "Generate",
                onClick = onGenerate,
                enabled = state !is SupportBundleUiState.Generating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state is SupportBundleUiState.Failure) "Try again" else "Generate")
            }
        },
        dismissButton = {
            AccessibleAction(
                label = "Cancel support bundle",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        },
    )
}
