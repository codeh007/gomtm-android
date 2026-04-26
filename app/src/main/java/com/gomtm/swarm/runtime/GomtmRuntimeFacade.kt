package com.gomtm.swarm.runtime

import android.content.Context
import com.gomtm.swarm.platform.accessibility.GomtmAccessibilityService
import com.gomtm.swarm.platform.remote.AndroidRemoteControlOps
import com.gomtm.swarm.platform.remote.AndroidScreenStreamHost
import com.gomtm.swarm.platform.remote.AndroidWebRtcScreenHost
import com.gomtm.swarm.platform.remote.RemoteControlCapabilityState
import com.gomtm.swarm.platform.remote.RemoteControlCommandResult
import com.gomtm.swarm.platform.remote.RemoteControlOps
import com.gomtm.swarm.platform.remote.RemoteControlPermissionState
import com.gomtm.swarm.platform.remote.RemoteControlWebRtcSessionState
import com.gomtm.swarm.platform.remote.RemoteControlWebRtcStartPayload
import com.gomtm.swarm.platform.remote.WebRtcScreenHost
import com.gomtm.swarm.platform.remote.encodeRemoteControlResponse
import com.gomtm.swarm.platform.remote.handleRemoteControlRequest
import com.gomtm.swarm.platform.remote.parseRemoteControlRequest

class GomtmRuntimeFacade(
    private val bridgeClassName: String = DEFAULT_BRIDGE_CLASS_NAME,
    private val classLoader: ClassLoader = GomtmRuntimeFacade::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader(),
) {
    fun pollRemoteControlRequest(timeoutMs: Int): String {
        val bridge = try {
            resolveBridgeClass()
        } catch (_: ReflectiveOperationException) {
            return ""
        }

        for (parameterType in timeoutParameterTypes()) {
            val result = invokeOptionalByNames(
                bridge = bridge,
                methodNames = listOf("PollRemoteControlRequest", "pollRemoteControlRequest"),
                args = arrayOf(castTimeout(timeoutMs, parameterType)),
                parameterTypes = arrayOf(parameterType),
            )
            if (result is String) {
                return result
            }
        }
        return ""
    }

    fun resolveRemoteControlResponse(responseJson: String) {
        val bridge = try {
            resolveBridgeClass()
        } catch (_: ReflectiveOperationException) {
            return
        }
        invokeOptionalByNames(
            bridge = bridge,
            methodNames = listOf("ResolveRemoteControlResponse", "resolveRemoteControlResponse"),
            args = arrayOf(responseJson),
            parameterTypes = arrayOf(String::class.java),
        )
    }

    fun setRemoteControlPermissionState(accessibility: String, screenCapture: String) {
        val bridge = try {
            resolveBridgeClass()
        } catch (_: ReflectiveOperationException) {
            return
        }
        invokeOptionalByNames(
            bridge = bridge,
            methodNames = listOf("SetRemoteControlPermissionState", "setRemoteControlPermissionState"),
            args = arrayOf(accessibility, screenCapture),
            parameterTypes = arrayOf(String::class.java, String::class.java),
        )
    }

    fun setRemoteControlStreamState(state: String, reason: String) {
        val bridge = try {
            resolveBridgeClass()
        } catch (_: ReflectiveOperationException) {
            return
        }
        invokeOptionalByNames(
            bridge = bridge,
            methodNames = listOf("SetRemoteControlStreamState", "setRemoteControlStreamState"),
            args = arrayOf(state, reason),
            parameterTypes = arrayOf(String::class.java, String::class.java),
        )
    }

    fun setRemoteControlWebRTCState(state: String, topology: String, sessionId: String, reason: String) {
        val bridge = try {
            resolveBridgeClass()
        } catch (_: ReflectiveOperationException) {
            return
        }
        invokeOptionalByNames(
            bridge = bridge,
            methodNames = listOf("SetRemoteControlWebRTCState", "setRemoteControlWebRTCState"),
            args = arrayOf(state, topology, sessionId, reason),
            parameterTypes = arrayOf(String::class.java, String::class.java, String::class.java, String::class.java),
        )
    }

    fun remoteControlPermissionState(context: Context): RemoteControlPermissionState {
        return RemoteControlPermissionState(
            accessibility = if (GomtmAccessibilityService.isEnabled(context)) "granted" else "not_granted",
            screenCapture = AndroidScreenStreamHost.currentPermissionState(context),
        )
    }

    fun processRemoteControlTick(context: Context, timeoutMs: Int = 0) {
        val permissionState = remoteControlPermissionState(context)
        val streamState = AndroidScreenStreamHost.currentCapabilityState(context)
        val webRtcHost = AndroidWebRtcScreenHost.forContext(context)
        publishRemoteControlTickForTest(
            permissionState = permissionState,
            streamState = streamState,
            webRtcSessionState = webRtcHost.currentState(),
        )

        processRemoteControlRequestForTest(
            requestJson = pollRemoteControlRequest(timeoutMs),
            ops = AndroidRemoteControlOps(context),
            webRtcHost = webRtcHost,
        )
    }

    internal fun publishRemoteControlTickForTest(
        permissionState: RemoteControlPermissionState,
        streamState: RemoteControlCapabilityState,
        webRtcSessionState: RemoteControlWebRtcSessionState,
    ) {
        setRemoteControlPermissionState(permissionState.accessibility, permissionState.screenCapture)
        setRemoteControlStreamState(streamState.state, streamState.reason.orEmpty())
        val webRtcState = projectRemoteControlWebRtcBridgeState(streamState, webRtcSessionState)
        setRemoteControlWebRTCState(
            webRtcState.state,
            webRtcState.topology.orEmpty(),
            webRtcState.sessionId.orEmpty(),
            webRtcState.lastError.orEmpty(),
        )
    }

    internal fun processRemoteControlRequestForTest(
        requestJson: String,
        ops: RemoteControlOps,
        webRtcHost: WebRtcScreenHost = object : WebRtcScreenHost {
            override fun start(): RemoteControlCommandResult<RemoteControlWebRtcStartPayload> =
                RemoteControlCommandResult.Error("SB_CAPABILITY_UNAVAILABLE", "screen.webrtc.start host is not configured", false)

            override fun stop() = Unit

            override fun currentState(): RemoteControlWebRtcSessionState = RemoteControlWebRtcSessionState(state = "host_not_ready")
        },
    ) {
        val request = parseRemoteControlRequest(requestJson) ?: return
        val response = handleRemoteControlRequest(request, ops, webRtcHost)
        resolveRemoteControlResponse(encodeRemoteControlResponse(response))
    }

    internal fun resolveBridgeClass(): Class<*> = Class.forName(bridgeClassName, true, classLoader)

    private fun invokeOptionalByNames(
        bridge: Class<*>,
        methodNames: List<String>,
        args: Array<out Any?>,
        parameterTypes: Array<out Class<*>>,
    ): Any? {
        for (name in methodNames) {
            try {
                val method = bridge.getMethod(name, *parameterTypes)
                return method.invoke(null, *args)
            } catch (_: NoSuchMethodException) {
                continue
            } catch (_: ReflectiveOperationException) {
                return null
            }
        }
        return null
    }

    private fun timeoutParameterTypes(): List<Class<*>> {
        return listOf(
            java.lang.Long.TYPE,
            java.lang.Long::class.java,
            Integer.TYPE,
            java.lang.Integer::class.java,
        )
    }

    private fun castTimeout(timeoutMs: Int, parameterType: Class<*>): Any {
        return when (parameterType) {
            java.lang.Long.TYPE, java.lang.Long::class.java -> timeoutMs.toLong()
            else -> timeoutMs
        }
    }

    companion object {
        internal const val DEFAULT_BRIDGE_CLASS_NAME = "io.nekohasekai.p2pandroid.P2pandroid"
    }
}

