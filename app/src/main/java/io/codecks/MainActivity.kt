package io.codecks

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.os.Bundle
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import io.codecks.core.actions.ActionRunner
import io.codecks.core.reactive.ConnectionRepositoryReactiveSftpTransferClient
import io.codecks.core.reactive.DefaultReactiveActionExecutor
import io.codecks.core.reactive.ReactiveHelperActionExecution
import io.codecks.core.reactive.StateFlowReactiveHelperActionClient
import io.codecks.core.reactive.reactiveActionRevision
import io.codecks.core.reactive.defaultReactiveTrackpadEngine
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.core.trackpad.TrackpadSettingsRepository
import io.codecks.domain.reactive.InMemoryReactiveReceiptStore
import io.codecks.domain.ActionKind
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.domain.isRunnableFromSmartSuggestion
import io.codecks.data.ai.AndroidSecureApiKeyStore
import io.codecks.data.clipboard.ClipboardSettingsRepository
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.ActionRepository
import io.codecks.data.ConnectionRepository
import io.codecks.data.CodecksBackupRepository
import io.codecks.data.BackupInputTooLargeException
import io.codecks.data.PendingBackupRecovery
import io.codecks.data.PendingBackupRecoveryException
import io.codecks.data.readCodecksBackupBounded
import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.privacy.SupportBundleTempFilePolicy
import io.codecks.data.privacy.recordTerminal
import io.codecks.data.features.LocalFeatureFlagRepository
import io.codecks.data.reactive.LiveMacStateInputs
import io.codecks.data.reactive.LiveMacStateRepository
import io.codecks.data.reactive.helper.ReactiveHelperPairingImporter
import io.codecks.data.reactive.helper.reactiveHelperPairingJsonFromUri
import io.codecks.data.reactive.state.ConnectionRepositorySshMacStateSource
import io.codecks.data.reactive.state.StateFlowReactiveHelperClientMacStateSource
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
import io.codecks.ui.connection.HidConfirmationStore
import io.codecks.ui.connection.HidTerminalReceipt
import io.codecks.ui.connection.HidTerminalResult
import io.codecks.ui.connection.BluetoothPermissionState
import io.codecks.ui.connection.BluetoothPermissionPolicy
import io.codecks.ui.connection.codecksReadiness
import io.codecks.ui.connection.evaluateRuntimeSetupCompletion
import io.codecks.ui.connection.hidHostToken
import io.codecks.ui.connection.revisionToken
import io.codecks.ui.connection.setupTargetId
import io.codecks.ui.connection.nextSetupProofExpiryAtEpochMs
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
import io.codecks.ui.home.HomeStatusFeedback
import io.codecks.ui.home.HomeScreen
import io.codecks.ui.home.HomeViewModel
import io.codecks.ui.home.homeStatusFeedback
import io.codecks.ui.keyboard.KeyboardScreen
import io.codecks.ui.keyboard.KeyboardViewModel
import io.codecks.ui.mouse.MouseScreen
import io.codecks.ui.mouse.MouseViewModel
import io.codecks.ui.mouse.TrackpadHostScreen
import io.codecks.ui.mouse.reactive.ReactiveTrackpadCard
import io.codecks.ui.mouse.reactive.ReactiveTrackpadViewModel
import io.codecks.ui.mouse.reactive.reactiveTrackpadViewModelFactory
import io.codecks.ui.palette.CommandPaletteScreen
import io.codecks.ui.runlog.RunLogScreen
import io.codecks.ui.settings.CodecksHelperConnectionKind
import io.codecks.ui.settings.SettingsScreen
import io.codecks.ui.settings.UpdateViewModel
import io.codecks.ui.settings.SupportBundleUiState
import io.codecks.ui.settings.SupportBundleViewModel
import io.codecks.ui.settings.codecksHelperUiState
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
import java.io.File
import java.util.UUID
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.backup.RestorePlan
import io.codecks.domain.features.FeatureFlaggedEntitlementRepository
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.LocalOnlyEntitlementRepository
import io.codecks.domain.smart.SmartAppKey
import io.codecks.domain.smart.SmartMacId
import io.codecks.domain.smart.SmartSurface
import io.codecks.platform.helper.ReactiveHelperDiscovery
import io.codecks.platform.helper.ReactiveHelperEndpoint
import io.codecks.platform.helper.ReactiveHelperIdentityStore
import io.codecks.platform.helper.ReactiveHelperSecretStore
import io.codecks.platform.helper.ReactiveHelperSessionManager
import io.codecks.platform.helper.ReactiveHelperSessionStatus
import io.codecks.platform.helper.StoredReactiveHelperIdentity
import io.codecks.platform.helper.TcpReactiveHelperTransportFactory
import io.codecks.shared.protocol.ReactiveHelperRequest
import io.codecks.ui.app.LocalActionDispatcher
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.domain.privacy.DiagnosticResultCode
import io.codecks.domain.privacy.SupportActionHealth
import io.codecks.domain.privacy.SupportBundleHealth
import io.codecks.domain.privacy.SupportBundleManifest
import io.codecks.domain.privacy.SupportBundleSettings
import io.codecks.domain.privacy.SupportBundleSnapshot
import io.codecks.domain.privacy.SupportConnectionHealth
import io.codecks.domain.privacy.SupportHidHealth
import io.codecks.domain.privacy.SupportIntervalBucket
import io.codecks.domain.privacy.SupportSpeedBucket
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
import java.security.MessageDigest

