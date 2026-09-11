package com.vorht.capture.util

import android.os.Process
import android.util.Log

/**
 * Structured pipeline logger for Vorht Capture.
 * Emits standardized log entries for every event across the notification and dispatch pipeline:
 *
 *   LISTENER_CONNECTED
 *   LISTENER_DISCONNECTED
 *   REBIND_REQUESTED
 *   CAPTURE_RECEIVED
 *   CAPTURE_PARSED
 *   DISPATCH_STARTED
 *   HTTP_ATTEMPT
 *   HTTP_RESULT
 *   DISPATCH_FINISHED
 *   TTL_EXPIRED
 *
 * Every entry contains: event, eventId, timestamp, notificationKey, pid, and thread name.
 */
object VorhtLogger {

    private const val TAG = "VorhtPipeline"

    fun log(
        event: String,
        eventId: String? = null,
        notificationKey: String? = null,
        details: String? = null,
    ) {
        val pid = Process.myPid()
        val thread = Thread.currentThread().name
        val ts = System.currentTimeMillis()

        val logMessage = buildString {
            append("event=").append(event)
            append(" pid=").append(pid)
            append(" thread=\"").append(thread).append("\"")
            append(" ts=").append(ts)
            append(" eventId=").append(eventId ?: "none")
            append(" key=").append(notificationKey ?: "none")
            if (!details.isNullOrBlank()) {
                append(" ").append(details)
            }
        }

        Log.i(TAG, logMessage)
    }
}
