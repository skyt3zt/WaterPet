package com.waterpet.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class DataStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("water_pet_prefs", Context.MODE_PRIVATE)

    var isPetEnabled: Boolean
        get() = prefs.getBoolean("key_pet_enabled", false)
        set(value) = prefs.edit().putBoolean("key_pet_enabled", value).apply()

    var intervalMinutes: Int
        get() = prefs.getInt("key_interval_minutes", 30)
        set(value) = prefs.edit().putInt("key_interval_minutes", value).apply()

    // 每日饮水目标总量 (ml)，默认 2000ml
    var dailyTargetMl: Int
        get() = prefs.getInt("key_daily_target_ml", 2000)
        set(value) = prefs.edit().putInt("key_daily_target_ml", value.coerceAtLeast(100)).apply()

    // 单杯水容量 (ml)，默认 250ml
    var cupCapacityMl: Int
        get() = prefs.getInt("key_cup_capacity_ml", 250)
        set(value) = prefs.edit().putInt("key_cup_capacity_ml", value.coerceAtLeast(50)).apply()

    // 计算满足每日目标需要喝多少杯
    val targetCups: Int
        get() = ceil(dailyTargetMl.toDouble() / cupCapacityMl.toDouble()).toInt().coerceAtLeast(1)

    fun getTodayDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // 获取所有日期的历史记录 JSON: {"2026-11-05": 6, "2026-11-04": 8}
    private fun getHistoryJson(): JSONObject {
        val str = prefs.getString("key_history_json", "{}") ?: "{}"
        return try {
            JSONObject(str)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun saveHistoryJson(json: JSONObject) {
        prefs.edit().putString("key_history_json", json.toString()).apply()
    }

    // 获取某天的喝水杯数
    fun getWaterCountForDate(dateStr: String): Int {
        val json = getHistoryJson()
        return json.optInt(dateStr, 0)
    }

    // 设置某天的喝水杯数
    fun setWaterCountForDate(dateStr: String, count: Int) {
        val json = getHistoryJson()
        val safeCount = if (count < 0) 0 else count
        json.put(dateStr, safeCount)
        saveHistoryJson(json)

        // 如果设置的是今天，也同步更新今天指标
        if (dateStr == getTodayDateStr()) {
            prefs.edit().putInt("key_today_count", safeCount).apply()
        }
    }

    // 获取今天喝水杯数
    fun getTodayWaterCount(): Int {
        val today = getTodayDateStr()
        val savedDate = prefs.getString("key_last_date", "")
        if (savedDate != today) {
            prefs.edit().putString("key_last_date", today).apply()
            val existing = getWaterCountForDate(today)
            prefs.edit().putInt("key_today_count", existing).apply()
            return existing
        }
        return getWaterCountForDate(today)
    }

    // 喝水打卡 +1 杯
    fun incrementWaterCount(): Int {
        val today = getTodayDateStr()
        val current = getWaterCountForDate(today)
        val newCount = current + 1
        setWaterCountForDate(today, newCount)
        return newCount
    }

    // 某天微调 -1 杯
    fun decrementWaterCount(dateStr: String): Int {
        val current = getWaterCountForDate(dateStr)
        val newCount = if (current > 0) current - 1 else 0
        setWaterCountForDate(dateStr, newCount)
        return newCount
    }

    // 某天微调 +1 杯
    fun incrementWaterCountForDate(dateStr: String): Int {
        val current = getWaterCountForDate(dateStr)
        val newCount = current + 1
        setWaterCountForDate(dateStr, newCount)
        return newCount
    }

    // 获取指定年月的所有天数喝水记录 Map<DayOfMonth, Cups>
    fun getMonthDaysData(year: Int, month: Int): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val json = getHistoryJson()
        for (day in 1..maxDays) {
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day)
            val cups = json.optInt(dateStr, 0)
            result[day] = cups
        }
        return result
    }

    // 月度总览统计数据
    data class MonthOverview(
        val totalCups: Int,
        val totalMl: Int,
        val averageCups: Float,
        val goalDays: Int, // 达到自定义目标的达标天数
        val activeDays: Int // 有喝水记录的天数
    )

    fun getMonthOverview(year: Int, month: Int): MonthOverview {
        val daysData = getMonthDaysData(year, month)
        var totalCups = 0
        var goalDays = 0
        var activeDays = 0
        val target = targetCups

        for ((_, cups) in daysData) {
            if (cups > 0) {
                totalCups += cups
                activeDays++
                if (cups >= target) {
                    goalDays++
                }
            }
        }

        val avg = if (activeDays > 0) totalCups.toFloat() / activeDays.toFloat() else 0f
        return MonthOverview(
            totalCups = totalCups,
            totalMl = totalCups * cupCapacityMl,
            averageCups = avg,
            goalDays = goalDays,
            activeDays = activeDays
        )
    }
}
