package io.codecks

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.codecks.ui.connection.BluetoothPermissionPolicy
import io.codecks.ui.connection.BluetoothPermissionState
import io.codecks.ui.connection.CodecksReadiness
import io.codecks.ui.connection.ConnectionHealth
import io.codecks.ui.connection.ConnectionUiState
import io.codecks.ui.connection.HidConfirmationStore
import io.codecks.ui.connection.HidTerminalReceipt
import io.codecks.ui.connection.HidTerminalResult
import io.codecks.ui.connection.codecksReadiness
import io.codecks.ui.connection.evaluateRuntimeSetupCompletion
import io.codecks.ui.connection.hidHealth
import io.codecks.ui.connection.hidHostToken
import io.codecks.ui.connection.isReady
import io.codecks.ui.connection.revisionToken
import io.codecks.ui.connection.setupTargetId

internal data class InputReadinessRuntime(
    val permissionGranted: Boolean,
    val permissionPermanentlyDenied: Boolean,
    val requestPermission: () -> Unit,
    val confirmAndConnect: (String) -> Unit,
    val terminalReceipt: HidTerminalReceipt?,
    val readiness: CodecksReadiness,
)

@Composable
internal fun rememberInputReadinessRuntime(
    appContext: Context,
    hostContext: Context,
    activity: Activity?,
    repository: HidRepository,
    hidState: HidState,
    connectionState: ConnectionUiState,
    connectionHealth: ConnectionHealth,
    aiReady: Boolean,
    proofClockEpochMs: Long,
    confirmationStore: HidConfirmationStore,
): InputReadinessRuntime {
    var terminalReceipt by remember { mutableStateOf(confirmationStore.load()) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val requiredPermissions = remember {
        BluetoothPermissionPolicy.requiredRuntimePermissions(Build.VERSION.SDK_INT).toTypedArray()
    }
    val permissionGranted = remember(permissionRefresh) {
        requiredPermissions.all { ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionRequested = true
        permissionRefresh += 1
        if (results.values.all { it }) repository.start()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        permissionRequested && !permissionGranted &&
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) == false
    val requestPermission = {
        if (permanentlyDenied) {
            hostContext.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${hostContext.packageName}")),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionRequested = true
            launcher.launch(requiredPermissions)
        }
    }
    val confirmAndConnect: (String) -> Unit = { address ->
        connectionState.config.takeIf { it.isReady }?.let { config ->
            HidTerminalReceipt(
                setupRevision = connectionState.setupSnapshot.revisionToken(),
                macTargetId = config.setupTargetId(),
                hidHostToken = hidHostToken(address),
                result = HidTerminalResult.USER_CONFIRMED,
                completedAtEpochMs = System.currentTimeMillis(),
            ).also {
                confirmationStore.record(it)
                terminalReceipt = it
            }
        }
        repository.connect(address)
    }
    val setupCompletion = evaluateRuntimeSetupCompletion(
        state = connectionState,
        hidState = hidState,
        permissionState = when {
            permissionGranted -> BluetoothPermissionState.Granted
            permanentlyDenied -> BluetoothPermissionState.PermanentlyDenied
            else -> BluetoothPermissionState.Denied
        },
        hidReceipt = terminalReceipt,
        nowEpochMs = proofClockEpochMs,
    )
    return InputReadinessRuntime(
        permissionGranted = permissionGranted,
        permissionPermanentlyDenied = permanentlyDenied,
        requestPermission = requestPermission,
        confirmAndConnect = confirmAndConnect,
        terminalReceipt = terminalReceipt,
        readiness = codecksReadiness(connectionHealth, hidState.hidHealth(permissionGranted), aiReady, setupCompletion),
    )
}
