package com.example.service

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

class MaxRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "MaxRecognitionService"
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.d(TAG, "MaxRecognitionService onStartListening")
    }

    override fun onCancel(listener: Callback?) {
        Log.d(TAG, "MaxRecognitionService onCancel")
    }

    override fun onStopListening(listener: Callback?) {
        Log.d(TAG, "MaxRecognitionService onStopListening")
    }
}
