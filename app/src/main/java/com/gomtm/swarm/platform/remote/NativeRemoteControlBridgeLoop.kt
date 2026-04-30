package com.gomtm.swarm.platform.remote

import io.nekohasekai.p2pandroid.P2pandroid
import java.util.concurrent.atomic.AtomicBoolean

interface NativeRemoteControlBridgeApi {
    fun pollRemoteControlRequest(timeoutMs: Int): String

    fun resolveRemoteControlResponse(responseJson: String)
}

object P2pandroidNativeRemoteControlBridgeApi : NativeRemoteControlBridgeApi {
    private val pollMethod = runCatching {
        P2pandroid::class.java.getMethod("pollRemoteControlRequest", Int::class.javaPrimitiveType)
    }.getOrNull()
    private val resolveMethod = runCatching {
        P2pandroid::class.java.getMethod("resolveRemoteControlResponse", String::class.java)
    }.getOrNull()

    override fun pollRemoteControlRequest(timeoutMs: Int): String {
        val method = pollMethod ?: return ""
        return (method.invoke(null, timeoutMs) as? String).orEmpty()
    }

    override fun resolveRemoteControlResponse(responseJson: String) {
        val method = resolveMethod ?: return
        method.invoke(null, responseJson)
    }
}

class NativeRemoteControlBridgeLoop(
    private val bridgeApi: NativeRemoteControlBridgeApi = P2pandroidNativeRemoteControlBridgeApi,
    private val opsFactory: () -> RemoteControlOps,
    private val webRtcHostFactory: () -> WebRtcScreenHost,
) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val ops = opsFactory()
        val webRtcHost = webRtcHostFactory()
        worker = Thread(
            {
                while (running.get()) {
                    val rawRequest = bridgeApi.pollRemoteControlRequest(POLL_TIMEOUT_MS)
                    if (rawRequest.isBlank()) {
                        continue
                    }

                    val response = runCatching {
                        val request = requireNotNull(parseRemoteControlRequest(rawRequest)) {
                            "invalid remote control request"
                        }
                        handleRemoteControlRequest(request = request, ops = ops, webRtcHost = webRtcHost)
                    }.getOrElse { error ->
                        val requestId = parseRemoteControlRequest(rawRequest)?.requestId.orEmpty()
                        RemoteControlCommandResponse(
                            requestId = requestId,
                            ok = false,
                            errorCode = "SB_INTERNAL",
                            errorMessage = error.message ?: "remote control bridge loop failed",
                            retryable = true,
                        )
                    }

                    bridgeApi.resolveRemoteControlResponse(encodeRemoteControlResponse(response))
                }
            },
            "gomtm-remote-control-bridge",
        ).apply { start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        worker?.interrupt()
        worker = null
    }

    companion object {
        private const val POLL_TIMEOUT_MS = 250
    }
}
