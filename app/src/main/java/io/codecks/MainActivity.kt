package io.codecks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import io.codecks.core.actions.ActionRunner
import io.codecks.data.ActionRepository
import io.codecks.data.CodecksBackupRepository
import io.codecks.data.ConnectionRepository
import io.codecks.data.reactive.helper.ReactiveHelperPairingImporter
import io.codecks.data.reactive.helper.reactiveHelperPairingJsonFromUri
import io.codecks.domain.device.DeviceRepository
import io.codecks.launcher.LauncherIconManager
import io.codecks.navigation.CommandPaletteRoute
import io.codecks.navigation.SettingsRoute
import io.codecks.platform.helper.ReactiveHelperDiscovery
import io.codecks.platform.helper.ReactiveHelperIdentityStore
import io.codecks.platform.helper.ReactiveHelperSecretStore
import io.codecks.ui.app.RouteBuildExposure
import io.codecks.ui.app.RouteRegistry
import io.codecks.ui.theme.CodecksTheme
import io.codecks.ui.theme.CodecksThemeSettings
import io.codecks.ui.theme.ThemeSettingsRepository
import io.codecks.ui.theme.resolveForCodecksRelease
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var hidRepository: HidRepository
    @Inject lateinit var actionRunner: ActionRunner
    @Inject lateinit var actionRepository: ActionRepository
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var backupRepository: CodecksBackupRepository
    @Inject lateinit var reactiveHelperDiscovery: Lazy<ReactiveHelperDiscovery>
    @Inject lateinit var reactiveHelperIdentityStore: Lazy<ReactiveHelperIdentityStore>
    @Inject lateinit var reactiveHelperSecretStore: Lazy<ReactiveHelperSecretStore>
    @Inject lateinit var reactiveHelperPairingImporter: Lazy<ReactiveHelperPairingImporter>

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
            val launcherIconManager = remember(appContext) { LauncherIconManager(appContext) }
            var launcherIcon by remember { mutableStateOf(launcherIconManager.current()) }
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
                    bindings = AppFeatureBindings(
                        core = CoreFeatureBindings(
                            hidRepository = hidRepository,
                            actionRunner = actionRunner,
                            actionRepository = actionRepository,
                            connectionRepository = connectionRepository,
                            deviceRepository = deviceRepository,
                            backupRepository = backupRepository,
                        ),
                        optional = OptionalFeatureBinders(
                            helperDiscovery = reactiveHelperDiscovery,
                            helperIdentityStore = reactiveHelperIdentityStore,
                            helperSecretStore = reactiveHelperSecretStore,
                            helperPairingImporter = reactiveHelperPairingImporter,
                        ),
                    ),
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
                    launcherIcon = launcherIcon,
                    onLauncherIconChange = { selected ->
                        launcherIconManager.select(selected).onSuccess { launcherIcon = it }
                    },
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
            destinationRequest = RouteRegistry.requestAlias(CommandPaletteRoute)
            return true
        }
        if (hardwareKeyHandler?.invoke(event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    fun setHardwareKeyHandler(handler: ((KeyEvent) -> Boolean)?) {
        hardwareKeyHandler = handler
    }

    private fun acceptIntent(intent: Intent?) {
        if (RouteRegistry.publicDeepLinkRoute(intent?.dataString, RouteBuildExposure.PUBLIC) == SettingsRoute) {
            val pairingPrefix = requireNotNull(RouteRegistry.descriptor(SettingsRoute)).publicDeepLinks.single()
            reactiveHelperPairingJsonFromUri(intent?.dataString, pairingPrefix)?.let { payload ->
                pendingReactiveHelperPairingJson = payload
                destinationRequest = RouteRegistry.requestAlias(SettingsRoute)
                return
            }
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
