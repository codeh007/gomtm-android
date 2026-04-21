package com.gomtm.swarm.runtime

import org.json.JSONObject

data class RuntimeLaunchConfig(
    val connectionAddress: String,
    val autoReconnect: Boolean = true,
)

data class DiscoveredPeer(
    val peerId: String,
    val name: String?,
    val state: String,
    val discoveredInCurrentSession: Boolean,
    val lastSeenAt: String,
) {
    companion object {
        fun parseSnapshot(raw: String): List<DiscoveredPeer> {
            if (raw.isBlank()) {
                return emptyList()
            }
            return runCatching {
                val root = JSONObject(raw)
                val peers = root.optJSONArray("peers")
                if (peers == null) {
                    emptyList()
                } else {
                    buildList(peers.length()) {
                        for (index in 0 until peers.length()) {
                            val item = peers.optJSONObject(index) ?: continue
                            add(
                                DiscoveredPeer(
                                    peerId = item.optString("peer_id"),
                                    name = item.optString("name").ifBlank { null },
                                    state = item.optString("state", "unknown"),
                                    discoveredInCurrentSession = item.optBoolean("discovered_in_current_session", false),
                                    lastSeenAt = item.optString("last_seen_at"),
                                ),
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

data class RuntimeSnapshot(
    val bridgeClassName: String,
    val state: String,
    val peerId: String,
    val connectionAddress: String,
    val lastError: String,
    val lastAutoRestartAtMs: Long = 0L,
    val lastAutoRestartReason: String = "",
    val discoveredPeers: List<DiscoveredPeer>,
    val rawDiscoveredPeers: String,
) {
    companion object {
        fun missing(message: String): RuntimeSnapshot = RuntimeSnapshot(
            bridgeClassName = "",
            state = "Error",
            peerId = "",
            connectionAddress = "",
            lastError = message,
            lastAutoRestartAtMs = 0L,
            lastAutoRestartReason = "",
            discoveredPeers = emptyList(),
            rawDiscoveredPeers = "",
        )
    }
}
