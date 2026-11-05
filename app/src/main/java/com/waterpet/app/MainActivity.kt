package com.waterpet.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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

    // 🎯 目标与水杯设置控件
    private lateinit var layoutTargetMl: View
    private lateinit var tvDailyTargetMl: TextView
    private lateinit var layoutCupCapacity: View
    private lateinit var tvCupCapacityMl: TextView
    private lateinit var tvTargetCalculation: TextView
    private lateinit var pbTodayProgress: ProgressBar
    private lateinit var tvTodayProgressText: TextView

    // 📊 日历统计相关控件
    private lateinit var btnPrevMonth: TextView
    private lateinit var btnNextMonth: TextView
    private lateinit var tvMonthYear: TextView
    private lateinit var tvMonthTotal: TextView
    private lateinit var tvMonthTotalMl: TextView
    private lateinit var tvMonthGoal: TextView
    private lateinit var tvMonthGoalLabel: TextView
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
        updateGoalCard()
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

        // 🎯 目标与水杯控件
        layoutTargetMl = findViewById(R.id.layoutTargetMl)
        tvDailyTargetMl = findViewById(R.id.tvDailyTargetMl)
        layoutCupCapacity = findViewById(R.id.layoutCupCapacity)
        tvCupCapacityMl = findViewById(R.id.tvCupCapacityMl)
        tvTargetCalculation = findViewById(R.id.tvTargetCalculation)
        pbTodayProgress = findViewById(R.id.pbTodayProgress)
        tvTodayProgressText = findViewById(R.id.tvTodayProgressText)

        // 📊 日历控件
        btnPrevMonth = findViewById(R.id.btnPrevMonth)
        btnNextMonth = findViewById(R.id.btnNextMonth)
        tvMonthYear = findViewById(R.id.tvMonthYear)
        tvMonthTotal = findViewById(R.id.tvMonthTotal)
        tvMonthTotalMl = findViewById(R.id.tvMonthTotalMl)
        tvMonthGoal = findViewById(R.id.tvMonthGoal)
        tvMonthGoalLabel = findViewById(R.id.tvMonthGoalLabel)
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

        // 🎯 目标与水杯容量修改点击
        layoutTargetMl.setOnClickListener {
            showTargetMlDialog()
        }

        layoutCupCapacity.setOnClickListener {
            showCupCapacityDialog()
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
            updateGoalCard()
            renderCalendar()
        }

        btnMinusCup.setOnClickListener {
            storage.decrementWaterCount(selectedDateStr)
            updateGoalCard()
            renderCalendar()
        }
    }

    // 🎯 刷新目标卡片数据与今日进度条
    private fun updateGoalCard() {
        val targetMl = storage.dailyTargetMl
        val cupMl = storage.cupCapacityMl
        val targetCups = storage.targetCups

        tvDailyTargetMl.text = "$targetMl ml"
        tvCupCapacityMl.text = "$cupMl ml"
        tvTargetCalculation.text = "💡 每天需喝 $targetCups 杯水满足今日目标"

        val todayCups = storage.getTodayWaterCount()
        val todayMl = todayCups * cupMl
        val percent = if (targetMl > 0) ((todayMl.toFloat() / targetMl.toFloat()) * 100).toInt().coerceAtMost(100) else 0

        pbTodayProgress.progress = percent
        val statusSuffix = if (todayMl >= targetMl) " 🎉 今日目标已达成！" else ""
        tvTodayProgressText.text = "今日已喝：$todayMl / $targetMl ml ($todayCups / $targetCups 杯) · $percent%$statusSuffix"
    }

    // 弹出选择/修改每日目标弹窗
    private fun showTargetMlDialog() {
        val options = arrayOf(
            "1500 ml (轻量饮水)",
            "1800 ml (标准)",
            "2000 ml (健康推荐)",
            "2500 ml (运动补水)",
            "3000 ml (高强度补水)",
            "自定义容量..."
        )
        AlertDialog.Builder(this)
            .setTitle("设置每日喝水目标总量")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveDailyTarget(1500)
                    1 -> saveDailyTarget(1800)
                    2 -> saveDailyTarget(2000)
                    3 -> saveDailyTarget(2500)
                    4 -> saveDailyTarget(3000)
                    5 -> showCustomNumberDialog("请输入每日喝水目标 (ml)", storage.dailyTargetMl) { saveDailyTarget(it) }
                }
            }
            .show()
    }

    // 弹出选择/修改单杯容量弹窗
    private fun showCupCapacityDialog() {
        val options = arrayOf(
            "150 ml (小水杯)",
            "200 ml (一次性纸杯)",
            "250 ml (标准马克杯/推荐)",
            "350 ml (大马克杯/咖啡杯)",
            "500 ml (运动保温杯)",
            "自定义容量..."
        )
        AlertDialog.Builder(this)
            .setTitle("设置单杯水容量")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveCupCapacity(150)
                    1 -> saveCupCapacity(200)
                    2 -> saveCupCapacity(250)
                    3 -> saveCupCapacity(350)
                    4 -> saveCupCapacity(500)
                    5 -> showCustomNumberDialog("请输入单杯容量 (ml)", storage.cupCapacityMl) { saveCupCapacity(it) }
                }
            }
            .show()
    }

    private fun showCustomNumberDialog(title: String, initialValue: Int, onConfirm: (Int) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initialValue.toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val num = input.text.toString().toIntOrNull()
                if (num != null && num > 0) {
                    onConfirm(num)
                } else {
                    Toast.makeText(this, "请输入有效数值", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveDailyTarget(ml: Int) {
        storage.dailyTargetMl = ml
        updateGoalCard()
        renderCalendar()
        Toast.makeText(this, "每日目标已更新为 $ml ml (需喝 ${storage.targetCups} 杯)", Toast.LENGTH_SHORT).show()
    }

    private fun saveCupCapacity(ml: Int) {
        storage.cupCapacityMl = ml
        updateGoalCard()
        renderCalendar()
        Toast.makeText(this, "单杯容量已更新为 $ml ml (需喝 ${storage.targetCups} 杯)", Toast.LENGTH_SHORT).show()
    }

    // 渲染日历网格与总览数据
    private fun renderCalendar() {
        tvMonthYear.text = String.format(Locale.getDefault(), "%d年%02d月", displayYear, displayMonth)

        // 1. 刷新月度总览核心指标
        val overview = storage.getMonthOverview(displayYear, displayMonth)
        tvMonthTotal.text = "${overview.totalCups} 杯"
        tvMonthTotalMl.text = "约 ${overview.totalMl} ml"
        tvMonthGoal.text = "${overview.goalDays} 天"
        tvMonthGoalLabel.text = "达标天数(≥${storage.targetCups}杯)"
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
        val targetCups = storage.targetCups

        var currentRow: TableRow? = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        }

        // 第一行填充空白单元格（1号之前的星期占位）
        for (i in 1 until firstDayOfWeek) {
            currentRow?.addView(createEmptyCell())
        }

        for (day in 1..maxDays) {
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", displayYear, displayMonth, day)
            val cups = monthData[day] ?: 0

            val cellView = createDayCell(day, cups, dateStr, todayStr, targetCups)
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

        // 最后一行补齐 7 天
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

    private fun createDayCell(day: Int, cups: Int, dateStr: String, todayStr: String, targetCups: Int): View {
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
            cups >= targetCups -> {
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

        // 喝水杯数角标 (如 "4" 或 "•")
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
        val ml = cups * storage.cupCapacityMl
        val targetCups = storage.targetCups
        val targetMl = storage.dailyTargetMl
        val isGoalReached = (ml >= targetMl)

        tvSelectedDetail.text = "共喝水 $cups 杯 (约 ${ml} ml / 目标 ${targetMl} ml)" +
                if (isGoalReached) " ✨ 达成目标！" else " (还需 ${targetCups - cups} 杯达标)"
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
