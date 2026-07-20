package io.codecks.feature.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.codecks.core.designsystem.CodecksAppTheme
import io.codecks.core.designsystem.CodecksTokens
import io.codecks.core.designsystem.adaptive.CodecksWidthClass
import io.codecks.core.designsystem.adaptive.rememberCodecksWindowSize
import io.codecks.core.designsystem.deck.DeckKeySize
import io.codecks.core.designsystem.deck.DeckKeyState
import io.codecks.core.designsystem.deck.LuminousDeckKey
import io.codecks.domain.decks.Deck
import io.codecks.domain.decks.DeckButton
import kotlin.math.min

@Immutable
data class DeckUiModel(
    val title: String,
    val keys: List<DeckKeyUiModel>,
)

@Immutable
data class DeckKeyUiModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val size: DeckKeySize = DeckKeySize.OneU,
    val state: DeckKeyState = DeckKeyState.Idle,
    val actionId: String? = null,
)

internal enum class DeckKeyRoute {
    TRACKPAD,
    KEYBOARD,
    ACTION,
}

@Composable
fun DeckScreen(
    onOpenTrackpad: () -> Unit = {},
    onOpenKeyboard: () -> Unit = {},
    modifier: Modifier = Modifier,
    deck: DeckUiModel = rememberDefaultDeck(),
    onKeyClick: (DeckKeyUiModel) -> Unit = {},
) {
    val windowSize = rememberCodecksWindowSize()
    val layout = deckLayoutFor(windowSize.width, windowSize.height, windowSize.widthClass)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CodecksTokens.colors.oledBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(layout.minCellWidth),
            modifier = Modifier
                .fillMaxSize()
                .padding(layout.outerPadding),
            contentPadding = PaddingValues(bottom = layout.outerPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.gap),
            verticalArrangement = Arrangement.spacedBy(layout.gap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = deck.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    color = CodecksTokens.colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            items(
                items = deck.keys,
                key = { it.id },
                span = { key ->
                    GridItemSpan(min(key.size.gridSpan, maxLineSpan))
                },
            ) { key ->
                LuminousDeckKey(
                    title = key.title,
                    subtitle = key.subtitle,
                    size = key.size,
                    state = key.state,
                    minHeight = layout.keyMinHeight,
                    modifier = Modifier.heightIn(min = layout.keyMinHeight),
                    onClick = if (key.state == DeckKeyState.Disabled) {
                        null
                    } else {
                        {
                            when (key.route()) {
                                DeckKeyRoute.TRACKPAD -> onOpenTrackpad()
                                DeckKeyRoute.KEYBOARD -> onOpenKeyboard()
                                DeckKeyRoute.ACTION -> onKeyClick(key)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun DeckScreen(
    deck: Deck,
    onOpenTrackpad: () -> Unit = {},
    onOpenKeyboard: () -> Unit = {},
    modifier: Modifier = Modifier,
    onKeyClick: (DeckKeyUiModel) -> Unit = {},
) {
    DeckScreen(
        onOpenTrackpad = onOpenTrackpad,
        onOpenKeyboard = onOpenKeyboard,
        modifier = modifier,
        deck = deck.toDeckUiModel(),
        onKeyClick = onKeyClick,
    )
}

internal fun DeckKeyUiModel.route(): DeckKeyRoute = when (actionId ?: id) {
    "android.trackpad.open", "trackpad" -> DeckKeyRoute.TRACKPAD
    "android.keyboard.open", "keyboard" -> DeckKeyRoute.KEYBOARD
    else -> DeckKeyRoute.ACTION
}

@Immutable
private data class DeckLayout(
    val minCellWidth: Dp,
    val keyMinHeight: Dp,
    val gap: Dp,
    val outerPadding: Dp,
)

internal fun Deck.toDeckUiModel(): DeckUiModel {
    val page = pages.firstOrNull()
    return DeckUiModel(
        title = page?.name?.takeIf { it.isNotBlank() } ?: name,
        keys = page
            ?.buttons
            ?.sortedWith(compareBy<DeckButton> { it.position.row }.thenBy { it.position.column })
            ?.map { it.toDeckKeyUiModel() }
            ?: emptyList(),
    )
}

private fun DeckButton.toDeckKeyUiModel(): DeckKeyUiModel {
    val actionId = actionRef.stableId
    return DeckKeyUiModel(
        id = id,
        title = presentation.label,
        subtitle = presentation.glyph,
        size = if (span.columns >= 3) DeckKeySize.ThreeU else DeckKeySize.OneU,
        state = when {
            behavior.disabledReason != null -> DeckKeyState.Disabled
            behavior.confirmDangerous && (actionId.contains("stop") || actionId.contains("lock")) -> DeckKeyState.Danger
            else -> DeckKeyState.Idle
        },
        actionId = actionId,
    )
}

private fun deckLayoutFor(
    width: Dp,
    height: Dp,
    widthClass: CodecksWidthClass,
): DeckLayout {
    val landscape = width > height
    return when (widthClass) {
        CodecksWidthClass.Compact -> DeckLayout(
            minCellWidth = if (landscape) 96.dp else 80.dp,
            keyMinHeight = if (landscape) 64.dp else 72.dp,
            gap = 10.dp,
            outerPadding = 16.dp,
        )
        CodecksWidthClass.Medium -> DeckLayout(
            minCellWidth = if (landscape) 104.dp else 96.dp,
            keyMinHeight = 80.dp,
            gap = 12.dp,
            outerPadding = 20.dp,
        )
        CodecksWidthClass.Expanded -> DeckLayout(
            minCellWidth = 116.dp,
            keyMinHeight = 88.dp,
            gap = 14.dp,
            outerPadding = 28.dp,
        )
    }
}

@Composable
private fun rememberDefaultDeck(): DeckUiModel = DeckUiModel(
    title = "Codecks",
    keys = listOf(
        DeckKeyUiModel("trackpad", "Trackpad", "Pointer", state = DeckKeyState.Running),
        DeckKeyUiModel("keyboard", "Keyboard", "Input"),
        DeckKeyUiModel("run", "Run", "Primary"),
        DeckKeyUiModel("build", "Build", "Debug"),
        DeckKeyUiModel("test", "Test", "Unit"),
        DeckKeyUiModel("ship", "Ship", "Ready", state = DeckKeyState.Success),
        DeckKeyUiModel("search", "Search"),
        DeckKeyUiModel("review", "Review", size = DeckKeySize.ThreeU),
        DeckKeyUiModel("pause", "Pause"),
        DeckKeyUiModel("focus", "Focus"),
        DeckKeyUiModel("record", "Record"),
        DeckKeyUiModel("danger", "Stop", "Danger", state = DeckKeyState.Danger),
        DeckKeyUiModel("one", "Slot 11"),
        DeckKeyUiModel("two", "Slot 12"),
        DeckKeyUiModel("three", "Slot 13"),
        DeckKeyUiModel("four", "Slot 14"),
        DeckKeyUiModel("five", "Slot 15"),
        DeckKeyUiModel("six", "Slot 16"),
        DeckKeyUiModel("seven", "Slot 17"),
        DeckKeyUiModel("eight", "Slot 18"),
        DeckKeyUiModel("nine", "Slot 19"),
        DeckKeyUiModel("disabled", "Offline", "Disabled", state = DeckKeyState.Disabled),
    ),
)

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp,dpi=420", showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DeckScreenPreview() {
    CodecksAppTheme {
        DeckScreen()
    }
}
