package com.gomtm.swarm.platform.lifecycle

import android.content.Context
import java.util.UUID
import org.json.JSONObject

private const val PREFS_NAME = "gomtm_host_shell"
private const val KEY_HOST_INSTANCE_ID = "host_instance_id"
private const val KEY_STARTUP_PAYLOAD = "startup_payload"

object AndroidHostInstallStore {
    fun getOrCreateHostInstanceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_HOST_INSTANCE_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) {
            return existing
        }
        val created = "android-host-" + UUID.randomUUID().toString()
        prefs.edit().putString(KEY_HOST_INSTANCE_ID, created).apply()
        return created
    }

    fun persistStartupPayload(context: Context, payload: AndroidHostStartupPayload) {
        val raw = JSONObject()
            .put("deviceId", payload.deviceId)
            .put("deviceName", payload.deviceName)
            .put("runtimeCredential", payload.runtimeCredential)
            .put("credentialVersion", payload.credentialVersion)
            .toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STARTUP_PAYLOAD, raw)
            .apply()
    }

    fun restoreSavedStartupPayload(context: Context): AndroidHostStartupPayload? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STARTUP_PAYLOAD, null)
        val json = raw?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: return null
        val deviceId = json.optString("deviceId").trim()
        val deviceName = json.optString("deviceName").trim()
        val runtimeCredential = json.optString("runtimeCredential").trim()
        val credentialVersion = json.optInt("credentialVersion")
        if (deviceId.isEmpty() || deviceName.isEmpty() || runtimeCredential.isEmpty() || credentialVersion <= 0) {
            clearStartupPayload(context)
            return null
        }
        return AndroidHostStartupPayload(
            deviceId = deviceId,
            deviceName = deviceName,
            runtimeCredential = runtimeCredential,
            credentialVersion = credentialVersion,
        )
    }

    fun clearStartupPayload(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_STARTUP_PAYLOAD)
            .apply()
    }
}
