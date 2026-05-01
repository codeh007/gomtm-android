package com.gomtm.swarm.platform.lifecycle

data class AndroidHostStartupPayload(
    val deviceId: String,
    val deviceName: String,
    val runtimeCredential: String,
    val credentialVersion: Int,
)
