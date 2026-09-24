package com.example.util

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import com.example.service.MaxAccessibilityService

object CallController {

    private const val TAG = "CallController"

    var isCallControlActive: Boolean = true
    private var isCurrentlyRinging: Boolean = false
    private var lastIncomingNumber: String = ""
    private var lastCallerName: String = ""

    fun onIncomingCallDetected(context: Context, incomingNumber: String?) {
        if (!isCallControlActive) return

        val number = incomingNumber ?: ""
        if (isCurrentlyRinging && lastIncomingNumber == number) return

        isCurrentlyRinging = true
        lastIncomingNumber = number

        val callerName = getContactName(context, number).ifBlank {
            if (number.isNotBlank()) "Number $number" else "Unknown Caller"
        }
        lastCallerName = callerName

        Log.d(TAG, "Incoming Call Detected from: $callerName ($number)")

        // PART 1: INCOMING CALL ANNOUNCEMENT
        val announcementText = "Aapko $callerName ki call aa rahi hai."
        postToast(context, "📞 Incoming Call: $callerName")

        val voiceAssistant = VoiceAssistantManager(context)
        voiceAssistant.speak(announcementText) {
            // PART 2: VOICE ACCEPT/REJECT LISTENING WINDOW
            Log.d(TAG, "Finished speaking announcement. Opening Voice Command Listener...")
            Handler(Looper.getMainLooper()).postDelayed({
                listenForCallCommand(context, voiceAssistant)
            }, 500)
        }
    }

    fun onCallEnded(context: Context) {
        if (isCurrentlyRinging) {
            Log.d(TAG, "Call ended or answered")
            isCurrentlyRinging = false
            lastIncomingNumber = ""
            lastCallerName = ""
        }
    }

    private fun listenForCallCommand(context: Context, voiceAssistant: VoiceAssistantManager) {
        if (!isCurrentlyRinging) return

        postToast(context, "🎙️ Boliyen: 'Utha lo', 'Reject karo' ya 'Speaker karo'")
        voiceAssistant.onSpeechResultListener = { commandText ->
            processCallVoiceCommand(context, commandText)
        }
        voiceAssistant.startListening()
    }

    fun processCallVoiceCommand(context: Context, rawCommand: String): Boolean {
        val cleanCmd = rawCommand.trim().lowercase()
        Log.d(TAG, "Call Voice Command received: \"$cleanCmd\"")

        // 1. ACCEPT CALL
        if (cleanCmd.contains("utha lo") || cleanCmd.contains("receive") ||
            cleanCmd.contains("accept") || cleanCmd.contains("answer") ||
            cleanCmd.contains("call uthao") || cleanCmd.contains("uthao")
        ) {
            val withSpeaker = cleanCmd.contains("speaker") || cleanCmd.contains("loud")
            acceptIncomingCall(context, enableSpeaker = withSpeaker)
            return true
        }

        // 2. REJECT / DISCONNECT CALL
        if (cleanCmd.contains("katt do") || cleanCmd.contains("kat do") ||
            cleanCmd.contains("reject") || cleanCmd.contains("decline") ||
            cleanCmd.contains("call kaato") || cleanCmd.contains("disconnect") || cleanCmd.contains("kaat do")
        ) {
            rejectIncomingCall(context)
            return true
        }

        // 3. PART 3 — MAX KHUD BAAT KARE (AI Conversation Mode Placeholder)
        if (cleanCmd.contains("max tum baat karo") || cleanCmd.contains("max baat karo") || cleanCmd.contains("tum baat karo")) {
            startAiCallConversationMode(context, lastCallerName, lastIncomingNumber)
            return true
        }

        return false
    }

    @SuppressLint("MissingPermission")
    fun acceptIncomingCall(context: Context, enableSpeaker: Boolean = false) {
        Log.d(TAG, "Attempting to accept incoming call. Speaker = $enableSpeaker")
        postToast(context, "✅ Accepting Call ${if (enableSpeaker) "on Speaker" else ""}...")

        var success = false

        // Primary method: TelecomManager API (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                @Suppress("DEPRECATION")
                telecomManager.acceptRingingCall()
                success = true
                Log.d(TAG, "Call accepted via TelecomManager.acceptRingingCall()")
            } catch (e: Exception) {
                Log.w(TAG, "TelecomManager accept failed, trying Accessibility Service fallback", e)
            }
        }

        // Fallback method: Accessibility Service tap
        if (!success) {
            val service = MaxAccessibilityService.instance
            if (service != null) {
                val clicked = service.findAndClick("Answer") ||
                        service.findAndClick("Accept") ||
                        service.findAndClick("Receive") ||
                        service.findAndClick("Swipe up to answer") ||
                        service.findAndClick("उठाएं")
                success = clicked
                Log.d(TAG, "Accessibility Service accept click status: $clicked")
            }
        }

        // Enable Speakerphone if requested
        if (enableSpeaker) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.mode = AudioManager.MODE_IN_CALL
                    audioManager.isSpeakerphoneOn = true
                    postToast(context, "🔊 Speakerphone Turned ON")
                    Log.d(TAG, "Speakerphone enabled successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable speakerphone", e)
                }
            }, 1000)
        }

        isCurrentlyRinging = false
    }

    @SuppressLint("MissingPermission")
    fun rejectIncomingCall(context: Context) {
        Log.d(TAG, "Attempting to reject incoming call")
        postToast(context, "❌ Rejecting Call...")

        var success = false

        // Primary method: TelecomManager endCall (Android 9.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                success = telecomManager.endCall()
                Log.d(TAG, "Call ended via TelecomManager.endCall() status = $success")
            } catch (e: Exception) {
                Log.w(TAG, "TelecomManager endCall failed, trying Accessibility Service fallback", e)
            }
        }

        // Fallback method: Accessibility Service tap
        if (!success) {
            val service = MaxAccessibilityService.instance
            if (service != null) {
                val clicked = service.findAndClick("Decline") ||
                        service.findAndClick("Reject") ||
                        service.findAndClick("Dismiss") ||
                        service.findAndClick("Hang up") ||
                        service.findAndClick("काटें")
                Log.d(TAG, "Accessibility Service reject click status: $clicked")
            }
        }

        isCurrentlyRinging = false
    }

    // --- PART 3: MAX KHUD BAAT KARE STRUCTURE / PLACEHOLDER ---
    fun startAiCallConversationMode(context: Context, callerName: String, callerNumber: String) {
        Log.d(TAG, "Initializing Max AI Auto-Conversation Mode for caller: $callerName")
        postToast(context, "🤖 Max is taking over the call to speak with $callerName...")

        // Step A: Answer Call & enable Speaker/Mic
        acceptIncomingCall(context, enableSpeaker = true)

        // Step B: Greet Caller
        Handler(Looper.getMainLooper()).postDelayed({
            val greeting = "Namaste $callerName! Main Max hoon, user ka AI assistant. Aap batayein kya kaam hai?"
            VoiceAssistantManager(context).speak(greeting) {
                Log.d(TAG, "AI Conversation Greeting spoken. Ready for Gemini Live API pipeline integration.")
            }
        }, 1500)
    }

    private fun getContactName(context: Context, phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        return try {
            val uri: Uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return it.getString(nameIndex) ?: ""
                    }
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name for number: $phoneNumber", e)
            ""
        }
    }

    private fun postToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
