package com.vorht.capture.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Permission checks + the exact system screens that grant them. */
object SystemIntents {

    /** True when the OS currently binds our NotificationListenerService. */
    fun isListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** The exact screen with the toggle (this is where the user flips it on). */
    fun openNotificationAccessSettings(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * Direct system dialog: "Allow Vorht Capture to ignore battery optimizations?"
     * Falls back to the full exemption list if the direct dialog is unavailable.
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        try {
            activity.startActivity(direct)
        } catch (e: Exception) {
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
