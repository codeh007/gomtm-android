package com.gomtm.swarm.shell
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ConnectionParseResult(
    val connectionAddress: String? = null,
    val errorMessage: String? = null,
)

object ConnectionInputParser {
    fun parse(raw: String): ConnectionParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ConnectionParseResult(errorMessage = "地址不能为空")
        }
        if (trimmed.startsWith("/")) {
            return ConnectionParseResult(connectionAddress = trimmed)
        }

        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri?.scheme != DEEP_LINK_SCHEME || uri.host != DEEP_LINK_HOST) {
            return ConnectionParseResult(errorMessage = "格式无效")
        }

        val connectionAddress = extractConnectionFromQuery(uri.rawQuery)
            ?: return ConnectionParseResult(errorMessage = "格式无效")
        if (connectionAddress.isEmpty()) {
            return ConnectionParseResult(errorMessage = "地址不能为空")
        }
        if (!connectionAddress.startsWith("/")) {
            return ConnectionParseResult(errorMessage = "格式无效")
        }

        return ConnectionParseResult(connectionAddress = connectionAddress)
    }

    private const val EXTRA_CONNECTION = "connection"
    private const val LEGACY_BOOTSTRAP_QUERY_KEY = "bootstrap"
    private const val DEEP_LINK_SCHEME = "gomtm"
    private const val DEEP_LINK_HOST = "bootstrap"

    private fun extractConnectionFromQuery(rawQuery: String?): String? {
        if (rawQuery == null) {
            return ""
        }

        var legacyDeepLinkAddress: String? = null
        for (segment in rawQuery.split('&')) {
            val rawKey = segment.substringBefore('=', missingDelimiterValue = segment)
            val rawValue = segment.substringAfter('=', missingDelimiterValue = "")
            val key = decodeQueryComponent(rawKey) ?: return null
            val value = decodeQueryComponent(rawValue)?.trim() ?: return null
            if (key == EXTRA_CONNECTION) {
                return value
            }
            if (key != LEGACY_BOOTSTRAP_QUERY_KEY) {
                continue
            }
            legacyDeepLinkAddress = value
        }

        return legacyDeepLinkAddress ?: ""
    }

    private fun decodeQueryComponent(value: String): String? {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrNull()
    }
}
