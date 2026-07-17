package io.codex.s23deck

import android.bluetooth.BluetoothDevice
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HidHost(
    val address: String,
    val label: String,
)

enum class HidLifecycle {
    Idle,
    Opening,
    Ready,
    Connected,
    PermissionMissing,
    Unavailable,
    Failed,
}

data class HidState(
    val status: String = "Bluetooth idle",
    val lifecycle: HidLifecycle = HidLifecycle.Idle,
    val isReady: Boolean = false,
    val isConnected: Boolean = false,
    val hosts: List<HidHost> = emptyList(),
    val selectedHostAddress: String? = null,
)

enum class HidCommand {
    Copy,
    Paste,
    Cut,
    SelectAll,
    Undo,
    Redo,
    Find,
    Save,
    NewDocument,
    OpenDocument,
    CloseWindow,
    Enter,
    Tab,
    Escape,
    Backspace,
    ForwardDelete,
    LineStart,
    LineEnd,
    WordLeft,
    WordRight,
    Spotlight,
    MissionControl,
    AppExpose,
    Launchpad,
    ShowDesktop,
    NotificationCenter,
    AppSwitcher,
    WindowSwitcher,
    SpaceLeft,
    SpaceRight,
    ScreenshotArea,
    ScreenshotWindow,
    PresentationPrevious,
    PresentationNext,
    PresentationPlayPause,
    PresentationStart,
    PresentationFirst,
    PresentationLast,
    PresentationBlack,
    PresentationWhite,
    PresentationExit,
    MediaPlayPause,
    MediaPrevious,
    MediaNext,
    MediaMute,
    MediaVolumeDown,
    MediaVolumeUp,
}

interface HidRepository {
    val state: StateFlow<HidState>
    fun start()
    fun refreshHosts()
    fun connect(address: String)
    fun disconnect()
    fun move(dx: Int, dy: Int)
    fun scroll(vertical: Int, horizontal: Int = 0)
    fun click(buttonMask: Int)
    fun press(buttonMask: Int)
    fun releaseButtons()
    fun typeText(text: String)
    fun send(command: HidCommand)
}

