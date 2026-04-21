package com.gomtm.swarm.runtime

import android.content.Context
import android.util.Log
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
import java.io.File
import java.lang.reflect.InvocationTargetException

class GomtmRuntimeFacade(
    private val bridgeClassName: String = DEFAULT_BRIDGE_CLASS_NAME,
    private val configClassName: String = DEFAULT_CONFIG_CLASS_NAME,
    private val classLoader: ClassLoader = GomtmRuntimeFacade::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader(),
) {
    fun start(context: Context, config: RuntimeLaunchConfig) {
        val bridge = resolveBridgeClass()
        Log.i(LOG_TAG, "bridge class=${bridge.name} methods=${bridge.methods.joinToString { method -> method.name + method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName } }}")
        val configClass = Class.forName(configClassName, true, classLoader)
        val configInstance = configClass.getDeclaredConstructor().newInstance()
        setStringProperty(
            configClass,
            configInstance,
            listOf(
                "SetConnectionAddr",
                "setConnectionAddr",
                "SetConnectionAddress",
                "setConnectionAddress",
            ),
            config.connectionAddress,
        )
        setBooleanProperty(configClass, configInstance, listOf("SetAutoReconnect", "setAutoReconnect"), config.autoReconnect)
        val baseDir = runtimeBaseDir(context)
        invokeStartBridge(bridge, configClass, configInstance, baseDir, config)
    }

    internal fun invokeStartBridge(
        bridge: Class<*>,
        configClass: Class<*>,
        configInstance: Any,
        baseDir: String,
        config: RuntimeLaunchConfig,
    ) {
        if (hasMethod(bridge, listOf("StartNode", "startNode"), arrayOf(String::class.java, configClass))) {
            invokeStaticByNames(
                bridge = bridge,
                methodNames = listOf("StartNode", "startNode"),
                args = arrayOf(baseDir, configInstance),
                parameterTypes = arrayOf(String::class.java, configClass),
            )
            return
        }

        for (booleanType in booleanParameterTypes()) {
            if (hasMethod(
                    bridge,
                    listOf("StartNodeWithOptions", "startNodeWithOptions"),
					arrayOf(String::class.java, String::class.java, booleanType),
                )
            ) {
                invokeStaticByNames(
                    bridge = bridge,
                    methodNames = listOf("StartNodeWithOptions", "startNodeWithOptions"),
					args = arrayOf(baseDir, config.connectionAddress, config.autoReconnect),
					parameterTypes = arrayOf(String::class.java, String::class.java, booleanType),
                )
                return
            }
        }

		if (hasMethod(
				bridge,
				listOf("StartNodeWithOptions", "startNodeWithOptions"),
				arrayOf(String::class.java, String::class.java),
			)
		) {
			invokeStaticByNames(
				bridge = bridge,
				methodNames = listOf("StartNodeWithOptions", "startNodeWithOptions"),
				args = arrayOf(baseDir, config.connectionAddress),
				parameterTypes = arrayOf(String::class.java, String::class.java),
			)
			return
		}

        throw IllegalStateException("bridge method not found: StartNode/StartNodeWithOptions")
    }

    fun stop() {
        invokeStaticByNames(
            bridge = resolveBridgeClass(),
            methodNames = listOf("StopNode", "stopNode"),
            args = emptyArray(),
            parameterTypes = emptyArray(),
        )
    }

    fun drainLogs(): String = invokeStringByNames("DrainLogs", "drainLogs")

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

    fun probe(): RuntimeSnapshot {
        val bridge = try {
            resolveBridgeClass()
        } catch (error: ReflectiveOperationException) {
            return RuntimeSnapshot.missing("gomtm swarm bridge unavailable: ${error.message ?: error.javaClass.simpleName}")
        }

        val rawDiscoveredPeers = invokeStringByNames(bridge, "GetDiscoveredPeers", "getDiscoveredPeers")
        val discoveredPeers = DiscoveredPeer.parseSnapshot(rawDiscoveredPeers)
        val state = invokeStringByNames(bridge, "GetState", "getState").ifBlank { "Unknown" }
        val peerId = invokeStringByNames(bridge, "GetPeerID", "GetPeerId", "getPeerID", "getPeerId")
        val connectionAddress = invokeStringByNames(
            bridge,
            "GetConnectionAddr",
            "GetConnectionAddress",
            "getConnectionAddr",
            "getConnectionAddress",
        )
        val lastError = invokeStringByNames(bridge, "GetLastError", "getLastError")
        val autoRestartBridge = runCatching { Class.forName("com.gomtm.swarm.platform.lifecycle.GomtmForegroundService", true, classLoader) }.getOrNull()
        val lastAutoRestartAtMs = autoRestartBridge?.let { bridgeClass ->
            runCatching {
                (bridgeClass.getMethod("getLastDegradedRestartAtMs").invoke(null) as? Number)?.toLong() ?: 0L
            }.getOrDefault(0L)
        } ?: 0L
        val lastAutoRestartReason = autoRestartBridge?.let { bridgeClass ->
            runCatching {
                bridgeClass.getMethod("getLastDegradedRestartReason").invoke(null) as? String ?: ""
            }.getOrDefault("")
        } ?: ""
        return RuntimeSnapshot(
            bridgeClassName = bridge.name,
            state = state,
            peerId = peerId,
            connectionAddress = connectionAddress,
            lastError = lastError,
            lastAutoRestartAtMs = lastAutoRestartAtMs,
            lastAutoRestartReason = lastAutoRestartReason,
            discoveredPeers = discoveredPeers,
            rawDiscoveredPeers = rawDiscoveredPeers,
        )
    }

    internal fun resolveBridgeClass(): Class<*> = Class.forName(bridgeClassName, true, classLoader)

    private fun runtimeBaseDir(context: Context): String {
        val baseDir = File(context.filesDir, "gomtm")
        baseDir.mkdirs()
        return baseDir.absolutePath
    }

    private fun invokeStringByNames(vararg methodNames: String): String {
        return try {
            invokeStringByNames(resolveBridgeClass(), *methodNames)
        } catch (_: ReflectiveOperationException) {
            ""
        }
    }

    private fun invokeStringByNames(bridge: Class<*>, vararg methodNames: String): String {
        for (name in methodNames) {
            try {
                val method = bridge.getMethod(name)
                return (method.invoke(null) as? String).orEmpty()
            } catch (_: NoSuchMethodException) {
                continue
            } catch (_: ReflectiveOperationException) {
                return ""
            }
        }
        return ""
    }

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

    private fun hasMethod(
        bridge: Class<*>,
        methodNames: List<String>,
        parameterTypes: Array<out Class<*>>,
    ): Boolean {
        for (name in methodNames) {
            try {
                bridge.getMethod(name, *parameterTypes)
                return true
            } catch (_: NoSuchMethodException) {
                continue
            }
        }
        return false
    }

    private fun invokeStaticByNames(
        bridge: Class<*>,
        methodNames: List<String>,
        args: Array<out Any?>,
        parameterTypes: Array<out Class<*>>,
    ): Any? {
        var lastMissing: NoSuchMethodException? = null
        for (name in methodNames) {
            try {
                val method = bridge.getMethod(name, *parameterTypes)
                return method.invoke(null, *args)
            } catch (error: NoSuchMethodException) {
                lastMissing = error
                continue
            } catch (error: InvocationTargetException) {
                val cause = error.targetException ?: error.cause ?: error
                throw IllegalStateException(cause.message ?: cause.javaClass.simpleName, cause)
            }
        }
        throw IllegalStateException("bridge method not found: ${methodNames.joinToString()}", lastMissing)
    }

    private fun setStringProperty(targetClass: Class<*>, target: Any, methodNames: List<String>, value: String) {
        var lastMissing: NoSuchMethodException? = null
        for (name in methodNames) {
            try {
                targetClass.getMethod(name, String::class.java).invoke(target, value)
                return
            } catch (error: NoSuchMethodException) {
                lastMissing = error
                continue
            }
        }
        throw IllegalStateException("config setter not found: ${methodNames.joinToString()}", lastMissing)
    }

    private fun setBooleanProperty(targetClass: Class<*>, target: Any, methodNames: List<String>, value: Boolean) {
        var lastMissing: NoSuchMethodException? = null
        for (booleanType in booleanParameterTypes()) {
            for (name in methodNames) {
                try {
                    targetClass.getMethod(name, booleanType).invoke(target, value)
                    return
                } catch (error: NoSuchMethodException) {
                    lastMissing = error
                    continue
                }
            }
        }
        throw IllegalStateException("config setter not found: ${methodNames.joinToString()}", lastMissing)
    }

    private fun booleanParameterTypes(): List<Class<*>> {
        val primitive = java.lang.Boolean.TYPE
        val boxed = java.lang.Boolean::class.java
        return if (primitive == boxed) listOf(primitive) else listOf(primitive, boxed)
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
        internal const val DEFAULT_CONFIG_CLASS_NAME = "io.nekohasekai.p2pandroid.Config"
        private const val LOG_TAG = "GomtmSwarmRuntime"
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
