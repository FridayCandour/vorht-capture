package com.vorht.capture.net

import android.os.SystemClock
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DeliveryResult(
    val isSuccess: Boolean,
    val statusCode: Int? = null,
    val errorMessage: String? = null,
    val durationMs: Long = 0L,
)

/**
 * Delivers captured WhatsApp details to the team chat via the carla relay:
 *
 *   POST https://carla.codedynasty.dev
 *   Headers: Idempotency-Key: <uuid>
 *   Body: { "project": "<whatsapp sender name>", "message": "<capture details>" }
 *
 * Any 2xx or 409 (duplicate) counts as delivered.
 * Non-blocking coroutine transport strictly bounded by per-attempt timeoutMs.
 */
object CarlaDelivery {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun formatMessage(
        sender: String,
        text: String,
    ): String {
        return buildString {
            appendLine("From: $sender")
            append("Message: $text")
        }
    }

    fun formatMessage(
        sender: String,
        code: String? = null,
        detectedAt: Long = 0L,
        eventId: String? = null,
        text: String,
    ): String = formatMessage(sender = sender, text = text)

    /**
     * Executes a synchronous HTTP POST with strict socket-level timeout bounding.
     * Runs directly on the calling thread (e.g. Dispatchers.IO) holding the WakeLock,
     * without delegating to OkHttp's asynchronous thread pool which can be throttled in sleep.
     */
    fun send(
        message: String,
        eventId: String,
        timeoutMs: Long,
    ): DeliveryResult {
        if (timeoutMs <= 0L) {
            return DeliveryResult(isSuccess = false, errorMessage = "TimeoutMs <= 0")
        }

        val start = SystemClock.elapsedRealtime()
        val payload = gson.toJson(
            mapOf(
                "message" to message.take(3500),
            )
        )
        val request = Request.Builder()
            .url(VorhtEndpoints.CARLA)
            .header("Idempotency-Key", eventId)
            .post(payload.toRequestBody(json))
            .build()

        val callClient = client.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(minOf(timeoutMs, 5000L), TimeUnit.MILLISECONDS)
            .readTimeout(minOf(timeoutMs, 10000L), TimeUnit.MILLISECONDS)
            .writeTimeout(minOf(timeoutMs, 10000L), TimeUnit.MILLISECONDS)
            .build()

        return try {
            callClient.newCall(request).execute().use { response ->
                val duration = SystemClock.elapsedRealtime() - start
                val success = response.isSuccessful || response.code == 409
                DeliveryResult(
                    isSuccess = success,
                    statusCode = response.code,
                    durationMs = duration,
                )
            }
        } catch (e: Exception) {
            val duration = SystemClock.elapsedRealtime() - start
            DeliveryResult(
                isSuccess = false,
                errorMessage = e.message ?: e.javaClass.simpleName,
                durationMs = duration,
            )
        }
    }

    fun send(
        project: String?,
        message: String,
        eventId: String,
        timeoutMs: Long,
    ): DeliveryResult = send(message = message, eventId = eventId, timeoutMs = timeoutMs)
}
