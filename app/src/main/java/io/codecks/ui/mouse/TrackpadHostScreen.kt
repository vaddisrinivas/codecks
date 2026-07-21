package io.codecks.ui.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.codecks.HidState
import io.codecks.ui.keyboard.HidHostHeader

@Composable
internal fun TrackpadHostScreen(
    contentPadding: PaddingValues,
    hidState: HidState,
    bluetoothPermissionGranted: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onStartHid: () -> Unit,
    onRefreshHosts: () -> Unit,
    onConnectHost: (String) -> Unit,
    onConnection: () -> Unit,
    onFullscreen: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        val compact = maxHeight < 560.dp
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            if (!hidState.isConnected) {
                HidHostHeader(
                    title = "Trackpad targets",
                    disconnectedTitle = "Trackpad target",
                    connectedTitle = "Trackpad connected",
                    icon = Icons.Outlined.Bluetooth,
                    state = hidState,
                    permissionGranted = bluetoothPermissionGranted,
                    onRequestPermission = onRequestBluetoothPermission,
                    onStart = onStartHid,
                    onRefreshHosts = onRefreshHosts,
                    onConnect = onConnectHost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content(PaddingValues(0.dp))
            }
        }
    }
}
