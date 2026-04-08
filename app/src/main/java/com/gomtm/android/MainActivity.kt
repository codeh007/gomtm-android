package com.gomtm.swarm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
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
import com.gomtm.swarm.web.EMBEDDED_CONSOLE_ORIGIN
import com.gomtm.swarm.web.HOST_BRIDGE_NAME
import com.gomtm.swarm.web.HostSettingsStore
import com.gomtm.swarm.web.SharedPreferencesHostSettingsStore
import com.gomtm.swarm.web.SwarmRuntimeBridgeHost
import com.gomtm.swarm.web.defaultAccessibilitySettingsLauncher
import com.gomtm.swarm.web.isEmbeddedConsoleUrl
import com.gomtm.swarm.web.resolveHostNavigationUrl
import com.gomtm.swarm.web.resolveEmbeddedConsoleProxyTarget
import com.gomtm.swarm.web.rewriteEmbeddedConsoleLocation
import com.gomtm.swarm.web.toSwarmNodeConfig
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
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
                val targetRequest = request ?: return null
                return interceptHostRequest(targetRequest) ?: super.shouldInterceptRequest(view, targetRequest)
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

    private fun interceptHostRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        return assetLoader.shouldInterceptRequest(url)
            ?: if (isEmbeddedConsoleUrl(url.toString())) proxyConsoleRequest(request) else null
    }

    // Serve the remote console from a local HTTPS origin so WebView treats /dash/p2p as a secure context.
    private fun proxyConsoleRequest(request: WebResourceRequest): WebResourceResponse {
        if (request.method !in setOf("GET", "HEAD")) {
            return plainTextResponse(
                statusCode = 405,
                reason = "Method Not Allowed",
                body = "Embedded console proxy only supports GET/HEAD requests.",
            )
        }

        val currentConsoleUrl = settingsStore.load().consoleUrl
        val upstreamUrl = resolveEmbeddedConsoleProxyTarget(currentConsoleUrl, request.url.toString())
            ?: return plainTextResponse(
                statusCode = 503,
                reason = "Service Unavailable",
                body = "Embedded console URL is not configured.",
            )

        return runCatching {
            val connection = (URL(upstreamUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = request.method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept-Encoding", "identity")
                request.requestHeaders.forEach { (name, value) ->
                    if (
                        name.equals("Accept-Encoding", ignoreCase = true) ||
                        name.equals("Connection", ignoreCase = true) ||
                        name.equals("Host", ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    setRequestProperty(name, value)
                }
            }

            val statusCode = connection.responseCode
            val headers = buildConsoleProxyHeaders(connection, currentConsoleUrl)
            val contentType = connection.contentType.orEmpty()
            val mimeType = contentType.substringBefore(';').ifBlank { "text/plain" }
            val encoding = contentType.substringAfter("charset=", "").ifBlank { null }
            val stream = when {
                request.method == "HEAD" -> ByteArrayInputStream(ByteArray(0))
                statusCode >= 400 -> connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
                else -> connection.inputStream ?: ByteArrayInputStream(ByteArray(0))
            }

            WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                connection.responseMessage ?: "OK",
                headers,
                stream,
            )
        }.getOrElse { error ->
            plainTextResponse(
                statusCode = 502,
                reason = "Bad Gateway",
                body = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun buildConsoleProxyHeaders(connection: HttpURLConnection, currentConsoleUrl: String): Map<String, String> {
        val responseHeaders = linkedMapOf<String, String>()
        val cookieManager = CookieManager.getInstance()
        connection.headerFields.forEach { (name, values) ->
            if (name == null || values.isNullOrEmpty()) {
                return@forEach
            }
            when {
                name.equals("Set-Cookie", ignoreCase = true) || name.equals("Set-Cookie2", ignoreCase = true) -> {
                    values.forEach { cookie ->
                        cookieManager.setCookie(EMBEDDED_CONSOLE_ORIGIN, rewriteEmbeddedConsoleCookie(cookie))
                    }
                }

                name.equals("Content-Length", ignoreCase = true) ||
                    name.equals("Content-Encoding", ignoreCase = true) ||
                    name.equals("Connection", ignoreCase = true) ||
                    name.equals("Transfer-Encoding", ignoreCase = true) -> {
                    return@forEach
                }

                name.equals("Location", ignoreCase = true) -> {
                    responseHeaders[name] = rewriteEmbeddedConsoleLocation(currentConsoleUrl, values.last()) ?: values.last()
                }

                else -> {
                    responseHeaders[name] = values.joinToString(", ")
                }
            }
        }
        cookieManager.flush()
        return responseHeaders
    }

    private fun rewriteEmbeddedConsoleCookie(rawCookie: String): String {
        return rawCookie
            .split(';')
            .mapNotNull { segment ->
                val trimmed = segment.trim()
                if (trimmed.startsWith("Domain=", ignoreCase = true)) {
                    null
                } else {
                    trimmed
                }
            }
            .joinToString("; ")
    }

    private fun plainTextResponse(statusCode: Int, reason: String, body: String): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            statusCode,
            reason,
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(body.toByteArray()),
        )
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
        val requestedConsoleUrl = intent.getStringExtra(EXTRA_CONSOLE_URL)?.trim().orEmpty()
        val next = current.copy(
            bootstrapAddress = intent.getStringExtra(EXTRA_BOOTSTRAP)?.trim().orEmpty().ifBlank { current.bootstrapAddress },
            consoleUrl = when {
                requestedConsoleUrl.isBlank() -> current.consoleUrl
                else -> resolveHostNavigationUrl(requestedConsoleUrl) ?: current.consoleUrl
            },
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
        private const val EXTRA_CONSOLE_URL = "console_url"
        private const val REFRESH_INTERVAL_MS = 750L
    }
}
