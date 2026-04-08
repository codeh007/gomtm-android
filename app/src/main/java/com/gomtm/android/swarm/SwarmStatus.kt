package com.gomtm.swarm.swarm

data class SwarmStatus(
    val bridgeClassName: String,
    val state: String,
    val peerId: String,
    val bootstrapAddress: String,
    val lastError: String,
    val discoveredPeers: List<DiscoveredPeer>,
    val rawDiscoveredPeers: String,
) {
    companion object {
        fun missing(message: String): SwarmStatus = SwarmStatus(
            bridgeClassName = "",
            state = "Error",
            peerId = "",
            bootstrapAddress = "",
            lastError = message,
            discoveredPeers = emptyList(),
            rawDiscoveredPeers = "",
        )
    }
}
