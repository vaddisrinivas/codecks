package io.codecks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.codecks.data.privacy.DiagnosticEventStore
import io.codecks.data.privacy.SupportBundleTempFilePolicy
import io.codecks.data.privacy.recordTerminal
import io.codecks.domain.privacy.DiagnosticComponent
import io.codecks.ui.settings.SupportBundleUiState
import io.codecks.ui.settings.SupportBundleViewModel
import io.codecks.ui.settings.UpdateSettingsState
import io.codecks.ui.settings.UpdateViewModel

internal data class SettingsSupportRuntime(
    val updateViewModel: UpdateViewModel,
    val updateState: UpdateSettingsState,
    val supportBundleViewModel: SupportBundleViewModel,
    val supportBundleState: SupportBundleUiState,
)

@Composable
internal fun rememberSettingsSupportRuntime(appContext: Context): SettingsSupportRuntime {
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateViewModel: UpdateViewModel = viewModel {
        UpdateViewModel(
            appForeground = { lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
            terminalEvent = { result, timestamp ->
                DiagnosticEventStore(appContext).recordTerminal(
                    component = DiagnosticComponent.UPDATE,
                    result = result,
                    timestampEpochMs = timestamp,
                )
            },
        )
    }
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val supportBundleViewModel: SupportBundleViewModel = viewModel {
        SupportBundleViewModel(SupportBundleTempFilePolicy(appContext.cacheDir))
    }
    val supportBundleState by supportBundleViewModel.state.collectAsStateWithLifecycle()
    val shareCallbackAction = remember(appContext) { "${appContext.packageName}.SUPPORT_SHARE_TARGET_CHOSEN" }
    DisposableEffect(supportBundleViewModel, shareCallbackAction) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == shareCallbackAction) supportBundleViewModel.shareTargetChosen()
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(shareCallbackAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { appContext.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(supportBundleState) {
        val ready = supportBundleState as? SupportBundleUiState.Ready ?: return@LaunchedEffect
        if (shareSupportBundle(appContext, ready.file, shareCallbackAction)) supportBundleViewModel.chooserOpened()
        else supportBundleViewModel.shareFailed()
    }
    return SettingsSupportRuntime(updateViewModel, updateState, supportBundleViewModel, supportBundleState)
}
