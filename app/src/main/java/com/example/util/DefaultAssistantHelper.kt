package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.util.Log
import android.widget.Toast
import com.example.service.MaxVoiceInteractionService

object DefaultAssistantHelper {

    private const val TAG = "DefaultAssistantHelper"

    /**
     * Checks if Max is currently set as the default digital assistant on this device.
     */
    fun isDefaultAssistant(context: Context): Boolean {
        try {
            val componentName = ComponentName(context, MaxVoiceInteractionService::class.java)
            if (VoiceInteractionService.isActiveService(context, componentName)) {
                return true
            }

            val assistantSetting = Settings.Secure.getString(context.contentResolver, "assistant")
            val voiceInteractionSetting = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")

            val isAssistant = assistantSetting != null && assistantSetting.contains(context.packageName)
            val isVoiceService = voiceInteractionSetting != null && voiceInteractionSetting.contains(context.packageName)

            return isAssistant || isVoiceService
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default assistant status", e)
            return false
        }
    }

    /**
     * Opens Android Settings -> Apps -> Default apps -> Digital assistant app.
     */
    fun openAssistantSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                Log.d(TAG, "Opened settings via: ${intent.action}")
                Toast.makeText(context, "Max ko 'Default Digital Assistant' select karein", Toast.LENGTH_LONG).show()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch intent: ${intent.action}", e)
            }
        }
    }
}
