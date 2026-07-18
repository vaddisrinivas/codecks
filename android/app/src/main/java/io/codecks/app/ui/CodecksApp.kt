package io.codecks.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.codecks.app.data.CodecksDataContainer
import io.codecks.core.security.SensitiveChars
import io.codecks.domain.actions.ActionReceipt
import io.codecks.domain.actions.ReceiptState
import io.codecks.domain.decks.DefaultDeckFactory
import io.codecks.domain.targets.LiveState
import io.codecks.domain.targets.MacTarget
import io.codecks.domain.targets.SshIdentity
import io.codecks.domain.targets.TargetCapability
import io.codecks.domain.targets.TargetSetupDraft
import io.codecks.domain.targets.TrustState
import io.codecks.feature.connection.ConnectionSetupScreen
import io.codecks.feature.connection.ConnectionSetupUiState
import io.codecks.feature.deck.DeckScreen
import io.codecks.feature.automations.AutomationsScreen
import io.codecks.feature.settings.SettingsScreen
import io.codecks.feature.trackpad.KeyboardScreen
import io.codecks.feature.trackpad.TrackpadScreen
import io.codecks.transport.ssh.SshPublicKeyInstallResult
import kotlinx.coroutines.launch

@Composable
fun CodecksApp() {
    val appContext = LocalContext.current.applicationContext
    val dataContainer = remember(appContext) { CodecksDataContainer.get(appContext) }
    var selectedRouteId: String by rememberSaveable { mutableStateOf(AppRoute.Deck.id) }
    var keyboardReturnRouteId: String by rememberSaveable { mutableStateOf(AppRoute.Deck.id) }
    var firstRunState: String by rememberSaveable { mutableStateOf("first-run") }
    val selectedRoute = AppRoute.fromId(selectedRouteId)
    val openKeyboard: (AppRoute) -> Unit = { origin ->
        keyboardReturnRouteId = AppRoute.keyboardReturnTarget(origin.id).id
        selectedRouteId = AppRoute.Keyboard.id
    }
    val closeKeyboard = {
        selectedRouteId = AppRoute.keyboardReturnTarget(keyboardReturnRouteId).id
    }

    when (firstRunState) {
        "first-run" -> {
            FirstRunScreen(
                onConnect = { firstRunState = "connect" },
                onTryDemo = { firstRunState = "done" },
            )
            return
        }
        "connect" -> {
            ConnectionSetupRoute(
                dataContainer = dataContainer,
                onTryDemo = { firstRunState = "done" },
                onFinish = { firstRunState = "done" },
            )
            return
        }
    }

    BackHandler(enabled = selectedRoute != AppRoute.Deck) {
        if (selectedRoute == AppRoute.Keyboard) {
            closeKeyboard()
        } else {
            selectedRouteId = AppRoute.Deck.id
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val width = maxWidth
        when {
            width >= 960.dp -> PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(256.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        DrawerHeader()
                        AppRoute.topLevel.forEach { route ->
                            NavigationDrawerItem(
                                selected = route == selectedRoute,
                                onClick = { selectedRouteId = route.id },
                                icon = { Icon(route.icon, contentDescription = null) },
                                label = { Text(route.label) },
                            )
                        }
                    }
                },
            ) {
                RouteContent(
                    route = selectedRoute,
                    onNavigate = { selectedRouteId = it.id },
                    onOpenKeyboard = openKeyboard,
                    onCloseKeyboard = closeKeyboard,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            width >= 600.dp -> Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Spacer(Modifier.weight(1f))
                    AppRoute.topLevel.forEach { route ->
                        NavigationRailItem(
                            selected = route == selectedRoute,
                            onClick = { selectedRouteId = route.id },
                            icon = { Icon(route.icon, contentDescription = route.label) },
                            label = { Text(route.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                RouteContent(
                    route = selectedRoute,
                    onNavigate = { selectedRouteId = it.id },
                    onOpenKeyboard = openKeyboard,
                    onCloseKeyboard = closeKeyboard,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> RouteContent(
                route = selectedRoute,
                onNavigate = { selectedRouteId = it.id },
                onOpenKeyboard = openKeyboard,
                onCloseKeyboard = closeKeyboard,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FirstRunScreen(
    onConnect: () -> Unit,
    onTryDemo: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "Codecks",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Your Mac controls, under your hand.",
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Computer, contentDescription = null)
                    Text("Connect my Mac", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onTryDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text("Try the demo Deck")
                }
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null)
                    Text(
                        text = "No account. No Codecks cloud.",
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = "Codecks",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Local Mac control",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionSetupRoute(
    dataContainer: CodecksDataContainer,
    onTryDemo: () -> Unit,
    onFinish: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var draft by remember {
        mutableStateOf(
            TargetSetupDraft(
                targetName = "MacBook",
                host = "192.168.1.42",
                username = "user",
                fingerprintSha256 = "SHA256:6E9140AC7729",
            ),
        )
    }
    var phonePublicKey by remember { mutableStateOf<String?>(null) }
    var installPassword by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }
    var lastReceipt by remember { mutableStateOf<ActionReceipt?>(null) }

    LaunchedEffect(dataContainer, draft.targetName) {
        phonePublicKey = runCatching {
            dataContainer.sshCredentialProvider.publicKeyFor(draft.keyAliasTarget())
        }.getOrElse {
            installError = "Could not create phone key."
            null
        }
    }

    ConnectionSetupScreen(
        state = ConnectionSetupUiState.fromDraft(
            draft = draft,
            phonePublicKey = phonePublicKey,
            installPassword = installPassword,
            installError = installError,
            lastReceipt = lastReceipt,
        ),
        onTargetNameChanged = { draft = draft.copy(targetName = it) },
        onAddressChanged = { draft = draft.withAddress(it) },
        onUsernameChanged = { draft = draft.copy(username = it) },
        onFindAddress = onTryDemo,
        onTrustFingerprint = {
            installError = null
            draft = runCatching { draft.trustFingerprint() }.getOrElse { current -> draft }
        },
        onInstallPasswordChanged = { installPassword = it },
        onCopyPublicKey = {
            phonePublicKey?.let { key ->
                val clipboard = appContext.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Codecks phone public key", key))
            }
        },
        onInstallKey = {
            val publicKey = phonePublicKey ?: return@ConnectionSetupScreen
            scope.launch {
                installError = null
                val password = SensitiveChars.copyOf(installPassword)
                try {
                    when (
                        val result = dataContainer.publicKeyInstaller.install(
                            target = draft.toTrustedSshTarget(),
                            publicKey = publicKey,
                            password = password,
                        )
                    ) {
                        is SshPublicKeyInstallResult.Installed -> {
                            draft = draft.installPhoneKey(publicKey)
                            installPassword = ""
                        }
                        is SshPublicKeyInstallResult.Failed -> installError = result.safeMessage
                    }
                } finally {
                    password.close()
                }
            }
        },
        onMarkKeyInstalled = {
            phonePublicKey?.let { publicKey ->
                installError = null
                installPassword = ""
                draft = draft.installPhoneKey(publicKey)
            }
        },
        onTestFinder = {
            scope.launch {
                val receipt = dataContainer.firstFinderProof(draft.toMacTarget(liveState = LiveState.ONLINE))
                lastReceipt = receipt
                if (receipt.state == ReceiptState.SUCCESS) {
                    dataContainer.currentTarget = draft.toMacTarget(liveState = LiveState.ONLINE)
                    draft = draft.markFinderProofSucceeded()
                    onFinish()
                }
            }
        },
    )
}

@Composable
private fun RouteContent(
    route: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onOpenKeyboard: (AppRoute) -> Unit,
    onCloseKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val dataContainer = remember(appContext) { CodecksDataContainer.get(appContext) }
    val defaultDeck = remember { DefaultDeckFactory.mainDeck() }
    val decks by dataContainer.deckRepository.observeDecks().collectAsState(initial = listOf(defaultDeck))

    LaunchedEffect(dataContainer, defaultDeck) {
        if (dataContainer.deckRepository.getDeck(defaultDeck.id) == null) {
            dataContainer.deckRepository.upsertDeck(defaultDeck)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (route) {
            AppRoute.Deck -> DeckScreen(
                deck = decks.firstOrNull() ?: defaultDeck,
                onOpenTrackpad = { onNavigate(AppRoute.Trackpad) },
                onOpenKeyboard = { onOpenKeyboard(AppRoute.Deck) },
            )

            AppRoute.Trackpad -> TrackpadScreen(
                onExit = { onNavigate(AppRoute.Deck) },
                onOpenKeyboard = { onOpenKeyboard(AppRoute.Trackpad) },
            )

            AppRoute.Keyboard -> KeyboardScreen(onClose = onCloseKeyboard)
            AppRoute.Automations -> AutomationsScreen()
            AppRoute.Settings -> SettingsScreen()
        }
    }
}

private fun TargetSetupDraft.withAddress(rawAddress: String): TargetSetupDraft {
    val trimmed = rawAddress.trim()
    val lastColon = trimmed.lastIndexOf(':')
    val parsedPort = if (lastColon > 0) trimmed.substring(lastColon + 1).toIntOrNull() else null
    return if (parsedPort != null) {
        copy(host = trimmed.substring(0, lastColon), port = parsedPort)
    } else {
        copy(host = trimmed)
    }
}

private fun TargetSetupDraft.keyAliasTarget(): MacTarget = MacTarget(
    logicalId = "current-mac",
    displayName = targetName.ifBlank { "Mac" },
    sshIdentity = if (hasAddress) {
        SshIdentity(
            host = host,
            port = port,
            username = username,
            hostFingerprintSha256 = fingerprintSha256,
            phonePublicKey = phonePublicKey,
        )
    } else {
        null
    },
    capabilities = setOf(TargetCapability.SSH_APP_CONTROL, TargetCapability.SSH_SYSTEM_CONTROL),
    trustState = if (fingerprintTrusted) TrustState.TRUSTED else TrustState.UNTRUSTED,
    liveState = LiveState.CONFIGURED,
)
