package com.gomtm.swarm.swarm

import android.content.Context
import java.util.UUID

object AndroidWebRtcScreenHost {
    private val lock = Any()
    private var currentState = RemoteControlWebRtcSessionState(state = "host_not_ready")

    fun forContext(context: Context): WebRtcScreenHost {
        val appContext = context.applicationContext
        return object : WebRtcScreenHost {
            override fun start(): RemoteControlCommandResult<RemoteControlWebRtcStartPayload> {
                return when (val result = AndroidScreenStreamHost.ensureStream(appContext)) {
                    is RemoteControlCommandResult.Success -> {
                        val payload = RemoteControlWebRtcStartPayload(
                            sessionId = "webrtc-${UUID.randomUUID()}",
                            state = "connecting",
                            topology = "signaling_starting",
                            lastError = result.payload.lastError,
                        )
                        synchronized(lock) {
                            currentState = RemoteControlWebRtcSessionState(
                                state = payload.state,
                                topology = payload.topology,
                                sessionId = payload.sessionId,
                                lastError = payload.lastError,
                            )
                        }
                        RemoteControlCommandResult.Success(payload)
                    }

                    is RemoteControlCommandResult.Error -> {
                        synchronized(lock) {
                            currentState = RemoteControlWebRtcSessionState(
                                state = if (result.retryable) "host_not_ready" else "error",
                                lastError = result.message,
                            )
                        }
                        RemoteControlCommandResult.Error(result.code, result.message, result.retryable)
                    }
                }
            }

            override fun stop() {
                synchronized(lock) {
                    currentState = RemoteControlWebRtcSessionState(state = "idle")
                }
            }

            override fun currentState(): RemoteControlWebRtcSessionState {
                synchronized(lock) {
                    return currentState
                }
            }
        }
    }
}
