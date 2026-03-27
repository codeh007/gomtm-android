package com.gomtm.android.swarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwarmRuntimeTest {
    @Test
    fun exposesMissingBridgeAsErrorState() {
        val runtime = SwarmRuntime(
            bridgeClassName = "does.not.exist.Bridge",
            configClassName = FakeConfig::class.java.name,
            classLoader = javaClass.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        val status = runtime.probe()

        assertEquals("Error", status.state)
        assertTrue(status.lastError.contains("gomtm swarm bridge unavailable"))
    }

    @Test
    fun parsesCurrentNodeLifecycleSurface() {
        val runtime = SwarmRuntime(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        val status = runtime.probe()

        assertEquals("Registered", status.state)
        assertEquals("peer-123", status.peerId)
        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", status.bootstrapAddress)
        assertEquals(1, status.discoveredPeers.size)
        assertEquals("peer-b", status.discoveredPeers.single().peerId)
    }

    @Test
    fun parsesDiscoveredPeersSnapshot() {
        val json =
            """
            {"schema_version":"v1","generated_at":"2026-03-27T10:00:00Z","peers":[{"peer_id":"peer-b","name":"android-b","state":"registered","discovered_in_current_session":true,"is_bootstrap":false,"last_seen_at":"2026-03-27T10:00:00Z"}]}
            """.trimIndent()

        val peers = DiscoveredPeer.parseSnapshot(json)

        assertEquals(1, peers.size)
        assertEquals("peer-b", peers.single().peerId)
        assertEquals("android-b", peers.single().name)
    }

    class FakeConfig {
        fun setBootstrapAddr(@Suppress("UNUSED_PARAMETER") value: String) = Unit
        fun setNodeName(@Suppress("UNUSED_PARAMETER") value: String) = Unit
        fun setAutoReconnect(@Suppress("UNUSED_PARAMETER") value: Boolean) = Unit
    }

    class FakeNodeBridge {
        companion object {
            @JvmStatic
            fun startNode(@Suppress("UNUSED_PARAMETER") baseDir: String, @Suppress("UNUSED_PARAMETER") config: FakeConfig) = Unit

            @JvmStatic
            fun stopNode() = Unit

            @JvmStatic
            fun getState(): String = "Registered"

            @JvmStatic
            fun getPeerID(): String = "peer-123"

            @JvmStatic
            fun getBootstrapAddr(): String = "/ip4/127.0.0.1/tcp/4101/p2p/test"

            @JvmStatic
            fun getLastError(): String = ""

            @JvmStatic
            fun getDiscoveredPeers(): String =
                """
                {"schema_version":"v1","generated_at":"2026-03-27T10:00:00Z","peers":[{"peer_id":"peer-b","name":"android-b","state":"registered","discovered_in_current_session":true,"is_bootstrap":false,"last_seen_at":"2026-03-27T10:00:00Z"}]}
                """.trimIndent()

            @JvmStatic
            fun drainLogs(): String = "bootstrap ok"
        }
    }
}
