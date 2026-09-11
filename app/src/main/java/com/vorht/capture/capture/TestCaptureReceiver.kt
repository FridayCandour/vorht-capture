package com.vorht.capture.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vorht.capture.util.VorhtLogger
import java.util.UUID

/**
 * Diagnostic broadcast receiver for testing background delivery via ADB while device sleeps:
 * adb shell am broadcast -a com.vorht.capture.TEST_CAPTURE --es sender "Lord Friday" --es message "Test OTP: 998-112"
 */
class TestCaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TEST_CAPTURE) return

        val sender = intent.getStringExtra("sender")?.trim()?.takeIf { it.isNotBlank() } ?: "VorhtTest"
        val message = intent.getStringExtra("message")?.trim()?.takeIf { it.isNotBlank() } ?: "Test verification code: 123-456"
        val code = CodeParser.parse(message)
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        VorhtLogger.log(
            "TEST_CAPTURE_TRIGGERED",
            eventId = eventId,
            details = "sender=\"$sender\" message=\"$message\" code=${code ?: "none"}",
        )

        CaptureDispatcher.forward(
            context = context,
            eventId = eventId,
            key = "test|capture|$eventId",
            sender = sender,
            message = message,
            code = code,
            detectedAt = now,
        )
    }

    companion object {
        const val ACTION_TEST_CAPTURE = "com.vorht.capture.TEST_CAPTURE"
    }
}
