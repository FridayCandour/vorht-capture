package com.vorht.capture.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object EventStatus {
    const val PENDING = "PENDING" // captured, waiting for delivery
    const val SENT = "SENT"       // relay confirmed (HTTP 2xx)
}

@Entity(
    tableName = "verification_events",
    indices = [Index(value = ["notificationKey", "message"], unique = true)]
)
data class VerificationEvent(
    /** UUID — also the per-message uniqueness anchor for the relay payload. */
    @PrimaryKey val eventId: String,
    /** Android notification key. (key + message) unique index = never captured twice. */
    val notificationKey: String,
    val senderName: String,
    val message: String,
    val code: String?,
    /** Epoch millis. */
    val detectedAt: Long,
    val status: String = EventStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
)
