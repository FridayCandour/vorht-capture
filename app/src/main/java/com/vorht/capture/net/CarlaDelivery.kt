package com.vorht.capture.net

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Delivers captured WhatsApp details to the team chat via the carla relay:
 *
 *   POST https://carla.codedynasty.dev
 *   { "project": "<whatsapp sender name>", "message": "<capture details>" }
 *
 * Any 2xx counts as delivered. Failures stay queued in Room and are retried
 * by WorkManager with backoff — an event is never lost.
 */
object CarlaDelivery {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            // Fail fast: slow timeouts in a doze-blocked background turn one
            // attempt into a 45s+ hang. Short timeouts let retries cycle and
            // hit the instant the network becomes usable.
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    /** True when the relay accepted the message (HTTP 2xx). */
    fun send(project: String, message: String): Boolean {
        val payload = gson.toJson(
            mapOf(
                "project" to project.take(120),
                "message" to message.take(3500),
            )
        )
        val request = Request.Builder()
            .url(VorhtEndpoints.CARLA)
            .post(payload.toRequestBody(json))
            .build()
        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }
}
