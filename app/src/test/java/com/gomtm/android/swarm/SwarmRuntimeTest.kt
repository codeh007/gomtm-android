package com.gomtm.android.swarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwarmRuntimeTest {
    @Test
    fun exposesUnboundHostShellWhenBridgeIsMissing() {
        val runtime = SwarmRuntime(
            bridgeClassNames = listOf("does.not.exist.Bridge"),
            classLoader = javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        )

        val status = runtime.probe()

        assertEquals("host-shell", status.integrationMode)
        assertFalse(status.aarDetected)
        assertEquals("AAR not bound", status.state)
    }

    @Test
    fun detectsLegacyWorkerLifecycleSurface() {
        val runtime = SwarmRuntime(
            bridgeClassNames = listOf(FakeLegacyBridge::class.java.name),
            classLoader = FakeLegacyBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
        )

        val status = runtime.probe()

        assertTrue(status.aarDetected)
        assertEquals("worker-legacy", status.lifecycleSurface)
        assertEquals("Running", status.state)
        assertEquals("peer-123", status.peerId)
        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", status.bootstrapAddress)
    }

    class FakeLegacyBridge {
        companion object {
            @JvmStatic
            fun StartWorker(baseDir: String, nodeName: String) = Unit

            @JvmStatic
            fun StopWorker() = Unit

            @JvmStatic
            fun GetState(): String = "Running"

            @JvmStatic
            fun GetPeerID(): String = "peer-123"

            @JvmStatic
            fun GetBootstrapAddr(): String = "/ip4/127.0.0.1/tcp/4101/p2p/test"

            @JvmStatic
            fun GetLastError(): String = ""

            @JvmStatic
            fun DrainLogs(): String = "bootstrap ok"
        }
    }
}
