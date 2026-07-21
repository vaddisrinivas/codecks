package io.codecks

import android.bluetooth.BluetoothDevice
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val autoReconnectEnabled: Boolean = true,
    val reconnectAttempt: Int = 0,
    val nextReconnectAtMillis: Long = 0L,
    val lastTransitionReason: String = "Bluetooth idle",
    val lastTransitionAtMillis: Long = 0L,
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
    BrowserBack,
    BrowserForward,
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

private const val RECONNECT_TICK_MS = 8_000L
private val RECONNECT_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)

@Singleton
class DefaultHidRepository @Inject constructor(
    @ApplicationContext context: Context,
) : HidRepository {
    private val prefs = context.getSharedPreferences("hid_repository", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(HidState())
    override val state: StateFlow<HidState> = _state.asStateFlow()
    private var devices: List<BluetoothDevice> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reconnectJob: Job? = null
    private var userDisconnected = false
    private val controller = HidController(context) { status ->
        refreshState(status)
    }

    init {
        _state.value = _state.value.copy(selectedHostAddress = prefs.getString(PREF_SELECTED_HOST, null))
    }

    override fun start() {
        controller.openProfile()
        refreshHosts()
        ensureReconnectLoop()
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
        userDisconnected = false
        saveSelectedHost(address)
        _state.update { it.copy(reconnectAttempt = 0, nextReconnectAtMillis = 0L, autoReconnectEnabled = true) }
        devices.firstOrNull { runCatching { it.address == address }.getOrDefault(false) }
            ?.let(controller::connect)
    }

    override fun disconnect() {
        userDisconnected = true
        _state.update { it.copy(autoReconnectEnabled = false, reconnectAttempt = 0, nextReconnectAtMillis = 0L) }
        controller.disconnect()
    }
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
            HidCommand.BrowserBack -> key(HidReports.MOD_GUI, HidReports.KEY_LEFT_BRACKET)
            HidCommand.BrowserForward -> key(HidReports.MOD_GUI, HidReports.KEY_RIGHT_BRACKET)
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
        val nextLifecycle = lifecycleFor(status)
        val now = System.currentTimeMillis()
        _state.update {
            val changed = status != it.status || nextLifecycle != it.lifecycle || controller.isConnected != it.isConnected
            it.copy(
                status = status,
                lifecycle = nextLifecycle,
                isReady = controller.isReady,
                isConnected = controller.isConnected,
                autoReconnectEnabled = !userDisconnected,
                lastTransitionReason = if (changed) status else it.lastTransitionReason,
                lastTransitionAtMillis = if (changed) now else it.lastTransitionAtMillis,
            )
        }
        if (controller.isConnected) {
            _state.update { it.copy(reconnectAttempt = 0, nextReconnectAtMillis = 0L, autoReconnectEnabled = true) }
        } else if (status.equals("Disconnected", ignoreCase = true) && !userDisconnected) {
            scheduleNextReconnectAttempt()
        } else if (status.equals("HID profile closed", ignoreCase = true) && !userDisconnected) {
            scheduleNextReconnectAttempt()
        } else if (status.equals("HID unregistered", ignoreCase = true) && !userDisconnected) {
            scheduleNextReconnectAttempt()
        } else if (status.equals("Connect request failed", ignoreCase = true) && !userDisconnected) {
            scheduleNextReconnectAttempt()
        } else if (status.equals("Disconnected", ignoreCase = true)) {
            _state.update { it.copy(reconnectAttempt = 0, nextReconnectAtMillis = 0L) }
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
        if (userDisconnected) return
        val current = _state.value
        val selectedAddress = current.selectedHostAddress ?: return
        val now = System.currentTimeMillis()
        if (!controller.isReady || controller.isConnected || now < current.nextReconnectAtMillis) {
            return
        }
        devices.firstOrNull { runCatching { it.address == selectedAddress }.getOrDefault(false) }
            ?.let { device ->
                val attempt = current.reconnectAttempt + 1
                val nextDelayMillis = reconnectBackoffMillis(attempt)
                _state.update {
                    it.copy(
                        reconnectAttempt = attempt,
                        nextReconnectAtMillis = now + nextDelayMillis,
                        status = if (attempt == 1) {
                            "Reconnecting ${HidController.deviceLabel(device)}"
                        } else {
                            "Reconnecting ${HidController.deviceLabel(device)} (try $attempt)"
                        },
                        autoReconnectEnabled = true,
                    )
                }
                controller.connect(device)
            }
    }

    private fun ensureReconnectLoop() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (isActive) {
                if (!userDisconnected) {
                    controller.openProfile()
                    refreshHosts()
                    attemptAutoReconnect()
                }
                delay(RECONNECT_TICK_MS)
            }
        }
    }

    private fun scheduleNextReconnectAttempt() {
        val now = System.currentTimeMillis()
        _state.update { state ->
            val attempt = state.reconnectAttempt.coerceAtLeast(1)
            state.copy(
                nextReconnectAtMillis = if (state.nextReconnectAtMillis <= now) {
                    now + reconnectBackoffMillis(attempt)
                } else {
                    state.nextReconnectAtMillis
                },
                autoReconnectEnabled = true,
            )
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

private fun reconnectBackoffMillis(attempt: Int): Long =
    RECONNECT_BACKOFF_MS[(attempt - 1).coerceIn(0, RECONNECT_BACKOFF_MS.lastIndex)]

fun HidState.redactedDiagnosticSummary(nowMillis: Long = System.currentTimeMillis()): String {
    val retryInSeconds = ((nextReconnectAtMillis - nowMillis).coerceAtLeast(0L) / 1_000L).toInt()
    val ageSeconds = lastTransitionAtMillis
        .takeIf { it > 0L }
        ?.let { ((nowMillis - it).coerceAtLeast(0L) / 1_000L).toInt() }
    return buildString {
        append("lifecycle=$lifecycle")
        append(" ready=$isReady")
        append(" connected=$isConnected")
        append(" hosts=${hosts.size}")
        append(" selected=${selectedHostAddress != null}")
        append(" reconnectAttempt=$reconnectAttempt")
        append(" retryIn=${retryInSeconds}s")
        append(" lastReason=${lastTransitionReason.safeHidReason()}")
        if (ageSeconds != null) append(" lastAge=${ageSeconds}s")
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

private fun String.safeHidReason(): String =
    replace(Regex("""([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"""), "[bluetooth-address]")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(96)
        .ifBlank { "none" }

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
