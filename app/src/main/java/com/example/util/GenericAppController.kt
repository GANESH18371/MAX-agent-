package com.example.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.ScreenNodeInfo
import com.example.data.model.ScreenStateDump
import com.example.service.MaxAccessibilityService
import java.util.Locale

/**
 * GenericAppController
 * 
 * Centralized, single-source-of-truth controller for:
 * PART 1: 100% Local / Offline App Opening with wake-word stripping, alias mapping,
 *         fresh PackageManager resolution, fuzzy matching, and instant fallback launches.
 *         (NEVER sends app-open queries to Gemini API!)
 * 
 * PART 2: Generic In-App Screen Content Reading & Real Accessibility Action Execution.
 */
object GenericAppController {

    private const val TAG = "GenericAppController"

    // --- ALIASES MAP FOR POPULAR & INDIAN ANDROID APPS ---
    private val APP_ALIASES = mapOf(
        "insta" to "instagram",
        "ig" to "instagram",
        "yt" to "youtube",
        "yt music" to "youtube music",
        "ytmusic" to "youtube music",
        "fb" to "facebook",
        "fblite" to "facebook",
        "fb lite" to "facebook",
        "facebook lite" to "facebook",
        "wa" to "whatsapp",
        "whatsapp business" to "whatsapp business",
        "wa business" to "whatsapp business",
        "gmaps" to "maps",
        "google maps" to "maps",
        "playstore" to "google play store",
        "play store" to "google play store",
        "photos" to "gallery",
        "photo" to "gallery",
        "chrome" to "chrome",
        "browser" to "chrome",
        "google chrome" to "chrome",
        "dialer" to "phone",
        "call" to "phone",
        "contacts" to "contacts",
        "contact" to "contacts",
        "camera" to "camera",
        "calculator" to "calculator",
        "calc" to "calculator",
        "clock" to "clock",
        "ghadi" to "clock",
        "files" to "files",
        "file manager" to "files",
        "file explorer" to "files",
        "spotify" to "spotify",
        "gaana" to "spotify",
        "jiosaavn" to "jiosaavn",
        "telegram" to "telegram",
        "tg" to "telegram",
        "gmail" to "gmail",
        "email" to "gmail",
        "mail" to "gmail",
        "settings" to "settings",
        "setting" to "settings",
        "messages" to "messages",
        "sms" to "messages",
        "paytm" to "paytm",
        "gpay" to "google pay",
        "phonepe" to "phonepe",
        "phone pe" to "phonepe",
        "termux" to "termux"
    )

    // Wake words and polite fillers to strip
    private val WAKE_WORDS = listOf(
        "hey max", "hello max", "hi max", "suno max", "oye max", "ok max", "okay max",
        "are max", "namaste max", "max", "suno", "hey", "hello", "hi", "oye", "bhai",
        "yaar", "mera", "meri", "mere", "kripya", "please", "zara", "ek baar"
    )

    // Action verbs to strip
    private val ACTION_VERBS = listOf(
        "open karo", "open kar do", "open kar", "open", "kholo", "khol do", "khol de", "khol",
        "chalu karo", "chalu kar do", "chalu kar", "chalu", "chalao", "chala do", "chala de", "chala",
        "start karo", "start kar do", "start", "launch karo", "launch kar do", "launch",
        "application", "app", "dikhao", "laga do", "lagao"
    )

    data class InstalledApp(
        val label: String,
        val packageName: String,
        val isSystem: Boolean = false
    )

    data class AppMatchResult(
        val isMatch: Boolean,
        val app: InstalledApp?,
        val confidenceScore: Int,
        val queryUsed: String
    )

    data class OfflineOpenResult(
        val isHandled: Boolean,
        val isSuccess: Boolean,
        val appLabel: String = "",
        val packageName: String = "",
        val spokenResponseHindi: String = "",
        val message: String = ""
    )

    // =========================================================================
    // PART 1: 100% LOCAL / OFFLINE APP OPENING
    // =========================================================================

