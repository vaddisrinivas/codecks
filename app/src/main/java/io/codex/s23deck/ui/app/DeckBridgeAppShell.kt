package io.codex.s23deck.ui.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import io.codex.s23deck.navigation.AiBuilderRoute
import io.codex.s23deck.navigation.ConnectionRoute
import io.codex.s23deck.navigation.EditorRoute
import io.codex.s23deck.navigation.HomeRoute
import io.codex.s23deck.navigation.MouseRoute
import io.codex.s23deck.navigation.title
import io.codex.s23deck.ui.connection.ConnectionHealth
import io.codex.s23deck.ui.connection.CodecksReadiness
import io.codex.s23deck.ui.connection.isReady
import io.codex.s23deck.ui.connection.statusLabel
import io.codex.s23deck.ui.designsystem.CodecksDeckEdgeGlowBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckBridgeAppShell(
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    destinations: List<AppDestination>,
    currentRoute: NavKey,
    backStackSize: Int,
    fullscreen: Boolean,
    connectionHealth: ConnectionHealth,
    readiness: CodecksReadiness,
    deckEditorEnabled: Boolean,
    aiBuilderEnabled: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenConnection: () -> Unit,
    onOpenEditor: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val drawerScope = rememberCoroutineScope()
    fun closeDrawer() {
        drawerScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !fullscreen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                CodecksDrawerContent(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    connectionHealth = connectionHealth,
                    readiness = readiness,
                    deckEditorEnabled = deckEditorEnabled,
                    aiBuilderEnabled = aiBuilderEnabled,
                    onDestinationSelected = { route ->
                        onDestinationSelected(route)
                        closeDrawer()
                    },
                    onOpenConnection = {
                        onOpenConnection()
                        closeDrawer()
                    },
                    onOpenEditor = {
                        onOpenEditor()
                        closeDrawer()
                    },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = if (fullscreen || currentRoute == HomeRoute) {
                    {}
                } else {
                    {
                        DeckBridgeTopBar(
                            currentRoute = currentRoute,
                            backStackSize = backStackSize,
                            connectionHealth = connectionHealth,
                            deckEditorEnabled = deckEditorEnabled,
                            onBack = onBack,
                            onOpenDrawer = onOpenDrawer,
                            onOpenConnection = onOpenConnection,
                            onOpenEditor = onOpenEditor,
                            onToggleFullscreen = onToggleFullscreen,
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
}

@Composable
private fun CodecksDrawerContent(
    destinations: List<AppDestination>,
    currentRoute: NavKey,
    connectionHealth: ConnectionHealth,
    readiness: CodecksReadiness,
    deckEditorEnabled: Boolean,
    aiBuilderEnabled: Boolean,
    onDestinationSelected: (NavKey) -> Unit,
    onOpenConnection: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CodecksDeckEdgeGlowBackground(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            DrawerHeader(readiness = readiness, onOpenConnection = onOpenConnection)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                DrawerSectionLabel("Make")
                if (deckEditorEnabled) {
                    CodecksDrawerItem(
                        label = "Create button",
                        summary = "Emoji, blank, Mac action",
                        icon = Icons.Outlined.Palette,
                        selected = currentRoute == EditorRoute,
                        onClick = onOpenEditor,
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (aiBuilderEnabled) {
                    CodecksDrawerItem(
                        label = "AI builder",
                        summary = "Draft buttons and rules",
                        icon = Icons.Outlined.AutoAwesome,
                        selected = currentRoute == AiBuilderRoute,
                        onClick = { onDestinationSelected(AiBuilderRoute) },
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                }

                DrawerSectionLabel("Core")
                destinations.forEach { destination ->
                    CodecksDrawerItem(
                        label = destination.label,
                        summary = destination.summary,
                        icon = destination.icon,
                        selected = destination.isSelectedFor(currentRoute),
                        onClick = { onDestinationSelected(destination.route) },
                    )
                }

                DrawerSectionLabel("Customize")
                if (deckEditorEnabled) {
                    CodecksDrawerItem(
                        label = "Edit deck",
                        summary = "Move and resize keys",
                        icon = Icons.Outlined.Edit,
                        selected = currentRoute == EditorRoute,
                        onClick = onOpenEditor,
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            DrawerFooter()
        }
    }
}

@Composable
private fun DrawerHeader(
    readiness: CodecksReadiness,
    onOpenConnection: () -> Unit,
) {
    val statusColor = if (readiness.coreReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onOpenConnection),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Surface(
                color = statusColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ck", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Codecks", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (readiness.coreReady) "Ready" else "Setup optional for making decks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun CodecksDrawerItem(
    label: String,
    summary: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = 0.13f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "drawerItemContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.72f) else accent.copy(alpha = 0.16f),
        label = "drawerItemBorder",
    )
    val shape = RoundedCornerShape(16.dp)
    Surface(
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Surface(
                color = accent.copy(alpha = if (selected) 0.20f else 0.10f),
                contentColor = accent.copy(alpha = if (selected) 1f else 0.82f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (selected) {
                StatusDot(color = accent)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun DrawerFooter() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    Text(
        text = "Local-only v1 • no Codecks account or cloud database",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
    )
}

private fun AppDestination.isSelectedFor(currentRoute: NavKey): Boolean =
    currentRoute == route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckBridgeTopBar(
    currentRoute: NavKey,
    backStackSize: Int,
    connectionHealth: ConnectionHealth,
    deckEditorEnabled: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenEditor: () -> Unit,
    onToggleFullscreen: () -> Unit,
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
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open navigation")
                }
            }
        },
        actions = {
            if (currentRoute == HomeRoute) {
                IconButton(onClick = onOpenConnection) {
                    Icon(
                        imageVector = if (connectionHealth.isReady) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.Link
                        },
                        contentDescription = connectionHealth.title,
                    )
                }
                if (deckEditorEnabled) {
                    IconButton(onClick = onOpenEditor) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit deck")
                    }
                }
            }
            if (currentRoute == MouseRoute) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = Icons.Outlined.Fullscreen,
                        contentDescription = "Toggle fullscreen",
                    )
                }
            }
        },
    )
}
