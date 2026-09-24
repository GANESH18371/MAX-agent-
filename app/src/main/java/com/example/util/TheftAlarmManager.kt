package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TheftAlarmManager {
    private const val TAG = "TheftAlarmManager"

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalVolume: Int = -1

    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive: StateFlow<Boolean> = _isAlarmActive.asStateFlow()

    fun startEmergencySiren(context: Context) {
        if (_isAlarmActive.value) {
            Log.d(TAG, "Siren is already ringing")
            return
        }

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            // Override volume to MAXIMUM
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Acquire safe bounded WakeLock to turn screen on for emergency alert (max 3 mins)
            wakeLock = BatteryOptimizationManager.acquireSafeWakeLock(
                context = context,
                tag = "EmergencySiren",
                maxDurationMs = 3 * 60 * 1000L
            )

            val rawResId = context.resources.getIdentifier("emergency_siren", "raw", context.packageName)
            val player = if (rawResId != 0) {
                MediaPlayer.create(context, rawResId)
            } else {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                }
            }

            player?.apply {
                isLooping = true
                start()
            }
            mediaPlayer = player

            _isAlarmActive.value = true
            Log.d(TAG, "Emergency Siren Activated at MAX VOLUME")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency siren", e)
        }
    }

    fun stopEmergencySiren(context: Context) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null

            // Restore original volume if saved
            if (originalVolume != -1) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                originalVolume = -1
            }

            BatteryOptimizationManager.releaseSafeWakeLock(wakeLock)
            wakeLock = null

            _isAlarmActive.value = false
            Log.d(TAG, "Emergency Siren Stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping emergency siren", e)
        }
    }
}
