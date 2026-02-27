package com.smsgateway.app

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var apiUrl: String
        get() = prefs.getString("api_url", "") ?: ""
        set(value) = prefs.edit().putString("api_url", value).apply()

    var intervalSeconds: Int
        get() = prefs.getInt("interval_seconds", 30)
        set(value) = prefs.edit().putInt("interval_seconds", value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean("is_service_running", false)
        set(value) = prefs.edit().putBoolean("is_service_running", value).apply()
}
