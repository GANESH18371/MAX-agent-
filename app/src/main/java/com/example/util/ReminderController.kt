package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.data.db.AppDatabase
import com.example.data.db.ReminderEntity
import com.example.receiver.ReminderReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object ReminderController {

    private const val TAG = "ReminderController"
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun parseAndProcessReminderCommand(
        context: Context,
        voiceAssistant: VoiceAssistantManager,
        rawCommand: String
    ): Boolean {
        val cmd = rawCommand.lowercase().trim()

        // 1. LIST REMINDERS ("mere saare reminders batao", "reminders dikhao", "list reminders")
        if (cmd.contains("saare reminder") || cmd.contains("reminder list") ||
            cmd.contains("reminders batao") || cmd.contains("reminders dikhao") ||
            cmd.contains("reminders kya hain")
        ) {
            listActiveReminders(context, voiceAssistant)
            return true
        }

        // 2. CANCEL REMINDER ("mera dawai wala reminder cancel karo")
        if (cmd.contains("cancel") || cmd.contains("hatao") || cmd.contains("delete")) {
            val query = cmd.replace("cancel", "")
                .replace("hatao", "")
                .replace("delete", "")
                .replace("reminder", "")
                .replace("mera", "")
                .replace("wala", "")
                .trim()
            cancelReminder(context, voiceAssistant, query)
            return true
        }

        // 3. SET REMINDER / ALARM ("5 minute baad dawai ka reminder laga do", "8 baje alarm laga do")
        if (cmd.contains("yaad dilana") || cmd.contains("reminder") || cmd.contains("alarm")) {
            parseAndSchedule(context, voiceAssistant, cmd)
            return true
        }

        return false
    }

    private fun parseAndSchedule(
        context: Context,
        voiceAssistant: VoiceAssistantManager,
        cmd: String
    ) {
        val calendar = Calendar.getInstance()
        var targetTimeMs: Long = -1
        var reminderTitle = "Reminder"

        // Pattern A: Relative Minutes ("5 minute baad", "10 min me", "1 ghante baad")
        val minPattern = Pattern.compile("(\\d+)\\s*(minute|min|m|ghante|ghanta|hour|hrs)")
        val minMatcher = minPattern.matcher(cmd)

        if (minMatcher.find()) {
            val amount = minMatcher.group(1)?.toIntOrNull() ?: 5
            val unit = minMatcher.group(2) ?: "minute"

            if (unit.contains("ghant") || unit.contains("hour") || unit.contains("hrs")) {
                calendar.add(Calendar.HOUR_OF_DAY, amount)
            } else {
                calendar.add(Calendar.MINUTE, amount)
            }
            targetTimeMs = calendar.timeInMillis
        } else {
            // Pattern B: Specific Time ("8 baje", "8:30 baje", "7:15 pm", "shaam 6 baje")
            val timePattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(baje|pm|am)?")
            val timeMatcher = timePattern.matcher(cmd)

            if (timeMatcher.find()) {
                var hour = timeMatcher.group(1)?.toIntOrNull() ?: 8
                val minute = timeMatcher.group(2)?.toIntOrNull() ?: 0
                val ampm = timeMatcher.group(3) ?: ""

                if ((cmd.contains("shaam") || cmd.contains("raat") || ampm.equals("pm", true)) && hour < 12) {
                    hour += 12
                }

                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)

                // If set time has already passed today, set for tomorrow
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                targetTimeMs = calendar.timeInMillis
            }
        }

        if (targetTimeMs <= System.currentTimeMillis()) {
            // Default: 10 minutes from now if parsing couldn't find explicit time
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.MINUTE, 10)
            targetTimeMs = calendar.timeInMillis
        }

        // Extract title
        val cleanedTitle = cmd.replace("reminder", "")
            .replace("laga do", "")
            .replace("set karo", "")
            .replace("yaad dilana", "")
            .replace("mujhe", "")
            .replace("par", "")
            .replace("baad", "")
            .replace("baje", "")
            .replace("alarm", "")
            .trim()

        if (cleanedTitle.isNotBlank()) {
            reminderTitle = cleanedTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        val formattedTimeStr = timeFormat.format(Date(targetTimeMs))

        GlobalScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val reminder = ReminderEntity(
                title = reminderTitle,
                timeInMillis = targetTimeMs,
                formattedTime = formattedTimeStr
            )

            val insertedId = db.reminderDao().insertReminder(reminder).toInt()
            scheduleExactAlarm(context, insertedId, reminderTitle, targetTimeMs)

            val responseSpeech = "Teekh hai! Maine $formattedTimeStr par '$reminderTitle' ka reminder set kar diya hai."
            withContext(Dispatchers.Main) {
                postToast(context, "⏰ Reminder Set: $reminderTitle at $formattedTimeStr")
                voiceAssistant.speak(responseSpeech)
            }
        }
    }

    private fun scheduleExactAlarm(context: Context, id: Int, title: String, timeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", id)
            putExtra("REMINDER_TITLE", title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
            }
            Log.d(TAG, "Scheduled alarm ID $id for $title at $timeMs")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm", e)
        }
    }

    private fun cancelReminder(
        context: Context,
        voiceAssistant: VoiceAssistantManager,
        query: String
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val deletedCount = db.reminderDao().deleteMatchingReminder(query)

            val speechRes = if (deletedCount > 0) {
                "Maine $query wala reminder cancel kar diya hai."
            } else {
                "Mujhe '$query' naam se koi active reminder nahi mila."
            }

            withContext(Dispatchers.Main) {
                postToast(context, "🗑️ Cancelled $deletedCount reminder(s)")
                voiceAssistant.speak(speechRes)
            }
        }
    }

    private fun listActiveReminders(
        context: Context,
        voiceAssistant: VoiceAssistantManager
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val list = db.reminderDao().getActiveRemindersSync()

            val speechRes = if (list.isEmpty()) {
                "Aapka koi bhi active reminder nahi hai."
            } else {
                val sb = StringBuilder("Aapke paas ${list.size} active reminders hain. ")
                list.take(5).forEachIndexed { idx, item ->
                    sb.append("${idx + 1}. ${item.title} at ${item.formattedTime}. ")
                }
                sb.toString()
            }

            withContext(Dispatchers.Main) {
                postToast(context, "📋 ${list.size} Active Reminder(s)")
                voiceAssistant.speak(speechRes)
            }
        }
    }

    private fun postToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
