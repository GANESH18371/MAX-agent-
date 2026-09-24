package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MaxDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("MaxDeviceAdminReceiver", "Device Admin Enabled for Max")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("MaxDeviceAdminReceiver", "Device Admin Disabled for Max")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w("MaxDeviceAdminReceiver", "Lock Screen Password/PIN Failed!")
        com.example.util.SecurityManager.triggerIntruderAlert(context)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d("MaxDeviceAdminReceiver", "Lock Screen Password Succeeded")
    }
}