    /**
     * Checks if a user command is an app-opening request and executes it offline immediately.
     * Returns OfflineOpenResult(isHandled = true) on match.
     */
    fun tryOpenAppOffline(context: Context, rawCommand: String): OfflineOpenResult {
        val cleanQuery = extractAppTargetName(rawCommand)
        Log.d(TAG, "[APP_OPEN_REQUEST] rawCommand: '$rawCommand' -> extractedQuery: '$cleanQuery'")

        if (cleanQuery.isBlank()) {
            return OfflineOpenResult(isHandled = false, isSuccess = false)
        }

        // Direct hardware/shortcut overrides first
        if (cleanQuery == "camera" || cleanQuery == "selfie" || cleanQuery == "cam") {
            val launched = launchCameraApp(context)
            Log.d(TAG, "[APP_OPEN_LAUNCH] Camera launched: $launched")
            return OfflineOpenResult(
                isHandled = true,
                isSuccess = launched,
                appLabel = "Camera",
                packageName = "android.media.action.STILL_IMAGE_CAMERA",
                spokenResponseHindi = "Camera khol diya hai.",
                message = "Opened Camera app"
            )
        }

        if (cleanQuery == "settings" || cleanQuery == "setting") {
            val launched = launchSettingsApp(context)
            Log.d(TAG, "[APP_OPEN_LAUNCH] Settings launched: $launched")
            return OfflineOpenResult(
                isHandled = true,
                isSuccess = launched,
                appLabel = "Settings",
                packageName = "android.settings.SETTINGS",
                spokenResponseHindi = "Settings khol diya hai.",
                message = "Opened Settings"
            )
        }

        // Get fresh installed apps list directly from PackageManager
        val installedApps = getFreshInstalledApps(context)
        val matchResult = findBestMatchingApp(cleanQuery, installedApps)

        if (matchResult.isMatch && matchResult.app != null) {
            val matchedApp = matchResult.app
            Log.d(
                TAG,
                "[APP_OPEN_MATCH] query: '$cleanQuery' matched: '${matchedApp.label}' (${matchedApp.packageName}) score: ${matchResult.confidenceScore}"
            )

            val launchSuccess = launchAppByPackage(context, matchedApp.packageName)
            Log.d(TAG, "[APP_OPEN_LAUNCH] package: '${matchedApp.packageName}' isSuccess: $launchSuccess")

            val spokenName = matchedApp.label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val responseHindi = if (launchSuccess) {
                "$spokenName khol diya hai."
            } else {
                "$spokenName kholne me dikkat hui."
            }

            return OfflineOpenResult(
                isHandled = true,
                isSuccess = launchSuccess,
                appLabel = matchedApp.label,
                packageName = matchedApp.packageName,
                spokenResponseHindi = responseHindi,
                message = "Opened ${matchedApp.label} (${matchedApp.packageName})"
            )
        }

        Log.d(TAG, "[APP_OPEN_REQUEST] No installed app matched query: '$cleanQuery'")
        return OfflineOpenResult(isHandled = false, isSuccess = false)
    }

    /**
     * Strips wake-words, conversational prefixes, and action verbs to isolate the app name query.
     */
    fun extractAppTargetName(rawCommand: String): String {
        var query = rawCommand.trim().lowercase(Locale.getDefault())

        // 1. Strip wake words
        for (wake in WAKE_WORDS) {
            if (query.startsWith(wake)) {
                query = query.removePrefix(wake).trim()
            }
            if (query.endsWith(wake)) {
                query = query.removeSuffix(wake).trim()
            }
        }

        // 2. Strip verbs and keywords
        for (verb in ACTION_VERBS) {
            if (query.startsWith(verb)) {
                query = query.removePrefix(verb).trim()
            }
            if (query.endsWith(verb)) {
                query = query.removeSuffix(verb).trim()
            }
        }

        // 3. Remove punctuation
        query = query.replace(Regex("[.,!?;:'\"]"), "").trim()

        // 4. Resolve alias if query exactly matches
        val mapped = APP_ALIASES[query] ?: query
        return mapped
    }

