package io.codecks.ui.mouse.lockscreen

import android.Manifest
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.codecks.HidRepository
import io.codecks.InternalIntentAuth
import io.codecks.MainActivity
import io.codecks.PUBLIC_TRACKPAD_URI
import io.codecks.core.trackpad.LockscreenControlState
import io.codecks.core.trackpad.LockscreenDecision
import io.codecks.core.trackpad.LockscreenTrackpadPolicy
import io.codecks.core.trackpad.TrackpadEntryOrigin
import io.codecks.core.trackpad.TrackpadSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackpadEntryActivity : ComponentActivity() {
    @Inject lateinit var hidRepository: HidRepository
    @Inject lateinit var trackpadSettingsRepository: TrackpadSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeIntent(intent)
    }

    private fun routeIntent(intent: Intent?) {
        lifecycleScope.launch {
            val origin = resolveEntryOrigin(intent, this@TrackpadEntryActivity)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            val userManager = getSystemService(UserManager::class.java)
            // Do not touch credential-protected settings before the first unlock.
            // Direct-boot and service failures fail closed without opening any UI.
            if (!userManager.isUserUnlocked) {
                finish()
                return@launch
            }
            val settings = trackpadSettingsRepository.settings.first()
            val hidState = hidRepository.state.value
            val state = LockscreenControlState(
                keyguardShowing = keyguardManager?.isKeyguardLocked == true,
                deviceLocked = keyguardManager?.isDeviceLocked == true,
                userUnlockedSinceBoot = userManager?.isUserUnlocked == true,
                hidConnected = hidState.isConnected,
                selectedHostPresent = hidState.selectedHostAddress != null,
                bluetoothPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(
                        this@TrackpadEntryActivity,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ) == PackageManager.PERMISSION_GRANTED,
                featureEnabled = settings.lockscreenTrackpadEnabled,
                entryOrigin = origin,
            )
            when (LockscreenTrackpadPolicy.decision(state)) {
                LockscreenDecision.ForwardToUnlockedTrackpad -> {
                    startActivity(fullTrackpadIntent(this@TrackpadEntryActivity))
                }
                LockscreenDecision.AllowRestrictedPointer,
                LockscreenDecision.RequireUnlock,
                -> {
                    startActivity(LockscreenTrackpadActivity.intent(this@TrackpadEntryActivity, origin))
                }
                LockscreenDecision.IgnoreAutomaticEntry -> Unit
            }
            finish()
        }
    }

    companion object {
        internal const val EXTRA_ENTRY_ORIGIN = "io.codecks.trackpad.entry_origin"
        private const val ACTION_INTERNAL_TRACKPAD_ENTRY = "io.codecks.action.OPEN_TRACKPAD_ENTRY"
        private const val REQUEST_CODE_WIDGET = 42011
        private const val REQUEST_CODE_NOTIFICATION = 42012
        private const val TRUSTED_PARSE_BYPASS_TOKEN = "trackpad-entry-trusted-parse"

        fun notificationPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_NOTIFICATION,
                internalEntryIntent(context, TrackpadEntryOrigin.InternalNotification),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun widgetPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_WIDGET,
                internalEntryIntent(context, TrackpadEntryOrigin.InternalWidget),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun fullTrackpadIntent(context: Context): Intent =
            InternalIntentAuth.sign(
                context,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_DESTINATION, "mouse")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )

        private fun internalEntryIntent(context: Context, origin: TrackpadEntryOrigin): Intent =
            InternalIntentAuth.sign(
                context,
                Intent(context, TrackpadEntryActivity::class.java)
                    .setAction(ACTION_INTERNAL_TRACKPAD_ENTRY)
                    .putExtra(EXTRA_ENTRY_ORIGIN, origin.name)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )

        internal fun resolveEntryOrigin(intent: Intent?): TrackpadEntryOrigin {
            return resolveTrustedEntryOrigin(
                action = intent?.action,
                dataString = intent?.dataString,
                declaredOrigin = intent?.getStringExtra(EXTRA_ENTRY_ORIGIN),
                providedToken = TRUSTED_PARSE_BYPASS_TOKEN,
                expectedToken = TRUSTED_PARSE_BYPASS_TOKEN,
            )
        }

        private fun resolveEntryOrigin(intent: Intent?, context: Context): TrackpadEntryOrigin {
            return resolveTrustedEntryOrigin(
                action = intent?.action,
                dataString = intent?.dataString,
                declaredOrigin = intent?.getStringExtra(EXTRA_ENTRY_ORIGIN),
                providedToken = intent?.getStringExtra(InternalIntentAuth.EXTRA_TOKEN),
                expectedToken = InternalIntentAuth.token(context),
            )
        }

        internal fun resolveTrustedEntryOrigin(
            action: String?,
            dataString: String?,
            declaredOrigin: String?,
            providedToken: String?,
            expectedToken: String?,
        ): TrackpadEntryOrigin {
            if (action == Intent.ACTION_VIEW && dataString == PUBLIC_TRACKPAD_URI) {
                return TrackpadEntryOrigin.ExactPublicUri
            }
            if (providedToken != expectedToken) {
                return TrackpadEntryOrigin.Unknown
            }
            return TrackpadEntryOrigin.entries.firstOrNull {
                it.name == declaredOrigin
            } ?: TrackpadEntryOrigin.Unknown
        }
    }
}
