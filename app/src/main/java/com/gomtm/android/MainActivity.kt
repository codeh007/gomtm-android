package com.gomtm.swarm

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.gomtm.swarm.swarm.GomtmForegroundService
import com.gomtm.swarm.swarm.NodeRuntimeStore
import com.gomtm.swarm.swarm.ScreenCaptureService
import com.gomtm.swarm.swarm.SwarmNodeConfig
import com.gomtm.swarm.swarm.SwarmRuntime
import com.gomtm.swarm.swarm.SwarmStatus
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val swarmRuntime = SwarmRuntime()
    private val runtimeStore by lazy { NodeRuntimeStore(this) }
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            val snapshot = swarmRuntime.probe()
            val drainedLogs = swarmRuntime.drainLogs().trim()
            if (drainedLogs.isNotBlank()) {
                drainedLogs.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { Log.i(LOG_TAG, "runtime_log: $it") }
            }
            Log.i(
                LOG_TAG,
                "probe state=${snapshot.state} peerId=${snapshot.peerId} bootstrap=${snapshot.bootstrapAddress} lastError=${snapshot.lastError}",
            )
            refreshServiceState(snapshot)
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private lateinit var serviceStatusValue: TextView
    private lateinit var serviceStatusDetail: TextView
    private lateinit var versionValue: TextView
    private lateinit var serviceToggleButton: MaterialButton
    private lateinit var screenCapturePermissionButton: MaterialButton

    private var isAwaitingRuntimeStart = false
    private var latestBootstrapAddress = SwarmNodeConfig.DEFAULT_BOOTSTRAP
    private var autoStartRequested = false
    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.startProjection(this, result.resultCode, data)
        }
        refreshServiceState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serviceStatusValue = findViewById(R.id.serviceStatusValue)
        serviceStatusDetail = findViewById(R.id.serviceStatusDetail)
        versionValue = findViewById(R.id.surfaceVersion)
        serviceToggleButton = findViewById(R.id.serviceToggleButton)
        screenCapturePermissionButton = findViewById(R.id.screenCapturePermissionButton)

        versionValue.text = getString(R.string.surface_version_format, BuildConfig.VERSION_NAME)
        serviceToggleButton.setOnClickListener { toggleService() }
        screenCapturePermissionButton.setOnClickListener { requestScreenCapturePermission() }

        applyIntentOverrides(intent)
        refreshServiceState()
        if (autoStartRequested) {
            requestRuntimeStartIfNeeded(forceRestart = true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentOverrides(intent)
        requestRuntimeStartIfNeeded(forceRestart = true)
    }

    override fun onStart() {
        super.onStart()
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        refreshHandler.post(autoRefreshRunnable)
    }

    override fun onStop() {
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        super.onStop()
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        super.onDestroy()
    }

    private fun applyIntentOverrides(intent: Intent?) {
        val persisted = runtimeStore.load()
        if (latestBootstrapAddress == SwarmNodeConfig.DEFAULT_BOOTSTRAP && persisted.bootstrapAddress.isNotBlank()) {
            latestBootstrapAddress = persisted.bootstrapAddress
        }
        autoStartRequested = persisted.autoStart
        val requestedBootstrap = intent?.getStringExtra(EXTRA_BOOTSTRAP)?.trim().orEmpty()
        if (requestedBootstrap.isNotBlank()) {
            latestBootstrapAddress = requestedBootstrap
            autoStartRequested = true
        }
        if (intent?.getBooleanExtra(EXTRA_AUTO_START, false) == true) {
            autoStartRequested = true
        }
    }

    private fun requestRuntimeStartIfNeeded(forceRestart: Boolean = false) {
        val snapshot = swarmRuntime.probe()
        val bootstrapChanged = latestBootstrapAddress.isNotBlank() && latestBootstrapAddress != snapshot.bootstrapAddress
        if (!forceRestart && isRuntimeActiveState(snapshot.state) && !bootstrapChanged) {
            refreshServiceState(snapshot)
            return
        }
        requestRuntimeStart()
    }

    private fun toggleService() {
        val snapshot = swarmRuntime.probe()
        if (isRuntimeActiveState(snapshot.state) || isRuntimeStartingState(snapshot.state)) {
            isAwaitingRuntimeStart = false
            autoStartRequested = false
            runtimeStore.save(com.gomtm.swarm.swarm.NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress, autoStart = false))
            GomtmForegroundService.stop(this)
            refreshServiceState()
            return
        }
        requestRuntimeStart()
    }

    private fun requestRuntimeStart() {
        isAwaitingRuntimeStart = true
        autoStartRequested = true
        runtimeStore.save(com.gomtm.swarm.swarm.NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress, autoStart = true))
        Log.i(LOG_TAG, "requestRuntimeStart bootstrap=$latestBootstrapAddress")
        renderServiceState(
            SwarmStatus(
                bridgeClassName = "",
                state = "Starting",
                peerId = "",
                bootstrapAddress = latestBootstrapAddress,
                lastError = "",
                discoveredPeers = emptyList(),
                rawDiscoveredPeers = "",
            ),
        )
        GomtmForegroundService.start(
            context = this,
            bootstrapAddress = latestBootstrapAddress.ifBlank { SwarmNodeConfig.DEFAULT_BOOTSTRAP },
            autoStart = true,
            forceRestart = true,
        )
        refreshServiceState()
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: return
        screenCapturePermissionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun refreshServiceState(snapshot: SwarmStatus = swarmRuntime.probe()) {
        if (isRuntimeActiveState(snapshot.state)) {
            isAwaitingRuntimeStart = false
        }
        renderServiceState(snapshot)
    }

    private fun renderServiceState(snapshot: SwarmStatus) {
        when {
            isRuntimeActiveState(snapshot.state) -> applyServiceState(
                stateText = R.string.service_state_running,
                detailText = detailForSnapshot(snapshot),
                buttonContentDescription = R.string.service_action_stop,
                badgeBackground = R.drawable.bg_status_running,
                textColor = R.color.gomtm_status_running_text,
                buttonIcon = R.drawable.ic_node_online,
                buttonEnabled = true,
            )

            snapshot.state.equals("Error", ignoreCase = true) -> applyServiceState(
                stateText = R.string.service_state_error,
                detailText = detailForSnapshot(snapshot),
                buttonContentDescription = R.string.service_action_start,
                badgeBackground = R.drawable.bg_status_stopped,
                textColor = R.color.gomtm_status_stopped_text,
                buttonIcon = R.drawable.ic_node_offline,
                buttonEnabled = true,
            )

            isRuntimeStartingState(snapshot.state) -> applyServiceState(
                stateText = R.string.service_state_starting,
                detailText = detailForSnapshot(snapshot),
                buttonContentDescription = R.string.service_action_stop,
                badgeBackground = R.drawable.bg_status_starting,
                textColor = R.color.gomtm_status_starting_text,
                buttonIcon = R.drawable.ic_node_starting,
                buttonEnabled = true,
            )

            isAwaitingRuntimeStart -> applyServiceState(
                stateText = R.string.service_state_starting,
                detailText = getString(R.string.service_state_starting_detail),
                buttonContentDescription = R.string.service_action_starting,
                badgeBackground = R.drawable.bg_status_starting,
                textColor = R.color.gomtm_status_starting_text,
                buttonIcon = R.drawable.ic_node_starting,
                buttonEnabled = false,
            )

            else -> applyServiceState(
                stateText = R.string.service_state_stopped,
                detailText = getString(R.string.service_state_stopped_detail),
                buttonContentDescription = R.string.service_action_start,
                badgeBackground = R.drawable.bg_status_stopped,
                textColor = R.color.gomtm_status_stopped_text,
                buttonIcon = R.drawable.ic_node_offline,
                buttonEnabled = true,
            )
        }
    }

    private fun detailForSnapshot(snapshot: SwarmStatus): String {
        return when {
            isRuntimeActiveState(snapshot.state) && snapshot.peerId.isNotBlank() -> getString(
                R.string.service_state_running_detail_peer,
                snapshot.peerId,
            )

            isRuntimeActiveState(snapshot.state) -> getString(R.string.service_state_running_detail)

            snapshot.state.equals("Error", ignoreCase = true) -> getString(
                R.string.service_state_error_detail,
                snapshot.lastError.ifBlank { getString(R.string.service_state_error_unknown) },
            )

            snapshot.bootstrapAddress.isNotBlank() -> getString(
                R.string.service_state_starting_detail_runtime,
                snapshot.state.ifBlank { getString(R.string.service_state_starting) },
                snapshot.bootstrapAddress,
            )

            else -> getString(R.string.service_state_starting_detail)
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

    private fun applyServiceState(
        stateText: Int,
        detailText: String,
        buttonContentDescription: Int,
        @DrawableRes badgeBackground: Int,
        @ColorRes textColor: Int,
        @DrawableRes buttonIcon: Int,
        buttonEnabled: Boolean,
    ) {
        serviceStatusValue.text = getString(stateText)
        serviceStatusValue.setBackgroundResource(badgeBackground)
        serviceStatusValue.setTextColor(ContextCompat.getColor(this, textColor))
        serviceStatusDetail.text = detailText
        serviceToggleButton.text = ""
        serviceToggleButton.contentDescription = getString(buttonContentDescription)
        serviceToggleButton.setIconResource(buttonIcon)
        serviceToggleButton.iconTint = null
        serviceToggleButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.surface_panel),
        )
        serviceToggleButton.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.gomtm_outline_variant),
        )
        serviceToggleButton.isEnabled = buttonEnabled
    }

    companion object {
        private const val LOG_TAG = "GomtmMainActivity"
        private const val EXTRA_BOOTSTRAP = "bootstrap"
        private const val EXTRA_AUTO_START = "auto_start"
        private const val REFRESH_INTERVAL_MS = 750L
    }
}
