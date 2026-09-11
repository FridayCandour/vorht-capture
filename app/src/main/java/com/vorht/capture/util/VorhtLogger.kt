package com.vorht.capture.util

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Structured pipeline logger for Vorht Capture.
 * Emits standardized log entries for every event across the notification and dispatch pipeline
 * to both Logcat and a local persistent file (files/pipeline.log) readable via run-as.
 */
object VorhtLogger {

    private const val TAG = "VorhtPipeline"
    private var logFile: File? = null
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Synchronized
    fun init(context: Context) {
        if (logFile == null) {
            try {
                val filesDir = context.applicationContext.filesDir
                logFile = File(filesDir, "pipeline.log")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize pipeline.log file: ${e.message}")
            }
        }
    }

    @Synchronized
    fun log(
        event: String,
        eventId: String? = null,
        notificationKey: String? = null,
        details: String? = null,
    ) {
        val pid = Process.myPid()
        val thread = Thread.currentThread().name
        val now = System.currentTimeMillis()
        val formattedTime = isoFormat.format(Date(now))

        val logMessage = buildString {
            append(formattedTime).append(" ")
            append("event=").append(event)
            append(" pid=").append(pid)
            append(" thread=\"").append(thread).append("\"")
            append(" eventId=").append(eventId ?: "none")
            append(" key=\"").append(notificationKey ?: "none").append("\"")
            if (!details.isNullOrBlank()) {
                append(" ").append(details)
            }
        }

        Log.i(TAG, logMessage)

        try {
            logFile?.let { file ->
                if (file.length() > 500_000L) { // Rotate at ~500KB
                    file.delete()
                }
                FileWriter(file, true).use { writer ->
                    writer.write(logMessage)
                    writer.write("\n")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to pipeline.log: ${e.message}")
        }
    }

    @Synchronized
    fun readLogs(): String {
        return try {
            logFile?.takeIf { it.exists() }?.readText() ?: "No logs found"
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }
}
