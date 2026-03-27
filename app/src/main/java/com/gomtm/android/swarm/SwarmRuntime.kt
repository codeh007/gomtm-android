package com.gomtm.android.swarm

import android.content.Context
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
        setStringProperty(configClass, configInstance, "setBootstrapAddr", config.bootstrapAddress)
        setStringProperty(configClass, configInstance, "setNodeName", config.nodeName)
        setBooleanProperty(configClass, configInstance, "setAutoReconnect", config.autoReconnect)
        invokeStatic(
            bridge = bridge,
            methodName = "startNode",
            args = arrayOf(runtimeBaseDir(context), configInstance),
            parameterTypes = arrayOf(String::class.java, configClass),
        )
    }

    fun stop() {
        invokeStatic(
            bridge = resolveBridgeClass(),
            methodName = "stopNode",
            args = emptyArray(),
            parameterTypes = emptyArray(),
        )
    }

    fun drainLogs(): String = invokeString("drainLogs")

    fun probe(): SwarmStatus {
        val bridge = try {
            resolveBridgeClass()
        } catch (error: ReflectiveOperationException) {
            return SwarmStatus.missing("gomtm swarm bridge unavailable: ${error.message ?: error.javaClass.simpleName}")
        }

        val rawDiscoveredPeers = invokeString("getDiscoveredPeers")
        return SwarmStatus(
            bridgeClassName = bridge.name,
            state = invokeString("getState").ifBlank { "Unknown" },
            peerId = invokeString("getPeerID"),
            bootstrapAddress = invokeString("getBootstrapAddr"),
            lastError = invokeString("getLastError"),
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

    private fun invokeString(methodName: String): String {
        return try {
            (invokeStatic(resolveBridgeClass(), methodName, emptyArray(), emptyArray()) as? String).orEmpty()
        } catch (_: ReflectiveOperationException) {
            ""
        }
    }

    private fun invokeStatic(
        bridge: Class<*>,
        methodName: String,
        args: Array<out Any?>,
        parameterTypes: Array<out Class<*>>,
    ): Any? {
        try {
            val method = bridge.getMethod(methodName, *parameterTypes)
            return method.invoke(null, *args)
        } catch (error: InvocationTargetException) {
            val cause = error.targetException ?: error.cause ?: error
            throw IllegalStateException(cause.message ?: cause.javaClass.simpleName, cause)
        }
    }

    private fun setStringProperty(targetClass: Class<*>, target: Any, methodName: String, value: String) {
        targetClass.getMethod(methodName, String::class.java).invoke(target, value)
    }

    private fun setBooleanProperty(targetClass: Class<*>, target: Any, methodName: String, value: Boolean) {
        val booleanType = Boolean::class.javaPrimitiveType ?: Boolean::class.javaObjectType
        targetClass.getMethod(methodName, booleanType).invoke(target, value)
    }

    companion object {
        internal const val DEFAULT_BRIDGE_CLASS_NAME = "io.nekohasekai.p2pandroid.P2pandroid"
        internal const val DEFAULT_CONFIG_CLASS_NAME = "io.nekohasekai.p2pandroid.Config"
    }
}
