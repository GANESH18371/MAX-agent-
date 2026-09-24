package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SecurityManager {

    private const val TAG = "SecurityManager"
    private const val PREFS_NAME = "max_security_prefs"
    private const val KEY_PIN_ENABLED = "key_pin_enabled"
    private const val KEY_PIN_CODE = "key_pin_code"
    private const val KEY_TRUSTED_CONTACT = "key_trusted_contact"
    private const val DEFAULT_PIN = "1234"

    private val securityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isPinLockEnabled = MutableStateFlow(false)
    val isPinLockEnabled: StateFlow<Boolean> = _isPinLockEnabled.asStateFlow()

    private val _isSettingsUnlocked = MutableStateFlow(true)
    val isSettingsUnlocked: StateFlow<Boolean> = _isSettingsUnlocked.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _trustedContactNumber = MutableStateFlow("")
    val trustedContactNumber: StateFlow<String> = _trustedContactNumber.asStateFlow()

    fun init(context: Context) {
        val prefs = getPrefs(context)
        _isPinLockEnabled.value = prefs.getBoolean(KEY_PIN_ENABLED, false)
        _trustedContactNumber.value = prefs.getString(KEY_TRUSTED_CONTACT, "") ?: ""
        _isSettingsUnlocked.value = !_isPinLockEnabled.value
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getStoredPin(context: Context): String {
        return getPrefs(context).getString(KEY_PIN_CODE, DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        val storedPin = getStoredPin(context)
        if (inputPin == storedPin) {
            _failedAttempts.value = 0
            _isSettingsUnlocked.value = true
            Log.d(TAG, "PIN verified successfully!")
            return true
        } else {
            val attempts = _failedAttempts.value + 1
            _failedAttempts.value = attempts
            Log.w(TAG, "Incorrect PIN entered! Attempt $attempts/3")

            if (attempts >= 3) {
                triggerIntruderAlert(context)
            }
            return false
        }
    }

    fun setPin(context: Context, newPin: String) {
        if (newPin.length >= 4) {
            getPrefs(context).edit().putString(KEY_PIN_CODE, newPin).apply()
            Log.d(TAG, "New security PIN updated successfully")
        }
    }

    fun togglePinLock(context: Context, enable: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PIN_ENABLED, enable).apply()
        _isPinLockEnabled.value = enable
        if (enable) {
            _isSettingsUnlocked.value = false
        } else {
            _isSettingsUnlocked.value = true
        }
    }

    fun setTrustedContact(context: Context, phone: String) {
        getPrefs(context).edit().putString(KEY_TRUSTED_CONTACT, phone).apply()
        _trustedContactNumber.value = phone
    }

    fun lockSettings() {
        if (_isPinLockEnabled.value) {
            _isSettingsUnlocked.value = false
        }
    }

    fun unlockSettings() {
        _isSettingsUnlocked.value = true
        _failedAttempts.value = 0
    }

    @SuppressLint("MissingPermission")
    fun triggerIntruderAlert(context: Context) {
        Log.e(TAG, "🚨 INTRUDER ALERT TRIGGERED! 3+ failed PIN attempts detected.")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "⚠️ 3 Galat PIN! Intruder photo capture ho rahi hai...", Toast.LENGTH_LONG).show()
        }

        securityScope.launch {
            // 1. Silent Front Camera Selfie Capture
            try {
                CameraController.captureFrame(context, isFrontCamera = true) { bitmap ->
                    if (bitmap != null) {
                        securityScope.launch {
                            val savedUri = CameraController.saveBitmapToGallery(context, bitmap)
                            Log.d(TAG, "Intruder photo saved: $savedUri")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed silent intruder capture", e)
            }

            // 2. Fetch Current GPS Location
            var locationStr = "Location unavailable"
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (location != null) {
                        locationStr = "Lat: ${location.latitude}, Lng: ${location.longitude} (https://maps.google.com/?q=${location.latitude},${location.longitude})"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading location for intruder alert", e)
            }

            // 3. Dispatch SMS alert to trusted contact if configured
            val contact = _trustedContactNumber.value
            if (contact.isNotBlank()) {
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        val smsManager = SmsManager.getDefault()
                        val alertMsg = "⚠️ MAX SECURITY ALERT: Unauthorized PIN attempts on device. $locationStr"
                        smsManager.sendTextMessage(contact, null, alertMsg, null, null)
                        Log.d(TAG, "Emergency SMS alert dispatched to trusted contact: $contact")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending intruder SMS", e)
                }
            }
        }
    }
}
