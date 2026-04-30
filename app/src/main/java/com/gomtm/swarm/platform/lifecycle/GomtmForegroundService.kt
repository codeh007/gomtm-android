package com.gomtm.swarm.platform.lifecycle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gomtm.swarm.MainActivity
import com.gomtm.swarm.R

class GomtmForegroundService : Service() {
    private var runtimeStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        HostActivationStore.markDeviceServiceActivationRequested(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        HostActivationStore.markDeviceServiceActivationRequested(this)
        if ((intent?.action ?: ACTION_START) == ACTION_STOP) {
            HostActivationStore.clearDeviceServiceActivationRequested(this)
            if (runtimeStarted) {
                runCatching { GomtmRuntimeBridge.stopRuntime() }
                runtimeStarted = false
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!runtimeStarted) {
            val connectionAddr = intent?.getStringExtra(EXTRA_CONNECTION).orEmpty()
            runCatching {
                GomtmRuntimeBridge.startRuntime(filesDir.absolutePath, connectionAddr)
                runtimeStarted = true
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        HostActivationStore.clearDeviceServiceActivationRequested(this)
        if (runtimeStarted) {
            runCatching { GomtmRuntimeBridge.stopRuntime() }
            runtimeStarted = false
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.runtime_notification_title))
            .setContentText(getString(R.string.runtime_notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.runtime_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        private const val ACTION_START = "com.gomtm.swarm.action.START_RUNTIME"
        private const val ACTION_STOP = "com.gomtm.swarm.action.STOP_RUNTIME"
        private const val EXTRA_CONNECTION = "connection"
        private const val NOTIFICATION_CHANNEL_ID = "gomtm_runtime"
        private const val NOTIFICATION_ID = 41001

        fun start(
            context: Context,
            connectionAddr: String? = null,
        ) {
            val intent = Intent(context, GomtmForegroundService::class.java).apply {
                action = ACTION_START
                if (!connectionAddr.isNullOrBlank()) {
                    putExtra(EXTRA_CONNECTION, connectionAddr)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GomtmForegroundService::class.java).apply { action = ACTION_STOP })
        }
    }
}
