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
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.example.service.MaxAccessibilityService

object SystemToggleController {

    private const val TAG = "SystemToggleController"
    private var isTorchOnState: Boolean = false

    // --- 1. WIFI TOGGLE ---
    fun handleWifi(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isWifiEnabled(context)
        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "WiFi pehle se hi $statusText hai.",
                actionType = "WIFI_ALREADY_IN_STATE",
                isSuccess = true,
                message = "WiFi already $statusText"
            )
        }

        // Try direct API first (Works on Android < 10 or with system privileges)
        var directSuccess = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = targetEnable
                directSuccess = true
            } catch (e: Exception) {
                Log.w(TAG, "Direct WiFi API failed, using Quick Settings fallback", e)
            }
        }

        if (!directSuccess) {
            toggleViaQuickSettings(
                context = context,
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

    // --- 2. BLUETOOTH TOGGLE ---
    fun handleBluetooth(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isBluetoothEnabled(context)
        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
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
            Log.w(TAG, "Direct Bluetooth API failed, using Quick Settings fallback", e)
        }

        if (!directSuccess) {
            toggleViaQuickSettings(
                context = context,
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

    // --- 3. MOBILE DATA TOGGLE ---
    fun handleMobileData(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isMobileDataEnabled(context)
        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Mobile data pehle se hi $statusText hai.",
                actionType = "MOBILE_DATA_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Mobile data already $statusText"
            )
        }

        toggleViaQuickSettings(
            context = context,
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

    // --- 4. AIRPLANE MODE TOGGLE ---
    fun handleAirplaneMode(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isAirplaneModeEnabled(context)
        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Airplane mode pehle se hi $statusText hai.",
                actionType = "AIRPLANE_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Airplane mode already $statusText"
            )
        }

        toggleViaQuickSettings(
            context = context,
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

    // --- 5. TORCH / FLASHLIGHT TOGGLE ---
    fun handleTorch(context: Context, targetEnable: Boolean): LocalExecutionResult {
        if (isTorchOnState == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
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
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, targetEnable)
                isTorchOnState = targetEnable
                directSuccess = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Torch API failed, trying Quick Settings fallback", e)
        }

        if (!directSuccess) {
            toggleViaQuickSettings(
                context = context,
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

    // --- 6. VOLUME CONTROL ---
    fun handleVolume(context: Context, action: String): LocalExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        return when (action.uppercase()) {
            "UP" -> {
                if (currentVol >= maxVol) {
                    LocalExecutionResult(
                        isHandledLocally = true,
                        spokenResponseHindi = "Volume pehle se hi maximum par hai.",
                        actionType = "VOLUME_MAX_ALREADY",
                        isSuccess = true,
                        message = "Volume already at MAX"
                    )
                } else {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
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
                if (currentVol <= 0) {
                    LocalExecutionResult(
                        isHandledLocally = true,
                        spokenResponseHindi = "Volume pehle se hi minimum par hai.",
                        actionType = "VOLUME_MIN_ALREADY",
                        isSuccess = true,
                        message = "Volume already at MIN"
                    )
                } else {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
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
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
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

    // --- 7. SCREEN BRIGHTNESS CONTROL ---
    fun handleBrightness(context: Context, action: String): LocalExecutionResult {
        if (!Settings.System.canWrite(context)) {
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
            val newBrightness = when (action.uppercase()) {
                "UP" -> (currentBrightness + 60).coerceAtMost(255)
                "DOWN" -> (currentBrightness - 60).coerceAtLeast(10)
                "MAX", "FULL" -> 255
                "MIN", "LOW" -> 10
                else -> currentBrightness
            }

            if (currentBrightness == newBrightness) {
                LocalExecutionResult(
                    isHandledLocally = true,
                    spokenResponseHindi = "Brightness pehle se hi desired level par hai.",
                    actionType = "BRIGHTNESS_UNCHANGED",
                    isSuccess = true,
                    message = "Brightness level already at $currentBrightness"
                )
            } else {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
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
            Log.e(TAG, "Error adjusting brightness", e)
            LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Brightness change nahi ho paya.",
                actionType = "BRIGHTNESS_FAILED",
                isSuccess = false,
                message = e.localizedMessage ?: "Brightness error"
            )
        }
    }

    // --- 8. DO NOT DISTURB (DND) TOGGLE ---
    fun handleDnd(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isDndEnabled(context)
        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
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
            if (targetEnable) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            val statusText = if (targetEnable) "ON" else "OFF"
            LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Do Not Disturb $statusText kar diya hai.",
                actionType = if (targetEnable) "DND_ON" else "DND_OFF",
                isSuccess = true,
                message = "DND set to $statusText"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting DND", e)
            toggleViaQuickSettings(
                context = context,
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

    // --- 9. HOTSPOT TOGGLE ---
    fun handleHotspot(context: Context, targetEnable: Boolean): LocalExecutionResult {
        toggleViaQuickSettings(
            context = context,
            tileKeywords = listOf("Hotspot", "Tethering", "Personal Hotspot"),
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

    // --- 10. GPS / LOCATION TOGGLE ---
    fun handleLocation(context: Context, targetEnable: Boolean): LocalExecutionResult {
        val currentStatus = isLocationEnabled(context)
        if (currentStatus == targetEnable) {
            val statusText = if (targetEnable) "ON" else "OFF"
            return LocalExecutionResult(
                isHandledLocally = true,
                spokenResponseHindi = "Location pehle se hi $statusText hai.",
                actionType = "LOCATION_ALREADY_IN_STATE",
                isSuccess = true,
                message = "Location already $statusText"
            )
        }

        toggleViaQuickSettings(
            context = context,
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

    // --- STATE QUERY HELPERS ---
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

    // --- ACCESSIBILITY AUTOMATION FALLBACK FOR QUICK SETTINGS TILES ---
    private fun toggleViaQuickSettings(context: Context, tileKeywords: List<String>, fallbackIntent: String) {
        val service = MaxAccessibilityService.instance
        if (service != null) {
            // Step 1: Open Quick Settings Panel via Accessibility Global Action
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

            // Step 2: Background thread to wait for animation and perform precise click on tile
            Thread {
                try {
                    Thread.sleep(750)
                    var foundAndClicked = false
                    for (keyword in tileKeywords) {
                        if (service.findAndClick(keyword)) {
                            foundAndClicked = true
                            Log.d(TAG, "Successfully clicked Quick Settings tile matching keyword: \"$keyword\"")
                            break
                        }
                    }

                    if (!foundAndClicked) {
                        Log.w(TAG, "Could not locate tile in Quick Settings for keywords $tileKeywords. Launching Intent fallback.")
                        openFallbackIntent(context, fallbackIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error interacting with Quick Settings panel", e)
                    openFallbackIntent(context, fallbackIntent)
                }
            }.start()
        } else {
            Log.w(TAG, "Accessibility Service is not running. Launching fallback Intent.")
            openFallbackIntent(context, fallbackIntent)
        }
    }

    private fun openFallbackIntent(context: Context, intentAction: String) {
        try {
            val intent = Intent(intentAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch fallback intent: $intentAction", e)
        }
    }
}
