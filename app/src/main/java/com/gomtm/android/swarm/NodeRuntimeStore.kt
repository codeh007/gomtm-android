package com.gomtm.swarm.swarm

import android.content.Context
import android.content.SharedPreferences

data class NodeRuntimeConfig(
    val bootstrapAddress: String,
    val autoStart: Boolean,
)

class NodeRuntimeStore {
    private val preferences: SharedPreferences

    constructor(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    internal constructor(preferences: SharedPreferences) {
        this.preferences = preferences
    }

    fun load(): NodeRuntimeConfig {
        return NodeRuntimeConfig(
            bootstrapAddress = preferences.getString(KEY_BOOTSTRAP, "").orEmpty(),
            autoStart = preferences.getBoolean(KEY_AUTO_START, false),
        )
    }

    fun save(config: NodeRuntimeConfig) {
        preferences.edit()
            .putString(KEY_BOOTSTRAP, config.bootstrapAddress)
            .putBoolean(KEY_AUTO_START, config.autoStart)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "gomtm_node_runtime"
        private const val KEY_BOOTSTRAP = "bootstrap"
        private const val KEY_AUTO_START = "auto_start"
    }
}
