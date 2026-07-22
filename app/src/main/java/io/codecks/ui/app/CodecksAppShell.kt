package io.codecks.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    tabs: List<PrimaryTab> = PrimaryTab.entries,
    onBack: () -> Unit,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val currentTab = tabs.firstOrNull { it.route == currentRoute } ?: PrimaryTab.entries.firstOrNull { it.route == currentRoute }
    val showBottomBar = !fullscreen && currentTab != null

    Box(modifier = Modifier.fillMaxSize()) {
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
                if (showBottomBar) {
                    CodecksBottomBar(
                        currentTab = currentTab,
                        tabs = tabs,
                        onTabSelected = { tab -> onDestinationSelected(tab.route) },
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
                content(contentPadding)
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

@Composable
private fun CodecksBottomBar(
    currentTab: PrimaryTab,
    tabs: List<PrimaryTab>,
    onTabSelected: (PrimaryTab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 18.dp),
        ) {
            tabs.forEach { tab ->
                val selected = currentTab == tab
                Surface(
                    onClick = { onTabSelected(tab) },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).height(70.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                    ) {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
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
