package com.gomtm.swarm.runtime

import com.gomtm.swarm.platform.remote.RemoteControlActionPayload
import com.gomtm.swarm.platform.remote.RemoteControlCapabilityState
import com.gomtm.swarm.platform.remote.RemoteControlCommandResult
import com.gomtm.swarm.platform.remote.RemoteControlOps
import com.gomtm.swarm.platform.remote.RemoteControlPermissionState
import com.gomtm.swarm.platform.remote.RemoteControlScreenStreamPayload
import com.gomtm.swarm.platform.remote.RemoteControlScreenshotPayload
import com.gomtm.swarm.platform.remote.RemoteControlStreamChannelPayload
import com.gomtm.swarm.platform.remote.RemoteControlStreamResolvedTarget
import com.gomtm.swarm.platform.remote.RemoteControlWebRtcSessionState
import com.gomtm.swarm.platform.remote.RemoteControlWebRtcStartPayload
import com.gomtm.swarm.platform.remote.WebRtcScreenHost
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwarmRuntimeTest {
    @Test
    fun exposesMissingBridgeAsErrorState() {
        val runtime = GomtmRuntimeFacade(
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
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )
        FakeNodeBridge.discoveredPeersJson =
            """
            {"schema_version":"v1","generated_at":"2026-03-27T10:00:00Z","peers":[{"peer_id":"test","name":"seed-peer","state":"registered","discovered_in_current_session":true,"last_seen_at":"2026-03-27T10:00:00Z"},{"peer_id":"peer-b","name":"android-b","state":"registered","discovered_in_current_session":true,"last_seen_at":"2026-03-27T10:00:00Z"}]}
            """.trimIndent()

        val status = runtime.probe()

        assertEquals("Registered", status.state)
        assertEquals("peer-123", status.peerId)
        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", status.connectionAddress)
        assertEquals(2, status.discoveredPeers.size)
        assertEquals("peer-b", status.discoveredPeers.last().peerId)
    }

    @Test
    fun keepsRuntimeSnapshotRegisteredWhenSeedObservationIsMissing() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )
        FakeNodeBridge.discoveredPeersJson =
            """
            {"schema_version":"v1","generated_at":"2026-03-27T10:00:00Z","peers":[{"peer_id":"peer-b","name":"android-b","state":"registered","discovered_in_current_session":true,"last_seen_at":"2026-03-27T10:00:00Z"}]}
            """.trimIndent()

        val status = runtime.probe()

        assertEquals("Registered", status.state)
        assertEquals("", status.lastError)
    }

    @Test
    fun exposesLastAutoRestartObservationFromForegroundService() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )
        com.gomtm.swarm.platform.lifecycle.GomtmForegroundService.getLastDegradedRestartReason()
        val bridgeClass = com.gomtm.swarm.platform.lifecycle.GomtmForegroundService::class.java
        bridgeClass.getDeclaredField("lastKnownDegradedRestartAtMs").apply {
            isAccessible = true
            setLong(null, 123456789L)
        }
        bridgeClass.getDeclaredField("lastKnownDegradedRestartReason").apply {
            isAccessible = true
            set(null, "connection observation stale")
        }

        val status = runtime.probe()

        assertEquals(123456789L, status.lastAutoRestartAtMs)
        assertEquals("connection observation stale", status.lastAutoRestartReason)
    }

    @Test
    fun parsesDiscoveredPeersSnapshot() {
        val json =
            """
            {"schema_version":"v1","generated_at":"2026-03-27T10:00:00Z","peers":[{"peer_id":"peer-b","name":"android-b","state":"registered","discovered_in_current_session":true,"last_seen_at":"2026-03-27T10:00:00Z"}]}
            """.trimIndent()

        val peers = DiscoveredPeer.parseSnapshot(json)

        assertEquals(1, peers.size)
        assertEquals("peer-b", peers.single().peerId)
        assertEquals("android-b", peers.single().name)
    }

    @Test
    fun readsRemoteControlRequestFromBridge() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        val request = runtime.pollRemoteControlRequest(timeoutMs = 0)

        assertTrue(request.contains("screen.snapshot"))
    }

    @Test
    fun forwardsRemoteControlResponseAndPermissionStateToBridge() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        runtime.resolveRemoteControlResponse("{\"request_id\":\"req-1\",\"ok\":true}")
        runtime.setRemoteControlPermissionState("granted", "unsupported")
        runtime.setRemoteControlStreamState("streaming", "")
        runtime.setRemoteControlWebRTCState("connecting", "signaling_starting", "sess-webrtc-1", "")

        assertEquals("{\"request_id\":\"req-1\",\"ok\":true}", FakeNodeBridge.lastRemoteControlResponse)
        assertEquals("granted", FakeNodeBridge.lastAccessibilityPermission)
        assertEquals("unsupported", FakeNodeBridge.lastScreenCapturePermission)
        assertEquals("streaming", FakeNodeBridge.lastScreenStreamState)
        assertEquals("connecting", FakeNodeBridge.lastScreenWebRtcState)
        assertEquals("signaling_starting", FakeNodeBridge.lastScreenWebRtcTopology)
        assertEquals("sess-webrtc-1", FakeNodeBridge.lastScreenWebRtcSessionId)
    }

    @Test
    fun publishesIdleDeviceOwnedWebRtcSessionAsAvailableCapability() {
        FakeNodeBridge.lastScreenWebRtcState = ""
        FakeNodeBridge.lastScreenWebRtcTopology = "stale"
        FakeNodeBridge.lastScreenWebRtcSessionId = "stale"
        FakeNodeBridge.lastScreenWebRtcReason = "stale"

        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        runtime.publishRemoteControlTickForTest(
            permissionState = RemoteControlPermissionState(accessibility = "granted", screenCapture = "granted"),
            streamState = RemoteControlCapabilityState(state = "streaming"),
            webRtcSessionState = RemoteControlWebRtcSessionState(state = "idle"),
        )

        assertEquals("available", FakeNodeBridge.lastScreenWebRtcState)
        assertEquals("", FakeNodeBridge.lastScreenWebRtcTopology)
        assertEquals("", FakeNodeBridge.lastScreenWebRtcSessionId)
        assertEquals("", FakeNodeBridge.lastScreenWebRtcReason)
    }

    @Test
    fun handlesQueuedRemoteControlRequestAndResolvesBridgeResponse() {
        FakeNodeBridge.queuedRemoteControlRequest =
            """{"request_id":"req-stream-42","command":"screen.stream.ensure","params":{}}"""
        FakeNodeBridge.lastRemoteControlResponse = ""

        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        runtime.processRemoteControlRequestForTest(
            requestJson = runtime.pollRemoteControlRequest(timeoutMs = 0),
            ops = object : RemoteControlOps {
                override fun screenSnapshot(format: String): RemoteControlCommandResult<RemoteControlScreenshotPayload> =
                    RemoteControlCommandResult.Error("SB_CAPABILITY_UNAVAILABLE", "unsupported", false)

                override fun screenStreamEnsure(): RemoteControlCommandResult<RemoteControlScreenStreamPayload> =
                    RemoteControlCommandResult.Success(
                        RemoteControlScreenStreamPayload(
                            status = "streaming",
                            resolved = RemoteControlStreamResolvedTarget(
                                kind = "loopback_tcp",
                                host = "127.0.0.1",
                                port = 9200,
                                protocolHint = "tcp",
                                serviceHint = "android_media_projection_h264",
                            ),
                            channel = RemoteControlStreamChannelPayload(
                                kind = "video_h264_annexb",
                                framing = "length_prefixed_access_units",
                                codec = "avc1.64001f",
                                width = 1080,
                                height = 1920,
                                rotation = 0,
                                keyframeRequiredOnStart = true,
                            ),
                        ),
                    )

                override fun screenStreamStop(): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputTap(x: Int, y: Int): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputText(text: String): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputKey(key: String): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())
            },
        )

        assertTrue(FakeNodeBridge.lastRemoteControlResponse.contains("\"request_id\":\"req-stream-42\""))
        assertTrue(FakeNodeBridge.lastRemoteControlResponse.contains("\"ok\":true"))
        assertTrue(FakeNodeBridge.lastRemoteControlResponse.contains("video_h264_annexb"))
    }

    @Test
    fun handlesQueuedWebRtcStartRequestAndResolvesBridgeResponse() {
        FakeNodeBridge.queuedRemoteControlRequest =
            """{"request_id":"req-webrtc-1","command":"screen.webrtc.start","params":{}}"""
        FakeNodeBridge.lastRemoteControlResponse = ""

        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        runtime.processRemoteControlRequestForTest(
            requestJson = runtime.pollRemoteControlRequest(timeoutMs = 0),
            ops = object : RemoteControlOps {
                override fun screenSnapshot(format: String): RemoteControlCommandResult<RemoteControlScreenshotPayload> =
                    RemoteControlCommandResult.Error("SB_CAPABILITY_UNAVAILABLE", "unsupported", false)

                override fun screenStreamEnsure(): RemoteControlCommandResult<RemoteControlScreenStreamPayload> =
                    RemoteControlCommandResult.Error("SB_CAPABILITY_UNAVAILABLE", "unsupported", false)

                override fun screenStreamStop(): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputTap(x: Int, y: Int): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputText(text: String): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputKey(key: String): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())
            },
            webRtcHost = object : WebRtcScreenHost {
                override fun start(): RemoteControlCommandResult<RemoteControlWebRtcStartPayload> =
                    RemoteControlCommandResult.Success(
                        RemoteControlWebRtcStartPayload(
                            sessionId = "sess-webrtc-1",
                            state = "connecting",
                        ),
                    )

                override fun stop() = Unit

                override fun currentState(): RemoteControlWebRtcSessionState =
                    RemoteControlWebRtcSessionState(
                        state = "connecting",
                        topology = "signaling_starting",
                        sessionId = "sess-webrtc-1",
                    )
            },
        )

        assertTrue(FakeNodeBridge.lastRemoteControlResponse.contains("\"request_id\":\"req-webrtc-1\""))
        assertTrue(FakeNodeBridge.lastRemoteControlResponse.contains("sess-webrtc-1"))
        assertTrue(FakeNodeBridge.lastRemoteControlResponse.contains("connecting"))
    }

    @Test
    fun startSupportsTwoArgStartNodeWithOptionsBridge() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeTwoArgOptionsBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeTwoArgOptionsBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

		val configClass = FakeConfig::class.java
        val configInstance = configClass.getDeclaredConstructor().newInstance()
        configInstance.setConnectionAddr("/ip4/127.0.0.1/tcp/4101/p2p/test")
        configInstance.setAutoReconnect(true)
        runtime.invokeStartBridge(
            bridge = FakeTwoArgOptionsBridge::class.java,
            configClass = configClass,
            configInstance = configInstance,
            baseDir = "/tmp/gomtm-test",
            config = RuntimeLaunchConfig(connectionAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test"),
        )

        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", FakeTwoArgOptionsBridge.lastConnectionAddr)
    }

    @Test
    fun startSupportsThreeArgStartNodeWithOptionsBridge() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeThreeArgOptionsBridge::class.java.name,
            configClassName = FakeConfig::class.java.name,
            classLoader = FakeThreeArgOptionsBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        val configClass = FakeConfig::class.java
        val configInstance = configClass.getDeclaredConstructor().newInstance()
        configInstance.setConnectionAddr("/ip4/127.0.0.1/tcp/4101/p2p/test")
        configInstance.setAutoReconnect(false)
        runtime.invokeStartBridge(
            bridge = FakeThreeArgOptionsBridge::class.java,
            configClass = configClass,
            configInstance = configInstance,
            baseDir = "/tmp/gomtm-test",
            config = RuntimeLaunchConfig(connectionAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test", autoReconnect = false),
        )

        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", FakeThreeArgOptionsBridge.lastConnectionAddr)
        assertEquals(false, FakeThreeArgOptionsBridge.lastAutoReconnect)
    }

    @Test
    fun runtimeFacadeUsesConnectionOnlyReflectionNames() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/runtime/GomtmRuntimeFacade.kt")

        assertFalse(source.contains("SetBootstrapAddr"))
        assertFalse(source.contains("setBootstrapAddr"))
        assertFalse(source.contains("GetBootstrapAddr"))
        assertFalse(source.contains("GetBootstrapAddress"))
        assertFalse(source.contains("getBootstrapAddr"))
        assertFalse(source.contains("getBootstrapAddress"))
        assertTrue(source.contains("SetConnectionAddr"))
        assertTrue(source.contains("GetConnectionAddr"))
    }

    @Test
    fun bundledAarExposesConnectionOnlyBridgeApis() {
        val configClass = readBundledClassText("io/nekohasekai/p2pandroid/Config.class")
        val bridgeClass = readBundledClassText("io/nekohasekai/p2pandroid/P2pandroid.class")

        assertTrue(configClass.contains("ConnectionAddr"))
        assertTrue(configClass.contains("getConnectionAddr"))
        assertTrue(configClass.contains("setConnectionAddr"))
        assertFalse(configClass.contains("BootstrapAddr"))
        assertFalse(configClass.contains("getBootstrapAddr"))
        assertFalse(configClass.contains("setBootstrapAddr"))
        assertTrue(bridgeClass.contains("getConnectionAddr"))
        assertFalse(bridgeClass.contains("getBootstrapAddr"))
    }

    class FakeConfig {
        fun setConnectionAddr(@Suppress("UNUSED_PARAMETER") value: String) = Unit
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

            @JvmField
            var lastScreenWebRtcState: String = ""

            @JvmField
            var lastScreenWebRtcTopology: String = ""

            @JvmField
            var lastScreenWebRtcSessionId: String = ""

            @JvmField
            var lastScreenWebRtcReason: String = ""

            @JvmField
            var queuedRemoteControlRequest: String = "{\"request_id\":\"req-1\",\"command\":\"screen.snapshot\",\"params\":{\"format\":\"png\"}}"

            @JvmStatic
            fun startNode(@Suppress("UNUSED_PARAMETER") baseDir: String, @Suppress("UNUSED_PARAMETER") config: FakeConfig) = Unit

            @JvmStatic
            fun stopNode() = Unit

            @JvmStatic
            fun getState(): String = "Registered"

            @JvmStatic
            fun getPeerID(): String = "peer-123"

            @JvmStatic
            fun getConnectionAddr(): String = "/ip4/127.0.0.1/tcp/4101/p2p/test"

            @JvmStatic
            fun getLastError(): String = ""

            @JvmStatic
            fun getDiscoveredPeers(): String = discoveredPeersJson

            @JvmField
            var discoveredPeersJson: String =
                """
                {"schema_version":"v1","generated_at":"2026-03-27T10:00:00Z","peers":[{"peer_id":"peer-b","name":"android-b","state":"registered","discovered_in_current_session":true,"last_seen_at":"2026-03-27T10:00:00Z"}]}
                """.trimIndent()

            @JvmStatic
            fun drainLogs(): String = "connection ok"

            @JvmStatic
            fun pollRemoteControlRequest(@Suppress("UNUSED_PARAMETER") timeoutMs: Long): String =
                queuedRemoteControlRequest

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

            @JvmStatic
            fun setRemoteControlWebRTCState(state: String, topology: String, sessionId: String, reason: String) {
                lastScreenWebRtcState = state
                lastScreenWebRtcTopology = topology
                lastScreenWebRtcSessionId = sessionId
                lastScreenWebRtcReason = reason
            }
        }
    }

    class FakeTwoArgOptionsBridge {
        companion object {
            @JvmField
            var lastConnectionAddr: String = ""

            @JvmStatic
            fun startNodeWithOptions(@Suppress("UNUSED_PARAMETER") baseDir: String, connectionAddr: String) {
                lastConnectionAddr = connectionAddr
            }
        }
    }

    class FakeThreeArgOptionsBridge {
        companion object {
            @JvmField
            var lastConnectionAddr: String = ""

            @JvmField
            var lastAutoReconnect: Boolean? = null

            @JvmStatic
            fun startNodeWithOptions(
                @Suppress("UNUSED_PARAMETER") baseDir: String,
                connectionAddr: String,
                autoReconnect: Boolean,
            ) {
                lastConnectionAddr = connectionAddr
                lastAutoReconnect = autoReconnect
            }
        }
    }

    private fun readProjectFile(relative: String): String {
        val path = resolveProjectPath(relative)
        return String(Files.readAllBytes(path))
    }

    private fun resolveProjectPath(relative: String): Path {
        val candidates = listOf(
            Paths.get(relative),
            Paths.get("../$relative"),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("path not found: $relative")
    }

    private fun readBundledClassText(classEntry: String): String {
        val aarPath = resolveProjectPath("app/libs/gomtm-swarm-android.aar")
        ZipFile(aarPath.toFile()).use { aar ->
            val classesEntry = aar.getEntry("classes.jar") ?: error("classes.jar missing from bundled AAR")
            val classesBytes = aar.getInputStream(classesEntry).use { it.readBytes() }
            ZipInputStream(ByteArrayInputStream(classesBytes)).use { jar ->
                while (true) {
                    val entry = jar.nextEntry ?: break
                    if (entry.name == classEntry) {
                        return jar.readBytes().toString(Charsets.ISO_8859_1)
                    }
                }
            }
        }
        error("bundled class not found: $classEntry")
    }
}
