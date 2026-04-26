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
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwarmRuntimeTest {
    @Test
    fun readsRemoteControlRequestFromBridge() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
            classLoader = FakeNodeBridge::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
        )

        val request = runtime.pollRemoteControlRequest(timeoutMs = 0)

        assertTrue(request.contains("screen.snapshot"))
    }

    @Test
    fun forwardsRemoteControlResponseAndPermissionStateToBridge() {
        val runtime = GomtmRuntimeFacade(
            bridgeClassName = FakeNodeBridge::class.java.name,
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
}
