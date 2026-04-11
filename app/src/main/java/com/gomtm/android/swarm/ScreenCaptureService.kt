package com.gomtm.swarm.swarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gomtm.swarm.MainActivity
import com.gomtm.swarm.R

class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val projectionData = intent.readProjectionData()
                if (projectionData == null) {
                    stopRuntimeService()
                    return START_NOT_STICKY
                }
                ensureForeground()
                if (!AndroidScreenStreamHost.startProjection(this, resultCode, projectionData)) {
                    stopRuntimeService()
                    return START_NOT_STICKY
                }
                return START_NOT_STICKY
            }

            ACTION_STOP_PROJECTION -> {
                AndroidScreenStreamHost.stopStream(this)
                stopRuntimeService()
                return START_NOT_STICKY
            }

            else -> {
                stopRuntimeService()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        AndroidScreenStreamHost.onServiceDestroyed()
        super.onDestroy()
    }

    private fun ensureForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.screen_capture_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopRuntimeService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.screen_capture_notification_title))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_START_PROJECTION = "com.gomtm.swarm.action.START_PROJECTION"
        private const val ACTION_STOP_PROJECTION = "com.gomtm.swarm.action.STOP_PROJECTION"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_PROJECTION_DATA = "projection_data"
        private const val NOTIFICATION_CHANNEL_ID = "gomtm_screen_capture"
        private const val NOTIFICATION_ID = 41010

        fun startProjection(context: Context, resultCode: Int, projectionData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START_PROJECTION
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, projectionData)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopProjection(context: Context) {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).apply {
                    action = ACTION_STOP_PROJECTION
                },
            )
        }
    }
}

private fun Intent.readProjectionData(): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra("projection_data", Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra("projection_data")
    }
}
