package com.gomtm.swarm.platform.remote

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRemoteControlBridgeLoopTest {
    @Test
    fun bridgeLoopDoesNotSilentlyReflectMissingNativeApi() {
        val source = String(Files.readAllBytes(resolveProjectPath("app/src/main/java/com/gomtm/swarm/platform/remote/NativeRemoteControlBridgeLoop.kt")))

        assertFalse(source.contains("getMethod("))
        assertFalse(source.contains("getOrNull()"))
        assertFalse(source.contains("?: return \"\""))
    }

    @Test
    fun resolvesPendingPythonStatusRequestThroughBridgeLoop() {
        val bridgeApi = FakeBridgeApi()
        bridgeApi.enqueue(
            """
            {"request_id":"req-py-status","command":"runtime.python.status","params":{}}
            """.trimIndent(),
        )

        val context = FakeContext()
        val loop = NativeRemoteControlBridgeLoop(
            bridgeApi = bridgeApi,
            opsFactory = { AndroidRemoteControlOps(context) },
            webRtcHostFactory = { AndroidWebRtcScreenHost.forContext(context) },
        )

        loop.start()
        val response = bridgeApi.awaitResolvedResponse()
        loop.stop()

        assertTrue(response.contains("\"request_id\":\"req-py-status\""))
        assertTrue(response.contains("\"ok\":true"))
        assertTrue(response.contains("installed"))
        assertTrue(response.contains("message"))
    }

    private class FakeContext : ContextWrapper(null) {
        private val filesRoot = createTempDir(prefix = "gomtm-android-bridge-test-")

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesRoot
    }

    private class FakeBridgeApi : NativeRemoteControlBridgeApi {
        private val requests = LinkedBlockingQueue<String>()
        private val responses = LinkedBlockingQueue<String>()

        fun enqueue(rawRequest: String) {
            requests.offer(rawRequest)
        }

        fun awaitResolvedResponse(): String {
            return requireNotNull(responses.poll(5, TimeUnit.SECONDS)) { "timed out waiting for resolved response" }
        }

        override fun pollRemoteControlRequest(timeoutMs: Int): String {
            return requests.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: ""
        }

        override fun resolveRemoteControlResponse(responseJson: String) {
            responses.offer(responseJson)
        }
    }

    private fun resolveProjectPath(relative: String): Path {
        val candidates = listOf(
            Paths.get(relative),
            Paths.get("../$relative"),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("path not found: $relative")
    }
}
