package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

data class AppCandidate(
    val appLabel: String,
    val packageName: String,
    val score: Int
)

enum class ConfirmationMatchStatus {
    DIRECT_EXECUTE,    // Score >= 90% -> Direct launch without asking
    NEED_CONFIRMATION, // Score 50-89% -> Ask: "[app-name] se matlab hai?"
    MULTIPLE_MATCHES,  // Multiple close candidates or Score < 50% -> Ask: "Mujhe [X] aur [Y] mile, kaunsa?"
    NOT_FOUND          // Score == 0 / No installed match -> "Yeh app nahi mila"
}

data class ConfirmationEvaluationResult(
    val status: ConfirmationMatchStatus,
    val bestCandidate: AppCandidate?,
    val candidates: List<AppCandidate>,
    val confidenceScore: Int,
    val spokenPromptHindi: String = ""
)

data class ConfirmationResolution(
    val isHandled: Boolean,
    val action: String, // "executed", "cancelled", "candidate_selected", "unrelated"
    val spokenResponseHindi: String = "",
    val executedPackage: String = "",
    val executedAppName: String = ""
)

object ConfirmationManager {

    private const val TAG = "ConfirmationManager"

    // --- PENDING CONFIRMATION STATE ---
    @Volatile
    var isAwaitingConfirmation: Boolean = false
        private set

    @Volatile
    var pendingCommand: String = ""
        private set

    @Volatile
    var pendingTargetPackage: String = ""
        private set

    @Volatile
    var pendingTargetAppName: String = ""
        private set

    @Volatile
    var pendingCandidates: List<AppCandidate> = emptyList()
        private set

    @Volatile
    var pendingActionType: String = "APP_LAUNCH" // "APP_LAUNCH" or "TERMUX_DESTRUCTIVE"
        private set

    @Volatile
    var pendingTermuxCommand: String = ""
        private set

    @Volatile
    var pendingTermuxSource: String = "LOCAL"
        private set

    private val AFFIRMATIVE_WORDS = setOf(
        "haan", "ha", "haa", "han", "yes", "sahi hai", "wahi", "kholo",
        "chalu karo", "ok", "okay", "sure", "bilkul", "yahi", "correct",
        "yahi wala", "wahi wala", "open karo"
    )

    private val NEGATIVE_WORDS = setOf(
        "nahi", "na", "no", "nah", "mat karo", "cancel", "rehne do",
        "galat", "stop", "chhod do", "nahi chahiye", "nope"
    )

    private val ALIAS_MAP = mapOf(
        "insta" to "instagram",
        "ig" to "instagram",
        "yt" to "youtube",
        "yt music" to "youtube music",
        "fb" to "facebook",
        "fblite" to "facebook",
        "fb lite" to "facebook",
        "wa" to "whatsapp",
        "whatsapp business" to "whatsapp",
        "gmaps" to "maps",
        "google maps" to "maps",
        "playstore" to "play store",
        "play store" to "play store",
        "photos" to "gallery",
        "photo" to "gallery",
        "chrome" to "chrome",
        "browser" to "chrome",
        "settings" to "settings",
        "camera" to "camera",
        "dialer" to "phone",
        "call" to "phone",
        "spotify" to "spotify",
        "telegram" to "telegram"
    )

    /**
     * Evaluates installed apps for a given query and calculates confidence score (0-100%).
     */
    fun evaluateAppQuery(context: Context, rawCommand: String): ConfirmationEvaluationResult {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val installedApps: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)

        val cleanQuery = rawCommand.lowercase()
            .replace("kholo", "")
            .replace("open", "")
            .replace("chalu karo", "")
            .replace("chalu", "")
            .replace("chalao", "")
            .replace("karo", "")
            .replace("app", "")
            .replace("application", "")
            .replace("start", "")
            .replace("launch", "")
            .trim()

        if (cleanQuery.isBlank()) {
            return ConfirmationEvaluationResult(
                status = ConfirmationMatchStatus.NOT_FOUND,
                bestCandidate = null,
                candidates = emptyList(),
                confidenceScore = 0,
                spokenPromptHindi = "Aap kaunsa app kholna chahte hain?"
            )
        }

        val targetAlias = ALIAS_MAP[cleanQuery] ?: cleanQuery
        val scoredList = mutableListOf<AppCandidate>()

        for (resolveInfo in installedApps) {
            val appLabel = resolveInfo.loadLabel(pm).toString().trim()
            val lowerLabel = appLabel.lowercase()
            val packageName = resolveInfo.activityInfo.packageName.lowercase().trim()

            var score = 0

            // 1. EXACT MATCH (95-100%)
            if (lowerLabel == cleanQuery || lowerLabel == targetAlias) {
                score = 98
            } else if (packageName.endsWith(".$cleanQuery") || packageName.endsWith(".$targetAlias")) {
                score = 95
            }
            // 2. KNOWN DIRECT ALIAS EXACT MATCH (e.g. "insta" -> "Instagram")
            else if (cleanQuery in ALIAS_MAP && lowerLabel == ALIAS_MAP[cleanQuery]) {
                score = 95
            }
            // 3. STRONG PREFIX MATCH
            else if (lowerLabel.startsWith("$cleanQuery ") || lowerLabel.startsWith("$targetAlias ")) {
                score = 85
            } else if (lowerLabel.startsWith(cleanQuery) || lowerLabel.startsWith(targetAlias)) {
                score = 80
            }
            // 4. CONTAINS MATCH (Fuzzy / Partial)
            else if (lowerLabel.contains(cleanQuery) || lowerLabel.contains(targetAlias)) {
                score = 68
            } else if (packageName.contains(cleanQuery) || packageName.contains(targetAlias)) {
                score = 60
            }

            if (score > 0) {
                scoredList.add(AppCandidate(appLabel = appLabel, packageName = resolveInfo.activityInfo.packageName, score = score))
            }
        }

