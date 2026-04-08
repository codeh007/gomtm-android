package com.gomtm.swarm.web

import android.content.Context
import com.gomtm.swarm.swarm.SwarmNodeConfig
import java.net.URI

data class HostSettings(
    val bootstrapAddress: String = SwarmNodeConfig.DEFAULT_BOOTSTRAP,
    val nodeName: String = "",
    val consoleUrl: String = "",
)

interface HostSettingsStore {
    fun load(): HostSettings

    fun save(settings: HostSettings)
}

class SharedPreferencesHostSettingsStore(
    context: Context,
) : HostSettingsStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): HostSettings {
        return HostSettings(
            bootstrapAddress = preferences.getString(KEY_BOOTSTRAP, SwarmNodeConfig.DEFAULT_BOOTSTRAP).orEmpty(),
            nodeName = preferences.getString(KEY_NODE_NAME, "").orEmpty(),
            consoleUrl = preferences.getString(KEY_CONSOLE_URL, "").orEmpty(),
        )
    }

    override fun save(settings: HostSettings) {
        preferences
            .edit()
            .putString(KEY_BOOTSTRAP, settings.bootstrapAddress)
            .putString(KEY_NODE_NAME, settings.nodeName)
            .putString(KEY_CONSOLE_URL, settings.consoleUrl)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "gomtm-swarm"
        private const val KEY_BOOTSTRAP = "bootstrap"
        private const val KEY_NODE_NAME = "node_name"
        private const val KEY_CONSOLE_URL = "console_url"
    }
}

fun resolveHostNavigationUrl(raw: String, defaultPath: String = "/dash/p2p"): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val candidate = if (SCHEME_REGEX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
        return null
    }
    if (uri.host.equals(EMBEDDED_CONSOLE_HOST, ignoreCase = true)) {
        return null
    }
    val normalizedPath = uri.rawPath?.takeIf { it.isNotBlank() && it != "/" } ?: defaultPath
    return buildUriString(uri.scheme, uri.rawAuthority, normalizedPath, uri.rawQuery, uri.rawFragment)
}

fun resolveEmbeddedConsoleUrl(raw: String): String? {
    val canonical = resolveHostNavigationUrl(raw) ?: return null
    val uri = runCatching { URI(canonical) }.getOrNull() ?: return null
    return buildUriString(EMBEDDED_CONSOLE_SCHEME, EMBEDDED_CONSOLE_HOST, uri.rawPath, uri.rawQuery, uri.rawFragment)
}

fun resolveHostPeerAgentUrl(raw: String, peerId: String): String? {
    val normalizedPeerId = peerId.trim()
    if (normalizedPeerId.isEmpty()) {
        return null
    }
    val canonical = resolveHostNavigationUrl(raw) ?: return null
    val uri = runCatching { URI(canonical) }.getOrNull() ?: return null
    val basePath = uri.rawPath?.trimEnd('/')?.ifBlank { "/dash/p2p" } ?: "/dash/p2p"
    return buildUriString(
        uri.scheme,
        uri.rawAuthority,
        "$basePath/$normalizedPeerId/android",
        uri.rawQuery,
        uri.rawFragment,
    )
}

fun resolveEmbeddedPeerAgentUrl(raw: String, peerId: String): String? {
    val canonical = resolveHostPeerAgentUrl(raw, peerId) ?: return null
    val uri = runCatching { URI(canonical) }.getOrNull() ?: return null
    return buildUriString(EMBEDDED_CONSOLE_SCHEME, EMBEDDED_CONSOLE_HOST, uri.rawPath, uri.rawQuery, uri.rawFragment)
}

fun isEmbeddedConsoleUrl(raw: String?): Boolean {
    val uri = runCatching { URI(raw) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() == EMBEDDED_CONSOLE_SCHEME &&
        uri.host.equals(EMBEDDED_CONSOLE_HOST, ignoreCase = true)
}

fun resolveEmbeddedConsoleProxyTarget(currentConsoleUrl: String, embeddedRequestUrl: String): String? {
    if (!isEmbeddedConsoleUrl(embeddedRequestUrl)) {
        return null
    }
    val canonical = resolveHostNavigationUrl(currentConsoleUrl) ?: return null
    val upstream = runCatching { URI(canonical) }.getOrNull() ?: return null
    val embedded = runCatching { URI(embeddedRequestUrl) }.getOrNull() ?: return null
    return buildUriString(upstream.scheme, upstream.rawAuthority, embedded.rawPath, embedded.rawQuery, embedded.rawFragment)
}

fun rewriteEmbeddedConsoleLocation(currentConsoleUrl: String, rawLocation: String): String? {
    val canonical = resolveHostNavigationUrl(currentConsoleUrl) ?: return null
    val upstream = runCatching { URI(canonical) }.getOrNull() ?: return null
    val resolved = runCatching { upstream.resolve(rawLocation) }.getOrNull() ?: return null
    val scheme = resolved.scheme?.lowercase()
    if (scheme !in setOf("http", "https") || resolved.host.isNullOrBlank()) {
        return null
    }
    val sameUpstreamOrigin = scheme == upstream.scheme?.lowercase() && resolved.rawAuthority == upstream.rawAuthority
    return if (sameUpstreamOrigin) {
        resolveEmbeddedConsoleUrl(resolved.toString())
    } else {
        resolved.toString()
    }
}

fun HostSettings.toSwarmNodeConfig(): SwarmNodeConfig {
    return SwarmNodeConfig(
        bootstrapAddress = bootstrapAddress.ifBlank { SwarmNodeConfig.DEFAULT_BOOTSTRAP },
        autoReconnect = true,
    )
}

private fun buildUriString(
    scheme: String?,
    rawAuthority: String?,
    rawPath: String?,
    rawQuery: String?,
    rawFragment: String?,
): String? {
    if (scheme.isNullOrBlank() || rawAuthority.isNullOrBlank()) {
        return null
    }

    val normalizedPath = when {
        rawPath.isNullOrBlank() -> "/"
        rawPath.startsWith('/') -> rawPath
        else -> "/$rawPath"
    }
    return buildString {
        append(scheme)
        append("://")
        append(rawAuthority)
        append(normalizedPath)
        if (!rawQuery.isNullOrBlank()) {
            append('?')
            append(rawQuery)
        }
        if (!rawFragment.isNullOrBlank()) {
            append('#')
            append(rawFragment)
        }
    }
}

private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

const val EMBEDDED_CONSOLE_SCHEME = "https"
const val EMBEDDED_CONSOLE_HOST = "gomtm.console.invalid"
const val EMBEDDED_CONSOLE_ORIGIN = "$EMBEDDED_CONSOLE_SCHEME://$EMBEDDED_CONSOLE_HOST"
