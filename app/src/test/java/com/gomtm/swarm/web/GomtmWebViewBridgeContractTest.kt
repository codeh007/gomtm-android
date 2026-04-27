package com.gomtm.swarm.web

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GomtmWebViewBridgeContractTest {
    @Test
    fun bridgeExposesOnlyMinimalHostPrimitives() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/web/GomtmWebViewBridge.kt")

        assertTrue(source.contains("@JavascriptInterface"))
        assertTrue(Regex("""@JavascriptInterface\s+fun getHostInfo\(\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun startDeviceService\(\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun stopDeviceService\(\)""").containsMatchIn(source))
        assertTrue(Regex("""@JavascriptInterface\s+fun requestScreenCapture\(\)""").containsMatchIn(source))
        assertFalse(Regex("""@JavascriptInterface\s+fun getConnectionConfig\(\)""").containsMatchIn(source))
        assertFalse(Regex("""@JavascriptInterface\s+fun getRuntimeSnapshot\(\)""").containsMatchIn(source))
        assertFalse(Regex("""@JavascriptInterface\s+fun listDiscoveredPeers\(\)""").containsMatchIn(source))
        assertFalse(Regex("""@JavascriptInterface\s+fun saveConnectionConfig\(payloadJson: String\)""").containsMatchIn(source))
        assertFalse(Regex("""@JavascriptInterface\s+fun requestRuntimeRestart\(\)""").containsMatchIn(source))
    }

    @Test
    fun bridgeDoesNotExposeLegacyRuntimePresencePayloads() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/web/GomtmWebViewBridge.kt")

        assertFalse(source.contains("currentNode"))
        assertFalse(source.contains("activeConnectionAddr"))
        assertFalse(source.contains("serverUrl"))
        assertFalse(source.contains("multiaddrs"))
        assertFalse(source.contains("lastDiscoveredAt"))
        assertFalse(source.contains("peer_candidates_ready"))
        assertFalse(source.contains("discovering"))
        assertFalse(source.contains("RuntimeSnapshot"))
        assertFalse(source.contains("DiscoveredPeer"))
    }

    @Test
    fun bridgeReportsHostMetadataOnly() {
        val source = readProjectFile("app/src/main/java/com/gomtm/swarm/web/GomtmWebViewBridge.kt")

        assertTrue(source.contains("hostKind"))
        assertTrue(source.contains("packageName"))
        assertTrue(source.contains("appVersion"))
        assertTrue(source.contains("dashP2pUrl"))
        assertTrue(source.contains("canStartDeviceService"))
        assertTrue(source.contains("canStopDeviceService"))
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
