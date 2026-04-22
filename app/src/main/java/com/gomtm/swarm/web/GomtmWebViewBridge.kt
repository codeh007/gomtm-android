package com.gomtm.swarm.web

import android.app.Activity
import android.webkit.JavascriptInterface
import com.gomtm.swarm.BuildConfig
import com.gomtm.swarm.runtime.DiscoveredPeer
import com.gomtm.swarm.runtime.GomtmRuntimeFacade
import com.gomtm.swarm.runtime.RuntimeSnapshot
import com.gomtm.swarm.shell.ConnectionInputParser
import com.gomtm.swarm.shell.NodeRuntimeConfig
import com.gomtm.swarm.shell.NodeRuntimeStore
import org.json.JSONArray
import org.json.JSONObject

class GomtmWebViewBridge(
    private val activity: Activity,
    private val runtime: GomtmRuntimeFacade,
    private val store: NodeRuntimeStore,
    private val onScreenCaptureRequested: () -> Unit,
    private val onRuntimeRestartRequested: (String?) -> Unit,
) {
    @JavascriptInterface
    fun getHostInfo(): String {
        return JSONObject()
            .put("hostKind", "android-host")
            .put("packageName", activity.packageName)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("dashP2pUrl", BuildConfig.GOMTM_UI_DASH_P2P_URL)
            .toString()
    }

    @JavascriptInterface
    fun getConnectionConfig(): String {
        val config = store.load()
        return JSONObject()
            .put("connectionAddress", config.connectionAddress)
            .toString()
    }

    @JavascriptInterface
    fun getRuntimeSnapshot(): String = encodeRuntimeSnapshot(runtime.probe())

    @JavascriptInterface
    fun listDiscoveredPeers(): String = encodeDiscoveredPeers(runtime.probe().discoveredPeers)

    @JavascriptInterface
    fun saveConnectionConfig(payloadJson: String): String {
        val rawConnection = extractConnectionValue(payloadJson)
        val parsed = ConnectionInputParser.parse(rawConnection)
        if (parsed.connectionAddress == null) {
            return JSONObject()
                .put("ok", false)
                .put("error", parsed.errorMessage.orEmpty())
                .toString()
        }

        store.save(NodeRuntimeConfig(connectionAddress = parsed.connectionAddress))
        activity.runOnUiThread { onRuntimeRestartRequested(parsed.connectionAddress) }
        return JSONObject()
            .put("ok", true)
            .put("connectionAddress", parsed.connectionAddress)
            .toString()
    }

    @JavascriptInterface
    fun requestScreenCapture(): String {
        activity.runOnUiThread { onScreenCaptureRequested() }
        return JSONObject().put("accepted", true).toString()
    }

    @JavascriptInterface
    fun requestRuntimeRestart(): String {
        activity.runOnUiThread { onRuntimeRestartRequested(null) }
        return JSONObject().put("accepted", true).toString()
    }

    private fun encodeRuntimeSnapshot(snapshot: RuntimeSnapshot): String {
        val config = store.load()
        val status = deriveRuntimeStatus(snapshot, config.connectionAddress)
        return JSONObject()
            .put("status", status)
            .put("serverUrl", config.connectionAddress)
            .put("activeConnectionAddr", snapshot.connectionAddress)
            .put("currentNode", encodeCurrentNode(snapshot))
            .put(
                "diagnostics",
                JSONObject()
                    .put("bridgeClassName", snapshot.bridgeClassName)
                    .put("runtimeState", snapshot.state)
                    .put("lastError", snapshot.lastError)
                    .put("lastAutoRestartAtMs", snapshot.lastAutoRestartAtMs)
                    .put("lastAutoRestartReason", snapshot.lastAutoRestartReason)
                    .put("discoveredPeerCount", snapshot.discoveredPeers.size),
            )
            .toString()
    }

    private fun encodeCurrentNode(snapshot: RuntimeSnapshot): JSONObject {
        return JSONObject().apply {
            if (snapshot.peerId.isNotBlank()) {
                put("peerId", snapshot.peerId)
            }
        }
    }

    private fun encodeDiscoveredPeers(peers: List<DiscoveredPeer>): String {
        val array = JSONArray()
        peers.forEach { peer ->
            array.put(encodeDiscoveredPeer(peer))
        }
        return array.toString()
    }

    private fun encodeDiscoveredPeer(peer: DiscoveredPeer): JSONObject {
        return JSONObject()
            .put("peerId", peer.peerId)
            .put("name", peer.name ?: JSONObject.NULL)
            .put("state", peer.state)
            .put("discoveredInCurrentSession", peer.discoveredInCurrentSession)
            .put("multiaddrs", JSONArray())
            .put("lastDiscoveredAt", peer.lastSeenAt)
            .put("lastSeenAt", peer.lastSeenAt)
    }

    private fun deriveRuntimeStatus(snapshot: RuntimeSnapshot, configuredConnectionAddress: String): String {
        if (configuredConnectionAddress.isBlank()) {
            return "needs-server-url"
        }
        if (snapshot.state.equals("Error", ignoreCase = true) || snapshot.state.equals("Degraded", ignoreCase = true)) {
            return "error"
        }
        return if (snapshot.discoveredPeers.isNotEmpty()) {
            "peer_candidates_ready"
        } else {
            "discovering"
        }
    }

    private fun extractConnectionValue(payloadJson: String): String {
        val trimmed = payloadJson.trim()
        if (!trimmed.startsWith("{")) {
            return trimmed
        }

        val payload = runCatching { JSONObject(trimmed) }.getOrNull() ?: return trimmed
        return payload.optString("connectionAddress")
            .ifBlank { payload.optString("connection") }
            .ifBlank { payload.optString("value") }
            .ifBlank { trimmed }
    }
}
