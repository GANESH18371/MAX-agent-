package com.example.service

import android.service.voice.VoiceInteractionService
import android.util.Log

class MaxVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "MaxVoiceInteraction"
        var isServiceActive = false
            private set
    }

    override fun onReady() {
        super.onReady()
        isServiceActive = true
        Log.d(TAG, "MaxVoiceInteractionService is ready and set as active Digital Assistant")
    }

    override fun onShutdown() {
        super.onShutdown()
        isServiceActive = false
        Log.d(TAG, "MaxVoiceInteractionService is shutting down")
    }
}