internal fun projectRemoteControlWebRtcBridgeState(
    streamState: RemoteControlCapabilityState,
    sessionState: RemoteControlWebRtcSessionState,
): RemoteControlWebRtcSessionState {
    val streamCapabilityState = normalizeRemoteControlState(streamState.state)
    val streamReason = trimRemoteControlText(streamState.reason)
    val sessionLifecycleState = normalizeRemoteControlState(sessionState.state)
    val sessionId = trimRemoteControlText(sessionState.sessionId)
    val topology = trimRemoteControlText(sessionState.topology)
    val sessionReason = trimRemoteControlText(sessionState.lastError)
    if (sessionId != null) {
        return when (sessionLifecycleState) {
            "connecting", "connected", "ready", "active" -> RemoteControlWebRtcSessionState(
                state = sessionLifecycleState,
                topology = topology ?: "signaling_starting",
                sessionId = sessionId,
                lastError = sessionReason ?: streamReason,
            )

            "error" -> RemoteControlWebRtcSessionState(
                state = "error",
                topology = topology ?: "signaling_starting",
                sessionId = sessionId,
                lastError = sessionReason ?: streamReason,
            )

            "permission_required" -> RemoteControlWebRtcSessionState(
                state = "permission_required",
                lastError = sessionReason ?: streamReason ?: "screen_capture_not_granted",
            )

            "unavailable" -> RemoteControlWebRtcSessionState(
                state = "unavailable",
                lastError = sessionReason ?: streamReason ?: "unsupported",
            )

            else -> RemoteControlWebRtcSessionState(
                state = "host_not_ready",
                topology = topology ?: "signaling_starting",
                sessionId = sessionId,
                lastError = sessionReason ?: streamReason ?: "awaiting_stream_runtime",
            )
        }
    }

    return when (sessionLifecycleState) {
        "error" -> RemoteControlWebRtcSessionState(
            state = "error",
            lastError = sessionReason ?: streamReason,
        )

        "permission_required" -> RemoteControlWebRtcSessionState(
            state = "permission_required",
            lastError = sessionReason ?: streamReason ?: "screen_capture_not_granted",
        )

        "unavailable" -> RemoteControlWebRtcSessionState(
            state = "unavailable",
            lastError = sessionReason ?: streamReason ?: "unsupported",
        )

        "host_not_ready" -> RemoteControlWebRtcSessionState(
            state = "host_not_ready",
            lastError = sessionReason ?: streamReason ?: "awaiting_stream_runtime",
        )

        else -> when (streamCapabilityState) {
            "available", "streaming" -> RemoteControlWebRtcSessionState(state = "available")
            "permission_required" -> RemoteControlWebRtcSessionState(
                state = "permission_required",
                lastError = streamReason ?: "screen_capture_not_granted",
            )

            "unavailable" -> RemoteControlWebRtcSessionState(
                state = "unavailable",
                lastError = streamReason ?: "unsupported",
            )

            "error" -> RemoteControlWebRtcSessionState(
                state = "error",
                lastError = streamReason,
            )

            else -> RemoteControlWebRtcSessionState(
                state = "host_not_ready",
                lastError = streamReason ?: "awaiting_stream_runtime",
            )
        }
    }
}

private fun normalizeRemoteControlState(value: String?): String? {
    return value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}

private fun trimRemoteControlText(value: String?): String? {
    return value?.trim()?.takeIf { it.isNotEmpty() }
}
