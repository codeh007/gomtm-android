package com.gomtm.swarm

import android.animation.ValueAnimator
import android.app.Activity
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
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.gomtm.swarm.platform.lifecycle.GomtmForegroundService
import com.gomtm.swarm.platform.lifecycle.ScreenCaptureService
import com.gomtm.swarm.runtime.GomtmRuntimeFacade
import com.gomtm.swarm.runtime.RuntimeSnapshot
import com.gomtm.swarm.shell.BootstrapInputParser
import com.gomtm.swarm.shell.NodeRuntimeConfig
import com.gomtm.swarm.shell.NodeRuntimeStore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val swarmRuntime = GomtmRuntimeFacade()
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
    private lateinit var advancedMenuButton: ImageButton
    private lateinit var serviceStateValue: TextView
    private lateinit var serviceStatusHint: TextView
    private lateinit var versionValue: TextView

    private var currentSurfaceColor: Int? = null
    private var isAwaitingRuntimeStart = false
    private var latestBootstrapAddress = ""

    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.startProjection(this, result.resultCode, data)
        }
        refreshServiceState()
    }

    private val bootstrapScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents.orEmpty()
        if (contents.isBlank()) {
            return@registerForActivityResult
        }

        val parsed = BootstrapInputParser.parse(contents)
        if (parsed.bootstrapAddress != null) {
            showBootstrapDialog(parsed.bootstrapAddress)
        } else {
            Toast.makeText(
                this,
                parsed.errorMessage ?: getString(R.string.bootstrap_scan_invalid_message),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        runtimeSurface = findViewById(R.id.runtimeSurface)
        peerSuffixValue = findViewById(R.id.peerSuffixValue)
        versionValue = findViewById(R.id.surfaceVersion)
        advancedMenuButton = findViewById(R.id.advancedMenuButton)
        serviceStateValue = findViewById(R.id.serviceStateValue)
        serviceStatusHint = findViewById(R.id.serviceStatusHint)

        versionValue.text = getString(R.string.surface_version_format, BuildConfig.VERSION_NAME)
        advancedMenuButton.setOnClickListener { showAdvancedMenu(it) }

        val skipImmediateStart = applyIntentOverrides(intent)
        refreshServiceState()
        if (!skipImmediateStart) {
            requestRuntimeStartIfNeeded(forceRestart = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val skipImmediateStart = applyIntentOverrides(intent)
        if (!skipImmediateStart) {
            requestRuntimeStartIfNeeded(forceRestart = true)
        }
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

    private fun applyIntentOverrides(intent: Intent?): Boolean {
        val persisted = runtimeStore.load()
        if (latestBootstrapAddress.isBlank() && persisted.bootstrapAddress.isNotBlank()) {
            latestBootstrapAddress = persisted.bootstrapAddress
        }

        val internalBootstrap = intent?.getStringExtra(INTERNAL_BOOTSTRAP_EXTRA)?.trim().orEmpty()
        if (internalBootstrap.isNotBlank()) {
            val parsed = BootstrapInputParser.parse(internalBootstrap)
            if (parsed.bootstrapAddress != null) {
                latestBootstrapAddress = parsed.bootstrapAddress
            }
            return false
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            val deepLink = intent.dataString.orEmpty()
            if (deepLink.isNotBlank()) {
                val parsed = BootstrapInputParser.parse(deepLink)
                if (parsed.bootstrapAddress != null) {
                    showBootstrapDialog(parsed.bootstrapAddress)
                    return true
                }

                Toast.makeText(
                    this,
                    parsed.errorMessage ?: getString(R.string.bootstrap_invalid_message),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        return false
    }

    private fun requestRuntimeStartIfNeeded(forceRestart: Boolean = false) {
        if (latestBootstrapAddress.isBlank()) {
            isAwaitingRuntimeStart = false
            refreshServiceState()
            return
        }

        val snapshot = swarmRuntime.probe()
        val bootstrapChanged = latestBootstrapAddress != snapshot.bootstrapAddress
        if (isRuntimeStartingState(snapshot.state)) {
            refreshServiceState(snapshot)
            return
        }
        if (!forceRestart && isRuntimeActiveState(snapshot.state) && !bootstrapChanged) {
            refreshServiceState(snapshot)
            return
        }

        requestRuntimeRestart()
    }

    private fun showAdvancedMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.main_actions, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit_bootstrap -> {
                        showBootstrapDialog(latestBootstrapAddress)
                        true
                    }

                    R.id.action_scan_bootstrap -> {
                        bootstrapScanLauncher.launch(
                            ScanOptions().apply {
                                setOrientationLocked(false)
                            },
                        )
                        true
                    }

                    R.id.action_request_screen_capture -> {
                        requestScreenCapturePermission()
                        true
                    }

                    R.id.action_reconnect_runtime -> {
                        requestRuntimeRestart()
                        true
                    }

                    else -> false
                }
            }
        }.show()
    }

    private fun showBootstrapDialog(initialValue: String) {
        val contentView = layoutInflater.inflate(R.layout.dialog_bootstrap_input, null)
        val input = contentView.findViewById<TextInputEditText>(R.id.bootstrapInputValue)
        input.setText(initialValue)

        AlertDialog.Builder(this)
            .setTitle(R.string.menu_edit_bootstrap)
            .setView(contentView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.bootstrap_save_action) { _, _ ->
                val parsed = BootstrapInputParser.parse(input.text?.toString().orEmpty())
                if (parsed.bootstrapAddress != null) {
                    latestBootstrapAddress = parsed.bootstrapAddress
                    runtimeStore.save(NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress))
                    requestRuntimeRestart()
                } else {
                    Toast.makeText(
                        this,
                        parsed.errorMessage ?: getString(R.string.bootstrap_invalid_message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .show()
    }

    private fun requestRuntimeRestart() {
        if (latestBootstrapAddress.isBlank()) {
            isAwaitingRuntimeStart = false
            refreshServiceState(RuntimeSnapshot.missing(getString(R.string.bootstrap_required_message)))
            return
        }

        isAwaitingRuntimeStart = true
        runtimeStore.save(NodeRuntimeConfig(bootstrapAddress = latestBootstrapAddress))
        GomtmForegroundService.start(
            context = this,
            bootstrapAddress = latestBootstrapAddress,
            forceRestart = true,
        )
        refreshServiceState()
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: return
        screenCapturePermissionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun refreshServiceState(snapshot: RuntimeSnapshot = swarmRuntime.probe()) {
        if (latestBootstrapAddress.isBlank() && snapshot.bootstrapAddress.isNotBlank()) {
            latestBootstrapAddress = snapshot.bootstrapAddress
        }
        if (isRuntimeActiveState(snapshot.state)) {
            isAwaitingRuntimeStart = false
        }
        renderServiceState(snapshot)
    }

    private fun renderServiceState(snapshot: RuntimeSnapshot) {
        when {
            latestBootstrapAddress.isBlank() -> renderStatus(
                stateLabel = getString(R.string.service_state_unconfigured),
                hint = snapshot.lastError.ifBlank { getString(R.string.service_hint_unconfigured) },
                peerSuffix = getString(R.string.peer_suffix_placeholder),
                surfaceBackgroundColor = R.color.gomtm_surface_dim,
                peerTextColor = R.color.gomtm_peer_suffix_dim,
                versionTextColor = R.color.gomtm_version_dim,
            )

            isRuntimeActiveState(snapshot.state) -> renderStatus(
                stateLabel = getString(R.string.service_state_ready),
                hint = getString(R.string.service_hint_ready),
                peerSuffix = peerSuffixForSnapshot(snapshot),
                surfaceBackgroundColor = R.color.gomtm_surface_live,
                peerTextColor = R.color.gomtm_peer_suffix_live,
                versionTextColor = R.color.gomtm_version_live,
            )

            snapshot.state.equals("Error", ignoreCase = true) -> renderStatus(
                stateLabel = getString(R.string.service_state_error),
                hint = snapshot.lastError.ifBlank { getString(R.string.service_hint_error) },
                peerSuffix = peerSuffixForSnapshot(snapshot),
                surfaceBackgroundColor = R.color.gomtm_surface_error,
                peerTextColor = R.color.gomtm_peer_suffix_error,
                versionTextColor = R.color.gomtm_version_error,
            )

            isAwaitingRuntimeStart || snapshot.state.isNotBlank() -> renderStatus(
                stateLabel = getString(R.string.service_state_connecting),
                hint = getString(R.string.service_hint_connecting),
                peerSuffix = peerSuffixForSnapshot(snapshot),
                surfaceBackgroundColor = R.color.gomtm_surface_dim,
                peerTextColor = R.color.gomtm_peer_suffix_dim,
                versionTextColor = R.color.gomtm_version_dim,
            )

            else -> renderStatus(
                stateLabel = getString(R.string.service_state_connecting),
                hint = getString(R.string.service_hint_connecting),
                peerSuffix = peerSuffixForSnapshot(snapshot),
                surfaceBackgroundColor = R.color.gomtm_surface_dim,
                peerTextColor = R.color.gomtm_peer_suffix_dim,
                versionTextColor = R.color.gomtm_version_dim,
            )
        }
    }

    private fun renderStatus(
        stateLabel: String,
        hint: String,
        peerSuffix: String,
        @ColorRes surfaceBackgroundColor: Int,
        @ColorRes peerTextColor: Int,
        @ColorRes versionTextColor: Int,
    ) {
        val versionColor = resolveColor(versionTextColor)
        peerSuffixValue.text = peerSuffix
        peerSuffixValue.setTextColor(resolveColor(peerTextColor))
        serviceStateValue.text = stateLabel
        serviceStateValue.setTextColor(versionColor)
        serviceStatusHint.text = hint
        serviceStatusHint.setTextColor(versionColor)
        versionValue.setTextColor(versionColor)
        advancedMenuButton.imageTintList = ColorStateList.valueOf(versionColor)
        animateSurfaceBackground(resolveColor(surfaceBackgroundColor))
    }

    private fun peerSuffixForSnapshot(snapshot: RuntimeSnapshot): String {
        val peerId = snapshot.peerId.trim()
        if (peerId.isEmpty()) {
            return getString(R.string.peer_suffix_placeholder)
        }
        return peerId.takeLast(8).uppercase(Locale.ROOT)
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
        private const val INTERNAL_BOOTSTRAP_EXTRA = "bootstrap"
        private const val LOG_TAG = "GomtmMainActivity"
        private const val REFRESH_INTERVAL_MS = 750L
    }
}
