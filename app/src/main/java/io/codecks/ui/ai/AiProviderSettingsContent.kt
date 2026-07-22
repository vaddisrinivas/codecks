package io.codecks.ui.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.codecks.BuildConfig
import io.codecks.domain.ai.DraftKind
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckFilterPill

internal fun LazyListScope.aiProviderSettingsItems(
    state: AiProviderSettingsState,
    onProviderSelected: (AiProviderChoice) -> Unit,
    onModelSelected: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onTest: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onRefreshEntitlement: () -> Unit,
) {
    item {
        AiProviderSummary(
            state = state,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
    item {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            AiProviderChoice.entries.forEach { provider ->
                DeckFilterPill(
                    label = provider.label,
                    selected = state.selectedProvider == provider,
                    onClick = { onProviderSelected(provider) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
    item {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text("Model", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                state.selectedProvider.models.forEach { model ->
                    val modelEnabled = state.draftKind == DraftKind.ContextApps || model.supportsStructuredDrafts
                    DeckFilterPill(
                        label = if (modelEnabled) model.label else "${model.label} · draft unavailable",
                        selected = state.selectedModelId == model.id,
                        onClick = { onModelSelected(model.id) },
                        enabled = modelEnabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
    item {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Paste an AI key here. Codecks does not read API keys from your Mac over SSH.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.selectedProvider == AiProviderChoice.LiteLLM) {
                OutlinedTextField(
                    value = state.baseUrlInput,
                    onValueChange = onBaseUrlChanged,
                    label = { Text("Endpoint URL") },
                    placeholder = { Text(BuildConfig.LITELLM_BASE_URL) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (state.savedBaseUrl.isNotBlank()) {
                                "Saved endpoint used for generation"
                            } else {
                                "Use this for LiteLLM, Azure/OpenAI-compatible gateways, or local model routers"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = onApiKeyChanged,
                label = { Text("${state.selectedProvider.label} API key") },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                supportingText = {
                    Text(
                        when {
                            state.hasSavedKey && state.apiKeyInput.isNotBlank() -> "Save to replace the stored key"
                            state.hasSavedKey -> "Saved key ready for tests and generation"
                            else -> "Save a key before testing or generating"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeckActionButton(
                    label = "Save API key",
                    onClick = onSaveApiKey,
                    enabled = state.apiKeyInput.isNotBlank(),
                    icon = Icons.Outlined.Key,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
                DeckActionButton(
                    label = if (state.testStatus == AiProviderTestStatus.Running) "Testing" else "Test API key",
                    onClick = onTest,
                    enabled = state.hasSavedKey && state.testStatus != AiProviderTestStatus.Running,
                    icon = Icons.Outlined.Science,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun AiProviderSummary(
    state: AiProviderSettingsState,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text("Provider", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${state.selectedProvider.label} · ${state.selectedModel.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        state.hasSavedKey -> "Encrypted key saved"
                        else -> "Save an API key before generating"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
