package io.codex.s23deck

import android.Manifest
import android.app.ActivityManager
import android.os.Bundle
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import io.codex.s23deck.core.actions.ActionRunner
import io.codex.s23deck.core.trackpad.TrackpadSettings
import io.codex.s23deck.core.trackpad.TrackpadSettingsRepository
import io.codex.s23deck.domain.ActionKind
import io.codex.s23deck.domain.ActionStatus
import io.codex.s23deck.domain.DeckAction
import io.codex.s23deck.data.ai.AndroidSecureApiKeyStore
import io.codex.s23deck.data.ai.AiProviderFactory
import io.codex.s23deck.data.clipboard.ClipboardSettingsRepository
import io.codex.s23deck.data.clipboard.ClipboardSyncSettings
import io.codex.s23deck.data.ConnectionRepository
import io.codex.s23deck.data.CodecksBackupRepository
import io.codex.s23deck.data.commerce.LocalFeatureFlagRepository
import io.codex.s23deck.data.context.ContextAppInventory
import io.codex.s23deck.data.context.ContextAppRankingGate
import io.codex.s23deck.data.context.ContextDeckInteractionStore
import io.codex.s23deck.data.context.ContextDeckWidgetState
import io.codex.s23deck.data.context.DeviceSurfaceContextSource
import io.codex.s23deck.data.context.UsageStatsContextSource
import io.codex.s23deck.data.notifications.NotificationPreview
import io.codex.s23deck.data.notifications.NotificationPrivacySettings
import io.codex.s23deck.data.notifications.NotificationPrivacySettingsRepository
import io.codex.s23deck.data.notifications.PhoneNotificationBackplane
import io.codex.s23deck.domain.ai.AiProviderCatalog
import io.codex.s23deck.domain.ai.DraftKind
import io.codex.s23deck.domain.ai.DraftRequest
import io.codex.s23deck.domain.clipboard.ClipboardSyncMode
import io.codex.s23deck.navigation.ActivityRoute
import io.codex.s23deck.navigation.AdvancedRoute
import io.codex.s23deck.navigation.AppearanceRoute
import io.codex.s23deck.navigation.AutomationsRoute
import io.codex.s23deck.navigation.BluetoothRoute
import io.codex.s23deck.navigation.ClipboardRoute
import io.codex.s23deck.navigation.ConnectionRoute
import io.codex.s23deck.navigation.ContextDeckRoute
import io.codex.s23deck.navigation.AiBuilderRoute
import io.codex.s23deck.navigation.AiProviderRoute
import io.codex.s23deck.navigation.DevicesRoute
import io.codex.s23deck.navigation.EditorRoute
import io.codex.s23deck.navigation.HomeRoute
import io.codex.s23deck.navigation.KeyboardRoute
import io.codex.s23deck.navigation.MouseRoute
import io.codex.s23deck.navigation.PremiumRoute
import io.codex.s23deck.navigation.SettingsRoute
import io.codex.s23deck.navigation.WidgetRoute
import io.codex.s23deck.navigation.title
import io.codex.s23deck.ui.connection.ConnectionScreen
import io.codex.s23deck.ui.connection.ConnectionSetupController
import io.codex.s23deck.ui.connection.ConnectionViewModel
import io.codex.s23deck.ui.connection.connectionHealth
import io.codex.s23deck.ui.connection.codecksReadiness
import io.codex.s23deck.ui.connection.hidHealth
import io.codex.s23deck.ui.connection.isReady
import io.codex.s23deck.ui.contextdeck.ContextDeckScreen
import io.codex.s23deck.ui.activity.ActivityItem
import io.codex.s23deck.ui.activity.ActivityResult
import io.codex.s23deck.ui.activity.ActivityScreen
import io.codex.s23deck.ui.activity.ActivityUiState
import io.codex.s23deck.ui.advanced.AdvancedDiagnosticsState
import io.codex.s23deck.ui.advanced.AdvancedScreen
import io.codex.s23deck.ui.automations.AutomationsScreen
import io.codex.s23deck.ui.automations.AutomationsViewModel
import io.codex.s23deck.ui.app.destinationRequestToRoute
import io.codex.s23deck.ui.app.DeckBridgeAppShell
import io.codex.s23deck.ui.app.guardRoute
import io.codex.s23deck.ui.app.mainDestinations
import io.codex.s23deck.ui.ai.AiWorkspaceMode
import io.codex.s23deck.ui.ai.AiProviderSettingsRoute
import io.codex.s23deck.ui.commerce.PremiumRoute as PremiumScreenRoute
import io.codex.s23deck.ui.clipboard.ClipboardScreen
import io.codex.s23deck.ui.clipboard.ClipboardViewModel
import io.codex.s23deck.ui.editor.DeckEditorScreen
import io.codex.s23deck.ui.home.HomeScreen
import io.codex.s23deck.ui.home.HomeViewModel
import io.codex.s23deck.ui.keyboard.KeyboardScreen
import io.codex.s23deck.ui.keyboard.KeyboardViewModel
import io.codex.s23deck.ui.mouse.MouseScreen
import io.codex.s23deck.ui.mouse.MouseViewModel
import io.codex.s23deck.ui.settings.BluetoothSetupScreen
import io.codex.s23deck.ui.settings.SettingsScreen
import io.codex.s23deck.ui.settings.DevicesScreen
import io.codex.s23deck.ui.settings.DevicesViewModel
import io.codex.s23deck.ui.settings.AppearanceScreen
import io.codex.s23deck.ui.settings.WidgetScreen
import io.codex.s23deck.ui.theme.CodecksDeckStyle
import io.codex.s23deck.ui.theme.CodecksIconPack
import io.codex.s23deck.ui.theme.DeckBridgeAccent
import io.codex.s23deck.ui.theme.DeckBridgeBorderStyle
import io.codex.s23deck.ui.theme.DeckBridgeShapeStyle
import io.codex.s23deck.ui.theme.DeckBridgeSurfaceStyle
import io.codex.s23deck.ui.theme.DeckBridgeThemeMode
import io.codex.s23deck.ui.theme.DeckBridgeThemeSettings
import io.codex.s23deck.ui.theme.DeckBridgeTheme
import io.codex.s23deck.ui.theme.ThemeSettingsRepository
import io.codex.s23deck.ui.theme.resolveForCodecksRelease
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import io.codex.s23deck.domain.commerce.FeatureFlag
import io.codex.s23deck.domain.commerce.FeatureFlaggedEntitlementRepository
import io.codex.s23deck.domain.commerce.DEFAULT_FEATURE_FLAGS
import io.codex.s23deck.domain.commerce.AccountRepository
import io.codex.s23deck.domain.commerce.BillingRepository
import io.codex.s23deck.domain.commerce.EntitlementRepository
import io.codex.s23deck.domain.commerce.LocalOnlyAccountRepository
import io.codex.s23deck.domain.commerce.LocalOnlyBillingRepository
import io.codex.s23deck.domain.commerce.LocalOnlyEntitlementRepository
import io.codex.s23deck.domain.context.AiContextRanker
import io.codex.s23deck.domain.context.ContextApp
import io.codex.s23deck.domain.context.ContextAppPromptBuilder
import io.codex.s23deck.domain.context.ContextAppRanker
import io.codex.s23deck.domain.context.ContextAppSuggestionParser
import io.codex.s23deck.domain.context.ContextDeckTileRanker
import io.codex.s23deck.domain.context.RankedContextApp
import io.codex.s23deck.domain.context.RecentContextApp
import io.codex.s23deck.domain.context.SurfaceContext
import io.codex.s23deck.domain.context.UserContextSnapshot
import io.codex.s23deck.domain.privacy.DiagnosticRedactor
import io.codex.s23deck.BuildConfig
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

