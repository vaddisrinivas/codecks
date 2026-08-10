package io.codecks.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.codecks.data.icons.BundledDeckIconCatalog
import io.codecks.data.icons.DeckIconPreferenceRepository
import io.codecks.domain.ActionIcon
import io.codecks.domain.icons.IconCategory
import io.codecks.domain.icons.IconPreferenceState
import io.codecks.ui.icons.imageVector
import io.codecks.ui.theme.CodecksIconPack
import io.codecks.ui.theme.LocalCodecksIconPack
import kotlinx.coroutines.launch

@Composable
internal fun DeckIconPicker(
    selectedIcon: ActionIcon,
    onSelectIcon: (ActionIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { DeckIconPreferenceRepository(context) }
    val preferences by repository.state.collectAsStateWithLifecycle(IconPreferenceState())
    val scope = rememberCoroutineScope()
    val currentPack = LocalCodecksIconPack.current
    var packName by rememberSaveable { mutableStateOf(currentPack.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf(IconCategory.ALL.name) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var recentsOnly by rememberSaveable { mutableStateOf(false) }
    val pack = CodecksIconPack.entries.firstOrNull { it.name == packName } ?: currentPack
    val category = IconCategory.entries.firstOrNull { it.name == categoryName } ?: IconCategory.ALL
    val catalog = BundledDeckIconCatalog.catalog
    val visible = if (recentsOnly) {
        val allowedIds = catalog.search(query, category, preferences = preferences).mapTo(mutableSetOf()) { it.id }
        catalog.recent(preferences).filter { it.id in allowedIds }
    } else {
        catalog.search(query, category, favoritesOnly, preferences)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Text("Icon", style = MaterialTheme.typography.titleSmall)
        Text(
            "Preview an icon-pack style here. The active pack for every Deck button is set in Appearance.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CodecksIconPack.entries, key = CodecksIconPack::name) { choice ->
                Surface(
                    onClick = { packName = choice.name },
                    color = if (choice == pack) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (choice == pack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(choice.label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            singleLine = true,
            label = { Text("Find icon") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PickerFilter("Favorites", favoritesOnly) {
                    favoritesOnly = !favoritesOnly
                    if (favoritesOnly) recentsOnly = false
                }
            }
            item {
                PickerFilter("Recents", recentsOnly) {
                    recentsOnly = !recentsOnly
                    if (recentsOnly) favoritesOnly = false
                }
            }
            items(IconCategory.entries, key = IconCategory::name) { choice ->
                PickerFilter(choice.name.lowercase().replaceFirstChar(Char::uppercase), choice == category) {
                    categoryName = choice.name
                }
            }
        }
        if (visible.isEmpty()) {
            Text(
                "No matching icons. Clear search or favorites.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(visible, key = { it.id.value }) { definition ->
                    val favorite = definition.id in preferences.favorites
                    val selected = definition.actionIcon == selectedIcon
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.width(88.dp).heightIn(min = 116.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                            IconButton(
                                onClick = {
                                    onSelectIcon(definition.actionIcon)
                                    scope.launch { repository.recordRecent(definition.id) }
                                },
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    definition.actionIcon.imageVector(pack),
                                    contentDescription = "Use ${definition.label} icon",
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                definition.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = { scope.launch { repository.toggleFavorite(definition.id) } },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (favorite) "Remove ${definition.label} from favorites" else "Favorite ${definition.label}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) }
}
