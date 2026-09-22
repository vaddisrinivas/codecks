package io.codecks.internalquality

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation3.runtime.NavKey
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.MouseRoute
import io.codecks.ui.app.CodecksAppShell
import io.codecks.ui.app.RouteRegistry
import io.codecks.ui.designsystem.DeckActionButton
import io.codecks.ui.mouse.RawTrackpadView
import io.codecks.ui.settings.SupportBundleDialog
import io.codecks.ui.settings.SupportBundlePreviewSummary
import io.codecks.ui.settings.SupportBundleUiState
import io.codecks.ui.theme.CodecksTheme

/** Internal-only host that exercises production Deck, Trackpad, shell, and support-dialog surfaces. */
class M11DexProxyActivity : ComponentActivity() {
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    lateinit var probeView: View
        private set
    private lateinit var dialogVisible: MutableState<Boolean>
    var pointerEvents: Int = 0
        private set
    var deckEvents: Int = 0
        private set
    var navigationEvents: Int = 0
        private set
    var dialogEvents: Int = 0
        private set
    var selectedRoute: NavKey = HomeRoute
        private set
    var keyboardEvents: Int = 0
        private set
    var windowMarker: Int = 0
        private set
    var durableMarker: Int = 0
        private set
    var productSurfacesReady: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowMarker = savedInstanceState?.getInt(WINDOW_MARKER) ?: 0
        durableMarker = preferences.getInt(DURABLE_MARKER, 0)
        setContent {
            CodecksTheme {
                var route by remember { mutableStateOf<NavKey>(HomeRoute) }
                dialogVisible = remember { mutableStateOf(false) }
                CodecksAppShell(
                    snackbarHostState = remember { SnackbarHostState() },
                    currentRoute = route,
                    backStackSize = 1,
                    fullscreen = false,
                    tabs = RouteRegistry.primaryDestinations().filter { it.route == HomeRoute || it.route == MouseRoute },
                    onBack = {},
                    onDestinationSelected = {
                        route = it
                        selectedRoute = it
                        navigationEvents += 1
                    },
                    onOpenSettings = {},
                    onRequestFullscreen = {},
                    onExitFullscreen = {},
                ) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        DeckActionButton(
                            label = "Deck action",
                            onClick = { deckEvents += 1 },
                            modifier = Modifier.fillMaxWidth().testTag("m11-deck-action"),
                        )
                        AndroidView(
                            factory = { context ->
                                RawTrackpadView(context).apply {
                                    contentDescription = "M11 Trackpad surface"
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    onLeftClick = ::recordPointer
                                    requestFocus()
                                    probeView = this
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(160.dp).testTag("m11-trackpad"),
                        )
                        SideEffect { productSurfacesReady = true }
                    }
                }
                if (dialogVisible.value) {
                    SupportBundleDialog(
                        state = SupportBundleUiState.Preview(
                            summary = SupportBundlePreviewSummary(
                                build = "M11 bounded preview",
                                connection = "Ready",
                                hid = "Ready",
                                bluetoothPermission = "Granted",
                                notificationPermission = "Granted",
                                batteryPolicy = "Optimized",
                            ),
                            includedSections = listOf("Redacted health", "Bounded operation receipts"),
                        ),
                        onGenerate = {
                            dialogEvents += 1
                            dialogVisible.value = false
                        },
                        onCancel = { dialogVisible.value = false },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(WINDOW_MARKER, windowMarker)
        super.onSaveInstanceState(outState)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && probeView.hasFocus()) {
            keyboardEvents += 1
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun openProductDialog() { dialogVisible.value = true }

    fun resetDurableMarker() {
        durableMarker = 0
        preferences.edit().remove(DURABLE_MARKER).commit()
    }

    private fun recordPointer() {
        pointerEvents += 1
        windowMarker += 1
        durableMarker += 1
        preferences.edit().putInt(DURABLE_MARKER, durableMarker).commit()
    }

    companion object {
        private const val PREFERENCES = "m11_dex_proxy"
        private const val WINDOW_MARKER = "window_marker"
        private const val DURABLE_MARKER = "durable_marker"
    }
}
