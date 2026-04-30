package com.gomtm.swarm.platform.remote

import io.nekohasekai.gomtmruntime.Gomtmruntime
import java.util.concurrent.atomic.AtomicBoolean

interface NativeRemoteControlBridgeApi {
    fun pollRemoteControlRequest(timeoutMs: Int): String

    fun resolveRemoteControlResponse(responseJson: String)
}

object GomtmruntimeNativeRemoteControlBridgeApi : NativeRemoteControlBridgeApi {
    override fun pollRemoteControlRequest(timeoutMs: Int): String {
        return Gomtmruntime.pollRemoteControlRequest(timeoutMs.toLong())
    }

    override fun resolveRemoteControlResponse(responseJson: String) {
        Gomtmruntime.resolveRemoteControlResponse(responseJson)
    }
}

class NativeRemoteControlBridgeLoop(
    private val bridgeApi: NativeRemoteControlBridgeApi = GomtmruntimeNativeRemoteControlBridgeApi,
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
