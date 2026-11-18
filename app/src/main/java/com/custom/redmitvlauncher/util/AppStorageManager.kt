package com.custom.redmitvlauncher.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppStorageManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tv_launcher_prefs", Context.MODE_PRIVATE)

    fun saveAppConfig(packageOrder: List<String>, hiddenPackages: Set<String>) {
        val json = JSONObject().apply {
            put("order", JSONArray(packageOrder))
            put("hidden", JSONArray(hiddenPackages.toList()))
        }
        prefs.edit().putString("app_config", json.toString()).apply()
    }

    fun getSavedOrder(): List<String> {
        val raw = prefs.getString("app_config", null) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("order") ?: return emptyList()
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getHiddenPackages(): MutableSet<String> {
        val raw = prefs.getString("app_config", null) ?: return mutableSetOf()
        return try {
            val arr = JSONObject(raw).optJSONArray("hidden") ?: return mutableSetOf()
            MutableSet(arr.length()) { arr.getString(it) }.toMutableSet()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    fun getDeviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = "tv_redmi_" + (1000..9999).random()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }
}
