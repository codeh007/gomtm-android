package com.gomtm.android.swarm

import android.content.Context
import android.os.Build
import java.io.File
import java.lang.reflect.InvocationTargetException

class SwarmRuntime(
    private val bridgeClassName: String = DEFAULT_BRIDGE_CLASS_NAME,
    private val configClassName: String = DEFAULT_CONFIG_CLASS_NAME,
    private val classLoader: ClassLoader = SwarmRuntime::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader(),
) {
    fun start(context: Context, config: SwarmNodeConfig) {
        val bridge = resolveBridgeClass()
        val configClass = Class.forName(configClassName, true, classLoader)
        val configInstance = configClass.getDeclaredConstructor().newInstance()
        setStringProperty(configClass, configInstance, listOf("SetBootstrapAddr", "setBootstrapAddr"), config.bootstrapAddress)
        setStringProperty(configClass, configInstance, listOf("SetNodeName", "setNodeName"), config.nodeName)
        setBooleanProperty(configClass, configInstance, listOf("SetAutoReconnect", "setAutoReconnect"), config.autoReconnect)
        val baseDir = runtimeBaseDir(context)
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
                    arrayOf(String::class.java, String::class.java, String::class.java, booleanType),
                )
            ) {
                invokeStaticByNames(
                    bridge = bridge,
                    methodNames = listOf("StartNodeWithOptions", "startNodeWithOptions"),
                    args = arrayOf(baseDir, config.bootstrapAddress, config.nodeName, config.autoReconnect),
                    parameterTypes = arrayOf(String::class.java, String::class.java, String::class.java, booleanType),
                )
                return
            }
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

    fun remoteControlPermissionState(context: Context): RemoteControlPermissionState {
        return deriveRemoteControlPermissionState(
            accessibilityEnabled = GomtmAccessibilityService.isEnabled(context),
            screenshotSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
    }

    fun processRemoteControlTick(context: Context, timeoutMs: Int = 0) {
        val permissionState = remoteControlPermissionState(context)
        setRemoteControlPermissionState(permissionState.accessibility, permissionState.screenCapture)

        val request = parseRemoteControlRequest(pollRemoteControlRequest(timeoutMs)) ?: return
        val response = handleRemoteControlRequest(request, AndroidRemoteControlOps(context))
        resolveRemoteControlResponse(encodeRemoteControlResponse(response))
    }

    fun probe(): SwarmStatus {
        val bridge = try {
            resolveBridgeClass()
        } catch (error: ReflectiveOperationException) {
            return SwarmStatus.missing("gomtm swarm bridge unavailable: ${error.message ?: error.javaClass.simpleName}")
        }

        val rawDiscoveredPeers = invokeStringByNames(bridge, "GetDiscoveredPeers", "getDiscoveredPeers")
        return SwarmStatus(
            bridgeClassName = bridge.name,
            state = invokeStringByNames(bridge, "GetState", "getState").ifBlank { "Unknown" },
            peerId = invokeStringByNames(bridge, "GetPeerID", "GetPeerId", "getPeerID", "getPeerId"),
            bootstrapAddress = invokeStringByNames(bridge, "GetBootstrapAddr", "GetBootstrapAddress", "getBootstrapAddr", "getBootstrapAddress"),
            lastError = invokeStringByNames(bridge, "GetLastError", "getLastError"),
            discoveredPeers = DiscoveredPeer.parseSnapshot(rawDiscoveredPeers),
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
    }
}
