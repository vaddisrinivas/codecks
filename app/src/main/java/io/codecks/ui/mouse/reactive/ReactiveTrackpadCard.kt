package io.codecks.ui.mouse.reactive

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.codecks.domain.reactive.MacStateRepository
import io.codecks.domain.reactive.ReactiveActionExecutor
import io.codecks.domain.reactive.ReactiveActionResult
import io.codecks.domain.reactive.ReactiveEngine
import io.codecks.domain.reactive.ReactiveRisk

@Composable
fun ReactiveTrackpadCard(
    enabled: Boolean,
    viewModel: ReactiveTrackpadViewModel,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val presentation = uiState.present()
    var overflowOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Reactive Trackpad", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = uiState.connectionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = viewModel::refreshBasic) {
                    Text(if (uiState.loading) "Refreshing…" else "Refresh")
                }
            }

            Text(
                text = presentation.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (presentation.visibleControls.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    presentation.visibleControls.forEach { control ->
                        AssistChip(
                            onClick = { viewModel.runControl(control.id) },
                            label = { Text(control.title) },
                            colors = when (control.risk) {
                                ReactiveRisk.Safe -> AssistChipDefaults.assistChipColors()
                                ReactiveRisk.Review,
                                ReactiveRisk.Dangerous,
                                ReactiveRisk.Private,
                                -> AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                        )
                    }
                    if (presentation.hasOverflow) {
                        AssistChip(
                            onClick = { overflowOpen = true },
                            label = { Text("More (${uiState.overflowCount()})") },
                        )
                    }
                }
            }

            presentation.resultMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    uiState.pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingConfirmation,
            title = { Text(pending.title) },
            text = { Text(pending.body) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingConfirmation) {
                    Text("Cancel")
                }
            },
        )
    }

    if (overflowOpen) {
        AlertDialog(
            onDismissRequest = { overflowOpen = false },
            title = { Text("More controls") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presentation.overflowControls.forEach { control ->
                        AssistChip(
                            onClick = {
                                overflowOpen = false
                                viewModel.runControl(control.id)
                            },
                            label = { Text(control.title) },
                            colors = when (control.risk) {
                                ReactiveRisk.Safe -> AssistChipDefaults.assistChipColors()
                                ReactiveRisk.Review,
                                ReactiveRisk.Dangerous,
                                ReactiveRisk.Private,
                                -> AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overflowOpen = false }) {
                    Text("Close")
                }
            },
        )
    }
}

fun reactiveTrackpadViewModelFactory(
    macStateRepository: MacStateRepository,
    engine: ReactiveEngine,
    executor: ReactiveActionExecutor,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ReactiveTrackpadViewModel(
            macStateRepository = macStateRepository,
            engine = engine,
            executor = executor,
        )
    }
}
