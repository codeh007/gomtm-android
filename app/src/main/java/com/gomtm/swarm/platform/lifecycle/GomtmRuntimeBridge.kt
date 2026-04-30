package com.gomtm.swarm.platform.lifecycle

import io.nekohasekai.gomtmruntime.Gomtmruntime

internal object GomtmRuntimeBridge {
    private const val RUNTIME_ADDR = "127.0.0.1:8383"

    fun startRuntime(baseDir: String, connectionAddr: String) {
        Gomtmruntime.startRuntimeWithOptions(baseDir, connectionAddr, RUNTIME_ADDR)
    }

    fun stopRuntime() {
        Gomtmruntime.stopRuntime()
    }
}
