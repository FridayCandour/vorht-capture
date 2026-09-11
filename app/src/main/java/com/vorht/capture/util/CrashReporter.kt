package com.vorht.capture.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.vorht.capture.net.VorhtEndpoints
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Reports crashes + non-fatal errors to the Vorht ops endpoint
 * (POST { "project": ..., "message": ... } → WhatsApp relay).
 *
 * Crashes are posted before handing off to the default handler so the
 * system crash flow still happens.
 */
object CrashReporter : Thread.UncaughtExceptionHandler {

    private const val ENDPOINT = VorhtEndpoints.CARLA
    private const val PROJECT = VorhtEndpoints.PROJECT

    private lateinit var appContext: Context
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun install(context: Context) {
        appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            post(buildMessage("CRASH", throwable.stackTraceAsString()))
        } catch (ignored: Exception) {
        } finally {
            // Hand off so the system still logs/shows the crash properly.
            previousHandler?.uncaughtException(thread, throwable)
                ?: Runtime.getRuntime().exit(10)
        }
    }

    /** Fire-and-forget non-fatal reports (listener errors, sync failures, odd states). */
    fun report(message: String) {
        thread(name = "vorht-report", isDaemon = true) {
            try {
                post(buildMessage("ERROR", message))
            } catch (ignored: Exception) {
            }
        }
    }

    private fun buildMessage(kind: String, detail: String): String {
        val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android " +
            "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val body = mapOf(
            "project" to PROJECT,
            "message" to ("$kind · $device\n" + detail).take(3500),
        )
        return Gson().toJson(body)
    }

    private fun post(json: String) {
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            Log.d("CrashReporter", "report delivered, status ${response.code}")
        }
    }

    private fun Throwable.stackTraceAsString(): String {
        val sw = StringWriter()
        printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
