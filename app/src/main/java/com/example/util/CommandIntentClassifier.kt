package com.example.util

import android.util.Log

enum class CommandIntent {
    HARDWARE_TASK,
    CONVERSATION_QUESTION
}

object CommandIntentClassifier {
    private const val TAG = "CommandIntentClassifier"

    private val ACTION_KEYWORDS = listOf(
        "kholo", "open", "chalu", "band", "on", "off", "lock", "scroll",
        "badhao", "kam", "mute", "unmute", "click", "tap", "type", "search",
        "lagao", "send", "bhejo", "play", "chalao", "skip", "call", "dial",
        "delete", "remove", "clean", "swipe", "up", "down", "left", "right",
        "selfie", "photo", "alarm", "reminder", "wifi", "bluetooth", "data",
        "flashlight", "torch", "brightness", "volume"
    )

    private val CONVERSATION_KEYWORDS = listOf(
        "kaise", "kyun", "kya hai", "kya hota", "joke", "kahani", "chutkula",
        "kaun", "kab", "kahan", "samjhao", "socho", "haal", "namaste", "hello",
        "hi ", "hey ", "tarika", "fayda", "nuksan", "meaning", "matlab", "batao ki",
        "bataiye ki", "kya aap", "kya tum", "kya main", "pasand", "favorite",
        "soch", "vichar", "kaisa lagta", "batayein"
    )

    fun classify(command: String): CommandIntent {
        val clean = command.trim().lowercase()

        val hasAction = ACTION_KEYWORDS.any { clean.contains(it) }
        val hasConversation = CONVERSATION_KEYWORDS.any { clean.contains(it) }

        val result = when {
            hasAction && !hasConversation -> CommandIntent.HARDWARE_TASK
            hasConversation && !hasAction -> CommandIntent.CONVERSATION_QUESTION
            hasAction -> CommandIntent.HARDWARE_TASK
            else -> CommandIntent.CONVERSATION_QUESTION
        }

        Log.d(TAG, "Command: \"$command\" -> Classified Intent: $result (hasAction=$hasAction, hasConversation=$hasConversation)")
        return result
    }
}
