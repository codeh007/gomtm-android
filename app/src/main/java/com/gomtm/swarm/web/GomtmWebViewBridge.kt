package com.gomtm.swarm.web

import android.app.Activity
import android.webkit.JavascriptInterface
import com.gomtm.swarm.BuildConfig
import com.gomtm.swarm.platform.lifecycle.HostActivationStore
import org.json.JSONObject

class GomtmWebViewBridge(
    private val activity: Activity,
    private val onScreenCaptureRequested: () -> Unit,
    private val onDeviceServiceStartRequested: () -> Unit,
    private val onDeviceServiceStopRequested: () -> Unit,
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
        val serviceActivationRequested = HostActivationStore.isDeviceServiceActivationRequested(activity)
        return JSONObject()
            .put("available", true)
            .put("activationStatus", if (serviceActivationRequested) "activating" else "inactive")
            .put("hostActionState", if (serviceActivationRequested) "device_service_activation_requested" else "idle")
            .put("serviceActivationRequested", serviceActivationRequested)
            .put("canRequestScreenCapture", true)
            .put("canStartDeviceService", !serviceActivationRequested)
            .put("canStopDeviceService", serviceActivationRequested)
            .toString()
    }

    @JavascriptInterface
    fun startDeviceService(): String {
        activity.runOnUiThread { onDeviceServiceStartRequested() }
        return JSONObject().put("accepted", true).toString()
    }

    @JavascriptInterface
    fun stopDeviceService(): String {
        activity.runOnUiThread { onDeviceServiceStopRequested() }
        return JSONObject().put("accepted", true).toString()
    }

    @JavascriptInterface
    fun requestScreenCapture(): String {
        activity.runOnUiThread { onScreenCaptureRequested() }
        return JSONObject().put("accepted", true).toString()
    }
}
