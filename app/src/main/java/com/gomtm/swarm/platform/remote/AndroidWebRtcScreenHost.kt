package com.gomtm.swarm.platform.remote

import android.content.Context
import java.util.UUID

object AndroidWebRtcScreenHost {
    private val lock = Any()
    private var currentState = RemoteControlWebRtcSessionState(state = "idle")

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
                            currentState = payload.toSessionState()
                        }
                        RemoteControlCommandResult.Success(payload)
                    }

                    is RemoteControlCommandResult.Error -> {
                        synchronized(lock) {
                            currentState = errorStateFrom(result)
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
                val streamCapability = AndroidScreenStreamHost.currentCapabilityState(appContext)
                synchronized(lock) {
                    currentState = reconcileCurrentState(currentState, streamCapability)
                    return currentState
                }
            }
        }
    }

    internal fun reconcileCurrentState(
        currentState: RemoteControlWebRtcSessionState,
        streamCapability: RemoteControlCapabilityState,
    ): RemoteControlWebRtcSessionState {
        val state = normalizeState(currentState.state) ?: "idle"
        val sessionId = trimText(currentState.sessionId)
        val topology = trimText(currentState.topology)
        val lastError = trimText(currentState.lastError)
        if (sessionId == null) {
            return when (state) {
                "idle", "error", "host_not_ready", "permission_required", "unavailable" -> {
                    RemoteControlWebRtcSessionState(
                        state = state,
                        lastError = lastError,
                    )
                }

                else -> RemoteControlWebRtcSessionState(
                    state = "idle",
                    lastError = lastError,
                )
            }
        }

        return when (normalizeState(streamCapability.state)) {
            "permission_required" -> RemoteControlWebRtcSessionState(
                state = "permission_required",
                lastError = trimText(streamCapability.reason) ?: lastError,
            )

            "unavailable" -> RemoteControlWebRtcSessionState(
                state = "unavailable",
                lastError = trimText(streamCapability.reason) ?: lastError,
            )

            "starting", "host_not_ready" -> RemoteControlWebRtcSessionState(
                state = "host_not_ready",
                topology = topology ?: "signaling_starting",
                sessionId = sessionId,
                lastError = trimText(streamCapability.reason) ?: lastError,
            )

            "error" -> RemoteControlWebRtcSessionState(
                state = "error",
                topology = topology ?: "signaling_starting",
                sessionId = sessionId,
                lastError = trimText(streamCapability.reason) ?: lastError,
            )

            else -> RemoteControlWebRtcSessionState(
                state = normalizeActiveSessionState(state),
                topology = topology ?: "signaling_starting",
                sessionId = sessionId,
                lastError = lastError,
            )
        }
    }

    private fun errorStateFrom(error: RemoteControlCommandResult.Error): RemoteControlWebRtcSessionState {
        val state = when {
            error.code == "SB_PERMISSION_REQUIRED" -> "permission_required"
            error.code == "SB_CAPABILITY_UNAVAILABLE" -> "unavailable"
            error.retryable -> "host_not_ready"
            else -> "error"
        }
        return RemoteControlWebRtcSessionState(state = state, lastError = error.message)
    }

    private fun RemoteControlWebRtcStartPayload.toSessionState(): RemoteControlWebRtcSessionState {
        return RemoteControlWebRtcSessionState(
            state = state,
            topology = topology,
            sessionId = sessionId,
            lastError = lastError,
        )
    }

    private fun normalizeActiveSessionState(state: String): String {
        return when (state) {
            "active", "connected", "connecting", "ready" -> state
            else -> "connecting"
        }
    }

    private fun normalizeState(value: String?): String? {
        return value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    private fun trimText(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
