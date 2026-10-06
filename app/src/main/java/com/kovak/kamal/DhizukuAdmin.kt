package com.kovak.kamal

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class DhizukuAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Device owner is now active — app will auto-work from here on
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
