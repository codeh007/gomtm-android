package com.gomtm.swarm.web

import android.content.Context
import android.provider.Settings
import android.webkit.JavascriptInterface
import com.gomtm.swarm.swarm.DiscoveredPeer
import com.gomtm.swarm.swarm.RemoteControlPermissionState
import com.gomtm.swarm.swarm.SwarmRuntime
import com.gomtm.swarm.swarm.SwarmStatus
import org.json.JSONArray
import org.json.JSONObject

const val HOST_BRIDGE_NAME = "GomtmAndroid"

interface SwarmRuntimeHost {
    fun start(config: com.gomtm.swarm.swarm.SwarmNodeConfig)

    fun stop()

    fun probe(): SwarmStatus

    fun drainLogs(): String

    fun remoteControlPermissionState(): RemoteControlPermissionState
}

class SwarmRuntimeBridgeHost(
    private val context: Context,
    private val runtime: SwarmRuntime = SwarmRuntime(),
) : SwarmRuntimeHost {
    override fun start(config: com.gomtm.swarm.swarm.SwarmNodeConfig) {
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

class GomtmHostBridge(
    private val runtimeHost: SwarmRuntimeHost,
    private val settingsStore: HostSettingsStore,
    private val launchAccessibilitySettings: () -> Unit,
	private val requestScreenCapturePermissionAction: () -> Boolean,
    private val navigateToUrl: (String) -> Unit,
) {
    @JavascriptInterface
    fun isAvailable(): Boolean = true

    @JavascriptInterface
    fun getRuntimeSnapshot(): String {
        return buildSnapshot(settingsStore.load())
    }

    @JavascriptInterface
    fun saveHostSettings(configJson: String): String {
        return runCatching {
            val nextSettings = mergeSettings(settingsStore.load(), configJson)
            settingsStore.save(nextSettings)
            buildSnapshot(nextSettings)
        }.getOrElse { error ->
            buildSnapshot(settingsStore.load(), actionError = error.message ?: error.javaClass.simpleName)
        }
    }

    @JavascriptInterface
    fun startNode(configJson: String): String {
        return runCatching {
            val nextSettings = mergeSettings(settingsStore.load(), configJson)
            settingsStore.save(nextSettings)
            runtimeHost.start(nextSettings.toSwarmNodeConfig())
            buildSnapshot(nextSettings)
        }.getOrElse { error ->
            buildSnapshot(settingsStore.load(), actionError = error.message ?: error.javaClass.simpleName)
        }
    }

    @JavascriptInterface
    fun stopNode(): String {
        return runCatching {
            runtimeHost.stop()
            buildSnapshot(settingsStore.load())
        }.getOrElse { error ->
            buildSnapshot(settingsStore.load(), actionError = error.message ?: error.javaClass.simpleName)
        }
    }

    @JavascriptInterface
    fun openAccessibilitySettings(): Boolean {
        launchAccessibilitySettings()
        return true
    }

    @JavascriptInterface
    fun requestScreenCapturePermission(): Boolean {
		return requestScreenCapturePermissionAction()
    }

    @JavascriptInterface
    fun openConsoleUrl(rawUrl: String): String {
        val current = settingsStore.load()
        val candidate = rawUrl.trim().ifBlank { current.consoleUrl }
        val normalized = resolveHostNavigationUrl(candidate)
            ?: return JSONObject().put("ok", false).put("error", "invalid_console_url").toString()

        settingsStore.save(current.copy(consoleUrl = normalized))
        navigateToUrl(normalized)
        return JSONObject().put("ok", true).put("url", normalized).toString()
    }

    private fun mergeSettings(current: HostSettings, configJson: String): HostSettings {
        val json = runCatching { JSONObject(configJson) }.getOrNull()
        return current.copy(
            bootstrapAddress = json?.optString("bootstrap_address")?.trim().orEmpty().ifBlank { current.bootstrapAddress },
            nodeName = json?.optString("node_name")?.trim().orEmpty().ifBlank { current.nodeName },
            consoleUrl = json?.optString("console_url")?.trim().orEmpty().ifBlank { current.consoleUrl },
        )
    }

    private fun buildSnapshot(settings: HostSettings, actionError: String? = null): String {
        val status = runtimeHost.probe()
        val permissions = runtimeHost.remoteControlPermissionState()
        val logs = runtimeHost.drainLogs()
        return JSONObject()
            .put(
                "host",
                JSONObject()
                    .put("available", true)
                    .put("surface", "android_webview")
                    .put("surface_version", "v2")
                    .put("bridge_name", HOST_BRIDGE_NAME),
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
                    .put("action_error", actionError.orEmpty())
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

    companion object {
        const val LOCAL_BOOTSTRAP_URL = "https://appassets.androidplatform.net/assets/bootstrap/index.html"
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