        scoredList.sortByDescending { it.score }

        // Camera fallback check if not matched via launcher activity
        if (scoredList.isEmpty() && (cleanQuery.contains("camera") || targetAlias.contains("camera"))) {
            scoredList.add(AppCandidate("Camera", "android.media.action.STILL_IMAGE_CAMERA", 95))
        }

        if (scoredList.isEmpty()) {
            Log.d(TAG, "CONFIDENCE_SCORE: $cleanQuery = 0%")
            val spokenName = cleanQuery.replaceFirstChar { it.uppercase() }
            return ConfirmationEvaluationResult(
                status = ConfirmationMatchStatus.NOT_FOUND,
                bestCandidate = null,
                candidates = emptyList(),
                confidenceScore = 0,
                spokenPromptHindi = "Mujhe '$spokenName' app aapke phone me nahi mila."
            )
        }

        val topCandidate = scoredList[0]
        var finalScore = topCandidate.score

        // MULTIPLE CLOSE MATCHES PENALTY (e.g. "Camera" vs "Camera Pro")
        val closeCompetitors = scoredList.filter { it.score >= 60 }
        val isAmbiguous = closeCompetitors.size >= 2 && Math.abs(closeCompetitors[0].score - closeCompetitors[1].score) <= 15

        if (isAmbiguous) {
            // Lower confidence due to ambiguity
            finalScore = Math.min(finalScore - 30, 48)
            Log.d(TAG, "CONFIDENCE_SCORE: ${topCandidate.appLabel} = $finalScore% (Multiple matches: ${closeCompetitors.map { it.appLabel }})")

            val c1 = closeCompetitors[0].appLabel
            val c2 = closeCompetitors[1].appLabel
            val prompt = "Mujhe $c1 aur $c2 mile, kaunsa kholoon?"

            return ConfirmationEvaluationResult(
                status = ConfirmationMatchStatus.MULTIPLE_MATCHES,
                bestCandidate = topCandidate,
                candidates = closeCompetitors.take(3),
                confidenceScore = finalScore,
                spokenPromptHindi = prompt
            )
        }

        Log.d(TAG, "CONFIDENCE_SCORE: ${topCandidate.appLabel} = $finalScore%")

