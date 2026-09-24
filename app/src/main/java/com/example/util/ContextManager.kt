package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.ArrayDeque

data class ContextInteraction(
    val userCommand: String,
    val resolvedCommand: String,
    val appPackage: String,
    val appName: String,
    val actionType: String,
    val targetText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ContextResolutionResult(
    val originalCommand: String,
    val resolvedCommand: String,
    val isContextUsed: Boolean,
    val referringWordFound: String? = null,
    val needsClarification: Boolean = false,
    val clarificationPrompt: String = ""
)

object ContextManager {

    private const val TAG = "ContextManager"
    private const val MAX_HISTORY_SIZE = 5

    // In-memory short-term interaction history (last 3-5 interactions)
    private val history = ArrayDeque<ContextInteraction>(MAX_HISTORY_SIZE)

    // Current active app state
    private var currentActivePackage: String = "Unknown"
    private var currentActiveAppName: String = "Unknown"

    // Last recorded action
    private var lastActionType: String = ""
    private var lastTargetText: String = ""
    private var lastSpokenResponse: String = ""

    private val REFERRING_WORDS = listOf(
        "iska", "iski", "isko", "isse", "isme", "ise",
        "yeh wala", "ye wala", "yeh wali", "ye wali", "is wale", "iss wale",
        "usko", "uska", "uski", "usse", "uspe", "usme", "use",
        "wahi", "wahi wala", "wahi wali",
        "iss app", "is app", "is application"
    )

    private val SYSTEM_OR_HOME_PACKAGES = setOf(
        "com.android.launcher", "com.google.android.apps.nexuslauncher",
        "com.mi.android.globallauncher", "com.sec.android.app.launcher",
        "com.oppo.launcher", "com.oneplus.launcher", "com.huawei.android.launcher",
        "com.example", "com.aistudio.maxvoiceassistant.app", "Unknown"
    )

    /**
     * Updates currently active app from Accessibility Service or Foreground detector.
     */
    fun updateCurrentApp(context: Context, packageName: String) {
        if (packageName.isBlank() || packageName == "Unknown" || packageName.contains("systemui")) {
            return
        }

        currentActivePackage = packageName
        currentActiveAppName = resolveAppNameFromPackage(context, packageName)

        Log.d(TAG, "CONTEXT_CURRENT_APP: $currentActiveAppName ($currentActivePackage)")
    }

    /**
     * Resolves human-friendly app label from package name.
     */
    private fun resolveAppNameFromPackage(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            when {
                packageName.contains("youtube") -> "YouTube"
                packageName.contains("whatsapp") -> "WhatsApp"
                packageName.contains("instagram") -> "Instagram"
                packageName.contains("facebook") -> "Facebook"
                packageName.contains("chrome") -> "Chrome"
                packageName.contains("maps") -> "Google Maps"
                packageName.contains("camera") -> "Camera"
                packageName.contains("settings") -> "Settings"
                packageName.contains("spotify") -> "Spotify"
                packageName.contains("dialer") || packageName.contains("phone") -> "Phone"
                else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

    fun getCurrentAppName(): String = currentActiveAppName
    fun getCurrentPackage(): String = currentActivePackage
    fun getLastActionType(): String = lastActionType
    fun getLastTargetText(): String = lastTargetText

    /**
     * Checks if command contains referring words and resolves them with current context.
     * If ambiguous/unknown, asks for clarification instead of wrong guessing.
     */
    fun resolveCommandContext(rawCommand: String): ContextResolutionResult {
        val cleanCmd = rawCommand.trim().lowercase()

        // 1. Identify if any referring words exist
        val foundWord = REFERRING_WORDS.firstOrNull { ref ->
            cleanCmd.contains(Regex("\\b${Regex.escape(ref)}\\b"))
        }

        if (foundWord == null) {
            Log.d(TAG, "CONTEXT_CURRENT_APP: $currentActiveAppName")
            Log.d(TAG, "CONTEXT_USED: false")
            return ContextResolutionResult(
                originalCommand = rawCommand,
                resolvedCommand = rawCommand,
                isContextUsed = false
            )
        }

        Log.d(TAG, "Detected referring word '$foundWord' in command: \"$rawCommand\"")

        // 2. Check if context is available
        val hasActiveApp = currentActiveAppName.isNotBlank() &&
                currentActiveAppName != "Unknown" &&
                !isHomeScreenOrSelf(currentActivePackage)

        val lastInteraction = history.lastOrNull()
        val hasRecentContext = hasActiveApp || lastInteraction != null

        // 3. If context is missing, clarify instead of wrong guessing
        if (!hasRecentContext) {
            Log.d(TAG, "CONTEXT_CURRENT_APP: Unknown")
            Log.d(TAG, "CONTEXT_USED: false")
            return ContextResolutionResult(
                originalCommand = rawCommand,
                resolvedCommand = rawCommand,
                isContextUsed = false,
                referringWordFound = foundWord,
                needsClarification = true,
                clarificationPrompt = "Aap kiska matlab keh rahe hain? Kripya naam bataiye."
            )
        }

        // 4. Resolve referring word with the active context
        val contextTarget = when {
            hasActiveApp -> currentActiveAppName
            lastInteraction != null && lastInteraction.appName.isNotBlank() -> lastInteraction.appName
            lastInteraction != null && lastInteraction.targetText.isNotBlank() -> lastInteraction.targetText
            else -> ""
        }

        if (contextTarget.isBlank()) {
            Log.d(TAG, "CONTEXT_CURRENT_APP: Unknown")
            Log.d(TAG, "CONTEXT_USED: false")
            return ContextResolutionResult(
                originalCommand = rawCommand,
                resolvedCommand = rawCommand,
                isContextUsed = false,
                referringWordFound = foundWord,
                needsClarification = true,
                clarificationPrompt = "Aap kiska matlab keh rahe hain?"
            )
        }

        // 5. Build intelligent contextual resolution
        var resolved = cleanCmd

        when (foundWord) {
            "iska", "iski", "uska", "uski" -> {
                // Example: "iska volume badhao" -> "volume badhao" or "$contextTarget ka volume badhao"
                if (cleanCmd.contains("volume") || cleanCmd.contains("sound") || cleanCmd.contains("awaaz")) {
                    resolved = cleanCmd.replace(foundWord, "").trim()
                } else {
                    resolved = cleanCmd.replace(foundWord, "$contextTarget ka")
                }
            }
            "isko", "usko", "ise", "use" -> {
                // Example: "isko band karo" -> "$contextTarget band karo"
                // Example: "isko kholo" -> "$contextTarget kholo"
                resolved = cleanCmd.replace(foundWord, contextTarget)
            }
            "isme", "usme", "uspe" -> {
                // Example: "isme search karo X" -> "$contextTarget me search karo X"
                resolved = cleanCmd.replace(foundWord, "$contextTarget me")
            }
            "isse", "usse" -> {
                resolved = cleanCmd.replace(foundWord, "$contextTarget se")
            }
            "wahi", "wahi wala", "wahi wali", "yeh wala", "ye wala", "is wale", "iss wale" -> {
                val targetToUse = if (lastInteraction?.targetText?.isNotBlank() == true) {
                    lastInteraction.targetText
                } else {
                    contextTarget
                }
                resolved = cleanCmd.replace(foundWord, targetToUse)
            }
            "iss app", "is app", "is application" -> {
                resolved = cleanCmd.replace(foundWord, contextTarget)
            }
        }

        // Clean extra duplicate spaces
        resolved = resolved.replace(Regex("\\s+"), " ").trim()

        Log.d(TAG, "CONTEXT_CURRENT_APP: $currentActiveAppName")
        Log.d(TAG, "CONTEXT_USED: true")

        return ContextResolutionResult(
            originalCommand = rawCommand,
            resolvedCommand = resolved,
            isContextUsed = true,
            referringWordFound = foundWord
        )
    }

    /**
     * Records an interaction in short-term memory (last 3-5 interactions).
     */
    fun recordInteraction(
        userCommand: String,
        resolvedCommand: String,
        appPackage: String,
        appName: String,
        actionType: String,
        targetText: String = "",
        spokenResponse: String = ""
    ) {
        lastActionType = actionType
        lastTargetText = targetText
        lastSpokenResponse = spokenResponse

        if (appPackage.isNotBlank() && appPackage != "Unknown" && !isHomeScreenOrSelf(appPackage)) {
            currentActivePackage = appPackage
            if (appName.isNotBlank()) {
                currentActiveAppName = appName
            }
        }

        if (history.size >= MAX_HISTORY_SIZE) {
            history.removeFirst()
        }

        val interaction = ContextInteraction(
            userCommand = userCommand,
            resolvedCommand = resolvedCommand,
            appPackage = currentActivePackage,
            appName = currentActiveAppName,
            actionType = actionType,
            targetText = targetText
        )
        history.addLast(interaction)

        Log.d(TAG, "Recorded interaction in short-term context: ${interaction.appName} -> ${interaction.actionType} (History size: ${history.size})")
    }

    /**
     * Returns the formatted recent interactions for context-prompting if needed.
     */
    fun getRecentInteractionsSummary(): String {
        if (history.isEmpty()) return "No recent interactions."
        return history.joinToString("\n") { item ->
            "- Command: \"${item.userCommand}\" (App: ${item.appName}) Action: ${item.actionType} ${if (item.targetText.isNotBlank()) "[${item.targetText}]" else ""}"
        }
    }

    private fun isHomeScreenOrSelf(pkg: String): Boolean {
        return SYSTEM_OR_HOME_PACKAGES.any { pkg.contains(it, ignoreCase = true) }
    }
}
