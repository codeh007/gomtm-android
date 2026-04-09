package com.gomtm.swarm.swarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GomtmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val config = NodeRuntimeStore(context).load()
        GomtmForegroundService.start(
            context = context,
            bootstrapAddress = config.bootstrapAddress.ifBlank { SwarmNodeConfig.DEFAULT_BOOTSTRAP },
            forceRestart = false,
        )
    }
}
