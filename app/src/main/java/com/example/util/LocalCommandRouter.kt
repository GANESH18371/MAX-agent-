package com.example.util

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.receiver.MaxDeviceAdminReceiver
import com.example.service.MaxAccessibilityService
import com.example.service.MaxNotificationListenerService

data class LocalExecutionResult(
    val isHandledLocally: Boolean,
    val spokenResponseHindi: String = "",
    val actionType: String = "",
    val isSuccess: Boolean = false,
    val message: String = ""
)

object LocalCommandRouter {

    private const val TAG = "LocalCommandRouter"

    fun tryExecuteLocalCommand(
        context: Context,
        voiceAssistant: VoiceAssistantManager,
        commandText: String
    ): LocalExecutionResult {
        val cleanCmd = commandText.trim().lowercase()
        Log.d(TAG, "Evaluating command for local routing: \"$cleanCmd\"")

        // 1. FAST 100% OFFLINE APP OPENING VIA GENERIC APP CONTROLLER
        val appOpenRes = GenericAppController.tryOpenAppOffline(context, commandText)
        if (appOpenRes.isHandled) {
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = appOpenRes.spokenResponseHindi,
                actionType = "OPEN_APP",
                isSuccess = appOpenRes.isSuccess,
                message = appOpenRes.message
            )
        }

