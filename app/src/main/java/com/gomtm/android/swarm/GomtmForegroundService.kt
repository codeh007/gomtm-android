package com.gomtm.swarm.swarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gomtm.swarm.MainActivity
import com.gomtm.swarm.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class GomtmForegroundService : Service() {
    private val swarmRuntime = SwarmRuntime()
    private val runtimeStore by lazy { NodeRuntimeStore(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundWorker = Executors.newSingleThreadExecutor()
    private val tickRunning = AtomicBoolean(false)
    private var latestBootstrapAddress = SwarmNodeConfig.DEFAULT_BOOTSTRAP

    private val tickRunnable = object : Runnable {
        override fun run() {
            val snapshot = swarmRuntime.probe()
            val drainedLogs = swarmRuntime.drainLogs().trim()
            if (drainedLogs.isNotBlank()) {
                drainedLogs.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { Log.i(LOG_TAG, "runtime_log: $it") }
            }
            if (isRuntimeActiveState(snapshot.state) && tickRunning.compareAndSet(false, true)) {
                backgroundWorker.execute {
                    try {
                        swarmRuntime.processRemoteControlTick(this@GomtmForegroundService, timeoutMs = 0)
                    } catch (error: Throwable) {
                        Log.w(LOG_TAG, "processRemoteControlTick failed", error)
                    } finally {
                        tickRunning.set(false)
                    }
                }
            }
            mainHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                runtimeStore.save(NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress))
                stopRuntime()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val persisted = runtimeStore.load()
                latestBootstrapAddress = intent?.getStringExtra(EXTRA_BOOTSTRAP)?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: persisted.bootstrapAddress.ifBlank { SwarmNodeConfig.DEFAULT_BOOTSTRAP }
                val forceRestart = intent?.getBooleanExtra(EXTRA_FORCE_RESTART, false) ?: false
                runtimeStore.save(NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress))
                startRuntime(forceRestart)
                scheduleTicks()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tickRunnable)
        backgroundWorker.shutdownNow()
        super.onDestroy()
    }

    private fun startRuntime(forceRestart: Boolean) {
        val snapshot = swarmRuntime.probe()
        val bootstrapChanged = latestBootstrapAddress.isNotBlank() && latestBootstrapAddress != snapshot.bootstrapAddress
        if ((forceRestart || bootstrapChanged) && (isRuntimeActiveState(snapshot.state) || isRuntimeStartingState(snapshot.state))) {
            runCatching { swarmRuntime.stop() }
        }
        if (!forceRestart && isRuntimeActiveState(snapshot.state) && !bootstrapChanged) {
            return
        }
        Log.i(LOG_TAG, "startRuntime bootstrap=$latestBootstrapAddress forceRestart=$forceRestart")
        swarmRuntime.start(
            this,
            SwarmNodeConfig(
                bootstrapAddress = latestBootstrapAddress.ifBlank { SwarmNodeConfig.DEFAULT_BOOTSTRAP },
                autoReconnect = true,
            ),
        )
    }

    private fun stopRuntime() {
        mainHandler.removeCallbacks(tickRunnable)
        runCatching { swarmRuntime.stop() }
            .onFailure { Log.w(LOG_TAG, "stopRuntime failed", it) }
    }

    private fun scheduleTicks() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_BOOTSTRAP, latestBootstrapAddress)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
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

    private fun isRuntimeActiveState(state: String): Boolean {
        return state.equals("Ready", ignoreCase = true) ||
            state.equals("Connected", ignoreCase = true) ||
            state.equals("Registered", ignoreCase = true)
    }

    private fun isRuntimeStartingState(state: String): Boolean {
        return state.equals("Starting", ignoreCase = true) ||
            state.equals("Connecting", ignoreCase = true)
    }

    companion object {
        const val EXTRA_BOOTSTRAP = "bootstrap"
        const val EXTRA_FORCE_RESTART = "force_restart"

        private const val ACTION_START = "com.gomtm.swarm.action.START_RUNTIME"
        private const val ACTION_STOP = "com.gomtm.swarm.action.STOP_RUNTIME"
        private const val NOTIFICATION_CHANNEL_ID = "gomtm_runtime"
        private const val NOTIFICATION_ID = 41001
        private const val TICK_INTERVAL_MS = 750L
        private const val LOG_TAG = "GomtmForegroundSvc"

        fun start(
            context: Context,
            bootstrapAddress: String,
            forceRestart: Boolean,
        ) {
            val intent = Intent(context, GomtmForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BOOTSTRAP, bootstrapAddress)
                putExtra(EXTRA_FORCE_RESTART, forceRestart)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GomtmForegroundService::class.java).apply { action = ACTION_STOP })
        }
    }
}
