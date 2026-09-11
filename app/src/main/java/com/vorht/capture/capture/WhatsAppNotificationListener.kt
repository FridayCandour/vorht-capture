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
import android.provider.Telephony
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
        val notification = sbn.notification ?: return
        if (!isTargetNotification(sbn, notification)) return

        val pkg = sbn.packageName
        val key = sbn.key ?: "${pkg}|${sbn.id}"
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        VorhtLogger.log(
            "NOTIFICATION_POSTED",
            notificationKey = key,
            details = "pkg=$pkg id=${sbn.id} flags=0x${Integer.toHexString(notification.flags)} category=${notification.category} ongoing=$isOngoing groupSummary=$isGroupSummary postTime=${sbn.postTime}",
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

        val source = when {
            pkg.contains("whatsapp") -> "whatsapp"
            pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms") -> "sms"
            else -> pkg
        }

        for (item in messages) {
            val code = CodeParser.parse(item.text)
            val eventId = UUID.randomUUID().toString()

            VorhtLogger.log(
                "CAPTURE_PARSED",
                eventId = eventId,
                notificationKey = key,
                details = "source=$source sender=\"${item.sender}\" code=${code ?: "none"} textLength=${item.text.length}",
            )

            CaptureDispatcher.forward(
                context = applicationContext,
                eventId = eventId,
                key = key,
                sender = item.sender,
                message = item.text,
                code = code,
                detectedAt = item.timestamp,
                source = source,
            )
        }
    }

    private fun isTargetNotification(sbn: StatusBarNotification, notification: Notification): Boolean {
        val pkg = sbn.packageName

        // 1. Internal self-tests / diagnostics
        if (pkg == applicationContext.packageName || sbn.tag == "vorht_test") return true

        // 2. Known messaging & SMS packages
        if (TARGET_PACKAGES.contains(pkg)) return true

        // 3. Current default SMS app on device
        try {
            val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
            if (!defaultSms.isNullOrBlank() && defaultSms == pkg) return true
        } catch (_: Exception) {}

        // 4. Any package whose name indicates messaging or SMS/MMS
        if (pkg.contains("messaging") || pkg.contains(".mms") || pkg.contains(".sms")) return true

        // 5. Standard Android notification category for direct incoming messages
        if (notification.category == Notification.CATEGORY_MESSAGE) return true

        return false
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
                    description = "Maintains real-time background capture of SMS and WhatsApp verification codes"
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
                .setContentText("Active — listening for SMS & WhatsApp verification codes")
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
     * Resolves an appropriate human-readable sender title for the notification.
     * Uses EXTRA_TITLE_BIG, EXTRA_TITLE, or the application label (e.g. "Messages", "WhatsApp").
     */
    private fun resolveDefaultSender(extras: Bundle, pkg: String): String {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (title != null) return title

        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            when {
                pkg.contains("whatsapp") -> "WhatsApp"
                pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("sms") -> "SMS"
                else -> "Message"
            }
        }
    }

    /**
     * Extracts individual messages from notifications using all available styles.
     * Order of precedence:
     * 1. NotificationCompat.MessagingStyle (standard modern SMS and WhatsApp format)
     * 2. Framework Notification.EXTRA_MESSAGES bundle array
     * 3. InboxStyle EXTRA_TEXT_LINES (stacked group notifications)
     * 4. EXTRA_BIG_TEXT (expanded single message)
     * 5. EXTRA_TEXT (collapsed single message)
     */
    private fun extractMessages(notification: Notification, sbn: StatusBarNotification): List<CapturedMessage> {
        val extras = notification.extras ?: return emptyList()
        val pkg = sbn.packageName
        val defaultSender = resolveDefaultSender(extras, pkg)

        val results = mutableListOf<CapturedMessage>()

        // 1. AndroidX NotificationCompat.MessagingStyle (Google Messages, WhatsApp, Signal)
        try {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                val conversationTitle = messagingStyle.conversationTitle?.toString()?.trim()?.takeIf { it.isNotBlank() }
                for (msg in messagingStyle.messages) {
                    val text = msg.text?.toString()?.trim() ?: continue
                    if (text.isBlank() || isSummaryOrNoise(text)) continue
                    val sender = msg.person?.name?.toString()?.trim()?.takeIf { it.isNotBlank() }
                        ?: conversationTitle
                        ?: defaultSender
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

        // 4. EXTRA_BIG_TEXT (expanded message)
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.let { text ->
            if (text.isNotBlank() && !isSummaryOrNoise(text)) {
                results.add(CapturedMessage(sender = defaultSender, text = text, timestamp = sbn.postTime))
                return results
            }
        }

        // 5. EXTRA_TEXT (fallback single-line message)
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.let { text ->
            if (text.isNotBlank() && !isSummaryOrNoise(text)) {
                results.add(CapturedMessage(sender = defaultSender, text = text, timestamp = sbn.postTime))
                return results
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

        val TARGET_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.mms.service",
            "com.samsung.android.messaging",
            "com.hihonor.message",
            "com.huawei.message",
            "org.thoughtcrime.securesms",
            "org.telegram.messenger",
            "com.facebook.orca",
        )

        private val SUMMARY_ONLY = Regex(
            "(?i)^\\s*\\d+\\s+new\\s+messages?\\s*$" + "|" +
                "(?i)^\\s*\\d+\\s+messages?\\s*$"
        )
    }
}
