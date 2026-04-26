package com.gomtm.swarm.runtime

/**
 * 旧 connection/swarm runtime 遗留启动输入。
 * 当前仅保留给未完全退场的反射 bridge 启动路径使用，
 * 不再代表 Android 宿主壳或设备业务状态的 canonical contract。
 */
data class RuntimeLaunchConfig(
    val connectionAddress: String,
    val autoReconnect: Boolean = true,
)