        // THRESHOLD-BASED DECISION
        return when {
            finalScore >= 90 -> {
                // SEEDHA EXECUTE
                ConfirmationEvaluationResult(
                    status = ConfirmationMatchStatus.DIRECT_EXECUTE,
                    bestCandidate = topCandidate,
                    candidates = scoredList,
                    confidenceScore = finalScore,
                    spokenPromptHindi = "${topCandidate.appLabel} khol raha hoon."
                )
            }
            finalScore in 50..89 -> {
                // CHHOTA CONFIRMATION POOCHO
                val prompt = "${topCandidate.appLabel} se matlab hai?"
                ConfirmationEvaluationResult(
                    status = ConfirmationMatchStatus.NEED_CONFIRMATION,
                    bestCandidate = topCandidate,
                    candidates = scoredList,
                    confidenceScore = finalScore,
                    spokenPromptHindi = prompt
                )
            }
            else -> {
                // Confidence < 50%
                val prompt = if (scoredList.size >= 2) {
                    "Mujhe ${scoredList[0].appLabel} aur ${scoredList[1].appLabel} mile, kaunsa?"
                } else {
                    "${topCandidate.appLabel} kholna hai?"
                }
                ConfirmationEvaluationResult(
                    status = ConfirmationMatchStatus.MULTIPLE_MATCHES,
                    bestCandidate = topCandidate,
                    candidates = scoredList.take(2),
                    confidenceScore = finalScore,
                    spokenPromptHindi = prompt
                )
            }
        }
    }

    /**
     * Stores pending confirmation state when user confirmation is requested.
     */
    fun setPendingConfirmation(
        command: String,
        targetPackage: String,
        targetAppName: String,
        candidates: List<AppCandidate> = emptyList()
    ) {
        isAwaitingConfirmation = true
        pendingCommand = command
        pendingTargetPackage = targetPackage
        pendingTargetAppName = targetAppName
        pendingCandidates = candidates
        pendingActionType = "APP_LAUNCH"

        Log.d(TAG, "CONFIRMATION_STATE: awaiting=true, pendingCommand=$command")
    }

    fun setPendingTermuxDestructive(originalVoiceCommand: String, bashCommand: String, source: String) {
        isAwaitingConfirmation = true
        pendingActionType = "TERMUX_DESTRUCTIVE"
        pendingCommand = originalVoiceCommand
        pendingTermuxCommand = bashCommand
        pendingTermuxSource = source
        pendingTargetPackage = ""
        pendingTargetAppName = "Termux Shell"
        pendingCandidates = emptyList()
        Log.d(TAG, "CONFIRMATION_STATE: awaiting=true, pendingCommand=$originalVoiceCommand (TERMUX_DESTRUCTIVE: $bashCommand)")
    }

    /**
     * Clears pending confirmation state.
     */
    fun clearConfirmation() {
        Log.d(TAG, "CONFIRMATION_STATE: awaiting=false, pendingCommand=")
        isAwaitingConfirmation = false
        pendingCommand = ""
        pendingTargetPackage = ""
        pendingTargetAppName = ""
        pendingCandidates = emptyList()
        pendingActionType = "APP_LAUNCH"
        pendingTermuxCommand = ""
        pendingTermuxSource = "LOCAL"
    }

    /**
     * Processes input when isAwaitingConfirmation is true.
     */
    fun handleConfirmationResponse(context: Context, rawInput: String): ConfirmationResolution {
        val cleanInput = rawInput.trim().lowercase()

        // 1. Check for YES / AFFIRMATIVE
        val isYes = AFFIRMATIVE_WORDS.any { cleanInput == it || cleanInput.startsWith("$it ") || cleanInput.endsWith(" $it") }

        if (isYes) {
            Log.d(TAG, "CONFIRMATION_RESOLVED: haan, action=executed")
            if (pendingActionType == "TERMUX_DESTRUCTIVE") {
                val bashCmd = pendingTermuxCommand
                val src = pendingTermuxSource
                clearConfirmation()
                TermuxCommandManager.executeTermuxCommand(context, bashCmd, src)
                return ConfirmationResolution(
                    isHandled = true,
                    action = "executed",
                    spokenResponseHindi = "Command chala raha hoon: $bashCmd",
                    executedPackage = "com.termux",
                    executedAppName = "Termux"
                )
            }

            val targetPkg = pendingTargetPackage
            val targetName = pendingTargetAppName
            val success = launchPackageOrCamera(context, targetPkg)

            clearConfirmation()

            return ConfirmationResolution(
                isHandled = true,
                action = "executed",
                spokenResponseHindi = "$targetName khol raha hoon.",
                executedPackage = targetPkg,
                executedAppName = targetName
            )
        }

        // 2. Check for NO / NEGATIVE
        val isNo = NEGATIVE_WORDS.any { cleanInput == it || cleanInput.startsWith("$it ") || cleanInput.endsWith(" $it") }

        if (isNo) {
            Log.d(TAG, "CONFIRMATION_RESOLVED: nahi, action=cancelled")
            val isTermux = pendingActionType == "TERMUX_DESTRUCTIVE"
            clearConfirmation()

            return ConfirmationResolution(
                isHandled = true,
                action = "cancelled",
                spokenResponseHindi = if (isTermux) "Theek hai, destructive command cancel kar di." else "Theek hai, to phir kya karna hai?"
            )
        }

        // 3. Check for CANDIDATE SELECTION ("pehla", "doosra", or app name)
        if (pendingCandidates.isNotEmpty()) {
            val selectedCandidate = when {
                cleanInput.contains("pehla") || cleanInput == "1" || cleanInput == "first" -> pendingCandidates.getOrNull(0)
                cleanInput.contains("doosra") || cleanInput == "2" || cleanInput == "second" -> pendingCandidates.getOrNull(1)
                cleanInput.contains("teesra") || cleanInput == "3" || cleanInput == "third" -> pendingCandidates.getOrNull(2)
                else -> pendingCandidates.firstOrNull { cand -> cleanInput.contains(cand.appLabel.lowercase()) }
            }

            if (selectedCandidate != null) {
                Log.d(TAG, "CONFIRMATION_RESOLVED: haan, action=executed")
                launchPackageOrCamera(context, selectedCandidate.packageName)
                clearConfirmation()

                return ConfirmationResolution(
                    isHandled = true,
                    action = "executed",
                    spokenResponseHindi = "${selectedCandidate.appLabel} khol raha hoon.",
                    executedPackage = selectedCandidate.packageName,
                    executedAppName = selectedCandidate.appLabel
                )
            }
        }

        // 4. UNRELATED / NEW COMMAND: If user said something completely new (e.g. "WiFi band karo")
        Log.d(TAG, "CONFIRMATION_STATE: Resetting confirmation because new command detected: \"$rawInput\"")
        clearConfirmation()

        return ConfirmationResolution(
            isHandled = false,
            action = "unrelated"
        )
    }

    private fun launchPackageOrCamera(context: Context, packageName: String): Boolean {
        return try {
            if (packageName == "android.media.action.STILL_IMAGE_CAMERA" || packageName.contains("camera")) {
                val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching confirmed package $packageName", e)
            false
        }
    }
}
