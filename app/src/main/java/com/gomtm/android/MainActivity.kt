package com.gomtm.swarm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.gomtm.swarm.swarm.ScreenCaptureService
import com.gomtm.swarm.swarm.SwarmRuntime
import com.gomtm.swarm.web.GomtmHostBridge
import com.gomtm.swarm.web.HOST_BRIDGE_NAME
import com.gomtm.swarm.web.HostSettingsStore
import com.gomtm.swarm.web.SharedPreferencesHostSettingsStore
import com.gomtm.swarm.web.SwarmRuntimeBridgeHost
import com.gomtm.swarm.web.defaultAccessibilitySettingsLauncher
import com.gomtm.swarm.web.toSwarmNodeConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private val swarmRuntime = SwarmRuntime()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val backgroundWorker = Executors.newSingleThreadExecutor()
    private val remoteControlTickRunning = AtomicBoolean(false)
    private val activeRuntimeStates = setOf("Starting", "Connecting", "Connected", "Registered")
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (remoteControlTickRunning.compareAndSet(false, true)) {
                backgroundWorker.execute {
                    try {
                        swarmRuntime.processRemoteControlTick(this@MainActivity, timeoutMs = 0)
                    } catch (_: Throwable) {
                    } finally {
                        remoteControlTickRunning.set(false)
                    }
                }
            }
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private lateinit var webView: WebView
    private lateinit var settingsStore: HostSettingsStore
    private lateinit var hostBridge: GomtmHostBridge
    private lateinit var assetLoader: WebViewAssetLoader
    private var bridgeAttached = false
    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            ScreenCaptureService.startProjection(this, result.resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.hostWebView)
        settingsStore = SharedPreferencesHostSettingsStore(this)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        hostBridge = GomtmHostBridge(
            runtimeHost = SwarmRuntimeBridgeHost(this, swarmRuntime),
            settingsStore = settingsStore,
            launchAccessibilitySettings = defaultAccessibilitySettingsLauncher(this),
            requestScreenCapturePermissionAction = { requestScreenCapturePermission() },
            navigateToUrl = { url -> runOnUiThread { loadUrlInHost(url) } },
        )

        configureWebView()
        onBackPressedDispatcher.addCallback(this) {
            if (!goBackInHost()) {
                finish()
            }
        }

        applyIntentOverrides(intent)
        loadUrlInHost(GomtmHostBridge.LOCAL_BOOTSTRAP_URL)
        maybeAutoStartFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentOverrides(intent)
        loadUrlInHost(GomtmHostBridge.LOCAL_BOOTSTRAP_URL)
        maybeAutoStartFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        refreshHandler.post(autoRefreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(autoRefreshRunnable)
        if (this::webView.isInitialized) {
            webView.removeJavascriptInterface(HOST_BRIDGE_NAME)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        backgroundWorker.shutdownNow()
        super.onDestroy()
    }

    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = "$userAgentString GomtmSwarmHost/2.0"
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url ?: return super.shouldInterceptRequest(view, request)
                return assetLoader.shouldInterceptRequest(url) ?: super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                syncBridgeForUrl(url)
                super.onPageStarted(view, url, favicon)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetRequest = request ?: return false
                val targetUrl = targetRequest.url.toString()
                if (!targetRequest.isForMainFrame) {
                    return false
                }
                return when {
                    isBootstrapPage(targetUrl) || isRemoteNavigationUrl(targetUrl) -> {
                        syncBridgeForUrl(targetUrl)
                        false
                    }
                    isExternalAppNavigation(targetRequest.url) -> {
                        openExternalNavigation(targetRequest.url)
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun loadUrlInHost(url: String) {
        syncBridgeForUrl(url)
        webView.loadUrl(url)
    }

    private fun syncBridgeForUrl(url: String?) {
        val shouldAttach = isBootstrapPage(url)
        if (shouldAttach && !bridgeAttached) {
            webView.addJavascriptInterface(hostBridge, HOST_BRIDGE_NAME)
            bridgeAttached = true
        } else if (!shouldAttach && bridgeAttached) {
            webView.removeJavascriptInterface(HOST_BRIDGE_NAME)
            bridgeAttached = false
        }
    }

    private fun goBackInHost(): Boolean {
        if (!webView.canGoBack()) {
            return false
        }

        val history = webView.copyBackForwardList()
        val previousIndex = history.currentIndex - 1
        val previousUrl = if (previousIndex >= 0) history.getItemAtIndex(previousIndex).url else null
        if (isBootstrapPage(previousUrl)) {
            syncBridgeForUrl(previousUrl)
            webView.goBack()
            return true
        }

        webView.goBack()
        return true
    }

    private fun applyIntentOverrides(intent: Intent?) {
        if (intent == null) {
            return
        }

        val current = settingsStore.load()
        val next = current.copy(
            bootstrapAddress = intent.getStringExtra(EXTRA_BOOTSTRAP)?.trim().orEmpty().ifBlank { current.bootstrapAddress },
            nodeName = intent.getStringExtra(EXTRA_NODE_NAME)?.trim().orEmpty().ifBlank { current.nodeName },
            consoleUrl = intent.getStringExtra(EXTRA_CONSOLE_URL)?.trim().orEmpty().ifBlank { current.consoleUrl },
        )
        if (next != current) {
            settingsStore.save(next)
        }
    }

    private fun maybeAutoStartFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_START, false) != true) {
            return
        }

        val currentState = swarmRuntime.probe().state
        if (currentState in activeRuntimeStates) {
            return
        }

        runCatching {
            swarmRuntime.start(this, settingsStore.load().toSwarmNodeConfig())
        }
    }

    private fun isBootstrapPage(url: String?): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.authority == "appassets.androidplatform.net" &&
            uri.path == "/assets/bootstrap/index.html"
    }

    private fun isRemoteNavigationUrl(url: String?): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    private fun isExternalAppNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        return scheme !in setOf("http", "https", "javascript", "file", "data", "about", "blob", "content")
    }

    private fun openExternalNavigation(uri: Uri) {
        if (uri.scheme == "intent") {
            val parsedIntent = runCatching {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            }.getOrNull() ?: return

            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
            if (!fallbackUrl.isNullOrBlank() && isRemoteNavigationUrl(fallbackUrl)) {
                loadUrlInHost(fallbackUrl)
                return
            }

            parsedIntent.component = null
            parsedIntent.selector = null
            parsedIntent.addCategory(Intent.CATEGORY_BROWSABLE)
            openBrowsableIntent(parsedIntent)
            return
        }

        openBrowsableIntent(
            Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
        )
    }

    private fun openBrowsableIntent(intent: Intent) {
        runCatching {
            startActivity(intent)
        }.getOrElse { error ->
            if (error !is ActivityNotFoundException && error !is SecurityException) {
                throw error
            }
        }
    }

    private fun requestScreenCapturePermission(): Boolean {
        val projectionManager = getSystemService(MediaProjectionManager::class.java) ?: return false
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        return true
    }

    companion object {
        private const val EXTRA_AUTO_START = "auto_start"
        private const val EXTRA_BOOTSTRAP = "bootstrap"
        private const val EXTRA_NODE_NAME = "node_name"
        private const val EXTRA_CONSOLE_URL = "console_url"
        private const val REFRESH_INTERVAL_MS = 750L
    }
}
