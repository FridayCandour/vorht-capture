package com.vorht.capture.capture

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.vorht.capture.net.CarlaDelivery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Ephemeral realtime delivery dispatcher:
 *  - Captures notifications immediately
 *  - Holds a scoped WakeLock so CPU does not sleep during background delivery
 *  - Attempts HTTP POST immediately
 *  - Retries with rapid backoff within a 30-second freshness window while app process is alive
 *  - Discards events when 30s TTL expires
 *
 * No Room. No WorkManager. No deferred queue. No stale delivery.
 */
object CaptureDispatcher {

    private const val TAG = "CaptureDispatcher"
    private const val DELIVERY_WINDOW_MS = 30_000L
    private const val DEDUP_TTL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var powerManager: PowerManager? = null

    private val _forwarded = MutableStateFlow(0)
    val forwarded: StateFlow<Int> = _forwarded.asStateFlow()

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    // Dedup cache: dedupKey -> expireAtElapsedRealtime
    private val seenEvents = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    /**
     * Dispatch captured notification content for immediate delivery.
     */
    fun forward(
        key: String,
        sender: String,
        message: String,
        code: String?,
        detectedAt: Long,
    ) {
        val now = SystemClock.elapsedRealtime()
        cleanupSeen(now)

        // Dedup heuristic based on notification key + code / normalized snippet
        val dedupKey = "${WhatsAppNotificationListener.WHATSAPP_PACKAGE}:$key:${code ?: message.trim().take(64)}"
        if (seenEvents.putIfAbsent(dedupKey, now + DEDUP_TTL_MS) != null) {
            Log.d(TAG, "Duplicate notification event ignored: $dedupKey")
            return
        }

        val eventId = UUID.randomUUID().toString()
        _pending.update { it + 1 }

        scope.launch {
            val wakeLock = try {
                powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vorht:delivery:$eventId")?.apply {
                    setReferenceCounted(false)
                    acquire(35_000L) // Capped safety window
                }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock acquisition failed: ${e.message}")
                null
            }

            val deadline = SystemClock.elapsedRealtime() + DELIVERY_WINDOW_MS
            var attempt = 0
            var delivered = false

            val formattedMessage = CarlaDelivery.formatMessage(
                sender = sender,
                code = code,
                detectedAt = detectedAt,
                eventId = eventId,
                text = message,
            )

            try {
                while (SystemClock.elapsedRealtime() < deadline) {
                    attempt++
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break

                    val timeoutMs = minOf(remaining, 10_000L)

                    val success = CarlaDelivery.send(
                        project = sender,
                        message = formattedMessage,
                        eventId = eventId,
                        timeoutMs = timeoutMs,
                    )

                    if (success) {
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
                    Log.i(TAG, "Delivered event $eventId in $attempt attempt(s)")
                } else {
                    Log.w(TAG, "Discarded event $eventId: 30s TTL expired after $attempt attempt(s)")
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
