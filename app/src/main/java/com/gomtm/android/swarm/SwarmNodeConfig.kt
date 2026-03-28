package com.gomtm.android.swarm

import android.os.Build
import java.util.Locale

data class SwarmNodeConfig(
    val bootstrapAddress: String,
    val nodeName: String,
    val autoReconnect: Boolean = true,
) {
    companion object {
        const val DEFAULT_BOOTSTRAP = "/ip4/156.225.19.101/tcp/4101/p2p/12D3KooWEToGF72k9jypWMPFkwiofuedYrEGZKHNPfKEP2Cg68Cj"

        fun defaultNodeName(deviceName: String = Build.MODEL ?: "Android"): String {
            val normalized =
                deviceName
                    .trim()
                    .lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
            return if (normalized.isBlank()) {
                "android-node"
            } else {
                "android-$normalized"
            }
        }
    }
}
