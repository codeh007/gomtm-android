package com.gomtm.swarm.shell

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeRuntimeStoreTest {
    @Test
    fun persistsBootstrapAddress() {
        val store = NodeRuntimeStore(InMemorySharedPreferences())
        store.clear()

        store.save(NodeRuntimeConfig(bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test"))

        val restored = store.load()
        assertEquals("/ip4/127.0.0.1/tcp/4101/p2p/test", restored.bootstrapAddress)
    }

    @Test
    fun clearsPersistedState() {
        val store = NodeRuntimeStore(InMemorySharedPreferences())
        store.save(NodeRuntimeConfig(bootstrapAddress = "/ip4/127.0.0.1/tcp/4101/p2p/test"))

        store.clear()

        val restored = store.load()
        assertEquals("", restored.bootstrapAddress)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (values[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor(values)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private class Editor(
        private val values: LinkedHashMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { updates[key.orEmpty()] = value }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { updates[key.orEmpty()] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { updates[key.orEmpty()] = value }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { updates[key.orEmpty()] = value }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { updates[key.orEmpty()] = value }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { updates[key.orEmpty()] = value }

        override fun remove(key: String?): SharedPreferences.Editor = apply { updates[key.orEmpty()] = null }

        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (clearRequested) {
                values.clear()
            }
            for ((key, value) in updates) {
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
        }
    }
}
