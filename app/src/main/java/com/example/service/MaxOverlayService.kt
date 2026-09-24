package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R

class MaxOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "max_overlay_channel"
        const val NOTIF_ID = 1001
        var isOverlayRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        isOverlayRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification())

        setupOverlayWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Max AI Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating voice assistant controller over active apps."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Max Voice Assistant Active")
            .setContentText("Tap overlay button on screen for instant voice control.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        // Programmatically create simple floating pill view
        val inflater = LayoutInflater.from(this)
        // Create simple layout programmatically to avoid complex layout XML dependencies
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#1E293B"))
                cornerRadius = 60f
                setStroke(3, android.graphics.Color.parseColor("#6366F1"))
            }
            elevation = 16f
        }

        val micButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setColorFilter(android.graphics.Color.parseColor("#A5B4FC"))
        }

        val statusText = TextView(this).apply {
            text = "Max AI"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(12, 0, 8, 0)
        }

        container.addView(micButton)
        container.addView(statusText)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = initialX + (event.rawX - initialTouchX).toInt()
                    p.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(container, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        com.example.util.BatteryOptimizationManager.reportUserActivity()
                        // Single tap on floating icon -> trigger voice command or Skip Ad!
                        Toast.makeText(this, "Max: Screen analyze kar raha hoon...", Toast.LENGTH_SHORT).show()
                        MaxAccessibilityService.instance?.let { service ->
                            val dump = service.getScreenStateDump()
                            if (dump.rootNodesFormatted.lowercase().contains("skip ad")) {
                                service.findAndClick("Skip ad")
                                Toast.makeText(this, "Skip ad tap kar diya!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Active package: ${dump.packageName}", Toast.LENGTH_SHORT).show()
                            }
                        } ?: run {
                            Toast.makeText(this, "Pehle Max Accessibility Service enable karein!", Toast.LENGTH_LONG).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        overlayView = container
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isOverlayRunning = false
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
