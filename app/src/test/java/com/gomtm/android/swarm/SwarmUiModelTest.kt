package com.gomtm.android.swarm

import org.junit.Assert.assertEquals
import org.junit.Test

class SwarmUiModelTest {
    @Test
    fun usesOnlyRealConnectedBootstrapValue() {
        val status = SwarmStatus(
            bridgeClassName = "bridge",
            state = "Idle",
            peerId = "",
            bootstrapAddress = "",
            lastError = "",
            discoveredPeers = emptyList(),
            rawDiscoveredPeers = "",
        )

        val model = SwarmUiModel.from(status = status, actionError = null)

        assertEquals(SwarmUiModel.VALUE_NOT_AVAILABLE, model.connectedBootstrap)
    }

    @Test
    fun preservesImmediateActionErrorOverRuntimeLastError() {
        val status = SwarmStatus(
            bridgeClassName = "bridge",
            state = "Error",
            peerId = "",
            bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test",
            lastError = "runtime error",
            discoveredPeers = emptyList(),
            rawDiscoveredPeers = "",
        )

        val model = SwarmUiModel.from(status = status, actionError = "reflection mismatch")

        assertEquals("reflection mismatch", model.errorText)
    }

    @Test
    fun fallsBackToRuntimeErrorThenNone() {
        val runtimeErrorStatus = SwarmStatus(
            bridgeClassName = "bridge",
            state = "Error",
            peerId = "",
            bootstrapAddress = "",
            lastError = "runtime error",
            discoveredPeers = emptyList(),
            rawDiscoveredPeers = "",
        )
        assertEquals(
            "runtime error",
            SwarmUiModel.from(status = runtimeErrorStatus, actionError = null).errorText,
        )

        val cleanStatus = runtimeErrorStatus.copy(lastError = "")
        assertEquals(
            SwarmUiModel.VALUE_NONE,
            SwarmUiModel.from(status = cleanStatus, actionError = null).errorText,
        )
    }
}
