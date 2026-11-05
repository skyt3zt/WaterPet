package com.waterpet.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.waterpet.app.receiver.ReminderAlarmReceiver
import com.waterpet.app.service.FloatingPetService
import com.waterpet.app.util.DataStorage
import com.waterpet.app.util.PermissionHelper
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var storage: DataStorage

    // 悬浮窗与设置控件
    private lateinit var switchPet: SwitchCompat
    private lateinit var rgInterval: RadioGroup
    private lateinit var rb30m: RadioButton
    private lateinit var rb45m: RadioButton
    private lateinit var rb60m: RadioButton
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnBatteryPermission: Button
    private lateinit var btnTest: Button

    // 📊 日历统计相关控件
    private lateinit var btnPrevMonth: TextView
    private lateinit var btnNextMonth: TextView
    private lateinit var tvMonthYear: TextView
    private lateinit var tvMonthTotal: TextView
    private lateinit var tvMonthGoal: TextView
    private lateinit var tvMonthAvg: TextView
    private lateinit var tableCalendar: TableLayout
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedDetail: TextView
    private lateinit var btnMinusCup: Button
    private lateinit var btnAddCup: Button

    // 日历当前浏览状态
    private var displayYear: Int = 0
    private var displayMonth: Int = 0 // 1..12
    private var selectedDateStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = DataStorage(this)
        
        val cal = Calendar.getInstance()
        displayYear = cal.get(Calendar.YEAR)
        displayMonth = cal.get(Calendar.MONTH) + 1
        selectedDateStr = storage.getTodayDateStr()

        initViews()
        bindEvents()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        renderCalendar()
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

        btnPrevMonth = findViewById(R.id.btnPrevMonth)
        btnNextMonth = findViewById(R.id.btnNextMonth)
        tvMonthYear = findViewById(R.id.tvMonthYear)
        tvMonthTotal = findViewById(R.id.tvMonthTotal)
        tvMonthGoal = findViewById(R.id.tvMonthGoal)
        tvMonthAvg = findViewById(R.id.tvMonthAvg)
        tableCalendar = findViewById(R.id.tableCalendar)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedDetail = findViewById(R.id.tvSelectedDetail)
        btnMinusCup = findViewById(R.id.btnMinusCup)
        btnAddCup = findViewById(R.id.btnAddCup)

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

        // 📊 日历月份切换
        btnPrevMonth.setOnClickListener {
            if (displayMonth == 1) {
                displayMonth = 12
                displayYear--
            } else {
                displayMonth--
            }
            renderCalendar()
        }

        btnNextMonth.setOnClickListener {
            if (displayMonth == 12) {
                displayMonth = 1
                displayYear++
            } else {
                displayMonth++
            }
            renderCalendar()
        }

        // 选定日期的打卡增减微调
        btnAddCup.setOnClickListener {
            storage.incrementWaterCountForDate(selectedDateStr)
            renderCalendar()
        }

        btnMinusCup.setOnClickListener {
            storage.decrementWaterCount(selectedDateStr)
            renderCalendar()
        }
    }

    // 渲染日历网格与总览数据
    private fun renderCalendar() {
        tvMonthYear.text = String.format(Locale.getDefault(), "%d年%02d月", displayYear, displayMonth)

        // 1. 刷新月度总览三大核心指标
        val overview = storage.getMonthOverview(displayYear, displayMonth)
        tvMonthTotal.text = "${overview.totalCups} 杯"
        tvMonthGoal.text = "${overview.goalDays} 天"
        tvMonthAvg.text = String.format(Locale.getDefault(), "%.1f 杯", overview.averageCups)

        // 2. 构建日历网格
        tableCalendar.removeAllViews()

        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, displayYear)
        cal.set(Calendar.MONTH, displayMonth - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, ...
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthData = storage.getMonthDaysData(displayYear, displayMonth)
        val todayStr = storage.getTodayDateStr()

        var currentDay = 1
        var currentRow: TableRow? = null

        // 第一行填充空白单元格（1号之前的星期占位）
        currentRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        }
        for (i in 1 until firstDayOfWeek) {
            currentRow.addView(createEmptyCell())
        }

        for (day in 1..maxDays) {
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", displayYear, displayMonth, day)
            val cups = monthData[day] ?: 0

            val cellView = createDayCell(day, cups, dateStr, todayStr)
            currentRow?.addView(cellView)

            // 每周 7 天换行
            if (currentRow?.childCount == 7) {
                tableCalendar.addView(currentRow)
                if (day < maxDays) {
                    currentRow = TableRow(this).apply {
                        layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                    }
                } else {
                    currentRow = null
                }
            }
        }

        // 最后一行如果不满 7 天，补齐空白单元格保证对齐
        if (currentRow != null && currentRow.childCount > 0) {
            while (currentRow.childCount < 7) {
                currentRow.addView(createEmptyCell())
            }
            tableCalendar.addView(currentRow)
        }

        // 3. 刷新底部单日详情栏
        updateDayDetail()
    }

    private fun createEmptyCell(): View {
        return TextView(this).apply {
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 8, 0, 8)
        }
    }

    private fun createDayCell(day: Int, cups: Int, dateStr: String, todayStr: String): View {
        val container = LinearLayout(this).apply {
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 6)
        }

        val isSelected = (dateStr == selectedDateStr)
        val isToday = (dateStr == todayStr)

        // 背景样式判断
        when {
            isSelected -> {
                container.setBackgroundResource(R.drawable.bg_calendar_selected)
            }
            isToday -> {
                container.setBackgroundResource(R.drawable.bg_calendar_today)
            }
            cups >= 8 -> {
                container.setBackgroundResource(R.drawable.bg_calendar_goal)
            }
            else -> {
                container.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // 日期文字 (如 "5")
        val tvDay = TextView(this).apply {
            text = day.toString()
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(
                when {
                    isSelected -> Color.WHITE
                    isToday -> ContextCompat.getColor(context, R.color.primary_dark)
                    else -> ContextCompat.getColor(context, R.color.text_primary)
                }
            )
        }
        container.addView(tvDay)

        // 喝水杯数角标 (如 "💧4" 或 "·")
        val tvCups = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 9f
            if (cups > 0) {
                text = "$cups"
                setTextColor(if (isSelected) Color.WHITE else ContextCompat.getColor(context, R.color.primary))
            } else {
                text = if (isToday) "•" else ""
                setTextColor(if (isSelected) Color.WHITE else Color.LTGRAY)
            }
        }
        container.addView(tvCups)

        container.setOnClickListener {
            selectedDateStr = dateStr
            renderCalendar()
        }

        return container
    }

    private fun updateDayDetail() {
        val todayStr = storage.getTodayDateStr()
        val isToday = (selectedDateStr == todayStr)
        tvSelectedDate.text = "📅 $selectedDateStr" + if (isToday) " (今天)" else ""

        val cups = storage.getWaterCountForDate(selectedDateStr)
        val ml = cups * 250 // 每杯约 250ml
        tvSelectedDetail.text = "共喝水 $cups 杯 (约 ${ml} ml)" + if (cups >= 8) " ✨ 达到每日目标！" else ""
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

    companion object {
        private const val REQUEST_CODE_OVERLAY = 2001
    }
}
