package com.gomtm.swarm

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gomtm.swarm.platform.lifecycle.GomtmForegroundService
import com.gomtm.swarm.platform.lifecycle.ScreenCaptureService
import com.gomtm.swarm.runtime.GomtmRuntimeFacade
import com.gomtm.swarm.shell.ConnectionInputParser
import com.gomtm.swarm.shell.NodeRuntimeConfig
import com.gomtm.swarm.shell.NodeRuntimeStore
import com.gomtm.swarm.web.GomtmWebViewBridge

class MainActivity : AppCompatActivity() {
    private val swarmRuntime = GomtmRuntimeFacade()
    private val runtimeStore by lazy { NodeRuntimeStore(this) }
    private val dashP2PEntryUri by lazy { Uri.parse(BuildConfig.GOMTM_UI_DASH_P2P_URL) }

    private lateinit var webView: WebView
    private lateinit var webErrorMessage: TextView

    private var hasPageLoadError = false
    private var latestConnectionAddress = ""

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
        val forceRestart = applyIntentOverrides(intent)
        requestRuntimeStartIfNeeded(forceRestart = forceRestart)
        handleHostAction(intent)
        loadDashP2PEntry()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val forceRestart = applyIntentOverrides(intent)
        requestRuntimeStartIfNeeded(forceRestart = forceRestart)
        handleHostAction(intent)
        loadDashP2PEntry()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.destroy()
        super.onDestroy()
    }

    private fun applyIntentOverrides(intent: Intent?): Boolean {
        val persisted = runtimeStore.load()
        if (latestConnectionAddress.isBlank() && persisted.connectionAddress.isNotBlank()) {
            latestConnectionAddress = persisted.connectionAddress
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            val deepLink = intent.dataString.orEmpty()
            if (deepLink.isBlank()) {
                return false
            }

            val parsed = ConnectionInputParser.parse(deepLink)
            if (parsed.connectionAddress != null) {
                latestConnectionAddress = parsed.connectionAddress
                runtimeStore.save(NodeRuntimeConfig(connectionAddress = latestConnectionAddress))
                return true
            }

            Toast.makeText(
                this,
                parsed.errorMessage ?: getString(R.string.connection_invalid_message),
                Toast.LENGTH_SHORT,
            ).show()
        }

        return false
    }

    private fun requestRuntimeStartIfNeeded(forceRestart: Boolean = false) {
        val connectionAddress = latestConnectionAddress.ifBlank { runtimeStore.load().connectionAddress }
        if (connectionAddress.isBlank()) {
            return
        }
        latestConnectionAddress = connectionAddress

        val snapshot = swarmRuntime.probe()
        val connectionChanged = connectionAddress != snapshot.connectionAddress
        if (isRuntimeStartingState(snapshot.state)) {
            return
        }
        if (!forceRestart && isRuntimeActiveState(snapshot.state) && !connectionChanged) {
            return
        }

        requestRuntimeRestart(connectionAddress = connectionAddress)
    }

    private fun requestRuntimeRestart(connectionAddress: String? = null) {
        val restartAddress = connectionAddress
            ?.trim()
            .orEmpty()
            .ifBlank { latestConnectionAddress.ifBlank { runtimeStore.load().connectionAddress } }
        if (restartAddress.isBlank()) {
            Toast.makeText(this, R.string.connection_required_message, Toast.LENGTH_SHORT).show()
            return
        }

        latestConnectionAddress = restartAddress
        runtimeStore.save(NodeRuntimeConfig(connectionAddress = latestConnectionAddress))
        GomtmForegroundService.start(
            context = this,
            connectionAddress = latestConnectionAddress,
            forceRestart = true,
        )
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: return
        screenCapturePermissionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun handleHostAction(intent: Intent?) {
        if (intent?.action == ACTION_REQUEST_SCREEN_CAPTURE) {
            webView.post { requestScreenCapturePermission() }
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
                runtime = swarmRuntime,
                store = runtimeStore,
                onScreenCaptureRequested = ::requestScreenCapturePermission,
                onRuntimeRestartRequested = ::requestRuntimeRestart,
            ),
            BRIDGE_NAME,
        )
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.isForMainFrame != true) {
                    return false
                }
                if (isAllowedDashP2PUrl(request.url)) {
                    return false
                }
                if (!openInExternalBrowser(request.url)) {
                    showWebError(getString(R.string.web_navigation_blocked_message))
                }
                return true
            }

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

    private fun loadDashP2PEntry() {
        hasPageLoadError = false
        webErrorMessage.text = getString(R.string.web_loading_message)
        hideWebError()
        webView.loadUrl(BuildConfig.GOMTM_UI_DASH_P2P_URL)
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

    private fun isAllowedDashP2PUrl(uri: Uri?): Boolean {
        if (uri == null || !uri.isHierarchical) {
            return false
        }

        if (!uri.scheme.equals(dashP2PEntryUri.scheme, ignoreCase = true)) {
            return false
        }
        if (!uri.authority.equals(dashP2PEntryUri.authority, ignoreCase = true)) {
            return false
        }

        val allowedPath = dashP2PEntryUri.path.orEmpty().trimEnd('/')
        val candidatePath = uri.path.orEmpty().trimEnd('/')
        return candidatePath == allowedPath || candidatePath.startsWith("$allowedPath/")
    }

    private fun openInExternalBrowser(uri: Uri): Boolean {
        val scheme = uri.scheme.orEmpty()
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (intent.resolveActivity(packageManager) == null) {
            return false
        }
        startActivity(intent)
        return true
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

    companion object {
        private const val BRIDGE_NAME = "GomtmHostBridge"
        const val ACTION_REQUEST_SCREEN_CAPTURE = "com.gomtm.swarm.action.REQUEST_SCREEN_CAPTURE"
    }
}
