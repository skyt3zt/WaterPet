package com.waterpet.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.waterpet.app.receiver.ReminderAlarmReceiver
import com.waterpet.app.service.FloatingPetService
import com.waterpet.app.util.DataStorage
import com.waterpet.app.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var storage: DataStorage

    private lateinit var switchPet: SwitchCompat
    private lateinit var rgInterval: RadioGroup
    private lateinit var rb30m: RadioButton
    private lateinit var rb45m: RadioButton
    private lateinit var rb60m: RadioButton
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnBatteryPermission: Button
    private lateinit var btnTest: Button
    private lateinit var tvTodayCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = DataStorage(this)
        initViews()
        bindEvents()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        updateStats()
    }

    private fun initViews() {
        switchPet = findViewById(R.id.switchPet)
        rgInterval = findViewById(R.id.rgInterval)
        rb30m = findViewById(R.id.rb30m)
        rb45m = findViewById(R.id.rb45m)
        rb60m = findViewById(R.id.rb60m)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnBatteryPermission = findViewById(R.id.btnBatteryPermission)
        btnTest = findViewById(R.id.btnTest)
        tvTodayCount = findViewById(R.id.tvTodayCount)

        // 恢复已有设置
        switchPet.isChecked = storage.isPetEnabled
        when (storage.intervalMinutes) {
            45 -> rb45m.isChecked = true
            60 -> rb60m.isChecked = true
            else -> rb30m.isChecked = true
        }
    }

    private fun bindEvents() {
        // 主开关切换
        switchPet.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!PermissionHelper.hasOverlayPermission(this)) {
                    Toast.makeText(this, "请先授予悬浮窗显示权限", Toast.LENGTH_SHORT).show()
                    switchPet.isChecked = false
                    PermissionHelper.requestOverlayPermission(this, REQUEST_CODE_OVERLAY)
                    return@setOnCheckedChangeListener
                }
                startPetService()
                storage.isPetEnabled = true
                ReminderAlarmReceiver.scheduleNextReminder(this, storage.intervalMinutes)
                Toast.makeText(this, "喝水桌宠已开启，每 ${storage.intervalMinutes} 分钟提醒", Toast.LENGTH_SHORT).show()
            } else {
                stopPetService()
                storage.isPetEnabled = false
                ReminderAlarmReceiver.cancelReminder(this)
                Toast.makeText(this, "喝水桌宠已关闭", Toast.LENGTH_SHORT).show()
            }
        }

        // 提醒间隔切换
        rgInterval.setOnCheckedChangeListener { _, checkedId ->
            val minutes = when (checkedId) {
                R.id.rb45m -> 45
                R.id.rb60m -> 60
                else -> 30
            }
            storage.intervalMinutes = minutes
            if (storage.isPetEnabled) {
                ReminderAlarmReceiver.scheduleNextReminder(this, minutes)
                Toast.makeText(this, "已更新提醒间隔为 $minutes 分钟", Toast.LENGTH_SHORT).show()
            }
        }

        // 权限按钮操作
        btnOverlayPermission.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                PermissionHelper.requestOverlayPermission(this, REQUEST_CODE_OVERLAY)
            } else {
                Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
            }
        }

        btnBatteryPermission.setOnClickListener {
            if (!PermissionHelper.isIgnoringBatteryOptimizations(this)) {
                PermissionHelper.requestIgnoreBatteryOptimization(this)
            } else {
                Toast.makeText(this, "已加入电池白名单", Toast.LENGTH_SHORT).show()
            }
        }

        // 立即体验气泡按钮
        btnTest.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                PermissionHelper.requestOverlayPermission(this, REQUEST_CODE_OVERLAY)
                return@setOnClickListener
            }
            if (!storage.isPetEnabled) {
                switchPet.isChecked = true
            }
            val intent = Intent(this, FloatingPetService::class.java).apply {
                action = FloatingPetService.ACTION_SHOW_BUBBLE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun startPetService() {
        val intent = Intent(this, FloatingPetService::class.java).apply {
            action = FloatingPetService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopPetService() {
        val intent = Intent(this, FloatingPetService::class.java).apply {
            action = FloatingPetService.ACTION_STOP
        }
        stopService(intent)
    }

    private fun updatePermissionStates() {
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        btnOverlayPermission.text = if (hasOverlay) getString(R.string.btn_granted) else getString(R.string.btn_grant)
        btnOverlayPermission.isEnabled = !hasOverlay

        val hasBattery = PermissionHelper.isIgnoringBatteryOptimizations(this)
        btnBatteryPermission.text = if (hasBattery) getString(R.string.btn_granted) else getString(R.string.btn_grant)
        btnBatteryPermission.isEnabled = !hasBattery
    }

    private fun updateStats() {
        tvTodayCount.text = "${storage.getTodayWaterCount()} ${getString(R.string.stats_cups_unit)}"
    }

    companion object {
        private const val REQUEST_CODE_OVERLAY = 2001
    }
}
