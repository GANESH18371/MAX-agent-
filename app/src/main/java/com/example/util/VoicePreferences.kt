package com.example.util

import android.content.Context
import android.content.SharedPreferences

enum class VoiceGender {
    MALE,
    FEMALE,
    NEUTRAL
}

data class MaxVoiceOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val gender: VoiceGender,
    val systemVoiceName: String? = null,
    val pitch: Float = 1.0f,
    val speechRate: Float = 0.98f,
    val languageCode: String = "hi-IN",
    val isNetworkRequired: Boolean = false,
    val qualityTag: String = "High Quality"
)

object VoicePreferences {
    private const val PREFS_NAME = "max_voice_preferences"
    private const val KEY_SELECTED_VOICE_ID = "selected_voice_id"

    fun getSelectedVoiceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_VOICE_ID, "voice_male_hindi") ?: "voice_male_hindi"
    }

    fun saveSelectedVoiceId(context: Context, voiceId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.edit().putString(KEY_SELECTED_VOICE_ID, voiceId).commit()
    }
}
