package com.gomtm.swarm.platform.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GomtmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        // 旧模型会在开机或包替换后基于历史连接地址自动恢复前台运行时。
        // 新模型要求：未完成登录与设备绑定前，不得自动启动业务运行时。
        // 因此这里显式不做任何自动恢复动作，等待后续登录后的显式激活链路接管。
    }
}
