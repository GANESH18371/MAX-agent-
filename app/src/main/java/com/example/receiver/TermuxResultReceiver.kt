package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.TermuxCommandManager

class TermuxResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TERMUX_RESULT = "com.example.max.TERMUX_RESULT"
        private const val TAG = "TermuxResultReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        Log.d(TAG, "TermuxResultReceiver onReceive triggered: action=${intent.action}")

        val originalCommand = intent.getStringExtra("ORIGINAL_COMMAND") ?: ""
        val source = intent.getStringExtra("SOURCE") ?: "LOCAL"

        // Termux RUN_COMMAND bundle outputs
        val stdout = intent.getStringExtra("stdout") ?: ""
        val stderr = intent.getStringExtra("stderr") ?: ""
        val exitCode = intent.getIntExtra("exitCode", 0)
        val errCode = intent.getIntExtra("errCode", 0)
        val errmsg = intent.getStringExtra("errmsg") ?: ""

        val rawResult = when {
            stdout.isNotBlank() -> stdout
            stderr.isNotBlank() -> stderr
            errmsg.isNotBlank() -> "Error ($errCode): $errmsg"
            else -> "Exit code: $exitCode"
        }.trim()

        // Required debug logs
        Log.d("TermuxCommandManager", "TERMUX_COMMAND_SOURCE: $source")
        Log.d("TermuxCommandManager", "TERMUX_COMMAND_EXECUTED: $originalCommand")
        Log.d("TermuxCommandManager", "TERMUX_OUTPUT: $rawResult")

        TermuxCommandManager.handleExecutionResult(
            context = context,
            source = source,
            command = originalCommand,
            stdout = stdout,
            stderr = if (stderr.isNotBlank()) stderr else errmsg,
            exitCode = exitCode
        )
    }
}
