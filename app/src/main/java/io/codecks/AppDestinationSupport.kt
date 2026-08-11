package io.codecks

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.os.Bundle
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.domain.ActionStatus
import io.codecks.domain.DeckAction
import io.codecks.data.clipboard.ClipboardSyncSettings
import io.codecks.data.BackupInputTooLargeException
import io.codecks.data.PendingBackupRecovery
import io.codecks.data.PendingBackupRecoveryException
import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.context.NotificationPreview
import io.codecks.domain.clipboard.ClipboardSyncMode
import io.codecks.ui.connection.isReady
import io.codecks.ui.keyboard.KeyboardScreen
import io.codecks.ui.keyboard.KeyboardViewModel
import io.codecks.ui.mouse.MouseScreen
import io.codecks.ui.mouse.MouseViewModel
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.ui.designsystem.LocalCodecksMotionPolicy
import io.codecks.ui.designsystem.codecksSemanticColorTokens
import java.io.File
import io.codecks.domain.features.FeatureFlag
import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.privacy.SupportActionHealth
import io.codecks.domain.privacy.SupportBundleHealth
import io.codecks.domain.privacy.SupportBundleManifest
import io.codecks.domain.privacy.SupportBundleSettings
import io.codecks.domain.privacy.SupportBundleSnapshot
import io.codecks.domain.privacy.SupportConnectionHealth
import io.codecks.domain.privacy.SupportHidHealth
import io.codecks.domain.privacy.SupportIntervalBucket
import io.codecks.domain.privacy.SupportSpeedBucket
import io.codecks.BuildConfig
import kotlinx.coroutines.delay
import java.security.MessageDigest

@Composable
internal fun CelebrationOverlay(label: String, onDone: () -> Unit) {
    val motion = LocalCodecksMotionPolicy.current
    val colors = codecksSemanticColorTokens()
    LaunchedEffect(label) {
        kotlinx.coroutines.delay(1_250)
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (motion.allowsContinuousMotion) {
            Text("🎉", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.TopStart).padding(CodecksDesignTokens.Spacing.xxl).clearAndSetSemantics { })
            Text("✨", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.align(Alignment.TopEnd).padding(CodecksDesignTokens.Spacing.xxl).clearAndSetSemantics { })
            Text("💚", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.CenterStart).padding(CodecksDesignTokens.Spacing.xxl).clearAndSetSemantics { })
            Text("🔥", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.CenterEnd).padding(CodecksDesignTokens.Spacing.xxl).clearAndSetSemantics { })
            Text("✨", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.BottomStart).padding(CodecksDesignTokens.Spacing.xxl).clearAndSetSemantics { })
            Text("🎉", style = MaterialTheme.typography.displaySmall, modifier = Modifier.align(Alignment.BottomEnd).padding(CodecksDesignTokens.Spacing.xxl).clearAndSetSemantics { })
        }
        Surface(
            color = colors.surfaceRaised,
            contentColor = colors.content,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = CodecksDesignTokens.Elevation.medium,
            modifier = Modifier
                .align(Alignment.Center)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(label.take(24), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(CodecksDesignTokens.Spacing.xxl))
        }
    }
}

@Composable
internal fun KeyboardDestination(
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

internal fun createSupportBundleSnapshot(
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

internal fun shareSupportBundle(context: Context, file: File, callbackAction: String): Boolean =
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

internal tailrec fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}

@Composable
internal fun MouseDestination(
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

internal fun codecksSpotlightActionRevision(query: String): String =
    "spotlight-${MessageDigest.getInstance("SHA-256")
        .digest(query.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(64)}"
