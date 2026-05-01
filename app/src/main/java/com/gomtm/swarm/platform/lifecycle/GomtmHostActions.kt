package com.gomtm.swarm.platform.lifecycle

import android.content.Context

object GomtmHostActions {
    fun ensureRuntimeStarted(context: Context, payload: AndroidHostStartupPayload) {
        GomtmForegroundService.start(context = context, startupPayload = payload)
    }

    fun currentRuntimeSurface(context: Context): AndroidHostRuntimeSurface {
        return GomtmForegroundService.currentRuntimeSurface(context)
    }
}
