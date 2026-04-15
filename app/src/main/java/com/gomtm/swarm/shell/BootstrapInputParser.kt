package com.gomtm.swarm.shell

import android.content.Intent
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class BootstrapParseResult(
    val bootstrapAddress: String? = null,
    val errorMessage: String? = null,
)

object BootstrapInputParser {
    fun parse(raw: String): BootstrapParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return BootstrapParseResult(errorMessage = "地址不能为空")
        }
        if (trimmed.startsWith("/")) {
            return BootstrapParseResult(bootstrapAddress = trimmed)
        }

        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri?.scheme != DEEP_LINK_SCHEME || uri.host != DEEP_LINK_HOST) {
            return BootstrapParseResult(errorMessage = "格式无效")
        }

        val bootstrap = extractBootstrapFromQuery(uri.rawQuery)
            ?: return BootstrapParseResult(errorMessage = "格式无效")
        if (bootstrap.isEmpty()) {
            return BootstrapParseResult(errorMessage = "地址不能为空")
        }
        if (!bootstrap.startsWith("/")) {
            return BootstrapParseResult(errorMessage = "格式无效")
        }

        return BootstrapParseResult(bootstrapAddress = bootstrap)
    }

    fun parseIntent(intent: Intent?): BootstrapParseResult {
        val extra = intent?.getStringExtra(EXTRA_BOOTSTRAP).orEmpty()
        if (extra.isNotBlank()) {
            return parse(extra)
        }

        val deepLink = intent?.dataString.orEmpty()
        if (deepLink.isNotBlank()) {
            return parse(deepLink)
        }

        return BootstrapParseResult()
    }

    private const val EXTRA_BOOTSTRAP = "bootstrap"
    private const val DEEP_LINK_SCHEME = "gomtm"
    private const val DEEP_LINK_HOST = "bootstrap"

    private fun extractBootstrapFromQuery(rawQuery: String?): String? {
        if (rawQuery == null) {
            return ""
        }

        for (segment in rawQuery.split('&')) {
            val rawKey = segment.substringBefore('=', missingDelimiterValue = segment)
            val rawValue = segment.substringAfter('=', missingDelimiterValue = "")
            val key = decodeQueryComponent(rawKey) ?: return null
            if (key != EXTRA_BOOTSTRAP) {
                continue
            }
            return decodeQueryComponent(rawValue)?.trim()
        }

        return ""
    }

    private fun decodeQueryComponent(value: String): String? {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
