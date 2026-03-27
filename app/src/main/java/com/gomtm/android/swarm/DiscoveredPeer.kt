package com.gomtm.android.swarm

import org.json.JSONObject

data class DiscoveredPeer(
    val peerId: String,
    val name: String?,
    val state: String,
    val discoveredInCurrentSession: Boolean,
    val isBootstrap: Boolean,
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
                                    isBootstrap = item.optBoolean("is_bootstrap", false),
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
