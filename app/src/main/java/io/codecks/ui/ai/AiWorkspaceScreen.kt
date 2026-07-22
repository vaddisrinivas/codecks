package io.codecks.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.domain.ai.AiArtifact
import io.codecks.domain.ai.AiArtifactTest
import io.codecks.domain.ai.AiArtifactTestStatus
import io.codecks.domain.ai.AiGenerationRecord
import io.codecks.domain.ai.AiGenerationStatus
import io.codecks.domain.ai.DraftKind
import io.codecks.domain.ai.GeneratedDraft
import io.codecks.ui.designsystem.CodecksDeckEdgeGlowBackground
import io.codecks.ui.designsystem.CodecksPanel
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.designsystem.DeckFilterPill

@Composable
internal fun AiArtifactWorkspaceScreen(
    state: AiProviderSettingsState,
    draftKinds: List<DraftKind>,
    currentArtifact: AiArtifact?,
    previousArtifacts: List<AiArtifact>,
    contentPadding: PaddingValues,
    onDraftKindChanged: (DraftKind) -> Unit,
    onSkillInstructionsChanged: (String) -> Unit,
    onPromptChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancelRefinement: () -> Unit,
    onTestArtifact: (String) -> Unit,
    onSaveDraft: (GeneratedDraft) -> Unit,
    onRefineArtifact: (String) -> Unit,
    onDeleteArtifact: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
    onNewArtifact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.fillMaxSize())
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = CodecksDesignTokens.Size.sheetMaxWidth),
        ) {
            item {
                AiWorkspaceHero(
                    ready = state.aiAllowed && state.hasSavedKey,
                    artifactCount = state.artifacts.size,
                    onOpenAiSettings = onOpenAiSettings,
                    onNewArtifact = onNewArtifact,
                    hasActiveArtifact = currentArtifact != null || state.prompt.isNotBlank(),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item {
                AiSkillStrip(
                    draftKinds = draftKinds,
                    selected = state.draftKind,
                    onSelected = onDraftKindChanged,
                    skillInstructions = state.skillInstructions,
                    onSkillInstructionsChanged = onSkillInstructionsChanged,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item {
                AiPromptComposerCard(
                    state = state,
                    currentArtifact = currentArtifact,
                    onPromptChanged = onPromptChanged,
                    onGenerate = onGenerate,
                    onCancelRefinement = onCancelRefinement,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (currentArtifact != null) {
                item {
                    Text(
                        text = "Result preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                item {
                    val artifact = currentArtifact
                    val draftToSave = state.generatedDraft.takeIf { state.generatedArtifactId == artifact.id }
                    AiArtifactCard(
                        artifact = artifact,
                        refinementSource = state.lastRefinedFromArtifact.takeIf { state.lastRefinedArtifactId == artifact.id },
                        isTesting = state.testingArtifactId == artifact.id,
                        onTest = { onTestArtifact(artifact.id) },
                        onSave = draftToSave?.let {
                            { onSaveDraft(it) }
                        },
                        saveLabel = "Save disabled",
                        onRefine = { onRefineArtifact(artifact.id) },
                        onDelete = { onDeleteArtifact(artifact.id) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                item {
                    Text(
                        text = "Result preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                item {
                    AiEmptyArtifactPreview(
                        selected = state.draftKind,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (previousArtifacts.isNotEmpty()) {
                item {
                    Text(
                        text = "Saved drafts",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
            }
            previousArtifacts.take(6).forEach { artifact ->
                item {
                    AiArtifactHistoryCard(
                        artifact = artifact,
                        onRestore = { onRefineArtifact(artifact.id) },
                        onDelete = { onDeleteArtifact(artifact.id) },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (state.generationHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Generation log",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
            }
            state.generationHistory.take(4).forEach { record ->
                item {
                    AiGenerationRecordCard(
                        record = record,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            item {
                Text(
                    text = "Saved drafts stay disabled until you test and enable them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AiWorkspaceHero(
    ready: Boolean,
    artifactCount: Int,
    onOpenAiSettings: () -> Unit,
    onNewArtifact: () -> Unit,
    hasActiveArtifact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("AI Builder", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${if (ready) "Ready" else "AI key setup needed"} · $artifactCount saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!ready) {
                DeckActionButton(
                    label = "Open AI settings",
                    onClick = onOpenAiSettings,
                    icon = Icons.Outlined.Settings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
            if (hasActiveArtifact) {
                DeckActionButton(
                    label = "New draft",
                    onClick = onNewArtifact,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
            }
        }
    }
}

@Composable
private fun AiSkillStrip(
    draftKinds: List<DraftKind>,
    selected: DraftKind,
    onSelected: (DraftKind) -> Unit,
    skillInstructions: String,
    onSkillInstructionsChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var skillEditorOpen by remember { mutableStateOf(false) }
    CodecksPanel(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
            Text("Skill", style = MaterialTheme.typography.titleSmall)
            Text(
                text = selected.skillDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                draftKinds.forEach { kind ->
                    DeckFilterPill(
                        label = kind.skillLabel,
                        selected = selected == kind,
                        onClick = { onSelected(kind) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
            DeckActionButton(
                label = if (skillEditorOpen) "Hide instructions" else "Edit instructions",
                onClick = { skillEditorOpen = !skillEditorOpen },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            )
            if (skillEditorOpen) {
                OutlinedTextField(
                    value = skillInstructions,
                    onValueChange = onSkillInstructionsChanged,
                    label = { Text("Builder instructions") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AiEmptyArtifactPreview(
    selected: DraftKind,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(18.dp)) {
            Text("No result yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick a skill, write one instruction below, then generate. The preview appears here; edit the prompt and regenerate until it is right.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                selected.placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AiPromptComposerCard(
    state: AiProviderSettingsState,
    currentArtifact: AiArtifact?,
    onPromptChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onCancelRefinement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth().heightIn(max = 232.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.draftKind.skillLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (currentArtifact == null) "New draft" else "Edit prompt → regenerate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChanged,
                placeholder = { Text(state.draftKind.placeholder) },
                minLines = 1,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp, max = 88.dp),
            )
            DeckActionButton(
                label = when {
                    state.isGenerating -> "Thinking…"
                    currentArtifact != null || state.refiningArtifact != null -> "Regenerate"
                    else -> "Generate"
                },
                onClick = onGenerate,
                enabled = state.prompt.isNotBlank() && !state.isGenerating,
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
            if (state.isGenerating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                state.refiningArtifact?.let { artifact ->
                    Text(
                        text = "Loaded: ${artifact.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(modifier = Modifier.weight(1f))
                state.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.testStatus == AiProviderTestStatus.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.refiningArtifact != null) {
                    DeckActionButton(
                        label = "Clear",
                        onClick = onCancelRefinement,
                        enabled = !state.isGenerating,
                        icon = Icons.Outlined.ErrorOutline,
                        modifier = Modifier.weight(0.7f).heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiArtifactCard(
    artifact: AiArtifact,
    refinementSource: AiArtifact?,
    isTesting: Boolean,
    onTest: () -> Unit,
    onSave: (() -> Unit)?,
    saveLabel: String? = null,
    onRefine: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(artifact.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${artifact.kind.label} · ${artifact.actions.size} action${if (artifact.actions.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (artifact.description.isNotBlank()) {
                Text(
                    artifact.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            refinementSource?.let { source ->
                AiRefinementDiffCard(before = source, after = artifact)
            }
            AiArtifactReviewPanel(artifact = artifact)
            artifact.actions.forEachIndexed { index, action ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${index + 1}. ${action.title}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            action.command.ifBlank { "No command" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Review before saving. Test checks generated commands and never runs them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            artifact.lastTest?.let { test ->
                Text(
                    text = test.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (test.status) {
                        AiArtifactTestStatus.Succeeded -> MaterialTheme.colorScheme.primary
                        AiArtifactTestStatus.Failed -> MaterialTheme.colorScheme.error
                        AiArtifactTestStatus.RequiresConfirmation -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckActionButton(
                    label = if (isTesting) "Testing" else "Test draft",
                    onClick = onTest,
                    enabled = !isTesting,
                    icon = Icons.Outlined.PlayArrow,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                if (onSave != null) {
                    DeckActionButton(
                        label = saveLabel ?: if (artifact.kind == io.codecks.domain.ai.AiArtifactKind.Deck) "Save deck" else "Save",
                        onClick = onSave,
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
                DeckActionButton(
                    label = "Refine",
                    onClick = onRefine,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                DeckActionButton(
                    label = "Remove",
                    onClick = onDelete,
                    icon = Icons.Outlined.ErrorOutline,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun AiArtifactHistoryCard(
    artifact: AiArtifact,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(artifact.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${artifact.kind.label} · ${artifact.actions.size} action${if (artifact.actions.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (artifact.description.isNotBlank()) {
                Text(
                    artifact.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckActionButton(
                    label = "Load prompt",
                    onClick = onRestore,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                DeckActionButton(
                    label = "Remove",
                    onClick = onDelete,
                    icon = Icons.Outlined.ErrorOutline,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun AiArtifactReviewPanel(
    artifact: AiArtifact,
    modifier: Modifier = Modifier,
) {
    val review = artifact.review
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (review.requiresConfirmation) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
            Text("Proposal review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            AiReviewLine(
                label = "Risk",
                value = if (review.requiresConfirmation) {
                    "${review.riskLevel.name} · confirmation required"
                } else {
                    review.riskLevel.name
                },
            )
            AiReviewLine(label = "Mac", value = review.target)
            review.trigger?.let { trigger -> AiReviewLine(label = "Trigger", value = trigger) }
            AiReviewList(
                label = "Assumptions",
                values = review.assumptions.ifEmpty { listOf("No extra assumptions supplied") },
            )
            AiReviewList(
                label = "Capabilities",
                values = review.requiredCapabilities.ifEmpty { listOf("No special capability requested") },
            )
            AiReviewList(
                label = "Parameters",
                values = review.parameters.map { parameter ->
                    buildString {
                        append(parameter.label.ifBlank { parameter.name })
                        append(if (parameter.required) " · required" else " · optional")
                        parameter.defaultValue?.takeIf { it.isNotBlank() }?.let { append(" · default: $it") }
                    }
                }.ifEmpty { listOf("No runtime parameters") },
            )
            AiReviewList(
                label = "Compiled steps",
                values = review.steps.mapIndexed { index, step ->
                    "${index + 1}. ${step.label} · ${step.type} · ${step.summary}" +
                        if (step.requiresConfirmation) " · confirms" else ""
                }.ifEmpty { listOf("No compiled steps") },
            )
        }
    }
}

@Composable
private fun AiReviewLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AiReviewList(
    label: String,
    values: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEach { value ->
            Text("• $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

internal val io.codecks.domain.ai.AiArtifactKind.label: String
    get() = when (this) {
        io.codecks.domain.ai.AiArtifactKind.Button -> "Button"
        io.codecks.domain.ai.AiArtifactKind.Deck -> "Deck"
        io.codecks.domain.ai.AiArtifactKind.Automation -> "Rule"
        io.codecks.domain.ai.AiArtifactKind.Clock -> "Clock"
    }

@Composable
private fun AiRefinementDiffCard(
    before: AiArtifact,
    after: AiArtifact,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
            Text("Refinement compare", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Before: ${before.title} · ${before.actions.size} action${if (before.actions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "After: ${after.title} · ${after.actions.size} action${if (after.actions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = after.changedActionSummary(before),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun AiArtifact.changedActionSummary(before: AiArtifact): String {
    val beforeCommands = before.actions.associateBy { it.id }
    val changed = actions.filter { action -> beforeCommands[action.id]?.command != action.command }
    return when {
        changed.isEmpty() && actions.size == before.actions.size -> "No command-level changes detected; review labels and descriptions."
        changed.isEmpty() -> "Action count changed from ${before.actions.size} to ${actions.size}."
        else -> "Changed: ${changed.take(3).joinToString { it.title }}${if (changed.size > 3) " +${changed.size - 3}" else ""}"
    }
}

@Composable
private fun AiGenerationRecordCard(
    record: AiGenerationRecord,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.status.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = record.status.color(),
                )
                Text(
                    text = "${record.draftKind.label} · ${record.providerLabel} · ${record.modelLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = record.message.ifBlank { "No message" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (record.validationErrors.isNotEmpty()) {
                Text(
                    text = record.validationErrors.take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private val AiGenerationStatus.label: String
    get() = when (this) {
        AiGenerationStatus.Ready -> "Ready"
        AiGenerationStatus.NeedsInput -> "Needs input"
        AiGenerationStatus.Unsupported -> "Unsupported"
        AiGenerationStatus.Refused -> "Refused"
        AiGenerationStatus.Failed -> "Failed"
    }

@Composable
private fun AiGenerationStatus.color() =
    when (this) {
        AiGenerationStatus.Ready -> MaterialTheme.colorScheme.primary
        AiGenerationStatus.NeedsInput -> MaterialTheme.colorScheme.secondary
        AiGenerationStatus.Unsupported -> MaterialTheme.colorScheme.onSurfaceVariant
        AiGenerationStatus.Refused -> MaterialTheme.colorScheme.error
        AiGenerationStatus.Failed -> MaterialTheme.colorScheme.error
    }

private val DraftKind.label: String
    get() = when (this) {
        DraftKind.Action -> "Button"
        DraftKind.Deck -> "Deck"
        DraftKind.Automation -> "Rule"
        DraftKind.ContextApps -> "Context apps"
    }

private val DraftKind.skillLabel: String
    get() = when (this) {
        DraftKind.Action -> "Button"
        DraftKind.Deck -> "Deck"
        DraftKind.Automation -> "Rule"
        DraftKind.ContextApps -> "Context"
    }

private val DraftKind.skillDescription: String
    get() = when (this) {
        DraftKind.Action -> "Create one safe Deck button from a plain-English task."
        DraftKind.Deck -> "Create a grouped Deck of related buttons."
        DraftKind.Automation -> "Create a disabled rule from a task."
        DraftKind.ContextApps -> "Suggest context-aware app/action lanes from local signals."
    }

private val DraftKind.placeholder: String
    get() = when (this) {
        DraftKind.Action -> "Open Linear and start my daily standup note"
        DraftKind.Deck -> "Build a coding deck with browser, terminal, GitHub, music, and focus controls"
        DraftKind.Automation -> "When I start a meeting, open notes, calendar, and mute media"
        DraftKind.ContextApps -> "Suggest the best phone apps for my current work context"
    }
