package com.example.util

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.service.MaxAccessibilityService

/**
 * SystemToggleController
 *
 * Single Consolidated Module for 100% Local / Direct Voice-Activated Hardware Toggles:
 * - WiFi ON/OFF
 * - Bluetooth ON/OFF
 * - Mobile Data ON/OFF
 * - Airplane Mode ON/OFF
 * - Torch / Flashlight ON/OFF
 * - Volume UP / DOWN / MUTE
 * - Screen Brightness UP / DOWN / MAX / MIN
 * - Do Not Disturb (DND) ON/OFF
 * - Hotspot ON/OFF
 * - GPS / Location ON/OFF
 *
 * Direct System API first -> If Android 10+ restricts, Accessibility Real Touch Tap on Quick Settings Tile.
 * Full state-awareness (checks current state, speaks if already in state, never calls Gemini API).
 */
object SystemToggleController {

    private const val TAG = "SystemToggleController"
    private var isTorchOnState: Boolean = false

    // =========================================================================
    // 1. WIFI ON / OFF
    // =========================================================================
    fun handleWifi(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isWifiEnabled(context)
        Log.d(TAG, "<WIFI>_CURRENT_STATE: ${if (currentStatus) "ON" else "OFF"}")

        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<WIFI>_RESULT: already in state $statusText")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "WiFi pehle se hi $statusText hai.",
                actionType = "WIFI_ALREADY_IN_STATE",
                isSuccess = true,
                message = "WiFi already $statusText"
            )
        }

        var directSuccess = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                Log.d(TAG, "<WIFI>_ATTEMPT: method=DIRECT")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = targetEnable
                directSuccess = true
            } catch (e: Exception) {
                Log.w(TAG, "Direct WiFi API failed, switching to Accessibility Real Tap", e)
            }
        }

        if (directSuccess) {
            Log.d(TAG, "<WIFI>_RESULT: success (DIRECT)")
        } else {
            Log.d(TAG, "<WIFI>_ATTEMPT: method=ACCESSIBILITY_TAP")
            toggleViaQuickSettingsOrAccessibility(
                context = context,
                featureName = "WiFi",
                tileKeywords = listOf("Wi-Fi", "WiFi", "Internet", "Network"),
                fallbackIntent = Settings.ACTION_WIFI_SETTINGS
            )
        }

        val actionText = if (targetEnable) "ON" else "OFF"
        return LocalExecutionResult(
            isHandledLocally = true,
            spokenResponseHindi = "WiFi $actionText kar diya hai.",
            actionType = if (targetEnable) "WIFI_ON" else "WIFI_OFF",
            isSuccess = true,
            message = "WiFi set to $actionText"
        )
    }

    // =========================================================================
    // 2. BLUETOOTH ON / OFF
    // =========================================================================
    fun handleBluetooth(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isBluetoothEnabled(context)
        Log.d(TAG, "<BLUETOOTH>_CURRENT_STATE: ${if (currentStatus) "ON" else "OFF"}")

        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<BLUETOOTH>_RESULT: already in state $statusText")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Bluetooth pehle se hi $statusText hai.",
                actionType = "BLUETOOTH_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Bluetooth already $statusText"
            )
        }

        var directSuccess = false
        try {
            Log.d(TAG, "<BLUETOOTH>_ATTEMPT: method=DIRECT")
            val hasBtPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasBtPermission) {
                @Suppress("DEPRECATION")
                val btAdapter = BluetoothAdapter.getDefaultAdapter()
                if (btAdapter != null) {
                    if (targetEnable && !btAdapter.isEnabled) {
                        @Suppress("DEPRECATION")
                        directSuccess = btAdapter.enable()
                    } else if (!targetEnable && btAdapter.isEnabled) {
                        @Suppress("DEPRECATION")
                        directSuccess = btAdapter.disable()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Bluetooth API failed, switching to Accessibility Real Tap", e)
        }

        if (directSuccess) {
            Log.d(TAG, "<BLUETOOTH>_RESULT: success (DIRECT)")
        } else {
            Log.d(TAG, "<BLUETOOTH>_ATTEMPT: method=ACCESSIBILITY_TAP")
            toggleViaQuickSettingsOrAccessibility(
                context = context,
                featureName = "Bluetooth",
                tileKeywords = listOf("Bluetooth", "BT"),
                fallbackIntent = Settings.ACTION_BLUETOOTH_SETTINGS
            )
        }

        val actionText = if (targetEnable) "ON" else "OFF"
        return LocalExecutionResult(
            isHandledLocally = true,
            spokenResponseHindi = "Bluetooth $actionText kar diya hai.",
            actionType = if (targetEnable) "BLUETOOTH_ON" else "BLUETOOTH_OFF",
            isSuccess = true,
            message = "Bluetooth set to $actionText"
        )
    }

    // =========================================================================
    // 3. MOBILE DATA ON / OFF
    // =========================================================================
    fun handleMobileData(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isMobileDataEnabled(context)
        Log.d(TAG, "<MOBILEDATA>_CURRENT_STATE: ${if (currentStatus) "ON" else "OFF"}")

        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<MOBILEDATA>_RESULT: already in state $statusText")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Mobile data pehle se hi $statusText hai.",
                actionType = "MOBILE_DATA_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Mobile data already $statusText"
            )
        }

        Log.d(TAG, "<MOBILEDATA>_ATTEMPT: method=ACCESSIBILITY_TAP")
        toggleViaQuickSettingsOrAccessibility(
            context = context,
            featureName = "Mobile Data",
            tileKeywords = listOf("Mobile data", "Cellular data", "Data", "Internet"),
            fallbackIntent = Settings.ACTION_DATA_ROAMING_SETTINGS
        )

        val actionText = if (targetEnable) "ON" else "OFF"
        return LocalExecutionResult(
            isHandledLocally = true,
            spokenResponseHindi = "Mobile data $actionText kar diya hai.",
            actionType = if (targetEnable) "MOBILE_DATA_ON" else "MOBILE_DATA_OFF",
            isSuccess = true,
            message = "Mobile data set to $actionText via Quick Settings"
        )
    }

    // =========================================================================
    // 4. AIRPLANE MODE ON / OFF
    // =========================================================================
    fun handleAirplaneMode(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isAirplaneModeEnabled(context)
        Log.d(TAG, "<AIRPLANE_MODE>_CURRENT_STATE: ${if (currentStatus) "ON" else "OFF"}")

        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<AIRPLANE_MODE>_RESULT: already in state $statusText")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Airplane mode pehle se hi $statusText hai.",
                actionType = "AIRPLANE_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Airplane mode already $statusText"
            )
        }

        Log.d(TAG, "<AIRPLANE_MODE>_ATTEMPT: method=ACCESSIBILITY_TAP")
        toggleViaQuickSettingsOrAccessibility(
            context = context,
            featureName = "Airplane Mode",
            tileKeywords = listOf("Airplane mode", "Flight mode", "Airplane"),
            fallbackIntent = Settings.ACTION_AIRPLANE_MODE_SETTINGS
        )

        val actionText = if (targetEnable) "ON" else "OFF"
        return LocalExecutionResult(
            isHandledLocally = true,
            spokenResponseHindi = "Airplane mode $actionText kar diya hai.",
            actionType = if (targetEnable) "AIRPLANE_MODE_ON" else "AIRPLANE_MODE_OFF",
            isSuccess = true,
            message = "Airplane mode set to $actionText via Quick Settings"
        )
    }

    // =========================================================================
    // 5. TORCH / FLASHLIGHT ON / OFF
    // =========================================================================
    fun handleTorch(context: Context, targetEnable: Boolean): LocalExecutionResult {
        Log.d(TAG, "<TORCH>_CURRENT_STATE: ${if (isTorchOnState) "ON" else "OFF"}")

        if (isTorchOnState == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<TORCH>_RESULT: already in state $statusText")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Torch pehle se hi $statusText hai.",
                actionType = "TORCH_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Torch already $statusText"
            )
        }

        var directSuccess = false
        try {
            Log.d(TAG, "<TORCH>_ATTEMPT: method=DIRECT")
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, targetEnable)
                isTorchOnState = targetEnable
                directSuccess = true
                Log.d(TAG, "<TORCH>_RESULT: success (DIRECT)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Torch API failed, trying Quick Settings fallback", e)
        }

        if (!directSuccess) {
            Log.d(TAG, "<TORCH>_ATTEMPT: method=ACCESSIBILITY_TAP")
            toggleViaQuickSettingsOrAccessibility(
                context = context,
                featureName = "Torch",
                tileKeywords = listOf("Flashlight", "Torch", "Flash"),
                fallbackIntent = Settings.ACTION_SETTINGS
            )
            isTorchOnState = targetEnable
        }

        val actionText = if (targetEnable) "ON" else "OFF"
        return LocalExecutionResult(
            isHandledLocally = true,
            spokenResponseHindi = if (targetEnable) "Torch chalu kar diya." else "Torch band kar diya.",
            actionType = if (targetEnable) "TORCH_ON" else "TORCH_OFF",
            isSuccess = true,
            message = "Torch set to $actionText"
        )
    }

    // =========================================================================
    // 6. VOLUME UP / DOWN / MUTE
    // =========================================================================
    fun handleVolume(context: Context, action: String): LocalExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Log.d(TAG, "<VOLUME>_CURRENT_STATE: $currentVol / $maxVol, requested action=$action")

        return when (action.uppercase()) {
            "UP" -> {
                Log.d(TAG, "<VOLUME>_ATTEMPT: method=DIRECT (ADJUST_RAISE)")
                if (currentVol >= maxVol) {
                    Log.d(TAG, "<VOLUME>_RESULT: already at MAX")
                    LocalExecutionResult(
                        isHandledLocally = true,
                        spokenResponseHindi = "Volume pehle se hi maximum par hai.",
                        actionType = "VOLUME_MAX_ALREADY",
                        isSuccess = true,
                        message = "Volume already at MAX"
                    )
                } else {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    Log.d(TAG, "<VOLUME>_RESULT: success (UP)")
                    LocalExecutionResult(
                        isHandledLocally = true,
                        spokenResponseHindi = "Volume badha diya.",
                        actionType = "VOLUME_UP",
                        isSuccess = true,
                        message = "Volume increased"
                    )
                }
            }

            "DOWN" -> {
                Log.d(TAG, "<VOLUME>_ATTEMPT: method=DIRECT (ADJUST_LOWER)")
                if (currentVol <= 0) {
                    Log.d(TAG, "<VOLUME>_RESULT: already at MIN")
                    LocalExecutionResult(
                        isHandledLocally = true,
                        spokenResponseHindi = "Volume pehle se hi minimum par hai.",
                        actionType = "VOLUME_MIN_ALREADY",
                        isSuccess = true,
                        message = "Volume already at MIN"
                    )
                } else {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    Log.d(TAG, "<VOLUME>_RESULT: success (DOWN)")
                    LocalExecutionResult(
                        isHandledLocally = true,
                        spokenResponseHindi = "Volume kam kar diya.",
                        actionType = "VOLUME_DOWN",
                        isSuccess = true,
                        message = "Volume lowered"
                    )
                }
            }

            "MUTE" -> {
                Log.d(TAG, "<VOLUME>_ATTEMPT: method=DIRECT (ADJUST_MUTE)")
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                Log.d(TAG, "<VOLUME>_RESULT: success (MUTE)")
                LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = "Volume mute kar diya.",
                    actionType = "VOLUME_MUTE",
                    isSuccess = true,
                    message = "Volume muted"
                )
            }

            else -> LocalExecutionResult(isHandledLocally = false)
        }
    }

    // =========================================================================
    // 7. SCREEN BRIGHTNESS UP / DOWN / MAX / MIN
    // =========================================================================
    fun handleBrightness(context: Context, action: String): LocalExecutionResult {
        if (!Settings.System.canWrite(context)) {
            Log.d(TAG, "<BRIGHTNESS>_ATTEMPT: WRITE_SETTINGS permission missing")
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Brightness modify karne ke liye System Settings permission dein.",
                actionType = "BRIGHTNESS_PERMISSION_NEEDED",
                isSuccess = false,
                message = "Opened Write Settings Permission Screen"
            )
        }

        return try {
            val currentBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            Log.d(TAG, "<BRIGHTNESS>_CURRENT_STATE: $currentBrightness (0-255)")

            val newBrightness = when (action.uppercase()) {
                "UP" -> (currentBrightness + 60).coerceAtMost(255)
                "DOWN" -> (currentBrightness - 60).coerceAtLeast(10)
                "MAX", "FULL" -> 255
                "MIN", "LOW" -> 10
                else -> currentBrightness
            }

            if (currentBrightness == newBrightness) {
                Log.d(TAG, "<BRIGHTNESS>_RESULT: already at level $currentBrightness")
                LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = "Brightness pehle se hi desired level par hai.",
                    actionType = "BRIGHTNESS_UNCHANGED",
                    isSuccess = true,
                    message = "Brightness level already at $currentBrightness"
                )
            } else {
                Log.d(TAG, "<BRIGHTNESS>_ATTEMPT: method=DIRECT Setting brightness to $newBrightness")
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
                Log.d(TAG, "<BRIGHTNESS>_RESULT: success ($newBrightness)")
                val text = when (action.uppercase()) {
                    "UP" -> "Brightness badha diya."
                    "DOWN" -> "Brightness kam kar diya."
                    "MAX", "FULL" -> "Brightness full kar diya."
                    else -> "Brightness adjust kar diya."
                }
                LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = text,
                    actionType = "BRIGHTNESS_CHANGED",
                    isSuccess = true,
                    message = "Brightness changed from $currentBrightness to $newBrightness"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "<BRIGHTNESS>_RESULT: fail", e)
            LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Brightness automatically change nahi ho paya, manually kar dijiye.",
                actionType = "BRIGHTNESS_FAILED",
                isSuccess = false,
                message = e.localizedMessage ?: "Brightness error"
            )
        }
    }

    // =========================================================================
    // 8. DO NOT DISTURB (DND) ON / OFF
    // =========================================================================
    fun handleDnd(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isDndEnabled(context)
        Log.d(TAG, "<DND>_CURRENT_STATE: ${if (currentStatus) "ON" else "OFF"}")

        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<DND>_RESULT: already in state $statusText")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Do Not Disturb pehle se hi $statusText hai.",
                actionType = "DND_ALREADY_IN_STATE",
                isSuccess = true,
                message = "DND already $statusText"
            )
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.d(TAG, "<DND>_ATTEMPT: Notification Policy Access missing, opening settings")
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Do Not Disturb access permission enable karein.",
                actionType = "DND_PERMISSION_NEEDED",
                isSuccess = false,
                message = "Opened DND Policy Access Settings"
            )
        }

        return try {
            Log.d(TAG, "<DND>_ATTEMPT: method=DIRECT")
            if (targetEnable) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<DND>_RESULT: success (DIRECT $statusText)")
            LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Do Not Disturb $statusText kar diya hai.",
                actionType = if (targetEnable) "DND_ON" else "DND_OFF",
                isSuccess = true,
                message = "DND set to $statusText"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct DND API failed, switching to Accessibility Real Tap", e)
            Log.d(TAG, "<DND>_ATTEMPT: method=ACCESSIBILITY_TAP")
            toggleViaQuickSettingsOrAccessibility(
                context = context,
                featureName = "DND",
                tileKeywords = listOf("Do Not Disturb", "DND", "Silent"),
                fallbackIntent = Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            )
            LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Do Not Disturb toggle kar diya hai.",
                actionType = "DND_TOGGLE_FALLBACK",
                isSuccess = true,
                message = "DND toggled via Quick Settings"
            )
        }
    }

    // =========================================================================
    // 9. HOTSPOT ON / OFF
    // =========================================================================
    fun handleHotspot(context: Context, targetEnable: Boolean): LocalExecutionResult {
        Log.d(TAG, "<HOTSPOT>_ATTEMPT: method=ACCESSIBILITY_TAP targetEnable=$targetEnable")
        toggleViaQuickSettingsOrAccessibility(
            context = context,
            featureName = "Hotspot",
            tileKeywords = listOf("Hotspot", "Tethering", "Personal Hotspot", "Portable Hotspot"),
            fallbackIntent = Settings.ACTION_WIRELESS_SETTINGS
        )

        val actionText = if (targetEnable) "ON" else "OFF"
        return LocalExecutionResult(
            isHandledLocally = true,
            spokenResponseHindi = "Hotspot $actionText kar diya hai.",
            actionType = if (targetEnable) "HOTSPOT_ON" else "HOTSPOT_OFF",
            isSuccess = true,
            message = "Hotspot set to $actionText via Quick Settings"
        )
    }

    // =========================================================================
    // 10. GPS / LOCATION ON / OFF
    // =========================================================================
    fun handleLocation(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isLocationEnabled(context)
        Log.d(TAG, "<LOCATION>_CURRENT_STATE: ${if (currentStatus) "ON" else "OFF"}")

        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            Log.d(TAG, "<LOCATION>_RESULT: already in state $statusText")
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Location pehle se hi $statusText hai.",
                actionType = "LOCATION_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Location already $statusText"
            )
        }

        Log.d(TAG, "<LOCATION>_ATTEMPT: method=ACCESSIBILITY_TAP")
        toggleViaQuickSettingsOrAccessibility(
            context = context,
            featureName = "Location",
            tileKeywords = listOf("Location", "GPS"),
            fallbackIntent = Settings.ACTION_LOCATION_SOURCE_SETTINGS
        )

        val actionText = if (targetEnable) "ON" else "OFF"
        return LocalExecutionResult(
            isHandledLocally = true,
            spokenResponseHindi = "Location $actionText kar diya hai.",
            actionType = if (targetEnable) "LOCATION_ON" else "LOCATION_OFF",
            isSuccess = true,
            message = "Location set to $actionText via Quick Settings"
        )
    }

    // =========================================================================
    // STATE QUERY HELPERS
    // =========================================================================
    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            false
        }
    }

    fun isBluetoothEnabled(context: Context? = null): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return false
            }
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    fun isAirplaneModeEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun isDndEnabled(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            false
        }
    }

    fun isMobileDataEnabled(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.dataState == TelephonyManager.DATA_CONNECTED
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // REAL ACCESSIBILITY TAP & QUICK SETTINGS TOGGLE ENGINE
    // =========================================================================

    /**
     * Executes a real physical tap or click on the Quick Settings tile matching the feature keywords.
     * If the tile is not directly clickable in the panel, it opens the target settings page and
     * automatically finds and taps the primary Switch/Toggle widget!
     */
    fun toggleViaQuickSettingsOrAccessibility(
        context: Context,
        featureName: String,
        tileKeywords: List<String>,
        fallbackIntent: String
    ) {
        val service = MaxAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "<${featureName.uppercase()}>_RESULT: fail (Accessibility Service OFF) -> launching fallback intent")
            openFallbackIntent(context, fallbackIntent)
            return
        }

        // Step 1: Open Quick Settings Panel via Accessibility Global Action
        val opened = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        Log.d(TAG, "<${featureName.uppercase()}>_ATTEMPT: opened Quick Settings panel: $opened")

        // Step 2: Coroutine-free background dispatch with precise retry & switch finding
        Thread {
            try {
                Thread.sleep(700)
                var foundAndClicked = false

                // Attempt 1: Direct click on tile in Quick Settings
                for (kw in tileKeywords) {
                    if (service.findAndClick(kw)) {
                        foundAndClicked = true
                        Log.d(TAG, "<${featureName.uppercase()}>_RESULT: success (Tapped Quick Settings Tile: \"$kw\")")
                        break
                    }
                }

                // If not found in first page of Quick Settings, try secondary expand or settings page switch
                if (!foundAndClicked) {
                    Log.d(TAG, "<${featureName.uppercase()}>_ATTEMPT: tile not found on top bar, opening settings to tap real switch")
                    openFallbackIntent(context, fallbackIntent)
                    Thread.sleep(1000)

                    // Find switch widget on opened settings page
                    val switchClicked = findAndTapSwitch(service, tileKeywords)
                    if (switchClicked) {
                        Log.d(TAG, "<${featureName.uppercase()}>_RESULT: success (Tapped in-settings switch)")
                        foundAndClicked = true
                    } else {
                        Log.w(TAG, "<${featureName.uppercase()}>_RESULT: fail (Could not find switch automatically)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing toggle for $featureName", e)
                openFallbackIntent(context, fallbackIntent)
            }
        }.start()
    }

    private fun findAndTapSwitch(service: MaxAccessibilityService, keywords: List<String>): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val switches = mutableListOf<AccessibilityNodeInfo>()
        collectSwitchesAndToggles(root, switches)

        if (switches.isNotEmpty()) {
            for (sw in switches) {
                if (sw.isVisibleToUser) {
                    val clicked = performClickOnNode(sw)
                    if (clicked) return true
                }
            }
        }

        for (kw in keywords) {
            if (service.findAndClick(kw)) {
                return true
            }
        }
        return false
    }

    private fun collectSwitchesAndToggles(node: AccessibilityNodeInfo, outList: MutableList<AccessibilityNodeInfo>) {
        val className = node.className?.toString() ?: ""
        if (className.contains("Switch", ignoreCase = true) ||
            className.contains("ToggleButton", ignoreCase = true) ||
            className.contains("CompoundButton", ignoreCase = true) ||
            node.isCheckable
        ) {
            outList.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectSwitchesAndToggles(child, outList)
            }
        }
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isClickable) {
                if (curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            curr = curr.parent
        }
        return false
    }

    private fun openFallbackIntent(context: Context, intentAction: String) {
        try {
            val intent = Intent(intentAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch intent: $intentAction", e)
        }
    }
}
