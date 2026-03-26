package com.gomtm.android.swarm

data class SwarmStatus(
    val integrationMode: String,
    val aarDetected: Boolean,
    val bridgeClassName: String?,
    val lifecycleSurface: String,
    val state: String,
    val peerId: String?,
    val bootstrapAddress: String?,
    val lastError: String?
) {
    companion object {
        fun missing(): SwarmStatus = SwarmStatus(
            integrationMode = "host-shell",
            aarDetected = false,
            bridgeClassName = null,
            lifecycleSurface = "unbound",
            state = "AAR not bound",
            peerId = null,
            bootstrapAddress = null,
            lastError = null
        )
    }
}
