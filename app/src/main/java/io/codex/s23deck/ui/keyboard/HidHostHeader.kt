package io.codex.s23deck.ui.keyboard

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.codex.s23deck.HidHost
import io.codex.s23deck.HidState
import io.codex.s23deck.ui.connection.HidHealthKind
import io.codex.s23deck.ui.connection.hidHealth
import io.codex.s23deck.ui.connection.statusLabel
import io.codex.s23deck.ui.designsystem.DeckActionButton
import java.util.regex.Pattern

@Composable
fun HidHostHeader(
    title: String,
    disconnectedTitle: String,
    connectedTitle: String,
    icon: ImageVector,
    state: HidState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onRefreshHosts: () -> Unit,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val associationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val device = result.data?.companionBluetoothDevice()
            val canCreateBond = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (device != null && canCreateBond) {
                runCatching { device.createBond() }
            }
        }
        onRefreshHosts()
    }
    val selectedHost = state.hosts.firstOrNull { it.address == state.selectedHostAddress }
    val visibleTarget = selectedHost?.label?.cleanHidHostLabel()
    val health = state.hidHealth(permissionGranted)
    val canConnectSelected = health.kind == HidHealthKind.ReadyToConnect && selectedHost != null
    val statusLabel = visibleTarget ?: if (state.isConnected) connectedTitle else if (permissionGranted) health.title else disconnectedTitle
    val statusText = if (canConnectSelected) "Tap to connect" else health.statusLabel() + " · " + health.detail
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (canConnectSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .then(
                    if (canConnectSelected) {
                        Modifier.clickable { onConnect(selectedHost.address) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            health.kind == HidHealthKind.PermissionMissing -> DeckActionButton(
                label = "Allow",
                onClick = onRequestPermission,
                modifier = Modifier.widthIn(min = 96.dp, max = 112.dp).heightIn(min = 48.dp),
            )
            health.kind == HidHealthKind.Stopped || health.kind == HidHealthKind.Failed -> DeckActionButton(
                label = "Start",
                onClick = onStart,
                modifier = Modifier.widthIn(min = 96.dp, max = 112.dp).heightIn(min = 48.dp),
            )
        }
        IconButton(onClick = { pickerOpen = true }) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = "Choose target")
        }
        IconButton(onClick = onRefreshHosts) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh targets")
        }
    }

    if (pickerOpen) {
        HidHostPickerDialog(
            title = title,
            hosts = state.hosts,
            selectedAddress = state.selectedHostAddress,
            onRefreshHosts = onRefreshHosts,
            onAddDevice = {
                startCompanionPairing(
                    context = context,
                    onPending = { sender ->
                        associationLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    },
                    onFallback = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                    },
                    onRefreshHosts = onRefreshHosts,
                )
            },
            onDismiss = { pickerOpen = false },
            onConnect = {
                pickerOpen = false
                onConnect(it)
            },
        )
    }
}

@Composable
private fun HidHostPickerDialog(
    title: String,
    hosts: List<HidHost>,
    selectedAddress: String?,
    onRefreshHosts: () -> Unit,
    onAddDevice: () -> Unit,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (hosts.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "No compatible targets found yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Pair your Mac first, then refresh this list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DeckActionButton(
                        label = "Refresh",
                        onClick = onRefreshHosts,
                        icon = Icons.Outlined.Refresh,
                        modifier = Modifier.widthIn(min = 160.dp).heightIn(min = 56.dp),
                    )
                    DeckActionButton(
                        label = "Add device",
                        onClick = onAddDevice,
                        icon = Icons.Outlined.Bluetooth,
                        modifier = Modifier.widthIn(min = 160.dp).heightIn(min = 56.dp),
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(hosts, key = { it.address }) { host ->
                        val remembered = host.address == selectedAddress
                        HidHostTargetRow(
                            host = host,
                            remembered = remembered,
                            onConnect = { onConnect(host.address) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onAddDevice) {
                Text("Add device")
            }
        },
    )
}

@Composable
private fun HidHostTargetRow(
    host: HidHost,
    remembered: Boolean,
    onConnect: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = host.label.cleanHidHostLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (remembered) "Saved target" else "Available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DeckActionButton(
                label = if (remembered) "Reconnect" else "Use",
                onClick = onConnect,
                modifier = Modifier.width(124.dp).heightIn(min = 48.dp),
            )
        }
    }
}

private fun String.cleanHidHostLabel(): String {
    val withoutAddress = replace(Regex("\\s+[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}\\s*$"), "")
    return withoutAddress.trim().ifBlank { "Mac" }
}

private fun startCompanionPairing(
    context: Context,
    onPending: (IntentSender) -> Unit,
    onFallback: () -> Unit,
    onRefreshHosts: () -> Unit,
) {
    val manager = context.getSystemService(CompanionDeviceManager::class.java)
    if (manager == null) {
        onFallback()
        return
    }
    val request = AssociationRequest.Builder()
        .addDeviceFilter(
            BluetoothDeviceFilter.Builder()
                .setNamePattern(Pattern.compile(".*"))
                .build(),
        )
        .setSingleDevice(false)
        .build()
    val callback = object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            onPending(intentSender)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Legacy CompanionDeviceManager callback for API 28 compatibility.")
        override fun onDeviceFound(intentSender: IntentSender) {
            onPending(intentSender)
        }

        override fun onAssociationCreated(associationInfo: android.companion.AssociationInfo) {
            onRefreshHosts()
        }

        override fun onFailure(error: CharSequence?) {
            onFallback()
        }

        override fun onFailure(errorCode: Int, error: CharSequence?) {
            onFallback()
        }
    }
    runCatching {
        manager.associate(request, callback, Handler(Looper.getMainLooper()))
    }.onFailure {
        onFallback()
    }
}

@Suppress("DEPRECATION")
private fun Intent.companionBluetoothDevice(): BluetoothDevice? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
    }
