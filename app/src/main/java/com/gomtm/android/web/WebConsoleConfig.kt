package com.gomtm.android.web

import android.content.Context
import com.gomtm.android.swarm.SwarmNodeConfig
import java.net.URI

data class WebConsoleSettings(
    val bootstrapAddress: String = SwarmNodeConfig.DEFAULT_BOOTSTRAP,
    val nodeName: String = SwarmNodeConfig.defaultNodeName(),
    val consoleUrl: String = "",
)

interface WebConsoleSettingsStore {
    fun load(): WebConsoleSettings

    fun save(settings: WebConsoleSettings)
}

class SharedPreferencesWebConsoleSettingsStore(
    context: Context,
) : WebConsoleSettingsStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): WebConsoleSettings {
        return WebConsoleSettings(
            bootstrapAddress = preferences.getString(KEY_BOOTSTRAP, SwarmNodeConfig.DEFAULT_BOOTSTRAP).orEmpty(),
            nodeName = preferences.getString(KEY_NODE_NAME, SwarmNodeConfig.defaultNodeName()).orEmpty(),
            consoleUrl = preferences.getString(KEY_CONSOLE_URL, "").orEmpty(),
        )
    }

    override fun save(settings: WebConsoleSettings) {
        preferences
            .edit()
            .putString(KEY_BOOTSTRAP, settings.bootstrapAddress)
            .putString(KEY_NODE_NAME, settings.nodeName)
            .putString(KEY_CONSOLE_URL, settings.consoleUrl)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "gomtm-android"
        private const val KEY_BOOTSTRAP = "bootstrap"
        private const val KEY_NODE_NAME = "node_name"
        private const val KEY_CONSOLE_URL = "console_url"
    }
}

fun resolveWebConsoleLaunchUrl(raw: String, defaultPath: String = "/dash/p2p"): String? {
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
    val normalizedPath = uri.rawPath?.takeIf { it.isNotBlank() && it != "/" } ?: defaultPath
    return URI(uri.scheme, uri.rawAuthority, normalizedPath, uri.rawQuery, uri.rawFragment).toString()
}

fun WebConsoleSettings.toSwarmNodeConfig(): SwarmNodeConfig {
    return SwarmNodeConfig(
        bootstrapAddress = bootstrapAddress.ifBlank { SwarmNodeConfig.DEFAULT_BOOTSTRAP },
        nodeName = nodeName.ifBlank { SwarmNodeConfig.defaultNodeName() },
        autoReconnect = true,
    )
}

private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
