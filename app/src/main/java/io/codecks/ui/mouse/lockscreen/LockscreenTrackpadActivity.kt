package io.codecks.ui.mouse.lockscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.codecks.core.trackpad.TrackpadEntryOrigin
import io.codecks.ui.theme.CodecksTheme
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LockscreenTrackpadActivity : ComponentActivity() {
    private val viewModel: LockscreenTrackpadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    LockscreenTrackpadEvent.Finish -> finish()
                    LockscreenTrackpadEvent.RequestUnlock -> dismissKeyguardForFullTrackpad()
                }
            }
        }

        setContent {
            CodecksTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LockscreenTrackpadScreen(
                    state = state,
                    onMove = viewModel::move,
                    onScroll = viewModel::scroll,
                    onClick = viewModel::click,
                    onPress = viewModel::press,
                    onReleaseButtons = viewModel::releaseButtons,
                    onUnlock = viewModel::requestUnlock,
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onStop() {
        viewModel.releaseButtons()
        super.onStop()
    }

    private fun dismissKeyguardForFullTrackpad() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isKeyguardLocked == true) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        startActivity(TrackpadEntryActivity.fullTrackpadIntent(this@LockscreenTrackpadActivity))
                        finish()
                    }
                },
            )
        } else {
            startActivity(TrackpadEntryActivity.fullTrackpadIntent(this))
            finish()
        }
    }

    companion object {
        fun intent(context: Context, origin: TrackpadEntryOrigin): Intent =
            Intent(context, LockscreenTrackpadActivity::class.java)
                .putExtra(TrackpadEntryActivity.EXTRA_ENTRY_ORIGIN, origin.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