        // --- PART 0: OWNER / CREATOR IDENTITY COMMANDS ---
        if (isMatch(cleanCmd, listOf("kisne banaya", "owner kaun", "creator kaun", "tum kaun ho", "aap kaun ho", "who created you", "who made you", "who is your owner", "owner name", "creator name", "kiska assistant", "tumhe kisne"))) {
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Mujhe Ganesh Sahani ne banaya hai, main unka personal AI assistant Max hoon.",
                actionType = "OWNER_IDENTITY",
                isSuccess = true,
                message = "Responded with owner identity (Ganesh Sahani)"
            )
        }

        // --- PART 0.1: CONVERSATIONAL & GREETING QUICK RESPONSES ---
        if (isMatch(cleanCmd, listOf("kaise ho", "kaisa hai", "kya haal hai", "how are you", "sab thik", "kaise ho max"))) {
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Main bilkul badhiya hoon! Aap bataiye, aaj main aapki kya madad karoon?",
                actionType = "CHITCHAT",
                isSuccess = true,
                message = "Responded to greeting"
            )
        }

        if (isMatch(cleanCmd, listOf("namaste", "hello max", "hi max", "hey max", "hello", "namaskar"))) {
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Namaste! Main Max hoon. Bataiye, aaj kya kaam hai?",
                actionType = "CHITCHAT",
                isSuccess = true,
                message = "Responded to greeting"
            )
        }

        if (isMatch(cleanCmd, listOf("joke", "chutkula", "ek joke", "joke sunao", "chutkula sunao"))) {
            val jokes = listOf(
                "Pappu ne doctor se bola: Doctor saab, jab main sota hoon to sapne me football match dikhta hai! Doctor: Aaj se raat ko mat sona. Pappu: Par aaj to final match hai!",
                "Teacher: Newton ka niyam batao. Student: Sir, poora to nahi aata par aakhri part pata hai. Teacher: Aakhri part kya hai? Student: Aur is niyam ko Newton ka niyam kehte hain!",
                "Chintu: Papa, mujhe ek nayi car chahiye. Papa: Padhai me dhyaan do, car apne aap milegi. Chintu: Fir to rehne do, bina padhai ke scooter hi sahi hai!"
            )
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = jokes.random(),
                actionType = "CHITCHAT",
                isSuccess = true,
                message = "Told a joke"
            )
        }

        // --- PART 3: WEATHER COMMANDS ---
        if (isMatch(cleanCmd, listOf("mausam", "weather", "tapman", "temperature", "aaj ka mausam", "barish"))) {
            WeatherController.fetchAndSpeakWeather(context, voiceAssistant)
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "", // Spoken inside WeatherController async
                actionType = "FETCH_WEATHER",
                isSuccess = true,
                message = "Weather data requested"
            )
        }

        // --- EMERGENCY THEFT ALARM COMMANDS ---
        if (isMatch(cleanCmd, listOf("chori alarm band karo", "emergency alarm band karo", "siren band karo", "alarm band karo", "alarm off karo", "stop siren", "stop emergency alarm"))) {
            TheftAlarmManager.stopEmergencySiren(context)
            voiceAssistant.speak("Emergency alarm band kar diya hai.")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "",
                actionType = "STOP_EMERGENCY_ALARM",
                isSuccess = true,
                message = "Emergency Siren Stopped"
            )
        }

        if (isMatch(cleanCmd, listOf("chori alarm", "emergency alarm", "siren bajao", "emergency alarm on karo", "chori alarm bajao", "chori wala alarm", "siren chalao"))) {
            TheftAlarmManager.startEmergencySiren(context)
            voiceAssistant.speak("Emergency siren chalu kar diya hai!")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "",
                actionType = "START_EMERGENCY_ALARM",
                isSuccess = true,
                message = "Emergency Siren Started"
            )
        }

        // --- PART 4: REMINDER / ALARM COMMANDS ---
        if (cleanCmd.contains("reminder") || cleanCmd.contains("yaad dilana") || cleanCmd.contains("alarm")) {
            val handled = ReminderController.parseAndProcessReminderCommand(context, voiceAssistant, cleanCmd)
            if (handled) {
                return LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = "", // Spoken inside ReminderController async
                    actionType = "REMINDER_COMMAND",
                    isSuccess = true,
                    message = "Reminder command processed"
                )
            }
        }

        // --- PART 5: CAMERA & SCENE ANALYSIS COMMANDS ---
        if (cleanCmd.contains("selfie") || cleanCmd.contains("photo") || cleanCmd.contains("saamne kya hai") ||
            cleanCmd.contains("yeh kya hai") || cleanCmd.contains("dekh ke batao") || cleanCmd.contains("samne kya hai")
        ) {
            val handled = CameraController.processCameraCommand(context, voiceAssistant, cleanCmd)
            if (handled) {
                return LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = "", // Spoken inside CameraController async
                    actionType = "CAMERA_ACTION",
                    isSuccess = true,
                    message = "Camera / Scene Analysis processed"
                )
            }
        }

        // --- NAVIGATION & GESTURE SHORTCUTS (Handled 100% locally offline) ---
        if (isMatch(cleanCmd, listOf("home jao", "home screen", "go home", "main screen", "home karo"))) {
            val service = MaxAccessibilityService.instance
            val success = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: launchHomeIntent(context)
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Home screen par aa gaye.",
                actionType = "GO_HOME",
                isSuccess = success,
                message = "Navigated to Home Screen"
            )
        }

        if (isMatch(cleanCmd, listOf("back jao", "go back", "piche jao", "back karo", "wapas jao", "peeche jao"))) {
            val service = MaxAccessibilityService.instance
            val success = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = if (success) "Wapas aa gaye." else "Back action execute nahi ho paya.",
                actionType = "GO_BACK",
                isSuccess = success,
                message = if (success) "Navigated back" else "Accessibility service disabled"
            )
        }

        if (isMatch(cleanCmd, listOf("recent apps", "recent kholo", "recents", "recent karo"))) {
            val service = MaxAccessibilityService.instance
            val success = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = if (success) "Recent apps dikha raha hoon." else "Recents action execute nahi ho paya.",
                actionType = "SHOW_RECENTS",
                isSuccess = success,
                message = if (success) "Opened Recent Apps" else "Accessibility service disabled"
            )
        }

        // --- APP CLOSING / MINIMIZING SHORTCUT (Handled locally) ---
        if (cleanCmd.endsWith("band karo") || cleanCmd.endsWith("close karo") || cleanCmd == "band karo" || cleanCmd == "close app") {
            val isToggle = cleanCmd.contains("wifi") || cleanCmd.contains("bluetooth") || cleanCmd.contains("torch") ||
                    cleanCmd.contains("flashlight") || cleanCmd.contains("hotspot") || cleanCmd.contains("alarm") ||
                    cleanCmd.contains("siren") || cleanCmd.contains("dnd") || cleanCmd.contains("location") ||
                    cleanCmd.contains("data") || cleanCmd.contains("internet") || cleanCmd.contains("awaaz")
            if (!isToggle) {
                val service = MaxAccessibilityService.instance
                val success = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: launchHomeIntent(context)
                val targetAppName = cleanCmd.replace("band karo", "").replace("close karo", "").replace("app", "").trim()
                val currentName = ContextManager.getCurrentAppName()
                val spokenName = when {
                    targetAppName.isNotBlank() -> targetAppName.replaceFirstChar { it.uppercase() }
                    currentName.isNotBlank() && currentName != "Unknown" -> currentName
                    else -> "App"
                }
                return LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = "$spokenName band kar diya.",
                    actionType = "CLOSE_APP",
                    isSuccess = success,
                    message = "Closed $spokenName"
                )
            }
        }

        // --- PHONE LOCK COMMAND (Offline Execution) ---
        if (isMatch(cleanCmd, listOf("phone lock karo", "screen lock karo", "lock phone", "lock screen", "max lock kar do", "phone lock", "lock karo"))) {
            val service = MaxAccessibilityService.instance
            var locked = false

            // Speak confirmation first before audio/screen turns off
            voiceAssistant.speak("Phone lock kar diya.")

            // 1. Try Accessibility GLOBAL_ACTION_LOCK_SCREEN (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && service != null) {
                locked = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }

            // 2. Try DevicePolicyManager lockNow() if Device Admin active
            if (!locked) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val adminComponent = ComponentName(context, MaxDeviceAdminReceiver::class.java)
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    try {
                        dpm.lockNow()
                        locked = true
                    } catch (e: Exception) {
                        Log.e(TAG, "DevicePolicyManager lockNow error", e)
                    }
                } else {
                    // Prompt user to enable Device Admin
                    voiceAssistant.speak("Device Admin permission on karein phone lock karne ke liye.")
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Max app ko phone lock karne ke liye Device Admin permission chahiye.")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening Device Admin settings", e)
                    }
                    return LocalExecutionResult(
                        isHandledLocally = true,
                        spokenResponseHindi = "",
                        actionType = "DEVICE_ADMIN_NEEDED",
                        isSuccess = false,
                        message = "Opened Device Admin activation settings"
                    )
                }
            }

            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "", // Already spoken above
                actionType = "LOCK_PHONE",
                isSuccess = locked,
                message = if (locked) "Phone screen locked" else "Failed to lock screen"
            )
        }

        if (isMatch(cleanCmd, listOf("scroll", "niche karo", "upro karo", "upr karo", "slide", "page down", "page up"))) {
            val service = MaxAccessibilityService.instance
            if (service != null) {
                val isUp = cleanCmd.contains("upr") || cleanCmd.contains("up") || cleanCmd.contains("piche")
                if (isUp) {
                    service.performSwipe(540f, 400f, 540f, 1500f, 300)
                } else {
                    service.performSwipe(540f, 1500f, 540f, 400f, 300)
                }
                return LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = if (isUp) "Upar scroll kar diya." else "Niche scroll kar diya.",
                    actionType = "SWIPE",
                    isSuccess = true,
                    message = "Performed local scroll"
                )
            }
        }

        // Filter out complex queries requiring Gemini AI reasoning
        if (cleanCmd.contains("search") || cleanCmd.contains("play") || cleanCmd.contains("gaane") ||
            cleanCmd.contains("song") || cleanCmd.contains("video") || cleanCmd.contains("skip ad") ||
            cleanCmd.contains("ad skip") || cleanCmd.contains("kya ho raha") || cleanCmd.contains("batao") ||
            cleanCmd.contains("kaun") || cleanCmd.contains("kaise") || cleanCmd.contains("kyun") ||
            cleanCmd.contains("like") || cleanCmd.contains("comment") || cleanCmd.contains("post") ||
            cleanCmd.contains("message") || cleanCmd.contains("send")
        ) {
            Log.d(TAG, "Complex query detected -> Forwarding to Gemini AI")
            return LocalExecutionResult(isHandledLocally = false)
        }

        // --- PART 2: WHATSAPP AUTO-REPLY VOICE TOGGLES ---
        if (isMatch(cleanCmd, listOf("auto reply on", "auto reply chalu", "auto reply start", "auto reply enable"))) {
            if (!MaxNotificationListenerService.isNotificationListenerEnabled(context)) {
                openSystemSetting(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                return LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = "Pehle notification access permission enable karein.",
                    actionType = "WHATSAPP_AUTO_REPLY_PERMISSION",
                    isSuccess = false,
                    message = "Opened Notification Access Settings"
                )
            }
            MaxNotificationListenerService.isAutoReplyEnabled = true
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "WhatsApp auto-reply ON kar diya gaya hai.",
                actionType = "WHATSAPP_AUTO_REPLY_ON",
                isSuccess = true,
                message = "WhatsApp Auto-Reply ENABLED"
            )
        }

        if (isMatch(cleanCmd, listOf("auto reply off", "auto reply band", "auto reply stop", "auto reply disable"))) {
            MaxNotificationListenerService.isAutoReplyEnabled = false
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "WhatsApp auto-reply OFF kar diya gaya hai.",
                actionType = "WHATSAPP_AUTO_REPLY_OFF",
                isSuccess = true,
                message = "WhatsApp Auto-Reply DISABLED"
            )
        }

        // --- PART 1: SYSTEM TOGGLES (Delegated to SystemToggleController with state checking & Quick Settings fallback) ---

        // 1. WIFI CONTROL
        if (isMatch(cleanCmd, listOf("wifi on", "wifi chalu", "wifi connect", "wifi light on"))) {
            return SystemToggleController.handleWifi(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("wifi off", "wifi band", "wifi disconnect"))) {
            return SystemToggleController.handleWifi(context, targetEnable = false)
        }

        // 2. BLUETOOTH CONTROL
        if (isMatch(cleanCmd, listOf("bluetooth on", "bluetooth chalu", "bluetooth connect"))) {
            return SystemToggleController.handleBluetooth(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("bluetooth off", "bluetooth band", "bluetooth disconnect"))) {
            return SystemToggleController.handleBluetooth(context, targetEnable = false)
        }

        // 3. MOBILE DATA CONTROL
        if (isMatch(cleanCmd, listOf("mobile data on", "data on", "internet on", "data chalu", "cellular data on"))) {
            return SystemToggleController.handleMobileData(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("mobile data off", "data off", "internet off", "data band", "cellular data off"))) {
            return SystemToggleController.handleMobileData(context, targetEnable = false)
        }

        // 4. AIRPLANE MODE CONTROL
        if (isMatch(cleanCmd, listOf("airplane mode on", "flight mode on", "airplane mode chalu", "flight mode chalu"))) {
            return SystemToggleController.handleAirplaneMode(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("airplane mode off", "flight mode off", "airplane mode band", "flight mode band"))) {
            return SystemToggleController.handleAirplaneMode(context, targetEnable = false)
        }

        // 5. TORCH / FLASHLIGHT CONTROL
        if (isMatch(cleanCmd, listOf("torch on", "torch chalu", "flashlight on", "torch jalao", "torch light on", "flash on"))) {
            return SystemToggleController.handleTorch(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("torch off", "torch band", "flashlight off", "torch bujha", "flash off"))) {
            return SystemToggleController.handleTorch(context, targetEnable = false)
        }

        // 6. VOLUME CONTROL
        if (isMatch(cleanCmd, listOf("volume badhao", "volume up", "awaaz badhao", "sound badhao", "volume tez", "sound up"))) {
            return SystemToggleController.handleVolume(context, action = "UP")
        }
        if (isMatch(cleanCmd, listOf("volume kam", "volume down", "awaaz kam", "sound kam", "volume dhimmi", "sound down"))) {
            return SystemToggleController.handleVolume(context, action = "DOWN")
        }
        if (isMatch(cleanCmd, listOf("mute karo", "volume zero", "silent karo", "awaaz band"))) {
            return SystemToggleController.handleVolume(context, action = "MUTE")
        }

        // 7. SCREEN BRIGHTNESS CONTROL
        if (isMatch(cleanCmd, listOf("brightness badhao", "brightness tez", "brightness up", "brightness full", "brightness max"))) {
            val actionStr = if (cleanCmd.contains("full") || cleanCmd.contains("max")) "MAX" else "UP"
            return SystemToggleController.handleBrightness(context, action = actionStr)
        }
        if (isMatch(cleanCmd, listOf("brightness kam", "brightness dhimmi", "brightness down", "brightness low"))) {
            val actionStr = if (cleanCmd.contains("low") || cleanCmd.contains("min")) "MIN" else "DOWN"
            return SystemToggleController.handleBrightness(context, action = actionStr)
        }

        // 8. DO NOT DISTURB (DND) CONTROL
        if (isMatch(cleanCmd, listOf("dnd on", "dnd chalu", "do not disturb on", "silent mode on"))) {
            return SystemToggleController.handleDnd(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("dnd off", "dnd band", "do not disturb off", "silent mode off"))) {
            return SystemToggleController.handleDnd(context, targetEnable = false)
        }

        // 9. HOTSPOT CONTROL
        if (isMatch(cleanCmd, listOf("hotspot on", "hotspot chalu"))) {
            return SystemToggleController.handleHotspot(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("hotspot off", "hotspot band"))) {
            return SystemToggleController.handleHotspot(context, targetEnable = false)
        }

        // 10. GPS / LOCATION CONTROL
        if (isMatch(cleanCmd, listOf("location on", "gps on", "location chalu"))) {
            return SystemToggleController.handleLocation(context, targetEnable = true)
        }
        if (isMatch(cleanCmd, listOf("location off", "gps off", "location band"))) {
            return SystemToggleController.handleLocation(context, targetEnable = false)
        }

        // GENERIC APP LAUNCHING (Handled offline via GenericAppController)
        val appLaunchRes = GenericAppController.tryOpenAppOffline(context, cleanCmd)
        if (appLaunchRes.isHandled) {
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = appLaunchRes.spokenResponseHindi,
                actionType = "OPEN_APP",
                isSuccess = appLaunchRes.isSuccess,
                message = appLaunchRes.message
            )
        }

        Log.d(TAG, "Command \"$cleanCmd\" is not a local shortcut. Forwarding to Gemini AI.")
        return LocalExecutionResult(isHandledLocally = false)
    }

    private fun openSystemSetting(context: Context, action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening setting: $action", e)
            false
        }
    }

    private fun isMatch(input: String, keywords: List<String>): Boolean {
        return keywords.any { kw -> input.contains(kw) }
    }

    private fun launchHomeIntent(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching home intent", e)
            false
        }
    }
}
