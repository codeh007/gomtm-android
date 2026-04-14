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

import org.junit.Assert.assertEquals
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
        configInstance.setBootstrapAddr("/ip4/127.0.0.1/tcp/4101/p2p/test")
        configInstance.setAutoReconnect(true)
        runtime.invokeStartBridge(
            bridge = FakeTwoArgOptionsBridge::class.java,
            configClass = configClass,
            configInstance = configInstance,
            baseDir = "/tmp/gomtm-test",
            config = RuntimeLaunchConfig(bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test"),
        )

        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", FakeTwoArgOptionsBridge.lastBootstrapAddr)
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
        configInstance.setBootstrapAddr("/ip4/127.0.0.1/tcp/4101/p2p/test")
        configInstance.setAutoReconnect(false)
        runtime.invokeStartBridge(
            bridge = FakeThreeArgOptionsBridge::class.java,
            configClass = configClass,
            configInstance = configInstance,
            baseDir = "/tmp/gomtm-test",
            config = RuntimeLaunchConfig(bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test", autoReconnect = false),
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
