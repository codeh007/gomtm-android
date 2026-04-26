package com.gomtm.swarm.web

import android.app.Activity
import android.webkit.JavascriptInterface
import com.gomtm.swarm.BuildConfig
import org.json.JSONObject

class GomtmWebViewBridge(
    private val activity: Activity,
    private val onScreenCaptureRequested: () -> Unit,
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
    fun getActivationSurface(): String {
        return JSONObject()
            .put("available", true)
            .put("activationStatus", "inactive")
            .put("runtimeStatus", "unknown")
            .put("canRequestScreenCapture", true)
            .toString()
    }

    @JavascriptInterface
    fun requestScreenCapture(): String {
        activity.runOnUiThread { onScreenCaptureRequested() }
        return JSONObject().put("accepted", true).toString()
    }
}
