package com.gomtm.swarm.swarm

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

    @Test
    fun readsRemoteControlRequestFromBridge() {
        val runtime = SwarmRuntime(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        val request = runtime.pollRemoteControlRequest(timeoutMs = 0)

        assertTrue(request.contains("screen.snapshot"))
    }

    @Test
    fun forwardsRemoteControlResponseAndPermissionStateToBridge() {
        val runtime = SwarmRuntime(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        runtime.resolveRemoteControlResponse("{\"request_id\":\"req-1\",\"ok\":true}")
        runtime.setRemoteControlPermissionState("granted", "unsupported")
        runtime.setRemoteControlStreamState("streaming", "")

        assertEquals("{\"request_id\":\"req-1\",\"ok\":true}", FakeNodeBridge.lastRemoteControlResponse)
        assertEquals("granted", FakeNodeBridge.lastAccessibilityPermission)
        assertEquals("unsupported", FakeNodeBridge.lastScreenCapturePermission)
        assertEquals("streaming", FakeNodeBridge.lastScreenStreamState)
    }

    @Test
    fun startSupportsTwoArgStartNodeWithOptionsBridge() {
        val runtime = SwarmRuntime(
            bridgeClassName = FakeTwoArgOptionsBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeTwoArgOptionsBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

		val configClass = FakeConfig::class.java
        val configInstance = configClass.getDeclaredConstructor().newInstance()
        configInstance.setBootstrapAddr("/ip4/127.0.0.1/tcp/4101/p2p/test")
        configInstance.setAutoReconnect(true)
        runtime.invokeStartBridge(
            bridge = FakeTwoArgOptionsBridge::class.java,
            configClass = configClass,
            configInstance = configInstance,
            baseDir = "/tmp/gomtm-test",
            config = SwarmNodeConfig(bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test"),
        )

        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", FakeTwoArgOptionsBridge.lastBootstrapAddr)
    }

    @Test
    fun startSupportsThreeArgStartNodeWithOptionsBridge() {
        val runtime = SwarmRuntime(
            bridgeClassName = FakeThreeArgOptionsBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeThreeArgOptionsBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        val configClass = FakeConfig::class.java
        val configInstance = configClass.getDeclaredConstructor().newInstance()
        configInstance.setBootstrapAddr("/ip4/127.0.0.1/tcp/4101/p2p/test")
        configInstance.setAutoReconnect(false)
        runtime.invokeStartBridge(
            bridge = FakeThreeArgOptionsBridge::class.java,
            configClass = configClass,
            configInstance = configInstance,
            baseDir = "/tmp/gomtm-test",
            config = SwarmNodeConfig(bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test", autoReconnect = false),
        )

        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", FakeThreeArgOptionsBridge.lastBootstrapAddr)
        assertEquals(false, FakeThreeArgOptionsBridge.lastAutoReconnect)
    }

    class FakeConfig {
        fun setBootstrapAddr(@Suppress("UNUSED_PARAMETER") value: String) = Unit
        fun setAutoReconnect(@Suppress("UNUSED_PARAMETER") value: Boolean) = Unit
    }

    class FakeNodeBridge {
        companion object {
            @JvmField
            var lastRemoteControlResponse: String = ""

            @JvmField
            var lastAccessibilityPermission: String = ""

            @JvmField
            var lastScreenCapturePermission: String = ""

            @JvmField
            var lastScreenStreamState: String = ""

            @JvmField
            var lastScreenStreamReason: String = ""

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

            @JvmStatic
            fun pollRemoteControlRequest(@Suppress("UNUSED_PARAMETER") timeoutMs: Long): String =
                "{\"request_id\":\"req-1\",\"command\":\"screen.snapshot\",\"params\":{\"format\":\"png\"}}"

            @JvmStatic
            fun resolveRemoteControlResponse(responseJson: String) {
                lastRemoteControlResponse = responseJson
            }

            @JvmStatic
            fun setRemoteControlPermissionState(accessibility: String, screenCapture: String) {
                lastAccessibilityPermission = accessibility
                lastScreenCapturePermission = screenCapture
            }

            @JvmStatic
            fun setRemoteControlStreamState(state: String, reason: String) {
                lastScreenStreamState = state
                lastScreenStreamReason = reason
            }
        }
    }

    class FakeTwoArgOptionsBridge {
        companion object {
            @JvmField
            var lastBootstrapAddr: String = ""

            @JvmStatic
            fun startNodeWithOptions(@Suppress("UNUSED_PARAMETER") baseDir: String, bootstrapAddr: String) {
                lastBootstrapAddr = bootstrapAddr
            }
        }
    }

    class FakeThreeArgOptionsBridge {
        companion object {
            @JvmField
            var lastBootstrapAddr: String = ""

            @JvmField
            var lastAutoReconnect: Boolean? = null

            @JvmStatic
            fun startNodeWithOptions(
                @Suppress("UNUSED_PARAMETER") baseDir: String,
                bootstrapAddr: String,
                autoReconnect: Boolean,
            ) {
                lastBootstrapAddr = bootstrapAddr
                lastAutoReconnect = autoReconnect
            }
        }
    }
}
