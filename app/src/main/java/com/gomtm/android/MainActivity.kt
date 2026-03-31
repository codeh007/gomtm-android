package com.gomtm.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gomtm.android.swarm.DiscoveredPeer
import com.gomtm.android.swarm.SwarmNodeConfig
import com.gomtm.android.swarm.SwarmRuntime
import com.gomtm.android.swarm.SwarmUiModel
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private val swarmRuntime = SwarmRuntime()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val backgroundWorker = Executors.newSingleThreadExecutor()
    private val remoteControlTickRunning = AtomicBoolean(false)
    private var actionError: String? = null
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (remoteControlTickRunning.compareAndSet(false, true)) {
                backgroundWorker.execute {
                    try {
                        swarmRuntime.processRemoteControlTick(this@MainActivity, timeoutMs = 0)
                    } catch (_: Throwable) {
                    } finally {
                        remoteControlTickRunning.set(false)
                    }
                }
            }
            render()
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        restoreInputs()

        findViewById<MaterialButton>(R.id.startButton).setOnClickListener {
            saveInputs()
            runAction {
                swarmRuntime.start(this, currentConfig())
            }
        }
        findViewById<MaterialButton>(R.id.stopButton).setOnClickListener {
            runAction {
                swarmRuntime.stop()
            }
        }
        findViewById<MaterialButton>(R.id.refreshButton).setOnClickListener {
            saveInputs()
            actionError = null
            render()
        }
        findViewById<MaterialButton>(R.id.accessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        maybeAutoStartFromIntent()
        render()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        refreshHandler.post(autoRefreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        backgroundWorker.shutdownNow()
        super.onDestroy()
    }

    private fun runAction(action: () -> Unit) {
        actionError = null
        try {
            action()
        } catch (error: Exception) {
            actionError = error.message ?: error.javaClass.simpleName
        }
        render()
    }

    private fun currentConfig(): SwarmNodeConfig {
        val bootstrap = inputText(R.id.bootstrapInput).ifBlank { SwarmNodeConfig.DEFAULT_BOOTSTRAP }
        val nodeName = inputText(R.id.nodeNameInput).ifBlank { SwarmNodeConfig.defaultNodeName() }
        return SwarmNodeConfig(
            bootstrapAddress = bootstrap,
            nodeName = nodeName,
            autoReconnect = true,
        )
    }

    private fun maybeAutoStartFromIntent() {
        if (!intent.getBooleanExtra(EXTRA_AUTO_START, false)) {
            return
        }
        saveInputs()
        runAction {
            swarmRuntime.start(this, currentConfig())
        }
    }

    private fun render() {
        val status = swarmRuntime.probe()
        val logs = swarmRuntime.drainLogs()
        val uiModel = SwarmUiModel.from(status = status, actionError = actionError)
        val remotePermissions = swarmRuntime.remoteControlPermissionState(this)

        setText(R.id.bridgeValue, status.bridgeClassName.ifBlank { getString(R.string.value_not_available) })
        setText(R.id.stateValue, status.state)
        setText(R.id.peerValue, status.peerId.ifBlank { getString(R.string.value_not_available) })
        setText(R.id.bootstrapValue, uiModel.connectedBootstrap)
        setText(R.id.accessibilityValue, remotePermissions.accessibility)
        setText(R.id.screenCaptureValue, remotePermissions.screenCapture)
        setText(R.id.errorValue, uiModel.errorText)
        setText(R.id.peersValue, formatPeers(status.discoveredPeers))
        setText(R.id.logsValue, logs.ifBlank { getString(R.string.value_no_logs_yet) })
    }

    private fun formatPeers(peers: List<DiscoveredPeer>): String {
        if (peers.isEmpty()) {
            return getString(R.string.value_no_peers_yet)
        }
        return peers.joinToString(separator = "\n\n") { peer ->
            buildString {
                append(peer.name ?: peer.peerId)
                append("\npeer_id=")
                append(peer.peerId)
                append("\nstate=")
                append(peer.state)
                append("\nlast_seen_at=")
                append(peer.lastSeenAt.ifBlank { "unknown" })
            }
        }
    }

    private fun restoreInputs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setInput(
            R.id.bootstrapInput,
            prefs.getString(KEY_BOOTSTRAP, SwarmNodeConfig.DEFAULT_BOOTSTRAP).orEmpty(),
        )
        setInput(
            R.id.nodeNameInput,
            prefs.getString(KEY_NODE_NAME, SwarmNodeConfig.defaultNodeName()).orEmpty(),
        )
    }

    private fun saveInputs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOTSTRAP, inputText(R.id.bootstrapInput))
            .putString(KEY_NODE_NAME, inputText(R.id.nodeNameInput))
            .apply()
    }

    private fun inputText(viewId: Int): String = findViewById<EditText>(viewId).text.toString().trim()

    private fun setText(viewId: Int, value: String) {
        findViewById<TextView>(viewId).text = value
    }

    private fun setInput(viewId: Int, value: String) {
        findViewById<EditText>(viewId).setText(value)
    }

    companion object {
        private const val PREFS_NAME = "gomtm-android"
        private const val KEY_BOOTSTRAP = "bootstrap"
        private const val KEY_NODE_NAME = "node_name"
        private const val EXTRA_AUTO_START = "auto_start"
        private const val REFRESH_INTERVAL_MS = 750L
    }
}