private fun Throwable.rethrowIfCancellationOrFatalForUi() {
    when (this) {
        is kotlinx.coroutines.CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        is LinkageError,
        -> throw this
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var hidRepository: HidRepository
    @Inject lateinit var actionRunner: ActionRunner
    @Inject lateinit var actionRepository: ActionRepository
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var backupRepository: CodecksBackupRepository
    @Inject lateinit var reactiveHelperDiscovery: ReactiveHelperDiscovery
    @Inject lateinit var reactiveHelperIdentityStore: ReactiveHelperIdentityStore
    @Inject lateinit var reactiveHelperSecretStore: ReactiveHelperSecretStore
    @Inject lateinit var reactiveHelperPairingImporter: ReactiveHelperPairingImporter

    private var destinationRequest by mutableStateOf<String?>(null)
    private var pendingReactiveHelperPairingJson by mutableStateOf<String?>(null)
    private var pendingSharedText by mutableStateOf<String?>(null)
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
                    sharedText = pendingSharedText,
                    window = window,
                    hidRepository = hidRepository,
                    actionRunner = actionRunner,
                    actionRepository = actionRepository,
                    connectionRepository = connectionRepository,
                    deviceRepository = deviceRepository,
                    backupRepository = backupRepository,
                    reactiveHelperDiscovery = reactiveHelperDiscovery,
                    reactiveHelperIdentityStore = reactiveHelperIdentityStore,
                    reactiveHelperSecretStore = reactiveHelperSecretStore,
                    reactiveHelperPairingImporter = reactiveHelperPairingImporter,
                    pendingReactiveHelperPairingJson = pendingReactiveHelperPairingJson,
                    onReactiveHelperPairingConsumed = { pendingReactiveHelperPairingJson = null },
                    themeSettings = themeSettings,
                    onThemeModeChange = { mode -> themeScope.launch { themeSettingsRepository.setMode(mode) } },
                    onThemeAccentChange = { accent -> themeScope.launch { themeSettingsRepository.setAccent(accent) } },
                    onThemeSurfaceStyleChange = { style -> themeScope.launch { themeSettingsRepository.setSurfaceStyle(style) } },
                    onThemeBorderStyleChange = { style -> themeScope.launch { themeSettingsRepository.setBorderStyle(style) } },
                    onThemeShapeStyleChange = { style -> themeScope.launch { themeSettingsRepository.setShapeStyle(style) } },
                    onDeckStyleChange = { style -> themeScope.launch { themeSettingsRepository.setDeckStyle(style) } },
                    onIconPackChange = { iconPack -> themeScope.launch { themeSettingsRepository.setIconPack(iconPack) } },
                    onRequestConsumed = { destinationRequest = null },
                    onSharedTextConsumed = { pendingSharedText = null },
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
        reactiveHelperPairingJsonFromUri(intent?.dataString)?.let { payload ->
            pendingReactiveHelperPairingJson = payload
            destinationRequest = "pairing"
            return
        }
        destinationRequest = resolveDestinationRequest(
            action = intent?.action,
            type = intent?.type,
            dataUri = intent?.dataString,
            destination = intent?.getStringExtra(EXTRA_DESTINATION),
            providedToken = intent?.getStringExtra(InternalIntentAuth.EXTRA_TOKEN),
            expectedToken = InternalIntentAuth.token(this),
        )
        pendingSharedText = resolveSharedTextFromIntent(intent)
    }

    private fun resolveSharedTextFromIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val clipText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!clipText.isNullOrBlank()) return clipText
        return intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    private fun warmHidIfAllowed() {
        if (BuildConfig.DEBUG) {
            // Debug installs sit beside the protected release app. Do not auto-register
            // a second Bluetooth HID profile unless the tester explicitly starts input.
            return
        }
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

private fun navRouteFromStateKey(routeName: String?): NavKey = when (routeName) {
    "mouse" -> MouseRoute
    "keyboard" -> KeyboardRoute
    "clipboard" -> ClipboardRoute
    "automations" -> AutomationsRoute
    "settings" -> SettingsRoute
    "editor" -> EditorRoute
    "ai_builder" -> AiBuilderRoute
    "ai_provider" -> AiProviderRoute
    "run_log" -> RunLogRoute
    "command_palette" -> CommandPaletteRoute
    else -> HomeRoute
}

private fun routeStateKey(route: NavKey): String = when (route) {
    HomeRoute -> "home"
    MouseRoute -> "mouse"
    KeyboardRoute -> "keyboard"
    ClipboardRoute -> "clipboard"
    AutomationsRoute -> "automations"
    SettingsRoute -> "settings"
    EditorRoute -> "editor"
    AiBuilderRoute -> "ai_builder"
    AiProviderRoute -> "ai_provider"
    RunLogRoute -> "run_log"
    CommandPaletteRoute -> "command_palette"
    else -> "home"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodecksApp(
    destinationRequest: String?,
    sharedText: String? = null,
    window: android.view.Window,
    hidRepository: HidRepository,
    actionRunner: ActionRunner,
    actionRepository: ActionRepository,
    connectionRepository: ConnectionRepository,
    deviceRepository: DeviceRepository,
    backupRepository: CodecksBackupRepository,
    reactiveHelperDiscovery: ReactiveHelperDiscovery,
    reactiveHelperIdentityStore: ReactiveHelperIdentityStore,
    reactiveHelperSecretStore: ReactiveHelperSecretStore,
    reactiveHelperPairingImporter: ReactiveHelperPairingImporter,
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
    onRequestConsumed: () -> Unit,
    onSharedTextConsumed: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    connectionViewModel: ConnectionViewModel = viewModel(),
    automationsViewModel: AutomationsViewModel = viewModel(),
) {
    val restoredTopRouteName = rememberSaveable { mutableStateOf("home") }
    val backStack = rememberNavBackStack(navRouteFromStateKey(restoredTopRouteName.value))
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
    val hostContext = LocalContext.current
    val appContext = hostContext.applicationContext
    val hidConfirmationStore = remember(appContext) { HidConfirmationStore(appContext) }
    var hidTerminalReceipt by remember { mutableStateOf(hidConfirmationStore.load()) }
    val activity = remember(hostContext) { hostContext.findMainActivity() }
    val settingsConnectionSetupController = remember(hostContext, activity, connectionViewModel) {
        ConnectionSetupController(hostContext, activity, connectionViewModel)
    }
    val featureFlagRepository = remember(appContext) { LocalFeatureFlagRepository(appContext) }
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
    var reactiveHelperIdentities by remember { mutableStateOf<List<StoredReactiveHelperIdentity>>(emptyList()) }
    fun refreshReactiveHelperIdentities() {
        scope.launch {
            reactiveHelperIdentities = withContext(Dispatchers.IO) {
                reactiveHelperIdentityStore.identities()
            }
        }
    }
    LaunchedEffect(Unit) {
        reactiveHelperIdentities = withContext(Dispatchers.IO) {
            reactiveHelperIdentityStore.identities()
        }
    }
    LaunchedEffect(pendingReactiveHelperPairingJson) {
        val payload = pendingReactiveHelperPairingJson ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching { reactiveHelperPairingImporter.importJson(payload) }
        }
        if (result.isSuccess) {
            refreshReactiveHelperIdentities()
        }
        snackbarHostState.showSnackbar(
            result.fold(
                onSuccess = { "Codecks helper paired: ${it.displayName}" },
                onFailure = { it.message ?: "Codecks helper pairing failed" },
            ),
        )
        onReactiveHelperPairingConsumed()
    }
    var pendingBackupPayload by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestorePayload by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestorePlan by remember { mutableStateOf<RestorePlan?>(null) }
    var pendingBackupRecovery by remember { mutableStateOf<PendingBackupRecovery?>(null) }
    LaunchedEffect(Unit) {
        pendingBackupRecovery = withContext(Dispatchers.IO) { backupRepository.pendingRecovery() }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val payload = pendingBackupPayload
        pendingBackupPayload = null
        if (uri != null && payload != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        appContext.contentResolver.openOutputStream(uri, "w")
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
                    try {
                        appContext.contentResolver.openInputStream(uri)
                            ?.use { it.readCodecksBackupBounded() }
                            ?: error("Could not open backup file")
                    } catch (error: Throwable) {
                        error.rethrowIfCancellationOrFatalForUi()
                        return@withContext Result.failure(error)
                    }
                        .let { bytes ->
                            try {
                                Result.success(bytes to backupRepository.createRestorePlan(bytes).getOrThrow())
                            } catch (error: Throwable) {
                                error.rethrowIfCancellationOrFatalForUi()
                                Result.failure(error)
                            }
                        }
                }
                result
                    .onSuccess { (bytes, plan) ->
                        pendingRestorePayload = bytes
                        pendingRestorePlan = plan
                    }
                    .onFailure { error ->
                        snackbarHostState.showSnackbar(backupPreviewFailureMessage(error))
                    }
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
    var bluetoothPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val requiredBluetoothPermissions = remember {
        BluetoothPermissionPolicy.requiredRuntimePermissions(Build.VERSION.SDK_INT).toTypedArray()
    }
    val bluetoothPermissionGranted = remember(bluetoothPermissionRefresh) {
        requiredBluetoothPermissions.all { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        bluetoothPermissionRequested = true
        bluetoothPermissionRefresh += 1
        if (results.values.all { it }) hidRepository.start()
    }
    val permissionLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(permissionLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) bluetoothPermissionRefresh += 1
        }
        permissionLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { permissionLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val bluetoothPermissionPermanentlyDenied =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            bluetoothPermissionRequested &&
            !bluetoothPermissionGranted &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) == false
    val requestBluetoothPermission = {
        if (bluetoothPermissionPermanentlyDenied) {
            hostContext.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${hostContext.packageName}"),
                ),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionRequested = true
            bluetoothPermissionLauncher.launch(requiredBluetoothPermissions)
        }
    }
    val confirmAndConnectHid: (String) -> Unit = { address ->
        connectionState.config.takeIf { it.isReady }?.let { config ->
            HidTerminalReceipt(
                setupRevision = connectionState.setupSnapshot.revisionToken(),
                macTargetId = config.setupTargetId(),
                hidHostToken = hidHostToken(address),
                result = HidTerminalResult.USER_CONFIRMED,
                completedAtEpochMs = System.currentTimeMillis(),
            ).also {
                hidConfirmationStore.record(it)
                hidTerminalReceipt = it
            }
        }
        hidRepository.connect(address)
    }
    val runtimeSetupCompletion = evaluateRuntimeSetupCompletion(
        state = connectionState,
        hidState = hidState,
        permissionState = when {
            bluetoothPermissionGranted -> BluetoothPermissionState.Granted
            bluetoothPermissionPermanentlyDenied -> BluetoothPermissionState.PermanentlyDenied
            else -> BluetoothPermissionState.Denied
        },
        hidReceipt = hidTerminalReceipt,
        nowEpochMs = proofClockEpochMs,
    )
    val runtimeReadiness = codecksReadiness(
        connectionHealth = connectionHealth,
        hidHealth = hidState.hidHealth(bluetoothPermissionGranted),
        aiReady = aiProviderReady,
        setupCompletion = runtimeSetupCompletion,
    )
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
    val smartDeckViewModel: SmartDeckViewModel = viewModel()
    var smartSelectedMacId by remember { mutableStateOf<SmartMacId?>(null) }
    LaunchedEffect(connectionState.config, currentRoute, currentRoute == HomeRoute) {
        smartSelectedMacId = runCatching {
            deviceRepository.currentDeviceId()?.value?.let { SmartMacId(it) }
        }.getOrNull()
    }
    val reactiveHelperSessionManager = remember(
        reactiveHelperIdentityStore,
        reactiveHelperSecretStore,
    ) {
        val androidId = Settings.Secure
            .getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            .ifBlank { "unknown" }
        ReactiveHelperSessionManager(
            identityStore = reactiveHelperIdentityStore,
            secretStore = reactiveHelperSecretStore,
            transportFactory = TcpReactiveHelperTransportFactory(),
            deviceId = "android-$androidId",
        )
    }
    val discoveredReactiveHelpers by reactiveHelperDiscovery.helpers.collectAsStateWithLifecycle(emptyList())
    val reactiveHelperStatus by reactiveHelperSessionManager.status.collectAsStateWithLifecycle()
    DisposableEffect(reactiveHelperDiscovery) {
        reactiveHelperDiscovery.start()
        onDispose { reactiveHelperDiscovery.stop() }
    }
    fun codecksHelperEndpoint(): ReactiveHelperEndpoint? {
        val stored = reactiveHelperIdentities.firstOrNull()
        val host = stored?.host
        val port = stored?.port
        if (!host.isNullOrBlank() && port != null) return ReactiveHelperEndpoint(host, port)
        val discovered = discoveredReactiveHelpers.firstOrNull()
        return if (discovered != null) ReactiveHelperEndpoint(discovered.host, discovered.port) else null
    }
    LaunchedEffect(discoveredReactiveHelpers, smartSelectedMacId, reactiveHelperIdentities, reactiveHelperStatus) {
        val selectedMacId = smartSelectedMacId?.value ?: reactiveHelperIdentities.firstOrNull()?.macId ?: return@LaunchedEffect
        when (reactiveHelperStatus) {
            is ReactiveHelperSessionStatus.Connected,
            is ReactiveHelperSessionStatus.Connecting,
            is ReactiveHelperSessionStatus.Failed,
            -> return@LaunchedEffect
            else -> Unit
        }
        val endpoint = codecksHelperEndpoint() ?: return@LaunchedEffect
        runCatching {
            reactiveHelperSessionManager.connect(
                endpoint = endpoint,
                macId = selectedMacId,
            )
        }
    }
    val savedCodecksHelperEndpoint = reactiveHelperIdentities.firstOrNull()?.let { identity ->
        !identity.host.isNullOrBlank() && identity.port != null
    } == true
    val codecksHelperState = codecksHelperUiState(
        pairedDisplayName = reactiveHelperIdentities.firstOrNull()?.displayName,
        connectionKind = when (reactiveHelperStatus) {
            is ReactiveHelperSessionStatus.Connected -> CodecksHelperConnectionKind.Connected
            is ReactiveHelperSessionStatus.Connecting -> CodecksHelperConnectionKind.Connecting
            is ReactiveHelperSessionStatus.Failed -> CodecksHelperConnectionKind.Failed
            ReactiveHelperSessionStatus.Idle -> CodecksHelperConnectionKind.Idle
        },
        discoveredCount = discoveredReactiveHelpers.size,
        hasSavedEndpoint = savedCodecksHelperEndpoint,
        failureCode = (reactiveHelperStatus as? ReactiveHelperSessionStatus.Failed)?.code,
    )
    val connectCodecksHelper: () -> Unit = {
        scope.launch {
            val endpoint = codecksHelperEndpoint()
            val macId = smartSelectedMacId?.value ?: reactiveHelperIdentities.firstOrNull()?.macId
            when {
                macId == null -> snackbarHostState.showSnackbar("Pair Codecks helper first")
                endpoint == null -> snackbarHostState.showSnackbar("Open Codecks Mac helper on your Mac")
                else -> {
                    val result = withContext(Dispatchers.IO) {
                        reactiveHelperSessionManager.connect(
                            endpoint = endpoint,
                            macId = macId,
                        )
                    }
                    snackbarHostState.showSnackbar(
                        when (result) {
                            is ReactiveHelperSessionStatus.Connected -> "Codecks helper connected"
                            is ReactiveHelperSessionStatus.Connecting -> "Codecks helper connecting"
                            is ReactiveHelperSessionStatus.Failed -> "Codecks helper failed: ${result.code}"
                            ReactiveHelperSessionStatus.Idle -> "Codecks helper idle"
                        },
                    )
                }
            }
        }
    }
    val runCodecksHelperSpotlight: (String) -> Unit = { query ->
        scope.launch {
            if (query.isBlank()) {
                snackbarHostState.showSnackbar("Enter a Mac search query")
                return@launch
            }
            val sanitizedQuery = query.take(120)
            val operationId = "codecks-spotlight-${UUID.randomUUID()}"
            val request = ReactiveHelperRequest.Execute(
                actionId = "spotlight.search",
                actionRevision = codecksSpotlightActionRevision(sanitizedQuery),
                operationId = operationId,
                idempotencyKey = operationId,
                timeoutMillis = 10_000L,
                cancellationToken = operationId,
                arguments = mapOf(
                    "query" to sanitizedQuery,
                    "maxResults" to "8",
                ),
            )
            val execution = withContext(Dispatchers.IO) {
                reactiveHelperSessionManager.actionClient.value.execute(
                    request = request,
                    deadlineMillis = System.currentTimeMillis() + 10_000L,
                )
            }
            snackbarHostState.showSnackbar(
                when (execution) {
                    is ReactiveHelperActionExecution.Succeeded -> {
                        val count = Regex("""^spotlight_results_(\d+)$""")
                            .matchEntire(execution.resultCode)
                            ?.groupValues
                            ?.get(1)
                            ?: "?"
                        "Codecks found $count Mac matches"
                    }
                    is ReactiveHelperActionExecution.Failed -> "Codecks search failed: ${execution.errorCode}"
                    is ReactiveHelperActionExecution.Unsupported -> "Codecks helper unavailable: ${execution.reasonCode}"
                    is ReactiveHelperActionExecution.RequiresReview -> "Codecks search needs review: ${execution.reason}"
                    ReactiveHelperActionExecution.Expired -> "Codecks search timed out"
                },
            )
        }
    }
    val reactiveMacStateRepository = remember(connectionRepository, reactiveHelperSessionManager) {
        LiveMacStateRepository(
            helperSource = StateFlowReactiveHelperClientMacStateSource(reactiveHelperSessionManager.client),
            sshSource = ConnectionRepositorySshMacStateSource(connectionRepository),
        )
    }
    val reactiveReceiptStore = remember { InMemoryReactiveReceiptStore() }
    val reactiveEngine = remember(actionRepository, reactiveReceiptStore) {
        defaultReactiveTrackpadEngine(
            actionRevisions = actionRepository.allActions().associate { action ->
                action.id to action.reactiveActionRevision()
            },
            receipts = reactiveReceiptStore::all,
        )
    }
    val reactiveExecutor = remember(
        actionRepository,
        actionRunner,
        hidRepository,
        reactiveReceiptStore,
        connectionRepository,
        reactiveHelperSessionManager,
    ) {
        DefaultReactiveActionExecutor(
            actionRepository = actionRepository,
            actionRunner = actionRunner,
            hidRepository = hidRepository,
            receiptStore = reactiveReceiptStore,
            helperActionClient = StateFlowReactiveHelperActionClient(reactiveHelperSessionManager.actionClient),
            sftpTransferClient = ConnectionRepositoryReactiveSftpTransferClient(connectionRepository),
        )
    }
    val reactiveTrackpadViewModel: ReactiveTrackpadViewModel = viewModel(
        key = "reactive-trackpad",
        factory = remember(reactiveMacStateRepository, reactiveEngine, reactiveExecutor) {
            reactiveTrackpadViewModelFactory(
                macStateRepository = reactiveMacStateRepository,
                engine = reactiveEngine,
                executor = reactiveExecutor,
            )
        },
    )
    LaunchedEffect(
        smartSelectedMacId,
        runtimeReadiness.macCommandsReady,
        hidState.isConnected,
        homeState.activeMacApp,
    ) {
        reactiveMacStateRepository.update(
            LiveMacStateInputs(
                selectedMacId = smartSelectedMacId?.value,
                macCommandsReady = runtimeReadiness.macCommandsReady,
                macInputConnected = hidState.isConnected,
                activeMacApp = homeState.activeMacApp,
            ),
        )
    }
    LaunchedEffect(currentRoute, reactiveTrackpadEnabled) {
        reactiveTrackpadViewModel.setVisible(reactiveTrackpadEnabled && currentRoute == MouseRoute)
    }
    val smartSuggestions by smartDeckViewModel.suggestions.collectAsStateWithLifecycle(emptyList())
    val smartRunPending by smartDeckViewModel.runPending.collectAsStateWithLifecycle()
    val pendingDangerousSmartSuggestion by smartDeckViewModel.pendingDangerousSuggestion.collectAsStateWithLifecycle()
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
        smartDeckViewModel.updateInputs(
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
    var pendingDangerousAction by remember { mutableStateOf<DeckAction?>(null) }
    var acceptedSmartHomeRunId by remember { mutableStateOf<SmartRunId?>(null) }
    var selectedDeckSlot by remember { mutableStateOf(0) }
    var aiPlacementSlot by remember { mutableStateOf<Int?>(null) }
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

    LaunchedEffect(runtimeReadiness.macCommandsReady) {
        if (runtimeReadiness.macCommandsReady) automationsViewModel.startTriggerMonitor()
    }

    LaunchedEffect(currentRoute, runtimeReadiness.macCommandsReady, homeState.dynamicDeckEnabled) {
        if (currentRoute == HomeRoute && runtimeReadiness.macCommandsReady && homeState.dynamicDeckEnabled) {
            while (true) {
                homeViewModel.refreshActiveMacApp()
                delay(10_000)
            }
        }
    }
    LaunchedEffect(currentRoute, runtimeReadiness.macCommandsReady, reactiveTrackpadEnabled) {
        if (currentRoute == MouseRoute && runtimeReadiness.macCommandsReady && reactiveTrackpadEnabled) {
            homeViewModel.refreshActiveMacApp()
            while (true) {
                delay(10_000)
                homeViewModel.refreshActiveMacApp()
            }
        }
    }

    val currentNavigate by rememberUpdatedState<(NavKey, Boolean) -> Unit> { route, topLevel ->
        navigate(route, topLevel)
    }
    val currentMacInputConnected by rememberUpdatedState(hidState.isConnected)
    val currentMacCommandsReady by rememberUpdatedState(runtimeReadiness.macCommandsReady)
    val localActionDispatcher = remember(hidRepository, scope, snackbarHostState) {
        LocalActionDispatcher(
            onTrackpad = { currentNavigate(MouseRoute, true) },
            onKeyboard = { currentNavigate(KeyboardRoute, false) },
            onAutomations = { currentNavigate(AutomationsRoute, false) },
            onClipboard = { currentNavigate(ClipboardRoute, false) },
            onSettings = { currentNavigate(SettingsRoute, false) },
            onEditor = { currentNavigate(HomeRoute, true) },
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
        if (!runtimeReadiness.macCommandsReady) {
            scope.launch {
                snackbarHostState.showSnackbar("Test the Mac connection before running this action")
            }
            return LocalActionResult.Failed("Mac controls are not verified")
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
                        if (!currentMacCommandsReady) {
                            smartDeckViewModel.onExecutionRejected(request.id)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Test the Mac connection before running this suggestion",
                                )
                            }
                        } else when (
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
        when (val feedback = homeStatusFeedback(status)) {
            HomeStatusFeedback.None -> Unit
            is HomeStatusFeedback.TileOnly -> {
                delay(feedback.lingerMillis)
                homeViewModel.consumeResult()
            }
            is HomeStatusFeedback.Snackbar -> {
                val result = snackbarHostState.showSnackbar(
                    message = feedback.message,
                    actionLabel = feedback.actionLabel,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    if (feedback.actionLabel == "Undo") {
                        homeViewModel.undoLastDeckEdit()
                    } else {
                        navigate(SettingsRoute)
                    }
                }
                homeViewModel.consumeResult()
            }
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
                if (route == AiBuilderRoute) aiPlacementSlot = null
                navigate(
                    route,
                    topLevel = route in setOf(HomeRoute, MouseRoute, KeyboardRoute, ClipboardRoute),
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
                                ReactiveTrackpadCard(
                                    enabled = reactiveTrackpadEnabled,
                                    viewModel = reactiveTrackpadViewModel,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
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
                            onRestoreRecovery = {
                                importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
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
                        val settingsLifecycleOwner = LocalLifecycleOwner.current
                        val updateViewModel: UpdateViewModel = viewModel {
                            UpdateViewModel(
                                appForeground = {
                                    settingsLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                },
                                terminalEvent = { result, timestamp ->
                                    DiagnosticEventStore(appContext).recordTerminal(
                                        component = DiagnosticComponent.UPDATE,
                                        result = result,
                                        timestampEpochMs = timestamp,
                                    )
                                },
                            )
                        }
                        val updateState by updateViewModel.state.collectAsStateWithLifecycle()
                        val supportBundleViewModel: SupportBundleViewModel = viewModel {
                            SupportBundleViewModel(SupportBundleTempFilePolicy(appContext.cacheDir))
                        }
                        val supportBundleState by supportBundleViewModel.state.collectAsStateWithLifecycle()
                        val supportShareCallbackAction = remember(appContext) {
                            "${appContext.packageName}.SUPPORT_SHARE_TARGET_CHOSEN"
                        }
                        DisposableEffect(supportBundleViewModel, supportShareCallbackAction) {
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    if (intent?.action == supportShareCallbackAction) {
                                        supportBundleViewModel.shareTargetChosen()
                                    }
                                }
                            }
                            ContextCompat.registerReceiver(
                                appContext,
                                receiver,
                                IntentFilter(supportShareCallbackAction),
                                ContextCompat.RECEIVER_NOT_EXPORTED,
                            )
                            onDispose { runCatching { appContext.unregisterReceiver(receiver) } }
                        }
                        LaunchedEffect(supportBundleState) {
                            val ready = supportBundleState as? SupportBundleUiState.Ready
                                ?: return@LaunchedEffect
                            if (shareSupportBundle(appContext, ready.file, supportShareCallbackAction)) {
                                supportBundleViewModel.chooserOpened()
                            } else {
                                supportBundleViewModel.shareFailed()
                            }
                        }
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
                            onReactiveHelperPairingImport = { payload ->
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { reactiveHelperPairingImporter.importJson(payload) }
                                    }
                                    if (result.isSuccess) {
                                        refreshReactiveHelperIdentities()
                                    }
                                    snackbarHostState.showSnackbar(
                                        result.fold(
                                            onSuccess = { "Codecks helper paired: ${it.displayName}" },
                                            onFailure = { it.message ?: "Codecks helper pairing failed" },
                                        ),
                                    )
                                }
                            },
                            onOpenMacHelper = {
                                appContext.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://vaddisrinivas.github.io/codecks/mac-helper/"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            codecksHelperState = codecksHelperState,
                            onCodecksHelperConnect = connectCodecksHelper,
                            onCodecksHelperSearch = runCodecksHelperSpotlight,
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
                            onDeck = { navigate(HomeRoute, topLevel = true) },
                            onKeyboard = { navigate(KeyboardRoute) },
                            onClipboard = { navigate(ClipboardRoute) },
                            onExportBackup = {
                                scope.launch {
                                    backupRepository.exportArchive()
                                        .onSuccess { payload ->
                                            pendingBackupPayload = payload
                                            exportBackupLauncher.launch("codecks-backup-${System.currentTimeMillis()}.codecks.zip")
                                        }
                                        .onFailure { error ->
                                            snackbarHostState.showSnackbar(error.message ?: "Backup failed")
                                        }
                                }
                            },
                            onImportBackup = {
                                importBackupLauncher.launch(
                                    arrayOf("application/zip", "application/octet-stream", "application/json", "text/plain"),
                                )
                            },
                            pendingBackupRecovery = pendingBackupRecovery != null,
                            corruptBackupRecovery = pendingBackupRecovery is PendingBackupRecovery.Corrupt,
                            onRecoverBackup = {
                                pendingBackupRecovery?.let { recovery ->
                                    scope.launch {
                                        if (recovery is PendingBackupRecovery.Corrupt) {
                                            val choice = snackbarHostState.showSnackbar(
                                                message = "Recovery data is unreadable. Quarantine it to allow future restores.",
                                                actionLabel = "Quarantine",
                                                withDismissAction = true,
                                            )
                                            if (choice == SnackbarResult.ActionPerformed) {
                                                val result = withContext(Dispatchers.IO) {
                                                    backupRepository.quarantineCorruptRecovery(recovery.recoveryId)
                                                }
                                                if (result.isSuccess) pendingBackupRecovery = backupRepository.pendingRecovery()
                                                snackbarHostState.showSnackbar(
                                                    if (result.isSuccess) "Corrupt recovery quarantined"
                                                    else "Could not quarantine recovery data",
                                                )
                                            }
                                            return@launch
                                        }
                                        val result = withContext(Dispatchers.IO) {
                                            backupRepository.recoverPending(recovery.recoveryId)
                                        }
                                        if (result.isSuccess) pendingBackupRecovery = backupRepository.pendingRecovery()
                                        snackbarHostState.showSnackbar(
                                            if (result.isSuccess) "Previous Deck and Rules recovered"
                                            else "Recovery failed; preserved data remains available",
                                        )
                                    }
                                }
                            },
                            restorePlan = pendingRestorePlan,
                            onCancelRestore = {
                                pendingRestorePayload = null
                                pendingRestorePlan = null
                            },
                            onConfirmRestore = { planId ->
                                val bytes = pendingRestorePayload
                                if (bytes != null) {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            backupRepository.restoreConfirmed(planId, bytes)
                                        }
                                        val outcome = result.getOrNull()
                                        if (outcome is io.codecks.domain.backup.BackupRestoreResult.Committed) {
                                            pendingRestorePayload = null
                                            pendingRestorePlan = null
                                        } else if (outcome is io.codecks.domain.backup.BackupRestoreResult.RecoveryRequired) {
                                            pendingBackupRecovery = backupRepository.pendingRecovery()
                                            pendingRestorePayload = null
                                            pendingRestorePlan = null
                                        }
                                        snackbarHostState.showSnackbar(
                                            when (outcome) {
                                                is io.codecks.domain.backup.BackupRestoreResult.Committed ->
                                                    "Deck and Rules restored"
                                                is io.codecks.domain.backup.BackupRestoreResult.RolledBack ->
                                                    "Restore failed; previous data restored"
                                                is io.codecks.domain.backup.BackupRestoreResult.RecoveryRequired ->
                                                    "Restore incomplete; recovery data preserved"
                                                null -> "Restore preview expired; review again"
                                            },
                                        )
                                    }
                                }
                            },
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
                            onAppearance = {},
                            onAdvanced = {},
                            onDebugBundle = supportBundleViewModel::preview,
                            supportBundleState = supportBundleState,
                            onGenerateSupportBundle = {
                                supportBundleViewModel.generate(
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
                            trackpadSettings = trackpadSettings,
                            onTrackpadSettingsChange = { transform ->
                                scope.launch { trackpadSettingsRepository.update(transform) }
                            },
                            localOnlyV1 = localOnlyV1,
                            debugBundleEnabled = BuildConfig.DEBUG,
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
                                if (automationsViewModel.saveArtifact(artifact)) {
                                    aiPlacementSlot = null
                                } else {
                                    homeViewModel.requestArtifactPlacement(artifact, aiPlacementSlot)
                                    aiPlacementSlot = null
                                    navigate(HomeRoute, topLevel = true)
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
    bluetoothPermissionGranted: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onConnectHost: (String) -> Unit,
    viewModel: KeyboardViewModel = viewModel(),
) {
    val state by viewModel.hidState.collectAsStateWithLifecycle()
    val keyboardState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bluetoothPermissionGranted) {
        if (bluetoothPermissionGranted) viewModel.start()
    }

    KeyboardScreen(
        state = state,
        text = keyboardState.text,
        contentPadding = contentPadding,
        permissionGranted = bluetoothPermissionGranted,
        deliveryMode = keyboardState.deliveryMode,
        isSending = keyboardState.isSending,
        sendStatus = keyboardState.status,
        recentSends = keyboardState.recentSends,
        snippets = keyboardState.snippets,
        onRequestPermission = onRequestBluetoothPermission,
        onStart = viewModel::start,
        onRefreshHosts = viewModel::refreshHosts,
        onConnect = onConnectHost,
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

private fun openCodecksAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

internal fun backupPreviewFailureMessage(error: Throwable): String = when (error) {
    is BackupInputTooLargeException -> "Backup is too large to preview safely"
    is PendingBackupRecoveryException -> "Finish the pending backup recovery first"
    is java.io.IOException -> "Backup file could not be read"
    else -> "Backup is invalid or unsupported"
}

private fun createSupportBundleSnapshot(
    context: Context,
    homeState: io.codecks.ui.home.HomeUiState,
    connectionState: io.codecks.ui.connection.ConnectionUiState,
    hidState: HidState,
    featureFlags: Map<FeatureFlag, Boolean>,
    trackpadSettings: TrackpadSettings,
    clipboardSettings: ClipboardSyncSettings,
): SupportBundleSnapshot {
    val createdAt = System.currentTimeMillis()
    val config = connectionState.config
    val eventStore = DiagnosticEventStore(context)
    return SupportBundleSnapshot(
        manifest = SupportBundleManifest(
            appVersionCode = BuildConfig.VERSION_CODE,
            debugBuild = BuildConfig.DEBUG,
            createdAtEpochMs = createdAt,
        ),
        health = SupportBundleHealth(
            connection = when {
                config.isReady -> SupportConnectionHealth.READY
                config.isConfigured -> SupportConnectionHealth.CONFIGURED
                else -> SupportConnectionHealth.UNCONFIGURED
            },
            sshKeyPresent = config.hasKey,
            pinnedIdentityPresent = config.hostKey.isNotBlank(),
            hid = when {
                hidState.isConnected -> SupportHidHealth.CONNECTED
                hidState.isReady -> SupportHidHealth.READY
                else -> SupportHidHealth.UNAVAILABLE
            },
            knownHostCount = hidState.hosts.size,
            visibleActionCount = homeState.actions.size,
            catalogActionCount = homeState.allActions.size,
            action = when (homeState.actionStatus) {
                ActionStatus.Idle -> SupportActionHealth.IDLE
                is ActionStatus.Running -> SupportActionHealth.RUNNING
                is ActionStatus.Succeeded -> SupportActionHealth.SUCCEEDED
                is ActionStatus.Failed -> SupportActionHealth.FAILED
            },
            activityCount = homeState.activity.size,
            activityFailureCount = homeState.activity.count { !it.succeeded },
        ),
        events = eventStore.events(),
        settings = SupportBundleSettings(
            pointerSpeed = when {
                trackpadSettings.pointerSpeed < 0.75f -> SupportSpeedBucket.LOW
                trackpadSettings.pointerSpeed > 1.15f -> SupportSpeedBucket.HIGH
                else -> SupportSpeedBucket.MEDIUM
            },
            scrollRailEnabled = trackpadSettings.scrollRailEnabled,
            hapticsEnabled = trackpadSettings.hapticsEnabled,
            pointerTraceEnabled = trackpadSettings.pointerTraceEnabled,
            clipboardEnabled = clipboardSettings.mode != ClipboardSyncMode.Off,
            clipboardInterval = when {
                clipboardSettings.intervalMinutes <= 5 -> SupportIntervalBucket.SHORT
                clipboardSettings.intervalMinutes <= 20 -> SupportIntervalBucket.MEDIUM
                else -> SupportIntervalBucket.LONG
            },
            dynamicDeckEnabled = homeState.dynamicDeckEnabled,
            featureOverrideCount = featureFlags.count { (flag, value) -> DEFAULT_FEATURE_FLAGS[flag] != value },
            labsEnabled = featureFlags[FeatureFlag.Labs] == true,
        ),
    )
}

private fun shareSupportBundle(context: Context, file: File, callbackAction: String): Boolean =
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.supportfiles", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Codecks support bundle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val callback = PendingIntent.getBroadcast(
            context,
            file.name.hashCode(),
            Intent(callbackAction).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.startActivity(
            Intent.createChooser(send, "Share Codecks support bundle", callback.intentSender)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

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
    bluetoothPermissionGranted: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onConnectHost: (String) -> Unit,
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
    LaunchedEffect(bluetoothPermissionGranted) {
        if (bluetoothPermissionGranted) viewModel.start()
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
        permissionGranted = bluetoothPermissionGranted,
        onRequestPermission = onRequestBluetoothPermission,
        onStart = viewModel::start,
        onRefreshHosts = viewModel::refreshHosts,
        onConnect = onConnectHost,
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

private fun codecksSpotlightActionRevision(query: String): String =
    "spotlight-${MessageDigest.getInstance("SHA-256")
        .digest(query.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(64)}"
