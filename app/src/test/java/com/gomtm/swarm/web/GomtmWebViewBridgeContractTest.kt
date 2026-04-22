package com.gomtm.swarm.web

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GomtmWebViewBridgeContractTest {
    @Test
    fun bridgeExposesHostPrimitivesOnly() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/web/GomtmWebViewBridge.kt")

        assertTrue(source.contains("@JavascriptInterface"))
        assertTrue(Regex("""@JavascriptInterface\s+fun getHostInfo\(\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun getConnectionConfig\(\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun getRuntimeSnapshot\(\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun listDiscoveredPeers\(\)""").containsMatchIn(source))
        assertFalse(Regex("""@JavascriptInterface\s+fun getPeerCapabilities\(peerId: String\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun saveConnectionConfig\(payloadJson: String\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun requestScreenCapture\(\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun requestRuntimeRestart\(\)""").containsMatchIn(source))
        assertFalse(source.contains("@JavascriptInterface fun startBrowserRuntime("))
        assertFalse(source.contains("@JavascriptInterface fun openAndroidPage("))
    }

    @Test
    fun saveConnectionConfigRestartsRuntimeWithSavedAddress() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/web/GomtmWebViewBridge.kt")

        assertTrue(
            "bridge should hand the newly parsed address into the restart callback",
            source.contains("onRuntimeRestartRequested(parsed.connectionAddress)"),
        )
        assertFalse(
            "bridge should not restart runtime without passing the saved address",
            source.contains("onRuntimeRestartRequested()"),
        )
    }

    @Test
    fun bridgePublishesHostRuntimePayloadShapeOnly() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/web/GomtmWebViewBridge.kt")

        assertTrue(source.contains("put(\"status\""))
        assertTrue(source.contains("put(\"currentNode\""))
        assertTrue(source.contains("put(\"activeConnectionAddr\""))
        assertTrue(source.contains("put(\"serverUrl\""))
        assertTrue(source.contains("put(\"multiaddrs\""))
        assertTrue(source.contains("put(\"lastDiscoveredAt\""))
        assertFalse(source.contains("put(\"node\""))
        assertFalse(source.contains("android.native_remote_v2_webrtc"))
        assertFalse(source.contains("target_peer_capability_truth_unavailable"))
    }

    @Test
    fun bridgeDoesNotKeepDeadTrimTextHelper() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/web/GomtmWebViewBridge.kt")

        assertFalse(source.contains("private fun trimText("))
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
}
