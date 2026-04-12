package com.gomtm.swarm.platform.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gomtm.swarm.runtime.RuntimeLaunchConfig
import com.gomtm.swarm.shell.NodeRuntimeStore

class GomtmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val config = NodeRuntimeStore(context).load()
        GomtmForegroundService.start(
            context = context,
            bootstrapAddress = config.bootstrapAddress.ifBlank { RuntimeLaunchConfig.DEFAULT_BOOTSTRAP },
            forceRestart = false,
        )
    }
}
