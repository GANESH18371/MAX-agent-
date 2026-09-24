package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.data.db.AppDatabase
import com.example.util.VoiceAssistantManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "max_reminders_channel"
        private const val CHANNEL_NAME = "Max Voice Reminders"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        val reminderTitle = intent.getStringExtra("REMINDER_TITLE") ?: "Reminder"

        Log.d(TAG, "Alarm fired for Reminder ID: $reminderId, Title: $reminderTitle")

        // 1. Mark as triggered in Room Database
        if (reminderId != -1) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.reminderDao().markTriggered(reminderId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to mark reminder triggered", e)
                }
            }
        }

        // 2. Post System Notification
        showNotification(context, reminderTitle)

        // 3. Speak TTS Announcement in Hindi
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "⏰ REMINDER: $reminderTitle", Toast.LENGTH_LONG).show()
            val voiceAssistant = VoiceAssistantManager(context)
            val announcementText = "Aapne bola tha, ab $reminderTitle ka time ho gaya hai!"
            voiceAssistant.speak(announcementText)
        }
    }

    private fun showNotification(context: Context, title: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Max Voice Reminders and Alarms"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Max Reminder")
            .setContentText("Aapne bola tha: $title ka time ho gaya hai!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
