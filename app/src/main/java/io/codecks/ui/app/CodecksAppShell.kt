package io.codecks.ui.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import io.codecks.domain.DeckAction
import io.codecks.navigation.AppRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.SettingsRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecksAppShell(
    snackbarHostState: SnackbarHostState,
    currentRoute: NavKey,
    backStackSize: Int,
    fullscreen: Boolean,
    buildExposure: RouteBuildExposure = RouteBuildExposure.PUBLIC,
    tabs: List<RouteDescriptor<out AppRoute>> = RouteRegistry.primaryDestinations(),
    onBack: () -> Unit,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onStopInput: () -> Unit = {},
    visibleDeckActions: List<DeckAction> = emptyList(),
    selectedActionId: String? = null,
    actionRunning: Boolean = false,
    onAction: (DeckAction) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val shellDestinations = remember(tabs, buildExposure) { shellDestinations(tabs, buildExposure) }
    val currentDestination = shellDestinations.firstOrNull { it.route == currentRoute }
    val showNavigation = !fullscreen && currentDestination != null
    val focusManager = LocalFocusManager.current
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val openDrawer = { drawerScope.launch { drawerState.open() } }
    val closeDrawer = { drawerScope.launch { drawerState.close() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                handleShellKeyEvent(
                    event = event,
                    fullscreen = fullscreen,
                    canNavigateBack = backStackSize > 1,
                    focusManager = focusManager,
                    onBack = onBack,
                    onExitFullscreen = onExitFullscreen,
                )
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val fontScale = LocalDensity.current.fontScale
            val accessibilityLayout = shellAccessibilityLayout(
                widthDp = maxWidth.value.toInt().coerceAtLeast(1),
                heightDp = maxHeight.value.toInt().coerceAtLeast(1),
                fontScale = fontScale,
                fullscreen = fullscreen,
            )
            val drawerMode = shellDrawerMode(
                widthDp = maxWidth.value.toInt().coerceAtLeast(1),
                fontScale = fontScale,
                fullscreen = fullscreen,
            )
            val useRail = accessibilityLayout.navigationMode == ShellNavigationMode.Rail &&
                drawerMode == ShellDrawerMode.Modal
            @Composable
            fun ShellScaffold() {
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
                    if (showNavigation && !useRail && drawerMode == ShellDrawerMode.Modal) {
                        CodecksBottomBar(
                            currentRoute = currentRoute,
                            destinations = shellDestinations,
                            onDestinationSelected = onDestinationSelected,
                            onOpenDrawer = { openDrawer() },
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
                                onOpenDrawer = { openDrawer() },
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
            key(currentRoute) {
                if (showNavigation && drawerMode == ShellDrawerMode.Permanent) {
                    PermanentNavigationDrawer(
                        drawerContent = {
                            CodecksPermanentNavigationDrawerContent(
                                currentRoute = currentRoute,
                                destinations = shellDestinations,
                                actions = visibleDeckActions,
                                selectedActionId = selectedActionId,
                                actionRunning = actionRunning,
                                onDestinationSelected = onDestinationSelected,
                                onAction = onAction,
                                onDismiss = {},
                            )
                        },
                    ) {
                        ShellScaffold()
                    }
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = false,
                        drawerContent = {
                            if (showNavigation) {
                                CodecksNavigationDrawerContent(
                                    currentRoute = currentRoute,
                                    destinations = shellDestinations,
                                    actions = visibleDeckActions,
                                    selectedActionId = selectedActionId,
                                    actionRunning = actionRunning,
                                    onDestinationSelected = onDestinationSelected,
                                    onAction = onAction,
                                    onDismiss = { closeDrawer() },
                                )
                            }
                        },
                    ) {
                        ShellScaffold()
                    }
                }
            }
        }
        if (fullscreen) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                IconButton(onClick = onStopInput) {
                    Icon(Icons.Outlined.StopCircle, contentDescription = "Stop input")
                }
                IconButton(onClick = onExitFullscreen) {
                    Icon(Icons.Outlined.FullscreenExit, contentDescription = "Exit fullscreen")
                }
            }
        }
    }
}

private typealias ShellDestination = RouteDescriptor<out AppRoute>

private fun shellDestinations(
    tabs: List<RouteDescriptor<out AppRoute>>,
    buildExposure: RouteBuildExposure,
): List<ShellDestination> =
    tabs + RouteRegistry.visibleDestinations(buildExposure).filter { it.visibility == RouteVisibility.SECONDARY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodecksBottomBar(
    currentRoute: NavKey,
    destinations: List<ShellDestination>,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 2f
    val pinned = destinations
        .filter { it.visibility == RouteVisibility.PRIMARY }
        .sortedBy { it.navigationOrder }
        .take(4)

    NavigationBar(
        tonalElevation = 3.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        pinned.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination.route) },
                icon = { Icon(destination.icon, contentDescription = if (largeText) destination.label else null) },
                label = if (largeText) null else {{
                    Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }},
                modifier = Modifier.testTag(destination.testTag),
            )
        }
        NavigationBarItem(
            selected = currentRoute !in pinned.map { it.route },
            onClick = onOpenDrawer,
            icon = { Icon(Icons.Outlined.Menu, contentDescription = if (largeText) "All" else null) },
            label = if (largeText) null else {{ Text("All") }},
            modifier = Modifier.testTag("navigation-all"),
        )
    }
}

private fun handleShellKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    fullscreen: Boolean,
    canNavigateBack: Boolean,
    focusManager: FocusManager,
    onBack: () -> Unit,
    onExitFullscreen: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val key = when (event.key) {
        Key.Tab -> ShellKey.Tab
        Key.Enter,
        Key.NumPadEnter,
        -> ShellKey.Enter
        Key.Spacebar -> ShellKey.Space
        Key.Back -> ShellKey.Back
        Key.Escape -> ShellKey.Escape
        else -> ShellKey.Other
    }
    return when (shellKeyAction(key, event.isShiftPressed, fullscreen, canNavigateBack)) {
        ShellKeyAction.FocusNext -> focusManager.moveFocus(FocusDirection.Next)
        ShellKeyAction.FocusPrevious -> focusManager.moveFocus(FocusDirection.Previous)
        ShellKeyAction.ActivateFocused,
        ShellKeyAction.PassThrough,
        -> false
        ShellKeyAction.NavigateBack -> {
            onBack()
            true
        }
        ShellKeyAction.ExitFullscreen -> {
            onExitFullscreen()
            true
        }
    }
}

@Composable
private fun CodecksNavigationRail(
    currentRoute: NavKey,
    destinations: List<ShellDestination>,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .width(96.dp)
            .verticalScroll(rememberScrollState()),
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
                modifier = Modifier.testTag(destination.testTag),
            )
        }
        NavigationRailItem(
            selected = false,
            onClick = onOpenDrawer,
            icon = { Icon(Icons.Outlined.Menu, contentDescription = "All") },
            label = { Text("All") },
            modifier = Modifier.testTag("navigation-all"),
        )
    }
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
            if (currentRoute != SettingsRoute && RouteRegistry.primaryDestinations().none { it.route == currentRoute }) {
                IconButton(onClick = onOpenSettings) {
                    val settings = requireNotNull(RouteRegistry.descriptor(SettingsRoute))
                    Icon(settings.icon, contentDescription = settings.label)
                }
            }
        },
    )
}
