package com.gomtm.android.web

import com.gomtm.android.swarm.DiscoveredPeer
import com.gomtm.android.swarm.RemoteControlPermissionState
import com.gomtm.android.swarm.SwarmNodeConfig
import com.gomtm.android.swarm.SwarmStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GomtmWebViewBridgeTest {
    @Test
    fun returnsRuntimeSnapshotWithStoredConfigAndPermissionState() {
        val runtime = FakeRuntimeHost()
        val settingsStore = InMemorySettingsStore(
            WebConsoleSettings(
                bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/bootstrap",
                nodeName = "android-cloud-a",
                consoleUrl = "https://gomtm.example.com",
            ),
        )
        val bridge = GomtmWebViewBridge(
            runtimeHost = runtime,
            settingsStore = settingsStore,
            openAccessibilitySettings = {},
        )

        val snapshot = JSONObject(bridge.getRuntimeSnapshot())

        assertEquals("android_webview", snapshot.getJSONObject("host").getString("surface"))
        assertEquals("android-cloud-a", snapshot.getJSONObject("config").getString("node_name"))
        assertEquals("Ready", snapshot.getJSONObject("runtime").getString("state"))
        assertEquals("granted", snapshot.getJSONObject("runtime").getJSONObject("permissions").getString("accessibility"))
        assertEquals(1, snapshot.getJSONObject("runtime").getJSONArray("discovered_peers").length())
    }

    @Test
    fun startNodePersistsRequestedConfigAndReturnsUpdatedSnapshot() {
        val runtime = FakeRuntimeHost()
        val settingsStore = InMemorySettingsStore(WebConsoleSettings())
        val bridge = GomtmWebViewBridge(
            runtimeHost = runtime,
            settingsStore = settingsStore,
            openAccessibilitySettings = {},
        )

        val snapshot = JSONObject(
            bridge.startNode(
                """
                {"bootstrap_address":"/ip4/156.225.19.101/tcp/4101/p2p/relay","node_name":"android-cloud-b"}
                """.trimIndent(),
            ),
        )

        assertEquals("/ip4/156.225.19.101/tcp/4101/p2p/relay", settingsStore.current.bootstrapAddress)
        assertEquals("android-cloud-b", settingsStore.current.nodeName)
        assertEquals(1, runtime.startedConfigs.size)
        assertEquals("android-cloud-b", runtime.startedConfigs.single().nodeName)
        assertEquals("Starting", snapshot.getJSONObject("runtime").getString("state"))
    }

    @Test
    fun resolvesLaunchUrlAndAppendsDashP2PWhenOnlyOriginIsProvided() {
        assertEquals("https://gomtm.example.com/dash/p2p", resolveWebConsoleLaunchUrl("gomtm.example.com"))
        assertEquals("http://10.0.2.2:3700/dash/p2p", resolveWebConsoleLaunchUrl("http://10.0.2.2:3700"))
        assertEquals(
            "https://gomtm.example.com/dash/p2p?tab=host",
            resolveWebConsoleLaunchUrl("https://gomtm.example.com?tab=host"),
        )
    }

    @Test
    fun delegatesAccessibilitySettingsToHostCallback() {
        var opened = false
        val bridge = GomtmWebViewBridge(
            runtimeHost = FakeRuntimeHost(),
            settingsStore = InMemorySettingsStore(WebConsoleSettings()),
            openAccessibilitySettings = { opened = true },
        )

        assertTrue(bridge.openAccessibilitySettings())
        assertTrue(opened)
    }

    private class FakeRuntimeHost : SwarmRuntimeHost {
        val startedConfigs = mutableListOf<SwarmNodeConfig>()
        private var state: String = "Ready"

        override fun start(config: SwarmNodeConfig) {
            startedConfigs += config
            state = "Starting"
        }

        override fun stop() {
            state = "Stopped"
        }

        override fun probe(): SwarmStatus = SwarmStatus(
            bridgeClassName = "io.nekohasekai.p2pandroid.P2pandroid",
            state = state,
            peerId = "12D3KooW-test",
            bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/bootstrap",
            lastError = "",
            discoveredPeers = listOf(
                DiscoveredPeer(
                    peerId = "12D3KooW-peer-b",
                    name = "android-peer-b",
                    state = "connected",
                    discoveredInCurrentSession = true,
                    isBootstrap = false,
                    lastSeenAt = "2026-03-31T12:00:00Z",
                ),
            ),
            rawDiscoveredPeers = "",
        )

        override fun remoteControlPermissionState(): RemoteControlPermissionState {
            return RemoteControlPermissionState(accessibility = "granted", screenCapture = "unsupported")
        }
    }

    private class InMemorySettingsStore(initial: WebConsoleSettings) : WebConsoleSettingsStore {
        var current: WebConsoleSettings = initial

        override fun load(): WebConsoleSettings = current

        override fun save(settings: WebConsoleSettings) {
            current = settings
        }
    }
}
