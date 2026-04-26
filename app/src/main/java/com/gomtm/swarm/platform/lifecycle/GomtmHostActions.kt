package com.gomtm.swarm.platform.lifecycle

import android.content.Context

object GomtmHostActions {
    fun startDeviceService(context: Context) {
        GomtmForegroundService.start(
            context = context,
            connectionAddress = "",
            forceRestart = false,
        )
    }
}
