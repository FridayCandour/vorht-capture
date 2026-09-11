package com.vorht.capture.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    /** IGNORE + unique (notificationKey, message) index = race-safe dedup. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: VerificationEvent): Long

    @Query("SELECT * FROM verification_events WHERE status = 'PENDING' ORDER BY detectedAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<VerificationEvent>

    @Query("UPDATE verification_events SET status = 'SENT' WHERE eventId = :eventId")
    suspend fun markSent(eventId: String)

    @Query(
        "UPDATE verification_events SET attempts = attempts + 1, lastError = :error " +
            "WHERE eventId = :eventId AND status = 'PENDING'"
    )
    suspend fun markFailed(eventId: String, error: String)

    @Query("SELECT COUNT(*) FROM verification_events WHERE status = 'SENT'")
    fun observeSent(): Flow<Int>

    @Query("SELECT COUNT(*) FROM verification_events WHERE status = 'PENDING'")
    fun observePending(): Flow<Int>
}
