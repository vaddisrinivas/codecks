package io.codecks

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.codecks.ui.mouse.lockscreen.TrackpadEntryActivity
import javax.inject.Inject

@AndroidEntryPoint
class HidSessionService : Service() {
    @Inject lateinit var hidRepository: HidRepository

    private var startedActivities = 0
    private var receiverRegistered = false
    private var activityCallbacksRegistered = false

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            if (startedActivities++ == 0) {
                hidRepository.onSystemEvent(HidSystemEvent.AppForegrounded)
            }
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities == 0 && !activity.isChangingConfigurations) {
                hidRepository.onSystemEvent(HidSystemEvent.AppBackgrounded)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    hidRepository.onSystemEvent(HidSystemEvent.ScreenOn)
                    emitCurrentLockState()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    hidRepository.onSystemEvent(HidSystemEvent.ScreenOff)
                    // Conservative immediately: Android has no reliable dynamic "user locked"
                    // broadcast, and keyguard state can lag the screen-off broadcast.
                    hidRepository.onSystemEvent(HidSystemEvent.UserLocked)
                }
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_USER_UNLOCKED,
                -> hidRepository.onSystemEvent(HidSystemEvent.UserUnlocked)
                BluetoothAdapter.ACTION_STATE_CHANGED -> when (
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                ) {
                    BluetoothAdapter.STATE_OFF,
                    BluetoothAdapter.STATE_TURNING_OFF,
                    -> hidRepository.onSystemEvent(HidSystemEvent.BluetoothOff)
                    BluetoothAdapter.STATE_ON -> hidRepository.onSystemEvent(HidSystemEvent.BluetoothOn)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundSafely()
        registerSystemInputs()
        emitInitialSystemState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MANUAL_RETRY) {
            hidRepository.onSystemEvent(HidSystemEvent.ManualRetry)
        } else {
            hidRepository.maintain()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterSystemInputs()
        hidRepository.releaseButtons()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pendingOpen = TrackpadEntryActivity.notificationPendingIntent(this)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Codecks Bluetooth input")
            .setContentText("Keeping Trackpad and Keyboard ready for your Mac.")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_notification, "Trackpad", pendingOpen)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bluetooth input",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the Codecks HID mouse and keyboard session registered."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun registerSystemInputs() {
        if (!activityCallbacksRegistered) {
            application.registerActivityLifecycleCallbacks(activityCallbacks)
            activityCallbacksRegistered = true
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_USER_UNLOCKED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(systemEventReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    private fun unregisterSystemInputs() {
        if (receiverRegistered) {
            unregisterReceiver(systemEventReceiver)
            receiverRegistered = false
        }
        if (activityCallbacksRegistered) {
            application.unregisterActivityLifecycleCallbacks(activityCallbacks)
            activityCallbacksRegistered = false
        }
    }

    private fun emitInitialSystemState() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isInteractive == false) {
            hidRepository.onSystemEvent(HidSystemEvent.ScreenOff)
            hidRepository.onSystemEvent(HidSystemEvent.UserLocked)
        } else {
            hidRepository.onSystemEvent(HidSystemEvent.ScreenOn)
            emitCurrentLockState()
        }
        val canReadBluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        if (canReadBluetooth) {
            val bluetoothEnabled = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }
                .getOrDefault(false)
            hidRepository.onSystemEvent(
                if (bluetoothEnabled) HidSystemEvent.BluetoothOn else HidSystemEvent.BluetoothOff,
            )
        }
    }

    private fun emitCurrentLockState() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        hidRepository.onSystemEvent(
            if (keyguardManager?.isDeviceLocked == true || keyguardManager?.isKeyguardLocked == true) {
                HidSystemEvent.UserLocked
            } else {
                HidSystemEvent.UserUnlocked
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "codecks_hid_session"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_MANUAL_RETRY = "io.codecks.action.HID_MANUAL_RETRY"

        fun start(context: Context) {
            startService(context, action = null)
        }

        fun retry(context: Context) {
            startService(context, action = ACTION_MANUAL_RETRY)
        }

        private fun startService(context: Context, action: String?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val intent = Intent(context, HidSessionService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
