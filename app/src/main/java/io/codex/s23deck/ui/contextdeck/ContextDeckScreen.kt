package io.codex.s23deck.ui.contextdeck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.codex.s23deck.core.design.DeckBridgeDesignTokens
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.domain.context.ContextApp
import io.codex.s23deck.domain.context.ContextSignal
import io.codex.s23deck.domain.context.RankedContextApp
import io.codex.s23deck.domain.context.RankedContextAction
import io.codex.s23deck.ui.designsystem.DeckActionButton
import io.codex.s23deck.ui.designsystem.DeckPage
import io.codex.s23deck.ui.designsystem.DeckSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextDeckScreen(
    rankedActions: List<RankedContextAction>,
    rankedApps: List<RankedContextApp>,
    signals: List<ContextSignal>,
    appPrompt: String,
    appStatus: String,
    contentPadding: PaddingValues,
    onAction: (DeckAction) -> Unit,
    onApp: (ContextApp) -> Unit,
    onOpenAiBuilder: () -> Unit,
    onOpenWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val appColumns = when {
        configuration.screenWidthDp >= 960 -> 6
        configuration.screenWidthDp >= 600 -> 4
        else -> 4
    }
    val actionColumns = if (configuration.screenWidthDp >= 840) 3 else 2
    DeckPage(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DeckBridgeDesignTokens.Spacing.page),
            ) {
                Text(
                    text = "AI Context Deck",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
                )
                Text(
                    text = "A live command surface ranked from your Mac app, phone notifications, and local state.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 16.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                ) {
                    AssistChip(
                        onClick = onOpenAiBuilder,
                        label = { Text("Create controls") },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = onOpenWidget,
                        label = { Text("Pin widget") },
                        leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                    )
                }
            }
        }
        item { DeckSectionLabel("Live signals") }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DeckBridgeDesignTokens.Spacing.page, vertical = 4.dp),
            ) {
                signals.forEach { signal ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${signal.label}: ${signal.value}") },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    )
                }
            }
        }
        item { DeckSectionLabel("Context sent to AI") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DeckBridgeDesignTokens.Spacing.page),
            ) {
                Text(
                    text = appStatus,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
                )
                Text(
                    text = "This is the prompt used for scheduled app ranking. It includes installed app labels/package names and coarse signals only. Notification text bodies and location are not collected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
                )
                SelectionContainer {
                    Text(
                        text = appPrompt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 14.dp),
                    )
                }
            }
        }
        item { DeckSectionLabel("Suggested apps") }
        rankedApps.chunked(appColumns).forEach { row ->
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DeckBridgeDesignTokens.Spacing.page, vertical = 6.dp),
                ) {
                    row.forEach { ranked ->
                        ContextAppCard(
                            ranked = ranked,
                            onClick = { onApp(ranked.app) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(appColumns - row.size) {
                        Column(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
        if (rankedApps.isEmpty()) {
            item {
                Text(
                    text = "No app suggestion has enough context yet. Codecks will wait for a clear signal instead of guessing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DeckBridgeDesignTokens.Spacing.page),
                )
            }
        }
        item { DeckSectionLabel("Suggested controls") }
        rankedActions.chunked(actionColumns).forEach { row ->
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DeckBridgeDesignTokens.Spacing.page, vertical = 6.dp),
                ) {
                    row.forEach { ranked ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            DeckActionButton(
                                label = ranked.action.label,
                                onClick = { onAction(ranked.action) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = ranked.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    repeat(actionColumns - row.size) {
                        Column(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
        if (rankedActions.isEmpty()) {
            item {
                Text(
                    text = "No actions available yet. Open AI Creator or Deck Editor to add controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DeckBridgeDesignTokens.Spacing.page),
                )
            }
        }
    }
}

@Composable
private fun ContextAppCard(
    ranked: RankedContextApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(10.dp),
        ) {
            AppIcon(packageName = ranked.app.packageName, label = ranked.app.label)
            Text(
                text = ranked.app.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Text(
                text = ranked.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(48, 48)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = label,
            modifier = Modifier.size(40.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = label,
            modifier = Modifier.size(40.dp),
        )
    }
}

private fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
