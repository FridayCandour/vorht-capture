package com.vorht.capture.capture

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.vorht.capture.net.CarlaDelivery
import com.vorht.capture.util.VorhtLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Ephemeral realtime delivery dispatcher:
 *  - Captures notifications immediately
 *  - Synchronously acquires a scoped WakeLock so CPU does not sleep before coroutine execution
 *  - Attempts HTTP POST immediately
 *  - Retries with rapid backoff within a 30-second freshness window while app process is alive
 *  - Discards events when 30s TTL expires
 *
 * No Room. No WorkManager. No deferred queue. No stale delivery.
 */
object CaptureDispatcher {

    private const val TAG = "CaptureDispatcher"
    private const val DELIVERY_WINDOW_MS = 30_000L
    private const val DEDUP_TTL_MS = 300_000L // 5 minutes retention to suppress group summary duplicates

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var powerManager: PowerManager? = null

    private val _forwarded = MutableStateFlow(0)
    val forwarded: StateFlow<Int> = _forwarded.asStateFlow()

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    // Dedup cache: dedupKey -> expireAtElapsedRealtime
    private val seenEvents = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        if (powerManager == null) {
            powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }
    }

    /**
     * Dispatch captured notification content for immediate delivery.
     * Synchronously acquires a partial wake lock before returning to prevent CPU sleep.
     */
    fun forward(
        context: Context,
        eventId: String,
        key: String,
        sender: String,
        message: String,
        code: String?,
        detectedAt: Long,
        source: String = "notification",
    ) {
        init(context)

        val now = SystemClock.elapsedRealtime()
        cleanupSeen(now)

        // Global dedup key by source + sender + content (independent of notification key)
        // so WhatsApp group summary and individual conversation notifications are unified.
        val dedupKey = "$source:$sender:${code ?: message.trim()}"
        if (seenEvents.putIfAbsent(dedupKey, now + DEDUP_TTL_MS) != null) {
            VorhtLogger.log(
                "CAPTURE_DUPLICATE_IGNORED",
                eventId = eventId,
                notificationKey = key,
                details = "sender=\"$sender\" dedupKey=\"$dedupKey\"",
            )
            return
        }

        // CRITICAL: Synchronously acquire wake lock BEFORE onNotificationPosted returns,
        // so Android OS cannot put CPU to sleep before the coroutine is scheduled.
        val wakeLock = try {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vorht:delivery:$eventId")?.apply {
                setReferenceCounted(false)
                acquire(35_000L) // Capped safety window
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquisition failed: ${e.message}")
            null
        }

        _pending.update { it + 1 }

        scope.launch {
            VorhtLogger.log(
                "DISPATCH_STARTED",
                eventId = eventId,
                notificationKey = key,
                details = "sender=\"$sender\"",
            )

            val deadline = SystemClock.elapsedRealtime() + DELIVERY_WINDOW_MS
            var attempt = 0
            var delivered = false

            val formattedMessage = CarlaDelivery.formatMessage(
                sender = sender,
                text = message,
            )

            try {
                while (SystemClock.elapsedRealtime() < deadline) {
                    attempt++
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break

                    val timeoutMs = minOf(remaining, 10_000L)

                    VorhtLogger.log(
                        "HTTP_ATTEMPT",
                        eventId = eventId,
                        notificationKey = key,
                        details = "attempt=$attempt timeoutMs=$timeoutMs remainingMs=$remaining",
                    )

                    val result = CarlaDelivery.send(
                        project = sender,
                        message = formattedMessage,
                        eventId = eventId,
                        timeoutMs = timeoutMs,
                    )

                    VorhtLogger.log(
                        "HTTP_RESULT",
                        eventId = eventId,
                        notificationKey = key,
                        details = "attempt=$attempt status=${result.statusCode ?: "none"} success=${result.isSuccess} durationMs=${result.durationMs} error=\"${result.errorMessage ?: "none"}\"",
                    )

                    if (result.isSuccess) {
                        delivered = true
                        break
                    }

                    val afterAttemptRemaining = deadline - SystemClock.elapsedRealtime()
                    if (afterAttemptRemaining <= 0L) break

                    val backoffMs = minOf(1_000L * attempt, afterAttemptRemaining, 5_000L)
                    delay(backoffMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Delivery loop error for $eventId: ${e.message}")
            } finally {
                _pending.update { (it - 1).coerceAtLeast(0) }
                try {
                    wakeLock?.let { if (it.isHeld) it.release() }
                } catch (e: Exception) {
                    Log.w(TAG, "WakeLock release error: ${e.message}")
                }

                if (delivered) {
                    _forwarded.update { it + 1 }
                    VorhtLogger.log(
                        "DISPATCH_FINISHED",
                        eventId = eventId,
                        notificationKey = key,
                        details = "attempts=$attempt",
                    )
                } else {
                    VorhtLogger.log(
                        "TTL_EXPIRED",
                        eventId = eventId,
                        notificationKey = key,
                        details = "attempts=$attempt action=DISCARD",
                    )
                }
            }
        }
    }

    private fun cleanupSeen(now: Long) {
        val iterator = seenEvents.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < now) {
                iterator.remove()
            }
        }
    }
}