@Singleton
class DefaultHidRepository @Inject constructor(
    @ApplicationContext context: Context,
) : HidRepository {
    private val prefs = context.getSharedPreferences("hid_repository", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(HidState())
    override val state: StateFlow<HidState> = _state.asStateFlow()
    private var devices: List<BluetoothDevice> = emptyList()
    private var lastConnectAttemptAddress: String? = null
    private val controller = HidController(context) { status ->
        refreshState(status)
    }

    init {
        _state.value = _state.value.copy(selectedHostAddress = prefs.getString(PREF_SELECTED_HOST, null))
    }

    override fun start() {
        controller.openProfile()
        refreshHosts()
    }

    override fun refreshHosts() {
        devices = controller.bondedDevices()
        val hosts = devices.mapNotNull { device ->
            runCatching {
                HidHost(device.address, HidController.deviceLabel(device))
            }.getOrNull()
        }
        val selected = _state.value.selectedHostAddress
        val selectedStillBonded = selected == null || hosts.any { it.address == selected }
        if (!selectedStillBonded) {
            prefs.edit().remove(PREF_SELECTED_HOST).apply()
        }
        _state.update { state ->
            val nextSelected = state.selectedHostAddress?.takeIf { address -> hosts.any { it.address == address } }
            state.copy(
                hosts = prioritizeHosts(hosts, nextSelected),
                selectedHostAddress = nextSelected,
                status = if (state.selectedHostAddress != null && nextSelected == null) {
                    "Saved Bluetooth target unavailable"
                } else {
                    state.status
                },
            )
        }
        attemptAutoReconnect()
    }

    override fun connect(address: String) {
        saveSelectedHost(address)
        lastConnectAttemptAddress = address
        devices.firstOrNull { runCatching { it.address == address }.getOrDefault(false) }
            ?.let(controller::connect)
    }

    override fun disconnect() = controller.disconnect()
    override fun move(dx: Int, dy: Int) = controller.sendMouse(dx, dy, 0, 0)
    override fun scroll(vertical: Int, horizontal: Int) = controller.sendMouse(0, 0, vertical, horizontal)
    override fun click(buttonMask: Int) = controller.click(buttonMask)
    override fun press(buttonMask: Int) = controller.setMouseButtons(buttonMask)
    override fun releaseButtons() = controller.releaseAllInputs()
    override fun typeText(text: String) = controller.typeText(text)
    override fun send(command: HidCommand) {
        when (command) {
            HidCommand.Copy -> key(HidReports.MOD_GUI, HidReports.KEY_C)
            HidCommand.Paste -> key(HidReports.MOD_GUI, HidReports.KEY_V)
            HidCommand.Cut -> key(HidReports.MOD_GUI, HidReports.KEY_X)
            HidCommand.SelectAll -> key(HidReports.MOD_GUI, HidReports.KEY_A)
            HidCommand.Undo -> key(HidReports.MOD_GUI, HidReports.KEY_Z)
            HidCommand.Redo -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_Z,
            )
            HidCommand.Find -> key(HidReports.MOD_GUI, HidReports.KEY_F)
            HidCommand.Save -> key(HidReports.MOD_GUI, HidReports.KEY_S)
            HidCommand.NewDocument -> key(HidReports.MOD_GUI, HidReports.KEY_N)
            HidCommand.OpenDocument -> key(HidReports.MOD_GUI, HidReports.KEY_O)
            HidCommand.CloseWindow -> key(HidReports.MOD_GUI, HidReports.KEY_W)
            HidCommand.Enter -> key(0, HidReports.KEY_ENTER)
            HidCommand.Tab -> key(0, HidReports.KEY_TAB)
            HidCommand.Escape -> key(0, HidReports.KEY_ESC)
            HidCommand.Backspace -> key(0, HidReports.KEY_BACKSPACE)
            HidCommand.ForwardDelete -> key(0, HidReports.KEY_DELETE)
            HidCommand.LineStart -> key(HidReports.MOD_GUI, HidReports.KEY_LEFT)
            HidCommand.LineEnd -> key(HidReports.MOD_GUI, HidReports.KEY_RIGHT)
            HidCommand.WordLeft -> key(HidReports.MOD_ALT, HidReports.KEY_LEFT)
            HidCommand.WordRight -> key(HidReports.MOD_ALT, HidReports.KEY_RIGHT)
            HidCommand.Spotlight -> key(HidReports.MOD_GUI, HidReports.KEY_SPACE)
            HidCommand.MissionControl -> key(HidReports.MOD_CTRL, HidReports.KEY_UP)
            HidCommand.AppExpose -> key(HidReports.MOD_CTRL, HidReports.KEY_DOWN)
            HidCommand.Launchpad -> key(0, HidReports.KEY_F4)
            HidCommand.ShowDesktop -> key(HidReports.MOD_GUI, HidReports.KEY_F11)
            HidCommand.NotificationCenter -> key(HidReports.MOD_GUI, HidReports.KEY_F12)
            HidCommand.AppSwitcher -> key(HidReports.MOD_GUI, HidReports.KEY_TAB)
            HidCommand.WindowSwitcher -> key(HidReports.MOD_GUI, HidReports.KEY_GRAVE)
            HidCommand.SpaceLeft -> key(HidReports.MOD_CTRL, HidReports.KEY_LEFT)
            HidCommand.SpaceRight -> key(HidReports.MOD_CTRL, HidReports.KEY_RIGHT)
            HidCommand.ScreenshotArea -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_4,
            )
            HidCommand.ScreenshotWindow -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_5,
            )
            HidCommand.PresentationPrevious -> key(0, HidReports.KEY_LEFT)
            HidCommand.PresentationNext -> key(0, HidReports.KEY_RIGHT)
            HidCommand.PresentationPlayPause -> key(0, HidReports.KEY_SPACE)
            HidCommand.PresentationStart -> key(
                (HidReports.MOD_SHIFT.toInt() or HidReports.MOD_GUI.toInt()).toByte(),
                HidReports.KEY_ENTER,
            )
            HidCommand.PresentationFirst -> key(0, HidReports.KEY_HOME)
            HidCommand.PresentationLast -> key(0, HidReports.KEY_END)
            HidCommand.PresentationBlack -> key(0, HidReports.KEY_B)
            HidCommand.PresentationWhite -> key(0, HidReports.KEY_W)
            HidCommand.PresentationExit -> key(0, HidReports.KEY_ESC)
            HidCommand.MediaPlayPause -> consumer(HidReports.CONSUMER_PLAY_PAUSE)
            HidCommand.MediaPrevious -> consumer(HidReports.CONSUMER_SCAN_PREVIOUS)
            HidCommand.MediaNext -> consumer(HidReports.CONSUMER_SCAN_NEXT)
            HidCommand.MediaMute -> consumer(HidReports.CONSUMER_MUTE)
            HidCommand.MediaVolumeDown -> consumer(HidReports.CONSUMER_VOLUME_DOWN)
            HidCommand.MediaVolumeUp -> consumer(HidReports.CONSUMER_VOLUME_UP)
        }
    }

    private fun key(modifier: Byte, key: Byte) = controller.keyTap(modifier, key)
    private fun consumer(usage: Int) = controller.consumerTap(usage)

    private fun refreshState(status: String) {
        _state.update {
            it.copy(
                status = status,
                lifecycle = lifecycleFor(status),
                isReady = controller.isReady,
                isConnected = controller.isConnected,
            )
        }
        if (controller.isConnected) {
            lastConnectAttemptAddress = null
        } else if (status.equals("Disconnected", ignoreCase = true)) {
            lastConnectAttemptAddress = null
        }
        attemptAutoReconnect()
    }

    private fun lifecycleFor(status: String): HidLifecycle = when {
        status.contains("permission", ignoreCase = true) -> HidLifecycle.PermissionMissing
        status.contains("unavailable", ignoreCase = true) -> HidLifecycle.Unavailable
        status.contains("failed", ignoreCase = true) -> HidLifecycle.Failed
        controller.isConnected -> HidLifecycle.Connected
        controller.isReady -> HidLifecycle.Ready
        status.contains("Opening", ignoreCase = true) ||
            status.contains("Registering", ignoreCase = true) -> HidLifecycle.Opening
        else -> HidLifecycle.Idle
    }

    private fun attemptAutoReconnect() {
        val current = _state.value
        val selectedAddress = current.selectedHostAddress ?: return
        if (!controller.isReady || controller.isConnected || lastConnectAttemptAddress == selectedAddress) {
            return
        }
        devices.firstOrNull { runCatching { it.address == selectedAddress }.getOrDefault(false) }
            ?.let { device ->
                lastConnectAttemptAddress = selectedAddress
                controller.connect(device)
            }
    }

    private fun saveSelectedHost(address: String) {
        prefs.edit().putString(PREF_SELECTED_HOST, address).apply()
        _state.update { it.copy(selectedHostAddress = address) }
    }

    private companion object {
        const val PREF_SELECTED_HOST = "selected_host_address"
    }
}

