package io.codecks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadSettingsRepository
import io.codecks.domain.ActionKind
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.data.clipboard.ClipboardSettingsRepository
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.PendingBackupRecovery
import io.codecks.data.features.LocalFeatureFlagRepository
import io.codecks.data.reactive.LiveMacStateInputs
import io.codecks.data.context.NotificationPreview
import io.codecks.data.context.ContextFeatureStatus
import io.codecks.data.context.NotificationPrivacySettings
import io.codecks.data.context.NotificationPrivacySettingsRepository
import io.codecks.data.context.PhoneNotificationBackplane
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
import io.codecks.launcher.LauncherIcon
import io.codecks.ui.connection.ConnectionSetupController
import io.codecks.ui.connection.ConnectionViewModel
import io.codecks.ui.connection.HidConfirmationStore
import io.codecks.ui.connection.nextSetupProofExpiryAtEpochMs
import io.codecks.ui.connection.connectionHealth
import io.codecks.ui.connection.isReady
import io.codecks.ui.automations.AutomationsScreen
import io.codecks.ui.automations.AutomationsViewModel
import io.codecks.ui.app.CodecksAppShell
import io.codecks.ui.app.RouteRegistry
import io.codecks.ui.app.enabled
import io.codecks.ui.app.launchRouteForRestoredTop
import io.codecks.ui.app.routeStateKey
import io.codecks.ui.ai.AiWorkspaceMode
import io.codecks.ui.ai.AiProviderSettingsRoute
import io.codecks.ui.clipboard.ClipboardScreen
import io.codecks.ui.clipboard.ClipboardViewModel
import io.codecks.ui.clipboard.openClipboardBatterySaverSettings
import io.codecks.ui.editor.DeckEditorScreen
import io.codecks.ui.home.HomeScreen
import io.codecks.ui.home.HomeViewModel
import io.codecks.ui.mouse.TrackpadHostScreen
import io.codecks.ui.mouse.reactive.ReactiveTrackpadCard
import io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModel
import io.codecks.ui.mouse.reactive.reactiveTrackpadViewModelFactory
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
import kotlinx.coroutines.launch
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.features.FeatureFlaggedEntitlementRepository
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.LocalOnlyEntitlementRepository
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartSurface
import io.codecks.BuildConfig
import io.codecks.domain.LocalActionResult
import io.codecks.ui.home.smart.SmartDeckInputs
import io.codecks.ui.home.smart.SmartDeckViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CodecksApp(
    destinationRequest: String?,
    sharedText: String? = null,
    window: android.view.Window,
    bindings: AppFeatureBindings,
    pendingReactiveHelperPairingJson: String?,
    onReactiveHelperPairingConsumed: () -> Unit,
    themeSettings: CodecksThemeSettings,
    onThemeModeChange: (CodecksThemeMode) -> Unit,
    onThemeAccentChange: (CodecksAccent) -> Unit,
    onThemeSurfaceStyleChange: (CodecksSurfaceStyle) -> Unit,
    onThemeBorderStyleChange: (CodecksBorderStyle) -> Unit,
    onThemeShapeStyleChange: (CodecksShapeStyle) -> Unit,
    onDeckStyleChange: (CodecksDeckStyle) -> Unit,
    onIconPackChange: (CodecksIconPack) -> Unit,
    launcherIcon: LauncherIcon,
    onLauncherIconChange: (LauncherIcon) -> Unit,
    onRequestConsumed: () -> Unit,
    onSharedTextConsumed: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    connectionViewModel: ConnectionViewModel = viewModel(),
    automationsViewModel: AutomationsViewModel = viewModel(),
) {
    val hidRepository = bindings.core.hidRepository
    val actionRunner = bindings.core.actionRunner
    val actionRepository = bindings.core.actionRepository
    val connectionRepository = bindings.core.connectionRepository
    val deviceRepository = bindings.core.deviceRepository
    val backupRepository = bindings.core.backupRepository
    val hostContext = LocalContext.current
    val appContext = hostContext.applicationContext
    val featureFlagRepository = remember(appContext) { LocalFeatureFlagRepository(appContext) }
    val restoredTopRouteName = rememberSaveable { mutableStateOf(routeStateKey(HomeRoute)) }
    val navigationBootstrap = remember {
        AppNavigationCoordinator.bootstrap(
            restoredStateKey = restoredTopRouteName.value,
            flags = featureFlagRepository.currentFlags,
            distributionChannel = BuildConfig.DISTRIBUTION_CHANNEL,
        )
    }
    val routeBuildExposure = navigationBootstrap.exposure
    val backStack = rememberNavBackStack(navigationBootstrap.initialRoute)
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    var proofClockEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(
        connectionState.sshTerminalReceipt?.completedAtEpochMs,
        connectionState.macCapabilityReceipts,
    ) {
        proofClockEpochMs = System.currentTimeMillis()
        val expiryAt = connectionState.nextSetupProofExpiryAtEpochMs() ?: return@LaunchedEffect
        delay((expiryAt - System.currentTimeMillis()).coerceAtLeast(1L))
        proofClockEpochMs = System.currentTimeMillis()
    }
    val connectionHealth = connectionState.connectionHealth(proofClockEpochMs)
    val automationsState by automationsViewModel.uiState.collectAsStateWithLifecycle()
    val hidState by hidRepository.state.collectAsStateWithLifecycle()
    val hidConfirmationStore = remember(appContext) { HidConfirmationStore(appContext) }
    val activity = remember(hostContext) { hostContext.findMainActivity() }
    val settingsConnectionSetupController = remember(hostContext, activity, connectionViewModel) {
        ConnectionSetupController(hostContext, activity, connectionViewModel)
    }
    val featureFlags by featureFlagRepository.flags.collectAsStateWithLifecycle(initialValue = emptyMap())
    val smartDeckEnabled =
        featureFlags.focusedEnabled(FeatureFlag.SmartSuggestions) && featureFlags.focusedEnabled(FeatureFlag.SmartDeck)
    val reactiveTrackpadEnabled = featureFlags.focusedEnabled(FeatureFlag.ReactiveTrackpad)
    val notificationFeaturesEnabled = BuildConfig.OPTIONAL_CONTEXT_SURFACES_ENABLED
    val phoneNotificationFlow = remember(notificationFeaturesEnabled) {
        if (notificationFeaturesEnabled) {
            PhoneNotificationBackplane.notifications
        } else {
            flowOf(emptyList<NotificationPreview>())
        }
    }
    val phoneNotifications by phoneNotificationFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentRoute = backStack.lastOrNull() ?: HomeRoute
    LaunchedEffect(currentRoute) {
        restoredTopRouteName.value = routeStateKey(currentRoute)
    }
    LaunchedEffect(Unit) {
        val launchRoute = launchRouteForRestoredTop(currentRoute)
        if (launchRoute != currentRoute) {
            backStack.clear()
            backStack.add(launchRoute)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var smartSelectedMacId by remember { mutableStateOf<SmartMacId?>(null) }
    LaunchedEffect(connectionState.config, currentRoute, currentRoute == HomeRoute) {
        smartSelectedMacId = runCatching {
            deviceRepository.currentDeviceId()?.value?.let { SmartMacId(it) }
        }.getOrNull()
    }
    val helperRuntime = rememberHelperRuntime(
        appContext = appContext,
        bindings = bindings,
        currentRoute = currentRoute,
        pendingPairingJson = pendingReactiveHelperPairingJson,
        reactiveEnabled = reactiveTrackpadEnabled,
        selectedMacId = smartSelectedMacId,
        scope = scope,
        snackbarHostState = snackbarHostState,
        onPendingPairingConsumed = onReactiveHelperPairingConsumed,
    )
    val helperBinding = helperRuntime.binding
    val backupRuntime = rememberBackupRuntime(appContext, backupRepository, scope, snackbarHostState)
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
    val inputRuntime = rememberInputReadinessRuntime(
        appContext = appContext,
        hostContext = hostContext,
        activity = activity,
        repository = hidRepository,
        hidState = hidState,
        connectionState = connectionState,
        connectionHealth = connectionHealth,
        aiReady = aiProviderReady,
        proofClockEpochMs = proofClockEpochMs,
        confirmationStore = hidConfirmationStore,
    )
    val bluetoothPermissionGranted = inputRuntime.permissionGranted
    val bluetoothPermissionPermanentlyDenied = inputRuntime.permissionPermanentlyDenied
    val requestBluetoothPermission = inputRuntime.requestPermission
    val confirmAndConnectHid = inputRuntime.confirmAndConnect
    val hidTerminalReceipt = inputRuntime.terminalReceipt
    val runtimeReadiness = inputRuntime.readiness
    LaunchedEffect(runtimeReadiness.macCommandsReady) {
        homeViewModel.setTerminalProofReady(runtimeReadiness.macCommandsReady)
        automationsViewModel.setTerminalProofReady(runtimeReadiness.macCommandsReady)
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
    val smartDeckViewModel: SmartDeckViewModel? = if (smartDeckEnabled) viewModel() else null
    val reactiveBinding = remember(reactiveTrackpadEnabled, helperBinding, bindings) {
        helperBinding?.takeIf { reactiveTrackpadEnabled }?.let { helper ->
            bindings.optional.bindReactive(bindings.core, helper)
        }
    }
    val reactiveMacStateRepository = reactiveBinding?.macStateRepository
    val reactiveEngine = reactiveBinding?.engine
    val reactiveExecutor = reactiveBinding?.executor
    val reactiveTrackpadViewModel: ReactiveTrackpadViewModel? =
        if (reactiveMacStateRepository != null && reactiveEngine != null && reactiveExecutor != null) {
            viewModel(
                key = "reactive-trackpad",
                factory = remember(reactiveMacStateRepository, reactiveEngine, reactiveExecutor) {
                    reactiveTrackpadViewModelFactory(
                        macStateRepository = reactiveMacStateRepository,
                        engine = reactiveEngine,
                        executor = reactiveExecutor,
                    )
                },
            )
        } else {
            null
        }
    AppContextEffects(
        reactiveInputs = listOf(smartSelectedMacId, runtimeReadiness.macCommandsReady, hidState.isConnected, homeState.activeMacApp),
        reactiveTrackpadVisible = reactiveTrackpadEnabled && currentRoute == MouseRoute,
        contextDeckPolling = currentRoute == HomeRoute && homeState.connectionReady,
        updateReactiveInputs = { reactiveMacStateRepository?.update(LiveMacStateInputs(smartSelectedMacId?.value, runtimeReadiness.macCommandsReady, hidState.isConnected, homeState.activeMacApp)) },
        setReactiveTrackpadVisible = { reactiveTrackpadViewModel?.setVisible(it) },
        refreshContextDeck = {
            homeViewModel.refreshContextDeckLiveState()
        },
    )
    val smartSuggestions by (smartDeckViewModel?.suggestions ?: flowOf(emptyList())).collectAsStateWithLifecycle(emptyList())
    val smartRunPending by (smartDeckViewModel?.runPending ?: flowOf(false)).collectAsStateWithLifecycle(false)
    LaunchedEffect(
        smartDeckEnabled,
        currentRoute,
        smartSelectedMacId,
        runtimeReadiness.macCommandsReady,
        hidState.isConnected,
        homeState.activeMacApp,
        homeState.activity,
        homeState.allActions,
        visibleDeckActions,
    ) {
        smartDeckViewModel?.updateInputs(
            SmartDeckInputs(
                smartDeckEnabled = smartDeckEnabled,
                onHomeRoute = currentRoute == HomeRoute,
                currentSurface = SmartSurface.Deck,
                selectedMacId = smartSelectedMacId,
                connectionReady = runtimeReadiness.macCommandsReady,
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
    var selectedDeckSlot by remember { mutableStateOf(0) }
    var aiPlacementSlot by remember { mutableStateOf<Int?>(null) }
    var focusedDeckActionId by remember { mutableStateOf<String?>(null) }
    var fullscreenOverride by remember { mutableStateOf<Boolean?>(null) }
    var fullscreenConfirmOpen by remember { mutableStateOf(false) }
    var runLogActionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val fullscreen = fullscreenOverride == true
    LaunchedEffect(currentRoute) {
        if (!AppBootstrapCoordinator.needsAi(currentRoute)) {
            aiProviderReady = false
            return@LaunchedEffect
        }
        aiProviderReady = runCatching { bindings.optional.isAiProviderReady(appContext) }.getOrDefault(false)
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
        val guardedRoute = AppNavigationCoordinator.guard(route, featureFlags, routeBuildExposure)
        if (topLevel) backStack.clear()
        if (backStack.lastOrNull() != guardedRoute) backStack.add(guardedRoute)
    }

    fun openTrackpad() {
        navigate(MouseRoute, topLevel = true)
    }

    LaunchedEffect(featureFlags, currentRoute) {
        val guardedRoute = AppNavigationCoordinator.guard(currentRoute, featureFlags, routeBuildExposure)
        if (guardedRoute != currentRoute) {
            backStack.clear()
            backStack.add(guardedRoute)
        }
        if (currentRoute != MouseRoute) {
            fullscreenOverride = null
        }
    }

    LaunchedEffect(destinationRequest) {
        val route = AppNavigationCoordinator.request(destinationRequest, featureFlags, routeBuildExposure)
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

    val actionRuntime = rememberAppActionRuntime(
        AppActionContext(
            route = currentRoute,
            macCommandsReady = runtimeReadiness.macCommandsReady,
            macInputConnected = hidState.isConnected,
            reactiveTrackpadEnabled = reactiveTrackpadEnabled,
            homeState = homeState,
            automationsState = automationsState,
            hidRepository = hidRepository,
            homeViewModel = homeViewModel,
            automationsViewModel = automationsViewModel,
            smartDeckViewModel = smartDeckViewModel,
            scope = scope,
            snackbarHostState = snackbarHostState,
            navigate = { route, topLevel -> navigate(route, topLevel) },
        ),
    )
    fun executeAction(action: DeckAction, allowDangerous: Boolean = false): LocalActionResult? =
        actionRuntime.execute(action, allowDangerous)
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
        val openFileDrop = rememberFileDropLauncher(appContext, connectionRepository, scope, snackbarHostState)
        CodecksAppShell(
            snackbarHostState = snackbarHostState,
            currentRoute = currentRoute,
            backStackSize = backStack.size,
            fullscreen = fullscreen,
            buildExposure = routeBuildExposure,
            tabs = RouteRegistry.primaryDestinations(routeBuildExposure).filter { destination ->
                destination.enabled(featureFlags)
            },
            onBack = { backStack.removeLastOrNull() },
            onDestinationSelected = { route ->
                if (route == AiBuilderRoute) aiPlacementSlot = null
                navigate(
                    route,
                    topLevel = RouteRegistry.descriptor(route)?.clearsBackStack == true,
                )
            },
            onOpenSettings = { navigate(SettingsRoute) },
            onRequestFullscreen = { fullscreenConfirmOpen = true },
            onExitFullscreen = { fullscreenOverride = false },
            onStopInput = {
                hidRepository.disconnect()
                fullscreenOverride = false
            },
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
                            onEditDeck = { navigate(HomeRoute, topLevel = true) },
                            onOpenPalette = { navigate(CommandPaletteRoute) },
                            onEditSlot = { slot ->
                                selectedDeckSlot = slot.coerceIn(0, homeState.actions.lastIndex.coerceAtLeast(0))
                                navigate(HomeRoute, topLevel = true)
                            },
                            onCreateWithAiForSlot = { slot ->
                                selectedDeckSlot = slot.coerceIn(0, homeState.actions.lastIndex.coerceAtLeast(0))
                                aiPlacementSlot = selectedDeckSlot
                                navigate(AiBuilderRoute)
                            },
                            visibleSlotIndices = visibleDeckSlots.map { it.index },
                            onTestAction = homeViewModel::test,
                            onDuplicateAction = homeViewModel::duplicateAction,
                            onRemoveAction = { action -> homeViewModel.removeAction(action.id) },
                            onAssignSlot = homeViewModel::assign,
                            onMoveSlot = homeViewModel::move,
                            onResizeSlot = homeViewModel::resize,
                            onPlacePendingDeckPlacement = homeViewModel::placePendingDeckPlacement,
                            onCancelPendingDeckPlacement = homeViewModel::clearPendingDeckPlacement,
                            onForgetAction = homeViewModel::forgetAction,
                            onRemoveSlot = { slot ->
                                if (slot in homeState.actions.indices) {
                                    homeViewModel.remove(slot)
                                }
                            },
                            onOpenRunLog = { actionId ->
                                runLogActionFilter = actionId
                                navigate(RunLogRoute)
                            },
                            smartSuggestions = smartSuggestions,
                            smartRunPending = smartRunPending,
                            onRunSmartSuggestion = { suggestion -> smartDeckViewModel?.run(suggestion) },
                            onPinSmartSuggestion = { suggestion ->
                                smartDeckViewModel?.pin(suggestion)
                            },
                            onHideSmartSuggestion = { suggestion ->
                                smartDeckViewModel?.hide(suggestion)
                            },
                            onExplainSmartSuggestion = { suggestion ->
                                smartDeckViewModel?.explain(suggestion)
                            },
                            onSuppressSmartSuggestionForContext = { suggestion ->
                                smartDeckViewModel?.suppressHere(suggestion)
                            },
                            onNeverSmartSuggestionForAction = { suggestion ->
                                smartDeckViewModel?.never(suggestion)
                            },
                            onApplyAppDeckOffer = homeViewModel::applyAppDeckOffer,
                            onDismissAppDeckOffer = homeViewModel::dismissAppDeckOffer,
                            onSetAnalogControl = homeViewModel::setAnalogControl,
                            onModifierLayerPressed = homeViewModel::setModifierLayerPressed,
                            onRefreshWindowSpaceMap = homeViewModel::refreshWindowSpaceMap,
                            onFocusWindow = homeViewModel::focusWindow,
                            onRefreshMacTargets = homeViewModel::refreshMacTargets,
                            onRunOnTargets = { action, targets, confirmed ->
                                homeViewModel.runOnTargets(action, targets, confirmed)
                            },
                            onStartWorkflowRecording = homeViewModel::startWorkflowRecording,
                            onStopWorkflowRecording = homeViewModel::stopWorkflowRecording,
                            onRenameWorkflowDraft = homeViewModel::renameWorkflowDraft,
                            onRemoveWorkflowDraftStep = homeViewModel::removeWorkflowDraftStep,
                            onDiscardWorkflowDraft = homeViewModel::discardWorkflowDraft,
                            onPickFileDrop = openFileDrop,
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
                                requestBluetoothPermission()
                            },
                            onStartHid = hidRepository::start,
                            onRefreshHosts = hidRepository::refreshHosts,
                            onConnectHost = confirmAndConnectHid,
                            onConnection = hidRepository::refreshHosts,
                            onFullscreen = {
                                if (fullscreen) fullscreenOverride = false else fullscreenConfirmOpen = true
                            },
                            topContent = {
                                reactiveTrackpadViewModel?.let { viewModel ->
                                    ReactiveTrackpadCard(
                                        enabled = reactiveTrackpadEnabled,
                                        viewModel = viewModel,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                            },
                        ) { childPadding ->
                            MouseDestination(
                                contentPadding = childPadding,
                                customActions = customRowActions,
                                dynamicActions = visibleDeckActions.filter {
                                    it.id !in setOf("blank", "add_button") && it !in customRowActions
                                }.take(8),
                                customActionsReady = runtimeReadiness.macCommandsReady,
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
                                bluetoothPermissionGranted = bluetoothPermissionGranted,
                                onRequestBluetoothPermission = requestBluetoothPermission,
                                onConnectHost = confirmAndConnectHid,
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
                            bluetoothPermissionGranted = bluetoothPermissionGranted,
                            onRequestBluetoothPermission = requestBluetoothPermission,
                            onConnectHost = confirmAndConnectHid,
                        )
                    }
                    entry<ClipboardRoute> {
                        val clipboardViewModel: ClipboardViewModel = viewModel()
                        val clipboardState by clipboardViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(runtimeReadiness.macCommandsReady) {
                            clipboardViewModel.setTerminalProofReady(runtimeReadiness.macCommandsReady)
                        }
                        DisposableEffect(clipboardViewModel) {
                            clipboardViewModel.setLiveSyncSessionActive(true)
                            onDispose {
                                clipboardViewModel.setLiveSyncSessionActive(false)
                            }
                        }
                        LaunchedEffect(currentRoute, sharedText) {
                            if (currentRoute == ClipboardRoute && !sharedText.isNullOrBlank()) {
                                clipboardViewModel.acceptSharedText(sharedText, onSharedTextConsumed)
                            }
                        }
                        ClipboardScreen(
                            state = clipboardState,
                            contentPadding = contentPadding,
                            onRefreshPhone = clipboardViewModel::refreshPhone,
                            onPullFromMac = clipboardViewModel::pullFromMac,
                            onPushToMac = clipboardViewModel::pushToMac,
                            onModeChange = clipboardViewModel::setMode,
                            onIntervalChange = clipboardViewModel::setSyncIntervalMinutes,
                            onStartSession = clipboardViewModel::startClipboardSession,
                            onStopSession = clipboardViewModel::stopClipboardSession,
                            onForegroundVisibleChange = clipboardViewModel::setAppForegroundVisible,
                            onOpenBatterySaverSettings = {
                                openClipboardBatterySaverSettings(appContext)
                            },
                            onRetrySharedText = { clipboardViewModel.retrySharedText(onSharedTextConsumed) },
                            onDiscardSharedText = { clipboardViewModel.discardSharedText(onSharedTextConsumed) },
                        )
                    }
                    entry<AutomationsRoute> {
                        AutomationsScreen(
                            state = automationsState,
                            connectionHealth = connectionHealth,
                            contentPadding = contentPadding,
                            onRunAutomation = automationsViewModel::run,
                            onValidateAutomation = automationsViewModel::validate,
                            onPreflightAutomation = automationsViewModel::preflight,
                            onLiveTestAutomation = automationsViewModel::liveTest,
                            onApproveAutomation = automationsViewModel::approveAndRun,
                            onToggleAutomation = automationsViewModel::toggle,
                            onDuplicateAutomation = automationsViewModel::duplicate,
                            onDeleteAutomation = automationsViewModel::delete,
                            onCheckTriggers = { automationsViewModel.checkTriggersNow() },
                            onCreateAutomation = automationsViewModel::create,
                            onEditAutomation = automationsViewModel::edit,
                            onResetRecovery = automationsViewModel::resetDefaults,
                            onRestoreRecovery = backupRuntime.importRecovery,
                            onCreateWithAi = {
                                aiPlacementSlot = null
                                navigate(AiBuilderRoute)
                            },
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
                        val supportRuntime = rememberSettingsSupportRuntime(appContext)
                        val updateViewModel = supportRuntime.updateViewModel
                        val updateState = supportRuntime.updateState
                        val supportBundleViewModel = supportRuntime.supportBundleViewModel
                        val supportBundleState = supportRuntime.supportBundleState
                        SettingsScreen(
                            contentPadding = contentPadding,
                            connectionReady = runtimeReadiness.macCommandsReady,
                            connectionHealth = connectionHealth,
                            hidState = hidState,
                            bluetoothPermissionGranted = bluetoothPermissionGranted,
                            bluetoothPermissionPermanentlyDenied = bluetoothPermissionPermanentlyDenied,
                            notificationAccessReady = notificationAccessReady,
                            notificationPrivacySettings = notificationPrivacySettings,
                            contextFeatureStatus = contextFeatureStatus,
                            clipboardSettings = clipboardSettings,
                            aiProviderReady = aiProviderReady,
                            automationsReady = featureFlags.focusedEnabled(FeatureFlag.Automations) && connectionHealth.isReady,
                            fullscreen = fullscreen,
                            connectionState = connectionState,
                            hidTerminalReceipt = hidTerminalReceipt,
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
                            codecksHelperState = helperRuntime.uiState,
                            onCodecksHelperConnect = helperRuntime.connect,
                            onCodecksHelperSearch = helperRuntime.runSpotlight,
                            onCodecksHelperConfirmPairing = helperRuntime.confirmPairing,
                            onCodecksHelperCancelPairing = helperRuntime.cancelPairing,
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
                            onDeck = { navigate(HomeRoute, topLevel = true) },
                            onKeyboard = { navigate(KeyboardRoute) },
                            onClipboard = { navigate(ClipboardRoute) },
                            onExportBackup = backupRuntime.export,
                            onImportBackup = backupRuntime.import,
                            pendingBackupRecovery = backupRuntime.pendingRecovery != null,
                            corruptBackupRecovery = backupRuntime.pendingRecovery is PendingBackupRecovery.Corrupt,
                            onRecoverBackup = backupRuntime.recover,
                            restorePlan = backupRuntime.restorePlan,
                            onCancelRestore = backupRuntime.cancelRestore,
                            onConfirmRestore = backupRuntime.confirmRestore,
                            onClipboardModeChange = { mode ->
                                scope.launch { clipboardSettingsRepository.saveMode(mode) }
                            },
                            onClipboardIntervalChange = { minutes ->
                                scope.launch { clipboardSettingsRepository.saveIntervalMinutes(minutes) }
                            },
                            onAiBuilder = {
                                aiPlacementSlot = null
                                navigate(AiBuilderRoute)
                            },
                            onDebugBundle = {
                                supportBundleViewModel.preview(
                                    createSupportBundleSnapshot(
                                        context = appContext,
                                        homeState = homeState,
                                        connectionState = connectionState,
                                        hidState = hidState,
                                        featureFlags = featureFlags,
                                        trackpadSettings = trackpadSettings,
                                        clipboardSettings = clipboardSettings,
                                    ),
                                )
                            },
                            supportBundleState = supportBundleState,
                            onGenerateSupportBundle = {
                                supportBundleViewModel.generate()
                            },
                            onCancelSupportBundle = supportBundleViewModel::cancel,
                            onRetrySupportBundleShare = supportBundleViewModel::retryShare,
                            onDeletePendingSupportBundle = supportBundleViewModel::deletePending,
                            onCloseSupportBundleRetaining = supportBundleViewModel::dismissRetaining,
                            themeSettings = themeSettings,
                            onThemeModeChange = onThemeModeChange,
                            onThemeAccentChange = onThemeAccentChange,
                            onThemeSurfaceStyleChange = onThemeSurfaceStyleChange,
                            onThemeBorderStyleChange = onThemeBorderStyleChange,
                            onThemeShapeStyleChange = onThemeShapeStyleChange,
                            onDeckStyleChange = onDeckStyleChange,
                            onIconPackChange = onIconPackChange,
                            launcherIcon = launcherIcon,
                            onLauncherIconChange = onLauncherIconChange,
                            trackpadSettings = trackpadSettings,
                            onTrackpadSettingsChange = { transform ->
                                scope.launch { trackpadSettingsRepository.update(transform) }
                            },
                            localOnlyV1 = localOnlyV1,
                            debugBundleEnabled = true,
                            appVersionLabel = "Version ${BuildConfig.VERSION_NAME}",
                            updateState = updateState,
                            onCheckForUpdate = updateViewModel::checkForUpdate,
                            onOpenUpdateRelease = {
                                updateViewModel.openRelease { url ->
                                    runCatching {
                                        appContext.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }.isSuccess
                                }
                            },
                            featureFlags = featureFlags,
                            onFeatureFlagChange = featureFlagRepository::set,
                            onResetFeatureFlags = { scope.launch { featureFlagRepository.resetDefaults() } },
                            onClearSmartHistory = {
                                smartDeckViewModel?.clearHistory()
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
                            },
                            onMoveAction = { from, to ->
                                homeViewModel.move(from, to)
                                selectedDeckSlot = to.coerceIn(homeState.actions.indices)
                            },
                            onRemoveAction = {
                                homeViewModel.remove(it)
                            },
                            onResizeAction = { slot, span ->
                                homeViewModel.resize(slot, span)
                            },
                            onTestAction = homeViewModel::test,
                            onCreateWithAi = {
                                aiPlacementSlot = selectedDeckSlot
                                navigate(AiBuilderRoute)
                            },
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
                            preferredDeckSlot = aiPlacementSlot,
                            onSaveDraft = { draft ->
                                if (!automationsViewModel.saveGeneratedDraft(draft)) {
                                    homeViewModel.saveGeneratedDraft(draft)
                                }
                            },
                            onSaveArtifact = { artifact ->
                                routeAiArtifactPlacement(
                                    artifact = artifact,
                                    preferredDeckSlot = aiPlacementSlot,
                                    saveAutomation = automationsViewModel::saveArtifact,
                                    placeOnDeck = homeViewModel::requestArtifactPlacement,
                                    onAutomationSaved = { aiPlacementSlot = null },
                                    onDeckPlacementRequested = {
                                        aiPlacementSlot = null
                                        navigate(HomeRoute, topLevel = true)
                                    },
                                )
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
        actionRuntime.celebrationLabel?.let { label ->
            CelebrationOverlay(label = label, onDone = actionRuntime.clearCelebration)
        }
    }
}
