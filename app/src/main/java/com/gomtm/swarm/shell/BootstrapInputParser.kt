package com.gomtm.swarm.shell

import android.content.Intent
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
        if (!trimmed.startsWith(DEEP_LINK_PREFIX)) {
            return BootstrapParseResult(errorMessage = "格式无效")
        }

        val encodedBootstrap = trimmed.substringAfter("bootstrap=", missingDelimiterValue = "").trim()
        if (encodedBootstrap.isEmpty()) {
            return BootstrapParseResult(errorMessage = "地址不能为空")
        }

        val bootstrap = runCatching {
            URLDecoder.decode(encodedBootstrap, StandardCharsets.UTF_8)
        }.getOrNull()?.trim().orEmpty()

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
    private const val DEEP_LINK_PREFIX = "gomtm://bootstrap?"
}
