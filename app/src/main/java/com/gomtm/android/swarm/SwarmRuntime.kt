package com.gomtm.android.swarm

class SwarmRuntime(
    private val bridgeClassNames: List<String> = DEFAULT_BRIDGE_CLASS_NAMES,
    private val classLoader: ClassLoader = SwarmRuntime::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader()
) {
    fun probe(): SwarmStatus {
        val bridge = resolveBridgeClass() ?: return SwarmStatus.missing()

        return SwarmStatus(
            integrationMode = "aar-bound",
            aarDetected = true,
            bridgeClassName = bridge.name,
            lifecycleSurface = detectLifecycleSurface(bridge),
            state = callString(bridge, "GetState") ?: "Unknown",
            peerId = callString(bridge, "GetPeerID"),
            bootstrapAddress = callString(bridge, "GetBootstrapAddr"),
            lastError = callString(bridge, "GetLastError")
        )
    }

    fun drainLogs(): String {
        val bridge = resolveBridgeClass() ?: return ""
        return callString(bridge, "DrainLogs")?.trim().orEmpty()
    }

    internal fun resolveBridgeClass(): Class<*>? {
        for (candidate in bridgeClassNames) {
            try {
                return Class.forName(candidate, false, classLoader)
            } catch (_: ClassNotFoundException) {
                continue
            }
        }
        return null
    }

    private fun detectLifecycleSurface(bridge: Class<*>): String {
        val methodNames = bridge.methods.map { it.name }.toSet()
        return when {
            methodNames.containsAll(setOf("StartNode", "StopNode")) -> "node"
            methodNames.containsAll(setOf("StartWorker", "StopWorker")) -> "worker-legacy"
            else -> "probe-only"
        }
    }

    private fun callString(target: Class<*>, methodName: String): String? {
        return try {
            val method = target.getMethod(methodName)
            (method.invoke(null) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    companion object {
        internal val DEFAULT_BRIDGE_CLASS_NAMES = listOf(
            "io.nekohasekai.p2pandroid.P2pandroid",
            "com.gomtm.swarm.GomtmSwarm",
            "com.gomtm.android.swarm.GomtmSwarm"
        )
    }
}
