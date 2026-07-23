package io.codecks

import android.Manifest
import android.app.ActivityManager
import android.os.Bundle
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.codecks.core.actions.ActionRunner
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadSettingsRepository
import io.codecks.domain.ActionKind
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.isRunnableFromSmartSuggestion
import io.codecks.data.ai.AndroidSecureApiKeyStore
import io.codecks.data.clipboard.ClipboardSettingsRepository
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.ConnectionRepository
import io.codecks.data.CodecksBackupRepository
import io.codecks.data.features.LocalFeatureFlagRepository
import io.codecks.data.context.NotificationPreview
import io.codecks.data.context.ContextFeatureStatus
import io.codecks.data.context.NotificationPrivacySettings
import io.codecks.data.context.NotificationPrivacySettingsRepository
import io.codecks.data.context.PhoneNotificationBackplane
import io.codecks.domain.ai.AiProviderCatalog
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.navigation.AutomationsRoute
import io.codecks.navigation.ClipboardRoute
import io.codecks.navigation.CommandPaletteRoute
import io.codecks.navigation.AiBuilderRoute
import io.codecks.navigation.AiProviderRoute
import io.codecks.navigation.EditorRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.KeyboardRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.RunLogRoute
import io.codecks.navigation.SettingsRoute
import io.codecks.navigation.title
import io.codecks.ui.connection.ConnectionSetupController
import io.codecks.ui.connection.ConnectionViewModel
import io.codecks.ui.connection.connectionHealth
import io.codecks.ui.connection.hidHealth
import io.codecks.ui.connection.isReady
import io.codecks.ui.automations.AutomationsScreen
import io.codecks.ui.automations.AutomationsViewModel
import io.codecks.ui.app.destinationRequestToRoute
import io.codecks.ui.app.CodecksAppShell
import io.codecks.ui.app.PrimaryTab
import io.codecks.ui.app.guardRoute
import io.codecks.ui.app.launchRouteForRestoredTop
import io.codecks.ui.app.routeEnabled
import io.codecks.ui.ai.AiWorkspaceMode
import io.codecks.ui.ai.AiProviderSettingsRoute
import io.codecks.ui.clipboard.ClipboardScreen
import io.codecks.ui.clipboard.ClipboardViewModel
import io.codecks.ui.editor.DeckEditorScreen
import io.codecks.ui.home.HomeScreen
import io.codecks.ui.home.HomeViewModel
import io.codecks.ui.keyboard.KeyboardScreen
import io.codecks.ui.keyboard.KeyboardViewModel
import io.codecks.ui.mouse.MouseScreen
import io.codecks.ui.mouse.MouseViewModel
import io.codecks.ui.mouse.TrackpadHostScreen
import io.codecks.ui.palette.CommandPaletteScreen
import io.codecks.ui.runlog.RunLogScreen
import io.codecks.ui.settings.SettingsScreen
import io.codecks.ui.theme.CodecksDeckStyle
import io.codecks.ui.theme.CodecksIconPack
import io.codecks.ui.theme.CodecksAccent
import io.codecks.ui.theme.CodecksBorderStyle
import io.codecks.ui.theme.CodecksShapeStyle
import io.codecks.ui.theme.CodecksSurfaceStyle
import io.codecks.ui.theme.CodecksThemeMode
import io.codecks.ui.theme.CodecksThemeSettings
import io.codecks.ui.theme.CodecksTheme
import io.codecks.ui.theme.ThemeSettingsRepository
import io.codecks.ui.theme.resolveForCodecksRelease
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.features.FeatureFlaggedEntitlementRepository
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.LocalOnlyEntitlementRepository
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartSurface
import io.codecks.ui.app.LocalActionDispatcher
import io.codecks.domain.privacy.DiagnosticRedactor
import io.codecks.domain.device.DeviceRepository
import io.codecks.BuildConfig
import io.codecks.domain.LocalActionResult
import io.codecks.ui.home.HomeActionDispatchResult
import io.codecks.ui.home.smart.SmartDeckEffect
import io.codecks.ui.home.smart.SmartDeckInputs
import io.codecks.ui.home.smart.SmartDeckViewModel
import io.codecks.ui.home.smart.SmartRunId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var hidRepository: HidRepository
    @Inject lateinit var actionRunner: ActionRunner
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var deviceRepository: DeviceRepository
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
                initialValue = CodecksThemeSettings(),
            )
            LaunchedEffect(themeSettingsRepository) {
                themeSettingsRepository.migrateToCurrentVisualSystem()
            }
            val effectiveThemeSettings = themeSettings.resolveForCodecksRelease(
                customizationEnabled = !BuildConfig.LOCAL_ONLY_V1,
            )
            CodecksTheme(settings = effectiveThemeSettings) {
                CodecksApp(
                    destinationRequest = destinationRequest,
                    window = window,
                    hidRepository = hidRepository,
                    actionRunner = actionRunner,
                    connectionRepository = connectionRepository,
                    deviceRepository = deviceRepository,
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
        if (event.repeatCount == 0 &&
            (
                keyCode == KeyEvent.KEYCODE_SEARCH ||
                    (keyCode == KeyEvent.KEYCODE_K && (event.isCtrlPressed || event.isMetaPressed))
                )
        ) {
            destinationRequest = "palette"
            return true
        }
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
        const val EXTRA_DESTINATION = "io.codecks.DESTINATION"
    }
}

