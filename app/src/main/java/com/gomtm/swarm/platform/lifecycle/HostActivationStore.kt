package com.gomtm.swarm.platform.lifecycle

import android.content.Context

private const val PREFS_NAME = "gomtm_host_shell"
private const val KEY_DEVICE_SERVICE_ACTIVATION_REQUESTED = "device_service_activation_requested"

object HostActivationStore {
    fun markDeviceServiceActivationRequested(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEVICE_SERVICE_ACTIVATION_REQUESTED, true)
            .apply()
    }

    fun clearDeviceServiceActivationRequested(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEVICE_SERVICE_ACTIVATION_REQUESTED, false)
            .apply()
    }

    fun isDeviceServiceActivationRequested(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEVICE_SERVICE_ACTIVATION_REQUESTED, false)
    }
}
