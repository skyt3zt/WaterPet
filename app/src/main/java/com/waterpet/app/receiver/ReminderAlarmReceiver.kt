package com.waterpet.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.waterpet.app.service.FloatingPetService
import com.waterpet.app.util.DataStorage

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val storage = DataStorage(context)
        
        when (intent.action) {
            ACTION_REMIND -> {
                // 唤醒并通知悬浮窗服务展示喝水气泡
                val showIntent = Intent(context, FloatingPetService::class.java).apply {
                    action = FloatingPetService.ACTION_SHOW_BUBBLE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(showIntent)
                } else {
                    context.startService(showIntent)
                }

                // 调度下一次喝水提醒（保证循环）
                scheduleNextReminder(context, storage.intervalMinutes)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // 手机开机自启逻辑
                if (storage.isPetEnabled) {
                    val startServiceIntent = Intent(context, FloatingPetService::class.java).apply {
                        action = FloatingPetService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startServiceIntent)
                    } else {
                        context.startService(startServiceIntent)
                    }
                    scheduleNextReminder(context, storage.intervalMinutes)
                }
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.waterpet.app.ACTION_REMIND"
        private const val REQUEST_CODE_ALARM = 1001

        // 预约下一次精确定时提醒（单位：分钟）
        fun scheduleNextReminder(context: Context, minutes: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_REMIND
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + (minutes * 60 * 1000L)

            // 精准闹钟，允许在低电耗休眠模式下触发
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }

        // 取消定时提醒
        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_REMIND
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
