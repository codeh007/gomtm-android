package com.gomtm.swarm.web

import android.app.Activity
import android.webkit.JavascriptInterface
import com.gomtm.swarm.BuildConfig
import com.gomtm.swarm.platform.lifecycle.AndroidHostInstallStore
import com.gomtm.swarm.platform.lifecycle.AndroidHostRuntimeSurface
import com.gomtm.swarm.platform.lifecycle.AndroidHostStartupPayload
import org.json.JSONObject

class GomtmWebViewBridge(
    private val activity: Activity,
    private val runtimeSurfaceProvider: () -> AndroidHostRuntimeSurface,
    private val onEnsureRuntimeStarted: (AndroidHostStartupPayload) -> Unit,
    private val onScreenCaptureRequested: () -> Unit,
) {
    @JavascriptInterface
    fun getHostInfo(): String {
        return JSONObject()
            .put("hostKind", "android-host")
            .put("hostInstanceId", AndroidHostInstallStore.getOrCreateHostInstanceId(activity))
            .put("packageName", activity.packageName)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .toString()
    }

    @JavascriptInterface
    fun getRuntimeSurface(): String {
        val surface = runtimeSurfaceProvider()
        return JSONObject()
            .put("status", surface.status)
            .put("lastError", surface.lastError)
            .toString()
    }

    @JavascriptInterface
    fun ensureRuntimeStarted(payloadJson: String): String {
        val payload = decodePayload(payloadJson) ?: return JSONObject().put("accepted", false).toString()
        activity.runOnUiThread { onEnsureRuntimeStarted(payload) }
        return JSONObject().put("accepted", true).toString()
    }

    @JavascriptInterface
    fun requestScreenCapture(): String {
        activity.runOnUiThread { onScreenCaptureRequested() }
        return JSONObject().put("accepted", true).toString()
    }

    private fun decodePayload(payloadJson: String): AndroidHostStartupPayload? {
        return runCatching {
            val json = JSONObject(payloadJson)
            AndroidHostStartupPayload(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                runtimeCredential = json.getString("runtimeCredential"),
                credentialVersion = json.getInt("credentialVersion"),
            )
        }.getOrNull()
    }
}