internal fun prioritizeHosts(hosts: List<HidHost>, selectedAddress: String?): List<HidHost> {
    if (hosts.isEmpty()) return emptyList()

    val scored = hosts.map { host ->
        host to hostPriority(host, selectedAddress)
    }
    val source = scored.filter { (host, score) -> score > 0 || host.address == selectedAddress }
    return source
        .sortedWith(
            compareByDescending<Pair<HidHost, Int>> { it.second }
                .thenBy { it.first.label.lowercase() }
                .thenBy { it.first.address },
        )
        .map { it.first }
}

private fun hostPriority(host: HidHost, selectedAddress: String?): Int {
    val label = host.label.lowercase()
    var score = 0
    if (host.address == selectedAddress) score += 100
    if (COMPUTER_HINTS.any(label::contains)) score += 20
    if (UNRELATED_HINTS.any(label::contains)) score -= 80
    return score
}

private val COMPUTER_HINTS = listOf(
    "macbook",
    "imac",
    "mac mini",
    "mac studio",
    "desktop",
    "laptop",
    "notebook",
    "workstation",
    "surface",
    "thinkpad",
    "xps",
    "pc",
)

private val UNRELATED_HINTS = listOf(
    "airpods",
    "buds",
    "headphone",
    "headset",
    "speaker",
    "earbud",
    "watch",
    "keyboard",
    "mouse",
    "trackpad",
    "phone",
    "galaxy",
    "iphone",
    "ipad",
    "tablet",
    "tv",
)
