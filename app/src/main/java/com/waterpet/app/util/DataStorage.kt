package com.waterpet.app.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("water_pet_prefs", Context.MODE_PRIVATE)

    var isPetEnabled: Boolean
        get() = prefs.getBoolean("key_pet_enabled", false)
        set(value) = prefs.edit().putBoolean("key_pet_enabled", value).apply()

    var intervalMinutes: Int
        get() = prefs.getInt("key_interval_minutes", 30)
        set(value) = prefs.edit().putInt("key_interval_minutes", value).apply()

    fun getTodayWaterCount(): Int {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString("key_last_date", "")
        if (savedDate != today) {
            // 新的一天，重置喝水计数
            prefs.edit().putString("key_last_date", today).putInt("key_today_count", 0).apply()
            return 0
        }
        return prefs.getInt("key_today_count", 0)
    }

    fun incrementWaterCount(): Int {
        val count = getTodayWaterCount() + 1
        prefs.edit().putInt("key_today_count", count).apply()
        return count
    }
}
