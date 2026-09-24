package com.example.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * BatteryOptimizationManager
 * Manages power efficiency, smart listening timeouts, safe wakelocks,
 * and background resource throttling for Max AI Assistant.
 */
object BatteryOptimizationManager {

    private const val TAG = "BatteryOptManager"

    // Inactivity timeout before automatically pausing continuous listening / live mic (30-60s)
    private const val DEFAULT_INACTIVITY_TIMEOUT_SEC = 35

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isSmartListeningSleeping = MutableStateFlow(true)
    val isSmartListeningSleeping: StateFlow<Boolean> = _isSmartListeningSleeping.asStateFlow()

    private val _inactivityTimeoutSeconds = MutableStateFlow(DEFAULT_INACTIVITY_TIMEOUT_SEC)
    val inactivityTimeoutSeconds: StateFlow<Int> = _inactivityTimeoutSeconds.asStateFlow()

    private val _activeWakeLockCount = MutableStateFlow(0)
    val activeWakeLockCount: StateFlow<Int> = _activeWakeLockCount.asStateFlow()

    private val _batteryOptimizationStatus = MutableStateFlow("Active (Eco-Engine On)")
    val batteryOptimizationStatus: StateFlow<String> = _batteryOptimizationStatus.asStateFlow()

    // Map of active WakeLocks with their automatic release runnables
    private val activeWakeLocks = ConcurrentHashMap<PowerManager.WakeLock, Runnable>()

    private var onInactivitySleepCallback: (() -> Unit)? = null

    private val inactivityRunnable = Runnable {
        Log.d(TAG, "Inactivity timeout reached (${_inactivityTimeoutSeconds.value}s). Sleeping microphone to save battery.")
        _isSmartListeningSleeping.value = true
        onInactivitySleepCallback?.invoke()
    }

    fun registerInactivityCallback(callback: () -> Unit) {
        onInactivitySleepCallback = callback
    }

    /**
     * Call whenever user interacts or speech is detected to reset the inactivity timer
     */
    fun reportUserActivity() {
        _isSmartListeningSleeping.value = false
        mainHandler.removeCallbacks(inactivityRunnable)
        mainHandler.postDelayed(inactivityRunnable, _inactivityTimeoutSeconds.value * 1000L)
    }

    /**
     * Force immediate sleep of microphone / audio pipeline
     */
    fun forceSleepListening() {
        mainHandler.removeCallbacks(inactivityRunnable)
        _isSmartListeningSleeping.value = true
        onInactivitySleepCallback?.invoke()
    }

    fun setInactivityTimeout(seconds: Int) {
        _inactivityTimeoutSeconds.value = seconds.coerceIn(15, 120)
        reportUserActivity()
    }

    /**
     * Acquires a WakeLock with strict duration bounds to prevent battery drainage.
     * Automatically releases when timeout occurs.
     */
    fun acquireSafeWakeLock(
        context: Context,
        tag: String,
        maxDurationMs: Long = 60_000L
    ): PowerManager.WakeLock? {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MaxAssistant:$tag"
            ).apply {
                setReferenceCounted(false)
            }

            wakeLock.acquire(maxDurationMs)

            val autoReleaseRunnable = Runnable {
                releaseSafeWakeLock(wakeLock)
            }
            activeWakeLocks[wakeLock] = autoReleaseRunnable
            mainHandler.postDelayed(autoReleaseRunnable, maxDurationMs)
            _activeWakeLockCount.value = activeWakeLocks.size

            Log.d(TAG, "Safe WakeLock acquired ($tag) with ${maxDurationMs / 1000}s auto-release limit. Active count: ${activeWakeLocks.size}")
            wakeLock
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire safe WakeLock for $tag", e)
            null
        }
    }

    /**
     * Safely releases a WakeLock and removes its auto-release timer
     */
    fun releaseSafeWakeLock(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock == null) return
        try {
            val autoReleaseRunnable = activeWakeLocks.remove(wakeLock)
            if (autoReleaseRunnable != null) {
                mainHandler.removeCallbacks(autoReleaseRunnable)
            }
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            _activeWakeLockCount.value = activeWakeLocks.size
            Log.d(TAG, "Safe WakeLock released cleanly. Remaining active: ${activeWakeLocks.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    /**
     * Emergency cleanup of all active locks
     */
    fun releaseAllWakeLocks() {
        activeWakeLocks.forEach { (lock, runnable) ->
            mainHandler.removeCallbacks(runnable)
            try {
                if (lock.isHeld) lock.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        activeWakeLocks.clear()
        _activeWakeLockCount.value = 0
    }
}
