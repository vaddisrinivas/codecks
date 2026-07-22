package io.codecks.ui.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
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
                    title = if (bluetoothPermissionGranted) "Choose a paired Mac" else "Bluetooth permission",
                    disconnectedTitle = if (bluetoothPermissionGranted) "Connect a Mac" else "Allow Bluetooth",
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
                TrackpadSetupPanel(
                    bluetoothPermissionGranted = bluetoothPermissionGranted,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    content(PaddingValues(0.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackpadSetupPanel(
    bluetoothPermissionGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.heightIn(min = 240.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mouse, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = if (bluetoothPermissionGranted) "Pick a paired Mac" else "Allow Bluetooth first",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = if (bluetoothPermissionGranted) {
                    "Choose a paired Mac above. Trackpad opens as soon as Bluetooth input connects."
                } else {
                    "Android needs Bluetooth permission before this phone can act as your Mac pointer."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
