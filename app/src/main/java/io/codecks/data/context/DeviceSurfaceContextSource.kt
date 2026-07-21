package io.codecks.data.context

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.InputDevice

enum class DeviceSurfaceKind {
    Phone,
    Tablet,
    Desktop,
}

data class DeviceSurfaceContext(
    val kind: DeviceSurfaceKind,
    val label: String,
    val externalDisplayConnected: Boolean,
    val keyboardConnected: Boolean,
    val pointerConnected: Boolean,
)

class DeviceSurfaceContextSource(
    private val context: Context,
) {
    fun current(): DeviceSurfaceContext {
        val resources = context.resources
        val config = resources.configuration
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        val externalDisplayConnected = context.externalDisplayConnected()
        val desktopMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_DESK ||
            externalDisplayConnected ||
            config.screenWidthDp >= DESKTOP_WIDTH_DP
        val keyboardConnected = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .any { device -> device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD }
        val pointerConnected = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .any { device ->
                device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
                    device.sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD
            }
        val tabletMode = config.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
        val kind = when {
            desktopMode -> DeviceSurfaceKind.Desktop
            tabletMode -> DeviceSurfaceKind.Tablet
            else -> DeviceSurfaceKind.Phone
        }
        return DeviceSurfaceContext(
            kind = kind,
            label = when (kind) {
                DeviceSurfaceKind.Phone -> "Phone"
                DeviceSurfaceKind.Tablet -> "Tablet/Foldable"
                DeviceSurfaceKind.Desktop -> "Samsung DeX/Desktop"
            },
            externalDisplayConnected = externalDisplayConnected,
            keyboardConnected = keyboardConnected,
            pointerConnected = pointerConnected,
        )
    }

    private fun Context.externalDisplayConnected(): Boolean =
        getSystemService(DisplayManager::class.java)
            ?.displays
            .orEmpty()
            .any { it.displayId != android.view.Display.DEFAULT_DISPLAY }

    private companion object {
        const val TABLET_SMALLEST_WIDTH_DP = 600
        const val DESKTOP_WIDTH_DP = 840
    }
}