private fun Map<FeatureFlag, Boolean>.focusedEnabled(flag: FeatureFlag): Boolean =
    this[flag] ?: (DEFAULT_FEATURE_FLAGS[flag] == true)

private fun DeckAction.visibleForFlags(flags: Map<FeatureFlag, Boolean>): Boolean = when (kind) {
    ActionKind.Ssh -> true
    ActionKind.Local -> id in setOf("add_button", "blank", "blank_spacer", "magic_blank", "confetti", "sparkle", "emoji_heart", "emoji_fire", "emoji_focus", "emoji_coffee") ||
        route in setOf("trackpad", "automations", "ai", "button_picker", "empty_slot", "layout_builder", "celebrate", "decor") ||
        (route in setOf("keyboard", "text") && flags.focusedEnabled(FeatureFlag.Keyboard)) ||
        (route == "clipboard" && flags.focusedEnabled(FeatureFlag.Clipboard)) ||
        (id == "clipboard" && flags.focusedEnabled(FeatureFlag.Clipboard)) ||
        (route == "settings" && flags.focusedEnabled(FeatureFlag.Settings)) ||
        (route == "setup_scan" && flags.focusedEnabled(FeatureFlag.Connection)) ||
        false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodecksApp(
    destinationRequest: String?,
    window: android.view.Window,
    hidRepository: HidRepository,
    actionRunner: ActionRunner,
    connectionRepository: ConnectionRepository,
    deviceRepository: DeviceRepository,
    backupRepository: CodecksBackupRepository,
    themeSettings: CodecksThemeSettings,
    onThemeModeChange: (CodecksThemeMode) -> Unit,
    onThemeAccentChange: (CodecksAccent) -> Unit,
    onThemeSurfaceStyleChange: (CodecksSurfaceStyle) -> Unit,
    onThemeBorderStyleChange: (CodecksBorderStyle) -> Unit,
    onThemeShapeStyleChange: (CodecksShapeStyle) -> Unit,
    onDeckStyleChange: (CodecksDeckStyle) -> Unit,
    onIconPackChange: (CodecksIconPack) -> Unit,
    onRequestConsumed: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    connectionViewModel: ConnectionViewModel = viewModel(),
    automationsViewModel: AutomationsViewModel = viewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    val connectionHealth = connectionState.connectionHealth()
    val automationsState by automationsViewModel.uiState.collectAsStateWithLifecycle()
    val hidState by hidRepository.state.collectAsStateWithLifecycle()
    val hostContext = LocalContext.current
    val appContext = hostContext.applicationContext
    val activity = remember(hostContext) { hostContext.findMainActivity() }
    val settingsConnectionSetupController = remember(hostContext, activity, connectionViewModel) {
        ConnectionSetupController(hostContext, activity, connectionViewModel)
    }
    val featureFlagRepository = remember(appContext) { LocalFeatureFlagRepository(appContext) }
    val featureFlags by featureFlagRepository.flags.collectAsStateWithLifecycle(initialValue = emptyMap())
    val smartDeckEnabled =
        featureFlags.focusedEnabled(FeatureFlag.SmartSuggestions) && featureFlags.focusedEnabled(FeatureFlag.SmartDeck)
    val notificationFeaturesEnabled = BuildConfig.OPTIONAL_CONTEXT_SURFACES_ENABLED
    val phoneNotificationFlow = remember(notificationFeaturesEnabled) {
        if (notificationFeaturesEnabled) {
            PhoneNotificationBackplane.notifications
        } else {
            flowOf(emptyList<NotificationPreview>())
        }
    }
    val phoneNotifications by phoneNotificationFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val backStack = rememberNavBackStack(HomeRoute)
    val currentRoute = backStack.lastOrNull() ?: HomeRoute
    LaunchedEffect(Unit) {
        val launchRoute = launchRouteForRestoredTop(currentRoute)
        if (launchRoute != currentRoute) {
            backStack.clear()
            backStack.add(launchRoute)
        }
    }
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
                    result.fold(onSuccess = { "Deck and Rules restored" }, onFailure = { it.message ?: "Restore failed" }),
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
    LaunchedEffect(notificationPrivacySettings, notificationFeaturesEnabled) {
        PhoneNotificationBackplane.updatePrivacySettings(
            if (notificationFeaturesEnabled) {
                notificationPrivacySettings
            } else {
                NotificationPrivacySettings(showOnTrackpad = false)
            },
        )
    }
    var aiProviderReady by remember { mutableStateOf(false) }
    var bluetoothPermissionRefresh by remember { mutableIntStateOf(0) }
    val bluetoothPermissionGranted = remember(bluetoothPermissionRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        bluetoothPermissionRefresh += 1
        if (it) hidRepository.start()
    }
    val notificationAccessReady = notificationFeaturesEnabled && PhoneNotificationBackplane.isEnabled(appContext)
    val contextFeatureStatus = ContextFeatureStatus(
        compiledIntoBuild = true,
        componentEnabled = BuildConfig.OPTIONAL_CONTEXT_SURFACES_ENABLED,
        specialAccessGranted = PhoneNotificationBackplane.isEnabled(appContext),
        runtimeFeatureEnabled = notificationFeaturesEnabled,
        privacyLaneEnabled = notificationPrivacySettings.showOnTrackpad,
        allowedPackageCount = notificationPrivacySettings.allowedPackages.size,
    )
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
    val smartDeckViewModel: SmartDeckViewModel = viewModel()
    var smartSelectedMacId by remember { mutableStateOf<SmartMacId?>(null) }
    LaunchedEffect(connectionState.config, currentRoute, currentRoute == HomeRoute) {
        smartSelectedMacId = runCatching {
            deviceRepository.currentDeviceId()?.value?.let { SmartMacId(it) }
        }.getOrNull()
    }
    val smartSuggestions by smartDeckViewModel.suggestions.collectAsStateWithLifecycle(emptyList())
    val smartRunPending by smartDeckViewModel.runPending.collectAsStateWithLifecycle()
    val pendingDangerousSmartSuggestion by smartDeckViewModel.pendingDangerousSuggestion.collectAsStateWithLifecycle()
    LaunchedEffect(
        smartDeckEnabled,
        currentRoute,
        smartSelectedMacId,
        homeState.connectionReady,
        hidState.isConnected,
        homeState.activeMacApp,
        homeState.activity,
        homeState.allActions,
        visibleDeckActions,
    ) {
        smartDeckViewModel.updateInputs(
            SmartDeckInputs(
                smartDeckEnabled = smartDeckEnabled,
                onHomeRoute = currentRoute == HomeRoute,
                currentSurface = SmartSurface.Deck,
                selectedMacId = smartSelectedMacId,
                connectionReady = homeState.connectionReady,
                macInputConnected = hidState.isConnected,
                activeMacApp = homeState.activeMacApp?.let { runCatching { SmartAppKey(it) }.getOrNull() },
                recentActionIds = homeState.activity.filter { it.succeeded }.map { it.actionId },
                allActions = homeState.allActions,
                visibleDeckActions = visibleDeckActions,
            ),
        )
    }
    val localOnlyV1 = BuildConfig.LOCAL_ONLY_V1
    val localEntitlementRepository = remember { LocalOnlyEntitlementRepository() }
    val entitlementRepository = remember(localEntitlementRepository, featureFlagRepository) {
        FeatureFlaggedEntitlementRepository(localEntitlementRepository, featureFlagRepository)
    }
    var pendingDangerousAction by remember { mutableStateOf<DeckAction?>(null) }
    var acceptedSmartHomeRunId by remember { mutableStateOf<SmartRunId?>(null) }
    var selectedDeckSlot by remember { mutableStateOf(0) }
    var deckDirty by remember { mutableStateOf(false) }
    var focusedDeckActionId by remember { mutableStateOf<String?>(null) }
    var celebrationLabel by remember { mutableStateOf<String?>(null) }
    var fullscreenOverride by remember { mutableStateOf<Boolean?>(null) }
    var fullscreenConfirmOpen by remember { mutableStateOf(false) }
    var runLogActionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val fullscreen = fullscreenOverride == true
    LaunchedEffect(currentRoute) {
        val keyStore = AndroidSecureApiKeyStore(appContext)
        aiProviderReady = runCatching {
            AiProviderCatalog.all.any { spec -> keyStore.hasKey(spec.providerId) }
        }.getOrDefault(false)
    }
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

    fun openTrackpad() {
        navigate(MouseRoute, topLevel = true)
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

    val currentNavigate by rememberUpdatedState<(NavKey, Boolean) -> Unit> { route, topLevel ->
        navigate(route, topLevel)
    }
    val currentMacInputConnected by rememberUpdatedState(hidState.isConnected)
    val localActionDispatcher = remember(hidRepository, scope, snackbarHostState) {
        LocalActionDispatcher(
            onTrackpad = { currentNavigate(MouseRoute, true) },
            onKeyboard = { currentNavigate(KeyboardRoute, false) },
            onAutomations = { currentNavigate(AutomationsRoute, false) },
            onClipboard = { currentNavigate(ClipboardRoute, false) },
            onSettings = { currentNavigate(SettingsRoute, false) },
            onEditor = { currentNavigate(EditorRoute, false) },
            onCelebration = { celebrationLabel = it },
            onMissingMacInput = {
                scope.launch { snackbarHostState.showSnackbar("Connect Mac input first") }
            },
            onSendMediaPlayPause = { hidRepository.send(HidCommand.MediaPlayPause) },
            onSendMediaNext = { hidRepository.send(HidCommand.MediaNext) },
            onSendMediaPrevious = { hidRepository.send(HidCommand.MediaPrevious) },
            onUnsupported = { },
            supportsMacInput = { currentMacInputConnected },
        )
    }

    fun executeAction(
        action: DeckAction,
        allowDangerous: Boolean = false,
    ): LocalActionResult? {
        if (action.kind == ActionKind.Local) {
            return localActionDispatcher.handleAction(action)
        }
        if (action.dangerous && !allowDangerous) {
            pendingDangerousAction = action
            return null
        }
        homeViewModel.run(action, allowDangerous = allowDangerous)
        return null
    }

    LaunchedEffect(Unit) {
        smartDeckViewModel.effects.collect { effect ->
            when (effect) {
                is SmartDeckEffect.Execute -> {
                    val request = effect.request
                    if (request.suggestion.action.kind == ActionKind.Local) {
                        smartDeckViewModel.onExecutionAccepted(request.id)
                        val result = localActionDispatcher.handleAction(request.suggestion.action)
                            ?: LocalActionResult.Failed("Unsupported local action")
                        smartDeckViewModel.onLocalSuggestionResult(request.id, result)
                    } else {
                        when (
                            homeViewModel.run(
                                request.suggestion.action,
                                allowDangerous = request.allowDangerous,
                            )
                        ) {
                            HomeActionDispatchResult.Accepted -> {
                                smartDeckViewModel.onExecutionAccepted(request.id)
                                acceptedSmartHomeRunId = request.id
                            }
                            HomeActionDispatchResult.Busy,
                            is HomeActionDispatchResult.Rejected -> {
                                smartDeckViewModel.onExecutionRejected(request.id)
                            }
                        }
                    }
                }
                is SmartDeckEffect.Pin -> {
                    homeViewModel.pinAction(effect.suggestion.action)
                }
                is SmartDeckEffect.ShowExplanation -> {
                    scope.launch { snackbarHostState.showSnackbar("${effect.confidence}: ${effect.reason}") }
                }
                is SmartDeckEffect.ConfirmDangerousSuggestion -> {
                    pendingDangerousAction = effect.suggestion.action
                }
            }
        }
    }

    LaunchedEffect(homeState.actionStatus) {
        val status = homeState.actionStatus
        when (status) {
            is ActionStatus.Succeeded -> acceptedSmartHomeRunId?.let { runId ->
                smartDeckViewModel.onExecutionCompleted(runId, succeeded = true)
                acceptedSmartHomeRunId = null
            }
            is ActionStatus.Failed -> acceptedSmartHomeRunId?.let { runId ->
                smartDeckViewModel.onExecutionCompleted(runId, succeeded = false)
                acceptedSmartHomeRunId = null
            }
            else -> Unit
        }
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
                    navigate(SettingsRoute)
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
            onDismissRequest = {
                pendingDangerousAction = null
                smartDeckViewModel.cancelDangerousSuggestion()
            },
            title = { Text(action.label) },
            text = { Text(action.description) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val wasSmartSuggestion = pendingDangerousSmartSuggestion != null
                        pendingDangerousAction = null
                        if (wasSmartSuggestion) {
                            smartDeckViewModel.confirmDangerousSuggestion()
                        } else {
                            executeAction(action, allowDangerous = true)
                        }
                    },
                ) {
                    Text("Run")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDangerousAction = null
                        smartDeckViewModel.cancelDangerousSuggestion()
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
    if (fullscreenConfirmOpen) {
        AlertDialog(
            onDismissRequest = { fullscreenConfirmOpen = false },
            title = { Text("Enter fullscreen?") },
            text = { Text("Bottom navigation and system bars hide. Press Back to show them again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fullscreenConfirmOpen = false
                        fullscreenOverride = true
                    },
                ) {
                    Text("Fullscreen")
                }
            },
            dismissButton = {
                TextButton(onClick = { fullscreenConfirmOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CodecksAppShell(
            snackbarHostState = snackbarHostState,
            currentRoute = currentRoute,
            backStackSize = backStack.size,
            fullscreen = fullscreen,
            tabs = PrimaryTab.entries.filter { tab -> routeEnabled(tab.route, featureFlags) },
            onBack = { backStack.removeLastOrNull() },
            onDestinationSelected = { route ->
                navigate(route, topLevel = true)
            },
            onOpenSettings = { navigate(SettingsRoute) },
            onRequestFullscreen = { fullscreenConfirmOpen = true },
            onExitFullscreen = { fullscreenOverride = false },
        ) { contentPadding ->
            BackHandler(enabled = fullscreen) {
                fullscreenOverride = false
            }
            key(currentRoute) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                    entry<HomeRoute> {
                        HomeScreen(
                            state = homeState.copy(actions = visibleDeckActions),
                            connectionHealth = connectionHealth,
                            contentPadding = contentPadding,
                            onAction = ::executeAction,
                            onOpenSettings = { navigate(SettingsRoute) },
                            onOpenConnection = { navigate(SettingsRoute) },
                            onEditDeck = { navigate(EditorRoute) },
                            onOpenPalette = { navigate(CommandPaletteRoute) },
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
                            onOpenRunLog = { actionId ->
                                runLogActionFilter = actionId
                                navigate(RunLogRoute)
                            },
                            smartSuggestions = smartSuggestions,
                            smartRunPending = smartRunPending,
                            onRunSmartSuggestion = smartDeckViewModel::run,
                            onPinSmartSuggestion = { suggestion ->
                                smartDeckViewModel.pin(suggestion)
                            },
                            onHideSmartSuggestion = { suggestion ->
                                smartDeckViewModel.hide(suggestion)
                            },
                            onExplainSmartSuggestion = { suggestion ->
                                smartDeckViewModel.explain(suggestion)
                            },
                            onSuppressSmartSuggestionForContext = { suggestion ->
                                smartDeckViewModel.suppressHere(suggestion)
                            },
                            onNeverSmartSuggestionForAction = { suggestion ->
                                smartDeckViewModel.never(suggestion)
                            },
                            focusedActionId = focusedDeckActionId,
                            deckStyle = themeSettings.deckStyle,
                        )
                    }
                    entry<MouseRoute> {
                        TrackpadHostScreen(
                            contentPadding = contentPadding,
                            hidState = hidState,
                            bluetoothPermissionGranted = bluetoothPermissionGranted,
                            onRequestBluetoothPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                            },
                            onStartHid = hidRepository::start,
                            onRefreshHosts = hidRepository::refreshHosts,
                            onConnectHost = hidRepository::connect,
                            onConnection = hidRepository::refreshHosts,
                            onFullscreen = {
                                if (fullscreen) fullscreenOverride = false else fullscreenConfirmOpen = true
                            },
                        ) { childPadding ->
                            MouseDestination(
                                contentPadding = childPadding,
                                customActions = customRowActions,
                                dynamicActions = visibleDeckActions.filter {
                                    it.id !in setOf("blank", "add_button") && it !in customRowActions
                                }.take(8),
                                customActionsReady = homeState.connectionReady,
                                onCustomAction = ::executeAction,
                                selectedActionId = (homeState.actionStatus as? ActionStatus.Running)?.actionId,
                                featureFlags = featureFlags,
                                phoneNotifications = phoneNotifications,
                                laptopNotifications = laptopNotifications,
                                phoneNotificationAccessReady = notificationAccessReady,
                                phoneNotificationLaneEnabled = notificationFeaturesEnabled && notificationPrivacySettings.showOnTrackpad,
                                onOpenNotificationSettings = {
                                    appContext.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                },
                                onOpenKeyboardSurface = { navigate(KeyboardRoute, topLevel = true) },
                                onOpenClipboardSurface = { navigate(ClipboardRoute, topLevel = true) },
                                onExitTrackpad = { navigate(HomeRoute, topLevel = true) },
                            )
                        }
                    }
                    entry<KeyboardRoute> {
                        KeyboardDestination(
                            contentPadding = contentPadding,
                            customActions = customRowActions,
                            onCustomAction = ::executeAction,
                            selectedActionId = (homeState.actionStatus as? ActionStatus.Running)?.actionId,
                            showHostHeader = !hidState.isConnected,
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
                    entry<RunLogRoute> {
                        RunLogScreen(
                            events = homeState.activity,
                            actions = homeState.allActions,
                            filterActionId = runLogActionFilter,
                            contentPadding = contentPadding,
                            onClearFilter = { runLogActionFilter = null },
                            onRetry = { actionId ->
                                homeState.allActions.firstOrNull { it.id == actionId }?.let { executeAction(it) }
                            },
                            onClear = homeViewModel::clearActivity,
                        )
                    }
                    entry<CommandPaletteRoute> {
                        CommandPaletteScreen(
                            actions = homeState.allActions.distinctBy { it.id },
                            automations = automationsState.automations,
                            runningActionId = (homeState.actionStatus as? ActionStatus.Running)?.actionId
                                ?: automationsState.runningActionId,
                            contentPadding = contentPadding,
                            onRunAction = ::executeAction,
                            onRunAutomation = automationsViewModel::run,
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
                            contextFeatureStatus = contextFeatureStatus,
                            clipboardSettings = clipboardSettings,
                            aiProviderReady = aiProviderReady,
                            automationsReady = featureFlags.focusedEnabled(FeatureFlag.Automations) && connectionHealth.isReady,
                            fullscreen = fullscreen,
                            connectionState = connectionState,
                            onConnection = { navigate(SettingsRoute) },
                            onBluetooth = { openTrackpad() },
                            onFullscreen = {
                                if (fullscreen) fullscreenOverride = false else fullscreenConfirmOpen = true
                            },
                            onConnectionHostChange = connectionViewModel::setHost,
                            onConnectionPortChange = connectionViewModel::setPort,
                            onConnectionUserChange = connectionViewModel::setUser,
                            onConnectionPasswordChange = connectionViewModel::setPassword,
                            onConnectionSelectHost = connectionViewModel::selectHost,
                            onConnectionScan = connectionViewModel::scan,
                            onConnectionScanLocalNetwork = connectionViewModel::scanLocalNetwork,
                            onConnectionVerifyHostKey = connectionViewModel::verifyHostKey,
                            onConnectionConfirmHostKey = connectionViewModel::confirmHostKey,
                            onConnectionAuthorize = connectionViewModel::authorize,
                            onConnectionRotateKey = connectionViewModel::rotateKey,
                            onConnectionResetTrust = connectionViewModel::resetTrust,
                            onConnectionRemoveTarget = connectionViewModel::removeCurrentTarget,
                            onConnectionSavePassword = {
                                scope.launch { settingsConnectionSetupController.savePassword() }
                            },
                            onConnectionUseSavedPassword = {
                                scope.launch { settingsConnectionSetupController.useSavedPassword() }
                            },
                            onConnectionTest = connectionViewModel::test,
                            onOpenMacHelper = {
                                appContext.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://vaddisrinivas.github.io/codecks/mac-helper/"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
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
                            onDevices = {},
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
                            onAiBuilder = { navigate(AiBuilderRoute) },
                            onAppearance = {},
                            onAdvanced = {},
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
                            localOnlyV1 = localOnlyV1,
                            debugBundleEnabled = BuildConfig.DEBUG,
                            appVersionLabel = "Version ${BuildConfig.VERSION_NAME}",
                            featureFlags = featureFlags,
                            onFeatureFlagChange = featureFlagRepository::set,
                            onResetFeatureFlags = { scope.launch { featureFlagRepository.resetDefaults() } },
                            onClearSmartHistory = {
                                smartDeckViewModel.clearHistory()
                                scope.launch { snackbarHostState.showSnackbar("Smart history cleared") }
                            },
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
                    entry<AiBuilderRoute> {
                        AiProviderSettingsRoute(
                            entitlementRepository,
                            contentPadding,
                            actionRunner = actionRunner,
                            deviceRepository = deviceRepository,
                            availableActions = homeState.allActions.distinctBy { it.id },
                            onRunAction = ::executeAction,
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
                            contextAppsEnabled = smartDeckEnabled,
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
                            deviceRepository = deviceRepository,
                            mode = AiWorkspaceMode.ProviderSettings,
                            availableActions = homeState.allActions.distinctBy { it.id },
                            onRunAction = ::executeAction,
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
                            contextAppsEnabled = smartDeckEnabled,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        }
        celebrationLabel?.let { label ->
            CelebrationOverlay(label = label, onDone = { celebrationLabel = null })
        }
    }
}

@Composable
private fun CelebrationOverlay(label: String, onDone: () -> Unit) {
    LaunchedEffect(label) {
        kotlinx.coroutines.delay(1_250)
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Text("🎉", fontSize = 46.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 34.dp, top = 90.dp))
        Text("✨", fontSize = 38.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 38.dp, top = 150.dp))
        Text("💚", fontSize = 42.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp))
        Text(label.take(2), fontSize = 52.sp, modifier = Modifier.align(Alignment.Center))
        Text("🔥", fontSize = 42.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 34.dp))
        Text("✨", fontSize = 44.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 150.dp))
        Text("🎉", fontSize = 50.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 52.dp, bottom = 108.dp))
    }
}

@Composable
private fun KeyboardDestination(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    customActions: List<DeckAction>,
    onCustomAction: (DeckAction) -> Unit,
    selectedActionId: String? = null,
    showHostHeader: Boolean = true,
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
        showHostHeader = showHostHeader,
    )
}

private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

private fun shareDebugBundle(
    context: Context,
    route: String,
    homeState: io.codecks.ui.home.HomeUiState,
    connectionState: io.codecks.ui.connection.ConnectionUiState,
    hidState: HidState,
    featureFlags: Map<FeatureFlag, Boolean>,
    trackpadSettings: TrackpadSettings,
    clipboardSettings: ClipboardSyncSettings,
) {
    if (!BuildConfig.DEBUG) return
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val directory = File(context.cacheDir, "debug-bundles").apply { mkdirs() }
    val file = File(directory, "codecks-debug-$timestamp.txt")
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
            appendLine("diagnostic=${hidState.redactedDiagnosticSummary()}")
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
    onOpenNotificationSettings: () -> Unit = {},
    onOpenKeyboardSurface: () -> Unit = {},
    onOpenClipboardSurface: () -> Unit = {},
    onExitTrackpad: () -> Unit = {},
    viewModel: MouseViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trackpadSettings by viewModel.settings.collectAsStateWithLifecycle(
        initialValue = io.codecks.core.trackpad.TrackpadSettings(),
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
        onTapCorrection = viewModel::markLatestTapWrong,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onOpenKeyboardSurface = onOpenKeyboardSurface,
        onOpenClipboardSurface = onOpenClipboardSurface,
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
