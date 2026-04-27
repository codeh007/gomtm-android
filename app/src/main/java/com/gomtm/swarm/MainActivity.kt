package com.gomtm.swarm

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gomtm.swarm.platform.lifecycle.GomtmHostActions
import com.gomtm.swarm.platform.lifecycle.ScreenCaptureService
import com.gomtm.swarm.web.GomtmWebViewBridge

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var webErrorMessage: TextView

    private var hasPageLoadError = false

    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.startProjection(this, result.resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.p2pWebView)
        webErrorMessage = findViewById(R.id.webErrorMessage)

        configureWebView()
        handleHostAction(intent)
        loadDevicesEntry()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleHostAction(intent)
        loadDevicesEntry()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.destroy()
        super.onDestroy()
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: return
        screenCapturePermissionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startDeviceService() {
        GomtmHostActions.startDeviceService(this)
    }

    private fun stopDeviceService() {
        GomtmHostActions.stopDeviceService(this)
    }

    private fun handleHostAction(intent: Intent?) {
        if (intent?.action == ACTION_REQUEST_SCREEN_CAPTURE) {
            webView.post { requestScreenCapturePermission() }
            return
        }
        if (intent?.action == ACTION_START_DEVICE_SERVICE) {
            webView.post { startDeviceService() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.addJavascriptInterface(
            GomtmWebViewBridge(
                activity = this,
                onScreenCaptureRequested = ::requestScreenCapturePermission,
                onDeviceServiceStartRequested = ::startDeviceService,
                onDeviceServiceStopRequested = ::stopDeviceService,
            ),
            BRIDGE_NAME,
        )
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showWebError(error?.description?.toString())
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    showWebError("HTTP ${errorResponse?.statusCode}")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!hasPageLoadError) {
                    hideWebError()
                }
            }
        }
    }

    private fun loadDevicesEntry() {
        hasPageLoadError = false
        webErrorMessage.text = getString(R.string.web_loading_message)
        hideWebError()
        webView.loadUrl(BuildConfig.GOMTM_UI_DEVICES_URL)
    }

    private fun showWebError(reason: String?) {
        hasPageLoadError = true
        webErrorMessage.text = if (reason.isNullOrBlank()) {
            getString(R.string.web_error_message)
        } else {
            getString(R.string.web_error_reason_format, reason)
        }
        webErrorMessage.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    private fun hideWebError() {
        webErrorMessage.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    companion object {
        private const val BRIDGE_NAME = "GomtmHostBridge"
        const val ACTION_REQUEST_SCREEN_CAPTURE = "com.gomtm.swarm.action.REQUEST_SCREEN_CAPTURE"
        const val ACTION_START_DEVICE_SERVICE = "com.gomtm.swarm.action.START_DEVICE_SERVICE"
    }
}
