package com.gomtm.android.web

import com.gomtm.android.swarm.DiscoveredPeer
import com.gomtm.android.swarm.RemoteControlPermissionState
import com.gomtm.android.swarm.SwarmNodeConfig
import com.gomtm.android.swarm.SwarmStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GomtmHostBridgeTest {
    @Test
    fun returnsRuntimeSnapshotWithStoredConfigAndPermissionState() {
        val runtime = FakeRuntimeHost()
        val settingsStore = InMemorySettingsStore(
            HostSettings(
                bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/bootstrap",
                nodeName = "android-cloud-a",
                consoleUrl = "https://gomtm.example.com",
            ),
        )
        val bridge = GomtmHostBridge(
            runtimeHost = runtime,
            settingsStore = settingsStore,
            launchAccessibilitySettings = {},
            requestScreenCapturePermissionAction = { true },
            navigateToUrl = {},
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
        val settingsStore = InMemorySettingsStore(HostSettings())
        val bridge = GomtmHostBridge(
            runtimeHost = runtime,
            settingsStore = settingsStore,
            launchAccessibilitySettings = {},
            requestScreenCapturePermissionAction = { true },
            navigateToUrl = {},
        )

        val snapshot = JSONObject(
            bridge.startNode(
                """
                {"bootstrap_address":"/ip4/156.225.19.101/tcp/4101/p2p/relay","node_name":"android-cloud-b","console_url":"gomtm.example.com"}
                """.trimIndent(),
            ),
        )

        assertEquals("/ip4/156.225.19.101/tcp/4101/p2p/relay", settingsStore.current.bootstrapAddress)
        assertEquals("android-cloud-b", settingsStore.current.nodeName)
        assertEquals("gomtm.example.com", settingsStore.current.consoleUrl)
        assertEquals(1, runtime.startedConfigs.size)
        assertEquals("android-cloud-b", runtime.startedConfigs.single().nodeName)
        assertEquals("Starting", snapshot.getJSONObject("runtime").getString("state"))
    }

    @Test
    fun savesHostSettingsWithoutStartingNode() {
        val settingsStore = InMemorySettingsStore(HostSettings())
        val bridge = GomtmHostBridge(
            runtimeHost = FakeRuntimeHost(),
            settingsStore = settingsStore,
            launchAccessibilitySettings = {},
            requestScreenCapturePermissionAction = { true },
            navigateToUrl = {},
        )

        val snapshot = JSONObject(
            bridge.saveHostSettings(
                """
                {"bootstrap_address":"/ip4/203.0.113.1/tcp/4101/p2p/relay","node_name":"android-save-only","console_url":"http://10.0.2.2:3700"}
                """.trimIndent(),
            ),
        )

        assertEquals("/ip4/203.0.113.1/tcp/4101/p2p/relay", settingsStore.current.bootstrapAddress)
        assertEquals("android-save-only", settingsStore.current.nodeName)
        assertEquals("http://10.0.2.2:3700", settingsStore.current.consoleUrl)
        assertEquals("android-save-only", snapshot.getJSONObject("config").getString("node_name"))
    }

    @Test
    fun resolvesHostNavigationUrlAndAppendsDashP2PWhenOnlyOriginIsProvided() {
        assertEquals("https://gomtm.example.com/dash/p2p", resolveHostNavigationUrl("gomtm.example.com"))
        assertEquals("http://10.0.2.2:3700/dash/p2p", resolveHostNavigationUrl("http://10.0.2.2:3700"))
        assertEquals(
            "https://gomtm.example.com/dash/p2p?tab=host",
            resolveHostNavigationUrl("https://gomtm.example.com?tab=host"),
        )
    }

    @Test
    fun openConsoleUrlNormalizesAndDelegatesNavigation() {
        var navigatedTo = ""
        val bridge = GomtmHostBridge(
            runtimeHost = FakeRuntimeHost(),
            settingsStore = InMemorySettingsStore(HostSettings()),
            launchAccessibilitySettings = {},
            requestScreenCapturePermissionAction = { true },
            navigateToUrl = { navigatedTo = it },
        )

        val result = JSONObject(bridge.openConsoleUrl("gomtm.example.com"))

        assertEquals(true, result.getBoolean("ok"))
        assertEquals("https://gomtm.example.com/dash/p2p", result.getString("url"))
        assertEquals("https://gomtm.example.com/dash/p2p", navigatedTo)
    }

    @Test
    fun rejectsInvalidConsoleUrl() {
        var navigated = false
        val bridge = GomtmHostBridge(
            runtimeHost = FakeRuntimeHost(),
            settingsStore = InMemorySettingsStore(HostSettings()),
            launchAccessibilitySettings = {},
            requestScreenCapturePermissionAction = { true },
            navigateToUrl = { navigated = true },
        )

        val result = JSONObject(bridge.openConsoleUrl("javascript:alert(1)"))

        assertEquals(false, result.getBoolean("ok"))
        assertEquals("invalid_console_url", result.getString("error"))
        assertEquals(false, navigated)
    }

    @Test
    fun delegatesAccessibilitySettingsToHostCallback() {
        var opened = false
        val bridge = GomtmHostBridge(
            runtimeHost = FakeRuntimeHost(),
            settingsStore = InMemorySettingsStore(HostSettings()),
            launchAccessibilitySettings = { opened = true },
            requestScreenCapturePermissionAction = { true },
            navigateToUrl = {},
        )

        assertTrue(bridge.openAccessibilitySettings())
        assertTrue(opened)
    }

    @Test
    fun delegatesScreenCapturePermissionRequestToHostCallback() {
        var requested = false
        val bridge = GomtmHostBridge(
            runtimeHost = FakeRuntimeHost(),
            settingsStore = InMemorySettingsStore(HostSettings()),
            launchAccessibilitySettings = {},
            requestScreenCapturePermissionAction = {
                requested = true
                true
            },
            navigateToUrl = {},
        )

        assertTrue(bridge.requestScreenCapturePermission())
        assertTrue(requested)
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

        override fun drainLogs(): String = "bootstrap ok"

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

    private class InMemorySettingsStore(initial: HostSettings) : HostSettingsStore {
        var current: HostSettings = initial

        override fun load(): HostSettings = current

        override fun save(settings: HostSettings) {
            current = settings
        }
    }
}