    /**
     * Fetches the complete, fresh list of launcher-enabled installed applications from PackageManager.
     */
    fun getFreshInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val list = mutableListOf<InstalledApp>()
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString().trim()
            val pkg = info.activityInfo.packageName.trim()
            if (pkg.isNotBlank() && label.isNotBlank()) {
                list.add(InstalledApp(label = label, packageName = pkg))
            }
        }
        return list
    }

    /**
     * Fuzzy matching algorithm matching target query against all installed apps.
     */
    fun findBestMatchingApp(query: String, installedApps: List<InstalledApp>): AppMatchResult {
        if (query.isBlank() || installedApps.isEmpty()) {
            return AppMatchResult(false, null, 0, query)
        }

        val normalizedQuery = query.lowercase(Locale.getDefault())
        val targetAlias = APP_ALIASES[normalizedQuery] ?: normalizedQuery

        var bestMatch: InstalledApp? = null
        var bestScore = 0

        for (app in installedApps) {
            val label = app.label.lowercase(Locale.getDefault())
            val pkg = app.packageName.lowercase(Locale.getDefault())

            var score = 0

            when {
                // Exact label match
                label == normalizedQuery || label == targetAlias -> score = 100
                
                // Package name exact suffix match (e.g. com.whatsapp vs whatsapp)
                pkg.endsWith(".$normalizedQuery") || pkg.endsWith(".$targetAlias") -> score = 95
                
                // Package name contains exact target
                pkg.contains(normalizedQuery) || pkg.contains(targetAlias) -> score = 90
                
                // Label starts with query
                label.startsWith(normalizedQuery) || label.startsWith(targetAlias) -> score = 85
                
                // Label contains query
                label.contains(normalizedQuery) || label.contains(targetAlias) -> score = 75
                
                // Query contains label
                normalizedQuery.contains(label) || targetAlias.contains(label) -> score = 70

                else -> {
                    // Fuzzy similarity score
                    val simScore = calculateStringSimilarity(normalizedQuery, label)
                    if (simScore >= 65) {
                        score = simScore
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestMatch = app
            }
        }

        return if (bestScore >= 60 && bestMatch != null) {
            AppMatchResult(true, bestMatch, bestScore, query)
        } else {
            AppMatchResult(false, null, bestScore, query)
        }
    }

    /**
     * Launches an application directly by package name with fallback flags.
     */
    fun launchAppByPackage(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "[APP_OPEN_LAUNCH] Error launching package: $packageName", e)
            false
        }
    }

    private fun launchCameraApp(context: Context): Boolean {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            false
        }
    }

    private fun launchSettingsApp(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening settings", e)
            false
        }
    }

    private fun calculateStringSimilarity(s1: String, s2: String): Int {
        if (s1 == s2) return 100
        if (s1.isEmpty() || s2.isEmpty()) return 0

        val maxLen = maxOf(s1.length, s2.length)
        val distance = computeLevenshteinDistance(s1, s2)
        val similarity = (1.0 - distance.toDouble() / maxLen.toDouble()) * 100.0
        return similarity.toInt().coerceIn(0, 100)
    }

    private fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }


    // =========================================================================
    // PART 2: IN-APP CONTROL VIA ACCESSIBILITY SERVICE
    // =========================================================================

    /**
     * Reads real screen content generically from active window hierarchy.
     */
    fun readScreenContent(service: MaxAccessibilityService?): ScreenStateDump {
        if (service == null) {
            Log.w(TAG, "[SCREEN_READ] AccessibilityService is NULL or disabled.")
            return ScreenStateDump(
                packageName = "Unknown",
                rootNodesFormatted = "Accessibility service is off. Screen content unavailable.",
                totalClickables = 0
            )
        }

        val dump = service.getScreenStateDump()
        Log.d(
            TAG,
            "[SCREEN_READ] package: '${dump.packageName}', totalClickables: ${dump.totalClickables}, content preview: ${dump.rootNodesFormatted.take(120)}..."
        )
        return dump
    }

    /**
     * Executes real UI Accessibility action on current screen.
     */
    fun executeAccessibilityAction(
        service: MaxAccessibilityService?,
        action: String,
        target: String = "",
        textToType: String = "",
        x: Float = 540f,
        y: Float = 1000f
    ): Boolean {
        if (service == null) {
            Log.e(TAG, "[ACCESSIBILITY_ACTION] Failed: AccessibilityService is null")
            return false
        }

        val cleanAction = action.trim().uppercase(Locale.getDefault())
        var success = false

        when (cleanAction) {
            "CLICK", "TAP" -> {
                if (target.isNotBlank()) {
                    success = service.findAndClick(target)
                    if (!success) {
                        Log.d(TAG, "[ACCESSIBILITY_ACTION] Target '$target' click not found, trying fallback coordinates ($x, $y)")
                        service.tapAtCoordinates(x, y)
                        success = true
                    }
                } else {
                    service.tapAtCoordinates(x, y)
                    success = true
                }
            }

            "TYPE", "INPUT", "TEXT" -> {
                if (textToType.isNotBlank()) {
                    if (target.isNotBlank()) {
                        service.findAndClick(target)
                    }
                    success = service.typeTextIntoActiveField(textToType)
                }
            }

            "SCROLL_UP" -> {
                service.performSwipe(540f, 400f, 540f, 1500f, 300)
                success = true
            }

            "SCROLL_DOWN", "SCROLL" -> {
                service.performSwipe(540f, 1500f, 540f, 400f, 300)
                success = true
            }

            "SWIPE_LEFT" -> {
                service.performSwipe(900f, 1000f, 150f, 1000f, 300)
                success = true
            }

            "SWIPE_RIGHT" -> {
                service.performSwipe(150f, 1000f, 900f, 1000f, 300)
                success = true
            }

            "BACK" -> {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }

            "HOME" -> {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }

            "RECENTS" -> {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            }

            "NOTIFICATIONS" -> {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }

            "QUICK_SETTINGS" -> {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            }

            "LOCK_SCREEN" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
            }

            else -> {
                Log.w(TAG, "[ACCESSIBILITY_ACTION] Unknown action type: $action")
                success = false
            }
        }

        Log.d(
            TAG,
            "[ACCESSIBILITY_ACTION] action: '$cleanAction', target: '$target', text: '$textToType', isSuccess: $success"
        )
        return success
    }
}
