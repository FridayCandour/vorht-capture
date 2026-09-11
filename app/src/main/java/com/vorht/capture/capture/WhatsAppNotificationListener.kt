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
import com.vorht.capture.util.VorhtLogger
import java.util.UUID

import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Authoritative background entry point for WhatsApp notifications.
 * Operates independently of MainActivity and UI lifecycles.
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    private var serviceWakeLock: PowerManager.WakeLock? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        VorhtLogger.log("LISTENER_CONNECTED", details = "capture is live")
        CrashReporter.report("listener CONNECTED — capture is live")
        CaptureDispatcher.init(applicationContext)
        goForeground()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        releaseResidentWakeLock()
        VorhtLogger.log("LISTENER_DISCONNECTED", details = "system unbound listener")
        CrashReporter.report("listener DISCONNECTED — attempting rebind now")

        VorhtLogger.log("REBIND_REQUESTED", details = "requesting rebind")
        try {
            requestRebind(ComponentName(this, WhatsAppNotificationListener::class.java))
        } catch (e: Exception) {
            CrashReporter.report("requestRebind failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        releaseResidentWakeLock()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != WHATSAPP_PACKAGE && pkg != applicationContext.packageName && sbn.tag != "vorht_test") return

        val notification = sbn.notification ?: return
        val key = sbn.key ?: "${pkg}|${sbn.id}"
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        VorhtLogger.log(
            "NOTIFICATION_POSTED",
            notificationKey = key,
            details = "pkg=$pkg id=${sbn.id} flags=0x${Integer.toHexString(notification.flags)} ongoing=$isOngoing groupSummary=$isGroupSummary postTime=${sbn.postTime}",
        )

        // Ongoing notifications are progress bars / uploads / active calls, never incoming messages.
        if (isOngoing) return

        val messages = extractMessages(notification, sbn)
        if (messages.isEmpty()) {
            VorhtLogger.log(
                "NOTIFICATION_SKIPPED",
                notificationKey = key,
                details = "reason=no_valid_text_found",
            )
            return
        }

        VorhtLogger.log(
            "CAPTURE_RECEIVED",
            notificationKey = key,
            details = "messageCount=${messages.size} groupSummary=$isGroupSummary",
        )

        for (item in messages) {
            val code = CodeParser.parse(item.text)
            val eventId = UUID.randomUUID().toString()

            VorhtLogger.log(
                "CAPTURE_PARSED",
                eventId = eventId,
                notificationKey = key,
                details = "sender=\"${item.sender}\" code=${code ?: "none"} textLength=${item.text.length}",
            )

            CaptureDispatcher.forward(
                context = applicationContext,
                eventId = eventId,
                key = key,
                sender = item.sender,
                message = item.text,
                code = code,
                detectedAt = item.timestamp,
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
            acquireResidentWakeLock()
            Log.i(TAG, "Promoted to foreground service successfully")
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}", e)
            CrashReporter.report("startForeground failed: ${e.message}")
        }
    }

    private fun acquireResidentWakeLock() {
        if (serviceWakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            serviceWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vorht:service:resident")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            VorhtLogger.log("WAKELOCK_ACQUIRED", details = "resident service wakelock active")
            Log.i(TAG, "Acquired resident PARTIAL_WAKE_LOCK")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire resident service wakelock: ${e.message}")
        }
    }

    private fun releaseResidentWakeLock() {
        try {
            serviceWakeLock?.let {
                if (it.isHeld) it.release()
            }
            VorhtLogger.log("WAKELOCK_RELEASED", details = "resident service wakelock released")
            Log.i(TAG, "Released resident PARTIAL_WAKE_LOCK")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release resident service wakelock: ${e.message}")
        }
    }

    /**
     * Extracts individual messages from WhatsApp notifications using all available styles.
     * Order of precedence:
     * 1. NotificationCompat.MessagingStyle (standard modern WhatsApp format)
     * 2. Framework Notification.EXTRA_MESSAGES bundle array
     * 3. InboxStyle EXTRA_TEXT_LINES (stacked group notifications)
     * 4. EXTRA_BIG_TEXT
     * 5. EXTRA_TEXT
     */
    private fun extractMessages(notification: Notification, sbn: StatusBarNotification): List<CapturedMessage> {
        val extras = notification.extras ?: return emptyList()
        val defaultSender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() } ?: "WhatsApp"

        val results = mutableListOf<CapturedMessage>()

        // 1. AndroidX NotificationCompat.MessagingStyle
        try {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                for (msg in messagingStyle.messages) {
                    val text = msg.text?.toString()?.trim() ?: continue
                    if (text.isBlank() || isSummaryOrNoise(text)) continue
                    val sender = msg.person?.name?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: defaultSender
                    val time = if (msg.timestamp > 0L) msg.timestamp else sbn.postTime
                    results.add(CapturedMessage(sender = sender, text = text, timestamp = time))
                }
                if (results.isNotEmpty()) {
                    return results
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MessagingStyle extraction failed: ${e.message}")
        }

        // 2. Framework EXTRA_MESSAGES bundle array
        try {
            val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!rawMessages.isNullOrEmpty()) {
                for (item in rawMessages) {
                    if (item is Bundle) {
                        val text = item.getCharSequence("text")?.toString()?.trim() ?: continue
                        if (text.isBlank() || isSummaryOrNoise(text)) continue
                        val sender = item.getCharSequence("sender")?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: defaultSender
                        val time = item.getLong("time", sbn.postTime)
                        results.add(CapturedMessage(sender = sender, text = text, timestamp = time))
                    }
                }
                if (results.isNotEmpty()) {
                    return results
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "EXTRA_MESSAGES extraction failed: ${e.message}")
        }

        // 3. InboxStyle EXTRA_TEXT_LINES (for stacked lines / group summary)
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null) {
            for (line in lines) {
                val lineStr = line?.toString()?.trim() ?: continue
                if (lineStr.isBlank() || isSummaryOrNoise(lineStr)) continue
                val colonIdx = lineStr.indexOf(": ")
                val (lineSender, lineText) = if (colonIdx in 1..40) {
                    lineStr.substring(0, colonIdx).trim() to lineStr.substring(colonIdx + 2).trim()
                } else {
                    defaultSender to lineStr
                }
                if (lineText.isNotBlank() && !isSummaryOrNoise(lineText)) {
                    results.add(CapturedMessage(sender = lineSender, text = lineText, timestamp = sbn.postTime))
                }
            }
            if (results.isNotEmpty()) {
                return results
            }
        }

        // 4. EXTRA_BIG_TEXT
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.let { text ->
            if (text.isNotBlank() && !isSummaryOrNoise(text)) {
                results.add(CapturedMessage(sender = defaultSender, text = text, timestamp = sbn.postTime))
            }
        }

        // 5. EXTRA_TEXT
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.let { text ->
            if (text.isNotBlank() && !isSummaryOrNoise(text)) {
                results.add(CapturedMessage(sender = defaultSender, text = text, timestamp = sbn.postTime))
            }
        }

        return results
    }

    private fun isSummaryOrNoise(text: String): Boolean {
        if (SUMMARY_ONLY.matches(text)) return true
        if (text.contains("%") && !text.any { it.isLetterOrDigit() && it != '%' }) return true
        return false
    }

    data class CapturedMessage(
        val sender: String,
        val text: String,
        val timestamp: Long,
    )

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