private const val CONTEXT_APP_RANK_INTERVAL_MS = 15 * 60 * 1000L
private const val CONTEXT_APP_RANK_MODEL_ID = "gpt-5.5"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var hidRepository: HidRepository
    @Inject lateinit var actionRunner: ActionRunner
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var backupRepository: CodecksBackupRepository

    private var destinationRequest by mutableStateOf<String?>(null)
    private var hardwareKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        warmHidIfAllowed()
        acceptIntent(intent)
        enableEdgeToEdge()
        setContent {
            val appContext = LocalContext.current.applicationContext
            val themeSettingsRepository = remember(appContext) { ThemeSettingsRepository(appContext) }
            val themeScope = rememberCoroutineScope()
            val themeSettings by themeSettingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = DeckBridgeThemeSettings(),
            )
            LaunchedEffect(themeSettingsRepository) {
                themeSettingsRepository.migrateToCurrentVisualSystem()
            }
            val effectiveThemeSettings = themeSettings.resolveForCodecksRelease(
                customizationEnabled = !BuildConfig.LOCAL_ONLY_V1,
            )
            DeckBridgeTheme(settings = effectiveThemeSettings) {
                DeckBridgeApp(
                    destinationRequest = destinationRequest,
                    window = window,
                    hidRepository = hidRepository,
                    actionRunner = actionRunner,
                    connectionRepository = connectionRepository,
                    backupRepository = backupRepository,
                    themeSettings = themeSettings,
                    onThemeModeChange = { mode -> themeScope.launch { themeSettingsRepository.setMode(mode) } },
                    onThemeAccentChange = { accent -> themeScope.launch { themeSettingsRepository.setAccent(accent) } },
                    onThemeSurfaceStyleChange = { style -> themeScope.launch { themeSettingsRepository.setSurfaceStyle(style) } },
                    onThemeBorderStyleChange = { style -> themeScope.launch { themeSettingsRepository.setBorderStyle(style) } },
                    onThemeShapeStyleChange = { style -> themeScope.launch { themeSettingsRepository.setShapeStyle(style) } },
                    onDeckStyleChange = { style -> themeScope.launch { themeSettingsRepository.setDeckStyle(style) } },
                    onIconPackChange = { iconPack -> themeScope.launch { themeSettingsRepository.setIconPack(iconPack) } },
                    onRequestConsumed = { destinationRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        warmHidIfAllowed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (hardwareKeyHandler?.invoke(event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    fun setHardwareKeyHandler(handler: ((KeyEvent) -> Boolean)?) {
        hardwareKeyHandler = handler
    }

    private fun acceptIntent(intent: Intent?) {
        destinationRequest = resolveDestinationRequest(
            action = intent?.action,
            type = intent?.type,
            destination = intent?.getStringExtra(EXTRA_DESTINATION),
            providedToken = intent?.getStringExtra(InternalIntentAuth.EXTRA_TOKEN),
            expectedToken = InternalIntentAuth.token(this),
        )
    }

    private fun warmHidIfAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            hidRepository.start()
            HidSessionService.start(this)
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "io.codex.s23deck.DESTINATION"
    }
}

private fun Map<FeatureFlag, Boolean>.focusedEnabled(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)

private data class CommerceRuntime(
    val accountRepository: AccountRepository,
    val billingRepository: BillingRepository,
    val entitlementRepository: EntitlementRepository,
)

private fun DeckAction.visibleForFlags(flags: Map<FeatureFlag, Boolean>): Boolean = when (kind) {
    ActionKind.Ssh -> true
    ActionKind.Local -> id in setOf("add_button", "blank") ||
        route in setOf("trackpad", "automations", "ai", "button_picker", "empty_slot", "layout_builder", "drawer") ||
        (route in setOf("keyboard", "text") && flags.focusedEnabled(FeatureFlag.Keyboard)) ||
        (route == "clipboard" && flags.focusedEnabled(FeatureFlag.Clipboard)) ||
        (id == "clipboard" && flags.focusedEnabled(FeatureFlag.Clipboard)) ||
        (route == "settings" && flags.focusedEnabled(FeatureFlag.Settings)) ||
        (route == "setup_scan" && flags.focusedEnabled(FeatureFlag.Connection)) ||
        (route == "list_apps_panel" && flags.focusedEnabled(FeatureFlag.Advanced))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckBridgeApp(
    destinationRequest: String?,
    window: android.view.Window,
    hidRepository: HidRepository,
    actionRunner: ActionRunner,
    connectionRepository: ConnectionRepository,
    backupRepository: CodecksBackupRepository,
    themeSettings: DeckBridgeThemeSettings,
    onThemeModeChange: (DeckBridgeThemeMode) -> Unit,
    onThemeAccentChange: (DeckBridgeAccent) -> Unit,
    onThemeSurfaceStyleChange: (DeckBridgeSurfaceStyle) -> Unit,
    onThemeBorderStyleChange: (DeckBridgeBorderStyle) -> Unit,
    onThemeShapeStyleChange: (DeckBridgeShapeStyle) -> Unit,
    onDeckStyleChange: (CodecksDeckStyle) -> Unit,
    onIconPackChange: (CodecksIconPack) -> Unit,
    onRequestConsumed: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    connectionViewModel: ConnectionViewModel = viewModel(),
    automationsViewModel: AutomationsViewModel = viewModel(),
    devicesViewModel: DevicesViewModel = viewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    val connectionHealth = connectionState.connectionHealth()
    val automationsState by automationsViewModel.uiState.collectAsStateWithLifecycle()
    val devicesState by devicesViewModel.uiState.collectAsStateWithLifecycle()
    val hidState by hidRepository.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val configuration = LocalConfiguration.current
    val desktopSurface = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK
    val featureFlagRepository = remember(appContext) { LocalFeatureFlagRepository(appContext) }
    val featureFlags by featureFlagRepository.flags.collectAsStateWithLifecycle(initialValue = emptyMap())
    val widgetFeaturesEnabled = featureFlags.focusedEnabled(FeatureFlag.Widget)
    val contextFeaturesEnabled = featureFlags.focusedEnabled(FeatureFlag.ContextDeck) || widgetFeaturesEnabled
    val phoneNotificationFlow = remember(contextFeaturesEnabled) {
        if (contextFeaturesEnabled) {
            PhoneNotificationBackplane.notifications
        } else {
            flowOf(emptyList<NotificationPreview>())
        }
    }
    val phoneNotifications by phoneNotificationFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val backStack = rememberNavBackStack(HomeRoute)
    val currentRoute = backStack.lastOrNull() ?: HomeRoute
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingBackupPayload by remember { mutableStateOf<String?>(null) }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val payload = pendingBackupPayload
        pendingBackupPayload = null
        if (uri != null && payload != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver.openOutputStream(uri, "wt")
                            ?.bufferedWriter()
                            ?.use { it.write(payload) }
                            ?: error("Could not open backup file")
                    }
                }
                snackbarHostState.showSnackbar(
                    result.fold(onSuccess = { "Codecks backup saved" }, onFailure = { it.message ?: "Backup failed" }),
                )
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: error("Could not open backup file")
                    }.mapCatching { backupRepository.import(it).getOrThrow() }
                }
                snackbarHostState.showSnackbar(
                    result.fold(onSuccess = { "Deck and automations restored" }, onFailure = { it.message ?: "Restore failed" }),
                )
            }
        }
    }
    val clipboardSettingsRepository = remember(appContext) { ClipboardSettingsRepository(appContext) }
    val clipboardSettings by clipboardSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = ClipboardSyncSettings(),
    )
    val trackpadSettingsRepository = remember(appContext) { TrackpadSettingsRepository(appContext) }
    val trackpadSettings by trackpadSettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = TrackpadSettings(),
    )
    val notificationPrivacySettingsRepository = remember(appContext) { NotificationPrivacySettingsRepository(appContext) }
    val notificationPrivacySettings by notificationPrivacySettingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = NotificationPrivacySettings(),
    )
    LaunchedEffect(notificationPrivacySettings, contextFeaturesEnabled) {
        PhoneNotificationBackplane.updatePrivacySettings(
            if (contextFeaturesEnabled) {
                notificationPrivacySettings
            } else {
                NotificationPrivacySettings(showOnTrackpad = false)
            },
        )
    }
    var aiProviderReady by remember { mutableStateOf(false) }
    val bluetoothPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    val notificationAccessReady = contextFeaturesEnabled && PhoneNotificationBackplane.isEnabled(appContext)
    val laptopNotifications = homeState.activity.take(6).map { event ->
        NotificationPreview(
            id = "mac-${event.timestampMillis}-${event.actionId}",
            source = "Mac",
            title = event.label,
            text = event.message,
            postedAtMillis = event.timestampMillis,
        )
    }
    val visibleDeckSlots = homeState.actions.withIndex().filter { it.value.visibleForFlags(featureFlags) }
    val visibleDeckActions = visibleDeckSlots.map { it.value }
    val customRowActions = visibleDeckActions.filterNot { it.id in setOf("blank", "add_button") }
    var launchableApps by remember { mutableStateOf<List<ContextApp>>(emptyList()) }
    var recentUsageApps by remember { mutableStateOf<List<RecentContextApp>>(emptyList()) }
    var surfaceContext by remember { mutableStateOf(SurfaceContext()) }
    var contextScheduleTick by remember { mutableStateOf(0) }
    LaunchedEffect(contextFeaturesEnabled) {
        if (!contextFeaturesEnabled) return@LaunchedEffect
        while (true) {
            delay(CONTEXT_APP_RANK_INTERVAL_MS)
            contextScheduleTick += 1
        }
    }
    LaunchedEffect(appContext, contextScheduleTick, contextFeaturesEnabled) {
        if (!contextFeaturesEnabled) return@LaunchedEffect
        val surface = DeviceSurfaceContextSource(appContext).current()
        surfaceContext = SurfaceContext(
            label = surface.label,
            desktopMode = surface.kind.name == "Desktop",
            externalDisplayConnected = surface.externalDisplayConnected,
            keyboardConnected = surface.keyboardConnected,
            pointerConnected = surface.pointerConnected,
        )
    }
    val contextSnapshot = remember(contextFeaturesEnabled, homeState.activeMacApp, homeState.connectionReady, phoneNotifications, recentUsageApps, surfaceContext, contextScheduleTick) {
        if (contextFeaturesEnabled) {
            UserContextSnapshot(
                activeMacApp = homeState.activeMacApp,
                macConnected = homeState.connectionReady,
                notificationSources = phoneNotifications.map { it.source },
                recentApps = recentUsageApps,
                surface = surfaceContext,
                hourOfDay = LocalDateTime.now().hour,
            )
        } else {
            UserContextSnapshot(
                activeMacApp = homeState.activeMacApp,
                macConnected = homeState.connectionReady,
                notificationSources = emptyList(),
                recentApps = emptyList(),
                surface = SurfaceContext(),
                hourOfDay = LocalDateTime.now().hour,
            )
        }
    }
    val contextRankedActions = remember(contextFeaturesEnabled, contextSnapshot, visibleDeckActions, homeState.allActions) {
        if (contextFeaturesEnabled) {
            AiContextRanker.rank(
                snapshot = contextSnapshot,
                actions = (visibleDeckActions + homeState.allActions).distinctBy { it.id },
            )
        } else {
            emptyList()
        }
    }
    var aiRankedApps by remember { mutableStateOf<List<RankedContextApp>?>(null) }
    var contextAppStatus by remember { mutableStateOf("Local ranking ready") }
    LaunchedEffect(appContext, contextFeaturesEnabled) {
        if (!contextFeaturesEnabled) {
            launchableApps = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            launchableApps = withContext(Dispatchers.IO) {
                ContextAppInventory(appContext).launchableApps()
            }
            delay(CONTEXT_APP_RANK_INTERVAL_MS)
        }
    }
    LaunchedEffect(appContext, launchableApps, contextFeaturesEnabled) {
        if (!contextFeaturesEnabled) {
            recentUsageApps = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            recentUsageApps = withContext(Dispatchers.IO) {
                UsageStatsContextSource(appContext).recentApps(launchableApps)
            }
            delay(CONTEXT_APP_RANK_INTERVAL_MS)
        }
    }
    val fallbackRankedApps = remember(contextFeaturesEnabled, contextSnapshot, launchableApps) {
        if (contextFeaturesEnabled) {
            ContextAppRanker.rank(contextSnapshot, launchableApps)
        } else {
            emptyList()
        }
    }
    val contextAppPrompt = remember(contextFeaturesEnabled, contextSnapshot, launchableApps) {
        if (contextFeaturesEnabled) {
            ContextAppPromptBuilder.build(contextSnapshot, launchableApps)
        } else {
            ""
        }
    }
    val explainableAppPackages = remember(fallbackRankedApps) {
        fallbackRankedApps.map { it.app.packageName }.toSet()
    }
    val safeAiRankedApps = remember(aiRankedApps, explainableAppPackages) {
        aiRankedApps
            ?.filter { it.app.packageName in explainableAppPackages }
            .orEmpty()
    }
    val contextRankedApps = if (contextFeaturesEnabled) {
        safeAiRankedApps.takeIf { it.isNotEmpty() } ?: fallbackRankedApps
    } else {
        emptyList()
    }
    val contextDeckTiles = remember(contextFeaturesEnabled, contextSnapshot, launchableApps, visibleDeckActions, homeState.allActions) {
        if (contextFeaturesEnabled) {
            ContextDeckTileRanker.rank(
                snapshot = contextSnapshot,
                apps = launchableApps,
                actions = (visibleDeckActions + homeState.allActions).distinctBy { it.id },
            )
        } else {
            emptyList()
        }
    }
    LaunchedEffect(contextAppPrompt, launchableApps, fallbackRankedApps, aiProviderReady, contextFeaturesEnabled) {
        if (!contextFeaturesEnabled) {
            aiRankedApps = null
            contextAppStatus = "Context Deck is off"
            return@LaunchedEffect
        }
        if (launchableApps.isEmpty()) {
            contextAppStatus = "Collecting installed apps…"
            return@LaunchedEffect
        }
        if (!aiProviderReady) {
            aiRankedApps = null
            contextAppStatus = "Schedule ranking active • local until AI key is added"
            return@LaunchedEffect
        }
        while (true) {
            val gate = ContextAppRankingGate(appContext).evaluate()
            if (!gate.allowed) {
                aiRankedApps = null
                contextAppStatus = "${gate.reason} • next check in 15 min"
                delay(CONTEXT_APP_RANK_INTERVAL_MS)
                continue
            }
            contextAppStatus = "Scheduled AI app ranking with GPT-5.5…"
            val keyStore = AndroidSecureApiKeyStore(appContext)
            val providerSpec = AiProviderCatalog.all.firstOrNull { spec ->
                spec.models.any { it.id == CONTEXT_APP_RANK_MODEL_ID } && keyStore.hasKey(spec.providerId)
            }
            if (providerSpec == null) {
                aiRankedApps = null
                contextAppStatus = "Schedule ranking active • local until GPT-5.5 provider key is added"
            } else {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val provider = AiProviderFactory(
                            keyStore = keyStore,
                            liteLlmBaseUrl = BuildConfig.LITELLM_BASE_URL,
                        ).create(providerSpec.providerId)
                        val raw = provider.draftAction(
                            DraftRequest(
                                prompt = contextAppPrompt,
                                modelId = CONTEXT_APP_RANK_MODEL_ID,
                                draftKind = DraftKind.ContextApps,
                            ),
                        ).getOrThrow()
                        val locallyExplainedPackages = fallbackRankedApps.map { it.app.packageName }.toSet()
                        ContextAppSuggestionParser.parse(raw.json, launchableApps)
                            .filter { it.app.packageName in locallyExplainedPackages }
                            .take(8)
                    }
                }
                result
                    .onSuccess { ranked ->
                        aiRankedApps = ranked
                        contextAppStatus = if (ranked.isEmpty()) {
                            "Scheduled AI had no locally explainable app matches • using local"
                        } else {
                            "Scheduled AI ranked ${ranked.size} apps"
                        }
                    }
                    .onFailure { error ->
                        aiRankedApps = null
                        contextAppStatus = "${error.message ?: "AI app ranking failed"} • using local"
                    }
            }
            delay(CONTEXT_APP_RANK_INTERVAL_MS)
        }
    }
    val localOnlyV1 = BuildConfig.LOCAL_ONLY_V1
    val paywallEnabled = false
    val commerce = remember {
        CommerceRuntime(
            accountRepository = LocalOnlyAccountRepository(),
            billingRepository = LocalOnlyBillingRepository(),
            entitlementRepository = LocalOnlyEntitlementRepository(),
        )
    }
    val entitlementRepository = remember(commerce.entitlementRepository, featureFlagRepository) {
        FeatureFlaggedEntitlementRepository(commerce.entitlementRepository, featureFlagRepository)
    }
    var pendingDangerousAction by remember { mutableStateOf<DeckAction?>(null) }
    var selectedDeckSlot by remember { mutableStateOf(0) }
    var deckDirty by remember { mutableStateOf(false) }
    var focusedDeckActionId by remember { mutableStateOf<String?>(null) }
    var pointerSensitivity by remember { mutableStateOf(1f) }
    var naturalScroll by remember { mutableStateOf(true) }
    var fullscreenOverride by remember { mutableStateOf<Boolean?>(null) }
    val fullscreen = fullscreenOverride ?: (currentRoute == MouseRoute && !desktopSurface)
    LaunchedEffect(currentRoute) {
        val keyStore = AndroidSecureApiKeyStore(appContext)
        aiProviderReady = runCatching {
            AiProviderCatalog.all.any { spec ->
                spec.models.any { it.id == CONTEXT_APP_RANK_MODEL_ID } && keyStore.hasKey(spec.providerId)
            }
        }.getOrDefault(false)
    }
    val destinations = remember(featureFlags) { mainDestinations(featureFlags) }
    val readiness = codecksReadiness(
        connectionHealth = connectionHealth,
        hidHealth = hidState.hidHealth(bluetoothPermissionGranted),
        aiReady = aiProviderReady,
    )

    DisposableEffect(currentRoute, hidState.isConnected) {
        if (currentRoute == MouseRoute && hidState.isConnected) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun navigate(route: NavKey, topLevel: Boolean = false) {
        val guardedRoute = guardRoute(route, featureFlags)
        if (topLevel) backStack.clear()
        if (backStack.lastOrNull() != guardedRoute) backStack.add(guardedRoute)
    }

    LaunchedEffect(featureFlags, currentRoute) {
        val guardedRoute = guardRoute(currentRoute, featureFlags)
        if (guardedRoute != currentRoute) {
            backStack.clear()
            backStack.add(guardedRoute)
        }
        if (currentRoute != MouseRoute) {
            fullscreenOverride = null
        }
    }

    LaunchedEffect(destinationRequest) {
        val route = destinationRequestToRoute(destinationRequest, featureFlags)
        if (destinationRequest != null) {
            navigate(route, topLevel = true)
            onRequestConsumed()
        }
    }

    LaunchedEffect(fullscreen) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    LaunchedEffect(Unit) {
        automationsViewModel.startTriggerMonitor()
    }

    LaunchedEffect(currentRoute, homeState.connectionReady, homeState.dynamicDeckEnabled) {
        if (currentRoute == HomeRoute && homeState.connectionReady && homeState.dynamicDeckEnabled) {
            while (true) {
                homeViewModel.refreshActiveMacApp()
                delay(10_000)
            }
        }
    }

    LaunchedEffect(contextSnapshot, contextRankedActions, contextRankedApps, contextFeaturesEnabled, widgetFeaturesEnabled) {
        if (!contextFeaturesEnabled && !widgetFeaturesEnabled) return@LaunchedEffect
        ContextDeckWidgetState.save(
            appContext,
            ContextDeckWidgetState.fromRanked(
                activeMacApp = contextSnapshot.activeMacApp,
                notificationCount = phoneNotifications.size,
                ranked = contextRankedActions,
                rankedApps = contextRankedApps,
                tileCount = contextDeckTiles.size,
            ),
        )
    }

    fun handleAction(action: DeckAction) {
        if (action.kind == ActionKind.Local) {
            when (action.route) {
                "trackpad" -> navigate(MouseRoute)
                "keyboard", "text" -> navigate(KeyboardRoute)
                "automations" -> navigate(AutomationsRoute)
                "clipboard" -> navigate(ClipboardRoute)
                "settings" -> navigate(SettingsRoute)
                "drawer" -> scope.launch { drawerState.open() }
                "button_picker", "empty_slot", "layout_builder" -> navigate(EditorRoute)
                "setup_scan" -> navigate(ConnectionRoute)
                "list_apps_panel" -> if (featureFlags.focusedEnabled(FeatureFlag.Advanced)) navigate(AdvancedRoute) else Unit
                else -> Unit
            }
        } else if (action.dangerous) {
            pendingDangerousAction = action
        } else {
            homeViewModel.run(action)
        }
    }

    LaunchedEffect(homeState.actionStatus) {
        val status = homeState.actionStatus
        val message = when (status) {
            is ActionStatus.Succeeded -> status.message
            is ActionStatus.Failed -> status.message
            else -> null
        }
        if (message != null) {
            val undoableDeckRemove = status is ActionStatus.Succeeded && status.actionId == "deck_remove"
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = when {
                    undoableDeckRemove -> "Undo"
                    message == "Connect your Mac first" -> "Set up"
                    else -> null
                },
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (undoableDeckRemove) {
                    homeViewModel.undoLastDeckEdit()
                } else {
                    navigate(ConnectionRoute)
                }
            }
            homeViewModel.consumeResult()
        }
    }

    LaunchedEffect(automationsState.message) {
        val message = automationsState.message ?: return@LaunchedEffect
        val undoableDelete = automationsState.pendingUndo != null && message.startsWith("Deleted ")
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (undoableDelete) "Undo" else null,
        )
        if (result == SnackbarResult.ActionPerformed && undoableDelete) {
            automationsViewModel.undoDelete()
        } else {
            automationsViewModel.consumeMessage()
        }
    }

    pendingDangerousAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDangerousAction = null },
            title = { Text(action.label) },
            text = { Text(action.description) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDangerousAction = null
                        homeViewModel.run(action)
                    },
                ) {
                    Text("Run")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDangerousAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    DeckBridgeAppShell(
        drawerState = drawerState,
        snackbarHostState = snackbarHostState,
        destinations = destinations,
        currentRoute = currentRoute,
        backStackSize = backStack.size,
        fullscreen = fullscreen,
        connectionHealth = connectionHealth,
        readiness = readiness,
        deckEditorEnabled = featureFlags.focusedEnabled(FeatureFlag.DeckEditor),
        aiBuilderEnabled = featureFlags.focusedEnabled(FeatureFlag.Ai) && featureFlags.focusedEnabled(FeatureFlag.AiBuilder),
        onBack = { backStack.removeLastOrNull() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onDestinationSelected = { route ->
            navigate(route, topLevel = true)
            scope.launch { drawerState.close() }
        },
        onOpenConnection = { navigate(ConnectionRoute) },
        onOpenEditor = { navigate(EditorRoute) },
        onToggleFullscreen = { fullscreenOverride = !fullscreen },
        onExitFullscreen = { fullscreenOverride = false },
    ) { contentPadding ->
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        HomeScreen(
                            state = homeState.copy(actions = visibleDeckActions),
                            connectionHealth = connectionHealth,
                            contentPadding = contentPadding,
                            onAction = ::handleAction,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenConnection = { navigate(ConnectionRoute) },
                            onEditDeck = { navigate(EditorRoute) },
                            onEditSlot = { slot ->
                                selectedDeckSlot = slot.coerceIn(0, homeState.actions.lastIndex.coerceAtLeast(0))
                                navigate(EditorRoute)
                            },
                            visibleSlotIndices = visibleDeckSlots.map { it.index },
                            onTestAction = homeViewModel::test,
                            onDuplicateAction = homeViewModel::duplicateAction,
                            onRemoveAction = { action -> homeViewModel.removeAction(action.id) },
                            onRemoveSlot = { slot ->
                                if (slot in homeState.actions.indices) {
                                    homeViewModel.remove(slot)
                                    deckDirty = true
                                }
                            },
                            focusedActionId = focusedDeckActionId,
                            deckStyle = themeSettings.deckStyle,
                        )
                    }
                    entry<ConnectionRoute> {
                        val credentialContext = LocalContext.current
                        val activity = remember(credentialContext) { credentialContext.findMainActivity() }
                        val setupController = remember(credentialContext, activity, connectionViewModel) {
                            ConnectionSetupController(credentialContext, activity, connectionViewModel)
                        }
                        ConnectionScreen(
                            state = connectionState,
                            contentPadding = contentPadding,
                            onHostChange = connectionViewModel::setHost,
                            onPortChange = connectionViewModel::setPort,
                            onUserChange = connectionViewModel::setUser,
                            onPasswordChange = connectionViewModel::setPassword,
                            onSelectHost = connectionViewModel::selectHost,
                            onScan = connectionViewModel::scan,
                            onVerifyHostKey = connectionViewModel::verifyHostKey,
                            onConfirmHostKey = connectionViewModel::confirmHostKey,
                            onAuthorize = connectionViewModel::authorize,
                            onRotateKey = connectionViewModel::rotateKey,
                            onSavePassword = {
                                scope.launch {
                                    setupController.savePassword()
                                }
                            },
                            onUseSavedPassword = {
                                scope.launch {
                                    setupController.useSavedPassword()
                                }
                            },
                            onTest = connectionViewModel::test,
                            onResetTrust = connectionViewModel::resetTrust,
                            onRemoveTarget = connectionViewModel::removeCurrentTarget,
                            hidHealth = hidState.hidHealth(bluetoothPermissionGranted),
                            onOpenHidSetup = { navigate(BluetoothRoute) },
                        )
                    }
                    entry<BluetoothRoute> {
                        BluetoothDestination(
                            contentPadding = contentPadding,
                            onOpenTrackpad = { navigate(MouseRoute) },
                        )
                    }
                    entry<MouseRoute> {
                        MouseDestination(
                            contentPadding = contentPadding,
                            customActions = customRowActions,
                            dynamicActions = visibleDeckActions.filter {
                                it.id !in setOf("blank", "add_button") && it !in customRowActions
                            }.take(8),
                            customActionsReady = homeState.connectionReady,
                            onCustomAction = ::handleAction,
                            selectedActionId = (homeState.actionStatus as? ActionStatus.Running)?.actionId,
                            featureFlags = featureFlags,
                            phoneNotifications = phoneNotifications,
                            laptopNotifications = laptopNotifications,
                            phoneNotificationAccessReady = notificationAccessReady,
                            phoneNotificationLaneEnabled = contextFeaturesEnabled && notificationPrivacySettings.showOnTrackpad,
                            onOpenBluetoothSettings = { navigate(BluetoothRoute) },
                            onOpenNotificationSettings = {
                                appContext.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            onExitTrackpad = { navigate(HomeRoute, topLevel = true) },
                        )
                    }
                    entry<KeyboardRoute> {
                        KeyboardDestination(
                            contentPadding = contentPadding,
                            customActions = customRowActions,
                            onCustomAction = ::handleAction,
                            selectedActionId = (homeState.actionStatus as? ActionStatus.Running)?.actionId,
                        )
                    }
                    entry<ClipboardRoute> {
                        val clipboardViewModel: ClipboardViewModel = viewModel()
                        val clipboardState by clipboardViewModel.uiState.collectAsStateWithLifecycle()
                        ClipboardScreen(
                            state = clipboardState,
                            contentPadding = contentPadding,
                            onRefreshPhone = clipboardViewModel::refreshPhone,
                            onPullFromMac = clipboardViewModel::pullFromMac,
                            onPushToMac = clipboardViewModel::pushToMac,
                            onModeChange = clipboardViewModel::setMode,
                            onIntervalChange = clipboardViewModel::setSyncIntervalMinutes,
                        )
                    }
                    entry<AutomationsRoute> {
                        AutomationsScreen(
                            state = automationsState,
                            connectionHealth = connectionHealth,
                            contentPadding = contentPadding,
                            onRunAutomation = automationsViewModel::run,
                            onTestAutomation = automationsViewModel::test,
                            onApproveAutomation = automationsViewModel::approveAndRun,
                            onToggleAutomation = automationsViewModel::toggle,
                            onDuplicateAutomation = automationsViewModel::duplicate,
                            onDeleteAutomation = automationsViewModel::delete,
                            onCheckTriggers = { automationsViewModel.checkTriggersNow() },
                            onCreateAutomation = automationsViewModel::create,
                            onEditAutomation = automationsViewModel::edit,
                            onCreateWithAi = { navigate(AiBuilderRoute) },
                        )
                    }
                    entry<ActivityRoute> {
                        ActivityScreen(
                            state = ActivityUiState(
                                items = homeState.activity.mapIndexed { index, event ->
                                    ActivityItem(
                                        id = "${event.timestampMillis}-$index",
                                        actionId = event.actionId,
                                        actionLabel = event.label,
                                        result = if (event.succeeded) ActivityResult.Succeeded else ActivityResult.Failed,
                                        occurredAt = Instant.ofEpochMilli(event.timestampMillis),
                                        message = event.message,
                                    )
                                },
                            ),
                            contentPadding = contentPadding,
                            onRetry = { id -> homeState.allActions.firstOrNull { it.id == id }?.let(::handleAction) },
                            onClear = homeViewModel::clearActivity,
                        )
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            contentPadding = contentPadding,
                            connectionReady = homeState.connectionReady,
                            connectionHealth = connectionHealth,
                            hidState = hidState,
                            bluetoothPermissionGranted = bluetoothPermissionGranted,
                            notificationAccessReady = notificationAccessReady,
                            notificationPrivacySettings = notificationPrivacySettings,
                            clipboardSettings = clipboardSettings,
                            aiProviderReady = aiProviderReady,
                            automationsReady = featureFlags.focusedEnabled(FeatureFlag.Automations) && connectionHealth.isReady,
                            onConnection = { navigate(ConnectionRoute) },
                            onBluetooth = { navigate(BluetoothRoute) },
                            onNotificationAccess = {
                                appContext.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            onNotificationPrivacyChange = { transform ->
                                scope.launch { notificationPrivacySettingsRepository.update(transform) }
                            },
                            onAutomations = { navigate(AutomationsRoute) },
                            onDevices = { navigate(DevicesRoute) },
                            onDeck = { navigate(EditorRoute) },
                            onKeyboard = { navigate(KeyboardRoute) },
                            onClipboard = { navigate(ClipboardRoute) },
                            onExportBackup = {
                                scope.launch {
                                    backupRepository.export()
                                        .onSuccess { payload ->
                                            pendingBackupPayload = payload
                                            exportBackupLauncher.launch("codecks-backup-${System.currentTimeMillis()}.json")
                                        }
                                        .onFailure { error ->
                                            snackbarHostState.showSnackbar(error.message ?: "Backup failed")
                                        }
                                }
                            },
                            onImportBackup = {
                                importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                            onClipboardModeChange = { mode ->
                                scope.launch { clipboardSettingsRepository.saveMode(mode) }
                            },
                            onClipboardIntervalChange = { minutes ->
                                scope.launch { clipboardSettingsRepository.saveIntervalMinutes(minutes) }
                            },
                            onAiBuilder = { navigate(AiProviderRoute) },
                            onContextDeck = { navigate(ContextDeckRoute) },
                            onPremium = { navigate(PremiumRoute) },
                            onWidget = { navigate(WidgetRoute) },
                            onAppearance = { navigate(AppearanceRoute) },
                            onAdvanced = { navigate(AdvancedRoute) },
                            onDebugBundle = {
                                shareDebugBundle(
                                    context = appContext,
                                    route = currentRoute.title(),
                                    homeState = homeState,
                                    connectionState = connectionState,
                                    hidState = hidState,
                                    featureFlags = featureFlags,
                                    trackpadSettings = trackpadSettings,
                                    clipboardSettings = clipboardSettings,
                                )
                            },
                            themeSettings = themeSettings,
                            onThemeModeChange = onThemeModeChange,
                            onThemeAccentChange = onThemeAccentChange,
                            onThemeSurfaceStyleChange = onThemeSurfaceStyleChange,
                            onThemeBorderStyleChange = onThemeBorderStyleChange,
                            onThemeShapeStyleChange = onThemeShapeStyleChange,
                            onDeckStyleChange = onDeckStyleChange,
                            onIconPackChange = onIconPackChange,
                            trackpadSettings = trackpadSettings,
                            onTrackpadSettingsChange = { transform ->
                                scope.launch { trackpadSettingsRepository.update(transform) }
                            },
                            showPremium = paywallEnabled,
                            localOnlyV1 = localOnlyV1,
                            debugBundleEnabled = BuildConfig.DEBUG,
                            appVersionLabel = "Version ${BuildConfig.VERSION_NAME}",
                            featureFlags = featureFlags,
                            onFeatureFlagChange = featureFlagRepository::set,
                            onResetFeatureFlags = { scope.launch { featureFlagRepository.resetDefaults() } },
                        )
                    }
                    entry<EditorRoute> {
                        DeckEditorScreen(
                            slots = homeState.deckLayout.slots.map { it.action.takeUnless { action -> action.id == "blank" } },
                            slotSpans = homeState.deckLayout.slots.map { it.columnSpan },
                            allActions = homeState.allActions.filter { it.id != "blank" },
                            selectedSlot = selectedDeckSlot,
                            contentPadding = contentPadding,
                            onSelectSlot = { selectedDeckSlot = it },
                            onAssignAction = { slot, action ->
                                homeViewModel.assign(slot, action)
                                deckDirty = true
                            },
                            onMoveAction = { from, to ->
                                homeViewModel.move(from, to)
                                selectedDeckSlot = to.coerceIn(homeState.actions.indices)
                                deckDirty = true
                            },
                            onRemoveAction = {
                                homeViewModel.remove(it)
                                deckDirty = true
                            },
                            onResizeAction = { slot, span ->
                                homeViewModel.resize(slot, span)
                                deckDirty = true
                            },
                            onTestAction = homeViewModel::test,
                            onCreateWithAi = { navigate(AiBuilderRoute) },
                            onSave = {
                                homeViewModel.saveDeck()
                                deckDirty = false
                            },
                            hasUnsavedChanges = deckDirty,
                            deckStyle = themeSettings.deckStyle,
                        )
                    }
                    entry<ContextDeckRoute> {
                        ContextDeckScreen(
                            rankedActions = contextRankedActions,
                            rankedApps = contextRankedApps,
                            signals = contextSnapshot.signals(),
                            appPrompt = contextAppPrompt,
                            appStatus = contextAppStatus,
                            contentPadding = contentPadding,
                            onAction = ::handleAction,
                            onApp = { app ->
                                ContextDeckInteractionStore.record(
                                    context = appContext,
                                    type = "context_screen_app_launch",
                                    target = app.packageName,
                                    label = app.label,
                                )
                                appContext.packageManager
                                    .getLaunchIntentForPackage(app.packageName)
                                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ?.let(appContext::startActivity)
                            },
                            onOpenAiBuilder = { navigate(AiBuilderRoute) },
                            onOpenWidget = { navigate(WidgetRoute) },
                        )
                    }
                    entry<AdvancedRoute> {
                        val config = connectionState.config
                        AdvancedScreen(
                            state = AdvancedDiagnosticsState(
                                connectionReady = config.isReady,
                                host = config.host.ifBlank { "Not configured" },
                                user = config.user,
                                port = config.port,
                                keyReady = config.hasKey,
                                hostKeyPinned = config.hostKey.isNotBlank(),
                                actionCount = homeState.allActions.size,
                                safeActionCount = homeState.allActions.count { it.liveSafe },
                                lastResult = connectionState.error ?: connectionState.message ?: "Ready for diagnostics",
                                isRunning = homeState.actionStatus is ActionStatus.Running,
                                pointerSensitivity = pointerSensitivity,
                                naturalScroll = naturalScroll,
                            ),
                            actions = homeState.allActions,
                            contentPadding = contentPadding,
                            onOpenConnection = { navigate(ConnectionRoute) },
                            onTestConnection = connectionViewModel::test,
                            onRunDiagnostics = { homeViewModel.runRaw("Diagnostics", "printf 'Host: '; hostname; printf '\\nSystem: '; sw_vers -productVersion") },
                            onRunSafeActions = { homeViewModel.runRaw("Safe action check", "printf 'Codecks safe actions: ${homeState.allActions.count { it.liveSafe }}'") },
                            onRunAction = ::handleAction,
                            onOpenLink = { value -> homeViewModel.runRaw("Open link", "open ${shellQuote(value)}") },
                            onRunShell = { homeViewModel.runRaw("Shell command", it) },
                            onPointerSensitivityChange = { pointerSensitivity = it },
                            onNaturalScrollChange = { naturalScroll = it },
                            onOpenMouse = { navigate(MouseRoute) },
                        )
                    }
                    entry<WidgetRoute> {
                        val context = LocalContext.current
                        WidgetScreen(contentPadding) {
                            val manager = android.appwidget.AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported) {
                                manager.requestPinAppWidget(
                                    android.content.ComponentName(context, DeckWidgetProvider::class.java),
                                    null,
                                    null,
                                )
                            }
                        }
                    }
                    entry<AppearanceRoute> {
                        AppearanceScreen(
                            contentPadding = contentPadding,
                            themeMode = themeSettings.mode,
                            onThemeModeChange = onThemeModeChange,
                        )
                    }
                    entry<DevicesRoute> {
                        DevicesScreen(
                            contentPadding = contentPadding,
                            state = devicesState,
                            onSelectDevice = devicesViewModel::select,
                            onRefresh = devicesViewModel::refresh,
                            onAddDevice = { navigate(ConnectionRoute) },
                        )
                    }
                    entry<AiBuilderRoute> {
                        AiProviderSettingsRoute(
                            entitlementRepository,
                            contentPadding,
                            actionRunner = actionRunner,
                            availableActions = homeState.allActions.distinctBy { it.id },
                            onRunAction = ::handleAction,
                            trackpadSettings = trackpadSettings,
                            onTrackpadSettingsChange = { transform ->
                                scope.launch { trackpadSettingsRepository.update(transform) }
                            },
                            onThemeModeChange = onThemeModeChange,
                            onThemeAccentChange = onThemeAccentChange,
                            onThemeSurfaceStyleChange = onThemeSurfaceStyleChange,
                            onThemeBorderStyleChange = onThemeBorderStyleChange,
                            onThemeShapeStyleChange = onThemeShapeStyleChange,
                            onOpenDeck = { navigate(HomeRoute) },
                            onOpenTrackpad = { navigate(MouseRoute) },
                            onOpenSettings = { navigate(SettingsRoute) },
                            onOpenAiSettings = { navigate(AiProviderRoute) },
                            onOpenAction = { actionId ->
                                focusedDeckActionId = actionId
                                navigate(HomeRoute)
                            },
                            contextAppsEnabled = contextFeaturesEnabled,
                            onSaveDraft = { draft ->
                                if (!automationsViewModel.saveGeneratedDraft(draft)) {
                                    homeViewModel.saveGeneratedDraft(draft)
                                }
                            },
                            onSaveArtifact = { artifact ->
                                if (!automationsViewModel.saveArtifact(artifact)) {
                                    homeViewModel.saveArtifact(artifact)
                                }
                            },
                        )
                    }
                    entry<AiProviderRoute> {
                        AiProviderSettingsRoute(
                            entitlementRepository,
                            contentPadding,
                            actionRunner = actionRunner,
                            mode = AiWorkspaceMode.ProviderSettings,
                            availableActions = homeState.allActions.distinctBy { it.id },
                            onRunAction = ::handleAction,
                            trackpadSettings = trackpadSettings,
                            onTrackpadSettingsChange = { transform ->
                                scope.launch { trackpadSettingsRepository.update(transform) }
                            },
                            onThemeModeChange = onThemeModeChange,
                            onThemeAccentChange = onThemeAccentChange,
                            onThemeSurfaceStyleChange = onThemeSurfaceStyleChange,
                            onThemeBorderStyleChange = onThemeBorderStyleChange,
                            onThemeShapeStyleChange = onThemeShapeStyleChange,
                            onOpenDeck = { navigate(HomeRoute) },
                            onOpenTrackpad = { navigate(MouseRoute) },
                            onOpenSettings = { navigate(SettingsRoute) },
                            onOpenAiSettings = { navigate(AiProviderRoute) },
                            onOpenAction = { actionId ->
                                focusedDeckActionId = actionId
                                navigate(HomeRoute)
                            },
                            contextAppsEnabled = contextFeaturesEnabled,
                        )
                    }
                    entry<PremiumRoute> {
                        if (paywallEnabled) {
                            PremiumScreenRoute(
                                commerce.accountRepository,
                                commerce.billingRepository,
                                commerce.entitlementRepository,
                                featureFlagRepository,
                                contentPadding,
                            )
                        } else {
                            SettingsScreen(
                                contentPadding = contentPadding,
                                connectionReady = homeState.connectionReady,
                                connectionHealth = connectionHealth,
                                hidState = hidState,
                                bluetoothPermissionGranted = bluetoothPermissionGranted,
                                notificationAccessReady = notificationAccessReady,
                                notificationPrivacySettings = notificationPrivacySettings,
                                clipboardSettings = clipboardSettings,
                                aiProviderReady = aiProviderReady,
                                automationsReady = featureFlags.focusedEnabled(FeatureFlag.Automations) && connectionHealth.isReady,
                                onConnection = { navigate(ConnectionRoute) },
                                onBluetooth = { navigate(BluetoothRoute) },
                                onNotificationAccess = {
                                    appContext.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                },
                                onNotificationPrivacyChange = { transform ->
                                    scope.launch { notificationPrivacySettingsRepository.update(transform) }
                                },
                                onAutomations = { navigate(AutomationsRoute) },
                                onDevices = { navigate(DevicesRoute) },
                                onDeck = { navigate(EditorRoute) },
                                onKeyboard = { navigate(KeyboardRoute) },
                                onClipboard = { navigate(ClipboardRoute) },
                                onClipboardModeChange = { mode ->
                                    scope.launch { clipboardSettingsRepository.saveMode(mode) }
                                },
                                onClipboardIntervalChange = { minutes ->
                                    scope.launch { clipboardSettingsRepository.saveIntervalMinutes(minutes) }
                                },
                                onAiBuilder = { navigate(AiProviderRoute) },
                                onContextDeck = { navigate(ContextDeckRoute) },
                                onPremium = { navigate(PremiumRoute) },
                                onWidget = { navigate(WidgetRoute) },
                                onAppearance = { navigate(AppearanceRoute) },
                                onAdvanced = { navigate(AdvancedRoute) },
                                onDebugBundle = {
                                    shareDebugBundle(
                                        context = appContext,
                                        route = currentRoute.title(),
                                        homeState = homeState,
                                        connectionState = connectionState,
                                        hidState = hidState,
                                        featureFlags = featureFlags,
                                        trackpadSettings = trackpadSettings,
                                        clipboardSettings = clipboardSettings,
                                    )
                                },
                                themeSettings = themeSettings,
                                onThemeModeChange = onThemeModeChange,
                                onThemeAccentChange = onThemeAccentChange,
                                onThemeSurfaceStyleChange = onThemeSurfaceStyleChange,
                                onThemeBorderStyleChange = onThemeBorderStyleChange,
                                onThemeShapeStyleChange = onThemeShapeStyleChange,
                                onDeckStyleChange = onDeckStyleChange,
                                trackpadSettings = trackpadSettings,
                                onTrackpadSettingsChange = { transform ->
                                    scope.launch { trackpadSettingsRepository.update(transform) }
                                },
                                showPremium = false,
                                localOnlyV1 = localOnlyV1,
                                debugBundleEnabled = BuildConfig.DEBUG,
                                appVersionLabel = "Version ${BuildConfig.VERSION_NAME}",
                                featureFlags = featureFlags,
                                onFeatureFlagChange = featureFlagRepository::set,
                                onResetFeatureFlags = { scope.launch { featureFlagRepository.resetDefaults() } },
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
    }
}

@Composable
private fun BluetoothDestination(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpenTrackpad: () -> Unit,
    viewModel: MouseViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.start()
    }

    BluetoothSetupScreen(
        state = state,
        contentPadding = contentPadding,
        permissionGranted = permissionGranted,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
        onStart = viewModel::start,
        onRefreshHosts = viewModel::refreshHosts,
        onConnect = viewModel::connect,
        onOpenTrackpad = onOpenTrackpad,
    )
}

@Composable
private fun KeyboardDestination(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    customActions: List<DeckAction>,
    onCustomAction: (DeckAction) -> Unit,
    selectedActionId: String? = null,
    viewModel: KeyboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.hidState.collectAsStateWithLifecycle()
    val keyboardState by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.start()
    }

    KeyboardScreen(
        state = state,
        text = keyboardState.text,
        contentPadding = contentPadding,
        permissionGranted = permissionGranted,
        deliveryMode = keyboardState.deliveryMode,
        isSending = keyboardState.isSending,
        sendStatus = keyboardState.status,
        recentSends = keyboardState.recentSends,
        snippets = keyboardState.snippets,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
        onStart = viewModel::start,
        onRefreshHosts = viewModel::refreshHosts,
        onConnect = viewModel::connect,
        onTextChange = viewModel::setText,
        onDeliveryModeChange = viewModel::setDeliveryMode,
        onTypeText = viewModel::typeText,
        onClearText = viewModel::clearText,
        onUseSnippet = viewModel::useSnippet,
        onCommand = viewModel::send,
        customActions = customActions,
        onCustomAction = onCustomAction,
        selectedActionId = selectedActionId,
    )
}

private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

private fun shareDebugBundle(
    context: Context,
    route: String,
    homeState: io.codex.s23deck.ui.home.HomeUiState,
    connectionState: io.codex.s23deck.ui.connection.ConnectionUiState,
    hidState: HidState,
    featureFlags: Map<FeatureFlag, Boolean>,
    trackpadSettings: TrackpadSettings,
    clipboardSettings: ClipboardSyncSettings,
) {
    if (!BuildConfig.DEBUG) return
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val directory = File(context.cacheDir, "debug-bundles").apply { mkdirs() }
    val file = File(directory, "deckbridge-debug-$timestamp.txt")
    val config = connectionState.config
    file.writeText(
        buildString {
            appendLine("Codecks debug bundle")
            appendLine("created=$timestamp")
            appendLine("package=${context.packageName}")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("debug=${BuildConfig.DEBUG}")
            appendLine("routeSet=${route.isNotBlank()}")
            appendLine()
            appendLine("[connection]")
            appendLine("ready=${config.isReady}")
            appendLine("configured=${config.isConfigured}")
            appendLine("hostSet=${config.host.isNotBlank()}")
            appendLine("portSet=${config.port > 0}")
            appendLine("userSet=${config.user.isNotBlank()}")
            appendLine("hasKey=${config.hasKey}")
            appendLine("hostKeyPinned=${config.hostKey.isNotBlank()}")
            appendLine("passwordFieldSet=${connectionState.password.isNotEmpty()}")
            appendLine("operation=${connectionState.operation}")
            appendLine("message=${connectionState.message.orEmpty().redactedLog()}")
            appendLine("error=${connectionState.error.orEmpty().redactedLog()}")
            appendLine()
            appendLine("[bluetooth]")
            appendLine("ready=${hidState.isReady}")
            appendLine("connected=${hidState.isConnected}")
            appendLine("knownHosts=${hidState.hosts.size}")
            appendLine()
            appendLine("[trackpad]")
            appendLine("pointerSpeedBucket=${trackpadSettings.pointerSpeed.bucketedSpeed()}")
            appendLine("scrollRail=${trackpadSettings.scrollRailEnabled}")
            appendLine("haptics=${trackpadSettings.hapticsEnabled}")
            appendLine("trace=${trackpadSettings.pointerTraceEnabled}")
            appendLine()
            appendLine("[clipboard]")
            appendLine("enabled=${clipboardSettings.mode != ClipboardSyncMode.Off}")
            appendLine("intervalBucket=${clipboardSettings.intervalMinutes.bucketedInterval()}")
            appendLine()
            appendLine("[deck]")
            appendLine("dynamic=${homeState.dynamicDeckEnabled}")
            appendLine("visibleActions=${homeState.actions.size}")
            appendLine("catalogActions=${homeState.allActions.size}")
            appendLine("status=${homeState.actionStatus.safeDebugName()}")
            appendLine()
            appendLine("[feature-flags]")
            appendLine("overrideCount=${featureFlags.count { (flag, value) -> DEFAULT_FEATURE_FLAGS[flag] != value }}")
            appendLine("labsEnabled=${featureFlags[FeatureFlag.Labs] == true}")
            appendLine()
            appendLine("[activity]")
            appendLine("events=${homeState.activity.size}")
            appendLine("failures=${homeState.activity.count { !it.succeeded }}")
            appendLine("hasLatest=${homeState.activity.isNotEmpty()}")
        },
    )
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.debugfiles", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Codecks debug bundle $timestamp")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(send, "Share Codecks debug bundle").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun String.redactedLog(): String =
    DiagnosticRedactor.redact(this)

private fun Float.bucketedSpeed(): String = when {
    this < 0.75f -> "low"
    this > 1.15f -> "high"
    else -> "medium"
}

private fun Int.bucketedInterval(): String = when {
    this <= 5 -> "short"
    this <= 20 -> "medium"
    else -> "long"
}

private fun ActionStatus.safeDebugName(): String = when (this) {
    ActionStatus.Idle -> "Idle"
    is ActionStatus.Running -> "Running"
    is ActionStatus.Succeeded -> "Succeeded"
    is ActionStatus.Failed -> "Failed"
}

private tailrec fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}

@Composable
private fun MouseDestination(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    customActions: List<DeckAction>,
    dynamicActions: List<DeckAction>,
    customActionsReady: Boolean,
    onCustomAction: (DeckAction) -> Unit,
    selectedActionId: String? = null,
    featureFlags: Map<FeatureFlag, Boolean> = emptyMap(),
    phoneNotifications: List<NotificationPreview> = emptyList(),
    laptopNotifications: List<NotificationPreview> = emptyList(),
    phoneNotificationAccessReady: Boolean = false,
    phoneNotificationLaneEnabled: Boolean = false,
    onOpenBluetoothSettings: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onExitTrackpad: () -> Unit = {},
    viewModel: MouseViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trackpadSettings by viewModel.settings.collectAsStateWithLifecycle(
        initialValue = io.codex.s23deck.core.trackpad.TrackpadSettings(),
    )
    var airTouchActive by rememberSaveable { mutableStateOf(false) }
    var airTouchConfirmSignal by rememberSaveable { mutableStateOf(0) }
    var airTouchX by rememberSaveable { mutableStateOf(0f) }
    var airTouchY by rememberSaveable { mutableStateOf(0f) }
    var sessionPinned by remember { mutableStateOf(isLockTaskActive(context)) }
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) viewModel.start()
    }
    LaunchedEffect(activity) {
        while (activity != null) {
            sessionPinned = isLockTaskActive(context)
            delay(1_000L)
        }
    }

    fun moveAirTouch(dx: Float, dy: Float) {
        airTouchX = (airTouchX + dx).coerceIn(-500f, 500f)
        airTouchY = (airTouchY + dy).coerceIn(-500f, 500f)
        if (state.isConnected) viewModel.move(dx, dy)
    }

    val labsEnabled = featureFlags.focusedEnabled(FeatureFlag.Labs)
    val airMouseEnabled = labsEnabled && featureFlags.focusedEnabled(FeatureFlag.LabAirMouse)
    val airTouchEnabled = labsEnabled && featureFlags.focusedEnabled(FeatureFlag.LabAirTouch)
    val backTapAvailable = labsEnabled && featureFlags.focusedEnabled(FeatureFlag.LabBackTap)
    val volumeKeysAvailable = labsEnabled && featureFlags.focusedEnabled(FeatureFlag.LabVolumeKeys)
    val effectiveTrackpadSettings = trackpadSettings.copy(
        backTapEnabled = trackpadSettings.backTapEnabled && backTapAvailable,
        volumeKeysEnabled = trackpadSettings.volumeKeysEnabled && volumeKeysAvailable,
        airMouseEnabled = trackpadSettings.airMouseEnabled && airMouseEnabled,
        airTouchEnabled = trackpadSettings.airTouchEnabled && airTouchEnabled,
        labsEnabled = labsEnabled,
    )

    DisposableEffect(activity, effectiveTrackpadSettings.volumeKeysEnabled, airTouchActive, state.isConnected) {
        activity?.setHardwareKeyHandler { event ->
            if (airTouchActive) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        if (event.repeatCount == 0) airTouchConfirmSignal += 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        moveAirTouch(-36f, 0f)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        moveAirTouch(36f, 0f)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        moveAirTouch(0f, -36f)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        moveAirTouch(0f, 36f)
                        true
                    }
                    KeyEvent.KEYCODE_PAGE_UP -> {
                        airTouchX = 0f
                        airTouchY = 0f
                        true
                    }
                    KeyEvent.KEYCODE_PAGE_DOWN -> {
                        if (state.isConnected && event.repeatCount == 0) viewModel.rightClick()
                        true
                    }
                    else -> false
                }
            } else if (!effectiveTrackpadSettings.volumeKeysEnabled || !state.isConnected || event.repeatCount > 0) {
                false
            } else {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        viewModel.scroll(-4)
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        viewModel.scroll(4)
                        true
                    }
                    else -> false
                }
            }
        }
        onDispose { activity?.setHardwareKeyHandler(null) }
    }

    MouseScreen(
        state = state,
        settings = effectiveTrackpadSettings,
        onSettingsChange = viewModel::updateSettings,
        contentPadding = contentPadding,
        permissionGranted = permissionGranted,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
        onStart = viewModel::start,
        onRefreshHosts = viewModel::refreshHosts,
        onConnect = viewModel::connect,
        onMove = viewModel::move,
        onScroll = viewModel::scroll,
        onLeftClick = viewModel::leftClick,
        onRightClick = viewModel::rightClick,
        onMiddleClick = viewModel::middleClick,
        onPress = viewModel::press,
        onReleaseButtons = viewModel::releaseButtons,
        onHorizontalScroll = viewModel::horizontalScroll,
        onCommand = viewModel::send,
        customActions = customActions,
        dynamicActions = dynamicActions,
        customActionsReady = customActionsReady,
        onCustomAction = onCustomAction,
        selectedActionId = selectedActionId,
        phoneNotifications = phoneNotifications,
        laptopNotifications = laptopNotifications,
        phoneNotificationAccessReady = phoneNotificationAccessReady,
        phoneNotificationLaneEnabled = phoneNotificationLaneEnabled,
        labsEnabled = labsEnabled,
        airMouseEnabled = airMouseEnabled,
        airTouchEnabled = airTouchEnabled,
        backTapAvailable = backTapAvailable,
        volumeKeysAvailable = volumeKeysAvailable,
        airTouchCursor = Offset(airTouchX, airTouchY),
        airTouchConfirmSignal = airTouchConfirmSignal,
        onAirTouchActiveChange = { airTouchActive = it },
        onAirTouchDelta = { dx, dy -> moveAirTouch(dx, dy) },
        onAirTouchRecenter = {
            airTouchX = 0f
            airTouchY = 0f
        },
        onAirTouchSampleConfirmed = { target, observed ->
            val correctionX = (target.x - observed.x).coerceIn(-120f, 120f)
            val correctionY = (target.y - observed.y).coerceIn(-120f, 120f)
            airTouchX = target.x
            airTouchY = target.y
            if (state.isConnected) viewModel.move(correctionX, correctionY)
        },
        onOpenBluetoothSettings = onOpenBluetoothSettings,
        onOpenNotificationSettings = onOpenNotificationSettings,
        sessionPinned = sessionPinned,
        onToggleSessionPin = {
            activity?.let { host ->
                runCatching {
                    if (isLockTaskActive(context)) host.stopLockTask() else host.startLockTask()
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "Screen pinning is unavailable", Toast.LENGTH_LONG).show()
                }
                sessionPinned = isLockTaskActive(context)
            }
        },
        onExitTrackpad = {
            if (isLockTaskActive(context)) runCatching { activity?.stopLockTask() }
            onExitTrackpad()
        },
    )
}

private fun isLockTaskActive(context: Context): Boolean =
    ((context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE) != ActivityManager.LOCK_TASK_MODE_NONE
