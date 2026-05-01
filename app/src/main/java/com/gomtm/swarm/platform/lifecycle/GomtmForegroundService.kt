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
import com.gomtm.swarm.platform.remote.AndroidRemoteControlOps
import com.gomtm.swarm.platform.remote.AndroidWebRtcScreenHost
import com.gomtm.swarm.platform.remote.NativeRemoteControlBridgeLoop

class GomtmForegroundService : Service() {
    private var runtimeStarted = false
    private val remoteControlBridge by lazy {
        NativeRemoteControlBridgeLoop(
            opsFactory = { AndroidRemoteControlOps(this) },
            webRtcHostFactory = { AndroidWebRtcScreenHost.forContext(this) },
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if ((intent?.action ?: ACTION_START) == ACTION_STOP) {
            if (runtimeStarted) {
                remoteControlBridge.stop()
                runCatching { GomtmRuntimeBridge.stopRuntime() }
                runtimeStarted = false
            }
            AndroidHostInstallStore.clearStartupPayload(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val payload = intent?.getStringExtra(EXTRA_STARTUP_PAYLOAD_JSON)?.let(::decodePayload)
            ?: AndroidHostInstallStore.restoreSavedStartupPayload(this)

        if (payload == null) {
            runtimeStarted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!runtimeStarted) {
            startRuntimeFromPayload(payload)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (runtimeStarted) {
            remoteControlBridge.stop()
            runCatching { GomtmRuntimeBridge.stopRuntime() }
            runtimeStarted = false
        }
        super.onDestroy()
    }

    private fun startRuntimeFromPayload(payload: AndroidHostStartupPayload): Result<Unit> {
        return runCatching {
            AndroidHostInstallStore.persistStartupPayload(this, payload)
            GomtmRuntimeBridge.startRuntime(filesDir.absolutePath, payload.runtimeCredential)
            remoteControlBridge.start()
            runtimeStarted = true
            clearRuntimeError(this)
        }.onFailure { error ->
            runtimeStarted = false
            persistRuntimeError(this, error.message ?: "runtime_start_failed")
        }
    }

    private fun decodePayload(payloadJson: String): AndroidHostStartupPayload? {
        return runCatching {
            val json = org.json.JSONObject(payloadJson)
            AndroidHostStartupPayload(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                runtimeCredential = json.getString("runtimeCredential"),
                credentialVersion = json.getInt("credentialVersion"),
            )
        }.getOrNull()
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
        private const val EXTRA_STARTUP_PAYLOAD_JSON = "startup_payload_json"
        private const val NOTIFICATION_CHANNEL_ID = "gomtm_runtime"
        private const val NOTIFICATION_ID = 41001
        private const val ERROR_PREFS_NAME = "gomtm_host_shell"
        private const val KEY_RUNTIME_LAST_ERROR = "runtime_last_error"

        fun start(
            context: Context,
            startupPayload: AndroidHostStartupPayload,
        ) {
            val intent = Intent(context, GomtmForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STARTUP_PAYLOAD_JSON, org.json.JSONObject()
                    .put("deviceId", startupPayload.deviceId)
                    .put("deviceName", startupPayload.deviceName)
                    .put("runtimeCredential", startupPayload.runtimeCredential)
                    .put("credentialVersion", startupPayload.credentialVersion)
                    .toString())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GomtmForegroundService::class.java).apply { action = ACTION_STOP })
        }

        fun currentRuntimeSurface(context: Context): AndroidHostRuntimeSurface {
            val saved = AndroidHostInstallStore.restoreSavedStartupPayload(context)
            val lastError = readRuntimeError(context)
            return when {
                saved == null -> AndroidHostRuntimeSurface(status = "sign_in_required", lastError = null)
                GomtmRuntimeBridge.isRuntimeHttpReady() -> AndroidHostRuntimeSurface(status = "running", lastError = null)
                !lastError.isNullOrBlank() -> AndroidHostRuntimeSurface(status = "error", lastError = lastError)
                else -> AndroidHostRuntimeSurface(status = "error", lastError = "runtime_not_ready")
            }
        }

        private fun readRuntimeError(context: Context): String? {
            return context.getSharedPreferences(ERROR_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_RUNTIME_LAST_ERROR, null)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        private fun persistRuntimeError(context: Context, message: String) {
            context.getSharedPreferences(ERROR_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RUNTIME_LAST_ERROR, message)
                .apply()
        }

        private fun clearRuntimeError(context: Context) {
            context.getSharedPreferences(ERROR_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RUNTIME_LAST_ERROR)
                .apply()
        }
    }
}
