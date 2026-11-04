package com.waterpet.app.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.waterpet.app.MainActivity
import com.waterpet.app.R
import com.waterpet.app.receiver.ReminderAlarmReceiver
import com.waterpet.app.util.DataStorage
import kotlin.math.abs

class FloatingPetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private lateinit var storage: DataStorage

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        storage = DataStorage(this)
        startAsForeground()
        initFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> {
                showBubbleWithAnimation()
                vibrateAndFeedback()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "water_pet_foreground_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "喝水桌宠常驻提醒",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保证桌宠在屏幕边缘正常运行并准时提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_water_pet)
            .setContentTitle("喝水桌宠正在守护你")
            .setContentText("每 ${storage.intervalMinutes} 分钟提醒一次喝水")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun initFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_pet, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        bindViews()
        windowManager?.addView(floatingView, windowParams)
    }

    private fun bindViews() {
        val view = floatingView ?: return
        val ivPet = view.findViewById<ImageView>(R.id.ivPet)
        val bubbleCard = view.findViewById<LinearLayout>(R.id.bubbleCard)
        val btnCloseBubble = view.findViewById<TextView>(R.id.btnCloseBubble)
        val btnDelay = view.findViewById<Button>(R.id.btnDelay)
        val btnDrinkDone = view.findViewById<Button>(R.id.btnDrinkDone)

        // 1. 拖拽与吸附逻辑
        ivPet.setOnTouchListener { _, event ->
            val params = windowParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 单击：切换气泡显示/隐藏
                        toggleBubbleVisibility()
                    } else {
                        // 拖拽松手：自动向屏幕左右边缘吸附
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }

        // 2. 点击“喝了一杯 🥛”打卡
        btnDrinkDone.setOnClickListener {
            val count = storage.incrementWaterCount()
            Toast.makeText(this, "太棒啦！今日已喝水 $count 杯 🎉", Toast.LENGTH_SHORT).show()
            
            // 播放小水滴跳动动画后隐藏气泡
            playHappyAnimation(ivPet) {
                hideBubbleWithAnimation()
            }
        }

        // 3. 点击“稍后提醒 (5m)”
        btnDelay.setOnClickListener {
            Toast.makeText(this, "好的，5分钟后再提醒你~", Toast.LENGTH_SHORT).show()
            hideBubbleWithAnimation()
            ReminderAlarmReceiver.scheduleNextReminder(this, 5)
        }

        // 4. 关闭气泡
        btnCloseBubble.setOnClickListener {
            hideBubbleWithAnimation()
        }
    }

    private fun toggleBubbleVisibility() {
        val bubbleCard = floatingView?.findViewById<LinearLayout>(R.id.bubbleCard) ?: return
        if (bubbleCard.visibility == View.VISIBLE) {
            hideBubbleWithAnimation()
        } else {
            showBubbleWithAnimation()
        }
    }

    private fun showBubbleWithAnimation() {
        val bubbleCard = floatingView?.findViewById<LinearLayout>(R.id.bubbleCard) ?: return
        bubbleCard.visibility = View.VISIBLE
        bubbleCard.alpha = 0f
        bubbleCard.scaleX = 0.5f
        bubbleCard.scaleY = 0.5f

        val scaleX = ObjectAnimator.ofFloat(bubbleCard, "scaleX", 0.5f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(bubbleCard, "scaleY", 0.5f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(bubbleCard, "alpha", 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            interpolator = OvershootInterpolator(1.2f)
            duration = 300
            start()
        }
    }

    private fun hideBubbleWithAnimation() {
        val bubbleCard = floatingView?.findViewById<LinearLayout>(R.id.bubbleCard) ?: return
        val scaleX = ObjectAnimator.ofFloat(bubbleCard, "scaleX", 1.0f, 0.5f)
        val scaleY = ObjectAnimator.ofFloat(bubbleCard, "scaleY", 1.0f, 0.5f)
        val alpha = ObjectAnimator.ofFloat(bubbleCard, "alpha", 1.0f, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 200
            start()
        }.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                bubbleCard.visibility = View.GONE
            }
        })
    }

    private fun playHappyAnimation(view: View, onEnd: () -> Unit) {
        val bounceY = ObjectAnimator.ofFloat(view, "translationY", 0f, -20f, 0f, -10f, 0f)
        bounceY.duration = 450
        bounceY.start()
        view.postDelayed({ onEnd() }, 450)
    }

    private fun snapToEdge() {
        val params = windowParams ?: return
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        // 判断离左边近还是右边近
        val targetX = if (params.x < screenWidth / 2) 10 else screenWidth - 160
        params.x = targetX
        windowManager?.updateViewLayout(floatingView, params)
    }

    private fun vibrateAndFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(150)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null && windowManager != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.waterpet.app.ACTION_START"
        const val ACTION_SHOW_BUBBLE = "com.waterpet.app.ACTION_SHOW_BUBBLE"
        const val ACTION_STOP = "com.waterpet.app.ACTION_STOP"
    }
}
