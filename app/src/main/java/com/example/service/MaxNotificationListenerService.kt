package com.example.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.example.data.api.GeminiClient
import com.example.data.db.AppDatabase
import com.example.data.repository.MaxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MaxNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MaxNotifListener"

        @Volatile
        var isAutoReplyEnabled: Boolean = false

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat != null && flat.contains(context.packageName)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            if (!isAutoReplyEnabled) {
                Log.d(TAG, "WhatsApp notification received, but Auto-Reply is OFF")
                return
            }

            val extras = sbn.notification?.extras ?: return
            val senderName = extras.getString(Notification.EXTRA_TITLE)
                ?: extras.getString(Notification.EXTRA_TITLE_BIG)
                ?: "WhatsApp User"
            
            val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            if (messageText.isBlank() || messageText.contains("new messages", ignoreCase = true) || messageText.contains("messages from", ignoreCase = true)) {
                return
            }

            Log.d(TAG, "WhatsApp message from [$senderName]: \"$messageText\"")

            serviceScope.launch {
                handleWhatsAppAutoReply(sbn, senderName, messageText)
            }
        }
    }

    private suspend fun handleWhatsAppAutoReply(
        sbn: StatusBarNotification,
        senderName: String,
        messageText: String
    ) {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = MaxRepository(db.actionLogDao(), db.userMemoryDao(), db.reminderDao())

        // 1. Generate smart reply using Gemini AI with context
        val memories = repository.getAllMemoriesSync().joinToString("\n") { "- ${it.memoryKey}: ${it.memoryValue}" }
        val prompt = "User $senderName messaged on WhatsApp: \"$messageText\". Generate a short, polite 1-sentence Hindi/Hinglish reply."

        val decision = GeminiClient.analyzeAndDecide(
            userCommand = prompt,
            currentAppPackage = "com.whatsapp",
            screenTreeText = "WhatsApp Notification from $senderName: $messageText",
            userMemoryContext = memories
        )

        val replyText = decision.spokenResponseHindi.ifBlank {
            "Namaste $senderName! Main abhi busy hoon, Max AI dwara auto-reply sent."
        }

        // 2. Try sending via Notification Action RemoteInput (Direct inline reply)
        val actions = sbn.notification?.actions ?: emptyArray()
        var replyActionSent = false

        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                for (remoteInput in remoteInputs) {
                    try {
                        val intent = Intent()
                        val bundle = Bundle()
                        bundle.putCharSequence(remoteInput.resultKey, replyText)
                        android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

                        action.actionIntent.send(applicationContext, 0, intent)
                        replyActionSent = true
                        Log.d(TAG, "Sent WhatsApp reply via Direct Notification RemoteInput to $senderName: \"$replyText\"")
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send reply via RemoteInput", e)
                    }
                }
            }
            if (replyActionSent) break
        }

        // 3. Fallback: Use Accessibility Service to open notification / WhatsApp & send
        if (!replyActionSent) {
            val accService = MaxAccessibilityService.instance
            if (accService != null) {
                sbn.notification?.contentIntent?.send()
                delay(1200)
                accService.typeTextIntoActiveField(replyText)
                delay(500)
                accService.findAndClick("Send")
                replyActionSent = true
            }
        }

        // 4. Log and show Toast
        val successMsg = if (replyActionSent) "Auto-reply sent to $senderName: \"$replyText\"" else "Auto-reply failed to send to $senderName"
        postToast(successMsg)

        repository.logAction(
            userCommand = "WhatsApp Auto-Reply to $senderName",
            targetApp = "WhatsApp",
            aiReasoning = "Received: \"$messageText\" -> Replying: \"$replyText\"",
            actionType = "WHATSAPP_AUTO_REPLY",
            actionDetail = successMsg,
            isSuccess = replyActionSent
        )
    }

    private fun postToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "💬 $msg", Toast.LENGTH_LONG).show()
        }
    }
}
