package com.gomtm.swarm.platform.lifecycle

import io.nekohasekai.gomtmruntime.Gomtmruntime
import java.net.HttpURLConnection
import java.net.URL

internal object GomtmRuntimeBridge {
    private const val RUNTIME_ADDR = "127.0.0.1:8383"
    private const val RUNTIME_STATUS_URL = "http://127.0.0.1:8383/api/runtime/python/status"

    fun startRuntime(baseDir: String, runtimeCredential: String) {
        Gomtmruntime.startRuntimeWithOptions(baseDir, runtimeCredential, RUNTIME_ADDR)
    }

    fun stopRuntime() {
        Gomtmruntime.stopRuntime()
    }

    fun isRuntimeHttpReady(): Boolean {
        val connection = URL(RUNTIME_STATUS_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 1000
        connection.readTimeout = 1000
        connection.requestMethod = "GET"
        return runCatching {
            connection.responseCode in 200..299
        }.getOrDefault(false)
    }
}
