package io.codecks.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.SettingsRoute
import io.codecks.navigation.title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecksAppShell(
    snackbarHostState: SnackbarHostState,
    currentRoute: NavKey,
    backStackSize: Int,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val currentTab = PrimaryTab.entries.firstOrNull { it.route == currentRoute }
    val showBottomBar = !fullscreen && currentTab != null
    val swipeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val edgeSwipePx = with(LocalDensity.current) { 56.dp.toPx() }
    val swipeModifier = if (!fullscreen && currentTab != null && backStackSize <= 1) {
        Modifier.pointerInput(currentTab) {
            var dragTotal = 0f
            var edgeSwipe = false
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragTotal = 0f
                    edgeSwipe = offset.x <= edgeSwipePx || offset.x >= size.width - edgeSwipePx
                },
                onHorizontalDrag = { _, dragAmount ->
                    if (edgeSwipe) dragTotal += dragAmount
                },
                onDragEnd = {
                    if (!edgeSwipe || kotlin.math.abs(dragTotal) < swipeThresholdPx) return@detectHorizontalDragGestures
                    val tabs = PrimaryTab.entries
                    val index = tabs.indexOf(currentTab)
                    val nextIndex = if (dragTotal < 0f) index + 1 else index - 1
                    tabs.getOrNull(nextIndex)?.let { tab -> onDestinationSelected(tab.route) }
                    dragTotal = 0f
                    edgeSwipe = false
                },
                onDragCancel = {
                    dragTotal = 0f
                    edgeSwipe = false
                },
            )
        }
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxSize().then(swipeModifier)) {
        Scaffold(
            topBar = if (fullscreen || currentRoute == HomeRoute) {
                {}
            } else {
                {
                    DeckBridgeTopBar(
                        currentRoute = currentRoute,
                        backStackSize = backStackSize,
                        onBack = onBack,
                        onOpenSettings = onOpenSettings,
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    CodecksBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { tab -> onDestinationSelected(tab.route) },
                        onSettingsLongPress = onRequestFullscreen,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            content(contentPadding)
        }
        if (fullscreen) {
            IconButton(
                onClick = onExitFullscreen,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Outlined.FullscreenExit, contentDescription = "Exit fullscreen")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodecksBottomBar(
    currentTab: PrimaryTab,
    onTabSelected: (PrimaryTab) -> Unit,
    onSettingsLongPress: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
        PrimaryTab.entries.forEach { tab ->
                val selected = currentTab == tab
                Surface(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = (if (tab == PrimaryTab.Settings) {
                        Modifier.combinedClickable(
                            onClick = { onTabSelected(tab) },
                            onLongClick = onSettingsLongPress,
                        )
                    } else {
                        Modifier.combinedClickable(
                            onClick = { onTabSelected(tab) },
                            onLongClick = {},
                        )
                    })
                        .width(96.dp)
                        .heightIn(min = 64.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(tab.icon, contentDescription = null)
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckBridgeTopBar(
    currentRoute: NavKey,
    backStackSize: Int,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text(currentRoute.title()) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        navigationIcon = {
            if (backStackSize > 1) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        actions = {
            if (currentRoute != SettingsRoute && PrimaryTab.entries.none { it.route == currentRoute }) {
                IconButton(onClick = onOpenSettings) {
                    Icon(PrimaryTab.Settings.icon, contentDescription = "Settings")
                }
            }
        },
    )
}
