package com.vorht.capture.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.vorht.capture.MainActivity
import com.vorht.capture.R
import com.vorht.capture.util.CrashReporter

/**
 * Watches WhatsApp notifications ONLY (`com.whatsapp`) and hands every message
 * to CaptureDispatcher for immediate delivery with 30-second TTL.
 *
 * Runs as a foreground service so Android and OEM ROMs (e.g. MagicOS) do not
 * freeze the process or restrict background sockets while the screen is locked.
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected — promoting to foreground")
        CrashReporter.report("listener CONNECTED — capture is live")
        goForeground()
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Listener disconnected — requesting rebind")
        CrashReporter.report("listener DISCONNECTED — attempting rebind now")
        try {
            requestRebind(ComponentName(this, WhatsAppNotificationListener::class.java))
        } catch (e: Exception) {
            CrashReporter.report("requestRebind failed: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val notification = sbn.notification ?: return
        // Ongoing notifications are progress bars / uploads, never messages.
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val sender = if (title.isNullOrBlank()) "WhatsApp" else title

        val texts = extractTexts(extras)
        if (texts.isEmpty()) return

        val key = sbn.key ?: "${sbn.packageName}|${sbn.id}"
        for (text in texts) {
            // Skip progress-style/percentage-only strings.
            if (text.contains("%") && !text.any { it.isLetterOrDigit() && it != '%' }) continue

            CaptureDispatcher.forward(
                key = key,
                sender = sender,
                message = text,
                code = CodeParser.parse(text),
                detectedAt = sbn.postTime,
            )
        }
    }

    private fun goForeground() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Vorht Capture Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Maintains real-time background capture of WhatsApp verification codes"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
            val openApp = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Vorht Capture")
                .setContentText("Active — listening for WhatsApp verification codes")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "Promoted to foreground service successfully")
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}", e)
            CrashReporter.report("startForeground failed: ${e.message}")
        }
    }

    /**
     * Every variant of message content WhatsApp may attach, each forwarded
     * separately. Order matters: lines (stacked messages) first, then big text,
     * then the plain text.
     */
    private fun extractTexts(extras: Bundle): List<String> {
        val found = LinkedHashSet<String>()

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null) {
            for (line in lines) {
                line?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { found.add(it) }
            }
        }

        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { found.add(it) }

        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }?.let { found.add(it) }

        // Drop obvious bundle summaries like "2 new messages" — the real lines
        // are in TEXT_LINES/BIG_TEXT and are forwarded individually.
        return found.filter { text -> !SUMMARY_ONLY.matches(text) }
    }

    companion object {
        private const val TAG = "WhatsAppListener"
        private const val CHANNEL_ID = "vorht_listener"
        private const val NOTIFICATION_ID = 42
        const val WHATSAPP_PACKAGE = "com.whatsapp"

        private val SUMMARY_ONLY = Regex(
            "(?i)^\\s*\\d+\\s+new\\s+messages?\\s*$" + "|" +
                "(?i)^\\s*\\d+\\s+messages?\\s*$"
        )
    }
}
