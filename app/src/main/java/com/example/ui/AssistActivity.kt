package com.example.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.MainActivity

/**
 * Handles android.intent.action.ASSIST triggered by hardware assistant buttons or gestures.
 */
class AssistActivity : Activity() {

    companion object {
        private const val TAG = "AssistActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "AssistActivity onCreate: Assist trigger received")

        // If MainActivity is running, trigger voice assistant
        MaxViewModel.activeInstance?.let { vm ->
            vm.voiceAssistant.startListening()
        } ?: run {
            // Launch MainActivity to Assistant home tab with voice listen trigger
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("EXTRA_AUTO_START_LISTENING", true)
            }
            startActivity(launchIntent)
        }

        finish()
    }
}
