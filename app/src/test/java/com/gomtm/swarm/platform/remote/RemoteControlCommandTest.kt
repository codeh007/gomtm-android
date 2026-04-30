package com.gomtm.swarm.platform.remote

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import java.io.File
import com.gomtm.swarm.shell.NodeRuntimeProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteControlCommandTest {
    @Test
    fun webRtcSessionTruthStartsIdleInsteadOfPlaceholderState() {
        val state = AndroidWebRtcScreenHost.forContext(FakeContext()).currentState()

        assertEquals("idle", state.state)
        assertTrue(state.topology.isNullOrBlank())
        assertTrue(state.sessionId.isNullOrBlank())
    }

    private class FakeContext : ContextWrapper(null) {
        private val filesRoot = createTempDir(prefix = "gomtm-android-test-")
        private val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = File(filesRoot, "native-lib").absolutePath
        }

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesRoot

        override fun getPackageCodePath(): String = File(filesRoot, "packages/base.apk").absolutePath

        override fun getApplicationInfo(): ApplicationInfo = appInfo
    }

    @Test
    fun parsesScreenSnapshotRequest() {
        val request = parseRemoteControlRequest(
            """
            {"request_id":"req-1","command":"screen.snapshot","params":{"format":"png"}}
            """.trimIndent(),
        )

        assertEquals("req-1", request?.requestId)
        assertEquals("screen.snapshot", request?.command)
        assertEquals("png", request?.params?.get("format"))
    }

    @Test
    fun reportsPermissionDeniedWhenScreenshotUnavailable() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-2", "screen.snapshot", mapOf("format" to "png")),
            ops = object : RemoteControlOps {
                override fun screenSnapshot(format: String): RemoteControlCommandResult<RemoteControlScreenshotPayload> {
                    return RemoteControlCommandResult.Error(
                        code = "SB_PERMISSION_REQUIRED",
                        message = "screen capture permission missing",
                        retryable = false,
                    )
                }

                override fun screenStreamEnsure(): RemoteControlCommandResult<RemoteControlScreenStreamPayload> =
                    RemoteControlCommandResult.Error("SB_PERMISSION_REQUIRED", "screen capture permission missing", false)

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

        assertFalse(response.ok)
        assertEquals("SB_PERMISSION_REQUIRED", response.errorCode)
        assertEquals("screen capture permission missing", response.errorMessage)
    }

    @Test
    fun reportsOkForInputTapCommand() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-3", "input.tap", mapOf("x" to 120, "y" to 240)),
            ops = object : RemoteControlOps {
                override fun screenSnapshot(format: String): RemoteControlCommandResult<RemoteControlScreenshotPayload> =
                    RemoteControlCommandResult.Error("SB_CAPABILITY_UNAVAILABLE", "unsupported", false)

                override fun screenStreamEnsure(): RemoteControlCommandResult<RemoteControlScreenStreamPayload> =
                    RemoteControlCommandResult.Error("SB_CAPABILITY_UNAVAILABLE", "unsupported", false)

                override fun screenStreamStop(): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputTap(x: Int, y: Int): RemoteControlCommandResult<RemoteControlActionPayload> {
                    return RemoteControlCommandResult.Success(RemoteControlActionPayload(status = "ok", message = "tap complete"))
                }

                override fun inputSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputText(text: String): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())

                override fun inputKey(key: String): RemoteControlCommandResult<RemoteControlActionPayload> =
                    RemoteControlCommandResult.Success(RemoteControlActionPayload())
            },
        )

        assertTrue(response.ok)
        assertEquals("req-3", response.requestId)
        assertTrue(response.payloadJson?.contains("tap complete") == true)
    }

    @Test
    fun reportsOkForScreenStreamEnsureCommand() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-stream-1", "screen.stream.ensure"),
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

        assertTrue(response.ok)
        assertTrue(response.payloadJson?.contains("video_h264_annexb") == true)
        assertTrue(response.payloadJson?.contains("avc1.64001f") == true)
    }

    @Test
    fun routesDemoNodejsPingWithoutOpsMutation() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-node-ping", "demo.nodejs.ping"),
            ops = AndroidRemoteControlOps(FakeContext()),
        )

        assertTrue(response.ok)
        assertTrue(response.payloadJson?.contains("\"step\":\"ping\"") == true)
    }

    @Test
    fun routesDemoNodejsRuntimeThroughProbe() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-node-runtime", "demo.nodejs.ping"),
            ops = AndroidRemoteControlOps(FakeContext()),
        )

        assertTrue(response.ok)
        assertTrue(response.payloadJson?.contains("\"step\":\"ping\"") == true)
    }

    @Test
    fun rejectsUnknownPythonRuntimeProbeCommandNames() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-py-1", "runtime.python.probe", mapOf("probe" to "embedded-smoke")),
            ops = AndroidRemoteControlOps(FakeContext()),
        )

        assertFalse(response.ok)
    }

    @Test
    fun pythonRuntimeStatusReturnsStructuredPayload() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-py-status", "runtime.python.status"),
            ops = AndroidRemoteControlOps(FakeContext()),
        )

        assertTrue(response.ok)
        assertTrue(response.payloadJson?.contains("installed") == true)
        assertTrue(response.payloadJson?.contains("message") == true)
    }

    @Test
    fun pythonRuntimeVersionProbeReturnsStructuredFailureBeforeInstall() {
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-py-version", "runtime.python.probe", mapOf("probe" to "version")),
            ops = AndroidRemoteControlOps(FakeContext()),
        )

        assertTrue(response.ok)
        assertTrue(response.payloadJson?.contains("\"probe\":\"version\"") == true)
        assertTrue(response.payloadJson?.contains("python runtime not installed") == true)
    }

    @Test
    fun pythonRuntimeInstallReturnsStructuredPayload() {
        val context = FakeContext()
        val response = handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-py-install", "runtime.python.install"),
            ops = AndroidRemoteControlOps(context),
        )

        assertTrue(response.ok)
        assertTrue(response.payloadJson?.contains("installed") == true)
        assertTrue(response.payloadJson?.contains("message") == true)

        val wrapper = File(context.filesDir, "runtime/python/releases/3.14.4-aarch64/bin/python-real").readText()
        assertTrue(wrapper.contains(context.packageCodePath))
        assertTrue(wrapper.contains(context.applicationInfo.nativeLibraryDir))
    }

    @Test
    fun pythonRuntimeInstallDoesNotUseHardCodedLegacyApkPaths() {
        val context = FakeContext()

        handleRemoteControlRequest(
            request = RemoteControlCommandRequest("req-py-install-paths", "runtime.python.install"),
            ops = AndroidRemoteControlOps(context),
        )

        val wrapper = File(context.filesDir, "runtime/python/releases/3.14.4-aarch64/bin/python-real").readText()
        assertFalse(wrapper.contains("/data/app/com.gomtm.swarm/base.apk"))
        assertFalse(wrapper.contains("/data/app/com.gomtm.swarm/lib/arm64"))
    }
}
