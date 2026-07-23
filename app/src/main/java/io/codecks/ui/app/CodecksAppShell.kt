package io.codecks.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.RunLogRoute
import io.codecks.navigation.SettingsRoute
import io.codecks.navigation.title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecksAppShell(
    snackbarHostState: SnackbarHostState,
    currentRoute: NavKey,
    backStackSize: Int,
    fullscreen: Boolean,
    tabs: List<PrimaryTab> = PrimaryTab.entries,
    onBack: () -> Unit,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val shellDestinations = rememberShellDestinations(tabs)
    val currentDestination = shellDestinations.firstOrNull { it.route == currentRoute }
    val showNavigation = !fullscreen && currentDestination != null

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 840.dp
            Scaffold(
                topBar = if (fullscreen || currentRoute == HomeRoute) {
                    {}
                } else {
                    {
                        CodecksTopBar(
                            currentRoute = currentRoute,
                            backStackSize = backStackSize,
                            onBack = onBack,
                            onOpenSettings = onOpenSettings,
                            onRequestFullscreen = onRequestFullscreen,
                        )
                    }
                },
                bottomBar = {
                    if (showNavigation && !useRail) {
                        CodecksBottomBar(
                            currentRoute = currentRoute,
                            destinations = shellDestinations,
                            onDestinationSelected = onDestinationSelected,
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { contentPadding ->
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (showNavigation && useRail) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            CodecksNavigationRail(
                                currentRoute = currentRoute,
                                destinations = shellDestinations,
                                onDestinationSelected = onDestinationSelected,
                                modifier = Modifier.padding(top = if (currentRoute == HomeRoute) 0.dp else 64.dp),
                            )
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                content(contentPadding)
                            }
                        }
                    } else {
                        content(contentPadding)
                    }
                }
            }
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

private data class ShellDestination(
    val route: NavKey,
    val label: String,
    val icon: ImageVector,
    val group: ShellGroup,
)

private enum class ShellGroup {
    Control,
    Build,
    Manage,
}

@Composable
private fun rememberShellDestinations(tabs: List<PrimaryTab>): List<ShellDestination> {
    return tabs.map { tab ->
        ShellDestination(
            route = tab.route,
            label = tab.label,
            icon = tab.icon,
            group = when (tab) {
                PrimaryTab.Deck,
                PrimaryTab.Trackpad,
                PrimaryTab.Keyboard,
                PrimaryTab.Clipboard -> ShellGroup.Control
                PrimaryTab.Automations,
                PrimaryTab.Ai -> ShellGroup.Build
                PrimaryTab.Settings -> ShellGroup.Manage
            },
        )
    } + ShellDestination(
        route = RunLogRoute,
        label = "Run history",
        icon = Icons.Outlined.History,
        group = ShellGroup.Manage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodecksBottomBar(
    currentRoute: NavKey,
    destinations: List<ShellDestination>,
    onDestinationSelected: (NavKey) -> Unit,
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val pinnedRoutes = listOf(HomeRoute, PrimaryTab.Trackpad.route, PrimaryTab.Keyboard.route, PrimaryTab.Clipboard.route)
    val pinned = pinnedRoutes.mapNotNull { route -> destinations.firstOrNull { it.route == route } }
        .ifEmpty { destinations.take(4) }
    val moreDestinations = destinations.filterNot { destination -> pinned.any { it.route == destination.route } }
    val moreSelected = moreDestinations.any { it.route == currentRoute }

    NavigationBar(
        tonalElevation = 3.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        pinned.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = {
                    Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
        NavigationBarItem(
            selected = moreSelected,
            onClick = { moreOpen = true },
            icon = { Icon(Icons.Outlined.MoreHoriz, contentDescription = null) },
            label = { Text("More") },
        )
    }
    if (moreOpen) {
        ModalBottomSheet(onDismissRequest = { moreOpen = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "More",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                moreDestinations.forEach { destination ->
                    ListItem(
                        headlineContent = { Text(destination.label) },
                        supportingContent = { Text(destination.group.label) },
                        leadingContent = { Icon(destination.icon, contentDescription = null) },
                        modifier = Modifier.clickable {
                            moreOpen = false
                            onDestinationSelected(destination.route)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CodecksNavigationRail(
    currentRoute: NavKey,
    destinations: List<ShellDestination>,
    onDestinationSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.width(96.dp),
    ) {
        destinations.forEachIndexed { index, destination ->
            if (index > 0 && destinations[index - 1].group != destination.group) {
                Text(
                    destination.group.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            } else if (index == 0) {
                Text(
                    destination.group.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

private val ShellGroup.label: String
    get() = when (this) {
        ShellGroup.Control -> "Control"
        ShellGroup.Build -> "Build"
        ShellGroup.Manage -> "Manage"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodecksTopBar(
    currentRoute: NavKey,
    backStackSize: Int,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestFullscreen: () -> Unit,
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
            IconButton(onClick = onRequestFullscreen) {
                Icon(Icons.Outlined.Fullscreen, contentDescription = "Fullscreen")
            }
            if (currentRoute != SettingsRoute && PrimaryTab.entries.none { it.route == currentRoute }) {
                IconButton(onClick = onOpenSettings) {
                    Icon(PrimaryTab.Settings.icon, contentDescription = "Settings")
                }
            }
        },
    )
}
