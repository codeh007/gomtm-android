package com.gomtm.android.web

import android.content.Context
import android.provider.Settings
import android.webkit.JavascriptInterface
import com.gomtm.android.swarm.DiscoveredPeer
import com.gomtm.android.swarm.RemoteControlPermissionState
import com.gomtm.android.swarm.SwarmRuntime
import com.gomtm.android.swarm.SwarmStatus
import org.json.JSONArray
import org.json.JSONObject

interface SwarmRuntimeHost {
    fun start(config: com.gomtm.android.swarm.SwarmNodeConfig)

    fun stop()

    fun probe(): SwarmStatus

    fun drainLogs(): String

    fun remoteControlPermissionState(): RemoteControlPermissionState
}

class SwarmRuntimeBridgeHost(
    private val context: Context,
    private val runtime: SwarmRuntime = SwarmRuntime(),
) : SwarmRuntimeHost {
    override fun start(config: com.gomtm.android.swarm.SwarmNodeConfig) {
        runtime.start(context, config)
    }

    override fun stop() {
        runtime.stop()
    }

    override fun probe(): SwarmStatus = runtime.probe()

    override fun drainLogs(): String = runtime.drainLogs()

    override fun remoteControlPermissionState(): RemoteControlPermissionState {
        return runtime.remoteControlPermissionState(context)
    }
}

class GomtmWebViewBridge(
    private val runtimeHost: SwarmRuntimeHost,
    private val settingsStore: WebConsoleSettingsStore,
    private val openAccessibilitySettings: () -> Unit,
) {
    @JavascriptInterface
    fun isAvailable(): Boolean = true

    @JavascriptInterface
    fun getRuntimeSnapshot(): String {
        return buildSnapshot(settingsStore.load())
    }

    @JavascriptInterface
    fun startNode(configJson: String): String {
        val nextSettings = mergeSettings(settingsStore.load(), configJson)
        settingsStore.save(nextSettings)
        runtimeHost.start(nextSettings.toSwarmNodeConfig())
        return buildSnapshot(nextSettings)
    }

    @JavascriptInterface
    fun stopNode(): String {
        runtimeHost.stop()
        return buildSnapshot(settingsStore.load())
    }

    @JavascriptInterface
    fun openAccessibilitySettings(): Boolean {
        openAccessibilitySettings()
        return true
    }

    private fun mergeSettings(current: WebConsoleSettings, configJson: String): WebConsoleSettings {
        val json = runCatching { JSONObject(configJson) }.getOrNull()
        return current.copy(
            bootstrapAddress = json?.optString("bootstrap_address")?.trim().orEmpty().ifBlank { current.bootstrapAddress },
            nodeName = json?.optString("node_name")?.trim().orEmpty().ifBlank { current.nodeName },
        )
    }

    private fun buildSnapshot(settings: WebConsoleSettings): String {
        val status = runtimeHost.probe()
        val permissions = runtimeHost.remoteControlPermissionState()
        val logs = runtimeHost.drainLogs()
        return JSONObject()
            .put(
                "host",
                JSONObject()
                    .put("available", true)
                    .put("surface", "android_webview")
                    .put("surface_version", "v1"),
            )
            .put(
                "config",
                JSONObject()
                    .put("bootstrap_address", settings.bootstrapAddress)
                    .put("node_name", settings.nodeName)
                    .put("console_url", settings.consoleUrl),
            )
            .put(
                "runtime",
                JSONObject()
                    .put("state", status.state)
                    .put("peer_id", status.peerId)
                    .put("bootstrap_address", status.bootstrapAddress)
                    .put("last_error", status.lastError)
                    .put("recent_logs", logs)
                    .put("bridge_class_name", status.bridgeClassName)
                    .put(
                        "permissions",
                        JSONObject()
                            .put("accessibility", permissions.accessibility)
                            .put("screen_capture", permissions.screenCapture),
                    )
                    .put("discovered_peers", status.discoveredPeers.toJsonArray()),
            )
            .toString()
    }
}

fun defaultAccessibilitySettingsLauncher(context: Context): () -> Unit {
    return {
        context.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun List<DiscoveredPeer>.toJsonArray(): JSONArray {
    return JSONArray().apply {
        for (peer in this@toJsonArray) {
            put(
                JSONObject()
                    .put("peer_id", peer.peerId)
                    .put("name", peer.name)
                    .put("state", peer.state)
                    .put("discovered_in_current_session", peer.discoveredInCurrentSession)
                    .put("is_bootstrap", peer.isBootstrap)
                    .put("last_seen_at", peer.lastSeenAt),
            )
        }
    }
}
