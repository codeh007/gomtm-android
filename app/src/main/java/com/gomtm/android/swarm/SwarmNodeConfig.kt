package com.gomtm.swarm.swarm

data class SwarmNodeConfig(
    val bootstrapAddress: String,
    val autoReconnect: Boolean = true,
) {
    companion object {
        const val DEFAULT_BOOTSTRAP = "/ip4/156.225.19.101/tcp/4101/p2p/12D3KooWEToGF72k9jypWMPFkwiofuedYrEGZKHNPfKEP2Cg68Cj"
    }
}
