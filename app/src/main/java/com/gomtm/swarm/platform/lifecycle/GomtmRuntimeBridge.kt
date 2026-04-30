package com.gomtm.swarm.platform.lifecycle

import io.nekohasekai.p2pandroid.P2pandroid

internal object GomtmRuntimeBridge {
    private const val RUNTIME_ADDR = "127.0.0.1:8383"

    fun startRuntime(baseDir: String, connectionAddr: String) {
        val runtimeMethod = runCatching {
            P2pandroid::class.java.getMethod(
                "startRuntimeWithOptions",
                String::class.java,
                String::class.java,
                String::class.java,
            )
        }.getOrNull()

        if (runtimeMethod != null) {
            runtimeMethod.invoke(null, baseDir, connectionAddr, RUNTIME_ADDR)
            return
        }

        // Keep the host shell runtime-oriented while this repo's pinned AAR catches up.
        P2pandroid.startNodeWithOptions(baseDir, connectionAddr)
    }

    fun stopRuntime() {
        val runtimeMethod = runCatching {
            P2pandroid::class.java.getMethod("stopRuntime")
        }.getOrNull()

        if (runtimeMethod != null) {
            runtimeMethod.invoke(null)
            return
        }

        P2pandroid.stopNode()
    }
}
