package com.gomtm.swarm.shell

import android.content.Context
import android.content.SharedPreferences

data class NodeRuntimeConfig(
    val bootstrapAddress: String,
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
        )
    }

    fun save(config: NodeRuntimeConfig) {
        preferences.edit()
            .putString(KEY_BOOTSTRAP, config.bootstrapAddress)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "gomtm_node_runtime"
        private const val KEY_BOOTSTRAP = "bootstrap"
    }
}
