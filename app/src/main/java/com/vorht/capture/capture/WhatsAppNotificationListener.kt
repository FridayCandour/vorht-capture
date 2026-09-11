package com.vorht.capture.capture

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.vorht.capture.util.CrashReporter

/**
 * Watches WhatsApp notifications ONLY (`com.whatsapp`) and hands every message
 * to CaptureDispatcher for immediate delivery with 30-second TTL.
 *
 * System-managed lifecycle:
 *  - System-bound via BIND_NOTIFICATION_LISTENER_SERVICE
 *  - onListenerDisconnected requests an immediate rebind
 *  - Every message-bearing variant of a notification is extracted:
 *    EXTRA_TEXT_LINES (stacked/bundled messages), EXTRA_BIG_TEXT, EXTRA_TEXT
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected — capture is live")
        CrashReporter.report("listener CONNECTED — capture is live")
    }

    override fun onListenerDisconnected() {
        // The system dropped us — force an immediate rebind so nothing is missed.
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
        const val WHATSAPP_PACKAGE = "com.whatsapp"

        private val SUMMARY_ONLY = Regex(
            "(?i)^\\s*\\d+\\s+new\\s+messages?\\s*$" + "|" +
                "(?i)^\\s*\\d+\\s+messages?\\s*$"
        )
    }
}
