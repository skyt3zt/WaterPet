package com.waterpet.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object PermissionHelper {

    // 1. 检查悬浮窗权限
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    // 跳转开启悬浮窗权限界面
    fun requestOverlayPermission(activity: Activity, requestCode: Int) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, requestCode)
    }

    // 2. 检查电池白名单优化（防杀后台）
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    // 申请忽略电池优化白名单
    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 部分厂商定制系统可能没有该 Action，降级打开电池管理
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }

    // 3. 检查通知权限（Android 13+）
    fun hasNotificationPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
