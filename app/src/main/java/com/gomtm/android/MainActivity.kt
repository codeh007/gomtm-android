package com.gomtm.swarm

import android.app.Activity
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import java.util.Locale
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.gomtm.swarm.swarm.GomtmForegroundService
import com.gomtm.swarm.swarm.NodeRuntimeStore
import com.gomtm.swarm.swarm.ScreenCaptureService
import com.gomtm.swarm.swarm.SwarmNodeConfig
import com.gomtm.swarm.swarm.SwarmRuntime
import com.gomtm.swarm.swarm.SwarmStatus

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

    private lateinit var runtimeSurface: View
    private lateinit var peerSuffixValue: TextView
    private lateinit var serviceToggleButton: MaterialButton
    private lateinit var screenCapturePermissionButton: MaterialButton
    private lateinit var versionValue: TextView

    private var currentSurfaceColor: Int? = null

    private var isAwaitingRuntimeStart = false
    private var latestBootstrapAddress = SwarmNodeConfig.DEFAULT_BOOTSTRAP
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

        runtimeSurface = findViewById(R.id.runtimeSurface)
        peerSuffixValue = findViewById(R.id.peerSuffixValue)
        versionValue = findViewById(R.id.surfaceVersion)
        serviceToggleButton = findViewById(R.id.serviceToggleButton)
        screenCapturePermissionButton = findViewById(R.id.screenCapturePermissionButton)

        versionValue.text = getString(R.string.surface_version_format, BuildConfig.VERSION_NAME)
        serviceToggleButton.setOnClickListener { toggleService() }
        screenCapturePermissionButton.setOnClickListener { requestScreenCapturePermission() }
        screenCapturePermissionButton.contentDescription = getString(R.string.screen_capture_permission_action)

        applyIntentOverrides(intent)
        refreshServiceState()
        requestRuntimeStartIfNeeded(forceRestart = false)
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
        val requestedBootstrap = intent?.getStringExtra(EXTRA_BOOTSTRAP)?.trim().orEmpty()
        if (requestedBootstrap.isNotBlank()) {
            latestBootstrapAddress = requestedBootstrap
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
            runtimeStore.save(com.gomtm.swarm.swarm.NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress))
            GomtmForegroundService.stop(this)
            refreshServiceState()
            return
        }
        requestRuntimeStart()
    }

    private fun requestRuntimeStart() {
        isAwaitingRuntimeStart = true
        runtimeStore.save(com.gomtm.swarm.swarm.NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress))
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
                buttonIcon = R.drawable.ic_node_online,
                surfaceBackgroundColor = R.color.gomtm_surface_live,
                peerTextColor = R.color.gomtm_peer_suffix_live,
                versionTextColor = R.color.gomtm_version_live,
                buttonBackgroundColor = R.color.gomtm_shell_button_live_bg,
                buttonIconTintColor = R.color.gomtm_shell_button_live_icon,
                buttonStrokeColor = R.color.gomtm_shell_button_live_stroke,
                permissionIconTintColor = R.color.gomtm_shell_button_live_icon,
                permissionStrokeColor = R.color.gomtm_shell_button_live_stroke,
                buttonEnabled = true,
                peerSuffix = peerSuffixForSnapshot(snapshot),
                semanticPeerSuffix = semanticPeerSuffixForSnapshot(snapshot),
            )

            snapshot.state.equals("Error", ignoreCase = true) -> applyServiceState(
                stateText = R.string.service_state_error,
                detailText = detailForSnapshot(snapshot),
                buttonContentDescription = R.string.service_action_start,
                buttonIcon = R.drawable.ic_node_offline,
                surfaceBackgroundColor = R.color.gomtm_surface_error,
                peerTextColor = R.color.gomtm_peer_suffix_error,
                versionTextColor = R.color.gomtm_version_error,
                buttonBackgroundColor = R.color.gomtm_shell_button_error_bg,
                buttonIconTintColor = R.color.gomtm_shell_button_error_icon,
                buttonStrokeColor = R.color.gomtm_shell_button_error_stroke,
                permissionIconTintColor = R.color.gomtm_shell_button_error_icon,
                permissionStrokeColor = R.color.gomtm_shell_button_error_stroke,
                buttonEnabled = true,
                peerSuffix = peerSuffixForSnapshot(snapshot),
                semanticPeerSuffix = semanticPeerSuffixForSnapshot(snapshot),
            )

            isRuntimeStartingState(snapshot.state) -> applyServiceState(
                stateText = R.string.service_state_starting,
                detailText = detailForSnapshot(snapshot),
                buttonContentDescription = R.string.service_action_stop,
                buttonIcon = R.drawable.ic_node_starting,
                surfaceBackgroundColor = R.color.gomtm_surface_dim,
                peerTextColor = R.color.gomtm_peer_suffix_dim,
                versionTextColor = R.color.gomtm_version_dim,
                buttonBackgroundColor = R.color.gomtm_shell_button_dim_bg,
                buttonIconTintColor = R.color.gomtm_shell_button_dim_icon,
                buttonStrokeColor = R.color.gomtm_shell_button_dim_stroke,
                permissionIconTintColor = R.color.gomtm_shell_button_dim_icon,
                permissionStrokeColor = R.color.gomtm_shell_button_dim_stroke,
                buttonEnabled = true,
                peerSuffix = peerSuffixForSnapshot(snapshot),
                semanticPeerSuffix = semanticPeerSuffixForSnapshot(snapshot),
            )

            isAwaitingRuntimeStart -> applyServiceState(
                stateText = R.string.service_state_starting,
                detailText = getString(R.string.service_state_starting_detail),
                buttonContentDescription = R.string.service_action_starting,
                buttonIcon = R.drawable.ic_node_starting,
                surfaceBackgroundColor = R.color.gomtm_surface_dim,
                peerTextColor = R.color.gomtm_peer_suffix_dim,
                versionTextColor = R.color.gomtm_version_dim,
                buttonBackgroundColor = R.color.gomtm_shell_button_dim_bg,
                buttonIconTintColor = R.color.gomtm_shell_button_dim_icon,
                buttonStrokeColor = R.color.gomtm_shell_button_dim_stroke,
                permissionIconTintColor = R.color.gomtm_shell_button_dim_icon,
                permissionStrokeColor = R.color.gomtm_shell_button_dim_stroke,
                buttonEnabled = false,
                peerSuffix = peerSuffixForSnapshot(snapshot),
                semanticPeerSuffix = semanticPeerSuffixForSnapshot(snapshot),
            )

            else -> applyServiceState(
                stateText = R.string.service_state_stopped,
                detailText = getString(R.string.service_state_stopped_detail),
                buttonContentDescription = R.string.service_action_start,
                buttonIcon = R.drawable.ic_node_offline,
                surfaceBackgroundColor = R.color.gomtm_surface_dim,
                peerTextColor = R.color.gomtm_peer_suffix_dim,
                versionTextColor = R.color.gomtm_version_dim,
                buttonBackgroundColor = R.color.gomtm_shell_button_dim_bg,
                buttonIconTintColor = R.color.gomtm_shell_button_dim_icon,
                buttonStrokeColor = R.color.gomtm_shell_button_dim_stroke,
                permissionIconTintColor = R.color.gomtm_shell_button_dim_icon,
                permissionStrokeColor = R.color.gomtm_shell_button_dim_stroke,
                buttonEnabled = true,
                peerSuffix = peerSuffixForSnapshot(snapshot),
                semanticPeerSuffix = semanticPeerSuffixForSnapshot(snapshot),
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
        @ColorRes surfaceBackgroundColor: Int,
        @ColorRes peerTextColor: Int,
        @ColorRes versionTextColor: Int,
        @DrawableRes buttonIcon: Int,
        @ColorRes buttonBackgroundColor: Int,
        @ColorRes buttonIconTintColor: Int,
        @ColorRes buttonStrokeColor: Int,
        @ColorRes permissionIconTintColor: Int,
        @ColorRes permissionStrokeColor: Int,
        buttonEnabled: Boolean,
        peerSuffix: String,
        semanticPeerSuffix: String?,
    ) {
        val buttonHint = getString(buttonContentDescription)
        val stateLabel = getString(stateText)
        peerSuffixValue.text = peerSuffix
        peerSuffixValue.setTextColor(resolveColor(peerTextColor))
        versionValue.setTextColor(resolveColor(versionTextColor))
        animateSurfaceBackground(resolveColor(surfaceBackgroundColor))
        serviceToggleButton.text = ""
        serviceToggleButton.contentDescription = listOfNotNull(
            stateLabel,
            semanticPeerSuffix,
            detailText,
            buttonHint,
        ).joinToString(separator = ". ")
        serviceToggleButton.setIconResource(buttonIcon)
        serviceToggleButton.iconTint = ColorStateList.valueOf(resolveColor(buttonIconTintColor))
        serviceToggleButton.backgroundTintList = ColorStateList.valueOf(resolveColor(buttonBackgroundColor))
        serviceToggleButton.strokeColor = ColorStateList.valueOf(resolveColor(buttonStrokeColor))
        serviceToggleButton.isEnabled = buttonEnabled
        screenCapturePermissionButton.iconTint = ColorStateList.valueOf(resolveColor(permissionIconTintColor))
        screenCapturePermissionButton.strokeColor = ColorStateList.valueOf(resolveColor(permissionStrokeColor))
    }

    private fun peerSuffixForSnapshot(snapshot: SwarmStatus): String {
        val peerId = snapshot.peerId.trim()
        if (peerId.isEmpty()) {
            return getString(R.string.peer_suffix_placeholder)
        }
        return peerId.takeLast(8).uppercase(Locale.ROOT)
    }

    private fun semanticPeerSuffixForSnapshot(snapshot: SwarmStatus): String? {
        val peerId = snapshot.peerId.trim()
        if (peerId.isEmpty()) {
            return null
        }
        return peerId.takeLast(8).uppercase(Locale.ROOT)
    }

    private fun animateSurfaceBackground(targetColor: Int) {
        val startColor = currentSurfaceColor ?: (runtimeSurface.background as? ColorDrawable)?.color ?: targetColor
        if (startColor == targetColor) {
            runtimeSurface.setBackgroundColor(targetColor)
            currentSurfaceColor = targetColor
            return
        }
        ValueAnimator.ofArgb(startColor, targetColor).apply {
            duration = 280L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                runtimeSurface.setBackgroundColor(color)
                currentSurfaceColor = color
            }
            start()
        }
    }

    private fun resolveColor(@ColorRes colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

    companion object {
        private const val LOG_TAG = "GomtmMainActivity"
        private const val EXTRA_BOOTSTRAP = "bootstrap"
        private const val REFRESH_INTERVAL_MS = 750L
    }
}
